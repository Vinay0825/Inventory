package com.example.inventory.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.io.Serializable;

public class ProductModel implements Serializable {
    private String barcode;
    private String name;
    private String category;
    private String unit;
    private String imageUrl;
    private String imageBase64;
    private double price;
    private int currentStock;
    private double currentStockDecimal; // For kg, g, L, ml
    private int lowStockThreshold;
    private String supplier;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Timestamp expiryDate;

    public ProductModel() {
        // Required empty constructor for Firestore
    }

    public ProductModel(String barcode, String name, String category, String unit, String imageUrl, 
                        double price, int currentStock, int lowStockThreshold, 
                        Timestamp createdAt, Timestamp updatedAt, Timestamp expiryDate) {
        this.barcode = barcode;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.imageUrl = imageUrl;
        this.price = price;
        this.currentStock = currentStock;
        this.lowStockThreshold = lowStockThreshold;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiryDate = expiryDate;
    }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getCurrentStock() { return currentStock; }
    public void setCurrentStock(int currentStock) { this.currentStock = currentStock; }

    public double getCurrentStockDecimal() { return currentStockDecimal; }
    public void setCurrentStockDecimal(double currentStockDecimal) { this.currentStockDecimal = currentStockDecimal; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    // Add this field
    private int customLowStockThreshold = -1; // -1 means "use global default"

    // Add getter
    public int getCustomLowStockThreshold() {
        return customLowStockThreshold;
    }

    // Add setter
    public void setCustomLowStockThreshold(int customLowStockThreshold) {
        this.customLowStockThreshold = customLowStockThreshold;
    }

    // Add this helper — returns per-product threshold if set, else falls back to global
    public int getEffectiveThreshold(int globalThreshold) {
        return customLowStockThreshold > 0 ? customLowStockThreshold : globalThreshold;
    }

    // BUG 1 FIX
    public boolean isLowStock(int globalThreshold) {
        // Use decimal stock for weight/volume units
        double effectiveStock = (unit != null &&
            (unit.equalsIgnoreCase("kg") || unit.equalsIgnoreCase("g") ||
             unit.equalsIgnoreCase("l")  || unit.equalsIgnoreCase("ml")))
            ? currentStockDecimal
            : (double) currentStock;

        // Use the product's own lowStockThreshold if set (>0), else fall back to global
        int threshold = (lowStockThreshold > 0) ? lowStockThreshold : globalThreshold;
        return effectiveStock <= threshold;
    }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Timestamp getExpiryDate() { return expiryDate; }
    public void setExpiryDate(Timestamp expiryDate) { this.expiryDate = expiryDate; }

    public static boolean isDecimalUnit(String unit) {
        if (unit == null) return false;
        String u = unit.toLowerCase();
        return u.equals("kg") || u.equals("g") || u.equals("l") || u.equals("ml") || u.equals("kilogram") || u.equals("gram") || u.equals("litre") || u.equals("millilitre");
    }

    @com.google.firebase.firestore.Exclude
    public String getStockDisplay() {
        if (isDecimalUnit(unit)) {
            return String.format("%.2f %s", currentStockDecimal, unit);
        } else {
            return String.format("%d %s", currentStock, unit);
        }
    }

    @com.google.firebase.firestore.Exclude
    public android.graphics.Bitmap getImageBitmap() {
        if (imageBase64 == null || imageBase64.isEmpty()) return null;
        byte[] bytes = android.util.Base64.decode(imageBase64, android.util.Base64.DEFAULT);
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
