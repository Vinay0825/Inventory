package com.example.inventory.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.inventory.MainActivity;
import com.example.inventory.R;
import com.google.firebase.auth.FirebaseAuth;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        TextView appName = findViewById(R.id.splashAppName);
        TextView tagline = findViewById(R.id.splashTagline);

        // Fade in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(800);
        fadeIn.setFillAfter(true);

        AlphaAnimation fadeInSlow = new AlphaAnimation(0f, 1f);
        fadeInSlow.setStartOffset(300);
        fadeInSlow.setDuration(800);
        fadeInSlow.setFillAfter(true);

        appName.startAnimation(fadeIn);
        tagline.startAnimation(fadeInSlow);

        // Navigate after 1.5 seconds
        appName.postDelayed(() -> {
            boolean isLoggedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
            Intent intent = new Intent(SplashActivity.this,
                    isLoggedIn ? MainActivity.class : LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }, 1500);
    }
}