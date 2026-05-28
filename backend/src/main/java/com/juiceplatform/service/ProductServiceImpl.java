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
import com.juiceplatform.exception.ProductNotFoundException;
import com.juiceplatform.mapper.ProductMapper;
import com.juiceplatform.repository.ProductPriceHistoryRepository;
import com.juiceplatform.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductPriceHistoryRepository productPriceHistoryRepository;

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

        // TODO: Audit log — action_type: PRODUCT_CREATE, target_entity: product, acting_admin: adminId
        // TODO: old_value: null, new_value: product snapshot JSONB

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

            // TODO: Audit log — action_type: PRODUCT_PRICE_UPDATE, target_entity: product, acting_admin: adminId
        }

        // TODO: Audit log — action_type: PRODUCT_UPDATE, target_entity: product, acting_admin: adminId
        // TODO: old_value / new_value JSONB snapshots

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

        // TODO: Pause all ACTIVE and PENDING_START subscriptions for this product
        //       with pause_reason = SYSTEM_PAUSED_PRODUCT_DISABLED (BR-PRD-03)
        //       Return actual count of paused subscriptions
        int autoPausedSubscriptionCount = 0;

        // TODO: Audit log — action_type: PRODUCT_DISABLE, target_entity: product, acting_admin: adminId
        // TODO: Notify admin and affected customers (BR-PRD-03, BR-NOT-01)
        //       Notifications must be sent AFTER transaction commit (BR-NOT-01)

        return ProductMapper.toDisableProductResponse(product, autoPausedSubscriptionCount);
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

        // TODO: Audit log — action_type: PRODUCT_ENABLE, target_entity: product, acting_admin: adminId

        return ProductMapper.toEnableProductResponse(product);
    }
}
