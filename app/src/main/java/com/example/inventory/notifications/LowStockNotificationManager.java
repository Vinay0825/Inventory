package com.example.inventory.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.inventory.MainActivity;
import com.example.inventory.R;

public class LowStockNotificationManager {
    public static final String CHANNEL_ID = "LOW_STOCK_ALERTS";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Low Stock Alerts";
            String description = "Alerts when product stock is low";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    public static void sendLowStockNotification(Context context, String productName, String stockDisplay) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("navigate_to", "low_stock_alerts");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Low Stock Alert")
                .setContentText(productName + " is running low! Stock: " + stockDisplay)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            // Handle permission error if needed
        }
    }

    // REPLACE entire scheduleDailyReminder method:
    public static void scheduleDailyReminder(Context context) {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 9);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        long delay = calendar.getTimeInMillis() - System.currentTimeMillis();

        androidx.work.PeriodicWorkRequest reminderWork =
                new androidx.work.PeriodicWorkRequest.Builder(
                        com.example.inventory.notifications.DailyReminderWorker.class,
                        1, java.util.concurrent.TimeUnit.DAYS)
                        .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .addTag("daily_reminder")
                        .build();

        androidx.work.WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "daily_low_stock_check",
                        androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                        reminderWork);
    }

    public static void sendDailySummaryNotification(Context context,
            int lowStockCount, double todayRevenue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
        }

        String SUMMARY_CHANNEL = "DAILY_SUMMARY";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                SUMMARY_CHANNEL, "Daily Summary",
                NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm =
                context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String msg = lowStockCount > 0
            ? lowStockCount + " items low on stock. Today's revenue: ₹"
                + String.format("%.2f", todayRevenue)
            : "All stock levels OK. Today's revenue: ₹"
                + String.format("%.2f", todayRevenue);

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, SUMMARY_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("ShopEase Pro — Daily Summary")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);

        try {
            NotificationManagerCompat.from(context)
                .notify(3001, builder.build());
        } catch (SecurityException e) { /* permission not granted */ }
    }
}
