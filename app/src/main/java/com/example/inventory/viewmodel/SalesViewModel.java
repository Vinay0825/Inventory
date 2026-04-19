
package com.example.inventory.viewmodel;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.inventory.model.ProductModel;
import com.example.inventory.model.RestockModel;
import com.example.inventory.model.SaleModel;
import com.example.inventory.repository.AnalyticsRepository;
import com.example.inventory.repository.SalesRepository;
import com.example.inventory.utils.DateUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SalesViewModel extends AndroidViewModel {
    private final FirebaseFirestore db;
    private final String userId;
    private final SalesRepository salesRepository;
    private final AnalyticsRepository analyticsRepository;
    private final MutableLiveData<String> filter = new MutableLiveData<>("all");
    private final MutableLiveData<List<SaleModel>> _salesHistory = new MutableLiveData<>();

    public SalesViewModel(@NonNull Application application) {
        super(application);
        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        salesRepository = new SalesRepository();
        analyticsRepository = new AnalyticsRepository();
        // Pre-load sales history immediately on ViewModel creation
        // so SalesListFragment doesn't get an empty list on first observe
        fetchSalesHistory(_salesHistory);
    }

    public void sellProduct(String barcode, double quantity, Context context,
                            Runnable onSuccess,
                            java.util.function.Consumer<String> onFailure) {
        if (userId == null) {
            onFailure.accept("User not logged in");
            return;
        }

        DocumentReference productRef = db.collection("users")
                .document(userId).collection("products").document(barcode);
        DocumentReference saleRef = db.collection("users")
                .document(userId).collection("sales").document();

        // Step 1: Read product first (requires internet for first read,
        // but uses cache if available offline)
        productRef.get(com.google.firebase.firestore.Source.CACHE)
                .continueWithTask(task -> {
                    if (task.isSuccessful() && task.getResult() != null
                            && task.getResult().exists()) {
                        return com.google.android.gms.tasks.Tasks.forResult(
                                task.getResult());
                    }
                    return productRef.get();
                })
                .addOnSuccessListener(productSnap -> {
                    if (!productSnap.exists()) {
                        onFailure.accept("Product not found");
                        return;
                    }
                    ProductModel product = productSnap.toObject(ProductModel.class);
                    if (product == null) {
                        onFailure.accept("Product data error");
                        return;
                    }

                    String unit = product.getUnit();
                    boolean isDecimal = ProductModel.isDecimalUnit(unit);
                    double currentStock = isDecimal
                            ? product.getCurrentStockDecimal()
                            : product.getCurrentStock();

                    if (currentStock < quantity) {
                        onFailure.accept("Insufficient stock");
                        return;
                    }

                    // Step 2: Write sale record (queued offline if no internet)
                    SaleModel sale = new SaleModel();
                    sale.setSaleId(saleRef.getId());
                    sale.setBarcode(barcode);
                    sale.setProductName(product.getName());
                    sale.setUnit(unit);
                    sale.setDayKey(com.example.inventory.utils.DateUtils.getTodayKey());
                    if (isDecimal) {
                        sale.setQuantitySoldDecimal(quantity);
                    } else {
                        sale.setQuantitySold((int) quantity);
                    }
                    sale.setPriceAtSale(product.getPrice());
                    sale.setTotalAmount(product.getPrice() * quantity);
                    sale.setSoldAt(com.google.firebase.Timestamp.now());
                    sale.setVoided(false);

                    // Step 3: Decrement stock
                    double newStock = Math.max(0.0, currentStock - quantity);
                    java.util.Map<String, Object> stockUpdate = new java.util.HashMap<>();
                    if (isDecimal) {
                        stockUpdate.put("currentStockDecimal", newStock);
                    } else {
                        stockUpdate.put("currentStock", (int) newStock);
                    }
                    stockUpdate.put("updatedAt", com.google.firebase.Timestamp.now());

                    // Step 4: Analytics update
                    DocumentReference analyticsRef = db.collection("users")
                            .document(userId).collection("analytics").document(barcode);
                    String dayKey = sale.getDayKey();
                    double totalAmount = sale.getTotalAmount();

                    // Fire all writes — these are queued by Firestore if offline
                    saleRef.set(sale);
                    productRef.update(stockUpdate);
                    analyticsRef.get().addOnSuccessListener(analyticsSnap -> {
                        if (analyticsSnap.exists()) {
                            analyticsRef.update(
                                    "totalUnitsSold",
                                    com.google.firebase.firestore.FieldValue.increment(quantity),
                                    "totalRevenue",
                                    com.google.firebase.firestore.FieldValue.increment(totalAmount),
                                    "salesCount",
                                    com.google.firebase.firestore.FieldValue.increment(1),
                                    "lastSoldAt",
                                    com.google.firebase.Timestamp.now(),
                                    "dailySales." + dayKey,
                                    com.google.firebase.firestore.FieldValue.increment(quantity)
                            );
                        } else {
                            com.example.inventory.model.AnalyticsModel model =
                                    new com.example.inventory.model.AnalyticsModel();
                            model.setBarcode(barcode);
                            model.setTotalUnitsSold(quantity);
                            model.setTotalRevenue(totalAmount);
                            model.setSalesCount(1);
                            model.setLastSoldAt(com.google.firebase.Timestamp.now());
                            java.util.Map<String, Object> daily = new java.util.HashMap<>();
                            daily.put(dayKey, quantity);
                            model.setDailySales(daily);
                            model.setWeeklySales(new java.util.HashMap<>());
                            analyticsRef.set(model);
                        }
                    });

                    // Notify success immediately — writes are queued
                    onSuccess.run();

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

                }).addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public void restockProduct(String barcode, double quantityRestocked,
                               Context context,
                               Runnable onSuccess,
                               java.util.function.Consumer<String> onFailure) {
        if (userId == null) { onFailure.accept("User not logged in"); return; }

        DocumentReference productRef = db.collection("users")
                .document(userId).collection("products").document(barcode);
        DocumentReference restockRef = db.collection("users")
                .document(userId).collection("restocks").document();

        productRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                onFailure.accept("Product not found"); return;
            }
            ProductModel product = snapshot.toObject(ProductModel.class);
            if (product == null) {
                onFailure.accept("Product data error"); return;
            }

            boolean isDecimal = ProductModel.isDecimalUnit(product.getUnit());
            double currentStock = isDecimal
                    ? product.getCurrentStockDecimal()
                    : product.getCurrentStock();
            double newStock = currentStock + quantityRestocked;

            // Write stock update (queued offline if needed)
            java.util.Map<String, Object> stockUpdate = new java.util.HashMap<>();
            if (isDecimal) {
                stockUpdate.put("currentStockDecimal", newStock);
            } else {
                stockUpdate.put("currentStock", (int) newStock);
            }
            stockUpdate.put("updatedAt", com.google.firebase.Timestamp.now());
            productRef.update(stockUpdate);

            // Write restock record
            com.example.inventory.model.RestockModel record =
                    new com.example.inventory.model.RestockModel();
            record.setRestockId(restockRef.getId());
            record.setBarcode(barcode);
            record.setProductName(product.getName());
            record.setQuantityAdded(quantityRestocked);
            record.setUnit(product.getUnit());
            record.setSupplier("");
            record.setRestockedAt(com.google.firebase.Timestamp.now());
            record.setVoided(false);
            restockRef.set(record);

            onSuccess.run();

        }).addOnFailureListener(e -> onFailure.accept(e.getMessage()));
    }

    public LiveData<List<SaleModel>> getSalesHistory() {
        return Transformations.switchMap(filter, f -> {
            return Transformations.map(_salesHistory, list -> {
                if (list == null) return new ArrayList<>();
                if ("all".equals(f)) return list;
                return list.stream().filter(sale -> {
                    if (sale.getSoldAt() == null) return false;
                    switch (f) {
                        case "today":
                            return DateUtils.isToday(sale.getSoldAt());
                        case "week":
                            Calendar weekCal = Calendar.getInstance();
                            // Set to Monday of current week
                            int dow = weekCal.get(Calendar.DAY_OF_WEEK);
                            int daysFromMonday = (dow == Calendar.SUNDAY) ? 6 : dow - Calendar.MONDAY;
                            weekCal.add(Calendar.DAY_OF_YEAR, -daysFromMonday);
                            weekCal.set(Calendar.HOUR_OF_DAY, 0);
                            weekCal.set(Calendar.MINUTE, 0);
                            weekCal.set(Calendar.SECOND, 0);
                            weekCal.set(Calendar.MILLISECOND, 0);
                            return sale.getSoldAt().toDate().after(weekCal.getTime());
                        case "month":
                            Calendar monthCal = Calendar.getInstance();
                            monthCal.set(Calendar.DAY_OF_MONTH, 1);
                            monthCal.set(Calendar.HOUR_OF_DAY, 0);
                            monthCal.set(Calendar.MINUTE, 0);
                            monthCal.set(Calendar.SECOND, 0);
                            monthCal.set(Calendar.MILLISECOND, 0);
                            return sale.getSoldAt().toDate().after(monthCal.getTime());
                        default:
                            return true;
                    }
                }).collect(Collectors.toList());
            });
        });
    }

    public void fetchSalesHistory(
            androidx.lifecycle.MutableLiveData<List<com.example.inventory.model.SaleModel>> target) {
        salesRepository.getSalesHistoryOnce(list -> {
            // Only post to _salesHistory — getSalesHistory() Transformations
            // will automatically filter and emit to all observers
            _salesHistory.postValue(list);
            // target is no longer used for summary — kept for compatibility
        });
    }

    public void setFilter(String filterType) {
        filter.setValue(filterType);
    }

    public LiveData<String> getCurrentFilter() {
        return filter;
    }

    public void voidSale(SaleModel sale) {
        salesRepository.voidSale(sale, analyticsRepository);
    }

    public void voidRestock(String restockId, String barcode,
                            double quantityToSubtract, String unit) {
        db.collection("users").document(userId)
                .collection("restocks").document(restockId)
                .update("voided", true)
                .addOnSuccessListener(aVoid ->
                        db.collection("users").document(userId)
                                .collection("products").document(barcode)
                                .get()
                                .addOnSuccessListener(snapshot -> {
                                    if (!snapshot.exists()) return;
                                    ProductModel product =
                                            snapshot.toObject(ProductModel.class);
                                    if (product == null) return;
                                    if (ProductModel.isDecimalUnit(unit)) {
                                        double newStock = Math.max(0,
                                                product.getCurrentStockDecimal()
                                                        - quantityToSubtract);
                                        db.collection("users").document(userId)
                                                .collection("products").document(barcode)
                                                .update("currentStockDecimal", newStock,
                                                        "updatedAt",
                                                        com.google.firebase.Timestamp.now());
                                    } else {
                                        int newStock = (int) Math.max(0,
                                                product.getCurrentStock() - quantityToSubtract);
                                        db.collection("users").document(userId)
                                                .collection("products").document(barcode)
                                                .update("currentStock", newStock,
                                                        "updatedAt",
                                                        com.google.firebase.Timestamp.now());
                                    }
                                }));
    }
}
