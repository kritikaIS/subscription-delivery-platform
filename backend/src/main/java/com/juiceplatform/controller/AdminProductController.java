package com.juiceplatform.controller;

import com.juiceplatform.dto.common.ApiResponse;
import com.juiceplatform.dto.common.PagedResponse;
import com.juiceplatform.dto.common.PaginationMeta;
import com.juiceplatform.dto.product.CreateProductRequest;
import com.juiceplatform.dto.product.DisableProductResponse;
import com.juiceplatform.dto.product.EnableProductResponse;
import com.juiceplatform.dto.product.ProductResponse;
import com.juiceplatform.dto.product.UpdateProductRequest;
import com.juiceplatform.dto.product.UpdateProductResponse;
import com.juiceplatform.security.AuthenticatedUser;
import com.juiceplatform.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.annotations.ParameterObject;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> listProducts(
            @RequestParam(required = false) Boolean isAvailable,
            @ParameterObject @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<ProductResponse> page = productService.listProductsForAdmin(isAvailable, pageable);

        PagedResponse<ProductResponse> data = new PagedResponse<>(page.getContent());
        PaginationMeta meta = new PaginationMeta(page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.success(data, meta));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @RequestBody @Valid CreateProductRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        ProductResponse response = productService.createProduct(request, authenticatedUser.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UpdateProductResponse>> updateProduct(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateProductRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        UpdateProductResponse response = productService.updateProduct(id, request, authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<ApiResponse<DisableProductResponse>> disableProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        DisableProductResponse response = productService.disableProduct(id, authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<EnableProductResponse>> enableProduct(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {

        EnableProductResponse response = productService.enableProduct(id, authenticatedUser.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
