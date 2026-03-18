package com.example.inventory.ui.alerts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentLowStockAlertsBinding;
import com.example.inventory.databinding.ItemAlertBinding;
import com.example.inventory.model.AnalyticsModel;
import com.example.inventory.model.ProductModel;
import com.example.inventory.utils.StockRulesEngine;
import com.example.inventory.viewmodel.DashboardViewModel;
import com.example.inventory.viewmodel.ProductViewModel;

import java.util.ArrayList;
import java.util.List;

public class LowStockAlertsFragment extends Fragment {

    private FragmentLowStockAlertsBinding binding;
    private ProductViewModel productViewModel;
    private DashboardViewModel dashboardViewModel;
    private AlertsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentLowStockAlertsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        dashboardViewModel = new ViewModelProvider(this).get(DashboardViewModel.class);

        setupRecyclerView();

        productViewModel.getLowStockProducts().observe(getViewLifecycleOwner(), products -> {
            if (products == null || products.isEmpty()) {
                binding.noAlertsView.setVisibility(View.VISIBLE);
                binding.alertsRecyclerView.setVisibility(View.GONE);
                adapter.setProducts(new ArrayList<>(), new ArrayList<>());
            } else {
                binding.noAlertsView.setVisibility(View.GONE);
                binding.alertsRecyclerView.setVisibility(View.VISIBLE);
                
                dashboardViewModel.getAllProducts().observe(getViewLifecycleOwner(), allProducts -> {
                    dashboardViewModel.getBestSellers().observe(getViewLifecycleOwner(), analyticsList -> {
                        adapter.setProducts(products, analyticsList);
                    });
                });
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new AlertsAdapter(product -> {
            Bundle args = new Bundle();
            args.putString("barcode", product.getBarcode());
            Navigation.findNavController(binding.getRoot()).navigate(R.id.restockFragment, args);
        });
        binding.alertsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.alertsRecyclerView.setAdapter(adapter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class AlertsAdapter extends RecyclerView.Adapter<AlertsAdapter.ViewHolder> {
        private List<ProductModel> products = new ArrayList<>();
        private List<AnalyticsModel> analyticsList = new ArrayList<>();
        private final OnRestockClickListener listener;

        interface OnRestockClickListener {
            void onRestockClick(ProductModel product);
        }

        AlertsAdapter(OnRestockClickListener listener) {
            this.listener = listener;
        }

        void setProducts(List<ProductModel> products, List<AnalyticsModel> analyticsList) {
            this.products = products;
            this.analyticsList = analyticsList != null ? analyticsList : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemAlertBinding binding = ItemAlertBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProductModel product = products.get(position);
            holder.binding.alertProductName.setText(product.getName());
            holder.binding.alertCurrentStock.setText(product.getCurrentStock() + " left");
            
            AnalyticsModel am = findAnalytics(product.getBarcode());
            int suggested = StockRulesEngine.suggestRestock(product, am);
            holder.binding.alertSuggestedRestock.setText("Suggested restock: " + suggested + " " + product.getUnit());

            holder.binding.alertRestockButton.setOnClickListener(v -> listener.onRestockClick(product));
        }

        private AnalyticsModel findAnalytics(String barcode) {
            for (AnalyticsModel am : analyticsList) {
                if (am.getBarcode().equals(barcode)) return am;
            }
            return null;
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
