package com.example.inventory.ui.product;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class EditProductFragment extends Fragment {

    private FragmentEditProductBinding binding;
    private ProductViewModel viewModel;
    private String barcode;
    private String imageUrl;
    private String shopType;
    private Calendar expiryCalendar = Calendar.getInstance();

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Bitmap photo = (Bitmap) result.getData().getExtras().get("data");
                    binding.editProductImage.setImageBitmap(photo);
                    uploadImage(photo);
                }
            }
    );

    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri selectedImage = result.getData().getData();
                    Glide.with(this).load(selectedImage).circleCrop().into(binding.editProductImage);
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), selectedImage);
                        uploadImage(bitmap);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    launchCamera();
                } else {
                    Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                }
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
        setupDatePicker();

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
                .collection("settings").document("shopSettings")
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
                if ("Other".equals(selected)) {
                    binding.editCustomCategoryLayout.setVisibility(View.VISIBLE);
                } else {
                    binding.editCustomCategoryLayout.setVisibility(View.GONE);
                }
            });
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void uploadImage(Bitmap bitmap) {
        binding.editUploadProgressBar.setVisibility(View.VISIBLE);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] data = baos.toByteArray();

        String userId = FirebaseAuth.getInstance().getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("product_images/" + userId + "/" + barcode + ".jpg");

        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    imageUrl = uri.toString();
                    binding.editUploadProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Image uploaded", Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e -> {
                    binding.editUploadProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Image upload failed, try again", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupUnitDropdown() {
        String[] units = {"pcs", "kg", "g", "L", "ml", "Dozen", "Pack", "Box"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, units);
        binding.editUnitDropdown.setAdapter(adapter);
    }

    private void setupDatePicker() {
        binding.editExpiryDateEditText.setOnClickListener(v -> {
            // BUG FIX 2: Use requireActivity() instead of requireContext()
            new DatePickerDialog(requireActivity(), (view, year, month, dayOfMonth) -> {
                expiryCalendar.set(Calendar.YEAR, year);
                expiryCalendar.set(Calendar.MONTH, month);
                expiryCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateExpiryLabel();
            }, expiryCalendar.get(Calendar.YEAR), expiryCalendar.get(Calendar.MONTH), expiryCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void updateExpiryLabel() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        binding.editExpiryDateEditText.setText(sdf.format(expiryCalendar.getTime()));
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
                binding.editThresholdEditText.setText(String.valueOf(product.getLowStockThreshold()));
                imageUrl = product.getImageUrl();

                Glide.with(this)
                     .load(imageUrl)
                     .placeholder(R.drawable.ic_image_placeholder)
                     .error(R.drawable.ic_image_placeholder)
                     .circleCrop()
                     .into(binding.editProductImage);

                if (ProductModel.isDecimalUnit(product.getUnit())) {
                    binding.editStockEditText.setText(String.valueOf(product.getCurrentStockDecimal()));
                } else {
                    binding.editStockEditText.setText(String.valueOf(product.getCurrentStock()));
                }

                if (product.getExpiryDate() != null) {
                    expiryCalendar.setTime(product.getExpiryDate().toDate());
                    updateExpiryLabel();
                }
            }
        });
    }

    private void updateProduct() {
        String name = binding.editNameEditText.getText().toString().trim();
        String category;
        if ("Other / Custom".equals(shopType)) {
            category = binding.editCustomCategoryEditText.getText().toString().trim();
        } else {
            category = binding.editCategoryDropdown.getText().toString().trim();
            if ("Other".equals(category)) {
                category = binding.editCustomCategoryEditText.getText().toString().trim();
            }
        }

        String priceStr = binding.editPriceEditText.getText().toString().trim();
        String stockStr = binding.editStockEditText.getText().toString().trim();
        String unit = binding.editUnitDropdown.getText().toString().trim();
        String thresholdStr = binding.editThresholdEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(category) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(stockStr)) {
            Toast.makeText(getContext(), "Please fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductModel product = new ProductModel();
        product.setBarcode(barcode);
        product.setName(name);
        product.setCategory(category);
        product.setPrice(Double.parseDouble(priceStr));
        product.setUnit(unit);
        product.setLowStockThreshold(TextUtils.isEmpty(thresholdStr) ? 5 : Integer.parseInt(thresholdStr));
        product.setImageUrl(imageUrl);
        
        if (ProductModel.isDecimalUnit(unit)) {
            product.setCurrentStockDecimal(Double.parseDouble(stockStr));
        } else {
            product.setCurrentStock((int)Double.parseDouble(stockStr));
        }

        if (!binding.editExpiryDateEditText.getText().toString().isEmpty()) {
            product.setExpiryDate(new Timestamp(expiryCalendar.getTime()));
        }

        product.setUpdatedAt(Timestamp.now());
        viewModel.updateProduct(product);
        
        Toast.makeText(getContext(), "Product updated successfully", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).popBackStack();
    }

    private void showDeleteConfirmation() {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to delete this product? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    viewModel.deleteProduct(barcode);
                    Toast.makeText(getContext(), "Product deleted", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot()).popBackStack(R.id.navigation_products, false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
