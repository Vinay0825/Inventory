package com.example.inventory.notifications;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.inventory.model.ProductModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class DailyReminderWorker extends Worker {

    public DailyReminderWorker(@NonNull Context context,
                                @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null)
            return Result.success();

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Synchronous Firestore fetch using Tasks.await
        try {
            com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>
                productsTask = db.collection("users").document(userId)
                    .collection("products").get();
            com.google.firebase.firestore.QuerySnapshot productSnap =
                com.google.android.gms.tasks.Tasks.await(productsTask);

            int lowStockCount = 0;
            for (QueryDocumentSnapshot doc : productSnap) {
                ProductModel p = doc.toObject(ProductModel.class);
                if (p == null) continue;
                
                double effectiveStock = (p.getUnit() != null && 
                    (p.getUnit().equalsIgnoreCase("kg") || p.getUnit().equalsIgnoreCase("g") ||
                     p.getUnit().equalsIgnoreCase("l") || p.getUnit().equalsIgnoreCase("ml")))
                    ? p.getCurrentStockDecimal() : (double) p.getCurrentStock();

                // WITH:
                int threshold = (p.getLowStockThreshold() > 0)
                        ? p.getLowStockThreshold()
                        : com.example.inventory.utils.AppPrefs.DEFAULT_THRESHOLD;
                if (effectiveStock <= threshold) {
                    lowStockCount++;
                    LowStockNotificationManager.sendLowStockNotification(
                            getApplicationContext(),
                            p.getName(),
                            p.getStockDisplay());
                }
            }

            // Today's revenue
            String todayKey = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
            com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>
                salesTask = db.collection("users").document(userId)
                    .collection("sales")
                    .whereEqualTo("dayKey", todayKey)
                    .whereEqualTo("voided", false).get();
            com.google.firebase.firestore.QuerySnapshot salesSnap =
                com.google.android.gms.tasks.Tasks.await(salesTask);

            double todayRevenue = 0;
            for (QueryDocumentSnapshot doc : salesSnap) {
                Double amt = doc.getDouble("totalAmount");
                if (amt != null) todayRevenue += amt;
            }

            LowStockNotificationManager.sendDailySummaryNotification(
                getApplicationContext(), lowStockCount, todayRevenue);

            // Reschedule for next day
            LowStockNotificationManager.scheduleDailyReminder(
                getApplicationContext());

        } catch (Exception e) {
            return Result.retry();
        }

        return Result.success();
    }
}
