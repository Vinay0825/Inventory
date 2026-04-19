package com.example.inventory.utils;

import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;

import java.util.List;

public class StockRulesEngine {

    public static boolean isBestSeller(AnalyticsModel model, List<AnalyticsModel> allAnalytics) {
        if (model == null || allAnalytics == null || allAnalytics.isEmpty()) return false;

        double totalUnits = 0;
        for (AnalyticsModel am : allAnalytics) {
            totalUnits += am.getTotalUnitsSold();
        }

        double average = totalUnits / allAnalytics.size();
        return model.getTotalUnitsSold() > average * 1.5;
    }

    // REPLACE entire isSlowMover method:
    public static boolean isSlowMover(AnalyticsModel analytics, ProductModel product) {
        if (product == null) return false;

        long daysSinceCreated;
        if (product.getCreatedAt() != null) {
            daysSinceCreated = (System.currentTimeMillis()
                    - product.getCreatedAt().toDate().getTime())
                    / (24L * 60 * 60 * 1000);
        } else {
            daysSinceCreated = 30;
        }

        if (daysSinceCreated <= 7) return false;

        double unitsSold = (analytics != null) ? analytics.getTotalUnitsSold() : 0;
        return unitsSold < 5;
    }

    public static boolean isLowStock(ProductModel product) {
        if (product == null) return false;
        double effectiveStock = (product.getUnit() != null &&
                (product.getUnit().equalsIgnoreCase("kg") ||
                 product.getUnit().equalsIgnoreCase("g") ||
                 product.getUnit().equalsIgnoreCase("l") ||
                 product.getUnit().equalsIgnoreCase("ml")))
                ? product.getCurrentStockDecimal()
                : (double) product.getCurrentStock();
        int threshold = (product.getLowStockThreshold() > 0)
                ? product.getLowStockThreshold()
                : com.example.inventory.utils.AppPrefs.DEFAULT_THRESHOLD;
        return effectiveStock <= threshold;
    }

    public static int suggestRestock(ProductModel product, AnalyticsModel analytics) {
        if (product == null) return 0;
        int threshold = (product.getLowStockThreshold() > 0)
                ? product.getLowStockThreshold()
                : com.example.inventory.utils.AppPrefs.DEFAULT_THRESHOLD;
        if (analytics == null || analytics.getSalesCount() == 0) {
            return threshold * 2;
        }
        // Use daily average from last 30 days, suggest 14-day buffer
        // totalUnitsSold is all-time — approximate daily rate conservatively
        double allTimeSold = analytics.getTotalUnitsSold();
        // Assume product has been active at most 90 days to avoid inflated numbers
        double estimatedDays = Math.min(
                (System.currentTimeMillis() -
                        (product.getCreatedAt() != null
                                ? product.getCreatedAt().toDate().getTime()
                                : System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                        / (24.0 * 60 * 60 * 1000),
                90.0
        );
        if (estimatedDays < 1) estimatedDays = 1;
        double dailyAvg = allTimeSold / estimatedDays;
        int suggested = (int) Math.ceil(dailyAvg * 14); // 14-day buffer
        // Floor: never suggest less than threshold, cap at threshold * 4
        return Math.min(Math.max(suggested, threshold), threshold * 4);
    }
}
