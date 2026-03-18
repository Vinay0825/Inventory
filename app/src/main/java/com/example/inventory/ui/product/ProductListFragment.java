package com.example.inventory.ui.product;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentProductListBinding;
import com.example.inventory.databinding.ItemProductBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.utils.CategoryHelper;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductListFragment extends Fragment {

    private FragmentProductListBinding binding;
    private ProductViewModel viewModel;
    private ProductAdapter adapter;
    private String selectedFilter = "All";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProductListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        setupRecyclerView();
        setupSearch();
        setupFilterChips();

        binding.addProductFab.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.addProductFragment)
        );

        viewModel.getAllProducts().observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                adapter.setFullList(products);
                applyFilters();
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(product -> {
            Bundle args = new Bundle();
            args.putString("barcode", product.getBarcode());
            Navigation.findNavController(binding.getRoot()).navigate(R.id.productDetailFragment, args);
        });
        binding.productRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.productRecyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("settings").document("shopInfo")
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;

                    if (documentSnapshot.exists()) {
                        String shopType = documentSnapshot.getString("shopType");
                        List<String> categories = CategoryHelper.getCategoriesForShopType(shopType);
                        if (categories != null) {
                            for (String category : categories) {
                                Chip chip = new Chip(requireContext());
                                chip.setText(category);
                                chip.setCheckable(true);
                                chip.setClickable(true);
                                binding.filterChipGroup.addView(chip);
                            }
                        }
                    }
                });

        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding == null) return;

            if (checkedIds.isEmpty()) {
                selectedFilter = "All";
                binding.chipAll.setChecked(true);
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipAll) {
                    selectedFilter = "All";
                } else if (id == R.id.chipLowStock) {
                    selectedFilter = "Low Stock";
                } else {
                    Chip chip = group.findViewById(id);
                    if (chip != null) {
                        selectedFilter = chip.getText().toString();
                    }
                }
            }
            applyFilters();
        });
    }

    private void applyFilters() {
        if (binding == null) return;
        String query = binding.searchEditText.getText().toString().toLowerCase();
        adapter.filter(query, selectedFilter);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // --- Adapter ---

    private static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        private List<ProductModel> products = new ArrayList<>();
        private List<ProductModel> fullList = new ArrayList<>();
        private final OnProductClickListener listener;

        interface OnProductClickListener {
            void onProductClick(ProductModel product);
        }

        ProductAdapter(OnProductClickListener listener) {
            this.listener = listener;
        }

        void setFullList(List<ProductModel> products) {
            this.fullList = products;
        }

        void filter(String query, String categoryFilter) {
            List<ProductModel> filtered = new ArrayList<>();
            for (ProductModel p : fullList) {
                boolean matchesQuery = p.getName().toLowerCase().contains(query) || p.getBarcode().contains(query);
                boolean matchesFilter;

                if (categoryFilter.equals("All")) {
                    matchesFilter = true;
                } else if (categoryFilter.equals("Low Stock")) {
                    if (ProductModel.isDecimalUnit(p.getUnit())) {
                        matchesFilter = p.getCurrentStockDecimal() < p.getLowStockThreshold();
                    } else {
                        matchesFilter = p.getCurrentStock() < p.getLowStockThreshold();
                    }
                } else {
                    matchesFilter = categoryFilter.equals(p.getCategory());
                }

                if (matchesQuery && matchesFilter) {
                    filtered.add(p);
                }
            }
            this.products = filtered;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemProductBinding binding = ItemProductBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProductModel product = products.get(position);
            holder.binding.productName.setText(product.getName());
            holder.binding.productCategory.setText(product.getCategory());
            holder.binding.productBarcode.setText(product.getBarcode());
            holder.binding.productStock.setText(product.getStockDisplay());
            holder.binding.productPrice.setText(
                    String.format(Locale.getDefault(), "₹%.2f", product.getPrice()));

            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .circleCrop()
                    .into(holder.binding.productImage);

            // New feature: Stock status badge
            double stock = ProductModel.isDecimalUnit(product.getUnit()) ?
                    product.getCurrentStockDecimal() : product.getCurrentStock();
            int threshold = product.getLowStockThreshold();

            if (stock <= 0) {
                holder.binding.stockBadge.setText("OUT");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFEE2E2));
                holder.binding.stockBadge.setTextColor(0xFFDC2626);
            } else if (stock < threshold) {
                holder.binding.stockBadge.setText("LOW");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFEF3C7));
                holder.binding.stockBadge.setTextColor(0xFFD97706);
            } else if (stock < threshold * 2) {
                holder.binding.stockBadge.setText("MID");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFEF3C7));
                holder.binding.stockBadge.setTextColor(0xFFB45309); // Slightly different shade of amber/orange
            } else {
                holder.binding.stockBadge.setText("OK");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFD1FAE5));
                holder.binding.stockBadge.setTextColor(0xFF059669);
            }

            boolean isLow = stock < threshold;
            holder.binding.productStock.setTextColor(isLow ? 0xFFFF0000 : 0xFF00C896);

            holder.itemView.setOnClickListener(v -> listener.onProductClick(product));
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
