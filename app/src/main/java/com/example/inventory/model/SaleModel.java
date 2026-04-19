package com.example.inventory.model;

import com.google.firebase.Timestamp;

public class SaleModel {
    private String saleId;
    private String barcode;
    private String productName;
    private String dayKey;
    private String unit;
    private int quantitySold;
    private double quantitySoldDecimal;
    private double priceAtSale;
    private double totalAmount;
    private Timestamp soldAt;
    private boolean voided;

    public SaleModel() {
        // Required empty constructor for Firestore
    }

    public String getSaleId() { return saleId; }
    public void setSaleId(String saleId) { this.saleId = saleId; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getDayKey() { return dayKey; }
    public void setDayKey(String dayKey) { this.dayKey = dayKey; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public int getQuantitySold() { return quantitySold; }
    public void setQuantitySold(int quantitySold) { this.quantitySold = quantitySold; }

    public double getQuantitySoldDecimal() { return quantitySoldDecimal; }
    public void setQuantitySoldDecimal(double quantitySoldDecimal) { this.quantitySoldDecimal = quantitySoldDecimal; }

    public double getPriceAtSale() { return priceAtSale; }
    public void setPriceAtSale(double priceAtSale) { this.priceAtSale = priceAtSale; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public Timestamp getSoldAt() { return soldAt; }
    public void setSoldAt(Timestamp soldAt) { this.soldAt = soldAt; }

    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
}
