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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

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
    private int    restockQty          = 10;
    private double restockCurrentStock = 0.0;
    private ProductModel selectedProduct;
    // Stored reference so we can remove observers before re-observing,
    // preventing stacking when fetchProduct() is called multiple times.
    private LiveData<ProductModel> currentProductLiveData;

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding == null) return;
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
            fetchProduct(barcode);
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
        // ── ± stepper wiring ──────────────────────────────────────────
        binding.restockMinusButton.setOnClickListener(v -> {
            if (restockQty > 1) {
                restockQty--;
                updateRestockSummary();
            }
        });

        binding.restockPlusButton.setOnClickListener(v -> {
            restockQty++;
            updateRestockSummary();
        });

        binding.restockQuantityEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int v = Integer.parseInt(s.toString());
                    if (v >= 1) {
                        restockQty = v;
                        binding.restockAddingSummary.setText("Adding: " + restockQty + " pcs");
                        binding.restockNewTotal.setText((int)(restockCurrentStock + restockQty) + " units");
                    }
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void fetchProduct(String barcode) {
        // Remove observers from the previous LiveData before observing a new one.
        // This prevents stacking since getProduct() returns a fresh LiveData each call.
        if (currentProductLiveData != null) {
            currentProductLiveData.removeObservers(getViewLifecycleOwner());
        }
        currentProductLiveData = productViewModel.getProduct(barcode);
        currentProductLiveData.observe(getViewLifecycleOwner(), product -> {
            if (binding == null) return;
            if (product != null) {
                selectedProduct = product;
                binding.restockProductCard.setVisibility(View.VISIBLE);
                binding.restockProductName.setText(product.getName());
                binding.restockProductStock.setText(
                        "Current Stock: " + product.getStockDisplay());
                binding.restockProductPrice.setText(String.format(
                        Locale.getDefault(),
                        "Price: ₹%.2f", product.getPrice()));

                restockCurrentStock = ProductModel.isDecimalUnit(product.getUnit())
                        ? product.getCurrentStockDecimal()
                        : product.getCurrentStock();
                restockQty = 10;
                updateRestockSummary();

                String b64 = product.getImageBase64();
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(
                                b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory
                                        .decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            binding.restockProductImage.setImageBitmap(bmp);
                        } else {
                            binding.restockProductImage.setImageResource(
                                    R.drawable.ic_image_placeholder);
                        }
                    } catch (Exception ex) {
                        binding.restockProductImage.setImageResource(
                                R.drawable.ic_image_placeholder);
                    }
                } else {
                    binding.restockProductImage.setImageResource(
                            R.drawable.ic_image_placeholder);
                }

                binding.confirmRestockButton.setEnabled(true);
            } else {
                binding.restockProductCard.setVisibility(View.GONE);
                binding.confirmRestockButton.setEnabled(false);
            }
        });
    }

    private void updateRestockSummary() {
        binding.restockQuantityEditText.setText(String.valueOf(restockQty));
        binding.restockQuantityEditText.setSelection(binding.restockQuantityEditText.length());
        binding.restockAddingSummary.setText("Adding: " + restockQty + " pcs");
        binding.restockNewTotal.setText((int)(restockCurrentStock + restockQty) + " units");
    }
    private void processRestock() {
        if (selectedProduct == null) return;
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
        binding.confirmRestockButton.setText("Processing...");
        salesViewModel.restockProduct(
                selectedProduct.getBarcode(),
                quantity,
                getContext(),
                () -> {
                    if (binding == null) return;
                    binding.confirmRestockButton.setText("✓ Done!");
                    binding.confirmRestockButton.setBackgroundColor(0xFF00C896);
                    binding.getRoot().postDelayed(() -> {
                        if (binding == null) return;
                        Navigation.findNavController(binding.getRoot()).popBackStack();
                    }, 600);
                },
                errorMsg -> {
                    if (binding == null) return;
                    binding.confirmRestockButton.setText("Confirm Restock");
                    binding.confirmRestockButton.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Restock failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}