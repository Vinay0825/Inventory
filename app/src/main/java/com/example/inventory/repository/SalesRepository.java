package com.example.inventory.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.inventory.model.ProductModel;
import com.example.inventory.model.SaleModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class SalesRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final String userId;
    private final android.content.Context context;

    // Single shared LiveData + listener for sales history
    private final MutableLiveData<List<SaleModel>> salesLiveData =
        new MutableLiveData<>();
    private ListenerRegistration salesListener;

    public SalesRepository() {
        this.userId = auth.getCurrentUser() != null
            ? auth.getCurrentUser().getUid() : "unknown";
        this.context = com.example.inventory.App.getInstance()
            .getApplicationContext();
        attachSalesListener();
    }

    private CollectionReference getSalesRef() {
        return db.collection("users").document(userId).collection("sales");
    }

    private void attachSalesListener() {
        if (salesListener != null) salesListener.remove();
        salesListener = getSalesRef()
            .orderBy("soldAt", Query.Direction.DESCENDING)
            .addSnapshotListener((value, error) -> {
                if (value == null) return;
                List<SaleModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    SaleModel s = doc.toObject(SaleModel.class);
                    if (s != null) list.add(s);
                }
                salesLiveData.setValue(list);
            });
    }

    // Returns the single shared LiveData — same instance every call
    public LiveData<List<SaleModel>> getSalesHistory() {
        return salesLiveData;
    }

    public void getSalesHistoryOnce(com.google.android.gms.tasks.OnSuccessListener<List<SaleModel>> listener) {
        getSalesRef()
            .orderBy("soldAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshot -> {
                List<SaleModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    SaleModel sale = doc.toObject(SaleModel.class);
                    if (sale != null) list.add(sale);
                }
                listener.onSuccess(list);
            });
    }

    public LiveData<Double> getTodayTotal(String dayKey) {
        MutableLiveData<Double> todayTotal = new MutableLiveData<>();
        getSalesRef().whereEqualTo("dayKey", dayKey).whereEqualTo("voided", false).addSnapshotListener((value, error) -> {
            if (value != null) {
                double total = 0;
                for (DocumentSnapshot doc : value.getDocuments()) {
                    SaleModel sale = doc.toObject(SaleModel.class);
                    if (sale != null) {
                        total += sale.getTotalAmount();
                    }
                }
                todayTotal.setValue(total);
            }
        });
        return todayTotal;
    }

    public LiveData<Integer> getTodayTransactionCount(String dayKey) {
        MutableLiveData<Integer> count = new MutableLiveData<>();
        getSalesRef().whereEqualTo("dayKey", dayKey).whereEqualTo("voided", false).addSnapshotListener((value, error) -> {
            if (value != null) {
                count.setValue(value.size());
            }
        });
        return count;
    }

    public void voidSale(SaleModel sale,
                         AnalyticsRepository analyticsRepository) {
        DocumentReference saleRef =
            getSalesRef().document(sale.getSaleId());
        DocumentReference productRef = db.collection("users")
            .document(userId).collection("products")
            .document(sale.getBarcode());

        db.runTransaction(transaction -> {
            DocumentSnapshot saleSnap = transaction.get(saleRef);
            if (!saleSnap.exists()
                    || Boolean.TRUE.equals(saleSnap.getBoolean("voided")))
                return null;

            DocumentSnapshot productSnap = transaction.get(productRef);
            if (productSnap.exists()) {
                ProductModel product = productSnap.toObject(ProductModel.class);
                if (product != null) {
                    if (ProductModel.isDecimalUnit(product.getUnit())) {
                        double currentStock = productSnap.getDouble("currentStockDecimal");
                        transaction.update(productRef, "currentStockDecimal", currentStock + sale.getQuantitySoldDecimal());
                    } else {
                        long currentStock = productSnap.getLong("currentStock");
                        transaction.update(productRef, "currentStock", currentStock + sale.getQuantitySold());
                    }
                }
            }

            transaction.update(saleRef, "voided", true);
            
            // Negative increment to subtract from analytics
            double amountToSubtract = -sale.getTotalAmount();
            double quantityToSubtract = -(sale.getQuantitySoldDecimal() > 0 ? sale.getQuantitySoldDecimal() : sale.getQuantitySold());
            analyticsRepository.updateAnalytics(sale.getBarcode(), (int)quantityToSubtract, amountToSubtract, sale.getDayKey());

            return null;
        }).addOnSuccessListener(aVoid -> {
            // Post-sale low stock notification
            productRef.get().addOnSuccessListener(snap -> {
                if (snap == null || !snap.exists()) return;
                ProductModel updated = snap.toObject(ProductModel.class);
                if (updated != null &&
                        com.example.inventory.utils.StockRulesEngine.isLowStock(updated)) {
                    com.example.inventory.notifications.LowStockNotificationManager
                        .createNotificationChannel(context);
                    com.example.inventory.notifications.LowStockNotificationManager
                        .sendLowStockNotification(
                            context, updated.getName(), updated.getStockDisplay());
                }
            });
        });
    }
}
