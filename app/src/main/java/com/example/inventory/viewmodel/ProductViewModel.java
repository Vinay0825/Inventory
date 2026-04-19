package com.example.inventory.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.example.inventory.model.ProductModel;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.utils.AppPrefs;

import java.util.ArrayList;
import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    private final ProductRepository repository;

    public ProductViewModel(@NonNull Application application) {
        super(application);
        this.repository = new ProductRepository();
    }

    public void addProduct(ProductModel product) {
        repository.addProduct(product);
    }

    public void updateProduct(ProductModel product) {
        repository.updateProduct(product);
    }

    public void deleteProduct(String barcode) {
        repository.deleteProduct(barcode);
    }

    public LiveData<ProductModel> getProduct(String barcode) {
        return repository.getProduct(barcode);
    }

    public LiveData<List<ProductModel>> getAllProducts() {
        return repository.getAllProducts();
    }

    public void updateStock(String barcode, int newStock) {
        repository.updateStock(barcode, newStock);
    }

    public void updateStockDecimal(String barcode, double newStock) {
        repository.updateStockDecimal(barcode, newStock);
    }

    public LiveData<List<ProductModel>> searchProducts(String query) {
        return repository.searchProducts(query);
    }

    // Overload used by alerts screen with explicit threshold
    public LiveData<List<ProductModel>> getLowStockProducts(int globalThreshold) {
        // Get ALL products then filter — respects per-product custom thresholds
        return Transformations.map(
                repository.getAllProducts(),
                allProducts -> {
                    if (allProducts == null) return new ArrayList<>();
                    List<ProductModel> result = new ArrayList<>();
                    for (ProductModel p : allProducts) {
                        if (p.isLowStock(globalThreshold)) {
                            result.add(p);
                        }
                    }
                    return result;
                }
        );
    }

    // Keep existing no-arg call working — uses global default
    public LiveData<List<ProductModel>> getLowStockProducts() {
        return getLowStockProducts(AppPrefs.DEFAULT_THRESHOLD);
    }

    public LiveData<List<ProductModel>> getExpiringProducts() {
        return repository.getExpiringProducts();
    }
}
