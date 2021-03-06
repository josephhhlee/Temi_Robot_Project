package com.cyberpunk.temiiotproject;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.navigation.listener.OnCurrentPositionChangedListener;
import com.robotemi.sdk.navigation.model.Position;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class CallOut extends Service implements OnCurrentPositionChangedListener {

    public static final int SERVERPORT = 0; //Enter Server's Port
    public static final String SERVER_IP = ""; //Enter Server's IP

    private ClientThread clientThread;
    private Thread thread;

    String currentPos;

    @Override
    public void onCreate(){
        int delay = 5000;
        int period = 500*9;
        Timer timer = new Timer();

        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                onSchedule();
            }
        }, delay, period);
        Robot.getInstance().addOnCurrentPositionChangedListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void onSchedule() {
        clientThread = new ClientThread();
        thread = new Thread(clientThread);
        thread.start();
        SystemClock.sleep(100);
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (null != clientThread) {
            clientThread.sendMessage(sendJSONData());
        }
    }

    class ClientThread implements Runnable {
        private Socket socket;
        private BufferedReader input;
        @Override
        public void run() {
            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                socket = new Socket(serverAddr, SERVERPORT);
                while (!Thread.currentThread().isInterrupted()) {
                    this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    String message = input.readLine();
                    if (null == message || "Disconnect".contentEquals(message)) {
                        break;
                    }
                }
            } catch (UnknownHostException  e1) {
                e1.printStackTrace();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        void sendMessage(final String message) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (null != socket) {
                            PrintWriter out = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream())),
                                    true);
                            out.println(message);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    public String sendJSONData() {
        String[] batteryInfo = getJsonBatteryData();
        JSONObject sendData = new JSONObject();
        String reachedCheckpoint = MainActivity.getReachedCheckpoint();
        String previousCheckpoint = MainActivity.getPreviousCheckpoint();
        String patrol = MainActivity.getPatrolStatus();
        try {
            sendData.put("id", 1);
            sendData.put("battery_strength", batteryInfo[0]);
            sendData.put("charging_status", batteryInfo[1]);
            sendData.put("current_location", currentPos);
            sendData.put("checkpoint_reached", reachedCheckpoint);
            sendData.put("previous_checkpoint", previousCheckpoint);
            sendData.put("patrol_status", patrol);
            return sendData.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sendData.toString();
    }

    public static String[] getJsonBatteryData() {
        BatteryData batteryData = Robot.getInstance().getBatteryData();
        String[] batteryInfo = new String[2];
        if (batteryData == null) {
            batteryInfo[0] = "null";
            batteryInfo[1] = "null";
        }
        else if (batteryData.isCharging()) {
            batteryInfo[0] = String.valueOf(batteryData.getBatteryPercentage());
            batteryInfo[1] = "true";
        }
        else {
            batteryInfo[0] = String.valueOf(batteryData.getBatteryPercentage());
            batteryInfo[1] = "false";
        }
        return batteryInfo;
    }

    @Override
    public void onCurrentPositionChanged(@NotNull Position position) {
        float x, y, yaw;
        x = position.getX();
        y = position.getY();
        yaw = position.getYaw();
        currentPos = "X = "+x+", Y = "+y+", Yaw = "+yaw;
    }

}