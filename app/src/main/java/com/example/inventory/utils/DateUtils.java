package com.example.inventory.utils;

import com.google.firebase.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DateUtils {
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final SimpleDateFormat displaySdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat timeSdf = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public static String getTodayKey() {
        return sdf.format(new Date());
    }

    public static String formatDate(Date date) {
        return sdf.format(date);
    }

    public static String getFormattedDate(Date date) {
        if (date == null) return "";
        return displaySdf.format(date);
    }

    public static String getRelativeTime(Timestamp soldAt) {
        if (soldAt == null) return "";
        long time = soldAt.toDate().getTime();
        long now = System.currentTimeMillis();

        if (isToday(soldAt)) {
            long diff = now - time;
            if (diff < 60000) return "Just now";
            if (diff < 3600000) return (diff / 60000) + " mins ago";
            return (diff / 3600000) + " hours ago";
        } else {
            return timeSdf.format(soldAt.toDate());
        }
    }

    public static String getDayLabel(Timestamp soldAt) {
        if (soldAt == null) return "";
        if (isToday(soldAt)) return "Today";
        if (isYesterday(soldAt)) return "Yesterday";
        return displaySdf.format(soldAt.toDate());
    }

    public static boolean isToday(Timestamp timestamp) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(timestamp.toDate());
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isYesterday(Timestamp timestamp) {
        Calendar cal1 = Calendar.getInstance();
        cal1.add(Calendar.DAY_OF_YEAR, -1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(timestamp.toDate());
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
}
