package com.example.inventory.ui.restock;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentRestockBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.ui.scanner.ScannerActivity;
import com.example.inventory.viewmodel.ProductViewModel;
import com.example.inventory.viewmodel.SalesViewModel;

import java.util.Locale;

public class RestockFragment extends Fragment {

    private FragmentRestockBinding binding;
    private SalesViewModel salesViewModel;
    private ProductViewModel productViewModel;
    private ProductModel selectedProduct;

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("barcode");
                    binding.restockBarcodeEditText.setText(barcode);
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentRestockBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        salesViewModel = new ViewModelProvider(this).get(SalesViewModel.class);
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        if (getArguments() != null && getArguments().getString("barcode") != null) {
            String barcode = getArguments().getString("barcode");
            binding.restockBarcodeEditText.setText(barcode);
            fetchProduct(barcode); // explicitly load product on arrival
        }

        binding.restockBarcodeLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        binding.restockBarcodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 3) {
                    fetchProduct(s.toString());
                } else {
                    binding.restockProductCard.setVisibility(View.GONE);
                    binding.confirmRestockButton.setEnabled(false);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.confirmRestockButton.setOnClickListener(v -> processRestock());
    }

    private void fetchProduct(String barcode) {
        productViewModel.getProduct(barcode).observe(getViewLifecycleOwner(), product -> {
            if (binding == null) return;
            if (product != null) {
                selectedProduct = product;
                binding.restockProductCard.setVisibility(View.VISIBLE);
                binding.restockProductName.setText(product.getName());
                binding.restockProductStock.setText("Current Stock: " + product.getStockDisplay());
                binding.restockProductPrice.setText(String.format(Locale.getDefault(), "Price: ₹%.2f", product.getPrice()));

                Glide.with(this)
                     .load(product.getImageUrl())
                     .placeholder(R.drawable.ic_image_placeholder)
                     .error(R.drawable.ic_image_placeholder)
                     .circleCrop()
                     .into(binding.restockProductImage);

                binding.confirmRestockButton.setEnabled(true);
            } else {
                binding.restockProductCard.setVisibility(View.GONE);
                binding.confirmRestockButton.setEnabled(false);
            }
        });
    }

    private void processRestock() {
        String quantityStr = binding.restockQuantityEditText.getText().toString().trim();
        if (TextUtils.isEmpty(quantityStr)) {
            Toast.makeText(getContext(), "Enter quantity to restock", Toast.LENGTH_SHORT).show();
            return;
        }

        double quantity = Double.parseDouble(quantityStr);
        if (quantity <= 0) {
            Toast.makeText(getContext(), "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.confirmRestockButton.setEnabled(false);
        try {
            salesViewModel.restockProduct(selectedProduct.getBarcode(), quantity, getContext());
            Toast.makeText(getContext(), "Restock successful", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).popBackStack();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Restock failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            binding.confirmRestockButton.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
