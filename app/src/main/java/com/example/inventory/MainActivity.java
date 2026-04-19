package com.example.inventory;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
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

    private static boolean notificationsCheckedThisSession = false;
    private String cachedShopName = "ShopEase";

    private ActivityMainBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private NavController navController;
    private GestureDetector gestureDetector;
    private ConnectivityManager.NetworkCallback networkCallback;

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
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(
            com.example.inventory.utils.LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Real network monitoring
        ConnectivityManager cm = (ConnectivityManager)
            getSystemService(Context.CONNECTIVITY_SERVICE);

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                runOnUiThread(() -> {
                    if (binding != null)
                        binding.offlineBanner.setVisibility(View.GONE);
                });
            }
            @Override
            public void onLost(Network network) {
                runOnUiThread(() -> {
                    if (binding != null)
                        binding.offlineBanner.setVisibility(View.VISIBLE);
                });
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        cm.registerNetworkCallback(request, networkCallback);

        // Check current state immediately on launch
        boolean isOnline = false;
        Network activeNetwork = cm.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities caps =
                cm.getNetworkCapabilities(activeNetwork);
            isOnline = caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        binding.offlineBanner.setVisibility(
            isOnline ? View.GONE : View.VISIBLE);

        setSupportActionBar(binding.toolbar);
        LowStockNotificationManager.createNotificationChannel(this);

        // Schedule daily reminder if user is logged in
        if (mAuth.getCurrentUser() != null) {
            LowStockNotificationManager.scheduleDailyReminder(this);
        }

        if (!notificationsCheckedThisSession) {
            notificationsCheckedThisSession = true;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    androidx.core.app.ActivityCompat.requestPermissions(this,
                            new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
                }
            }

            // WITH:
            if (mAuth.getCurrentUser() != null) {
                db.collection("users").document(mAuth.getUid())
                        .collection("products")
                        .get()
                        .addOnSuccessListener(querySnapshot -> {
                            for (com.google.firebase.firestore.DocumentSnapshot doc
                                    : querySnapshot.getDocuments()) {
                                ProductModel product = doc.toObject(ProductModel.class);
                                if (product == null) continue;
                                if (com.example.inventory.utils.StockRulesEngine.isLowStock(product)) {
                                    LowStockNotificationManager.sendLowStockNotification(
                                            MainActivity.this,
                                            product.getName(),
                                            product.getStockDisplay());
                                }
                            }
                        });
            }
        }

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();

            binding.navView.setOnItemSelectedListener(item -> {
                int destinationId = item.getItemId();
                int currentId = navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId() : -1;
                if (currentId == destinationId) return true;

                // Determine direction for animation
                int currentIndex = TAB_ORDER.indexOf(currentId);
                int targetIndex = TAB_ORDER.indexOf(destinationId);
                boolean goingForward = targetIndex > currentIndex;

                navigateToTab(destinationId, goingForward);
                return true;
            });

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                if (getSupportActionBar() != null)
                    getSupportActionBar().setTitle(cachedShopName);
                int id = destination.getId();
                if (NAV_HIDDEN_DESTINATIONS.contains(id)) {
                    binding.navView.setVisibility(View.GONE);
                    binding.fabScan.setVisibility(View.GONE);
                    binding.bottomAppBar.setVisibility(View.GONE);
                } else {
                    binding.navView.setVisibility(View.VISIBLE);
                    binding.fabScan.setVisibility(View.VISIBLE);
                    binding.bottomAppBar.setVisibility(View.VISIBLE);
                }

                // Keep BottomNavigationView selection in sync
                binding.navView.getMenu().findItem(id); // Check if destination is in menu
                MenuItem menuItem = binding.navView.getMenu().findItem(id);
                if (menuItem != null) {
                    menuItem.setChecked(true);
                }
            });

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
        gestureDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onDown(MotionEvent e) {
                return true; // Required so onFling is called
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                                   float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (binding.navView.getVisibility() != View.VISIBLE)
                    return false;

                float dx = e1.getX() - e2.getX();
                float dy = Math.abs(e1.getY() - e2.getY());

                // Reject if more vertical than horizontal
                if (dy > Math.abs(dx) * 0.8f) return false;
                // Minimum distance
                if (Math.abs(dx) < 80) return false;
                // Minimum velocity
                if (Math.abs(velocityX) < 150) return false;

                int currentId = navController.getCurrentDestination() != null
                        ? navController.getCurrentDestination().getId() : -1;
                int currentIndex = TAB_ORDER.indexOf(currentId);
                if (currentIndex == -1) return false;

                // History tab: stricter threshold — ViewPager2 is inside
                if (currentId == R.id.navigation_history) {
                    if (Math.abs(dx) < 200) return false;
                    if (Math.abs(velocityX) < 400) return false;
                }

                int targetIndex;
                boolean swipeLeft = dx > 0; // finger moved left = go forward
                if (swipeLeft) {
                    targetIndex = currentIndex + 1;
                } else {
                    targetIndex = currentIndex - 1;
                }

                if (targetIndex < 0 || targetIndex >= TAB_ORDER.size())
                    return false;

                int targetId = TAB_ORDER.get(targetIndex);
                navigateToTab(targetId, swipeLeft);
                return true;
            }
        });
    }



    private void navigateToTab(int destinationId, boolean goingForward) {
        int currentId = navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId() : -1;
        if (currentId == destinationId) return;

        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(destinationId, false)
                .setEnterAnim(goingForward
                        ? R.anim.slide_in_right : R.anim.slide_in_left)
                .setExitAnim(goingForward
                        ? R.anim.slide_out_left : R.anim.slide_out_right)
                .setPopEnterAnim(R.anim.slide_in_left)
                .setPopExitAnim(R.anim.slide_out_right)
                .build();

        try {
            navController.navigate(destinationId, null, navOptions);
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Nav failed: " + e.getMessage());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    private void loadShopName() {
        if (mAuth.getCurrentUser() == null) return;
        db.collection("users").document(mAuth.getUid())
                .collection("settings").document("shopInfo")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        String name = snapshot.getString("shopName");
                        if (name != null && !name.isEmpty()) {
                            cachedShopName = name;
                            if (getSupportActionBar() != null)
                                getSupportActionBar().setTitle(cachedShopName);
                        }
                    }
                });
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
        if (item.getItemId() == R.id.navigation_alerts) {
            navController.navigate(R.id.navigation_alerts);
            return true;
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ConnectivityManager cm = (ConnectivityManager)
            getSystemService(Context.CONNECTIVITY_SERVICE);
        if (networkCallback != null) {
            cm.unregisterNetworkCallback(networkCallback);
        }
    }
}
