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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentSettingsBinding;
import com.example.inventory.ui.auth.LoginActivity;
import com.example.inventory.utils.AppPrefs;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
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

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();

        // Show email
        if (user.getEmail() != null)
            binding.settingsUserEmail.setText(user.getEmail());

        // Load shop info
        db.collection("users").document(userId)
                .collection("settings").document("shopInfo")
                .get()
                .addOnSuccessListener(doc -> {
                    if (binding == null || doc == null) return;
                    String name = doc.getString("shopName");
                    String type = doc.getString("shopType");
                    if (name != null && !name.isEmpty()) {
                        binding.settingsShopName.setText(name);
                        binding.shopNameValue.setText(name);
                        binding.settingsAvatarInitial.setText(
                                String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault()));
                    }
                    if (type != null) binding.settingsShopType.setText(type);
                    binding.shopTypeValue.setText(type);
                });

        // Edit shop name
        binding.editShopNameButton.setOnClickListener(v -> {
            android.widget.EditText input =
                    new android.widget.EditText(requireContext());
            input.setText(binding.settingsShopName.getText());
            input.setHint("Enter shop name");
            input.setPadding(48, 24, 48, 24);

            new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Edit Shop Name")
                    .setView(input)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newName = input.getText().toString().trim();
                        if (newName.isEmpty()) return;
                        db.collection("users").document(userId)
                                .collection("settings").document("shopInfo")
                                .update("shopName", newName)
                                .addOnSuccessListener(aVoid -> {
                                    if (binding == null) return;
                                    binding.settingsShopName.setText(newName);
                                    binding.shopNameValue.setText(newName);
                                    binding.settingsAvatarInitial.setText(
                                            String.valueOf(newName.charAt(0))
                                                    .toUpperCase(Locale.getDefault()));
                                    android.widget.Toast.makeText(getContext(),
                                            "Shop name updated",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Other settings initialization
        mAuth = FirebaseAuth.getInstance();
        prefs = requireActivity().getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE);
        
        binding.logoutButton.setOnClickListener(v -> logout());

        setupThemeToggles();
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

    private void logout() {
        if (!isAdded() || getActivity() == null) return;

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