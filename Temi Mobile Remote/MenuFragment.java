package com.example.temioperator;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Objects;

public class MenuFragment extends Fragment{

    View menuFragement;
    TextView xInfo, yInfo, batteryInfo, chargingInfo, previousCheckpointInfo, checkpointReachedInfo, patrolStatusInfo;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        menuFragement = Objects.requireNonNull(getView()).findViewById(R.id.menuFragment);
        menuFragement.setOnTouchListener(new OnSwipeTouchListener(getContext()) {
            final FragmentManager fragmentManager = getFragmentManager();
            public void onSwipeLeft() {
                assert fragmentManager != null;
                fragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_right, 0, 0)
                        .remove(MenuFragment.this)
                        .commit();
            }
        });
        initializeTV();
    }

    private void initializeTV(){
        xInfo = Objects.requireNonNull(getView()).findViewById(R.id.xInfo);
        yInfo = getView().findViewById(R.id.yInfo);
        batteryInfo = getView().findViewById(R.id.batteryInfo);
        chargingInfo = getView().findViewById(R.id.chargingInfo);
        previousCheckpointInfo = getView().findViewById(R.id.previousCheckpointInfo);
        checkpointReachedInfo = getView().findViewById(R.id.checkpointReachedInfo);
        patrolStatusInfo = getView().findViewById(R.id.patrolStatusInfo);
    }

    public void changeXInfo(String newText) {
        xInfo.setText(newText);
    }

    public void changeYInfo(String newText) {
        yInfo.setText(newText);
    }

    public void changeBatteryInfo(String newText) {
        batteryInfo.setText(newText);
    }

    public void changeChargingInfo(String newText) {
        chargingInfo.setText(newText);
    }

    public void changePatrolStatusInfo(String newText) {
        patrolStatusInfo.setText(newText);
    }

    public void changePreviousCheckpointInfo(String newText) {
        previousCheckpointInfo.setText(newText);
    }

    public void changeCheckpointReachedInfo(String newText) {
        checkpointReachedInfo.setText(newText);
    }

}