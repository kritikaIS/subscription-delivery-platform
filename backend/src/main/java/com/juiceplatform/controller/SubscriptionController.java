package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.subscription.CancelSubscriptionResponse;
import com.juiceplatform.dto.subscription.ChangeProductRequest;
import com.juiceplatform.dto.subscription.ChangeQuantityRequest;
import com.juiceplatform.dto.subscription.ChangeRequestListEntry;
import com.juiceplatform.dto.subscription.CreateSubscriptionRequest;
import com.juiceplatform.dto.subscription.PauseSubscriptionResponse;
import com.juiceplatform.dto.subscription.ProductChangeResponse;
import com.juiceplatform.dto.subscription.QuantityChangeResponse;
import com.juiceplatform.dto.subscription.ResumeSubscriptionResponse;
import com.juiceplatform.dto.subscription.SubscriptionDetailResponse;
import com.juiceplatform.dto.subscription.SubscriptionResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<SubscriptionResponse>> createSubscription(
            @RequestBody @Valid CreateSubscriptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        SubscriptionResponse response = subscriptionService.createSubscription(
                authenticatedUser.getUserId(), request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<SubscriptionResponse>>> listSubscriptions(
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        Page<SubscriptionResponse> page = subscriptionService.listSubscriptions(
                authenticatedUser.getUserId(), status, pageable);

        PagedResponse<SubscriptionResponse> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionDetailResponse>> getSubscriptionDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        SubscriptionDetailResponse response = subscriptionService.getSubscriptionDetail(
                authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/change-quantity")
    public ResponseEntity<ApiResponse<QuantityChangeResponse>> changeQuantity(
            @PathVariable UUID id,
            @RequestBody @Valid ChangeQuantityRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        QuantityChangeResponse response = subscriptionService.changeQuantity(
                authenticatedUser.getUserId(), id, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/change-product")
    public ResponseEntity<ApiResponse<ProductChangeResponse>> changeProduct(
            @PathVariable UUID id,
            @RequestBody @Valid ChangeProductRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        ProductChangeResponse response = subscriptionService.changeProduct(
                authenticatedUser.getUserId(), id, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}/change-requests")
    public ResponseEntity<ApiResponse<PagedResponse<ChangeRequestListEntry>>> listChangeRequests(
            @PathVariable UUID id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        Page<ChangeRequestListEntry> page = subscriptionService.listChangeRequests(
                authenticatedUser.getUserId(), id, type, status, pageable);

        PagedResponse<ChangeRequestListEntry> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<ApiResponse<PauseSubscriptionResponse>> pauseSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        PauseSubscriptionResponse response = subscriptionService.pauseSubscription(
                authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<ApiResponse<ResumeSubscriptionResponse>> resumeSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        ResumeSubscriptionResponse response = subscriptionService.resumeSubscription(
                authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CancelSubscriptionResponse>> cancelSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(
                authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<CancelSubscriptionResponse>> deleteSubscription(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        CancelSubscriptionResponse response = subscriptionService.cancelSubscription(
                authenticatedUser.getUserId(), id);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
