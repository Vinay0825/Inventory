package com.example.inventory.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.inventory.model.ProductModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ProductRepository {
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final String userId;

    public ProductRepository() {
        this.userId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "unknown";
    }

    private CollectionReference getProductsRef() {
        return db.collection("users").document(userId).collection("products");
    }

    public void addProduct(ProductModel product) {
        if (product.getBarcode() != null) {
            getProductsRef().document(product.getBarcode()).set(product);
        }
    }

    public void updateProduct(ProductModel product) {
        if (product.getBarcode() != null) {
            product.setUpdatedAt(Timestamp.now());
            getProductsRef().document(product.getBarcode()).set(product);
        }
    }

    public void deleteProduct(String barcode) {
        getProductsRef().document(barcode).delete();
    }

    public LiveData<ProductModel> getProduct(String barcode) {
        MutableLiveData<ProductModel> productData = new MutableLiveData<>();
        getProductsRef().document(barcode).addSnapshotListener((value, error) -> {
            if (value != null && value.exists()) {
                productData.setValue(value.toObject(ProductModel.class));
            } else {
                productData.setValue(null);
            }
        });
        return productData;
    }

    public LiveData<List<ProductModel>> getAllProducts() {
        MutableLiveData<List<ProductModel>> productsList = new MutableLiveData<>();
        getProductsRef().orderBy("name").addSnapshotListener((value, error) -> {
            if (value != null) {
                List<ProductModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    list.add(doc.toObject(ProductModel.class));
                }
                productsList.setValue(list);
            }
        });
        return productsList;
    }

    public void updateStock(String barcode, int newStock) {
        getProductsRef().document(barcode).update("currentStock", newStock, "updatedAt", Timestamp.now());
    }

    public void updateStockDecimal(String barcode, double newStock) {
        getProductsRef().document(barcode).update("currentStockDecimal", newStock, "updatedAt", Timestamp.now());
    }

    public LiveData<List<ProductModel>> searchProducts(String query) {
        MutableLiveData<List<ProductModel>> productsList = new MutableLiveData<>();
        getProductsRef().orderBy("name").startAt(query).endAt(query + "\uf8ff").addSnapshotListener((value, error) -> {
            if (value != null) {
                List<ProductModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    list.add(doc.toObject(ProductModel.class));
                }
                productsList.setValue(list);
            }
        });
        return productsList;
    }

    public LiveData<List<ProductModel>> getLowStockProducts() {
        MutableLiveData<List<ProductModel>> productsList = new MutableLiveData<>();
        getProductsRef().addSnapshotListener((value, error) -> {
            if (value != null) {
                List<ProductModel> list = new ArrayList<>();
                for (DocumentSnapshot doc : value.getDocuments()) {
                    ProductModel p = doc.toObject(ProductModel.class);
                    if (p != null) {
                        boolean isLow = false;
                        if (ProductModel.isDecimalUnit(p.getUnit())) {
                            isLow = p.getCurrentStockDecimal() < p.getLowStockThreshold();
                        } else {
                            isLow = p.getCurrentStock() < p.getLowStockThreshold();
                        }
                        if (isLow) list.add(p);
                    }
                }
                productsList.setValue(list);
            }
        });
        return productsList;
    }

    public LiveData<List<ProductModel>> getExpiringProducts() {
        MutableLiveData<List<ProductModel>> productsList = new MutableLiveData<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 30);
        Timestamp thirtyDaysFromNow = new Timestamp(cal.getTime());

        getProductsRef()
            .whereLessThanOrEqualTo("expiryDate", thirtyDaysFromNow)
            .addSnapshotListener((value, error) -> {
                if (value != null) {
                    List<ProductModel> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        ProductModel p = doc.toObject(ProductModel.class);
                        if (p != null && p.getExpiryDate() != null) {
                            list.add(p);
                        }
                    }
                    productsList.setValue(list);
                }
            });
        return productsList;
    }
}
