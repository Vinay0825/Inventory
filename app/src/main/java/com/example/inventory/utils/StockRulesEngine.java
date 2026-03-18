package com.example.inventory.utils;

import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;

import java.util.List;

public class StockRulesEngine {

    public static boolean isBestSeller(AnalyticsModel model, List<AnalyticsModel> allAnalytics) {
        if (model == null || allAnalytics == null || allAnalytics.isEmpty()) return false;

        long totalUnits = 0;
        for (AnalyticsModel am : allAnalytics) {
            totalUnits += am.getTotalUnitsSold();
        }

        double average = (double) totalUnits / allAnalytics.size();
        return model.getTotalUnitsSold() > average * 1.5;
    }

    public static boolean isSlowMover(AnalyticsModel analytics, ProductModel product) {
        if (analytics == null || product == null) return false;

        // FIX: null check on createdAt before calling toDate()
        if (product.getCreatedAt() == null) return false;

        long daysSinceCreated = (System.currentTimeMillis() - product.getCreatedAt().toDate().getTime()) / (24 * 60 * 60 * 1000);
        return daysSinceCreated > 30 && analytics.getTotalUnitsSold() < 5;
    }

    public static int suggestRestock(ProductModel product, AnalyticsModel analytics) {
        if (product == null) return 0;
        if (analytics == null) return product.getLowStockThreshold() * 2;

        long monthlySales = analytics.getTotalUnitsSold();
        int threshold = product.getLowStockThreshold();
        return Math.max((int) monthlySales * 2, threshold * 2);
    }
}