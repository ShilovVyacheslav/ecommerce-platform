package com.shilov.ecommerce.productservice.controller;

import com.shilov.ecommerce.productservice.dto.ProductCreateDto;
import com.shilov.ecommerce.productservice.dto.ProductDto;
import com.shilov.ecommerce.productservice.dto.ProductUpdateDto;
import com.shilov.ecommerce.productservice.dto.StockAdjustmentDto;
import com.shilov.ecommerce.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Page<ProductDto>> getProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProducts(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> createProduct(
            @Valid @RequestBody ProductCreateDto productCreateDto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.createProduct(productCreateDto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> updateProduct(@PathVariable String id,
                                                    @Valid @RequestBody ProductUpdateDto productUpdateDto) {
        return ResponseEntity.ok(productService.updateProduct(id, productUpdateDto));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDto> adjustStock(@PathVariable String id,
                                  @Valid @RequestBody StockAdjustmentDto stockAdjustmentDto) {
        return ResponseEntity.ok(productService.adjustStock(id, stockAdjustmentDto));
    }

}
