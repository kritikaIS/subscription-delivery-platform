package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.delivery.MarkDeliveredResponse;
import com.juiceplatform.dto.delivery.MarkSkippedRequest;
import com.juiceplatform.dto.delivery.MarkSkippedResponse;
import com.juiceplatform.dto.delivery.OrderCorrectionRequest;
import com.juiceplatform.dto.delivery.OrderCorrectionResponse;
import com.juiceplatform.dto.order.AdminOrderResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.AdminOrderQueryService;
import com.juiceplatform.service.DeliveryService;
import com.juiceplatform.service.OrderCorrectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final DeliveryService deliveryService;
    private final OrderCorrectionService orderCorrectionService;
    private final AdminOrderQueryService adminOrderQueryService;

    // --- Read endpoints ---

    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminOrderResponse>>> listOrders(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deliveryDate"));
        Page<AdminOrderResponse> orderPage = adminOrderQueryService.getAllOrders(startDate, endDate, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                orderPage.getContent(),
                new PaginationMeta(orderPage.getNumber(), orderPage.getSize(), orderPage.getTotalElements())
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminOrderResponse>> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(adminOrderQueryService.getOrderById(id)));
    }

    // --- Write / action endpoints ---

    @PostMapping("/{id}/deliver")
    public ResponseEntity<ApiResponse<MarkDeliveredResponse>> markDelivered(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        MarkDeliveredResponse response = deliveryService.markDelivered(id, authenticatedUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/skip")
    public ResponseEntity<ApiResponse<MarkSkippedResponse>> markSkipped(
            @PathVariable UUID id,
            @RequestBody @Valid MarkSkippedRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        MarkSkippedResponse response = deliveryService.markSkipped(
                id, request.getSkipReason(), authenticatedUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderCorrectionResponse>> correctOrder(
            @PathVariable UUID id,
            @RequestBody @Valid OrderCorrectionRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        OrderCorrectionResponse response = orderCorrectionService.correctOrder(
                id, request, authenticatedUser.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/override")
    public ResponseEntity<ApiResponse<OrderCorrectionResponse>> overrideOrder(
            @PathVariable("id") UUID orderId,
            @Valid @RequestBody com.juiceplatform.dto.delivery.AdminOrderOverrideRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedAdmin) {

        OrderCorrectionResponse response = orderCorrectionService.overrideOrder(
                orderId, request, authenticatedAdmin.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
