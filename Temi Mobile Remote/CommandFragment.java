package com.example.temioperator;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Objects;

public class CommandFragment extends Fragment implements View.OnClickListener {

    Button btnGoToPos, btnGoTo, btnSaveLocation, btnSpeak, btnDeleteLocation, btnFollowMe;
    static boolean pressed = false;
    static String execute;
    EditText etX, etY, etYaw, etSaveLocation, etSpeak;
    static String x, y, yaw, value;
    View commandFragement;
    Spinner spinner;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getActivity().setTitle("Command Menu");
        return inflater.inflate(R.layout.fragment_command, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeButtons();
        initializeText();
        commandFragement = Objects.requireNonNull(getView()).findViewById(R.id.commandFragment);
        commandFragement.setOnTouchListener(new OnSwipeTouchListener(getContext()) {
            final FragmentManager fragmentManager = getFragmentManager();
            public void onSwipeRight() {
                assert fragmentManager != null;
                fragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
                        .add(R.id.fragment_container_view, new MenuFragment(), "menu")
                        .commit();
            }
            public void onSwipeLeft() {
                assert fragmentManager != null;
                fragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                        .add(R.id.fragment_container_view, new MapFragment(), "map")
                        .commit();
            }
        });
        if (getArguments() != null) {
            ArrayList<String> locations = getArguments().getStringArrayList("locations");
            locations.add("patrol");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.custom_spinner, locations);
            adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
            spinner.setAdapter(adapter);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    public void initializeButtons() {
        btnGoToPos = Objects.requireNonNull(getView()).findViewById(R.id.btnGoToPos);
        btnGoTo = getView().findViewById(R.id.btnGoTo);
        btnSaveLocation = getView().findViewById(R.id.btnSaveLocation);
        btnSpeak = getView().findViewById(R.id.btnSpeak);
        btnDeleteLocation = getView().findViewById(R.id.btnDeleteLocation);
        btnFollowMe = getView().findViewById((R.id.btnFollowMe));
        btnGoToPos.setOnClickListener(this);
        btnGoTo.setOnClickListener(this);
        btnSaveLocation.setOnClickListener(this);
        btnSpeak.setOnClickListener(this);
        btnDeleteLocation.setOnClickListener(this);
        btnFollowMe.setOnClickListener(this);
    }

    public void initializeText() {
        etX = Objects.requireNonNull(getView()).findViewById(R.id.etX);
        etY = getView().findViewById(R.id.etY);
        etYaw = getView().findViewById(R.id.etYaw);
        etSaveLocation = getView().findViewById(R.id.etSaveLocation);
        etSpeak = getView().findViewById(R.id.etSpeak);
        spinner = getView().findViewById(R.id.spinnerGoTo);
    }

    public static void setXYYaw(String txtX, String txtY, String txtYaw) {
        x = txtX;
        y = txtY;
        yaw = txtYaw;
        execute = "goToPos";
        pressed = true;
    }

    public static void setValue(String txt, String command) {
        value = txt;
        execute = command;
        pressed = true;
    }

    public void updateDropDownList(ArrayList<String> checkpoints){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), R.layout.custom_spinner, checkpoints);
                adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
                adapter.notifyDataSetChanged();
                spinner.setAdapter(adapter);
            }
        });
    }

    public static Float getX() {
        if(x.equals("")) {return 0f;}
        return Float.parseFloat(x);
    }

    public static Float getY() {
        if(y.equals("")) {return 0f;}
        return Float.parseFloat(y);
    }

    public static Float getYaw() {
        if(yaw.equals("")) {return 0f;}
        return Float.parseFloat(yaw);
    }

    public static String getValue() {return value;}

    public static boolean getButtonPressed() {
        return pressed;
    }

    public static String getExecution() {
        return execute;
    }


    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnGoToPos:
                setXYYaw(etX.getText().toString().trim(), etY.getText().toString().trim(), etYaw.getText().toString().trim());
                break;
            case R.id.btnGoTo:
                setValue(spinner.getSelectedItem().toString().trim(), "goTo");
                break;
            case R.id.btnDeleteLocation:
                setValue(spinner.getSelectedItem().toString().trim(), "deleteLocation");
                spinner.setSelection(0);
                break;
            case R.id.btnSaveLocation:
                setValue(etSaveLocation.getText().toString().trim(), "saveLocation");
                etSaveLocation.setText("");
                break;
            case R.id.btnSpeak:
                setValue(etSpeak.getText().toString().trim(),"speak");
                etSpeak.setText("");
                break;
            case R.id.btnFollowMe:
                setValue(null,"followMe");
                break;
            default:
                pressed = false;
                break;
        }
        if(pressed){pressed = false;}
    }

}
