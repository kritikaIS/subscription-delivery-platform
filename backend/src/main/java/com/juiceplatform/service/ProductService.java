package com.juiceplatform.service;

import com.juiceplatform.dto.product.CreateProductRequest;
import com.juiceplatform.dto.product.DisableProductResponse;
import com.juiceplatform.dto.product.EnableProductResponse;
import com.juiceplatform.dto.product.ProductCustomerResponse;
import com.juiceplatform.dto.product.ProductResponse;
import com.juiceplatform.dto.product.UpdateProductRequest;
import com.juiceplatform.dto.product.UpdateProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductService {

    Page<ProductCustomerResponse> listProductsForCustomer(Pageable pageable);

    Page<ProductResponse> listProductsForAdmin(Boolean isAvailable, Pageable pageable);

    ProductResponse createProduct(CreateProductRequest request, UUID adminId);

    UpdateProductResponse updateProduct(UUID productId, UpdateProductRequest request, UUID adminId);

    DisableProductResponse disableProduct(UUID productId, UUID adminId);

    EnableProductResponse enableProduct(UUID productId, UUID adminId);
}
