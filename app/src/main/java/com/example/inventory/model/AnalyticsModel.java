package com.example.inventory.model;

import com.google.firebase.Timestamp;
import java.util.Map;

public class AnalyticsModel {
    private String barcode;
    private long totalUnitsSold;
    private double totalRevenue;
    private int salesCount;
    private Timestamp lastSoldAt;
    private Map<String, Integer> dailySales;
    private Map<String, Integer> weeklySales;

    public AnalyticsModel() {
        // Required empty constructor for Firestore
    }

    public AnalyticsModel(String barcode, long totalUnitsSold, double totalRevenue, int salesCount, 
                          Timestamp lastSoldAt, Map<String, Integer> dailySales, Map<String, Integer> weeklySales) {
        this.barcode = barcode;
        this.totalUnitsSold = totalUnitsSold;
        this.totalRevenue = totalRevenue;
        this.salesCount = salesCount;
        this.lastSoldAt = lastSoldAt;
        this.dailySales = dailySales;
        this.weeklySales = weeklySales;
    }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public long getTotalUnitsSold() { return totalUnitsSold; }
    public void setTotalUnitsSold(long totalUnitsSold) { this.totalUnitsSold = totalUnitsSold; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public int getSalesCount() { return salesCount; }
    public void setSalesCount(int salesCount) { this.salesCount = salesCount; }

    public Timestamp getLastSoldAt() { return lastSoldAt; }
    public void setLastSoldAt(Timestamp lastSoldAt) { this.lastSoldAt = lastSoldAt; }

    public Map<String, Integer> getDailySales() { return dailySales; }
    public void setDailySales(Map<String, Integer> dailySales) { this.dailySales = dailySales; }

    public Map<String, Integer> getWeeklySales() { return weeklySales; }
    public void setWeeklySales(Map<String, Integer> weeklySales) { this.weeklySales = weeklySales; }
}
