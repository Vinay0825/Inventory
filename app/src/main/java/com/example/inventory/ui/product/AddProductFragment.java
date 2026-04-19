package com.example.inventory.ui.product;

import android.Manifest;
import android.app.Activity;
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
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.io.InputStream;
import android.graphics.BitmapFactory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentAddProductBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.ui.scanner.ScannerActivity;
import com.example.inventory.utils.CategoryHelper;
import com.example.inventory.viewmodel.ProductViewModel;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddProductFragment extends Fragment {

    protected FragmentAddProductBinding binding;
    protected ProductViewModel viewModel;
    private Bitmap generatedBitmap;
    private String imageBase64;
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
                            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                                decoder.setMutableRequired(true);
                            });
                        } else {
                            bitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), cameraImageUri);
                        }
                        binding.productImage.setImageBitmap(bitmap);
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
                    try {
                        Bitmap bitmap;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source source = ImageDecoder.createSource(requireActivity().getContentResolver(), selectedImage);
                            bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                                decoder.setMutableRequired(true);  // RULE: always set this
                            });
                        } else {
                            InputStream inputStream = requireActivity().getContentResolver().openInputStream(selectedImage);
                            bitmap = BitmapFactory.decodeStream(inputStream);
                            if (inputStream != null) inputStream.close();
                        }
                        // FIX: removed Glide — use bitmap directly
                        binding.productImage.setImageBitmap(bitmap);
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
        binding = FragmentAddProductBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProductViewModel.class);
        setupUnitDropdown();
        fetchShopTypeAndSetupCategories();

        if (getArguments() != null) {
            String scannedBarcode = getArguments().getString("barcode");
            String productName = getArguments().getString("productName");
            if (scannedBarcode != null && !scannedBarcode.isEmpty()) {
                binding.barcodeEditText.setText(scannedBarcode);
                binding.generateBarcodeButton.setVisibility(View.GONE);
                binding.generatedBarcodeContainer.setVisibility(View.GONE);
            }
            if (productName != null && !productName.isEmpty()) {
                binding.nameEditText.setText(productName);
            }
        }

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
        FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("settings").document("shopInfo")
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (binding == null) return;
                    if (documentSnapshot.exists()) {
                        shopType = documentSnapshot.getString("shopType");
                        setupCategoryDropdown();
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
            binding.categoryDropdown.setThreshold(0);  // show without typing
            // FIX: MIUI won't open dropdown on tap without this
            binding.categoryDropdown.setOnClickListener(v -> binding.categoryDropdown.showDropDown());
            binding.categoryDropdown.setOnItemClickListener((parent, view, position, id) -> {
                if (binding == null) return;
                String selected = (String) parent.getItemAtPosition(position);
                if ("Other".equals(selected)) binding.customCategoryLayout.setVisibility(View.VISIBLE);
                else binding.customCategoryLayout.setVisibility(View.GONE);
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
        binding.uploadProgressBar.setVisibility(View.VISIBLE);
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
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        imageBase64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
        binding.uploadProgressBar.setVisibility(View.GONE);
        Toast.makeText(getContext(), "Image ready", Toast.LENGTH_SHORT).show();
    }

    private void setupUnitDropdown() {
        if (binding == null) return;
        String[] units = {"pcs", "kg", "g", "L", "ml", "Dozen", "Pack", "Box"};
        binding.unitDropdown.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, units));
    }

    private void generateBarcode() {
        if (binding == null) return;
        String bc = "LOCAL" + System.currentTimeMillis() + (int)(Math.random() * 1000);
        binding.barcodeEditText.setText(bc);
        try {
            BitMatrix bm = new MultiFormatWriter().encode(bc, BarcodeFormat.CODE_128, 600, 300);
            generatedBitmap = new BarcodeEncoder().createBitmap(bm);
            binding.barcodeImageView.setImageBitmap(generatedBitmap);
            binding.barcodeValueText.setText(bc);
            binding.generatedBarcodeContainer.setVisibility(View.VISIBLE);
            binding.generateBarcodeButton.setVisibility(View.GONE);
        } catch (WriterException e) { e.printStackTrace(); }
    }

    private void shareBarcode() {
        if (binding == null || generatedBitmap == null) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, "Barcode: " + binding.barcodeValueText.getText());
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

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        // Check if barcode already exists before saving
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(userId)
                .collection("products").document(barcode)
                .get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    if (doc.exists()) {
                        new android.app.AlertDialog.Builder(requireContext())
                                .setTitle("Barcode Already Exists")
                                .setMessage("A product named \"" + doc.getString("name")
                                        + "\" is already registered with this barcode. "
                                        + "Do you want to overwrite it?")
                                .setPositiveButton("Overwrite", (dialog, which) -> {
                                    proceedWithSave();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    } else {
                        proceedWithSave();
                    }
                });
    }

    private void proceedWithSave() {
        if (binding == null) return;
        String name = binding.nameEditText.getText().toString().trim();
        if (name.isEmpty()) { saveProduct(); return; }

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) { saveProduct(); return; }
        String currentBarcode = binding.barcodeEditText.getText().toString().trim();

        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("products")
            .whereEqualTo("name", name)
            .get()
            .addOnSuccessListener(query -> {
                if (binding == null) return;
                boolean duplicate = false;
                for (com.google.firebase.firestore.DocumentSnapshot doc
                        : query.getDocuments()) {
                    // Only flag if it's a DIFFERENT barcode
                    if (!doc.getId().equals(currentBarcode)) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    new android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Duplicate Product Name")
                        .setMessage("A product named \"" + name
                            + "\" already exists. "
                            + "Are you sure you want to add another?")
                        .setPositiveButton("Add Anyway",
                            (dialog, which) -> saveProduct())
                        .setNegativeButton("Cancel", null)
                        .show();
                } else {
                    saveProduct();
                }
            })
            .addOnFailureListener(e -> saveProduct()); // if check fails, proceed
    }

    protected void saveProduct() {
        if (binding == null) return;
        String barcode = binding.barcodeEditText.getText().toString().trim();
        String name = binding.nameEditText.getText().toString().trim();
        String category = "Other / Custom".equals(shopType)
                ? binding.customCategoryEditText.getText().toString().trim()
                : binding.categoryDropdown.getText().toString().trim();
        if ("Other".equals(category)) category = binding.customCategoryEditText.getText().toString().trim();
        String priceStr = binding.priceEditText.getText().toString().trim();
        String stockStr = binding.stockEditText.getText().toString().trim();
        String unit = binding.unitDropdown.getText().toString().trim();
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(category) || TextUtils.isEmpty(priceStr) || TextUtils.isEmpty(stockStr)) {
            Toast.makeText(getContext(), "Required fields missing", Toast.LENGTH_SHORT).show();
            return;
        }
        ProductModel product = new ProductModel();
        product.setBarcode(barcode);
        product.setName(name); product.setCategory(category);
        double price = Double.parseDouble(priceStr);
        product.setPrice(price); product.setUnit(unit);
        product.setImageBase64(imageBase64);
        if (ProductModel.isDecimalUnit(unit)) product.setCurrentStockDecimal(Double.parseDouble(stockStr));
        else product.setCurrentStock((int)Double.parseDouble(stockStr));
        product.setCreatedAt(Timestamp.now());
        product.setUpdatedAt(Timestamp.now());

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null) return;

        FirebaseFirestore.getInstance().collection("users").document(userId)
                .collection("products").document(barcode)
                .set(product)
                .addOnSuccessListener(aVoid -> {
                    if (binding == null) return;
                    if (barcode.startsWith("LOOSE")) {
                        Map<String, Object> looseItem = new HashMap<>();
                        looseItem.put("barcode", barcode);
                        looseItem.put("name", name);
                        looseItem.put("label", name);
                        looseItem.put("unit", unit);
                        looseItem.put("price", price);
                        looseItem.put("createdAt", Timestamp.now());
                        FirebaseFirestore.getInstance()
                                .collection("users").document(userId)
                                .collection("looseItems").document(barcode)
                                .set(looseItem);
                    }
                    Toast.makeText(getContext(), "Product saved", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(binding.getRoot()).popBackStack();
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    Toast.makeText(getContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onDestroyView() { super.onDestroyView(); binding = null; }
}