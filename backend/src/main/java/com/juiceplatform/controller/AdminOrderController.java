package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.delivery.MarkDeliveredResponse;
import com.juiceplatform.dto.delivery.MarkSkippedRequest;
import com.juiceplatform.dto.delivery.MarkSkippedResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.DeliveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final DeliveryService deliveryService;

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
}
