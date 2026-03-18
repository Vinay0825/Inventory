package com.example.inventory.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;
import com.example.inventory.repository.AnalyticsRepository;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.repository.SalesRepository;
import com.example.inventory.utils.DateUtils;

import java.util.List;

public class DashboardViewModel extends AndroidViewModel {
    private final ProductRepository productRepository;
    private final SalesRepository salesRepository;
    private final AnalyticsRepository analyticsRepository;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        this.productRepository = new ProductRepository();
        this.salesRepository = new SalesRepository();
        this.analyticsRepository = new AnalyticsRepository();
    }

    public LiveData<List<ProductModel>> getAllProducts() {
        return productRepository.getAllProducts();
    }

    public LiveData<Double> getTodayRevenue() {
        return salesRepository.getTodayTotal(DateUtils.getTodayKey());
    }

    public LiveData<List<AnalyticsModel>> getBestSellers() {
        return analyticsRepository.getBestSellers();
    }

    public LiveData<List<ProductModel>> getLowStockProducts() {
        return productRepository.getLowStockProducts();
    }
}
