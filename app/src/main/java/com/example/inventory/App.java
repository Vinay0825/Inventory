package com.example.inventory;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public class App extends Application {
    private static App instance;

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        // Apply saved theme before any Activity starts
        int savedTheme = PreferenceManager
            .getDefaultSharedPreferences(this)
            .getInt("app_theme",
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(savedTheme);
        androidx.work.WorkManager.getInstance(this).cancelAllWorkByTag("daily_reminder");
        com.example.inventory.notifications.LowStockNotificationManager.scheduleDailyReminder(this);
    }
}
