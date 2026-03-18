package com.example.inventory.model;

import com.google.firebase.Timestamp;

public class RestockModel {
    private String restockId;
    private String barcode;
    private String productName;
    private String supplier;
    private int quantityAdded;
    private Timestamp restockedAt;

    public RestockModel() {
        // Required empty constructor for Firestore
    }

    public RestockModel(String restockId, String barcode, String productName, String supplier, 
                        int quantityAdded, Timestamp restockedAt) {
        this.restockId = restockId;
        this.barcode = barcode;
        this.productName = productName;
        this.supplier = supplier;
        this.quantityAdded = quantityAdded;
        this.restockedAt = restockedAt;
    }

    public String getRestockId() { return restockId; }
    public void setRestockId(String restockId) { this.restockId = restockId; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public int getQuantityAdded() { return quantityAdded; }
    public void setQuantityAdded(int quantityAdded) { this.quantityAdded = quantityAdded; }

    public Timestamp getRestockedAt() { return restockedAt; }
    public void setRestockedAt(Timestamp restockedAt) { this.restockedAt = restockedAt; }
}
