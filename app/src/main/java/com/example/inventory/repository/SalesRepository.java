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
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class SalesRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final String userId;

    public SalesRepository() {
        this.userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
    }

    private CollectionReference getSalesRef() {
        return db.collection("users").document(userId).collection("sales");
    }

    public void addSale(SaleModel sale) {
        String id = getSalesRef().document().getId();
        sale.setSaleId(id);
        getSalesRef().document(id).set(sale);
    }

    public LiveData<List<SaleModel>> getSalesHistory() {
        MutableLiveData<List<SaleModel>> salesList = new MutableLiveData<>();
        getSalesRef().orderBy("soldAt", Query.Direction.DESCENDING).addSnapshotListener((value, error) -> {
            if (value != null) {
                List<SaleModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    list.add(doc.toObject(SaleModel.class));
                }
                salesList.setValue(list);
            }
        });
        return salesList;
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

    public void voidSale(SaleModel sale, AnalyticsRepository analyticsRepository) {
        DocumentReference saleRef = getSalesRef().document(sale.getSaleId());
        DocumentReference productRef = db.collection("users").document(userId).collection("products").document(sale.getBarcode());
        
        db.runTransaction(transaction -> {
            DocumentSnapshot saleSnap = transaction.get(saleRef);
            if (!saleSnap.exists() || saleSnap.getBoolean("voided") == Boolean.TRUE) return null;

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
        });
    }
}
