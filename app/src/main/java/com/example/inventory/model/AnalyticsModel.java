package com.example.inventory.model;

import com.google.firebase.Timestamp;
import java.util.Map;

public class AnalyticsModel {
    private String barcode;
    private double totalUnitsSold;
    private double totalRevenue;
    private int salesCount;
    private Timestamp lastSoldAt;
    private Map<String, Object> dailySales;
    private Map<String, Double> weeklySales;

    public AnalyticsModel() {
        // Required empty constructor for Firestore
    }

    public AnalyticsModel(String barcode, double totalUnitsSold, double totalRevenue, int salesCount, 
                          Timestamp lastSoldAt, Map<String, Object> dailySales, Map<String, Double> weeklySales) {
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

    public double getTotalUnitsSold() { return totalUnitsSold; }
    public void setTotalUnitsSold(double totalUnitsSold) { this.totalUnitsSold = totalUnitsSold; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public int getSalesCount() { return salesCount; }
    public void setSalesCount(int salesCount) { this.salesCount = salesCount; }

    public Timestamp getLastSoldAt() { return lastSoldAt; }
    public void setLastSoldAt(Timestamp lastSoldAt) { this.lastSoldAt = lastSoldAt; }

    public Map<String, Object> getDailySales() { return dailySales; }
    public void setDailySales(Map<String, Object> dailySales) { this.dailySales = dailySales; }

    public Map<String, Double> getWeeklySales() { return weeklySales; }
    public void setWeeklySales(Map<String, Double> weeklySales) { this.weeklySales = weeklySales; }
}
