package com.example.inventory.ui.analytics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentAnalyticsBinding;
import com.example.inventory.databinding.ItemProductBinding;
import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;
import com.example.inventory.utils.StockRulesEngine;
import com.example.inventory.viewmodel.DashboardViewModel;
import com.example.inventory.viewmodel.ProductViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalyticsFragment extends Fragment {

    private FragmentAnalyticsBinding binding;
    private DashboardViewModel dashboardViewModel;
    private ProductViewModel productViewModel;
    private AnalyticsProductAdapter bestSellersAdapter;
    private AnalyticsProductAdapter slowMoversAdapter;

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

        setupRecyclerViews();
        observeData();
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
        productViewModel.getAllProducts().observe(getViewLifecycleOwner(), products -> {
            if (products == null) return;
            List<ProductModel> productsCopy = new ArrayList<>(products);
            
            dashboardViewModel.getBestSellers().observe(getViewLifecycleOwner(), analyticsList -> {
                if (analyticsList == null) return;

                List<AnalyticsModel> copy = new ArrayList<>(analyticsList);
                Collections.sort(copy, (a, b) ->
                    Long.compare(b.getTotalUnitsSold(), a.getTotalUnitsSold()));

                List<ProductModel> bestSellers = new ArrayList<>();
                List<ProductModel> slowMovers = new ArrayList<>();
                Map<String, Integer> categoryCount = new HashMap<>();

                for (AnalyticsModel am : copy) {
                    ProductModel pm = findProduct(productsCopy, am.getBarcode());
                    if (pm != null) {
                        if (StockRulesEngine.isBestSeller(am, copy)) {
                            bestSellers.add(pm);
                        }
                        if (StockRulesEngine.isSlowMover(am, pm)) {
                            slowMovers.add(pm);
                        }
                        
                        String cat = pm.getCategory() != null ? pm.getCategory() : "Other";
                        categoryCount.put(cat, categoryCount.getOrDefault(cat, 0) + 1);
                    }
                }

                bestSellersAdapter.setProducts(bestSellers);
                slowMoversAdapter.setProducts(slowMovers);
                updateCategoryBreakdown(categoryCount);
                
                binding.analyticsSummaryText.setText(String.format(Locale.getDefault(), 
                    "You have %d best sellers and %d slow movers identified across %d categories.",
                    bestSellers.size(), slowMovers.size(), categoryCount.size()));
            });
        });
    }

    private ProductModel findProduct(List<ProductModel> products, String barcode) {
        for (ProductModel p : products) {
            if (p.getBarcode().equals(barcode)) return p;
        }
        return null;
    }

    private void updateCategoryBreakdown(Map<String, Integer> categoryCount) {
        binding.categoryBreakdownContainer.removeAllViews();
        for (Map.Entry<String, Integer> entry : categoryCount.entrySet()) {
            TextView tv = new TextView(getContext());
            tv.setText(entry.getKey() + ": " + entry.getValue() + " products");
            tv.setPadding(0, 8, 0, 8);
            binding.categoryBreakdownContainer.addView(tv);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class AnalyticsProductAdapter extends RecyclerView.Adapter<AnalyticsProductAdapter.ViewHolder> {
        private List<ProductModel> products = new ArrayList<>();

        void setProducts(List<ProductModel> products) {
            this.products = new ArrayList<>(products);
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

            Glide.with(holder.itemView.getContext())
                 .load(product.getImageUrl())
                 .placeholder(R.drawable.ic_image_placeholder)
                 .error(R.drawable.ic_image_placeholder)
                 .circleCrop()
                 .into(holder.binding.productImage);
        }

        @Override
        public int getItemCount() {
            return products.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ItemProductBinding binding;
            ViewHolder(ItemProductBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
