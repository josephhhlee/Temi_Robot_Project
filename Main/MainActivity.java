package com.cyberpunk.temiiotproject;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.TtsRequest;
import com.robotemi.sdk.constants.SdkConstants;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener;
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener;
import com.robotemi.sdk.navigation.listener.OnDistanceToLocationChangedListener;
import com.robotemi.sdk.navigation.model.Position;
import com.robotemi.sdk.permission.Permission;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements
        OnGoToLocationStatusChangedListener,
        OnDistanceToLocationChangedListener,
        Robot.AsrListener,
        OnDetectionStateChangedListener,
        OnCurrentPositionChangedListener,
        Robot.WakeupWordListener,
        Robot.TtsListener {

    private ServerSocket serverSocket;
    Thread serverThread = null;
    Thread goToPatrol = null;
    public static final int SERVER_PORT = 3003;
    private LinearLayout msgList;
    private Handler handler;
    private int greenColor;
    Intent serviceIntent;
    private Robot robot;
    volatile boolean reached, goToCancelled, onConversationDone;
    boolean patrol, touchDetected, positionChanged, onConversation, faceDetected, askResume, launchBrowser, detected, onResume , closedBrowser, checkout, timeout, exit;
    String[] resumePlace;
    static String lastCheckpoint;
    static String[] checkpoints;
    float[] checkpointsDistance;
    ArrayList<Float> nowCheckpointsDistance;
    private View marker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Server");
        greenColor = ContextCompat.getColor(this, R.color.green);
        handler = new Handler();
        msgList = findViewById(R.id.msgList);
        robot = Robot.getInstance();
        robot.setDetectionModeOn(false);
        checkpoints = new String[] {"checkpoint1", "checkpoint2", "checkpoint3"};
        checkpointsDistance = new float[3];
        nowCheckpointsDistance = new ArrayList<>();
        resumePlace = new String[3];
    }

    @Override
    protected void onStart() {
        super.onStart();
        startServer();
        startClient();
        robot.addOnGoToLocationStatusChangedListener(this);
        robot.addOnDistanceToLocationChangedListener(this);
        robot.addTtsListener(this);
        robot.addWakeupWordListener(this);
        robot.addAsrListener(this);
        robot.addOnDetectionStateChangedListener(this);
        robot.hideTopBar();
        robot.toggleWakeup(true);
        robot.finishConversation();
        robot.setTrackUserOn(true);
        robot.setDetectionModeOn(true);
        robot.toggleWakeup(false);
         
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkout = Browser.getCheckoutStatus();
        timeout = Browser.getTimeoutStatus();
        exit = Browser.getExitStatus();
        if(closedBrowser) {
            closedBrowser = false;
            onResume = true;
            patrol = true;
            goToPatrol = new Thread(this::goToPatrol);
            goToPatrol.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closedBrowser = true;
        interruptPatrol();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (robot.checkSelfPermission(Permission.FACE_RECOGNITION) == Permission.GRANTED) {
            robot.stopFaceRecognition();
        }
        robot.removeOnGoToLocationStatusChangedListener(this);
        robot.removeOnDistanceToLocationChangedListener(this);
        robot.removeTtsListener(this);
        robot.removeAsrListener(this);
        robot.removeWakeupWordListener(this);
        robot.removeOnDetectionStateChangedListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != serverThread) {
            serverThread.interrupt();
            serverThread = null;
        }
    }

    /**********
     SEVER SOCKET
     **********/
    public void startServer() {
        msgList.removeAllViews();
        showMessage("Server Started.", Color.YELLOW);
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
    }

    public void startClient() {
        serviceIntent = new Intent(this, CallOut.class);
        startService(serviceIntent);
    }

    public TextView textView(String message, int color) {
        if (null == message || message.trim().isEmpty()) {
            message = "<Empty Message>";
        }
        TextView tv = new TextView(this);
        tv.setTextColor(color);
        tv.setText(message/* + getTime()*/);
        tv.setTextSize(20);
        tv.setPadding(0, 5, 0, 0);
        return tv;
    }

    public void showMessage(final String message, final int color) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                msgList.addView(textView(message, color));
            }
        });
    }

    @Override
    public void onTtsStatusChanged(@NotNull TtsRequest ttsRequest) {

    }

