package com.example.inventory.ui.product;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentEditProductBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.utils.CategoryHelper;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EditProductFragment extends Fragment {

    private FragmentEditProductBinding binding;
    private ProductViewModel viewModel;
    private String barcode;
    private String imageBase64;
    private String shopType;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding == null) return;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(requireContext().getContentResolver(), cameraImageUri);
                            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                                decoder.setMutableRequired(true);
                            });
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), cameraImageUri);
                        }
                        binding.editProductImage.setImageBitmap(bitmap);
                        processAndStoreImage(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding == null) return;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    Glide.with(this).load(selectedImage).circleCrop().into(binding.editProductImage);
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(requireActivity().getContentResolver(), selectedImage);
                            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                                decoder.setMutableRequired(true);
                            });
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), selectedImage);
                        }
                        processAndStoreImage(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) launchCamera();
                else Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentEditProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        if (getArguments() != null) {
            barcode = getArguments().getString("barcode");
            fetchShopTypeAndSetupCategories();
            loadProductData();
        }
        setupUnitDropdown();
        binding.btnEditCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });
        binding.btnEditGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });
        binding.updateProductButton.setOnClickListener(v -> updateProduct());
        binding.deleteProductButton.setOnClickListener(v -> showDeleteConfirmation());
    }

    private void fetchShopTypeAndSetupCategories() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("settings").document("shopInfo")
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        shopType = documentSnapshot.getString("shopType");
                        setupCategoryDropdown();
                    }
                });
    }

    private void setupCategoryDropdown() {
        if ("Other / Custom".equals(shopType)) {
            binding.editCategoryLayout.setVisibility(View.GONE);
            binding.editCustomCategoryLayout.setVisibility(View.VISIBLE);
        } else {
            List<String> categories = CategoryHelper.getCategoriesForShopType(shopType);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
            binding.editCategoryDropdown.setAdapter(adapter);
            binding.editCategoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
                String selected = (String) parent.getItemAtPosition(position);
                if ("Other".equals(selected)) binding.editCustomCategoryLayout.setVisibility(View.VISIBLE);
                else binding.editCustomCategoryLayout.setVisibility(View.GONE);
            });
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            photoFile = File.createTempFile("JPEG_" + timeStamp + "_", ".jpg", storageDir);
        } catch (IOException ex) { ex.printStackTrace(); }
        if (photoFile != null) {
            cameraImageUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(intent);
        }
    }

    private void processAndStoreImage(Bitmap bitmap) {
        if (binding == null) return;
        binding.editUploadProgressBar.setVisibility(View.VISIBLE);
        // Scale to max 400px
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = (float) width / height;
        if (width > height) {
            width = 400;
            height = (int) (width / ratio);
        } else {
            height = 400;
            width = (int) (height * ratio);
        }
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width, height, true);
        // Compress and encode to Base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        binding.editUploadProgressBar.setVisibility(View.GONE);
        Toast.makeText(getContext(), "Image ready", Toast.LENGTH_SHORT).show();
    }

    private void setupUnitDropdown() {
        String[] units = {"pcs", "kg", "g", "L", "ml", "Dozen", "Pack", "Box"};
        binding.editUnitDropdown.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, units));
    }

    private void loadProductData() {
        viewModel.getProduct(barcode).observe(getViewLifecycleOwner(), product -> {
            if (product != null) {
                binding.editBarcodeEditText.setText(product.getBarcode());
                binding.editNameEditText.setText(product.getName());
                List<String> categories = CategoryHelper.getCategoriesForShopType(shopType);
                if (categories.contains(product.getCategory())) {
                    binding.editCategoryDropdown.setText(product.getCategory(), false);
                } else {
                    binding.editCategoryDropdown.setText("Other", false);
                    binding.editCustomCategoryLayout.setVisibility(View.VISIBLE);
                    binding.editCustomCategoryEditText.setText(product.getCategory());
                }
                binding.editPriceEditText.setText(String.valueOf(product.getPrice()));
                binding.editUnitDropdown.setText(product.getUnit(), false);
                
                imageBase64 = product.getImageBase64();
                if (imageBase64 != null && !imageBase64.isEmpty()) {
                    byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    binding.editProductImage.setImageBitmap(bmp);
                } else {
                    binding.editProductImage.setImageResource(R.drawable.ic_image_placeholder);
                }

                if (ProductModel.isDecimalUnit(product.getUnit())) {
                    binding.editStockEditText.setText(String.valueOf(product.getCurrentStockDecimal()));
                } else {
                    binding.editStockEditText.setText(String.valueOf(product.getCurrentStock()));
                }
            }
        });
    }

    private void updateProduct() {
        String name = binding.editNameEditText.getText().toString().trim();
        String category = "Other / Custom".equals(shopType)
            ? binding.editCustomCategoryEditText.getText().toString().trim()
            : binding.editCategoryDropdown.getText().toString().trim();
        if ("Other".equals(category)) category = binding.editCustomCategoryEditText.getText().toString().trim();
        String priceStr = binding.editPriceEditText.getText().toString().trim();
        String stockStr = binding.editStockEditText.getText().toString().trim();
        String unit = binding.editUnitDropdown.getText().toString().trim();
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(category) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(stockStr)) {
            Toast.makeText(getContext(), "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }
        ProductModel product = new ProductModel();
        product.setBarcode(barcode); product.setName(name); product.setCategory(category);
        product.setPrice(Double.parseDouble(priceStr)); product.setUnit(unit);
        product.setImageBase64(imageBase64);
        if (ProductModel.isDecimalUnit(unit)) product.setCurrentStockDecimal(Double.parseDouble(stockStr));
        else product.setCurrentStock((int)Double.parseDouble(stockStr));
        product.setUpdatedAt(Timestamp.now());
        viewModel.updateProduct(product);
        Toast.makeText(getContext(), "Product updated successfully", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).popBackStack();
    }

    private void showDeleteConfirmation() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to delete this product?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteProduct(barcode);
                    Toast.makeText(getContext(), "Product deleted", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot()).popBackStack(R.id.navigation_products, false);
                })
                .setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}
