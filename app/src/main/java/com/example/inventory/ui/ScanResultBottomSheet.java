package com.example.inventory.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.BottomSheetScanResultBinding;
import com.example.inventory.model.ProductModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class ScanResultBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetScanResultBinding binding;
    private ProductModel product;

    public static ScanResultBottomSheet newInstance(ProductModel product) {
        ScanResultBottomSheet fragment = new ScanResultBottomSheet();
        fragment.product = product;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetScanResultBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (product != null) {
            binding.tvProductName.setText(product.getName());
            binding.tvCategory.setText(product.getCategory());
            binding.tvStock.setText(product.getStockDisplay());
            binding.tvPrice.setText(String.format(Locale.getDefault(), "₹%.2f", product.getPrice()));

            Glide.with(this)
                 .load(product.getImageUrl())
                 .placeholder(R.drawable.ic_image_placeholder)
                 .error(R.drawable.ic_image_placeholder)
                 .circleCrop()
                 .into(binding.ivProductImage);

            binding.btnSell.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString("barcode", product.getBarcode());
                NavHostFragment.findNavController(this).navigate(R.id.sellFragment, args);
                dismiss();
            });

            binding.btnRestock.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString("barcode", product.getBarcode());
                NavHostFragment.findNavController(this).navigate(R.id.restockFragment, args);
                dismiss();
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