//    private void sendMessage(final String message) {
//        try {
//            if (null != tempClientSocket) {
//                new Thread(() -> {
//                    PrintWriter out = null;
//                    try {
//                        out = new PrintWriter(new BufferedWriter(
//                                new OutputStreamWriter(tempClientSocket.getOutputStream())),
//                                true);
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    if (message != null) {
//                        assert out != null;
//                        out.println(message);
//                    }
//                }).start();
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        hideKeyboard();
//    }

    class ServerThread implements Runnable {

        public void run() {
            Socket socket;
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                //   findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Starting Server : " + e.getMessage(), Color.RED);
            }
            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        socket = serverSocket.accept();
                        CommunicationThread commThread = new CommunicationThread(socket);
                        new Thread(commThread).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                        showMessage("Error Communicating to Client :" + e.getMessage(), Color.RED);
                    }
                }
            }
        }
    }

    class CommunicationThread implements Runnable {

        private BufferedReader input;

        public CommunicationThread(Socket clientSocket) {
            try {
                this.input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String info = input.readLine();
                if (info != null) {
                    commandList(info);
                }
            } catch (IOException e) {
                e.printStackTrace();
                showMessage("Error Connecting to Client!!", Color.RED);
            }
        }

        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    if (null == read || "Disconnect".contentEquals(read)) {
//                        Thread.interrupted();
                        break;
                    }
                    showMessage("Client : " + read, greenColor);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

//    String getTime() {
//        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
//        return sdf.format(new Date());
//    }

    // Launch browser activity
    public void launchBrowser(View view) {
        if (detected) {
            Toast.makeText(MainActivity.this, "Loading Food Menu", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, Browser.class);
            startActivity(intent);
            launchBrowser = true;
        }
        else {
            robot.speak(TtsRequest.create("Hi there, please start patrol mode to enable Menu", false));
            Toast.makeText(MainActivity.this, "Set Patrol Mode to Enable Menu", Toast.LENGTH_SHORT).show();
        }
    }

    public void launchBrowser() {
        Toast.makeText(MainActivity.this, "Loading Food Menu", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MainActivity.this, Browser.class);
        startActivity(intent);
        launchBrowser = true;
    }

    /**********
     TEMI SDK
     **********/
//    private void hideKeyboard() {
//        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
//        //Find the currently focused view, so we can grab the correct window token from it.
//        View view = getCurrentFocus();
//        //If no view currently has focus, create a new one, just so we can grab a window token from it
//        if (view == null) {
//            view = new View(this);
//        }
//        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
//    }

    public void goToPos(JSONObject jsonObject) throws JSONException {
        float x = 0, y = 0, yaw = 0;
        JSONArray goToPos = jsonObject.getJSONArray("goToPos");
        for (int i = 0; i < goToPos.length(); i++) {
            JSONObject data = goToPos.getJSONObject(i);
            x = Float.parseFloat(data.getString("x"));
            y = Float.parseFloat(data.getString("y"));
            yaw = Float.parseFloat(data.getString("yaw"));
        }
        robot.speak(TtsRequest.create("Going to destination " + x + " by " + y + ". Yaw, " + yaw + ".", false));
        robot.goToPosition(new Position(x, y, yaw, 0));
        showMessage("Go to position >> X =" + x + ", Y = " + y + ", Yaw = " + yaw, Color.GREEN);
    }

    public void goTo(JSONObject jsonObject) throws JSONException {
        String desto = "";
        JSONArray goTo = jsonObject.getJSONArray("goTo");
        for (int i = 0; i < goTo.length(); i++) {
            JSONObject data = goTo.getJSONObject(i);
            desto = data.getString("desto");
        }
        if (desto.equals("patrol")) {
            patrol = true;
            goToPatrol = new Thread(this::goToPatrol);
            goToPatrol.start();
        }
        for (String i : robot.getLocations()) {
            if (i.equals(desto)) {
                robot.speak(TtsRequest.create("Going to " + desto + ".", false));
                robot.goTo(desto.toLowerCase().trim());
            }
        }
        showMessage("Go to destination >> " + desto, Color.GREEN);
    }

    public void saveLocation(JSONObject jsonObject) throws JSONException {
        String loc = null;
        JSONArray saveLocation = jsonObject.getJSONArray("saveLocation");
        for (int i = 0; i < saveLocation.length(); i++) {
            JSONObject data = saveLocation.getJSONObject(i);
            loc = data.getString("location");
        }
        String location = loc;
        boolean result = robot.saveLocation(location.toLowerCase().trim());
        if (result) {
            robot.speak(TtsRequest.create("I've successfully saved the " + location + " location.", false));
        } else {
            robot.speak(TtsRequest.create("Saved the " + location + " location failed.", false));
        }
        showMessage("Save current location as >> " + loc, Color.GREEN);
    }

    public void speak(JSONObject jsonObject) throws JSONException {
        String say = "";
        JSONArray saveLocation = jsonObject.getJSONArray("speak");
        for (int i = 0; i < saveLocation.length(); i++) {
            JSONObject data = saveLocation.getJSONObject(i);
            say = data.getString("say");
        }
        robot.speak(TtsRequest.create(say, false));
        showMessage("Temi TTS >>\n" + say, Color.GREEN);
    }

    public void commandList(String info) {
        try {
            JSONObject jsonObject = new JSONObject(info);
            if (jsonObject != null) {
                if (jsonObject.has("stopPatrol")) {
                    interruptPatrol();
                }
                if (jsonObject.has("goToPos")) {
                    goToPos(jsonObject);
                } else if (jsonObject.has("goTo")) {
                    goTo(jsonObject);
                } else if (jsonObject.has("saveLocation")) {
                    saveLocation(jsonObject);
                } else if (jsonObject.has("speak")) {
                    speak(jsonObject);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void interruptPatrol() {
        patrol = false;
        if (goToPatrol != null) {
            goToPatrol.interrupt();
            doneConversation();
            robot.toggleWakeup(true);
            robot.setDetectionModeOn(false);
        }
    }

    public void goToPatrol() {
        detected = false;
        BatteryData batteryData = robot.getBatteryData();
        if(!onResume) {
            robot.speak(TtsRequest.create("Going to Patrol.", false));
        }
        else if (checkout) {
            robot.speak(TtsRequest.create("Thank for your patronage.", false));
        }
        else if (exit) {
            robot.speak(TtsRequest.create("Thank for the cancellation, Temi look forward to serve you again.", false));
        }
        else if (timeout) {
            robot.speak(TtsRequest.create("Please don't waste other people's time, thank you.", false));
        }
        try {
            for (int i = nowCheckpointsDistance.indexOf(findNearestCheckpoint()); i < 3; i++) {
                if (Objects.requireNonNull(batteryData).getBatteryPercentage() <= 15) {
                    robot.speak(TtsRequest.create("Low battery, going home to recharge.", false));
                    robot.goTo("home base");
                    while (batteryData.getBatteryPercentage() < 95) {}
                    robot.speak(TtsRequest.create("Going to Patrol.", false));
                    i = nowCheckpointsDistance.indexOf(findNearestCheckpoint());
                }
                if(!onResume) {
                    robot.goTo(checkpoints[i]);
                    while (!reached) {}
                    reached = false;
                    lastCheckpoint = checkpoints[i];
                }
                touchDetected = false;
                if (!patrol) {break;}
                for (int k = 0; k < 300; k++) {
                    showMessage("k > "+ k, greenColor);
                    if (!onResume && touchDetected || faceDetected) {
                        touchDetected = false;
                        onConversationDone = false;
                        robot.speak(TtsRequest.create("Hi there, if you need assistant for location guidance please call, " + robot.getWakeupWord() + ". If you wish to place your orders, please press, Browser Menu, on the screen. Thank you.", false));
                        detected = true;
                    }
                    onResume = false;
                    while(detected) {
                        for (int l = 0; l < 300; l++) {
                            if (touchDetected) {
                                l = -1;
                                touchDetected = false;
                            }
                            TimeUnit.MILLISECONDS.sleep(100);
                        }
                        detected = false;
                        if(onConversation){while(!onConversationDone){}break;}
                    }
                    if (positionChanged) {
                        i = nowCheckpointsDistance.indexOf(findNearestCheckpoint()) - 1;
                        positionChanged = false;
                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                }
                if (!patrol) {break;}
                if (i == 2) {i = -1;}
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
                
    public float findNearestCheckpoint() {
        float nearest = checkpointsDistance[0];
        for (int j = 0; j < 3; j++) {
            nowCheckpointsDistance.add(checkpointsDistance[j]);
            if (nowCheckpointsDistance.get(j) < nearest) {
                nearest = nowCheckpointsDistance.get(j);
            }
        }
        return nearest;
    }

    public void onUserInteraction() {
        touchDetected = true;
    }

    @Override
    public void onGoToLocationStatusChanged(@NotNull String location, String status, int descriptionId, @NotNull String description) {
        switch (status) {
            case OnGoToLocationStatusChangedListener.COMPLETE:
//                robot.speak(TtsRequest.create("Completed", false));
                goToCancelled = false;
                reached = true;
                break;

            case OnGoToLocationStatusChangedListener.ABORT:
//                robot.speak(TtsRequest.create("Cancelled", false));
                reached = false;
                goToCancelled = true;
                if (onConversation) {
                    return;
                }
                else if (patrol) {
                    robot.speak(TtsRequest.create("Travel interrupted, continue patrol.", false));
                    robot.goTo(checkpoints[nowCheckpointsDistance.indexOf(findNearestCheckpoint())]);
                }
//                else {
//                    robot.goTo("home base");
//                }
                break;
        }
    }

    @Override
    public void onDistanceToLocationChanged(@NotNull Map<String, Float> distances) {
        for (int i = 0; i < 3; i++) {
            checkpointsDistance[i] = distances.get(checkpoints[i]);
        }
    }

    @Override
    public void onWakeupWord(@NotNull String wakeupWord, int direction) {
        onConversation = true;
        robot.finishConversation();
        robot.askQuestion("Hi, if you need help with navigation please response to one of the following. Toilet. Cafe. Lift. Classroom.");
    }

    @Override
    public void onAsrResult(final @NonNull String asrResult) {
        try {
            Bundle metadata = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA)
                    .metaData;
            if (metadata == null) return;
            if (!robot.isSelectedKioskApp()) return;
            if (!metadata.getBoolean(SdkConstants.METADATA_OVERRIDE_NLU)) return;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        if (askResume) {
            askResume = false;
            if (asrResult.equalsIgnoreCase("Yes") || asrResult.equalsIgnoreCase("Yes please") || asrResult.equalsIgnoreCase("Yup") || asrResult.equalsIgnoreCase("Okay") || asrResult.equalsIgnoreCase("Resume") || asrResult.equalsIgnoreCase("Yes resume")) {
                onTemiNavigationGuidance(resumePlace[0], resumePlace[1], resumePlace[2]);
            } else {doneConversation(); return;}
        }
        if (asrResult.equalsIgnoreCase("Bring me to the toilet") || asrResult.equalsIgnoreCase("Where is the toilet") || asrResult.equalsIgnoreCase("Toilet") || asrResult.equalsIgnoreCase("Bring me to the restroom") || asrResult.equalsIgnoreCase("Where is the restroom") || asrResult.equalsIgnoreCase("Restroom")) {
            onTemiNavigationGuidance(checkpoints[2], "toilet", "The restrooms are to my right, have a nice day!");
        } else if (asrResult.equalsIgnoreCase("Bring me to the cafe") || asrResult.equalsIgnoreCase("Where is the cafe") || asrResult.equalsIgnoreCase("Cafe")) {
            onTemiNavigationGuidance("cafe", "cafe", "We have reached the cafe, have a nice day!");
        } else if (asrResult.equalsIgnoreCase("Bring me to the lift") || asrResult.equalsIgnoreCase("Where is the lift") || asrResult.equalsIgnoreCase("Lift") || asrResult.equalsIgnoreCase("Bring me to the elevator") || asrResult.equalsIgnoreCase("Where is the elevator") || asrResult.equalsIgnoreCase("Elevator")) {
            onTemiNavigationGuidance(checkpoints[0], "lift", "We have reached the lift, have a nice day!");
        } else if (asrResult.equalsIgnoreCase("Bring me to the classroom") || asrResult.equalsIgnoreCase("Where is the classroom") || asrResult.equalsIgnoreCase("Classroom")) {
            onTemiNavigationGuidance("classroom1", "classroom entrance hallway", "We have reached, to my left is a hallway leading to 3 different classrooms. Have a nice day!");
        } else if (asrResult.equalsIgnoreCase("Bring up food menu") || asrResult.equalsIgnoreCase("Food menu")) {
            doneConversation();
            launchBrowser();
        } /*else if (asrResult.equalsIgnoreCase("Entertain me") || asrResult.equalsIgnoreCase("Play something") || asrResult.equalsIgnoreCase("Play music") || asrResult.equalsIgnoreCase("Play video")){

        }*/ else {
            robot.finishConversation();
            robot.speak(TtsRequest.create("Sorry I can't understand you, thank you for your time.", false));
            doneConversation();
        }
    }

    private void onTemiNavigationGuidance(String place, String checkpoint, String speech) {
//        robot.finishConversation();
        robot.speak(TtsRequest.create("Please follow me.", false));
        positionChanged = true;
        robot.goTo(place);
        while (!reached || !goToCancelled) {}
        reached = false;
        if (goToCancelled) {
            goToCancelled = false;
            robot.askQuestion("Travel was interrupted, do you want to resume?");
            askResume = true;
            resumePlace[0] = place;
            resumePlace[1] = checkpoint;
            resumePlace[2] = speech;
            return;
        }
        lastCheckpoint = checkpoint;
        robot.speak(TtsRequest.create(speech, false));
        doneConversation();
    }

    public void doneConversation() {
        robot.finishConversation();
        onConversation = false;
        onConversationDone = true;
    }

    @Override
    public void onDetectionStateChanged(int state) {
        if (state == OnDetectionStateChangedListener.DETECTED) {
            faceDetected = true;
        } else  {faceDetected = false;}
    }

    public static String getLastCheckpoint() {
        return lastCheckpoint;
    }

    @Override
    public void onCurrentPositionChanged(@NotNull Position position) {
        showMessage(position.toString(), greenColor);
//        temiMarker(position.getX(), position.getY());
    }

//    private void temiMarker(float x, float y) {
//        marker = findViewById(R.id.markerView);
//        marker.animate()
//                .x(x)
//                .y(y)
//                .setDuration(1000)
//                .start();
//    }

}
