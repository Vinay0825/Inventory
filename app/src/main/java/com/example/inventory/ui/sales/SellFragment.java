package com.example.inventory.ui.sales;

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
import com.example.inventory.databinding.FragmentSellBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.ui.scanner.ScannerActivity;
import com.example.inventory.viewmodel.ProductViewModel;
import com.example.inventory.viewmodel.SalesViewModel;

import java.util.Locale;

public class SellFragment extends Fragment {

    private FragmentSellBinding binding;
    private SalesViewModel salesViewModel;
    private ProductViewModel productViewModel;
    private ProductModel selectedProduct;

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("barcode");
                    binding.sellBarcodeEditText.setText(barcode);
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSellBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        salesViewModel = new ViewModelProvider(this).get(SalesViewModel.class);
        productViewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        if (getArguments() != null && getArguments().getString("barcode") != null) {
            String barcode = getArguments().getString("barcode");
            binding.sellBarcodeEditText.setText(barcode);
            fetchProduct(barcode); // explicitly load product on arrival
        }

        binding.sellBarcodeLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        binding.sellBarcodeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 3) {
                    fetchProduct(s.toString());
                } else {
                    binding.sellProductCard.setVisibility(View.GONE);
                    binding.confirmSellButton.setEnabled(false);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.confirmSellButton.setOnClickListener(v -> processSale());
    }

    private void fetchProduct(String barcode) {
        productViewModel.getProduct(barcode).observe(getViewLifecycleOwner(), product -> {
            if (binding == null) return;
            if (product != null) {
                selectedProduct = product;
                binding.sellProductCard.setVisibility(View.VISIBLE);
                binding.sellProductName.setText(product.getName());
                binding.sellProductStock.setText("Stock: " + product.getStockDisplay());
                binding.sellProductPrice.setText(String.format(Locale.getDefault(), "Price: ₹%.2f", product.getPrice()));
                
                Glide.with(this)
                     .load(product.getImageUrl())
                     .placeholder(R.drawable.ic_image_placeholder)
                     .error(R.drawable.ic_image_placeholder)
                     .circleCrop()
                     .into(binding.sellProductImage);

                double stock = ProductModel.isDecimalUnit(product.getUnit()) ? 
                        product.getCurrentStockDecimal() : product.getCurrentStock();
                binding.confirmSellButton.setEnabled(stock > 0);
            } else {
                binding.sellProductCard.setVisibility(View.GONE);
                binding.confirmSellButton.setEnabled(false);
            }
        });
    }

    private void processSale() {
        String quantityStr = binding.sellQuantityEditText.getText().toString().trim();
        if (TextUtils.isEmpty(quantityStr)) return;

        double quantity = Double.parseDouble(quantityStr);
        if (quantity <= 0) {
            Toast.makeText(getContext(), "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }

        double stock = ProductModel.isDecimalUnit(selectedProduct.getUnit()) ? 
                selectedProduct.getCurrentStockDecimal() : selectedProduct.getCurrentStock();

        if (quantity > stock) {
            Toast.makeText(getContext(), "Insufficient stock", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.confirmSellButton.setEnabled(false);
        try {
            salesViewModel.sellProduct(selectedProduct.getBarcode(), quantity, getContext());
            Toast.makeText(getContext(), "Sale successful", Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).popBackStack();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Sale failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            binding.confirmSellButton.setEnabled(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}