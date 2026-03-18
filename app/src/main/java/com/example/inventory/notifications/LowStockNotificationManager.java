package com.example.inventory.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

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

    public static void sendLowStockNotification(Context context, String productName, int remainingStock) {
        // Create an explicit intent for an Activity in your app. 
        // The requirement says "Tap action opens LowStockAlertsFragment". 
        // This is usually done via a deep link or an extra in MainActivity.
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("navigate_to", "low_stock_alerts");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Low Stock Alert")
                .setContentText(productName + " is running low! Only " + remainingStock + " left.")
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
}
