package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.order.OrderListResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderListResponse>>> listOrders(
            @RequestParam(required = false) String status,
            @ParameterObject Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        Page<Order> page;
        if (status != null && !status.isBlank()) {
            Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status.toUpperCase());
            page = orderRepository.findByCustomerIdAndStatusOrderByDeliveryDateDesc(
                    authenticatedUser.getUserId(), orderStatus, pageable);
        } else {
            page = orderRepository.findByCustomerIdOrderByDeliveryDateDesc(
                    authenticatedUser.getUserId(), pageable);
        }

        Page<OrderListResponse> responsePage = page.map(order -> {
            String productName = productRepository.findById(order.getProductId())
                    .map(Product::getName)
                    .orElse("Unknown Product");

            return OrderListResponse.builder()
                    .id(order.getId())
                    .subscriptionId(order.getSubscriptionId())
                    .productName(productName)
                    .quantity(order.getQuantity())
                    .totalAmountPaise(order.getTotalAmountPaise())
                    .deliveryDate(order.getDeliveryDate())
                    .status(order.getStatus().name())
                    .isLocked(order.getStatus() == Order.OrderStatus.LOCKED
                            || order.getStatus() == Order.OrderStatus.DELIVERED
                            || order.getStatus() == Order.OrderStatus.SKIPPED)
                    .build();
        });

        PagedResponse<OrderListResponse> data = new PagedResponse<>(responsePage.getContent());
        PaginationMeta meta = new PaginationMeta(responsePage.getNumber(), responsePage.getSize(), responsePage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }
}
