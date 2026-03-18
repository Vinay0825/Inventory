package com.example.inventory.ui.alerts;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentAlertsBinding;
import com.example.inventory.databinding.ItemAlertBinding;
import com.example.inventory.model.ProductModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertsFragment extends Fragment {

    private FragmentAlertsBinding binding;
    private FirebaseFirestore db;
    private String userId;
    private AlertsAdapter adapter;
    private final Map<String, Double> restockSuggestions = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();

        setupRecyclerView();
        loadLowStockProducts();
    }

    private void setupRecyclerView() {
        adapter = new AlertsAdapter(new ArrayList<>(), restockSuggestions, barcode -> {
            Bundle bundle = new Bundle();
            bundle.putString("barcode", barcode);
            try {
                Navigation.findNavController(requireView()).navigate(R.id.restockFragment, bundle);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Restock action for " + barcode, Toast.LENGTH_SHORT).show();
            }
        });
        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setAdapter(adapter);
    }

    private void loadLowStockProducts() {
        if (userId == null) return;

        db.collection("users").document(userId).collection("products")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && binding != null) {
                        List<ProductModel> lowStockList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            ProductModel product = document.toObject(ProductModel.class);
                            double stock = ProductModel.isDecimalUnit(product.getUnit()) ? 
                                    product.getCurrentStockDecimal() : product.getCurrentStock();
                            
                            if (stock < product.getLowStockThreshold()) {
                                lowStockList.add(product);
                            }
                        }

                        // Sort by ratio: currentStock / lowStockThreshold (ascending - most critical first)
                        Collections.sort(lowStockList, (p1, p2) -> {
                            double r1 = getStockRatio(p1);
                            double r2 = getStockRatio(p2);
                            return Double.compare(r1, r2);
                        });

                        updateHeader(lowStockList.size());
                        adapter.setProducts(lowStockList);
                        
                        if (lowStockList.isEmpty()) {
                            binding.emptyState.setVisibility(View.VISIBLE);
                            binding.alertsRecyclerView.setVisibility(View.GONE);
                        } else {
                            binding.emptyState.setVisibility(View.GONE);
                            binding.alertsRecyclerView.setVisibility(View.VISIBLE);
                            fetchAnalyticsForProducts(lowStockList);
                        }
                    }
                });
    }

    private double getStockRatio(ProductModel product) {
        double stock = ProductModel.isDecimalUnit(product.getUnit()) ? 
                product.getCurrentStockDecimal() : product.getCurrentStock();
        return stock / Math.max(1, product.getLowStockThreshold());
    }

    private void updateHeader(int count) {
        if (binding == null) return;

        if (count > 0) {
            binding.alertHeaderBanner.setCardBackgroundColor(Color.parseColor("#D32F2F")); // Material Red 700
            binding.alertHeaderText.setText(getString(R.string.alert_action_required, count));
        } else {
            binding.alertHeaderBanner.setCardBackgroundColor(Color.parseColor("#388E3C")); // Material Green 700
            binding.alertHeaderText.setText(getString(R.string.alert_stock_healthy));
        }
    }

    private void fetchAnalyticsForProducts(List<ProductModel> products) {
        List<String> last7Days = getLast7DaysKeys();
        for (ProductModel product : products) {
            String barcode = product.getBarcode();
            db.collection("users").document(userId).collection("analytics").document(barcode)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (binding == null) return;
                        double suggestion;
                        if (documentSnapshot.exists()) {
                            Object dsObj = documentSnapshot.get("dailySales");
                            if (dsObj instanceof Map) {
                                Map<String, Long> dailySales = (Map<String, Long>) dsObj;
                                long totalSalesLast7Days = 0;
                                for (String dayKey : last7Days) {
                                    Long val = dailySales.get(dayKey);
                                    if (val != null) {
                                        totalSalesLast7Days += val;
                                    }
                                }
                                suggestion = Math.max(totalSalesLast7Days, product.getLowStockThreshold() * 2.0);
                            } else {
                                suggestion = product.getLowStockThreshold() * 2.0;
                            }
                        } else {
                            suggestion = product.getLowStockThreshold() * 2.0;
                        }
                        restockSuggestions.put(barcode, suggestion);
                        adapter.notifyItemChanged(products.indexOf(product));
                    });
        }
    }

    private List<String> getLast7DaysKeys() {
        List<String> keys = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 7; i++) {
            keys.add(sdf.format(cal.getTime()));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }
        return keys;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.ViewHolder> {
        private final List<ProductModel> products;
        private final Map<String, Double> suggestions;
        private final OnRestockClickListener listener;

        interface OnRestockClickListener {
            void onRestockClick(String barcode);
        }

        AlertsAdapter(List<ProductModel> products, Map<String, Double> suggestions, OnRestockClickListener listener) {
            this.products = products;
            this.suggestions = suggestions;
            this.listener = listener;
        }

        void setProducts(List<ProductModel> newProducts) {
            products.clear();
            products.addAll(newProducts);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAlertBinding b = ItemAlertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(b);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProductModel product = products.get(position);
            holder.binding.alertProductName.setText(product.getName());
            
            double stock = ProductModel.isDecimalUnit(product.getUnit()) ? 
                    product.getCurrentStockDecimal() : product.getCurrentStock();
            
            String stockFormatted = ProductModel.isDecimalUnit(product.getUnit()) ? 
                    String.format(Locale.getDefault(), "%.2f", stock) : String.valueOf((int)stock);
            
            String stockText = holder.itemView.getContext().getString(R.string.alert_stock_left, stockFormatted, product.getLowStockThreshold());
            holder.binding.alertCurrentStock.setText(stockText);

            Double suggestion = suggestions.get(product.getBarcode());
            if (suggestion != null) {
                holder.binding.alertSuggestedRestock.setText(String.format(Locale.getDefault(), 
                        "Suggest restocking %.1f units (7-day buffer)", suggestion));
            } else {
                holder.binding.alertSuggestedRestock.setText("Calculating suggestion...");
            }

            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(android.R.drawable.ic_menu_report_image)
                    .circleCrop()
                    .into(holder.binding.alertProductImage);

            holder.binding.alertRestockButton.setOnClickListener(v -> listener.onRestockClick(product.getBarcode()));
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemAlertBinding binding;
            ViewHolder(ItemAlertBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
