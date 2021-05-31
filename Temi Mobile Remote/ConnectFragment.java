package com.example.temioperator;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import java.util.Objects;

public class ConnectFragment extends Fragment {

    Button btnConnect;
    EditText etPort, etIP;
    static boolean connect = false;
    static String hostIP;
    static int hostPort;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle("Temi Remote");
        return inflater.inflate(R.layout.fragment_connect, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        etIP = Objects.requireNonNull(getView()).findViewById(R.id.etIP);
        etPort = Objects.requireNonNull(getView()).findViewById(R.id.etPort);
        btnConnect = getView().findViewById(R.id.btnConnect);
        btnConnect.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (!connect) {
                            connect = true;
                        }
                        setValues(etIP.getText().toString().trim(), etPort.getText().toString().trim());
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (connect) {
                            connect = false;
                        }
                        return true;
                }
                return false;
            }
        });
    }

    static public void setValues(String ip, String port) {
        hostIP = ip;
        if(port.equals("")){
            hostPort = 0;
            return;
        }
        hostPort = Integer.parseInt(port);
    }

    static public boolean getButtonPressed() {
        return connect;
    }

    static public String getIP() {
        return hostIP;
    }

    static public int getPort() {
        return hostPort;
    }
}