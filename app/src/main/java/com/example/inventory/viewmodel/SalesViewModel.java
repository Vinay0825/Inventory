package com.example.inventory.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.inventory.model.ProductModel;
import com.example.inventory.model.SaleModel;
import com.example.inventory.notifications.LowStockNotificationManager;
import com.example.inventory.repository.AnalyticsRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.SalesRepository;
import com.example.inventory.utils.DateUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class SalesViewModel extends AndroidViewModel {
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final AnalyticsRepository analyticsRepository;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final String userId;

    public SalesViewModel(@NonNull Application application) {
        super(application);
        this.salesRepository = new SalesRepository();
        this.productRepository = new ProductRepository();
        this.analyticsRepository = new AnalyticsRepository();
        this.userId = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "unknown";
    }

    public LiveData<List<SaleModel>> getSalesHistory() {
        return salesRepository.getSalesHistory();
    }

    public LiveData<Double> getTodayTotal() {
        return salesRepository.getTodayTotal(DateUtils.getTodayKey());
    }

    public LiveData<Integer> getTodayTransactionCount() {
        return salesRepository.getTodayTransactionCount(DateUtils.getTodayKey());
    }

    public void voidSale(SaleModel sale) {
        salesRepository.voidSale(sale, analyticsRepository);
    }

    public void sellProduct(String barcode, double quantitySold, Context context) {
        DocumentReference productRef = db.collection("users").document(userId).collection("products").document(barcode);
        
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(productRef);
            if (!snapshot.exists()) {
                throw new RuntimeException("Product not found");
            }

            ProductModel product = snapshot.toObject(ProductModel.class);
            if (product == null) throw new RuntimeException("Product data is null");

            double currentStock = ProductModel.isDecimalUnit(product.getUnit()) ? product.getCurrentStockDecimal() : product.getCurrentStock();
            if (quantitySold > currentStock) {
                throw new RuntimeException("Insufficient stock");
            }

            double newStock = currentStock - quantitySold;
            if (ProductModel.isDecimalUnit(product.getUnit())) {
                transaction.update(productRef, "currentStockDecimal", newStock, "updatedAt", Timestamp.now());
            } else {
                transaction.update(productRef, "currentStock", (int)newStock, "updatedAt", Timestamp.now());
            }

            String dayKey = DateUtils.getTodayKey();
            double totalAmount = product.getPrice() * quantitySold;

            SaleModel sale = new SaleModel();
            sale.setBarcode(barcode);
            sale.setProductName(product.getName());
            if (ProductModel.isDecimalUnit(product.getUnit())) {
                sale.setQuantitySoldDecimal(quantitySold);
            } else {
                sale.setQuantitySold((int)quantitySold);
            }
            sale.setPriceAtSale(product.getPrice());
            sale.setTotalAmount(totalAmount);
            sale.setDayKey(dayKey);
            sale.setSoldAt(Timestamp.now());
            sale.setVoided(false);

            DocumentReference saleRef = db.collection("users").document(userId).collection("sales").document();
            sale.setSaleId(saleRef.getId());
            transaction.set(saleRef, sale);

            analyticsRepository.updateAnalytics(barcode, (int)quantitySold, totalAmount, dayKey);

            if (newStock < product.getLowStockThreshold()) {
                LowStockNotificationManager.sendLowStockNotification(context, product.getName(), (int)newStock);
            }

            return null;
        }).addOnFailureListener(e -> {
            // Handle error
        });
    }

    public void restockProduct(String barcode, double quantityRestocked, Context context) {
        DocumentReference productRef = db.collection("users").document(userId).collection("products").document(barcode);
        
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(productRef);
            if (!snapshot.exists()) {
                throw new RuntimeException("Product not found");
            }

            ProductModel product = snapshot.toObject(ProductModel.class);
            if (product == null) throw new RuntimeException("Product data is null");

            double currentStock = ProductModel.isDecimalUnit(product.getUnit()) ? product.getCurrentStockDecimal() : product.getCurrentStock();
            double newStock = currentStock + quantityRestocked;

            if (ProductModel.isDecimalUnit(product.getUnit())) {
                transaction.update(productRef, "currentStockDecimal", newStock, "updatedAt", Timestamp.now());
            } else {
                transaction.update(productRef, "currentStock", (int)newStock, "updatedAt", Timestamp.now());
            }

            return null;
        });
    }
}
