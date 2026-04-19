package com.example.inventory.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.inventory.databinding.ActivityRegisterBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
            com.example.inventory.utils.LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupShopTypeDropdown();

        binding.registerButton.setOnClickListener(v -> registerUser());
        binding.goToLoginButton.setOnClickListener(v -> finish());
    }

    private void setupShopTypeDropdown() {
        String[] shopTypes = {
                "Kirana / General Store",
                "Medical / Pharmacy",
                "Bakery / Food Shop",
                "Electronics Shop",
                "Clothing / Textile",
                "Other / Custom"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, shopTypes);
        binding.shopTypeDropdown.setAdapter(adapter);
    }

    private void registerUser() {
        String shopName = binding.shopNameEditText.getText().toString().trim();
        String shopType = binding.shopTypeDropdown.getText().toString().trim();
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(shopName)) {
            binding.shopNameEditText.setError("Shop name is required");
            return;
        }

        if (TextUtils.isEmpty(shopType)) {
            binding.shopTypeDropdown.setError("Shop type is required");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            binding.emailEditText.setError("Email is required");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.setError("Please enter a valid email address");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.passwordEditText.setError("Password is required");
            return;
        }

        if (password.length() < 6) {
            binding.passwordEditText.setError("Password must be at least 6 characters");
            return;
        }

        binding.registerButton.setEnabled(false);
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        com.google.firebase.auth.FirebaseUser user =
                            mAuth.getCurrentUser();
                        if (user != null) {
                            // Save shop info IMMEDIATELY after account
                            // creation — before email verification
                            // so it's never lost
                            saveShopSettingsAndNavigate(shopName, shopType);
                            // Send verification email in background
                            user.sendEmailVerification()
                                .addOnSuccessListener(aVoid ->
                                    Toast.makeText(RegisterActivity.this,
                                        "Verification email sent. "
                                        + "Please verify before logging in.",
                                        Toast.LENGTH_LONG).show())
                                .addOnFailureListener(e ->
                                    Toast.makeText(RegisterActivity.this,
                                        "Account created. Check email for "
                                        + "verification link.",
                                        Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this,
                            "Registration failed: "
                            + task.getException().getMessage(),
                            Toast.LENGTH_SHORT).show();
                        binding.registerButton.setEnabled(true);
                    }
                });
    }

    private void saveShopSettingsAndNavigate(String shopName, String shopType) {
        String userId = mAuth.getUid();
        if (userId == null) return;

        Map<String, Object> settings = new HashMap<>();
        settings.put("shopName", shopName);
        settings.put("shopType", shopType);

        db.collection("users").document(userId).collection("settings").document("shopInfo")
                .set(settings)
                .addOnCompleteListener(task -> {
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                });
    }
}
