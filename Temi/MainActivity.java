package com.cyberpunk.temiiotproject;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
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

    private Thread goToPatrol = null;
    private View marker, pin;
    private Robot robot;
    volatile boolean touchDetected, positionChanged, onConversation, faceDetected, askResume, detected, onResume = false, needCharge, reached = false, goToCancelled = false, onConversationDone , checkout, timeout, exit, closeServer;
    static boolean launchBrowser;
    volatile String[] resumePlace;
    volatile static String reachedCheckpoint, previousCheckpoint;
    volatile static String[] checkpoints;
    volatile float[] checkpointsDistance;
    volatile static boolean patrol = false;
    volatile ArrayList<Float> nowCheckpointsDistance;
    volatile List<String> currentLocations;
    float xPos, yPos;

    private Thread serverThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        marker = findViewById(R.id.markerView);
        pin = findViewById(R.id.pin);
        robot = Robot.getInstance();
        checkpoints = new String[]{"checkpoint1", "checkpoint2", "checkpoint3"};
        checkpointsDistance = new float[3];
        nowCheckpointsDistance = new ArrayList<>();
        resumePlace = new String[3];
        launchBrowser = false;
        reachedCheckpoint = "home base";
        previousCheckpoint = "null";
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
        robot.addOnCurrentPositionChangedListener(this);
        robot.hideTopBar();
        robot.finishConversation();
        robot.toggleWakeup(false);
        robot.setTrackUserOn(true);
        robot.setDetectionModeOn(true);
        currentLocations = new ArrayList<>(robot.getLocations());
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkout = Browser.getCheckoutStatus();
        timeout = Browser.getTimeoutStatus();
        exit = Browser.getExitStatus();
        if (launchBrowser) {
            if (checkout) {
                robot.speak(TtsRequest.create("Thank for your patronage.", false));
            } else if (exit) {
                robot.speak(TtsRequest.create("Thank for the cancellation, Temi look forward to serve you again.", false));
            } else if (timeout) {
                robot.speak(TtsRequest.create("Please don't waste other people's time, thank you.", false));
            }
            launchBrowser = false;
            onResume = true;
            patrol = true;
            goToPatrol = new Thread(this::goToPatrol);
            goToPatrol.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        launchBrowser = getBrowserInfo();
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
        robot.removeOnCurrentPositionChangedListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != serverThread) {
            serverThread.interrupt();
            serverThread = null;
        }
    }

    void startServer() {
        serverThread = new Thread(new ServerThread());
        serverThread.start();
    }

    void startClient() {
        Intent serviceIntent = new Intent(this, CallOut.class);
        startService(serviceIntent);
    }

    class ServerThread implements Runnable {
        private static final int SERVER_PORT = 0; //Enter Port


        @Override
        public void run() {
            try {
                ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
                while (!closeServer) {
                    new Thread(new CommunicationThread(serverSocket.accept())).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class CommunicationThread implements Runnable {
        private final Socket clientSocket;
        private PrintWriter output;

        public CommunicationThread (Socket socket){
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                final String message = input.readLine();
                if (message != null) {
                    if (message.equals("remote")){
                        new Thread(new ContinuousCommunicationThread(clientSocket)).start();
                        return;
                    }
                    new Thread(() -> commandList(message)).start();
                }
                input.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ContinuousCommunicationThread implements Runnable {
        private final Socket clientSocket;
        private BufferedReader input;
        private PrintWriter output;

        public ContinuousCommunicationThread (Socket socket){
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
//                new Thread(this::packData).start();
                new Thread(new SendData(clientSocket)).start();
                input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                while (true) {
                    final String message = input.readLine();
                    if (message != null) {
                        if (message.equals("end")) {
                            input.close();
                            clientSocket.close();
                            return;
                        }
                        new Thread(() -> commandList(message)).start();
                    } else {
                        input.close();
                        clientSocket.close();
                        return;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    class SendData implements Runnable {
        private final Socket clientSocket;
        private PrintWriter output;
        private boolean sentLocation = false;

        SendData(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    output = new PrintWriter(clientSocket.getOutputStream());
                    output.println(packData());
                    output.flush();
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                    output.close();
                    break;
                }
            }
        }

        private String packData() {
            JSONObject jsonData = null;
            try {
                String[] batteryInfo = CallOut.getJsonBatteryData();
                jsonData = new JSONObject();
                jsonData.put("x", yPos);
                jsonData.put("y", xPos);
                jsonData.put("checkpointReached", getReachedCheckpoint());
                jsonData.put("previousCheckpoint", getPreviousCheckpoint());
                jsonData.put("battery", batteryInfo[0]);
                jsonData.put("charging", batteryInfo[1]);
                jsonData.put("patrolStatus", getPatrolStatus());
                if(!sentLocation || !currentLocations.equals(robot.getLocations())) {
                    currentLocations.clear();
                    currentLocations = new ArrayList<>(robot.getLocations());
                    JSONArray jsonLocations = new JSONArray(currentLocations);
                    jsonData.put("locations", jsonLocations);
                    sentLocation = true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return jsonData.toString();
        }

    }

     public void launchBrowser(View view) {
        if (detected) {
            Toast.makeText(MainActivity.this, "Loading Food Menu", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, Browser.class);
            startActivity(intent);
            launchBrowser = true;
        } else {
            robot.speak(TtsRequest.create("Hi there, please start patrol mode to enable Menu", false));
            Toast.makeText(MainActivity.this, "Set Patrol Mode to Enable Menu", Toast.LENGTH_SHORT).show();
        }
    }

    void launchBrowser() {
        Toast.makeText(MainActivity.this, "Loading Food Menu", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MainActivity.this, Browser.class);
        startActivity(intent);
        launchBrowser = true;
    }

    void goToPos(JSONObject jsonObject) throws JSONException {
        float x, y, yaw;
        JSONObject goToPos = jsonObject.getJSONObject("goToPos");
        x = (float)goToPos.getDouble("x");
        y = (float)goToPos.getDouble("y");
        yaw = (float)goToPos.getDouble("yaw");
        robot.speak(TtsRequest.create("Going to destination " + x + " by " + y + ". Yaw, " + yaw + ".", false));
        robot.goToPosition(new Position(x, y, yaw, 0));
    }

    void goTo(JSONObject jsonObject) throws JSONException {
        String desto;
        JSONObject goTo = jsonObject.getJSONObject("goTo");
        desto = goTo.getString("desto");
        if (desto.equals("patrol")) {
            patrol = true;
            goToPatrol = new Thread(this::goToPatrol);
            goToPatrol.start();
        }
        for (String i : robot.getLocations()) {
            if (i.equals(desto)) {
                robot.speak(TtsRequest.create("Going to " + desto + ".", false));
                robot.goTo(desto.toLowerCase().trim());
                return;
            }
        }
    }

    void saveLocation(JSONObject jsonObject) throws JSONException {
        String location;
        JSONObject saveLocation = jsonObject.getJSONObject("saveLocation");
        location = saveLocation.getString("location");
        boolean result = robot.saveLocation(location.toLowerCase().trim());
        if (result) {
            robot.speak(TtsRequest.create("I've successfully saved the " + location + " location.", false));
            currentLocations.add(location.toLowerCase().trim());
        } else {
            robot.speak(TtsRequest.create("Saved the " + location + " location failed.", false));
        }
    }

    void deleteLocation(JSONObject jsonObject) throws JSONException {
        String location;
        JSONObject deleteLocation = jsonObject.getJSONObject("deleteLocation");
        location = deleteLocation.getString("location");
        for (String scan : robot.getLocations()) {
            if (scan.equals(location)) {
                boolean result = robot.deleteLocation(location.toLowerCase().trim());
                if(result) {
                    robot.speak(TtsRequest.create(location + "delete successfully.", false));
                    currentLocations.remove(location.toLowerCase().trim());
                    return;
                }
            }
        }
        robot.speak(TtsRequest.create(location + "delete failed!", false));
    }

    void speak(JSONObject jsonObject) throws JSONException {
        String say;
        JSONObject saveLocation = jsonObject.getJSONObject("speak");
        say = saveLocation.getString("say");
        robot.speak(TtsRequest.create(say, false));
    }

    synchronized void commandList(String info) {
        try {
            JSONObject jsonObject = new JSONObject(info);
            if (patrol) {
                interruptPatrol();
                robot.speak(TtsRequest.create("Patrol Mode Stopped.", false));
            }
            if (jsonObject.has("goToPos")) {
                goToPos(jsonObject);
            } else if (jsonObject.has("goTo")) {
                goTo(jsonObject);
            } else if (jsonObject.has("saveLocation")) {
                saveLocation(jsonObject);
            } else if (jsonObject.has("speak")) {
                speak(jsonObject);
            } else if (jsonObject.has("deleteLocation")) {
                deleteLocation(jsonObject);
            } else if (jsonObject.has("followMe")) {
                    robot.beWithMe();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    void interruptPatrol() {
        patrol = false;
        if (goToPatrol != null) {
            goToPatrol.interrupt();
            doneConversation();
        }
    }

    synchronized void goToPatrol() {
        detected = false;
        needCharge = false;
        BatteryData batteryData = robot.getBatteryData();
        if (!onResume) {
            robot.speak(TtsRequest.create("Going to Patrol.", false));
        }
        try {
            for (int i = nowCheckpointsDistance.indexOf(findNearestCheckpoint()); i < checkpoints.length; i++) {
                if (Objects.requireNonNull(batteryData).getBatteryPercentage() <= 15) {
                    needCharge = true;
                    robot.speak(TtsRequest.create("Low battery, going home to recharge.", false));
                    robot.goTo("home base");
                    previousCheckpoint = reachedCheckpoint;
                    while (!reached) {
                        reachedCheckpoint = null;
                    }
                    reached = false;
                    reachedCheckpoint = "home base";
                    while (batteryData.getBatteryPercentage() < 95) {
                    }
                    needCharge = false;
                    robot.speak(TtsRequest.create("Going to Patrol.", false));
                    i = nowCheckpointsDistance.indexOf(findNearestCheckpoint());
                }
                if (!patrol) {
                    return;
                }
                if (!onResume) {
                    robot.goTo(checkpoints[i]);
                    previousCheckpoint = reachedCheckpoint;
                    while (!reached) {
                        reachedCheckpoint = null;
                    }
                    reached = false;
                    reachedCheckpoint = checkpoints[i];
                }
                touchDetected = false;
                for (int k = 0; k < 600; k++) {
                    if (!patrol) {
                        return;
                    }
                    if (!onResume && touchDetected || faceDetected) {
                        robot.constraintBeWith();
                        touchDetected = false;
                        onConversationDone = false;
                        robot.speak(TtsRequest.create("Hi there, if you need assistant for location guidance please call, " + robot.getWakeupWord() + ". If you wish to place your orders, please press, Menu, on the screen. Thank you.", true));
                        detected = true;
                    }
                    onResume = false;
                    while (detected) {
                        if (!patrol) {
                            return;
                        }
                        for (int l = 0; l < 300; l++) {
                            if (touchDetected) {
                                l = -1;
                                touchDetected = false;
                            }
                            TimeUnit.MILLISECONDS.sleep(100);
                        }
                        detected = false;
                        if (onConversation) {
                            while (!onConversationDone) {
                            }
                            break;
                        }
                    }
                    if (positionChanged) {
                        i = nowCheckpointsDistance.indexOf(findNearestCheckpoint()) - 1;
                        positionChanged = false;
                        break;
                    }
                    TimeUnit.MILLISECONDS.sleep(100);
                }
                if (!patrol) {
                    return;
                }
                if (i == checkpoints.length - 1) {
                    i = -1;
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    float findNearestCheckpoint() {
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
                goToCancelled = false;
                reached = true;
                break;

            case OnGoToLocationStatusChangedListener.ABORT:
                reached = false;
                goToCancelled = true;
                if (patrol) {
                    if (onConversation) {
                        return;
                    } else if (needCharge) {
                        robot.speak(TtsRequest.create("Low battery, going home to recharge.", false));
                        robot.goTo("home base");
                    } else {
                        robot.speak(TtsRequest.create("Travel interrupted, continue patrol.", false));
                        robot.goTo(checkpoints[nowCheckpointsDistance.indexOf(findNearestCheckpoint())]);
                    }
                }
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
        robot.finishConversation();
        robot.askQuestion("Hi, if you need help with navigation please response to one of the following. Toilet. Cafe. Lift. Classroom.");
    }

    @Override
    public void onAsrResult(final @NonNull String asrResult) {
        Thread onTemiNavigation;
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
                onTemiNavigation = new Thread(() -> onTemiNavigationGuidance(resumePlace[0], resumePlace[1], resumePlace[2]));
                onTemiNavigation.start();
            } else {
                doneConversation();
                return;
            }
        }
        if (asrResult.equalsIgnoreCase("Bring me to the toilet") || asrResult.equalsIgnoreCase("Where is the toilet") || asrResult.equalsIgnoreCase("Toilet") || asrResult.equalsIgnoreCase("Bring me to the restroom") || asrResult.equalsIgnoreCase("Where is the restroom") || asrResult.equalsIgnoreCase("Restroom")) {
            onTemiNavigation = new Thread(() -> onTemiNavigationGuidance(checkpoints[2], "toilet", "The restrooms are to my right, have a nice day!"));
            onTemiNavigation.start();
        } else if (asrResult.equalsIgnoreCase("Bring me to the cafe") || asrResult.equalsIgnoreCase("Where is the cafe") || asrResult.equalsIgnoreCase("Cafe")) {
            onTemiNavigation = new Thread(() -> onTemiNavigationGuidance("cafe", "cafe", "We have reached the cafe, have a nice day!"));
            onTemiNavigation.start();
        } else if (asrResult.equalsIgnoreCase("Bring me to the lift") || asrResult.equalsIgnoreCase("Where is the lift") || asrResult.equalsIgnoreCase("Lift") || asrResult.equalsIgnoreCase("Bring me to the elevator") || asrResult.equalsIgnoreCase("Where is the elevator") || asrResult.equalsIgnoreCase("Elevator")) {
            onTemiNavigation = new Thread(() -> onTemiNavigationGuidance(checkpoints[0], "lift", "We have reached the lift, have a nice day!"));
            onTemiNavigation.start();
        } else if (asrResult.equalsIgnoreCase("Bring me to the classroom") || asrResult.equalsIgnoreCase("Where is the classroom") || asrResult.equalsIgnoreCase("Classroom")) {
            onTemiNavigation = new Thread(() -> onTemiNavigationGuidance("classroom1", "classroom entrance hallway", "We have reached, to my left is a hallway leading to 3 different classrooms. Have a nice day!"));
            onTemiNavigation.start();
        } else if (asrResult.equalsIgnoreCase("Bring up food menu") || asrResult.equalsIgnoreCase("Food menu")) {
            doneConversation();
            launchBrowser();
        } else {
            robot.finishConversation();
            robot.speak(TtsRequest.create("Sorry I can't understand you, thank you for your time.", false));
            doneConversation();
        }
    }

    synchronized void onTemiNavigationGuidance(String place, String checkpoint, String speech) {
        onConversation = true;
        robot.speak(TtsRequest.create("Please follow me.", false));
        positionChanged = true;
        robot.goTo(place);
        previousCheckpoint = reachedCheckpoint;
        while (!reached || !goToCancelled) {
            reachedCheckpoint = null;
        }
        reached = false;
        if (goToCancelled) {
            goToCancelled = false;
            robot.finishConversation();
            askResume = true;
            resumePlace[0] = place;
            resumePlace[1] = checkpoint;
            resumePlace[2] = speech;
            robot.askQuestion("Travel was interrupted, do you want to resume?");
            return;
        }
        reachedCheckpoint = checkpoint;
        robot.speak(TtsRequest.create(speech, false));
        doneConversation();
    }

    void doneConversation() {
        robot.finishConversation();
        onConversation = false;
        onConversationDone = true;
    }

    @Override
    public void onTtsStatusChanged(@NotNull TtsRequest ttsRequest) {

    }

    @Override
    public void onDetectionStateChanged(int state) {
        if (state == OnDetectionStateChangedListener.DETECTED) {
            faceDetected = true;
        } else {
            faceDetected = false;
        }
    }

    static String getReachedCheckpoint() {
        return reachedCheckpoint;
    }

    static String getPreviousCheckpoint() {
        return previousCheckpoint;
    }

    static boolean getBrowserInfo() {
        return launchBrowser;
    }

    static String getPatrolStatus() {return Boolean.toString(patrol);}

    @Override
    public void onCurrentPositionChanged(@NotNull Position position) {
        Thread mark, direction;
        float toMinX = 0f, toMaxX = 1590f, fromMinX = -37.10f, fromMaxX = 20.15f;
        float toMinY = 0f, toMaxY = 445f, fromMinY = -3.44f, fromMaxY = 13.27f;
        float y = (position.getX() - fromMinY) * (toMaxY - toMinY) / (fromMaxY - fromMinY) + toMinY; // scale to ratio
        float x = (position.getY() - fromMinX) * (toMaxX - toMinX) / (fromMaxX - fromMinX) + toMinX; // scale to ratio
        float yaw = (position.getYaw() * -1) * (180 / 3.142f); // scale to degree
//        Log.i("Coord", position.toString());
        xPos = position.getX();
        yPos = position.getY();
        mark = new Thread(() -> setMarker(marker, x, y));
        direction = new Thread(() -> setPin(pin, yaw));
        mark.start();
        direction.start();
    }

    void setMarker(View marker, float x, float y) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ObjectAnimator markerX = ObjectAnimator.ofFloat(marker, "translationX", x);
                ObjectAnimator markerY = ObjectAnimator.ofFloat(marker, "translationY", y);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(markerX, markerY);
                set.setDuration(0);
                set.start();
            }
        });
    }

    void setPin(View pin, float yaw) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ObjectAnimator pinRotation = ObjectAnimator.ofFloat(pin, "rotation", yaw);
                pinRotation.setDuration(0);
                pinRotation.start();
            }
        });
    }

}