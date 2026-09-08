package com.shilov.ecommerce.productservice.service;

import com.shilov.ecommerce.productservice.dto.ProductCreateDto;
import com.shilov.ecommerce.productservice.dto.ProductDto;
import com.shilov.ecommerce.productservice.dto.ProductUpdateDto;
import com.shilov.ecommerce.productservice.dto.StockAdjustmentDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductService {

    ProductDto createProduct(ProductCreateDto productCreateDto);

    ProductDto updateProduct(String id, ProductUpdateDto productUpdateDto);

    ProductDto getProductById(String id);

    Page<ProductDto> getProducts(Pageable pageable);

    ProductDto adjustStock(String id, StockAdjustmentDto stockAdjustmentDto);

}
