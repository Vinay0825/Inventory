package com.example.inventory.ui.product;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.inventory.MainActivity;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentProductDetailBinding;
import com.example.inventory.model.ProductModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ProductDetailFragment extends Fragment {

    private FragmentProductDetailBinding binding;
    private String barcode;
    private String userId;
    private FirebaseFirestore db;
    private MiniChartView miniChartView;
    private ProductModel currentProduct;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            barcode = getArguments().getString("barcode");
        }
        userId = FirebaseAuth.getInstance().getUid();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProductDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (barcode == null || userId == null) {
            Toast.makeText(getContext(), "Error: Missing product data", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(view).popBackStack();
            return;
        }

        setupCharts();
        setupButtons();
        loadData();
    }

    private void setupCharts() {
        if (binding.miniChartContainer instanceof FrameLayout) {
            miniChartView = new MiniChartView(requireContext());
            ((FrameLayout) binding.miniChartContainer).addView(miniChartView);
        }
    }

    private void setupButtons() {
        binding.sellButton.setOnClickListener(v -> {
            if (currentProduct != null) {
                com.example.inventory.MainActivity activity =
                    (com.example.inventory.MainActivity) requireActivity();
                activity.showScanResultForProduct(currentProduct);
            }
        });
        
        binding.restockButton.setOnClickListener(v -> {
            if (currentProduct != null) {
                com.example.inventory.MainActivity activity =
                    (com.example.inventory.MainActivity) requireActivity();
                activity.showScanResultForProduct(currentProduct);
            }
        });
        
        binding.editButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("barcode", barcode);
            Navigation.findNavController(v).navigate(R.id.editProductFragment, args);
        });
    }

    private void loadData() {
        db.collection("users").document(userId).collection("products").document(barcode)
                .addSnapshotListener((snapshot, error) -> {
                    if (binding == null || snapshot == null || !snapshot.exists()) return;
                    ProductModel product = snapshot.toObject(ProductModel.class);
                    if (product != null) {
                        updateProductUI(product);
                    }
                });

        db.collection("users").document(userId).collection("analytics").document(barcode)
                .addSnapshotListener((snapshot, error) -> {
                    if (binding == null || snapshot == null || !snapshot.exists()) {
                        showNoAnalyticsUI();
                        return;
                    }
                    updateAnalyticsUI(snapshot);
                });
    }

    private void updateProductUI(ProductModel product) {
        this.currentProduct = product;
        binding.productName.setText(product.getName());
        binding.productCategory.setText(product.getCategory() + " • " + product.getUnit());
        binding.barcodeChip.setText(product.getBarcode());
        binding.stockCount.setText(String.valueOf(product.getCurrentStock()));
        binding.sellPrice.setText(String.format(Locale.getDefault(), "₹%.2f", product.getPrice()));
        binding.supplierValue.setText(product.getSupplier() != null ? product.getSupplier() : "N/A");

        Glide.with(this)
                .load(product.getImageUrl())
                .placeholder(R.drawable.ic_image_placeholder)
                .circleCrop()
                .into(binding.productImage);

        if (product.getExpiryDate() != null) {
            long diffInMillis = product.getExpiryDate().toDate().getTime() - new Date().getTime();
            long daysToExpiry = TimeUnit.MILLISECONDS.toDays(diffInMillis);
            
            if (daysToExpiry <= 30) {
                binding.expiryWarning.setVisibility(View.VISIBLE);
                String warning = daysToExpiry < 0 ? "Product expired!" : "Expires in " + daysToExpiry + " days";
                binding.expiryWarningText.setText(warning);
            } else {
                binding.expiryWarning.setVisibility(View.GONE);
            }
        } else {
            binding.expiryWarning.setVisibility(View.GONE);
        }
    }

    private void updateAnalyticsUI(DocumentSnapshot snapshot) {
        Long totalSold = snapshot.getLong("totalUnitsSold");
        Double totalRevenue = snapshot.getDouble("totalRevenue");
        Map<String, Long> dailySales = (Map<String, Long>) snapshot.get("dailySales");

        binding.totalSoldValue.setText(String.valueOf(totalSold != null ? totalSold : 0));
        binding.totalRevenueValue.setText(String.format(Locale.getDefault(), "₹%.2f", totalRevenue != null ? totalRevenue : 0.0));

        if (dailySales != null && !dailySales.isEmpty()) {
            processDailySales(dailySales);
        } else {
            showNoAnalyticsUI();
        }
    }

    private void processDailySales(Map<String, Long> dailySales) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        List<Double> chartData = new ArrayList<>();
        double totalLast7Days = 0;

        List<String> last7DaysKeys = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Calendar c = (Calendar) cal.clone();
            c.add(Calendar.DAY_OF_YEAR, -i);
            last7DaysKeys.add(sdf.format(c.getTime()));
        }
        Collections.reverse(last7DaysKeys);

        for (String key : last7DaysKeys) {
            long val = dailySales.containsKey(key) ? dailySales.get(key) : 0L;
            chartData.add((double) val);
            totalLast7Days += val;
        }

        if (miniChartView != null) {
            miniChartView.setData(chartData);
        }

        double avgPerDay = totalLast7Days / 7.0;
        binding.suggestionBox.setVisibility(View.VISIBLE);

        int currentStock = 0;
        try {
            currentStock = Integer.parseInt(binding.stockCount.getText().toString());
        } catch (Exception ignored) {}

        if (avgPerDay > 0) {
            int daysLeft = (int) (currentStock / avgPerDay);
            int suggestedRestock = (int) (avgPerDay * 7) - currentStock;
            if (suggestedRestock < 0) suggestedRestock = 0;
            String suggestion = String.format(Locale.getDefault(),
                "High-frequency seller (avg %.1f units/day).\nDays of stock left: ~%d days.\nSuggested restock: %d units (7-day buffer)",
                avgPerDay, daysLeft, suggestedRestock);
            binding.suggestionText.setText(suggestion);
        } else {
            long totalOverall = 0;
            try { totalOverall = Long.parseLong(binding.totalSoldValue.getText().toString()); } catch (Exception ignored) {}
            if (totalOverall > 0) {
                binding.suggestionText.setText("Slow mover. Consider reducing stock threshold.");
            } else {
                binding.suggestionText.setText("No recent sales data. Start selling to get suggestions.");
            }
        }
    }

    private void showNoAnalyticsUI() {
        if (binding == null) return;
        binding.totalSoldValue.setText("0");
        binding.totalRevenueValue.setText("₹0.00");
        binding.suggestionBox.setVisibility(View.VISIBLE);
        binding.suggestionText.setText("No sales data yet. Start selling to get suggestions.");
        if (miniChartView != null) {
            miniChartView.setData(new ArrayList<>(Collections.nCopies(7, 0.0)));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class MiniChartView extends View {
        private List<Double> data = new ArrayList<>();
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public MiniChartView(Context context) {
            super(context);
            barPaint.setColor(0xFF00C896);
            textPaint.setColor(0xFF757575);
            textPaint.setTextSize(24f);
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
            float padding = 20f;
            float labelSpace = 30f;
            float chartHeight = height - padding * 2 - labelSpace;
            float barWidth = (width - (padding * 2)) / 7f * 0.7f;
            float spacing = (width - (padding * 2)) / 7f * 0.3f;
            double maxVal = 0;
            for (Double d : data) if (d > maxVal) maxVal = d;
            if (maxVal == 0) maxVal = 1;
            for (int i = 0; i < data.size(); i++) {
                float barHeight = (float) (data.get(i) / maxVal * chartHeight);
                float left = padding + i * (barWidth + spacing) + spacing / 2;
                float top = height - padding - labelSpace - barHeight;
                float right = left + barWidth;
                float bottom = height - padding - labelSpace;
                RectF rect = new RectF(left, top, right, bottom);
                canvas.drawRoundRect(rect, 4f, 4f, barPaint);
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -(data.size() - 1 - i));
                String dayLabel = new SimpleDateFormat("E", Locale.getDefault()).format(cal.getTime()).substring(0, 1);
                canvas.drawText(dayLabel, left + barWidth / 2, height - 5f, textPaint);
            }
        }
    }
}
