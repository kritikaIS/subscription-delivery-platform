package com.juiceplatform.service;

import com.juiceplatform.dto.product.CreateProductRequest;
import com.juiceplatform.dto.product.DisableProductResponse;
import com.juiceplatform.dto.product.EnableProductResponse;
import com.juiceplatform.dto.product.ProductCustomerResponse;
import com.juiceplatform.dto.product.ProductResponse;
import com.juiceplatform.dto.product.UpdateProductRequest;
import com.juiceplatform.dto.product.UpdateProductResponse;
import com.juiceplatform.entity.Product;
import com.juiceplatform.entity.ProductPriceHistory;
import com.juiceplatform.entity.Subscription;
import com.juiceplatform.entity.User;
import com.juiceplatform.exception.ProductNotFoundException;
import com.juiceplatform.mapper.ProductMapper;
import com.juiceplatform.repository.ProductPriceHistoryRepository;
import com.juiceplatform.repository.ProductRepository;
import com.juiceplatform.repository.SubscriptionRepository;
import com.juiceplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceHistoryRepository productPriceHistoryRepository;
    private final AuditLogService auditLogService;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductCustomerResponse> listProductsForCustomer(Pageable pageable) {
        return productRepository.findByIsAvailableTrueOrderBySortOrderAsc(pageable)
                .map(ProductMapper::toProductCustomerResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> listProductsForAdmin(Boolean isAvailable, Pageable pageable) {
        if (isAvailable != null) {
            return productRepository.findByIsAvailable(isAvailable, pageable)
                    .map(ProductMapper::toProductResponse);
        }
        return productRepository.findAll(pageable)
                .map(ProductMapper::toProductResponse);
    }

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, UUID adminId) {
        Product product = ProductMapper.toEntity(request);
        product = productRepository.save(product);

        // Audit log — action_type: PRODUCT_CREATE (BR-AUD-01)
        auditLogService.log("PRODUCT_CREATE", "product", product.getId().toString(),
                null,
                java.util.Map.of("name", product.getName(), "pricePerUnitPaise", product.getPricePerUnitPaise()),
                adminId);

        return ProductMapper.toProductResponse(product);
    }

    @Override
    @Transactional
    public UpdateProductResponse updateProduct(UUID productId, UpdateProductRequest request, UUID adminId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        Long oldPrice = product.getPricePerUnitPaise();

        ProductMapper.applyUpdate(request, product);

        boolean priceChanged = request.getPricePerUnitPaise() != null
                && !request.getPricePerUnitPaise().equals(oldPrice);

        if (priceChanged) {
            ProductPriceHistory priceHistory = new ProductPriceHistory();
            priceHistory.setProductId(product.getId());
            priceHistory.setOldPricePaise(oldPrice);
            priceHistory.setNewPricePaise(request.getPricePerUnitPaise());
            priceHistory.setChangedBy(adminId);
            productPriceHistoryRepository.save(priceHistory);

            // Audit log — action_type: PRODUCT_PRICE_UPDATE (BR-AUD-01)
            auditLogService.log("PRODUCT_PRICE_UPDATE", "product", productId.toString(),
                    java.util.Map.of("pricePerUnitPaise", oldPrice),
                    java.util.Map.of("pricePerUnitPaise", request.getPricePerUnitPaise()),
                    adminId);
        }

        // Audit log — action_type: PRODUCT_UPDATE (BR-AUD-01)
        auditLogService.log("PRODUCT_UPDATE", "product", productId.toString(),
                null, java.util.Map.of("updatedAt", product.getUpdatedAt().toString()),
                adminId);

        return ProductMapper.toUpdateProductResponse(product);
    }

    @Override
    @Transactional
    public DisableProductResponse disableProduct(UUID productId, UUID adminId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (!product.getIsAvailable()) {
            // Idempotent: already disabled, return current state without changes
            return ProductMapper.toDisableProductResponse(product, 0);
        }

        product.setIsAvailable(false);
        product = productRepository.save(product);

        // Auto-pause all ACTIVE and PENDING_START subscriptions for this product (BR-PRD-03).
        // SYSTEM_PAUSED_PRODUCT_DISABLED: existing SCHEDULED orders remain unchanged (BR-PAU-05).
        List<Subscription> toAutoPause = subscriptionRepository.findAllByProductIdAndStatusIn(
                productId,
                List.of(Subscription.SubscriptionStatus.ACTIVE,
                        Subscription.SubscriptionStatus.PENDING_START));

        for (Subscription sub : toAutoPause) {
            sub.setStatus(Subscription.SubscriptionStatus.PAUSED);
            sub.setPauseReason(Subscription.PauseReason.SYSTEM_PAUSED_PRODUCT_DISABLED);
            subscriptionRepository.save(sub);

            // Audit log per auto-paused subscription — acting_admin is the admin who disabled
            // the product (db-schema §9 inconsistency resolution note).
            auditLogService.log(
                    "PRODUCT_DISABLE",
                    "subscription",
                    sub.getId().toString(),
                    java.util.Map.of("status", "ACTIVE_OR_PENDING_START", "pauseReason", "none"),
                    java.util.Map.of("status", "PAUSED",
                            "pauseReason", "SYSTEM_PAUSED_PRODUCT_DISABLED",
                            "productId", productId.toString()),
                    adminId,
                    "Auto-paused because product " + productId + " was disabled"
            );
        }

        // Audit log for the product disable itself (BR-AUD-01)
        auditLogService.log("PRODUCT_DISABLE", "product", productId.toString(),
                java.util.Map.of("isAvailable", true),
                java.util.Map.of("isAvailable", false,
                        "autoPausedSubscriptionCount", toAutoPause.size()),
                adminId);

        // Best-effort notifications after transaction — BR-NOT-01/02/03.
        // Notifications are sent outside the transaction boundary; failures never roll back state.
        final Product finalProduct = product;
        final int pausedCount = toAutoPause.size();
        for (Subscription sub : toAutoPause) {
            User customer = userRepository.findById(sub.getCustomerId()).orElse(null);
            String customerName = customer != null ? customer.getName() : "Unknown";
            notificationService.notifyProductAutoPause(
                    sub.getCustomerId(), customerName,
                    finalProduct.getId(), finalProduct.getName(),
                    sub.getId());
        }

        return ProductMapper.toDisableProductResponse(product, pausedCount);
    }

    @Override
    @Transactional
    public EnableProductResponse enableProduct(UUID productId, UUID adminId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getIsAvailable()) {
            // Idempotent: already enabled, return current state without changes
            return ProductMapper.toEnableProductResponse(product);
        }

        product.setIsAvailable(true);
        product = productRepository.save(product);

        // NOTE: Previously auto-paused subscriptions are NOT automatically resumed (BR-PRD-04)

        // Audit log — action_type: PRODUCT_ENABLE (BR-AUD-01)
        auditLogService.log("PRODUCT_ENABLE", "product", productId.toString(),
                java.util.Map.of("isAvailable", false),
                java.util.Map.of("isAvailable", true),
                adminId);

        return ProductMapper.toEnableProductResponse(product);
    }
}
