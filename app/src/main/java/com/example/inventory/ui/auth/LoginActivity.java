package com.example.inventory.ui.auth;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.inventory.MainActivity;
import com.example.inventory.databinding.ActivityLoginBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private ActivityLoginBinding binding;
    private FirebaseAuth mAuth;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
            com.example.inventory.utils.LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().isEmailVerified()) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        binding.loginButton.setOnClickListener(v -> loginUser());
        binding.forgotPasswordButton.setOnClickListener(v -> handleForgotPassword());
        binding.goToRegisterButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });
    }

    private void loginUser() {
        String email = binding.emailEditText.getText().toString().trim();
        String password = binding.passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            binding.emailEditText.setError("Email is required");
            return;
        }

        if (TextUtils.isEmpty(password)) {
            binding.passwordEditText.setError("Password is required");
            return;
        }

        binding.loginButton.setEnabled(false);
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && !user.isEmailVerified()) {
                            mAuth.signOut();
                            new android.app.AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("Email Not Verified")
                                    .setMessage("Please verify your email before logging in. Check your inbox.")
                                    .setPositiveButton("Resend Email", (dialog, which) -> {
                                        mAuth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener(result -> {
                                                    result.getUser().sendEmailVerification();
                                                    Toast.makeText(LoginActivity.this, "Verification email resent.", Toast.LENGTH_SHORT).show();
                                                    mAuth.signOut();
                                                });
                                    })
                                    .setNegativeButton("OK", null)
                                    .show();
                            binding.loginButton.setEnabled(true);
                        } else {
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        }
                    } else {
                        Toast.makeText(LoginActivity.this, "Authentication failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                        binding.loginButton.setEnabled(true);
                    }
                });
    }

    private void handleForgotPassword() {
        String email = binding.emailEditText.getText().toString().trim();
        if (email.isEmpty()) {
            binding.emailEditText.setError("Enter your email first");
            binding.emailEditText.requestFocus();
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailEditText.setError("Enter a valid email address");
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> {
                    new android.app.AlertDialog.Builder(LoginActivity.this)
                            .setTitle("Reset Email Sent")
                            .setMessage("A password reset link has been sent to " + email +
                                    ". Open it from your Gmail to reset your password.")
                            .setPositiveButton("OK", null)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LoginActivity.this,
                            "Failed to send reset email: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }
}
