package com.juiceplatform.service;

import com.juiceplatform.dto.order.AdminOrderResponse;
import com.juiceplatform.entity.Order;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.BusinessException;
import com.juiceplatform.repository.OrderRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only admin query service for orders.
 * Avoids N+1 queries by batch-loading related users and products before mapping.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderQueryService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getAllOrders(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Page<Order> orderPage;
        if (startDate != null && endDate != null) {
            orderPage = orderRepository.findByDeliveryDateBetween(startDate, endDate, pageable);
        } else {
            orderPage = orderRepository.findAll(pageable);
        }
        return mapPageWithBatchLoad(orderPage);
    }

    @Transactional(readOnly = true)
    public AdminOrderResponse getOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found", HttpStatus.NOT_FOUND));

        User customer = userRepository.findById(order.getCustomerId()).orElse(null);
        Product product = productRepository.findById(order.getProductId()).orElse(null);
        return mapToDto(order, customer, product);
    }

    /**
     * Batch-loads all related users and products for a page of orders to avoid N+1 queries.
     */
    private Page<AdminOrderResponse> mapPageWithBatchLoad(Page<Order> page) {
        List<Order> orders = page.getContent();

        Set<UUID> customerIds = orders.stream().map(Order::getCustomerId).collect(Collectors.toSet());
        Set<UUID> productIds = orders.stream().map(Order::getProductId).collect(Collectors.toSet());

        Map<UUID, User> userMap = userRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return page.map(order -> mapToDto(order, userMap.get(order.getCustomerId()), productMap.get(order.getProductId())));
    }

    private AdminOrderResponse mapToDto(Order order, User customer, Product product) {
        return AdminOrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .customerName(customer != null ? customer.getName() : "Unknown")
                .customerPhone(customer != null ? customer.getPhone() : "Unknown")
                .subscriptionId(order.getSubscriptionId())
                .productId(order.getProductId())
                .productName(product != null ? product.getName() : "Unknown")
                .quantity(order.getQuantity())
                .amountPaise(order.getTotalAmountPaise())
                .status(order.getStatus().name())
                .deliveryDate(order.getDeliveryDate())
                .deliveryAddress(formatAddress(order))
                .deliveryNotes(order.getDeliveryNotes())
                .build();
    }

    /**
     * Null-safe address formatter, consistent with DeliverySheetService.
     */
    private String formatAddress(Order order) {
        StringBuilder sb = new StringBuilder();
        if (order.getDeliveryLine1() != null) sb.append(order.getDeliveryLine1());
        if (order.getDeliveryLine2() != null && !order.getDeliveryLine2().isBlank()) {
            sb.append(", ").append(order.getDeliveryLine2());
        }
        if (order.getDeliveryCity() != null) sb.append(", ").append(order.getDeliveryCity());
        if (order.getDeliveryPincode() != null) sb.append(" ").append(order.getDeliveryPincode());
        return sb.toString();
    }
}
