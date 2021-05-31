package com.example.temioperator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    String SERVER_IP, dataOut;
    int SERVER_PORT;
    Thread commandListener = null;
    Thread getData = null;
    boolean interrupt = false, destroy = false, pause = false;
    volatile ArrayList<String> locations = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        setTheme(R.style.Theme_AppCompat_DayNight_DarkActionBar);
        setContentView(R.layout.activity_main);
        startView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(pause) {
            pause = false;
            new Thread(new ConnectionListener()).start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        new Thread(new SendData("end")).start();
        interrupt = true;
        destroy = true;
        pause = true;
    }

    public void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view == null) {
            view = new View(this);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void startView() {
        Fragment fragment = new ConnectFragment();
        if (interrupt) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getSupportFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
                            .replace(R.id.fragment_container_view, fragment, null)
                            .commit();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getSupportFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .replace(R.id.fragment_container_view, fragment, null)
                            .commit();
                }
            });
        }
        interrupt = false;
        new Thread(new ConnectionListener()).start();
    }

    @Override
    public void onBackPressed() {
        Fragment viewer = getSupportFragmentManager().findFragmentById(R.id.fragment_container_view);
        if (viewer instanceof MapFragment) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
                    .remove(Objects.requireNonNull(getSupportFragmentManager().findFragmentByTag("map")))
                    .commit();
            return;
        }
        if (viewer instanceof MenuFragment) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)
                    .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                    .remove(Objects.requireNonNull(getSupportFragmentManager().findFragmentByTag("menu")))
                    .commit();
            return;
        }
        if (viewer instanceof CommandFragment) {
            interrupt = true;
            new Thread(new SendData("end")).start();
            if (commandListener != null) {
                commandListener.interrupt();
            }
            if (getData != null) {
                getData.interrupt();
            }
            startView();
            return;
        }
        finishAndRemoveTask();
    }

    class ConnectionListener implements Runnable {

        @Override
        public void run() {
            while (!ConnectFragment.getButtonPressed()) {
                if (destroy) {
                    destroy = false;
                    return;
                }
            }
            loadingScreen();
            int timeout = 10000;
            try {
                SERVER_IP = ConnectFragment.getIP();
                SERVER_PORT = ConnectFragment.getPort();
                socket = new Socket();
                socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), timeout);
            } catch (UnknownHostException | SocketTimeoutException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
                    }
                });
                while (ConnectFragment.getButtonPressed()) {
                }
                startView();
                return;
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "Connection Error", Toast.LENGTH_SHORT).show();
                    }
                });
                while (ConnectFragment.getButtonPressed()) {
                }
                startView();
                return;
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                }
            });
            new Thread(new SendData("remote")).start();
            locations = null;
            getData = new Thread(new GetData());
            getData.start();
            while(locations == null){}
            commandView(locations);
        }

        private void loadingScreen() {
            hideKeyboard();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = new LoadingFragment();
                    getSupportFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                            .replace(R.id.fragment_container_view, fragment, null)
                            .commit();
                }
            });
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void commandView(ArrayList<String> locations) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("locations",locations);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = new CommandFragment();
                    fragment.setArguments(bundle);
                    getSupportFragmentManager().beginTransaction()
                            .setReorderingAllowed(true)
                            .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                            .replace(R.id.fragment_container_view, fragment, "command")
                            .commit();
                }
            });
            commandListener = new Thread(new CommandListener());
            commandListener.start();
        }
    }

    class CommandListener implements Runnable {

        @Override
        public void run() {
            while (!CommandFragment.getButtonPressed()) {
                if (interrupt) {
                    return;
                }
            }
            hideKeyboard();
            parseJson();
            while (CommandFragment.getButtonPressed()) {
            }
            commandListener = new Thread(new CommandListener());
            commandListener.start();
        }

        private void parseJson() {
            String execute = CommandFragment.getExecution();
            try {
                JSONObject jsonValue = new JSONObject();
                if (execute.equals("goToPos")) {
                    jsonValue.put("x", CommandFragment.getX());
                    jsonValue.put("y", CommandFragment.getY());
                    jsonValue.put("yaw", CommandFragment.getYaw());
                } else if (execute.equals("goTo")) {
                    jsonValue.put("desto", CommandFragment.getValue());
                } else if (execute.equals("saveLocation")) {
                    jsonValue.put("location", CommandFragment.getValue());
                } else if (execute.equals("speak")) {
                    jsonValue.put("say", CommandFragment.getValue());
                } else if (execute.equals("followMe")) {
                    jsonValue.put("",null);
                } else if (execute.equals("deleteLocation")) {
                    jsonValue.put("location", CommandFragment.getValue());
                }
                JSONObject jsonCommand = new JSONObject().put(execute, jsonValue);
                dataOut = jsonCommand.toString();
                new Thread(new SendData(dataOut)).start();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    class SendData implements Runnable {
        private final String message;

        SendData(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            try {
                PrintWriter output = new PrintWriter(socket.getOutputStream());
                output.println(message);
                output.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class GetData implements Runnable {

        float x, y;
        ImageView map,marker;

        @Override
        public void run() {
            try {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (!interrupt) {
                    final String message = input.readLine();
                    if (message != null) {
                        new Thread(() -> unPack(message)).start();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @SuppressLint("DefaultLocale")
        private void unPack(String message) {
            map = findViewById(R.id.map);
            marker = findViewById(R.id.marker);
            try {
                Fragment viewer = getSupportFragmentManager().findFragmentById(R.id.fragment_container_view);
                MenuFragment menuFragment = (MenuFragment) getSupportFragmentManager().findFragmentByTag("menu");
                MapFragment mapFragment = (MapFragment) getSupportFragmentManager().findFragmentByTag("map");
                CommandFragment commandFragment = (CommandFragment) getSupportFragmentManager().findFragmentByTag("command");
                JSONObject jsonObject = new JSONObject(message);
                if(jsonObject.has("locations")) {
                    JSONArray jsonArray = jsonObject.getJSONArray("locations");
                    locations = new ArrayList<>();
                    for(int i = 0; i < jsonArray.length(); i++) {
                        locations.add(jsonArray.getString(i));
                    }
                    if(viewer instanceof CommandFragment){
                        commandFragment.updateDropDownList(locations);
                    }
                }
                x = (float) jsonObject.getDouble("x");
                y = (float) jsonObject.getDouble("y");
                if (viewer instanceof MenuFragment) {
                    menuFragment.changeBatteryInfo(jsonObject.getString("battery")+"%");
                    menuFragment.changeChargingInfo(jsonObject.getString("charging"));
                    menuFragment.changeXInfo(String.format("%.2f", y));
                    menuFragment.changeYInfo(String.format("%.2f", x));
                    menuFragment.changePreviousCheckpointInfo(jsonObject.getString("previousCheckpoint"));
                    menuFragment.changeCheckpointReachedInfo(jsonObject.getString("checkpointReached"));
                    menuFragment.changePatrolStatusInfo(jsonObject.getString("patrolStatus"));
                }
                if (viewer instanceof MapFragment) {
                    float toMinX = (marker.getMeasuredHeight() / 4f), toMaxX = (float) map.getMeasuredHeight() - marker.getMeasuredHeight(), fromMinX = -37.10f, fromMaxX = 20.15f;
                    float toMinY = (float) map.getMeasuredWidth() / 5.1f, toMaxY = (float) map.getMeasuredWidth() - (marker.getMeasuredWidth() / 3.2f), fromMinY = 13.27f, fromMaxY = -3.44f;
                    float xScale = (x - fromMinX) * (toMaxX - toMinX) / (fromMaxX - fromMinX) + toMinX;
                    float yScale = (y - fromMinY) * (toMaxY - toMinY) / (fromMaxY - fromMinY) + toMinY;
                    mapFragment.runLocationMarker(xScale, yScale);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

}