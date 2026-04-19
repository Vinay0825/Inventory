package com.example.inventory.ui.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentDashboardBinding;
import com.example.inventory.databinding.ItemDashboardBestSellerBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.model.SaleModel;
import com.example.inventory.viewmodel.DashboardViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private DashboardViewModel viewModel;
    private ChartView chartView;
    private com.google.firebase.firestore.ListenerRegistration chartListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(super.getContext() != null ? view : null, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // Date Tag
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        binding.currentDateTag.setText("Today — " + sdf.format(new Date()));

        // BUG 2 FIX: Move low stock observer to onViewCreated
        viewModel.getLowStockProducts().observe(getViewLifecycleOwner(), products -> {
            if (binding == null) return;
            int count = (products != null) ? products.size() : 0;
            binding.lowStockCountValue.setText(String.valueOf(count));
            
            if (count > 0) {
                binding.lowStockCountValue.setTextColor(0xFFD32F2F);
            } else {
                binding.lowStockCountValue.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        binding.lowStockCountValue,
                        com.google.android.material.R.attr.colorOnSurface));
            }
            
            if (binding.alertBanner != null) {
                binding.alertBanner.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                if (binding.alertBannerText != null) {
                    binding.alertBannerText.setText(count + " products are low on stock!");
                }
            }
        });

        setupSummaryCards();
        setupChart();
        setupBestSellers();
        setupQuickActions();
        setupAlertBanner();

        binding.dashboardSwipeRefresh.setOnRefreshListener(() -> {
            setupSummaryCards();
            fetchWeeklySales();
            setupBestSellers();
            binding.dashboardSwipeRefresh.setRefreshing(false);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Re-run summary cards so yesterday card reappears after navigation
        setupSummaryCards();
        fetchWeeklySales();
    }

    private void setupSummaryCards() {
        viewModel.getTodayRevenue().observe(getViewLifecycleOwner(), revenue -> {
            if (binding != null) {
                binding.todaySalesValue.setText(String.format(Locale.getDefault(), "₹%.2f", revenue != null ? revenue : 0.0));
            }
        });

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            // Today's Transactions
            String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            db.collection("users").document(userId).collection("sales")
                    .whereEqualTo("dayKey", todayKey)
                    .whereEqualTo("voided", false)
                    .addSnapshotListener((value, error) -> {
                        if (binding == null) return;
                        if (value != null) {
                            binding.transactionCountValue.setText(String.valueOf(value.size()));
                        }
                    });

            // Total Products
            db.collection("users").document(userId).collection("products")
                    .addSnapshotListener((value, error) -> {
                        if (binding == null) return;
                        if (value != null) {
                            binding.totalProductsValue.setText(String.valueOf(value.size()));
                        }
                    });

            // Yesterday's sales summary
            Calendar yCal = Calendar.getInstance();
            yCal.add(Calendar.DAY_OF_YEAR, -1);
            String yesterdayKey = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", Locale.getDefault())
                .format(yCal.getTime());

            db.collection("users").document(userId)
                .collection("sales")
                .whereEqualTo("dayKey", yesterdayKey)
                .whereEqualTo("voided", false)
                .get()
                .addOnSuccessListener(snap -> {
                    if (binding == null) return;
                    double yTotal = 0;
                    int yCount = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snap.getDocuments()) {
                        SaleModel s = doc.toObject(SaleModel.class);
                        if (s != null) {
                            yTotal += s.getTotalAmount();
                            yCount++;
                        }
                    }
                    final String summary = String.format(
                        Locale.getDefault(),
                        "₹%.2f  (%d sales)", yTotal, yCount);
                    binding.yesterdaySummaryText.setText(summary);
                    binding.yesterdaySummaryCard.setVisibility(View.VISIBLE);
                });
        }
    }

    private void setupChart() {
        // Always create and add ChartView regardless of instanceof check
        chartView = new ChartView(requireContext());
        binding.chartView.removeAllViews();
        binding.chartView.addView(chartView);
        fetchWeeklySales();
    }

    private void fetchWeeklySales() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // Remove existing listener before adding a new one
        if (chartListener != null) {
            chartListener.remove();
            chartListener = null;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Timestamp sevenDaysAgo = new Timestamp(cal.getTime());

        chartListener = FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("sales")
            .whereGreaterThanOrEqualTo("soldAt", sevenDaysAgo)
            .whereEqualTo("voided", false)
            .orderBy("soldAt", Query.Direction.ASCENDING)
            .addSnapshotListener((value, error) -> {
                if (binding == null || chartView == null) return;
                if (value == null) return;

                Map<String, Double> dailyTotals = new TreeMap<>();
                SimpleDateFormat keySdf = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.getDefault());

                // Pre-fill 7 days with 0
                for (int i = 6; i >= 0; i--) {
                    Calendar c = Calendar.getInstance();
                    c.add(Calendar.DAY_OF_YEAR, -i);
                    dailyTotals.put(keySdf.format(c.getTime()), 0.0);
                }

                for (SaleModel sale : value.toObjects(SaleModel.class)) {
                    String key = sale.getDayKey();
                    if (key != null && dailyTotals.containsKey(key)) {
                        dailyTotals.put(key,
                            dailyTotals.get(key) + sale.getTotalAmount());
                    }
                }

                List<Double> data = new ArrayList<>(dailyTotals.values());
                chartView.setData(data);
            });
    }

    private void setupBestSellers() {
        viewModel.getBestSellers().observe(getViewLifecycleOwner(), analytics -> {
            if (binding != null && analytics != null) {
                viewModel.getAllProducts().observe(getViewLifecycleOwner(), products -> {
                    if (binding == null || products == null) return;
                    binding.bestSellersContainer.removeAllViews();
                    
                    int count = 0;
                    for (int i = 0; i < analytics.size() && count < 3; i++) {
                        String barcode = analytics.get(i).getBarcode();
                        ProductModel product = null;
                        for (ProductModel p : products) {
                            if (p.getBarcode().equals(barcode)) {
                                product = p;
                                break;
                            }
                        }
                        
                        if (product != null) {
                            addBestSellerItem(product, analytics.get(i).getTotalUnitsSold());
                            count++;
                        }
                    }
                });
            }
        });
    }

    private void addBestSellerItem(ProductModel product, double unitsSold) {
        ItemDashboardBestSellerBinding itemBinding = ItemDashboardBestSellerBinding.inflate(
                getLayoutInflater(), binding.bestSellersContainer, false);
        
        itemBinding.bestSellerName.setText(product.getName());
        itemBinding.bestSellerCategory.setText(product.getCategory());
        
        if (ProductModel.isDecimalUnit(product.getUnit())) {
            itemBinding.bestSellerSoldQty.setText(String.format(Locale.getDefault(), "%.2f %s sold", unitsSold, product.getUnit()));
        } else {
            itemBinding.bestSellerSoldQty.setText(String.valueOf((long)unitsSold) + " sold");
        }

        android.graphics.Bitmap bmp = product.getImageBitmap();
        if (bmp != null) {
            itemBinding.bestSellerImage.setImageBitmap(bmp);
        } else {
            itemBinding.bestSellerImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        itemBinding.getRoot().setOnClickListener(v -> {
            String barcode = product.getBarcode();
            if (barcode == null || barcode.isEmpty()) return;

            String userId = com.google.firebase.auth.FirebaseAuth
                .getInstance().getCurrentUser().getUid();
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("products").document(barcode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    if (doc == null || !doc.exists()) return;
                    com.example.inventory.model.ProductModel p =
                        doc.toObject(com.example.inventory.model.ProductModel.class);
                    if (p == null) return;
                    android.os.Bundle args = new android.os.Bundle();
                    args.putString("barcode", barcode);
                    androidx.navigation.Navigation
                        .findNavController(v)
                        .navigate(R.id.action_dashboard_to_productDetail, args);
                });
        });

        binding.bestSellersContainer.addView(itemBinding.getRoot());
    }

    private void setupQuickActions() {
        binding.btnQuickSell.setOnClickListener(v -> {
            View fab = requireActivity().findViewById(R.id.fab_scan);
            if (fab != null) fab.performClick();
        });
        binding.btnQuickRestock.setOnClickListener(v -> {
            View fab = requireActivity().findViewById(R.id.fab_scan);
            if (fab != null) fab.performClick();
        });
        binding.btnQuickAddProduct.setOnClickListener(v -> 
                Navigation.findNavController(v).navigate(R.id.addProductFragment));
    }

    private void setupAlertBanner() {
        if (binding.alertBanner != null) {
            binding.alertBanner.setOnClickListener(v -> 
                    Navigation.findNavController(v).navigate(R.id.navigation_alerts));
        }
    }

    @Override
    public void onDestroyView() {
        if (chartListener != null) {
            chartListener.remove();
            chartListener = null;
        }
        super.onDestroyView();
        binding = null;
    }

    private static class ChartView extends View {
        private List<Double> data = new ArrayList<>();
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChartView(Context context) {
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
            valuePaint.setTextSize(24f);
            valuePaint.setTextAlign(Paint.Align.CENTER);

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
            String[] dayNames = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            String[] labels = new String[n];
            for (int i = 0; i < n; i++) {
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1 - i));
                labels[i] =
                    dayNames[c.get(java.util.Calendar.DAY_OF_WEEK) - 1];
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
}
