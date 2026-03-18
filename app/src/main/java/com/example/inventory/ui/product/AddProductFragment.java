package com.example.inventory.ui.product;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
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
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.inventory.R;
import com.example.inventory.databinding.FragmentAddProductBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.ui.scanner.ScannerActivity;
import com.example.inventory.utils.CategoryHelper;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddProductFragment extends Fragment {

    protected FragmentAddProductBinding binding;
    protected ProductViewModel viewModel;
    private Bitmap generatedBitmap;
    private Calendar expiryCalendar = Calendar.getInstance();
    private String imageUrl;
    private String shopType;
    private Uri cameraImageUri;

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding == null) return;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("barcode");
                    binding.barcodeEditText.setText(barcode);
                    binding.generatedBarcodeContainer.setVisibility(View.GONE);
                    binding.generateBarcodeButton.setVisibility(View.GONE);
                }
            }
    );

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (binding == null) return;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(requireContext().getContentResolver(), cameraImageUri);
                            bitmap = ImageDecoder.decodeBitmap(source);
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), cameraImageUri);
                        }
                        binding.productImage.setImageBitmap(bitmap);
                        uploadImage(bitmap);
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
                    Glide.with(this).load(selectedImage).circleCrop().into(binding.productImage);
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(requireActivity().getContentResolver(), selectedImage);
                            bitmap = ImageDecoder.decodeBitmap(source);
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), selectedImage);
                        }
                        uploadImage(bitmap);
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
                if (isGranted) {
                    launchCamera();
                } else {
                    Toast.makeText(getContext(), "Camera permission denied", Toast.LENGTH_SHORT).show();
                }
            }
    );

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentAddProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);

        setupUnitDropdown();
        setupDatePicker();
        fetchShopTypeAndSetupCategories();

        binding.barcodeLayout.setEndIconOnClickListener(v -> {
            Intent intent = new Intent(getContext(), ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        binding.btnCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        binding.btnGallery.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });

        binding.generateBarcodeButton.setOnClickListener(v -> generateBarcode());
        binding.shareBarcodeButton.setOnClickListener(v -> shareBarcode());
        binding.saveButton.setOnClickListener(v -> handleSave());
    }

    private void fetchShopTypeAndSetupCategories() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("settings")
                .document("shopSettings") // ✅ FIXED
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;

                    if (documentSnapshot.exists()) {
                        shopType = documentSnapshot.getString("shopType");
                        setupCategoryDropdown();
                    } else {
                        Toast.makeText(getContext(), "Shop settings not found", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupCategoryDropdown() {
        if (binding == null) return;
        if ("Other / Custom".equals(shopType)) {
            binding.categoryLayout.setVisibility(View.GONE);
            binding.customCategoryLayout.setVisibility(View.VISIBLE);
        } else {
            List<String> categories = CategoryHelper.getCategoriesForShopType(shopType);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories);
            binding.categoryDropdown.setAdapter(adapter);

            binding.categoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (binding == null) return;
                String selected = (String) parent.getItemAtPosition(position);
                if ("Other".equals(selected)) {
                    binding.customCategoryLayout.setVisibility(View.VISIBLE);
                } else {
                    binding.customCategoryLayout.setVisibility(View.GONE);
                }
            });
        }
    }

    private void launchCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (photoFile != null) {
            cameraImageUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    photoFile);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(intent);
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void uploadImage(Bitmap bitmap) {
        if (binding == null) return;
        String barcode = binding.barcodeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(barcode)) {
            Toast.makeText(getContext(), "Please enter barcode first", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.uploadProgressBar.setVisibility(View.VISIBLE);
        
        // Bitmap scaling to max 800px
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float bitmapRatio = (float) width / (float) height;
        if (bitmapRatio > 1) {
            width = 800;
            height = (int) (width / bitmapRatio);
        } else {
            height = 800;
            width = (int) (height * bitmapRatio);
        }
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] data = baos.toByteArray();

        String userId = FirebaseAuth.getInstance().getUid();
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("product_images/" + userId + "/" + barcode + ".jpg");

        storageRef.putBytes(data)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    if (binding == null) return;
                    imageUrl = uri.toString();
                    binding.uploadProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Image uploaded", Toast.LENGTH_SHORT).show();
                }))
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    binding.uploadProgressBar.setVisibility(View.GONE);
                    Toast.makeText(getContext(), "Image upload failed, try again", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupUnitDropdown() {
        if (binding == null) return;
        String[] units = {"pcs", "kg", "g", "L", "ml", "Dozen", "Pack", "Box"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, units);
        binding.unitDropdown.setAdapter(adapter);
    }

    private void setupDatePicker() {
        if (binding == null) return;
        binding.expiryDateEditText.setOnClickListener(v -> {
            new DatePickerDialog(requireActivity(), (view, year, month, dayOfMonth) -> {
                if (binding == null) return;
                expiryCalendar.set(Calendar.YEAR, year);
                expiryCalendar.set(Calendar.MONTH, month);
                expiryCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                updateExpiryLabel();
            }, expiryCalendar.get(Calendar.YEAR), expiryCalendar.get(Calendar.MONTH), expiryCalendar.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void updateExpiryLabel() {
        if (binding == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        binding.expiryDateEditText.setText(sdf.format(expiryCalendar.getTime()));
    }

    private void generateBarcode() {
        if (binding == null) return;
        String generatedBarcode = "LOCAL" + System.currentTimeMillis() + (int) (Math.random() * 1000);
        binding.barcodeEditText.setText(generatedBarcode);
        MultiFormatWriter writer = new MultiFormatWriter();
        try {
            BitMatrix bitMatrix = writer.encode(generatedBarcode, BarcodeFormat.CODE_128, 600, 300);
            BarcodeEncoder encoder = new BarcodeEncoder();
            generatedBitmap = encoder.createBitmap(bitMatrix);
            binding.barcodeImageView.setImageBitmap(generatedBitmap);
            binding.barcodeValueText.setText(generatedBarcode);
            binding.generatedBarcodeContainer.setVisibility(View.VISIBLE);
            binding.generateBarcodeButton.setVisibility(View.GONE);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void shareBarcode() {
        if (binding == null) return;
        if (generatedBitmap == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Barcode: " + binding.barcodeValueText.getText().toString());
        startActivity(Intent.createChooser(intent, "Share Barcode"));
    }

    private void handleSave() {
        if (binding == null) return;
        String barcode = binding.barcodeEditText.getText().toString().trim();
        if (TextUtils.isEmpty(barcode)) {
            binding.generateBarcodeButton.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "Barcode required", Toast.LENGTH_SHORT).show();
            return;
        }
        saveProduct();
    }

    private void saveProduct() {
        if (binding == null) return;
        String name = binding.nameEditText.getText().toString().trim();
        String category;
        if ("Other / Custom".equals(shopType)) {
            category = binding.customCategoryEditText.getText().toString().trim();
        } else {
            category = binding.categoryDropdown.getText().toString().trim();
            if ("Other".equals(category)) {
                category = binding.customCategoryEditText.getText().toString().trim();
            }
        }
        
        String priceStr = binding.priceEditText.getText().toString().trim();
        String stockStr = binding.stockEditText.getText().toString().trim();
        String unit = binding.unitDropdown.getText().toString().trim();
        String thresholdStr = binding.thresholdEditText.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(category) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(stockStr)) {
            Toast.makeText(getContext(), "Required fields missing", Toast.LENGTH_SHORT).show();
            return;
        }

        ProductModel product = new ProductModel();
        product.setBarcode(binding.barcodeEditText.getText().toString().trim());
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

        if (!binding.expiryDateEditText.getText().toString().isEmpty()) {
            product.setExpiryDate(new Timestamp(expiryCalendar.getTime()));
        }

        product.setCreatedAt(Timestamp.now());
        product.setUpdatedAt(Timestamp.now());
        viewModel.addProduct(product);
        
        Toast.makeText(getContext(), "Product saved", Toast.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).popBackStack();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
