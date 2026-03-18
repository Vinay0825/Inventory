package com.example.inventory.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CategoryHelper {

    public static List<String> getCategoriesForShopType(String shopType) {
        if (shopType == null) return new ArrayList<>();

        switch (shopType) {
            case "Kirana / General Store":
                return Arrays.asList("Beverages", "Snacks", "Dairy", "Grains & Pulses", "Personal Care", "Household", "Frozen Foods", "Other");
            case "Medical / Pharmacy":
                return Arrays.asList("Tablets & Capsules", "Syrups", "Injections", "Surgical Items", "Baby Care", "Vitamins & Supplements", "Health Devices", "Ayurvedic", "Other");
            case "Bakery / Food Shop":
                return Arrays.asList("Breads & Buns", "Cakes & Pastries", "Cookies", "Beverages", "Dairy", "Sweets", "Packaged Snacks", "Other");
            case "Electronics Shop":
                return Arrays.asList("Mobile Accessories", "Cables & Chargers", "Batteries", "Bulbs & Lights", "Appliances", "Audio & Video", "Computers & Peripherals", "Other");
            case "Clothing / Textile":
                return Arrays.asList("Men's Wear", "Women's Wear", "Kids Wear", "Innerwear", "Accessories", "Fabrics", "Footwear", "Other");
            default:
                return new ArrayList<>();
        }
    }
}
