package com.example.inventory.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class RestockModel {
    private String restockId;   // set from doc.getId(), @Exclude from Firestore
    private String barcode;
    private String productName;
    private double quantityAdded;
    private String unit;
    private String supplier;
    private Timestamp restockedAt;
    private boolean voided = false;

    // Required no-arg constructor for Firestore deserialization
    public RestockModel() {}

    @Exclude
    public String getRestockId() { return restockId; }
    public void setRestockId(String restockId) { this.restockId = restockId; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public double getQuantityAdded() { return quantityAdded; }
    public void setQuantityAdded(double quantityAdded) {
        this.quantityAdded = quantityAdded; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public Timestamp getRestockedAt() { return restockedAt; }
    public void setRestockedAt(Timestamp restockedAt) {
        this.restockedAt = restockedAt; }

    public boolean isVoided() { return voided; }
    public void setVoided(boolean voided) { this.voided = voided; }
}
