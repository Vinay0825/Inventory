
package com.example.inventory.ui.sales;

import android.app.AlertDialog;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.databinding.FragmentSalesListBinding;
import com.example.inventory.databinding.ItemSaleBinding;
import com.example.inventory.databinding.ItemDateHeaderBinding;
import com.example.inventory.model.SaleModel;
import com.example.inventory.utils.DateUtils;
import com.example.inventory.viewmodel.SalesViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SalesListFragment extends Fragment {

    private FragmentSalesListBinding binding;
    private SalesViewModel viewModel;
    private SalesAdapter adapter;
    private String currentFilter = "all";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSalesListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(getViewModelOwner()).get(SalesViewModel.class);

        setupRecyclerView();
        observeViewModel();
    }

    private void observeViewModel() {
        viewModel.getSalesHistory().observe(getViewLifecycleOwner(),
                sales -> {
                    if (binding == null) return;
                    List<SaleModel> list =
                        sales != null ? sales : new ArrayList<>();
                    adapter.setSales(list);
                    if (getParentFragment()
                            instanceof SalesHistoryFragment) {
                        ((SalesHistoryFragment) getParentFragment())
                            .updateSummary(list);
                    }
                    binding.emptyState.setVisibility(
                        list.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    private void setupRecyclerView() {
        adapter = new SalesAdapter(sale -> {
            showVoidConfirmation(sale);
        });
        binding.salesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.salesRecyclerView.setAdapter(adapter);
    }

    private void showVoidConfirmation(SaleModel sale) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Void Sale")
                .setMessage("This will add back "
                        + (sale.getQuantitySoldDecimal() > 0
                        ? sale.getQuantitySoldDecimal() + " " + sale.getUnit()
                        : sale.getQuantitySold() + " " + sale.getUnit())
                        + " to \"" + sale.getProductName() + "\" stock. "
                        + "This cannot be undone.")
                .setPositiveButton("Void", (dialog, which) -> {
                    viewModel.voidSale(sale);
                    Toast.makeText(getContext(), "Sale voided", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Fragment getViewModelOwner() {
        return getParentFragment() != null ? getParentFragment() : this;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class SalesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int VIEW_TYPE_HEADER = 0;
        private static final int VIEW_TYPE_SALE = 1;
        private List<Object> items = new ArrayList<>();
        private final OnSaleClickListener listener;

        interface OnSaleClickListener { void onSaleClick(SaleModel sale); }

        SalesAdapter(OnSaleClickListener listener) { this.listener = listener; }

        void setSales(List<SaleModel> sales) {
            items.clear();
            String lastHeader = "";
            double dayTotal = 0;
            int headerInsertIndex = -1;

            for (int i = 0; i < sales.size(); i++) {
                SaleModel sale = sales.get(i);
                String currentHeader =
                    DateUtils.getDayLabel(sale.getSoldAt());

                if (!currentHeader.equals(lastHeader)) {
                    // Before inserting new header, update previous
                    // header's total if there was one
                    if (headerInsertIndex >= 0) {
                        String prevHeader = (String) items.get(
                            headerInsertIndex);
                        items.set(headerInsertIndex,
                            prevHeader + "||" + String.format(
                                java.util.Locale.getDefault(),
                                "₹%.0f", dayTotal));
                    }
                    dayTotal = 0;
                    items.add(currentHeader);
                    headerInsertIndex = items.size() - 1;
                    lastHeader = currentHeader;
                }

                if (!sale.isVoided()) {
                    dayTotal += sale.getTotalAmount();
                }
                items.add(sale);
            }

            // Update last header total
            if (headerInsertIndex >= 0 && headerInsertIndex < items.size()) {
                String lastHeaderStr = (String) items.get(headerInsertIndex);
                if (!lastHeaderStr.contains("||")) {
                    items.set(headerInsertIndex,
                        lastHeaderStr + "||" + String.format(
                            java.util.Locale.getDefault(),
                            "₹%.0f", dayTotal));
                }
            }

            notifyDataSetChanged();
        }

        @Override public int getItemViewType(int position) {
            return items.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_SALE;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_HEADER) {
                return new HeaderViewHolder(ItemDateHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            } else {
                return new SaleViewHolder(ItemSaleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                String raw = (String) items.get(position);
                String[] parts = raw.split("\\|\\|");
                String dateLabel = parts[0];
                String total = parts.length > 1 ? parts[1] : "";
                ((HeaderViewHolder) holder).binding.dateHeader
                    .setText(dateLabel);
                // Set total text if the layout has a dayTotalText view
                try {
                    android.widget.TextView totalView =
                        ((HeaderViewHolder) holder).binding
                            .getRoot().findViewById(
                                com.example.inventory.R.id.dayTotalText);
                    if (totalView != null) {
                        totalView.setText(total);
                        totalView.setVisibility(
                            total.isEmpty()
                                ? android.view.View.GONE
                                : android.view.View.VISIBLE);
                    }
                } catch (Exception ignored) {}
            } else {
                SaleModel sale = (SaleModel) items.get(position);
                SaleViewHolder saleHolder = (SaleViewHolder) holder;
                saleHolder.binding.saleProductName.setText(sale.getProductName());
                saleHolder.binding.saleRelativeTime.setText(DateUtils.getRelativeTime(sale.getSoldAt()));
                saleHolder.binding.saleAmount.setText(String.format(Locale.getDefault(), "₹%.2f", sale.getTotalAmount()));
                String quantity = sale.getQuantitySoldDecimal() > 0 ? String.format("%.2f", sale.getQuantitySoldDecimal()) : String.valueOf(sale.getQuantitySold());
                saleHolder.binding.saleQuantity.setText(quantity + " units");

                // Barcode
                saleHolder.binding.saleBarcodeText.setText(sale.getBarcode());

                if (sale.isVoided()) {
                    saleHolder.binding.saleProductName.setPaintFlags(
                            saleHolder.binding.saleProductName.getPaintFlags()
                                    | Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setPaintFlags(
                            saleHolder.binding.saleAmount.getPaintFlags()
                                    | Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setTextColor(0xFF757575);
                    saleHolder.binding.saleVoidedBadge.setVisibility(View.VISIBLE);
                    saleHolder.binding.voidSaleButton.setVisibility(View.GONE);
                } else {
                    saleHolder.binding.saleProductName.setPaintFlags(
                            saleHolder.binding.saleProductName.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setPaintFlags(
                            saleHolder.binding.saleAmount.getPaintFlags()
                                    & ~Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setTextColor(0xFF00C896);
                    saleHolder.binding.saleVoidedBadge.setVisibility(View.GONE);

                    String todayKey = com.example.inventory.utils.DateUtils.getTodayKey();
                    if (sale.getDayKey() != null && sale.getDayKey().equals(todayKey)) {
                        saleHolder.binding.voidSaleButton.setVisibility(View.VISIBLE);
                        saleHolder.binding.voidSaleButton.setOnClickListener(v ->
                                listener.onSaleClick(sale));
                    } else {
                        saleHolder.binding.voidSaleButton.setVisibility(View.GONE);
                    }
                }
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            ItemDateHeaderBinding binding;
            HeaderViewHolder(ItemDateHeaderBinding b) { super(b.getRoot()); this.binding = b; }
        }
        static class SaleViewHolder extends RecyclerView.ViewHolder {
            ItemSaleBinding binding;
            SaleViewHolder(ItemSaleBinding b) { super(b.getRoot()); this.binding = b; }
        }
    }
}