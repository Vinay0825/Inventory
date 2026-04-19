package com.example.inventory.ui.sales;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.R;
import com.example.inventory.model.RestockModel;
import com.example.inventory.utils.DateUtils;
import com.example.inventory.viewmodel.SalesViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RestockHistoryFragment extends Fragment {

    private static final String TAG = "RestockHistory";
    private RecyclerView recyclerView;
    private LinearLayout emptyView;
    private final List<RestockModel> allRestocks = new ArrayList<>();
    private RestockAdapter adapter;
    private SalesViewModel salesViewModel;
    private String currentFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_restock_history,
                container, false);
        recyclerView = view.findViewById(R.id.restockRecyclerView);
        emptyView    = view.findViewById(R.id.emptyRestockView);

        // Share SalesViewModel with parent so filter chip changes propagate
        Fragment parent = getParentFragment();
        salesViewModel = new ViewModelProvider(
                parent != null ? parent : this)
                .get(SalesViewModel.class);

        adapter = new RestockAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        loadRestocks();

        // Observe filter changes from SalesHistoryFragment chip clicks
        salesViewModel.getCurrentFilter().observe(getViewLifecycleOwner(), filter -> {
            if (filter == null) return;
            currentFilter = filter;
            applyFilter();
        });

        return view;
    }

    private void loadRestocks() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            showEmpty();
            return;
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        android.util.Log.d(TAG, "Loading restocks for: " + userId);

        FirebaseFirestore.getInstance()
            .collection("users").document(userId)
            .collection("restocks")
            .get()
            .addOnSuccessListener(snapshot -> {
                android.util.Log.d(TAG, "Snapshot size: " + snapshot.size());
                allRestocks.clear();
                for (QueryDocumentSnapshot doc : snapshot) {
                    try {
                        RestockModel r = doc.toObject(RestockModel.class);
                        if (r != null) {
                            r.setRestockId(doc.getId());
                            allRestocks.add(r);
                        }
                    } catch (Exception ex) {
                        android.util.Log.e(TAG, "Parse error: " + doc.getId(), ex);
                    }
                }
                // Sort descending by restockedAt
                allRestocks.sort((a, b) -> {
                    if (a.getRestockedAt() == null) return 1;
                    if (b.getRestockedAt() == null) return -1;
                    return b.getRestockedAt().compareTo(a.getRestockedAt());
                });
                applyFilter();
            })
            .addOnFailureListener(e -> {
                android.util.Log.e(TAG, "Fetch failed: " + e.getMessage(), e);
                showEmpty();
            });
    }

    private void applyFilter() {
        List<RestockModel> filtered = new ArrayList<>();
        for (RestockModel r : allRestocks) {
            if (r.getRestockedAt() == null) continue;
            Date d = r.getRestockedAt().toDate();
            if ("today".equals(currentFilter)) {
                String todayKey = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String rKey = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.getDefault()).format(d);
                if (todayKey.equals(rKey)) filtered.add(r);
            } else if ("week".equals(currentFilter)) {
                Calendar weekStart = Calendar.getInstance();
                weekStart.set(Calendar.DAY_OF_WEEK,
                    weekStart.getFirstDayOfWeek());
                weekStart.set(Calendar.HOUR_OF_DAY, 0);
                weekStart.set(Calendar.MINUTE, 0);
                weekStart.set(Calendar.SECOND, 0);
                weekStart.set(Calendar.MILLISECOND, 0);
                if (!d.before(weekStart.getTime())) filtered.add(r);
            } else if ("month".equals(currentFilter)) {
                Calendar monthStart = Calendar.getInstance();
                monthStart.set(Calendar.DAY_OF_MONTH, 1);
                monthStart.set(Calendar.HOUR_OF_DAY, 0);
                monthStart.set(Calendar.MINUTE, 0);
                monthStart.set(Calendar.SECOND, 0);
                monthStart.set(Calendar.MILLISECOND, 0);
                if (!d.before(monthStart.getTime())) filtered.add(r);
            } else {
                filtered.add(r); // all time
            }
        }

        adapter.setRestocks(filtered);

        // Update restock count in summary card via parent
        if (getParentFragment() instanceof SalesHistoryFragment) {
            ((SalesHistoryFragment) getParentFragment())
                .updateRestockCount(filtered.size());
        }

        if (filtered.isEmpty()) showEmpty();
        else showList();
    }

    private void showEmpty() {
        if (recyclerView == null || emptyView == null) return;
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.VISIBLE);
    }

    private void showList() {
        if (recyclerView == null || emptyView == null) return;
        recyclerView.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    // ── Adapter ──────────────────────────────────────────────────────────

    private class RestockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM   = 1;
        private final List<Object> items = new ArrayList<>(); // String headers or RestockModel

        void setRestocks(List<RestockModel> restocks) {
            items.clear();
            String lastHeader = "";
            for (RestockModel r : restocks) {
                String header = DateUtils.getDayLabel(r.getRestockedAt());
                if (!header.equals(lastHeader)) {
                    items.add(header);
                    lastHeader = header;
                }
                items.add(r);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int pos) {
            return items.get(pos) instanceof String ? TYPE_HEADER : TYPE_ITEM;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH(com.example.inventory.databinding
                    .ItemDateHeaderBinding.inflate(inf, parent, false));
            }
            return new ItemVH(com.example.inventory.databinding
                .ItemRestockBinding.inflate(inf, parent, false));
        }

        @Override
        public void onBindViewHolder(
                @NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderVH) {
                ((HeaderVH) holder).binding.dateHeader
                    .setText((String) items.get(position));
                return;
            }
            RestockModel r = (RestockModel) items.get(position);
            ItemVH h = (ItemVH) holder;

            h.binding.productNameText.setText(
                r.getProductName() != null ? r.getProductName() : "Unknown");

            // Quantity with + prefix
            String qtyStr = "+" + r.getQuantityAdded()
                + " " + (r.getUnit() != null ? r.getUnit() : "units");
            h.binding.quantityText.setText(qtyStr);

            // Date formatted as relative time
            if (r.getRestockedAt() != null) {
                h.binding.dateText.setText(
                    DateUtils.getRelativeTime(r.getRestockedAt()));
            } else {
                h.binding.dateText.setText("—");
            }

            // Barcode
            if (r.getBarcode() != null) {
                h.binding.restockBarcodeText.setText(r.getBarcode());
                h.binding.restockBarcodeText.setVisibility(View.VISIBLE);
            } else {
                h.binding.restockBarcodeText.setVisibility(View.GONE);
            }

            // Supplier
            String sup = r.getSupplier();
            if (sup != null && !sup.isEmpty()) {
                h.binding.supplierText.setText("· " + sup);
                h.binding.supplierText.setVisibility(View.VISIBLE);
            } else {
                h.binding.supplierText.setVisibility(View.GONE);
            }

            // Voided state — strikethrough + badge
            if (r.isVoided()) {
                h.binding.restockVoidedBadge.setVisibility(View.VISIBLE);
                h.binding.voidRestockButton.setVisibility(View.GONE);
                h.binding.quantityText.setPaintFlags(
                    h.binding.quantityText.getPaintFlags()
                        | Paint.STRIKE_THRU_TEXT_FLAG);
                h.binding.quantityText.setTextColor(0xFF757575);
            } else {
                h.binding.restockVoidedBadge.setVisibility(View.GONE);
                h.binding.voidRestockButton.setVisibility(View.VISIBLE);
                h.binding.quantityText.setPaintFlags(
                    h.binding.quantityText.getPaintFlags()
                        & ~Paint.STRIKE_THRU_TEXT_FLAG);
                // colorPrimary for positive quantity
                h.binding.quantityText.setTextColor(
                    com.google.android.material.color.MaterialColors.getColor(
                        h.binding.quantityText,
                        com.google.android.material.R.attr.colorPrimary));
            }

            // Void button
            h.binding.voidRestockButton.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(v.getContext())
                    .setTitle("Void Restock")
                    .setMessage("This will subtract "
                        + r.getQuantityAdded()
                        + " units back from \""
                        + r.getProductName()
                        + "\". Cannot be undone.")
                    .setPositiveButton("Void", (dialog, which) -> {
                        salesViewModel.voidRestock(
                            r.getRestockId(),
                            r.getBarcode(),
                            r.getQuantityAdded(),
                            r.getUnit() != null ? r.getUnit() : "pcs");
                        r.setVoided(true);
                        notifyItemChanged(holder.getAdapterPosition());
                        Toast.makeText(v.getContext(),
                            "Restock voided",
                            Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class HeaderVH extends RecyclerView.ViewHolder {
            com.example.inventory.databinding.ItemDateHeaderBinding binding;
            HeaderVH(com.example.inventory.databinding.ItemDateHeaderBinding b) {
                super(b.getRoot()); binding = b;
            }
        }
        class ItemVH extends RecyclerView.ViewHolder {
            com.example.inventory.databinding.ItemRestockBinding binding;
            ItemVH(com.example.inventory.databinding.ItemRestockBinding b) {
                super(b.getRoot()); binding = b;
            }
        }
    }
}