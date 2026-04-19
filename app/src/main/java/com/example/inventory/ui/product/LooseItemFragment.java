package com.example.inventory.ui.product;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.inventory.R;
import com.example.inventory.databinding.FragmentLooseItemBinding;
import com.example.inventory.databinding.ItemSavedBarcodeBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.ArrayList;
import java.util.List;

public class LooseItemFragment extends Fragment {

    private FragmentLooseItemBinding binding;
    private String generatedBarcode;
    private Bitmap generatedBitmap;

    private final List<DocumentSnapshot> savedDocs = new ArrayList<>();
    private SavedAdapter savedAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLooseItemBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Saved barcodes list
        savedAdapter = new SavedAdapter();
        binding.savedBarcodesRecyclerView.setLayoutManager(
            new LinearLayoutManager(getContext()));
        binding.savedBarcodesRecyclerView.setAdapter(savedAdapter);
        loadSavedBarcodes();

        // Generate button
        binding.generateLooseBarcodeButton.setOnClickListener(v -> {
            String name = binding.looseItemNameEditText
                .getText().toString().trim();
            String label = binding.looseItemLabelEditText
                .getText().toString().trim();
            if (name.isEmpty()) {
                binding.looseItemNameEditText.setError("Product name required");
                return;
            }
            String fullName = label.isEmpty() ? name : name + " - " + label;
            generateBarcode(fullName);
        });
    }

    private void generateBarcode(String name) {
        // barcode = "LOOSE" + timestamp + up-to-3 uppercase initials
        String letters = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        String initials = letters.substring(0, Math.min(3, letters.length()));
        generatedBarcode = "LOOSE" + System.currentTimeMillis() + initials;

        try {
            MultiFormatWriter writer = new MultiFormatWriter();
            BitMatrix matrix = writer.encode(
                generatedBarcode, BarcodeFormat.CODE_128, 900, 300);
            // Convert BitMatrix to Bitmap manually (no BarcodeEncoder needed)
            int w = matrix.getWidth(), h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    pixels[y * w + x] = matrix.get(x, y)
                        ? 0xFF000000 : 0xFFFFFFFF;
            generatedBitmap = Bitmap.createBitmap(
                pixels, w, h, Bitmap.Config.RGB_565);
            binding.barcodeImageView.setImageBitmap(generatedBitmap);
        } catch (Exception e) {
            Toast.makeText(getContext(),
                "Barcode generation failed: " + e.getMessage(),
                Toast.LENGTH_SHORT).show();
            return;
        }

        binding.looseBarcodeValue.setText(generatedBarcode);
        binding.generatedResultContainer.setVisibility(View.VISIBLE);
        binding.barcodeActionButtons.setVisibility(View.VISIBLE);

        // Save as Product
        binding.saveAsProductButton.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString("barcode", generatedBarcode);
            args.putString("productName", name);
            Navigation.findNavController(requireView())
                .navigate(R.id.addProductFragment, args);
        });

        // Share only (no delete button in generate UI)
        binding.shareButton.setOnClickListener(v -> shareBarcode());

        // Close
        binding.clearBarcodeButton.setOnClickListener(v -> clearBarcodeUI());
    }

    private void shareBarcode() {
        if (generatedBarcode == null) return;
        android.content.Intent i =
            new android.content.Intent(android.content.Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(android.content.Intent.EXTRA_TEXT, generatedBarcode);
        startActivity(android.content.Intent.createChooser(i, "Share Barcode"));
    }

    private void clearBarcodeUI() {
        if (binding == null) return;
        binding.barcodeImageView.setImageDrawable(null);
        binding.looseBarcodeValue.setText("");
        binding.barcodeActionButtons.setVisibility(View.GONE);
        binding.generatedResultContainer.setVisibility(View.GONE);
        generatedBarcode = null;
        generatedBitmap  = null;
        binding.looseItemNameEditText.setText("");
        binding.looseItemLabelEditText.setText("");
    }

    private void loadSavedBarcodes() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("looseItems")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(snapshot -> {
                if (binding == null) return;
                savedDocs.clear();
                savedDocs.addAll(snapshot.getDocuments());
                savedAdapter.notifyDataSetChanged();
            });
    }

    // ── Saved Barcodes Adapter ────────────────────────────────────────────

    private class SavedAdapter
            extends RecyclerView.Adapter<SavedAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ItemSavedBarcodeBinding b;
            VH(ItemSavedBarcodeBinding b) {
                super(b.getRoot()); this.b = b;
            }
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemSavedBarcodeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            DocumentSnapshot doc  = savedDocs.get(pos);
            String name    = doc.getString("name");
            String barcode = doc.getString("barcode");

            holder.b.savedBarcodeName.setText(name != null ? name : "—");
            holder.b.savedBarcodeValue.setText(barcode != null ? barcode : "—");

            // Generate barcode image inline
            if (barcode != null && !barcode.isEmpty()) {
                try {
                    MultiFormatWriter writer = new MultiFormatWriter();
                    BitMatrix matrix = writer.encode(
                        barcode, BarcodeFormat.CODE_128, 600, 150);
                    int w = matrix.getWidth(), h = matrix.getHeight();
                    int[] pixels = new int[w * h];
                    for (int y = 0; y < h; y++)
                        for (int x = 0; x < w; x++)
                            pixels[y * w + x] = matrix.get(x, y)
                                ? 0xFF000000 : 0xFFFFFFFF;
                    Bitmap bmp = Bitmap.createBitmap(
                        pixels, w, h, Bitmap.Config.RGB_565);
                    holder.b.savedBarcodeImage.setImageBitmap(bmp);
                    holder.b.savedBarcodeImage.setVisibility(View.VISIBLE);
                } catch (Exception e) {
                    holder.b.savedBarcodeImage.setVisibility(View.GONE);
                }
            }

            // Share button
            holder.b.savedBarcodeShareButton.setOnClickListener(v -> {
                if (barcode == null) return;
                android.content.Intent i =
                    new android.content.Intent(
                        android.content.Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(android.content.Intent.EXTRA_TEXT, barcode);
                startActivity(android.content.Intent.createChooser(
                    i, "Share Barcode"));
            });

            // Long press = delete dialog
            holder.itemView.setOnLongClickListener(v -> {
                new android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete Barcode")
                    .setMessage("Remove \"" + name + "\" from saved barcodes?")
                    .setPositiveButton("Delete", (d, w) -> {
                        String uid = FirebaseAuth.getInstance()
                            .getCurrentUser().getUid();
                        FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .collection("looseItems").document(doc.getId())
                            .delete()
                            .addOnSuccessListener(a -> loadSavedBarcodes());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
                return true;
            });
        }

        @Override public int getItemCount() { return savedDocs.size(); }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
