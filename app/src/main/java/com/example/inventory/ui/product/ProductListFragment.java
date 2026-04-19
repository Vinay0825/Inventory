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
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentProductListBinding;
import com.example.inventory.databinding.ItemProductBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProductListFragment extends Fragment {

    private FragmentProductListBinding binding;
    private ProductViewModel viewModel;
    private ProductAdapter adapter;
    private List<ProductModel> allProducts = new ArrayList<>();
    private String currentFilter = "all";

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

        binding.looseItemButton.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.navigation_loose_item));

        viewModel.getAllProducts().observe(getViewLifecycleOwner(), products -> {
            if (products != null) {
                allProducts = products;
                updateCategoryChips(products);
                applyFilter(currentFilter);
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new ProductAdapter(product -> {
            Bundle args = new Bundle();
            args.putString("barcode", product.getBarcode());
            Navigation.findNavController(binding.getRoot()).navigate(R.id.productDetailFragment, args);
        });
        binding.productRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        binding.productRecyclerView.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(currentFilter);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilterChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (binding == null) return;
            if (checkedIds.isEmpty()) {
                binding.chipAll.setChecked(true);
                applyFilter("all");
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipAll) {
                    applyFilter("all");
                } else if (id == R.id.chipLowStock) {
                    applyFilter("low_stock");
                } else if (id == R.id.chipLooseItems) {
                    applyFilter("loose_items");
                } else {
                    Chip chip = group.findViewById(id);
                    if (chip != null) {
                        applyFilter(chip.getText().toString());
                    }
                }
            }
        });
    }

    private void updateCategoryChips(List<ProductModel> products) {
        if (binding == null) return;
        
        // Keep the static chips
        int childCount = binding.filterChipGroup.getChildCount();
        for (int i = childCount - 1; i >= 0; i--) {
            View child = binding.filterChipGroup.getChildAt(i);
            int id = child.getId();
            if (id != R.id.chipAll && id != R.id.chipLowStock && id != R.id.chipLooseItems) {
                binding.filterChipGroup.removeViewAt(i);
            }
        }

        Set<String> categories = new HashSet<>();
        for (ProductModel p : products) {
            if (p.getCategory() != null && !p.getCategory().isEmpty()) {
                categories.add(p.getCategory());
            }
        }

        for (String category : categories) {
            Chip chip = new Chip(requireContext());
            chip.setText(category);
            chip.setCheckable(true);
            chip.setClickable(true);
            binding.filterChipGroup.addView(chip);
        }
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        if (allProducts == null) return;
        
        String query = binding.searchEditText.getText().toString().toLowerCase();
        List<ProductModel> filtered = new ArrayList<>();
        
        for (ProductModel p : allProducts) {
            boolean matchesQuery = p.getName().toLowerCase().contains(query) || p.getBarcode().contains(query);
            if (!matchesQuery) continue;

            boolean matchesFilter = false;
            switch (filter) {
                case "low_stock":
                    double stock = ProductModel.isDecimalUnit(p.getUnit()) ? p.getCurrentStockDecimal() : p.getCurrentStock();
                    int thresh = (p.getLowStockThreshold() > 0)
                            ? p.getLowStockThreshold()
                            : com.example.inventory.utils.AppPrefs.DEFAULT_THRESHOLD;
                    if (stock <= thresh) matchesFilter = true;
                    break;
                case "all":
                    matchesFilter = true;
                    break;
                case "loose_items":
                    if (p.getBarcode() != null && p.getBarcode().startsWith("LOOSE")) matchesFilter = true;
                    break;
                default:
                    // category filter
                    if (filter.equals(p.getCategory())) matchesFilter = true;
                    break;
            }

            if (matchesFilter) filtered.add(p);
        }
        
        adapter.setProducts(filtered);
        binding.emptyStateText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {
        private List<ProductModel> products = new ArrayList<>();
        private final OnProductClickListener listener;

        interface OnProductClickListener {
            void onProductClick(ProductModel product);
        }

        ProductAdapter(OnProductClickListener listener) {
            this.listener = listener;
        }

        void setProducts(List<ProductModel> products) {
            this.products = products;
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

            String b64 = product.getImageBase64();
            if (b64 != null && !b64.isEmpty()) {
                try {
                    byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                        .decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        holder.binding.productImage.setImageBitmap(bmp);
                    } else {
                        holder.binding.productImage.setImageResource(R.drawable.ic_image_placeholder);
                    }
                } catch (Exception ex) {
                    holder.binding.productImage.setImageResource(R.drawable.ic_image_placeholder);
                }
            } else {
                holder.binding.productImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            double stock = ProductModel.isDecimalUnit(product.getUnit()) ?
                    product.getCurrentStockDecimal() : product.getCurrentStock();
            int threshold = (product.getLowStockThreshold() > 0)
                    ? product.getLowStockThreshold()
                    : com.example.inventory.utils.AppPrefs.DEFAULT_THRESHOLD;

            if (stock <= 0) {
                holder.binding.stockBadge.setText("OUT");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFEE2E2));
                holder.binding.stockBadge.setTextColor(0xFFDC2626);
            } else if (stock < threshold) {
                holder.binding.stockBadge.setText("LOW");
                holder.binding.stockBadge.setBackgroundTintList(ColorStateList.valueOf(0xFFFEF3C7));
                holder.binding.stockBadge.setTextColor(0xFFD97706);
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
