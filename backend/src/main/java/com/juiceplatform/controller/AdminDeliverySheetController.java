package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.deliverysheet.DeliverySheetResponse;
import com.juiceplatform.entity.DeliverySheetSnapshot;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.DeliverySheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin delivery sheet endpoints — Domain 13 from API spec.
 * GET  /api/v1/admin/delivery-sheets/{date}
 * GET  /api/v1/admin/delivery-sheets/{date}/download/pdf
 * GET  /api/v1/admin/delivery-sheets/{date}/download/csv
 */
@RestController
@RequestMapping("/api/v1/admin/delivery-sheets")
@RequiredArgsConstructor
public class AdminDeliverySheetController {

    private final DeliverySheetService deliverySheetService;

    @GetMapping("/{date}")
    public ResponseEntity<ApiResponse<DeliverySheetResponse>> getDeliverySheet(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DeliverySheetResponse response = deliverySheetService.getSnapshot(date);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{date}/download/csv")
    public ResponseEntity<byte[]> downloadCsv(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DeliverySheetResponse sheet = deliverySheetService.getSnapshot(date);

        StringBuilder csv = new StringBuilder();
        csv.append("Order ID,Customer Name,Phone,Address,Delivery Notes,Product,Quantity\n");
        for (var order : sheet.getOrders()) {
            csv.append(csvEscape(order.getOrderId().toString())).append(",")
               .append(csvEscape(order.getCustomerName())).append(",")
               .append(csvEscape(order.getPhone())).append(",")
               .append(csvEscape(order.getAddress())).append(",")
               .append(csvEscape(order.getDeliveryNotes())).append(",")
               .append(csvEscape(order.getProductName())).append(",")
               .append(order.getQuantity()).append("\n");
        }
        csv.append("\nProduct Summary\nProduct,Total Quantity\n");
        for (var summary : sheet.getJuiceSummary()) {
            csv.append(csvEscape(summary.getProductName())).append(",")
               .append(summary.getTotalQuantity()).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"delivery-sheet-" + date + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @GetMapping("/{date}/download/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        // PDF generation requires a PDF library (e.g. iText, OpenPDF).
        // Per API spec §13.2: returns application/pdf binary stream.
        // TODO: Implement PDF generation when a PDF library is added to the project.
        // For now, return a plain-text representation as a placeholder.
        DeliverySheetResponse sheet = deliverySheetService.getSnapshot(date);

        StringBuilder content = new StringBuilder();
        content.append("DELIVERY SHEET — ").append(date).append("\n\n");
        for (var order : sheet.getOrders()) {
            content.append(order.getCustomerName()).append(" | ")
                   .append(order.getAddress()).append(" | ")
                   .append(order.getProductName()).append(" x").append(order.getQuantity()).append("\n");
        }
        content.append("\nJUICE SUMMARY\n");
        for (var summary : sheet.getJuiceSummary()) {
            content.append(summary.getProductName()).append(": ").append(summary.getTotalQuantity()).append("\n");
        }

        byte[] bytes = content.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"delivery-sheet-" + date + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/{date}/regenerate")
    public ResponseEntity<ApiResponse<DeliverySheetResponse>> regenerate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        DeliverySheetResponse response = deliverySheetService.generateSnapshot(
                date, DeliverySheetSnapshot.GeneratedBySource.ADMIN_RERUN,
                authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
