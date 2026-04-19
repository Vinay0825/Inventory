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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

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
    private double currentUnitPrice = 0.0;
    private int    currentQty       = 1;

    private ProductModel selectedProduct;
    // Stored reference so we can remove observers before re-observing,
    // preventing stacking when fetchProduct() is called multiple times.
    private LiveData<ProductModel> currentProductLiveData;

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
            fetchProduct(barcode);
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

        // ── ± stepper wiring ──────────────────────────────────────────
        binding.sellMinusButton.setOnClickListener(v -> {
            if (currentQty > 1) {
                currentQty--;
                updateSellSummary();
            }
        });

        binding.sellPlusButton.setOnClickListener(v -> {
            currentQty++;
            updateSellSummary();
        });

        binding.sellQuantityEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int v = Integer.parseInt(s.toString());
                    if (v >= 1) {
                        currentQty = v;
                        // update displays without triggering the watcher again
                        binding.sellQuantityDisplay.setText(currentQty + (currentQty == 1 ? " unit" : " units"));
                        binding.sellTotalPrice.setText(String.format(Locale.getDefault(), "₹%.2f", currentUnitPrice * currentQty));
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
                binding.sellProductCard.setVisibility(View.VISIBLE);
                binding.sellProductName.setText(product.getName());
                binding.sellProductStock.setText(
                        "Stock: " + product.getStockDisplay());
                binding.sellProductPrice.setText(String.format(
                        Locale.getDefault(),
                        "Price: ₹%.2f", product.getPrice()));

                currentUnitPrice = product.getPrice();
                currentQty = 1;
                updateSellSummary();

                String b64 = product.getImageBase64();
                if (b64 != null && !b64.isEmpty()) {
                    try {
                        byte[] bytes = android.util.Base64.decode(
                                b64, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap bmp =
                                android.graphics.BitmapFactory
                                        .decodeByteArray(bytes, 0, bytes.length);
                        if (bmp != null) {
                            binding.sellProductImage.setImageBitmap(bmp);
                        } else {
                            binding.sellProductImage.setImageResource(
                                    R.drawable.ic_image_placeholder);
                        }
                    } catch (Exception ex) {
                        binding.sellProductImage.setImageResource(
                                R.drawable.ic_image_placeholder);
                    }
                } else {
                    binding.sellProductImage.setImageResource(
                            R.drawable.ic_image_placeholder);
                }

                double stock = ProductModel.isDecimalUnit(product.getUnit())
                        ? product.getCurrentStockDecimal()
                        : product.getCurrentStock();
                binding.confirmSellButton.setEnabled(stock > 0);
            } else {
                binding.sellProductCard.setVisibility(View.GONE);
                binding.confirmSellButton.setEnabled(false);
            }
        });
    }

    private void updateSellSummary() {
        binding.sellQuantityEditText.setText(String.valueOf(currentQty));
        binding.sellQuantityEditText.setSelection(binding.sellQuantityEditText.length());
        binding.sellUnitPriceDisplay.setText(String.format(Locale.getDefault(), "₹%.2f", currentUnitPrice));
        binding.sellQuantityDisplay.setText(currentQty + (currentQty == 1 ? " unit" : " units"));
        binding.sellTotalPrice.setText(String.format(Locale.getDefault(), "₹%.2f", currentUnitPrice * currentQty));
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
        binding.confirmSellButton.setText("Processing...");
        salesViewModel.sellProduct(
                selectedProduct.getBarcode(),
                quantity,
                getContext(),
                () -> {
                    if (binding == null) return;
                    binding.confirmSellButton.setText("✓ Done!");
                    binding.confirmSellButton.setBackgroundColor(0xFF00C896);
                    binding.getRoot().postDelayed(() -> {
                        if (binding == null) return;
                        Navigation.findNavController(binding.getRoot()).popBackStack();
                    }, 600);
                },
                errorMsg -> {
                    if (binding == null) return;
                    binding.confirmSellButton.setText("Confirm Sale");
                    binding.confirmSellButton.setEnabled(true);
                    Toast.makeText(getContext(),
                            "Sale failed: " + errorMsg, Toast.LENGTH_LONG).show();
                }
        );
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}