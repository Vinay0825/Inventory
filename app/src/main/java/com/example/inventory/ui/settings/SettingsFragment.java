package com.example.inventory.ui.settings;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentSettingsBinding;
import com.example.inventory.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;
    private boolean isSettingUp = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        prefs = requireActivity().getSharedPreferences("ShopMatePrefs", Context.MODE_PRIVATE);

        loadSettings();

        binding.logoutButton.setOnClickListener(v -> logout());
        binding.changePasswordButton.setOnClickListener(v -> showChangePasswordDialog());
        binding.saveThresholdButton.setOnClickListener(v -> savePreferences());

        setupThemeToggles();
    }

    private void loadSettings() {
        if (mAuth.getCurrentUser() != null) {
            binding.settingsEmailDisplay.setText(mAuth.getCurrentUser().getEmail());
        }

        int threshold = prefs.getInt("global_low_stock_threshold", 5);
        binding.settingsGlobalThreshold.setText(String.valueOf(threshold));
    }

    private void savePreferences() {
        String val = binding.settingsGlobalThreshold.getText().toString().trim();
        if (!val.isEmpty()) {
            prefs.edit().putInt("global_low_stock_threshold", Integer.parseInt(val)).apply();
            Toast.makeText(getContext(), "Preferences saved", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupThemeToggles() {
        isSettingUp = true;
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        int savedTheme = sharedPrefs.getInt("app_theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        if (savedTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.themeToggleGroup.check(R.id.themeLight);
        } else if (savedTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.themeToggleGroup.check(R.id.themeDark);
        } else {
            binding.themeToggleGroup.check(R.id.themeSystem);
        }
        isSettingUp = false;

        binding.themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || isSettingUp) return;
            int mode;
            if (checkedId == R.id.themeLight) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.themeDark) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }
            sharedPrefs.edit().putInt("app_theme", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });
    }

    private void showChangePasswordDialog() {
        if (!isAdded() || getActivity() == null) return;

        final EditText passwordInput = new EditText(requireActivity());
        passwordInput.setHint("New Password");
        passwordInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setPadding(32, 32, 32, 32);

        // FIX: Plain AlertDialog.Builder — MaterialAlertDialogBuilder crashes on MIUI with custom themes
        new AlertDialog.Builder(requireActivity())
                .setTitle("Change Password")
                .setView(passwordInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPass = passwordInput.getText().toString().trim();
                    if (newPass.length() >= 6) {
                        if (mAuth.getCurrentUser() != null) {
                            mAuth.getCurrentUser().updatePassword(newPass)
                                    .addOnCompleteListener(task -> {
                                        if (!isAdded()) return;
                                        if (task.isSuccessful()) {
                                            Toast.makeText(getContext(), "Password updated", Toast.LENGTH_SHORT).show();
                                        } else {
                                            Toast.makeText(getContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(getContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void logout() {
        if (!isAdded() || getActivity() == null) return;

        // FIX: Plain AlertDialog.Builder — MaterialAlertDialogBuilder crashes on MIUI with custom themes
        new AlertDialog.Builder(requireActivity())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    mAuth.signOut();
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}