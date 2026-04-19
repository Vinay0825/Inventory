package com.example.inventory.ui.analytics;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentAnalyticsBinding;
import com.example.inventory.databinding.ItemProductBinding;
import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;
import com.example.inventory.model.SaleModel;
import com.example.inventory.utils.StockRulesEngine;
import com.example.inventory.viewmodel.DashboardViewModel;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsFragment extends Fragment {

    private FragmentAnalyticsBinding binding;
    private DashboardViewModel dashboardViewModel;
    private ProductViewModel productViewModel;
    private AnalyticsProductAdapter bestSellersAdapter;
    private AnalyticsProductAdapter slowMoversAdapter;
    private int analyticsFilterDays = 0; // 0 = all time
    private List<com.example.inventory.model.AnalyticsModel> cachedAnalytics = new ArrayList<>();
    private List<ProductModel> cachedProducts = new ArrayList<>();
    private com.example.inventory.repository.AnalyticsRepository analyticsRepo;
    private AnalyticsChartView analyticsChartView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAnalyticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        analyticsRepo = new com.example.inventory.repository.AnalyticsRepository();

        setupRecyclerViews();
        
        analyticsChartView = new AnalyticsChartView(requireContext());
        binding.analyticsChartContainer.removeAllViews();
        binding.analyticsChartContainer.addView(analyticsChartView);
        fetchAnalyticsChart();

        setupAnalyticsFilterChips();
        observeData();
        
        binding.exportExcelButton.setOnClickListener(v -> exportSalesToExcel());
        binding.exportRestocksButton.setOnClickListener(v -> {
            binding.exportRestocksButton.setEnabled(false);
            binding.exportRestocksButton.setText("Exporting...");
            exportRestocksToExcel();
        });
    }

    private void setupAnalyticsFilterChips() {
        binding.analyticsChipAllTime.setOnClickListener(v -> {
            analyticsFilterDays = 0;
            triggerAnalyticsRefresh();
            fetchAnalyticsChart();
        });
        binding.analyticsChipLast7.setOnClickListener(v -> {
            analyticsFilterDays = 7;
            triggerAnalyticsRefresh();
            fetchAnalyticsChart();
        });
        binding.analyticsChipLast15.setOnClickListener(v -> {
            analyticsFilterDays = 15;
            triggerAnalyticsRefresh();
            fetchAnalyticsChart();
        });
        binding.analyticsChipLast30.setOnClickListener(v -> {
            analyticsFilterDays = 30;
            triggerAnalyticsRefresh();
            fetchAnalyticsChart();
        });
    }

    private void setupRecyclerViews() {
        bestSellersAdapter = new AnalyticsProductAdapter();
        binding.analyticsBestSellersRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.analyticsBestSellersRecyclerView.setAdapter(bestSellersAdapter);

        slowMoversAdapter = new AnalyticsProductAdapter();
        binding.slowMoversRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.slowMoversRecyclerView.setAdapter(slowMoversAdapter);
    }

    private void observeData() {
        String userId = com.google.firebase.auth.FirebaseAuth
            .getInstance().getUid();
        if (userId == null) return;

        // Fetch products directly — LiveData getValue() returns null
        // when observed from a new fragment lifecycle
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("products")
            .addSnapshotListener((productSnaps, e1) -> {
                if (binding == null || productSnaps == null) return;
                List<ProductModel> products = new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc
                        : productSnaps.getDocuments()) {
                    ProductModel pm = doc.toObject(ProductModel.class);
                    if (pm != null) products.add(pm);
                }
                binding.analyticsActiveProducts.setText(
                    String.valueOf(products.size()));
                cachedProducts = products;
                triggerAnalyticsRefresh();
            });

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("analytics")
            .addSnapshotListener((snapshots, error) -> {
                if (binding == null || snapshots == null) return;
                List<com.example.inventory.model.AnalyticsModel> list =
                    new ArrayList<>();
                for (com.google.firebase.firestore.DocumentSnapshot doc
                        : snapshots.getDocuments()) {
                    com.example.inventory.model.AnalyticsModel am =
                        doc.toObject(
                            com.example.inventory.model.AnalyticsModel.class);
                    if (am != null) list.add(am);
                }
                cachedAnalytics = list;
                triggerAnalyticsRefresh();
            });
    }

    private void processAnalytics(
            List<com.example.inventory.model.AnalyticsModel> allAnalytics,
            List<ProductModel> products) {
        if (binding == null) return;
        if (products == null || products.isEmpty()) return;

        android.util.Log.d("ANALYTICS", "processAnalytics called");
        android.util.Log.d("ANALYTICS", "allAnalytics size: " + allAnalytics.size());
        android.util.Log.d("ANALYTICS", "products size: " + products.size());
        android.util.Log.d("ANALYTICS", "filterDays: " + analyticsFilterDays);

        java.util.Date cutoff = null;
        if (analyticsFilterDays > 0) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.add(java.util.Calendar.DAY_OF_YEAR, -analyticsFilterDays);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cutoff = cal.getTime();
        }
        final java.util.Date finalCutoff = cutoff;

        Map<String, com.example.inventory.model.AnalyticsModel> analyticsMap =
            new HashMap<>();
        for (com.example.inventory.model.AnalyticsModel am : allAnalytics) {
            analyticsMap.put(am.getBarcode(), am);
        }

        double totalRev = 0;
        double totalSold = 0;

        // For filtered periods, calculate revenue from dailySales map
        List<com.example.inventory.model.AnalyticsModel> filteredList =
            new ArrayList<>();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
            "yyyy-MM-dd", Locale.getDefault());

        for (com.example.inventory.model.AnalyticsModel am : allAnalytics) {
            if (finalCutoff == null) {
                // All time — use stored totals directly
                totalRev += am.getTotalRevenue();
                totalSold += am.getTotalUnitsSold();
                filteredList.add(am);
            } else {
                // Period filter — sum daily sales within range
                double periodSold = 0;
                Map<String, Object> rawDaily = am.getDailySales();
                if (rawDaily != null) {
                    for (Map.Entry<String, Object> entry : rawDaily.entrySet()) {
                        try {
                            java.util.Date dayDate = sdf.parse(entry.getKey());
                            if (dayDate != null && dayDate.after(finalCutoff)) {
                                Object val = entry.getValue();
                                double qty = 0;
                                if (val instanceof Double) qty = (Double) val;
                                else if (val instanceof Long) qty = ((Long) val).doubleValue();
                                else if (val instanceof Number) qty = ((Number) val).doubleValue();
                                periodSold += qty;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                if (periodSold > 0) {
                    // Estimate period revenue proportionally
                    double allTimeUnits = am.getTotalUnitsSold();
                    double periodRev = allTimeUnits > 0
                        ? (periodSold / allTimeUnits) * am.getTotalRevenue()
                        : 0;
                    totalRev += periodRev;
                    totalSold += periodSold;

                    com.example.inventory.model.AnalyticsModel copy =
                        new com.example.inventory.model.AnalyticsModel();
                    copy.setBarcode(am.getBarcode());
                    copy.setTotalUnitsSold(periodSold);
                    copy.setTotalRevenue(periodRev);
                    copy.setSalesCount(am.getSalesCount());
                    copy.setLastSoldAt(am.getLastSoldAt());
                    copy.setDailySales(am.getDailySales());
                    filteredList.add(copy);
                }
            }
        }

        android.util.Log.d("ANALYTICS", "filteredList size: " + filteredList.size());
        android.util.Log.d("ANALYTICS", "totalRev: " + totalRev + " totalSold: " + totalSold);

        binding.analyticsTotalRevenue.setText(String.format(
            Locale.getDefault(), "₹%.2f", totalRev));
        binding.analyticsTotalSold.setText(
            totalSold == (long) totalSold
                ? String.valueOf((long) totalSold)
                : String.format(Locale.getDefault(), "%.2f", totalSold));

        // Sort by units sold
        Collections.sort(filteredList, (a, b) ->
            Double.compare(b.getTotalUnitsSold(), a.getTotalUnitsSold()));

        List<ProductModel> bestSellers = new ArrayList<>();
        List<ProductModel> slowMovers = new ArrayList<>();
        Map<String, Integer> categoryCount = new HashMap<>();

        // Best sellers = top 5 by units sold (already sorted descending)
        int bestSellerLimit = Math.min(5, filteredList.size());
        for (int i = 0; i < filteredList.size(); i++) {
            com.example.inventory.model.AnalyticsModel am = filteredList.get(i);
            ProductModel pm = findProduct(products, am.getBarcode());
            if (pm == null) continue;
            if (i < bestSellerLimit && am.getTotalUnitsSold() > 0) {
                bestSellers.add(pm);
            }
        }

        // BUG 4 FIX: Slow movers and Category count unified loop
        // Evaluate ALL products against ALL TIME analytics
        Map<String, com.example.inventory.model.AnalyticsModel> allTimeMap = new HashMap<>();
        for (com.example.inventory.model.AnalyticsModel am : allAnalytics) {
            allTimeMap.put(am.getBarcode(), am);
        }

        for (ProductModel pm : products) {
            if (pm == null || pm.getBarcode() == null) continue;
            // Category count — all products regardless of sales
            String cat = pm.getCategory() != null ? pm.getCategory() : "Other";
            categoryCount.put(cat, categoryCount.getOrDefault(cat, 0) + 1);
            // Slow mover check uses all-time analytics (may be null for never-sold)
            com.example.inventory.model.AnalyticsModel allTimeAm = allTimeMap.get(pm.getBarcode());
            if (com.example.inventory.utils.StockRulesEngine.isSlowMover(allTimeAm, pm)) {
                slowMovers.add(pm);
            }
        }

        android.util.Log.d("ANALYTICS", "bestSellers: " + bestSellers.size());
        android.util.Log.d("ANALYTICS", "slowMovers: " + slowMovers.size());
        android.util.Log.d("ANALYTICS", "categoryCount: " + categoryCount.size());

        bestSellersAdapter.setProducts(bestSellers, analyticsMap);
        slowMoversAdapter.setProducts(slowMovers, analyticsMap);
        updateCategoryBreakdown(categoryCount);

        binding.analyticsSummaryText.setText(String.format(
            Locale.getDefault(),
            "%d best sellers · %d slow movers · %d categories",
            bestSellers.size(), slowMovers.size(),
            categoryCount.size()));
    }

    private void triggerAnalyticsRefresh() {
        processAnalytics(cachedAnalytics, cachedProducts);
    }

    private void fetchAnalyticsChart() {
        String userId = com.google.firebase.auth.FirebaseAuth
                .getInstance().getUid();
        if (userId == null) return;

        // Determine how many days to show based on filter
        int daysToShow = analyticsFilterDays > 0 ? analyticsFilterDays : 7;

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(daysToShow - 1));
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        com.google.firebase.Timestamp startDate =
                new com.google.firebase.Timestamp(cal.getTime());

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("sales")
                .whereGreaterThanOrEqualTo("soldAt", startDate)
                .whereEqualTo("voided", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (binding == null || analyticsChartView == null) return;

                    java.text.SimpleDateFormat keySdf =
                            new java.text.SimpleDateFormat(
                                    "yyyy-MM-dd", Locale.getDefault());
                    java.util.TreeMap<String, Double> dailyTotals =
                            new java.util.TreeMap<>();

                    // Pre-fill all days in range with 0
                    for (int i = daysToShow - 1; i >= 0; i--) {
                        java.util.Calendar c =
                                java.util.Calendar.getInstance();
                        c.add(java.util.Calendar.DAY_OF_YEAR, -i);
                        dailyTotals.put(keySdf.format(c.getTime()), 0.0);
                    }

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshot.getDocuments()) {
                        com.example.inventory.model.SaleModel sale =
                                doc.toObject(
                                        com.example.inventory.model.SaleModel.class);
                        if (sale != null && sale.getDayKey() != null
                                && dailyTotals.containsKey(
                                sale.getDayKey())) {
                            dailyTotals.put(sale.getDayKey(),
                                    dailyTotals.get(sale.getDayKey())
                                            + sale.getTotalAmount());
                        }
                    }

                    analyticsChartView.setData(
                            new ArrayList<>(dailyTotals.values()));
                });
    }

    private ProductModel findProduct(List<ProductModel> products, String barcode) {
        if (barcode == null) return null;
        for (ProductModel p : products) {
            if (p != null && barcode.equals(p.getBarcode())) return p;
        }
        return null;
    }

    private void updateCategoryBreakdown(
            Map<String, Integer> categoryCount) {
        if (binding == null) return;
        binding.categoryBreakdownContainer.removeAllViews();

        if (categoryCount == null || categoryCount.isEmpty()) {
            android.widget.TextView empty =
                new android.widget.TextView(getContext());
            empty.setText("No category data yet.");
            empty.setTextColor(0xFF9E9E9E);
            empty.setPadding(0, 8, 0, 8);
            binding.categoryBreakdownContainer.addView(empty);
            return;
        }

        List<Map.Entry<String, Integer>> entries =
            new ArrayList<>(categoryCount.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());

        int totalProducts = 0;
        for (Map.Entry<String, Integer> e : entries)
            totalProducts += e.getValue();
        final int total = totalProducts;

        for (Map.Entry<String, Integer> entry : entries) {
            android.widget.LinearLayout row =
                new android.widget.LinearLayout(getContext());
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setPadding(0, 8, 0, 8);

            android.widget.LinearLayout labelRow =
                new android.widget.LinearLayout(getContext());
            labelRow.setOrientation(
                android.widget.LinearLayout.HORIZONTAL);

            android.widget.TextView label =
                new android.widget.TextView(getContext());
            label.setText(entry.getKey());
            label.setLayoutParams(
                new android.widget.LinearLayout.LayoutParams(
                    0, android.widget.LinearLayout.LayoutParams
                        .WRAP_CONTENT, 1f));

            android.widget.TextView countTv =
                new android.widget.TextView(getContext());
            countTv.setText(entry.getValue() + " products");
            countTv.setTextColor(0xFF9E9E9E);

            labelRow.addView(label);
            labelRow.addView(countTv);

            android.widget.ProgressBar bar =
                new android.widget.ProgressBar(getContext(), null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(total);
            bar.setProgress(entry.getValue());
            android.widget.LinearLayout.LayoutParams barParams =
                new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    16);
            barParams.topMargin = 4;
            bar.setLayoutParams(barParams);

            row.addView(labelRow);
            row.addView(bar);
            binding.categoryBreakdownContainer.addView(row);
        }
    }

    private void exportSalesToExcel() {
        binding.exportExcelButton.setEnabled(false);
        binding.exportExcelButton.setText("Exporting...");
        
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;
        
        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("sales")
            .orderBy("soldAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (binding == null) return;
                try {
                    XSSFWorkbook workbook = new XSSFWorkbook();
                    
                    // Sheet 1: All Sales
                    Sheet salesSheet = workbook.createSheet("All Sales");
                    Row header = salesSheet.createRow(0);
                    header.createCell(0).setCellValue("Date");
                    header.createCell(1).setCellValue("Time");
                    header.createCell(2).setCellValue("Product Name");
                    header.createCell(3).setCellValue("Barcode");
                    header.createCell(4).setCellValue("Quantity Sold");
                    header.createCell(5).setCellValue("Unit");
                    header.createCell(6).setCellValue("Price Per Unit (₹)");
                    header.createCell(7).setCellValue("Total Amount (₹)");
                    header.createCell(8).setCellValue("Status");

                    // Set manual column widths Sheet 1
                    salesSheet.setColumnWidth(0, 12 * 256);
                    salesSheet.setColumnWidth(1, 10 * 256);
                    salesSheet.setColumnWidth(2, 30 * 256);
                    salesSheet.setColumnWidth(3, 20 * 256);
                    salesSheet.setColumnWidth(4, 8 * 256);
                    salesSheet.setColumnWidth(5, 8 * 256);
                    salesSheet.setColumnWidth(6, 12 * 256);
                    salesSheet.setColumnWidth(7, 12 * 256);
                    salesSheet.setColumnWidth(8, 10 * 256);
                    
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    
                    int rowIndex = 1;
                    double totalRevenue = 0;
                    double voidedAmount = 0;
                    int totalTransactions = 0;
                    int voidedTransactions = 0;
                    Map<String, Double> productRevenue = new LinkedHashMap<>();
                    Map<String, Long> productQty = new LinkedHashMap<>();
                    
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        SaleModel sale = doc.toObject(SaleModel.class);
                        
                        Row row = salesSheet.createRow(rowIndex++);
                        
                        Date saleDate = sale.getSoldAt() != null ? sale.getSoldAt().toDate() : new Date();
                        row.createCell(0).setCellValue(dateFormat.format(saleDate));
                        row.createCell(1).setCellValue(timeFormat.format(saleDate));
                        row.createCell(2).setCellValue(sale.getProductName() != null ? sale.getProductName() : "");
                        row.createCell(3).setCellValue(sale.getBarcode() != null ? sale.getBarcode() : "");
                        
                        double qty = sale.getQuantitySoldDecimal() > 0 ? sale.getQuantitySoldDecimal() : sale.getQuantitySold();
                        row.createCell(4).setCellValue(qty);
                        row.createCell(5).setCellValue(sale.getUnit() != null ? sale.getUnit() : "pcs");
                        row.createCell(6).setCellValue(sale.getPriceAtSale());
                        row.createCell(7).setCellValue(sale.getTotalAmount());
                        row.createCell(8).setCellValue(sale.isVoided() ? "VOIDED" : "Completed");
                        
                        totalTransactions++;
                        if (sale.isVoided()) {
                            voidedTransactions++;
                            voidedAmount += sale.getTotalAmount();
                        } else {
                            totalRevenue += sale.getTotalAmount();
                            String name = sale.getProductName() != null ? sale.getProductName() : "Unknown";
                            productRevenue.merge(name, sale.getTotalAmount(), Double::sum);
                            productQty.merge(name, (long) qty, Long::sum);
                        }
                    }
                    
                    // Sheet 2: Summary
                    Sheet summarySheet = workbook.createSheet("Summary");
                    // Set manual column widths Sheet 2
                    for (int i = 0; i < 3; i++) summarySheet.setColumnWidth(i, 20 * 256);

                    summarySheet.createRow(0).createCell(0).setCellValue("SALES AUDIT SUMMARY");
                    summarySheet.createRow(1).createCell(0).setCellValue("Generated on: " + dateFormat.format(new Date()));
                    summarySheet.createRow(2);
                    
                    Row r1 = summarySheet.createRow(3);
                    r1.createCell(0).setCellValue("Total Transactions"); r1.createCell(1).setCellValue(totalTransactions);
                    Row r2 = summarySheet.createRow(4);
                    r2.createCell(0).setCellValue("Completed Sales"); r2.createCell(1).setCellValue(totalTransactions - voidedTransactions);
                    Row r3 = summarySheet.createRow(5);
                    r3.createCell(0).setCellValue("Voided Sales"); r3.createCell(1).setCellValue(voidedTransactions);
                    Row r4 = summarySheet.createRow(6);
                    r4.createCell(0).setCellValue("Total Revenue (₹)"); r4.createCell(1).setCellValue(totalRevenue);
                    Row r5 = summarySheet.createRow(7);
                    r5.createCell(0).setCellValue("Voided Amount (₹)"); r5.createCell(1).setCellValue(voidedAmount);
                    summarySheet.createRow(8);
                    
                    // Product breakdown header
                    Row ph = summarySheet.createRow(9);
                    ph.createCell(0).setCellValue("Product Name");
                    ph.createCell(1).setCellValue("Total Qty Sold");
                    ph.createCell(2).setCellValue("Total Revenue (₹)");
                    
                    int pRow = 10;
                    // Sort by revenue descending
                    List<Map.Entry<String, Double>> sorted = new ArrayList<>(productRevenue.entrySet());
                    sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
                    for (Map.Entry<String, Double> entry : sorted) {
                        Row pr = summarySheet.createRow(pRow++);
                        pr.createCell(0).setCellValue(entry.getKey());
                        pr.createCell(1).setCellValue(productQty.getOrDefault(entry.getKey(), 0L));
                        pr.createCell(2).setCellValue(entry.getValue());
                    }
                    
                    // Save to cache
                    File outputFile = new File(requireContext().getCacheDir(),
                            "ShopEase_Sales_" + new SimpleDateFormat("yyyyMMdd_HHmm",
                            Locale.getDefault()).format(new Date()) + ".xlsx");
                    FileOutputStream fos = new FileOutputStream(outputFile);
                    workbook.write(fos);
                    fos.close();
                    workbook.close();

                    // Copy to Downloads folder
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, outputFile.getName());
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Downloads.IS_PENDING, 1);
                    }

                    Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    Uri itemUri = requireContext().getContentResolver().insert(collection, values);

                    if (itemUri != null) {
                        try (OutputStream os = requireContext().getContentResolver().openOutputStream(itemUri)) {
                            java.nio.file.Files.copy(outputFile.toPath(), os);
                        }
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            values.clear();
                            values.put(MediaStore.Downloads.IS_PENDING, 0);
                            requireContext().getContentResolver().update(itemUri, values, null, null);
                        }
                        sendExportNotification(itemUri);
                    }
                    
                    // Share via FileProvider
                    Uri shareUri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        outputFile
                    );
                    
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, shareUri);
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "ShopEase Sales Report");
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "Share Sales Report"));
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
                
                if (binding != null) {
                    binding.exportExcelButton.setEnabled(true);
                    binding.exportExcelButton.setText("Export Sales to Excel");
                }
            })
            .addOnFailureListener(e -> {
                if (binding == null) return;
                Toast.makeText(getContext(), "Failed to fetch sales: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                binding.exportExcelButton.setEnabled(true);
                binding.exportExcelButton.setText("Export Sales to Excel");
            });
    }

    private void exportRestocksToExcel() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("restocks")
            .get()
            .addOnSuccessListener(snapshot -> {
                if (binding == null) return;
                if (snapshot.isEmpty()) {
                    binding.exportRestocksButton.setEnabled(true);
                    binding.exportRestocksButton.setText("Export Restocks to Excel");
                    android.widget.Toast.makeText(getContext(),
                        "No restocks to export",
                        android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                List<com.example.inventory.model.RestockModel> list =
                    new ArrayList<>();
                for (com.google.firebase.firestore.QueryDocumentSnapshot doc
                        : snapshot) {
                    com.example.inventory.model.RestockModel r =
                        doc.toObject(
                            com.example.inventory.model.RestockModel.class);
                    if (r != null) {
                        r.setRestockId(doc.getId());
                        list.add(r);
                    }
                }
                writeRestockExcel(list);
            })
            .addOnFailureListener(e -> {
                if (binding == null) return;
                binding.exportRestocksButton.setEnabled(true);
                binding.exportRestocksButton.setText("Export Restocks to Excel");
                android.widget.Toast.makeText(getContext(),
                    "Failed to load restocks: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
            });
    }

    private void writeRestockExcel(
            List<com.example.inventory.model.RestockModel> list) {
        try {
            org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet =
                wb.createSheet("Restocks");

            org.apache.poi.ss.usermodel.CellStyle bold =
                wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font f = wb.createFont();
            f.setBold(true);
            bold.setFont(f);

            org.apache.poi.ss.usermodel.Row hdr = sheet.createRow(0);
            String[] cols = {"Date","Time","Product",
                "Barcode","Qty Added","Status"};
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = hdr.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(bold);
            }
            sheet.setColumnWidth(0, 14*256);
            sheet.setColumnWidth(1, 10*256);
            sheet.setColumnWidth(2, 30*256);
            sheet.setColumnWidth(3, 20*256);
            sheet.setColumnWidth(4, 10*256);
            sheet.setColumnWidth(5, 10*256);

            java.text.SimpleDateFormat df =
                new java.text.SimpleDateFormat(
                    "dd/MM/yyyy", java.util.Locale.getDefault());
            java.text.SimpleDateFormat tf =
                new java.text.SimpleDateFormat(
                    "hh:mm a", java.util.Locale.getDefault());

            int row = 1;
            for (com.example.inventory.model.RestockModel r : list) {
                org.apache.poi.ss.usermodel.Row ro = sheet.createRow(row++);
                if (r.getRestockedAt() != null) {
                    java.util.Date d = r.getRestockedAt().toDate();
                    ro.createCell(0).setCellValue(df.format(d));
                    ro.createCell(1).setCellValue(tf.format(d));
                } else {
                    ro.createCell(0).setCellValue("—");
                    ro.createCell(1).setCellValue("—");
                }
                ro.createCell(2).setCellValue(
                    r.getProductName() != null ? r.getProductName() : "");
                ro.createCell(3).setCellValue(
                    r.getBarcode() != null ? r.getBarcode() : "");
                ro.createCell(4).setCellValue(r.getQuantityAdded());
                ro.createCell(5).setCellValue(
                    r.isVoided() ? "VOIDED" : "OK");
            }

            java.io.File file = new java.io.File(
                requireContext().getCacheDir(),
                "ShopEase_Restocks_" + new java.text.SimpleDateFormat(
                    "yyyyMMdd_HHmm",
                    java.util.Locale.getDefault()).format(new java.util.Date())
                + ".xlsx");
            wb.write(new java.io.FileOutputStream(file));
            wb.close();

            // Copy to Downloads via MediaStore
            android.content.ContentValues cv =
                new android.content.ContentValues();
            cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME,
                file.getName());
            cv.put(android.provider.MediaStore.Downloads.MIME_TYPE,
                "application/vnd.openxmlformats-officedocument"
                + ".spreadsheetml.sheet");
            cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
            android.net.Uri col =
                android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
            android.net.Uri uri =
                requireContext().getContentResolver().insert(col, cv);
            if (uri != null) {
                try (java.io.OutputStream os =
                        requireContext().getContentResolver()
                            .openOutputStream(uri)) {
                    java.nio.file.Files.copy(file.toPath(), os);
                }
                cv.clear();
                cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                requireContext().getContentResolver()
                    .update(uri, cv, null, null);
            }

            // Share sheet
            android.net.Uri shareUri =
                androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            android.content.Intent intent =
                new android.content.Intent(
                    android.content.Intent.ACTION_SEND);
            intent.setType("application/vnd.openxmlformats-officedocument"
                + ".spreadsheetml.sheet");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, shareUri);
            intent.addFlags(
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(
                intent, "Share Restock Report"));

            // Re-enable button
            if (binding != null) {
                binding.exportRestocksButton.setEnabled(true);
                binding.exportRestocksButton.setText("Export Restocks to Excel");
            }

            // Send notification matching sales export
            sendExportNotification(uri != null ? uri :
                androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file));

            android.widget.Toast.makeText(getContext(),
                "Exported to Downloads",
                android.widget.Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            android.util.Log.e("RestockExport",
                "Export failed: " + e.getMessage(), e);
            if (binding != null) {
                binding.exportRestocksButton.setEnabled(true);
                binding.exportRestocksButton.setText("Export Restocks to Excel");
            }
            android.widget.Toast.makeText(getContext(),
                "Export failed: " + e.getMessage(),
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void sendExportNotification(Uri fileUri) {
        String channelId = "EXPORT_DONE";
        android.app.NotificationManager nm = (android.app.NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, "Export Notifications",
                android.app.NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(channel);
        }

        android.app.PendingIntent openIntent = android.app.PendingIntent.getActivity(
            requireContext(), 0,
            new android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(fileUri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
            android.app.PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Export Complete")
                .setContentText("Report saved to Downloads")
                .setContentIntent(openIntent)
                .setAutoCancel(true);

        nm.notify(2001, builder.build());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class AnalyticsChartView extends View {
        private List<Double> data = new ArrayList<>();
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public AnalyticsChartView(Context context) {
            super(context);
            linePaint.setColor(0xFF00C896);
            linePaint.setStrokeWidth(5f);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            linePaint.setStrokeCap(Paint.Cap.ROUND);

            fillPaint.setColor(0x2200C896); // semi-transparent fill
            fillPaint.setStyle(Paint.Style.FILL);

            dotPaint.setColor(0xFF00C896);
            dotPaint.setStyle(Paint.Style.FILL);

            textPaint.setColor(0xFF9E9E9E);
            textPaint.setTextSize(28f);
            textPaint.setTextAlign(Paint.Align.CENTER);

            valuePaint.setColor(0xFF212121);
            valuePaint.setTextSize(22f);
            valuePaint.setTextAlign(Paint.Align.CENTER);
            valuePaint.setTextSize(24f);

            gridPaint.setColor(0x22000000);
            gridPaint.setStrokeWidth(1f);
        }

        public void setData(List<Double> newData) {
            this.data = newData;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (data == null || data.isEmpty()) return;

            float w = getWidth();
            float h = getHeight();
            float padL = 16f, padR = 16f;
            float padTop = 36f;
            float padBot = 40f;
            float chartH = h - padTop - padBot;
            int n = data.size();
            float stepX = (w - padL - padR) / (n - 1);

            double max = 0;
            for (Double d : data) if (d > max) max = d;
            if (max == 0) max = 1;

            // Day labels
            String[] labels = new String[n];
            java.text.SimpleDateFormat shortFmt;
            if (n <= 7) {
                // Show day name for 7 days
                String[] dayNames =
                    {"Su","Mo","Tu","We","Th","Fr","Sa"};
                for (int i = 0; i < n; i++) {
                    java.util.Calendar c =
                        java.util.Calendar.getInstance();
                    c.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1 - i));
                    labels[i] =
                        dayNames[c.get(java.util.Calendar.DAY_OF_WEEK)-1];
                }
            } else {
                // Show "dd/M" for 15/30 days — only label every 5th point
                shortFmt = new java.text.SimpleDateFormat(
                    "d/M", java.util.Locale.getDefault());
                for (int i = 0; i < n; i++) {
                    java.util.Calendar c =
                        java.util.Calendar.getInstance();
                    c.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1 - i));
                    // Only show label every 5 days to avoid overlap
                    if (i % 5 == 0 || i == n - 1) {
                        labels[i] = shortFmt.format(c.getTime());
                    } else {
                        labels[i] = "";
                    }
                }
            }

            // Compute points
            float[] px = new float[n];
            float[] py = new float[n];
            for (int i = 0; i < n; i++) {
                px[i] = padL + i * stepX;
                py[i] = padTop + chartH
                    - (float)(data.get(i) / max * chartH);
            }

            // Draw grid lines (3 horizontal)
            for (int g = 0; g <= 2; g++) {
                float gy = padTop + chartH * g / 2f;
                canvas.drawLine(padL, gy, w - padR, gy, gridPaint);
            }

            // Draw filled area under line
            android.graphics.Path fillPath = new android.graphics.Path();
            fillPath.moveTo(px[0], padTop + chartH);
            for (int i = 0; i < n; i++) fillPath.lineTo(px[i], py[i]);
            fillPath.lineTo(px[n-1], padTop + chartH);
            fillPath.close();
            canvas.drawPath(fillPath, fillPaint);

            // Draw line
            android.graphics.Path linePath = new android.graphics.Path();
            linePath.moveTo(px[0], py[0]);
            for (int i = 1; i < n; i++) linePath.lineTo(px[i], py[i]);
            canvas.drawPath(linePath, linePaint);

            // Draw dots + labels
            for (int i = 0; i < n; i++) {
                canvas.drawCircle(px[i], py[i], 7f, dotPaint);
                canvas.drawText(labels[i], px[i],
                    h - padBot + 28f, textPaint);

                // Value label above dot
                if (data.get(i) > 0) {
                    double val = data.get(i);
                    String vLabel = val >= 1000
                        ? String.format(Locale.getDefault(),
                            "₹%.1fk", val / 1000)
                        : String.format(Locale.getDefault(),
                            "₹%.0f", val);
                    canvas.drawText(vLabel, px[i], py[i] - 12f, valuePaint);
                }
            }
        }
    }

    private static class AnalyticsProductAdapter extends RecyclerView.Adapter<AnalyticsProductAdapter.ViewHolder> {
        private List<ProductModel> products = new ArrayList<>();
        private Map<String, AnalyticsModel> analyticsMap = new HashMap<>();

        void setProducts(List<ProductModel> products, Map<String, AnalyticsModel> analyticsMap) {
            this.products = new ArrayList<>(products);
            this.analyticsMap = analyticsMap != null ? analyticsMap : new HashMap<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemProductBinding binding = ItemProductBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProductModel product = products.get(position);
            holder.binding.productName.setText(product.getName());
            holder.binding.productCategory.setText(product.getCategory());
            holder.binding.productStock.setText(product.getStockDisplay());
            holder.binding.productPrice.setText(String.format(Locale.getDefault(), "₹%.2f", product.getPrice()));

            AnalyticsModel analyticsModel = analyticsMap.get(product.getBarcode());
            if (analyticsModel != null) {
                holder.salesCountText.setVisibility(View.VISIBLE);
                holder.revenueText.setVisibility(View.VISIBLE);

                // Sales count
                long salesCount = analyticsModel.getSalesCount();
                holder.salesCountText.setText(salesCount + " sales");

                // Revenue
                double revenue = analyticsModel.getTotalRevenue();
                holder.revenueText.setText(String.format(
                    java.util.Locale.getDefault(), "₹%.2f revenue", revenue));
            } else {
                holder.salesCountText.setVisibility(View.GONE);
                holder.revenueText.setVisibility(View.GONE);
            }

            android.graphics.Bitmap bmp = product.getImageBitmap();
            if (bmp != null) {
                holder.binding.productImage.setImageBitmap(bmp);
            } else {
                holder.binding.productImage.setImageResource(R.drawable.ic_image_placeholder);
            }
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemProductBinding binding;
            TextView salesCountText;
            TextView revenueText;
            ViewHolder(ItemProductBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                this.salesCountText = binding.salesCountText;
                this.revenueText = binding.revenueText;
            }
        }
    }
}
