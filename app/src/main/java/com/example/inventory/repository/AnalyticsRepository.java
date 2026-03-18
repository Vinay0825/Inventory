package com.example.inventory.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.inventory.model.AnalyticsModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final String userId;

    public AnalyticsRepository() {
        this.userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
    }

    private CollectionReference getAnalyticsRef() {
        return db.collection("users").document(userId).collection("analytics");
    }

    public void updateAnalytics(String barcode, int quantitySold, double totalAmount, String dayKey) {
        DocumentReference ref = getAnalyticsRef().document(barcode);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                AnalyticsModel model = new AnalyticsModel();
                model.setBarcode(barcode);
                model.setTotalUnitsSold(quantitySold);
                model.setTotalRevenue(totalAmount);
                model.setSalesCount(1);
                model.setLastSoldAt(Timestamp.now());
                Map<String, Integer> daily = new HashMap<>();
                daily.put(dayKey, quantitySold);
                model.setDailySales(daily);
                model.setWeeklySales(new HashMap<>());
                transaction.set(ref, model);
            } else {
                transaction.update(ref, "totalUnitsSold", FieldValue.increment(quantitySold));
                transaction.update(ref, "totalRevenue", FieldValue.increment(totalAmount));
                transaction.update(ref, "salesCount", FieldValue.increment(1));
                transaction.update(ref, "lastSoldAt", Timestamp.now());
                transaction.update(ref, "dailySales." + dayKey, FieldValue.increment(quantitySold));
            }
            return null;
        });
    }

    public LiveData<AnalyticsModel> getAnalytics(String barcode) {
        MutableLiveData<AnalyticsModel> data = new MutableLiveData<>();
        getAnalyticsRef().document(barcode).addSnapshotListener((value, error) -> {
            if (value != null && value.exists()) {
                data.setValue(value.toObject(AnalyticsModel.class));
            }
        });
        return data;
    }

    public LiveData<List<AnalyticsModel>> getAllAnalytics() {
        MutableLiveData<List<AnalyticsModel>> analyticsLiveData = new MutableLiveData<>();
        db.collection("users").document(userId)
            .collection("analytics")
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) return;
                List<AnalyticsModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    AnalyticsModel model = doc.toObject(AnalyticsModel.class);
                    if (model != null) list.add(model);
                }
                analyticsLiveData.setValue(new ArrayList<>(list));
            });
        return analyticsLiveData;
    }

    public LiveData<List<AnalyticsModel>> getBestSellers() {
        MutableLiveData<List<AnalyticsModel>> analyticsLiveData = new MutableLiveData<>();
        db.collection("users").document(userId)
            .collection("analytics")
            .orderBy("totalUnitsSold", Query.Direction.DESCENDING).limit(10)
            .addSnapshotListener((snapshots, error) -> {
                if (error != null || snapshots == null) return;
                List<AnalyticsModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    AnalyticsModel model = doc.toObject(AnalyticsModel.class);
                    if (model != null) list.add(model);
                }
                analyticsLiveData.setValue(new ArrayList<>(list));
            });
        return analyticsLiveData;
    }
}
