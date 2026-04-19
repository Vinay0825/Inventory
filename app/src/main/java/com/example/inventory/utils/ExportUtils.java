package com.example.inventory.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.inventory.model.ProductModel;
import com.example.inventory.model.SaleModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ExportUtils {

    public static void exportProductsToCSV(Context context, List<ProductModel> products) {
        String fileName = "products_" + System.currentTimeMillis() + ".csv";
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Barcode,Name,Category,Price,Stock,Unit,Threshold,Expiry\n");
            for (ProductModel p : products) {
                writer.append(p.getBarcode()).append(",")
                        .append(p.getName()).append(",")
                        .append(p.getCategory()).append(",")
                        .append(String.valueOf(p.getPrice())).append(",")
                        .append(String.valueOf(ProductModel.isDecimalUnit(p.getUnit()) ? p.getCurrentStockDecimal() : p.getCurrentStock())).append(",")
                        .append(p.getUnit()).append(",")
                        .append(String.valueOf(p.getLowStockThreshold())).append(",")
                        .append(p.getExpiryDate() != null ? DateUtils.formatDate(p.getExpiryDate().toDate()) : "N/A").append("\n");
            }
            shareFile(context, file, "text/csv");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show();
        }
    }

    public static void exportSalesToCSV(Context context, List<SaleModel> sales) {
        String fileName = "sales_" + System.currentTimeMillis() + ".csv";
        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        try (FileWriter writer = new FileWriter(file)) {
            writer.append("Date,Product,Quantity,Price,Total,Voided\n");
            for (SaleModel s : sales) {
                String q = s.getQuantitySoldDecimal() > 0 ? String.valueOf(s.getQuantitySoldDecimal()) : String.valueOf(s.getQuantitySold());
                writer.append(DateUtils.formatDate(s.getSoldAt().toDate())).append(",")
                        .append(s.getProductName()).append(",")
                        .append(q).append(",")
                        .append(String.valueOf(s.getPriceAtSale())).append(",")
                        .append(String.valueOf(s.getTotalAmount())).append(",")
                        .append(String.valueOf(s.isVoided())).append("\n");
            }
            shareFile(context, file, "text/csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void exportSalesReportToPDF(Context context, List<SaleModel> sales, String shopName) {
        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        // Header
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(24);
        canvas.drawText(shopName != null ? shopName : "ShopEase", 40, 50, paint);
        paint.setTextSize(14);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText("Sales Report - " + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()), 40, 80, paint);

        // Table Header
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Product", 40, 120, paint);
        canvas.drawText("Qty", 250, 120, paint);
        canvas.drawText("Price", 350, 120, paint);
        canvas.drawText("Total", 450, 120, paint);

        int y = 150;
        double grandTotal = 0;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));

        for (SaleModel s : sales) {
            if (y > 800) break; // Simple page break check
            canvas.drawText(s.getProductName(), 40, y, paint);
            String q = s.getQuantitySoldDecimal() > 0 ? String.format("%.2f", s.getQuantitySoldDecimal()) : String.valueOf(s.getQuantitySold());
            canvas.drawText(q, 250, y, paint);
            canvas.drawText(String.format("%.2f", s.getPriceAtSale()), 350, y, paint);
            canvas.drawText(String.format("%.2f", s.getTotalAmount()), 450, y, paint);
            
            if (!s.isVoided()) {
                grandTotal += s.getTotalAmount();
            }
            y += 20;
        }

        // Footer
        y += 20;
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Total Revenue: ₹" + String.format("%.2f", grandTotal), 40, y, paint);

        document.finishPage(page);

        File file = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Sales_Report_" + System.currentTimeMillis() + ".pdf");
        try {
            document.writeTo(new FileOutputStream(file));
            shareFile(context, file, "application/pdf");
        } catch (IOException e) {
            e.printStackTrace();
        }
        document.close();
    }

    private static void shareFile(Context context, File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Share File"));
    }
}
