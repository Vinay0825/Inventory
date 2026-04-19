
package com.example.inventory.ui.sales;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.inventory.databinding.FragmentSalesHistoryBinding;
import com.example.inventory.model.SaleModel;
import com.example.inventory.viewmodel.SalesViewModel;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;
import java.util.Locale;

public class SalesHistoryFragment extends Fragment {

    private FragmentSalesHistoryBinding binding;
    private SalesViewModel viewModel;
    private androidx.lifecycle.MutableLiveData<List<com.example.inventory.model.SaleModel>> salesLiveData;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSalesHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SalesViewModel.class);

        salesLiveData = new androidx.lifecycle.MutableLiveData<>();
        // Observe getSalesHistory() directly — it applies the current
        // filter via Transformations.map so summary reflects filter
        viewModel.getSalesHistory().observe(getViewLifecycleOwner(),
                filteredSales -> {
            if (binding == null) return;
            if (filteredSales != null) updateSummary(filteredSales);
        });

        setupViewPager();
        setupFilterChips();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh sales data on every tab revisit
        viewModel.fetchSalesHistory(salesLiveData);
        // Refresh yesterday card every time we return to this tab
        loadYesterdayCard();
        // Refresh restock count
        loadRestockCount();
    }

    private void loadRestockCount() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance()
                        .getCurrentUser();
        if (user == null) return;
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("restocks")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (binding == null) return;
                    binding.summaryRestockCount.setText(
                            "Restocks: " + snapshot.size());
                });
    }

    private void loadYesterdayCard() {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance()
                        .getCurrentUser();
        if (user == null) return;
        java.util.Calendar yCal = java.util.Calendar.getInstance();
        yCal.add(java.util.Calendar.DAY_OF_YEAR, -1);
        String yesterdayKey = new java.text.SimpleDateFormat(
                "yyyy-MM-dd", java.util.Locale.getDefault())
                .format(yCal.getTime());
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("sales")
                .whereEqualTo("dayKey", yesterdayKey)
                .whereEqualTo("voided", false)
                .get()
                .addOnSuccessListener(snap -> {
                    if (binding == null) return;
                    double total = 0;
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snap.getDocuments()) {
                        com.example.inventory.model.SaleModel s =
                                doc.toObject(com.example.inventory.model.SaleModel.class);
                        if (s != null) {
                            total += s.getTotalAmount();
                            count++;
                        }
                    }
                    binding.summaryYesterdayText.setText(
                            String.format(java.util.Locale.getDefault(),
                                    "Yesterday: ₹%.2f (%d sales)", total, count));
                    binding.summaryYesterdayCard.setVisibility(
                            android.view.View.VISIBLE);
                });
    }

    private void setupViewPager() {
        binding.viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return position == 0 ? new SalesListFragment() : new RestockHistoryFragment();
            }

            @Override
            public int getItemCount() {
                return 2;
            }
        });

        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Sales" : "Restocks");
        }).attach();
    }

    private void setupFilterChips() {
        binding.chipAllTime.setOnClickListener(v -> {
            viewModel.setFilter("all");
            viewModel.fetchSalesHistory(salesLiveData);
        });
        binding.chipToday.setOnClickListener(v -> {
            viewModel.setFilter("today");
            viewModel.fetchSalesHistory(salesLiveData);
        });
        binding.chipThisWeek.setOnClickListener(v -> {
            viewModel.setFilter("week");
            viewModel.fetchSalesHistory(salesLiveData);
        });
        binding.chipThisMonth.setOnClickListener(v -> {
            viewModel.setFilter("month");
            viewModel.fetchSalesHistory(salesLiveData);
        });
    }

    public void updateRestockCount(int count) {
        if (binding == null) return;
        binding.summaryRestockCount.setText("Restocks: " + count);
    }

    public void updateSummary(List<SaleModel> filtered) {
        if (binding == null) return;
        double total = 0;
        int count = 0;
        for (SaleModel sale : filtered) {
            if (!sale.isVoided()) {
                total += sale.getTotalAmount();
                count++;
            }
        }
        binding.summaryTotalAmount.setText(
                String.format(Locale.getDefault(), "₹%.2f", total));
        binding.summaryTransactionCount.setText("Sales: " + count);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
