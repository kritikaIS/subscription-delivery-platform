package com.juiceplatform.mapper;

import com.juiceplatform.dto.product.CreateProductRequest;
import com.juiceplatform.dto.product.DisableProductResponse;
import com.juiceplatform.dto.product.EnableProductResponse;
import com.juiceplatform.dto.product.ProductCustomerResponse;
import com.juiceplatform.dto.product.ProductResponse;
import com.juiceplatform.dto.product.UpdateProductRequest;
import com.juiceplatform.dto.product.UpdateProductResponse;
import com.juiceplatform.entity.Product;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .pricePerUnitPaise(product.getPricePerUnitPaise())
                .unitLabel(product.getUnitLabel())
                .category(product.getCategory())
                .isAvailable(product.getIsAvailable())
                .imageUrl(product.getImageUrl())
                .createdAt(product.getCreatedAt())
                .build();
    }

    public static ProductCustomerResponse toProductCustomerResponse(Product product) {
        return ProductCustomerResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .pricePerUnitPaise(product.getPricePerUnitPaise())
                .unitLabel(product.getUnitLabel())
                .category(product.getCategory())
                .imageUrl(product.getImageUrl())
                .build();
    }

    public static UpdateProductResponse toUpdateProductResponse(Product product) {
        return UpdateProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .pricePerUnitPaise(product.getPricePerUnitPaise())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public static DisableProductResponse toDisableProductResponse(Product product, int autoPausedSubscriptionCount) {
        return DisableProductResponse.builder()
                .productId(product.getId())
                .isAvailable(product.getIsAvailable())
                .autoPausedSubscriptionCount(autoPausedSubscriptionCount)
                .disabledAt(product.getUpdatedAt())
                .build();
    }

    public static EnableProductResponse toEnableProductResponse(Product product) {
        return EnableProductResponse.builder()
                .productId(product.getId())
                .isAvailable(product.getIsAvailable())
                .enabledAt(product.getUpdatedAt())
                .build();
    }

    public static Product toEntity(CreateProductRequest request) {
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPricePerUnitPaise(request.getPricePerUnitPaise());
        product.setUnitLabel(request.getUnitLabel());
        product.setCategory(request.getCategory());
        product.setImageUrl(request.getImageUrl());
        return product;
    }

    public static void applyUpdate(UpdateProductRequest request, Product product) {
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPricePerUnitPaise() != null) {
            product.setPricePerUnitPaise(request.getPricePerUnitPaise());
        }
        if (request.getUnitLabel() != null) {
            product.setUnitLabel(request.getUnitLabel());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getImageUrl() != null) {
            product.setImageUrl(request.getImageUrl());
        }
    }
}
