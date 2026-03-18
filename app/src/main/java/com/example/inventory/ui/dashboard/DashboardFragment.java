package com.example.inventory.ui.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
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
import java.util.Collections;
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

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        // Date Tag
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        binding.currentDateTag.setText("Today — " + sdf.format(new Date()));

        setupSummaryCards();
        setupChart();
        setupBestSellers();
        setupQuickActions();
        setupAlertBanner();
    }

    private void setupSummaryCards() {
        viewModel.getTodayRevenue().observe(getViewLifecycleOwner(), revenue -> {
            if (binding != null) {
                binding.todaySalesValue.setText(String.format(Locale.getDefault(), "₹%.2f", revenue != null ? revenue : 0.0));
            }
        });

        // Using a more direct approach for counts since original VM was slightly different
        // In a real app, these should ideally come from the ViewModel as livedata
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            
            // Today's Transactions
            String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            db.collection("users").document(userId).collection("sales")
                    .whereEqualTo("dayKey", todayKey)
                    .whereEqualTo("voided", false)
                    .addSnapshotListener((value, error) -> {
                        if (binding != null && value != null) {
                            binding.transactionCountValue.setText(String.valueOf(value.size()));
                        }
                    });

            // Total Products
            db.collection("users").document(userId).collection("products")
                    .addSnapshotListener((value, error) -> {
                        if (binding != null && value != null) {
                            binding.totalProductsValue.setText(String.valueOf(value.size()));
                        }
                    });

            // Low Stock Count
            viewModel.getLowStockProducts().observe(getViewLifecycleOwner(), products -> {
                if (binding != null && products != null) {
                    int count = products.size();
                    binding.lowStockCountValue.setText(String.valueOf(count));
                    binding.lowStockCountValue.setTextColor(count > 0 ? 0xFFFF0000 : 0xFF000000);
                    
                    // Update alert banner visibility
                    if (binding.alertBanner != null) {
                        binding.alertBanner.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                        if (binding.alertBannerText != null) {
                            binding.alertBannerText.setText(count + " products are low on stock!");
                        }
                    }
                }
            });
        }
    }

    private void setupChart() {
        if (binding.chartView instanceof FrameLayout) {
            chartView = new ChartView(requireContext());
            ((FrameLayout) binding.chartView).addView(chartView);
            fetchWeeklySales();
        }
    }

    private void fetchWeeklySales() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Timestamp sevenDaysAgo = new Timestamp(cal.getTime());

        FirebaseFirestore.getInstance().collection("users").document(userId).collection("sales")
                .whereGreaterThanOrEqualTo("soldAt", sevenDaysAgo)
                .whereEqualTo("voided", false)
                .orderBy("soldAt", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (value != null && chartView != null) {
                        Map<String, Double> dailyTotals = new TreeMap<>();
                        SimpleDateFormat keySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        
                        // Initialize last 7 days with 0
                        for (int i = 0; i < 7; i++) {
                            Calendar c = Calendar.getInstance();
                            c.add(Calendar.DAY_OF_YEAR, -i);
                            dailyTotals.put(keySdf.format(c.getTime()), 0.0);
                        }

                        for (SaleModel sale : value.toObjects(SaleModel.class)) {
                            if (dailyTotals.containsKey(sale.getDayKey())) {
                                dailyTotals.put(sale.getDayKey(), dailyTotals.get(sale.getDayKey()) + sale.getTotalAmount());
                            }
                        }
                        
                        List<Double> data = new ArrayList<>(dailyTotals.values());
                        chartView.setData(data);
                    }
                });
    }

    private void setupBestSellers() {
        viewModel.getBestSellers().observe(getViewLifecycleOwner(), analytics -> {
            if (binding != null && analytics != null) {
                binding.bestSellersContainer.removeAllViews();
                // Original VM returns analytics, but we need to match with products
                // In a simplified version, let's assume we can fetch products or use existing list
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

    private void addBestSellerItem(ProductModel product, long unitsSold) {
        ItemDashboardBestSellerBinding itemBinding = ItemDashboardBestSellerBinding.inflate(
                getLayoutInflater(), binding.bestSellersContainer, false);
        
        itemBinding.bestSellerName.setText(product.getName());
        itemBinding.bestSellerCategory.setText(product.getCategory());
        itemBinding.bestSellerSoldQty.setText(unitsSold + " sold");

        Glide.with(this)
                .load(product.getImageUrl())
                .placeholder(R.drawable.ic_image_placeholder)
                .circleCrop()
                .into(itemBinding.bestSellerImage);

        binding.bestSellersContainer.addView(itemBinding.getRoot());
    }

    private void setupQuickActions() {
        binding.btnQuickSell.setOnClickListener(v -> {
            // Trigger the scan FAB — same as tapping the center scan button
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
        super.onDestroyView();
        binding = null;
    }

    // --- Custom Chart View ---
    private static class ChartView extends View {
        private List<Double> data = new ArrayList<>();
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public ChartView(Context context) {
            super(context);
            barPaint.setColor(0xFF00C896);
            accentPaint.setColor(0xFFFF6B35);
            textPaint.setColor(0xFF757575);
            textPaint.setTextSize(30f);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        public void setData(List<Double> newData) {
            this.data = newData;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (data == null || data.isEmpty()) return;

            float width = getWidth();
            float height = getHeight();
            float padding = 40f;
            float barWidth = (width - (padding * 2)) / 7f * 0.6f;
            float spacing = (width - (padding * 2)) / 7f * 0.4f;

            double maxVal = 0;
            int maxIdx = 0;
            for (int i = 0; i < data.size(); i++) {
                if (data.get(i) > maxVal) {
                    maxVal = data.get(i);
                    maxIdx = i;
                }
            }
            if (maxVal == 0) maxVal = 1; // avoid div by zero

            float chartHeight = height - padding * 2 - 40f;

            for (int i = 0; i < data.size(); i++) {
                float barHeight = (float) (data.get(i) / maxVal * chartHeight);
                float left = padding + i * (barWidth + spacing) + spacing / 2;
                float top = height - padding - 40f - barHeight;
                float right = left + barWidth;
                float bottom = height - padding - 40f;

                RectF rect = new RectF(left, top, right, bottom);
                // Last item is "Today" in our fetched list
                canvas.drawRoundRect(rect, 8f, 8f, i == data.size() - 1 ? accentPaint : barPaint);

                // Labels
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -(data.size() - 1 - i));
                String dayLabel = new SimpleDateFormat("E", Locale.getDefault()).format(cal.getTime()).substring(0, 1);
                canvas.drawText(dayLabel, left + barWidth / 2, height - 10f, textPaint);

                // Max value text
                if (i == maxIdx && maxVal > 0) {
                    textPaint.setFakeBoldText(true);
                    canvas.drawText(String.format(Locale.getDefault(), "₹%.0f", data.get(i)), left + barWidth / 2, top - 10f, textPaint);
                    textPaint.setFakeBoldText(false);
                }
            }
        }
    }
}
