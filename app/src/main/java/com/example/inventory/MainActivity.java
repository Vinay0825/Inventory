package com.example.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.inventory.databinding.ActivityMainBinding;
import com.example.inventory.model.ProductModel;
import com.example.inventory.notifications.LowStockNotificationManager;
import com.example.inventory.ui.ScanResultBottomSheet;
import com.example.inventory.ui.scanner.ScannerActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private NavController navController;
    private GestureDetector gestureDetector;

    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    private final List<Integer> TAB_ORDER = Arrays.asList(
            R.id.navigation_dashboard,
            R.id.navigation_products,
            R.id.navigation_history,
            R.id.navigation_analytics
    );

    private final List<Integer> NAV_HIDDEN_DESTINATIONS = Arrays.asList(
            R.id.navigation_settings
    );

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    String barcode = result.getData().getStringExtra("barcode");
                    if (barcode != null) {
                        checkProductAndShowResult(barcode);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        setSupportActionBar(binding.toolbar);
        LowStockNotificationManager.createNotificationChannel(this);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            NavigationUI.setupWithNavController(binding.navView, navController);

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (NAV_HIDDEN_DESTINATIONS.contains(id)) {
                    binding.navView.setVisibility(View.GONE);
                    binding.fabScan.setVisibility(View.GONE);
                } else {
                    binding.navView.setVisibility(View.VISIBLE);
                    binding.fabScan.setVisibility(View.VISIBLE);
                }
            });

            AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                    R.id.navigation_dashboard, R.id.navigation_products,
                    R.id.navigation_history, R.id.navigation_analytics)
                    .build();
            NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

            if (getIntent().hasExtra("navigate_to")) {
                String destination = getIntent().getStringExtra("navigate_to");
                if ("low_stock_alerts".equals(destination)) {
                    navController.navigate(R.id.navigation_alerts);
                }
            }
        }

        binding.fabScan.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScannerActivity.class);
            scannerLauncher.launch(intent);
        });

        setupSwipeGesture();
        loadShopName();
    }

    private void setupSwipeGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float deltaY = Math.abs(e1.getY() - e2.getY());
                float deltaX = e1.getX() - e2.getX();
                if (deltaY > SWIPE_MAX_OFF_PATH) return false;
                if (Math.abs(deltaX) < SWIPE_MIN_DISTANCE) return false;
                if (Math.abs(velocityX) < SWIPE_THRESHOLD_VELOCITY) return false;
                if (binding.navView.getVisibility() != View.VISIBLE) return false;
                int currentId = navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId() : -1;
                int currentIndex = TAB_ORDER.indexOf(currentId);
                if (currentIndex == -1) return false;
                if (deltaX > 0) {
                    if (currentIndex < TAB_ORDER.size() - 1) {
                        navController.navigate(TAB_ORDER.get(currentIndex + 1));
                        return true;
                    }
                } else {
                    if (currentIndex > 0) {
                        navController.navigate(TAB_ORDER.get(currentIndex - 1));
                        return true;
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void loadShopName() {
        if (mAuth.getCurrentUser() != null) {
            db.collection("users").document(mAuth.getUid())
                    .collection("settings").document("shopInfo")
                    .addSnapshotListener((documentSnapshot, e) -> {
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            String shopName = documentSnapshot.getString("shopName");
                            if (shopName != null && !shopName.isEmpty()) {
                                if (getSupportActionBar() != null)
                                    getSupportActionBar().setTitle(shopName);
                            }
                        }
                    });
        }
    }

    private void checkProductAndShowResult(String barcode) {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("users").document(mAuth.getUid())
                .collection("products").document(barcode)
                .get().addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        ProductModel product = documentSnapshot.toObject(ProductModel.class);
                        if (product != null) {
                            ScanResultBottomSheet bottomSheet = ScanResultBottomSheet.newInstance(product);
                            bottomSheet.show(getSupportFragmentManager(), "ScanResultBottomSheet");
                        }
                    } else {
                        showProductNotFoundDialog(barcode);
                    }
                }).addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public void showScanResultForProduct(ProductModel product) {
        ScanResultBottomSheet bottomSheet = ScanResultBottomSheet.newInstance(product);
        bottomSheet.show(getSupportFragmentManager(), "ScanResultBottomSheet");
    }

    private void showProductNotFoundDialog(String barcode) {
        new AlertDialog.Builder(this)
                .setTitle("Product Not Found")
                .setMessage("Product with barcode " + barcode + " is not registered. Add it now?")
                .setPositiveButton("Add", (dialog, which) -> {
                    Bundle args = new Bundle();
                    args.putString("barcode", barcode);
                    navController.navigate(R.id.addProductFragment, args);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.navigation_settings) {
            navController.navigate(R.id.navigation_settings);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
