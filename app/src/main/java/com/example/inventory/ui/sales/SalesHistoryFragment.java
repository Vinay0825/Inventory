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

import com.example.inventory.databinding.FragmentSalesHistoryBinding;
import com.example.inventory.databinding.ItemSaleBinding;
import com.example.inventory.databinding.ItemDateHeaderBinding;
import com.example.inventory.model.SaleModel;
import com.example.inventory.utils.DateUtils;
import com.example.inventory.viewmodel.SalesViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SalesHistoryFragment extends Fragment {

    private FragmentSalesHistoryBinding binding;
    private SalesViewModel viewModel;
    private SalesAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSalesHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SalesViewModel.class);

        setupRecyclerView();

        viewModel.getSalesHistory().observe(getViewLifecycleOwner(), sales -> {
            if (binding == null) return;
            if (sales != null) {
                adapter.setSales(sales);
            }
        });
    }

    private void setupRecyclerView() {
        adapter = new SalesAdapter(sale -> {
            if (sale.isVoided()) return;
            String todayKey = DateUtils.getTodayKey();
            if (sale.getDayKey().equals(todayKey)) {
                showVoidConfirmation(sale);
            } else {
                Toast.makeText(getContext(), "Only today's sales can be voided", Toast.LENGTH_SHORT).show();
            }
        });
        binding.salesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.salesRecyclerView.setAdapter(adapter);
    }

    private void showVoidConfirmation(SaleModel sale) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Void Sale")
                .setMessage("Are you sure you want to void this sale? Stock will be returned.")
                .setPositiveButton("Void", (dialog, which) -> {
                    viewModel.voidSale(sale);
                    Toast.makeText(getContext(), "Sale voided", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
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

        interface OnSaleClickListener {
            void onSaleClick(SaleModel sale);
        }

        SalesAdapter(OnSaleClickListener listener) {
            this.listener = listener;
        }

        void setSales(List<SaleModel> sales) {
            items.clear();
            if (sales.isEmpty()) {
                notifyDataSetChanged();
                return;
            }

            String lastHeader = "";
            for (SaleModel sale : sales) {
                String currentHeader = DateUtils.getDayLabel(sale.getSoldAt());
                if (!currentHeader.equals(lastHeader)) {
                    items.add(currentHeader);
                    lastHeader = currentHeader;
                }
                items.add(sale);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof String ? VIEW_TYPE_HEADER : VIEW_TYPE_SALE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_HEADER) {
                ItemDateHeaderBinding binding = ItemDateHeaderBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new HeaderViewHolder(binding);
            } else {
                ItemSaleBinding binding = ItemSaleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
                return new SaleViewHolder(binding);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).binding.dateHeader.setText((String) items.get(position));
            } else {
                SaleModel sale = (SaleModel) items.get(position);
                SaleViewHolder saleHolder = (SaleViewHolder) holder;
                saleHolder.binding.saleProductName.setText(sale.getProductName());
                saleHolder.binding.saleRelativeTime.setText(DateUtils.getRelativeTime(sale.getSoldAt()));
                saleHolder.binding.saleAmount.setText(String.format(Locale.getDefault(), "₹%.2f", sale.getTotalAmount()));
                
                String quantity = sale.getQuantitySoldDecimal() > 0 ? 
                    String.format("%.2f", sale.getQuantitySoldDecimal()) : 
                    String.valueOf(sale.getQuantitySold());
                saleHolder.binding.saleQuantity.setText(quantity + " units");

                if (sale.isVoided()) {
                    saleHolder.binding.saleProductName.setPaintFlags(saleHolder.binding.saleProductName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setPaintFlags(saleHolder.binding.saleAmount.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                    saleHolder.binding.saleAmount.setTextColor(0xFF757575);
                } else {
                    saleHolder.binding.saleProductName.setPaintFlags(saleHolder.binding.saleProductName.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    saleHolder.binding.saleAmount.setPaintFlags(saleHolder.binding.saleAmount.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                    saleHolder.binding.saleAmount.setTextColor(0xFF00C896);
                }

                saleHolder.itemView.setOnClickListener(v -> listener.onSaleClick(sale));
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class HeaderViewHolder extends RecyclerView.ViewHolder {
            ItemDateHeaderBinding binding;
            HeaderViewHolder(ItemDateHeaderBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }

        static class SaleViewHolder extends RecyclerView.ViewHolder {
            ItemSaleBinding binding;
            SaleViewHolder(ItemSaleBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
