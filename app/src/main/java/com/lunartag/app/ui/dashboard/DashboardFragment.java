package com.lunartag.app.ui.dashboard; 

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.lunartag.app.databinding.FragmentDashboardBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;

    // SharedPreferences to store the Shift State permanently
    private static final String PREFS_SHIFT = "LunarTagShiftPrefs";
    private static final String KEY_IS_SHIFT_ACTIVE = "is_shift_active";
    private static final String KEY_LAST_ACTION_TIME = "last_action_time";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Setup the RecyclerView for horizontal scrolling of recent photos
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        binding.recyclerViewRecentPhotos.setLayoutManager(layoutManager);

        // Set click listener for the shift toggle button
        binding.buttonToggleShift.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleShiftState();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // This method is called when the fragment becomes visible.
        // We load the current status and update the UI here.
        updateUI();
    }

    /**
     * Reads the current state from SharedPreferences and updates the Button and Text.
     */
    private void updateUI() {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_SHIFT, Context.MODE_PRIVATE);
        boolean isShiftActive = prefs.getBoolean(KEY_IS_SHIFT_ACTIVE, false);
        long lastActionTime = prefs.getLong(KEY_LAST_ACTION_TIME, 0);

        if (isShiftActive) {
            // Shift is currently running
            binding.buttonToggleShift.setText("End Shift");
            
            // Format the start time for display
            String timeStr = "";
            if (lastActionTime > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.US);
                timeStr = " since " + sdf.format(new Date(lastActionTime));
            }
            // Note: Assuming you have a TextView for status in your XML. 
            // If not, this acts as a fallback to ensure the button logic works.
            // binding.textShiftStatus.setText("On Duty" + timeStr);
            
        } else {
            // Shift is not running
            binding.buttonToggleShift.setText("Start Shift");
            // binding.textShiftStatus.setText("Off Duty");
        }

        // Here you would also add the logic to query the database for recent photos
        // and update the RecyclerView adapter.
    }

    /**
     * Handles the logic when the button is clicked.
     * Switches the state from On -> Off or Off -> On.
     */
    private void toggleShiftState() {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(PREFS_SHIFT, Context.MODE_PRIVATE);
        boolean isCurrentlyActive = prefs.getBoolean(KEY_IS_SHIFT_ACTIVE, false);
        SharedPreferences.Editor editor = prefs.edit();

        if (isCurrentlyActive) {
            // Logic to END the shift
            editor.putBoolean(KEY_IS_SHIFT_ACTIVE, false);
            editor.putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis());
            editor.apply();
            
            Toast.makeText(getContext(), "Shift Ended. Good job!", Toast.LENGTH_SHORT).show();
        } else {
            // Logic to START the shift
            editor.putBoolean(KEY_IS_SHIFT_ACTIVE, true);
            editor.putLong(KEY_LAST_ACTION_TIME, System.currentTimeMillis());
            editor.apply();
            
            Toast.makeText(getContext(), "Shift Started. Tracking active.", Toast.LENGTH_SHORT).show();
        }

        // Refresh the UI immediately
        updateUI();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Important to prevent memory leaks
    }
}