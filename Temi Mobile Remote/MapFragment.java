package com.example.temioperator;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Objects;

public class MapFragment extends Fragment {

    View map, marker;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        map = Objects.requireNonNull(getView()).findViewById(R.id.mapView);
        marker = getView().findViewById(R.id.marker);
        map.setOnTouchListener(new OnSwipeTouchListener(getContext()) {
            final FragmentManager fragmentManager = getFragmentManager();

            public void onSwipeRight() {
                assert fragmentManager != null;
                fragmentManager.beginTransaction()
                        .setReorderingAllowed(true)
                        .setCustomAnimations(R.animator.slide_in_right, R.animator.slide_out_left, 0, 0)
                        .remove(MapFragment.this)
                        .commit();
            }
        });
    }

    public void runLocationMarker(float x, float y) {
        new Thread(() -> setMarker(x, y)).start();
    }

    private void setMarker(float x, float y) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ObjectAnimator markerX = ObjectAnimator.ofFloat(marker, "translationX", y);
                ObjectAnimator markerY = ObjectAnimator.ofFloat(marker, "translationY", x);

                AnimatorSet set = new AnimatorSet();
                set.playTogether(markerX, markerY);
                set.setDuration(0);
                set.start();
            }
        });
    }
}