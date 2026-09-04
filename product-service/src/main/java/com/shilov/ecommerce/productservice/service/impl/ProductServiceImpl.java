package com.shilov.ecommerce.productservice.service.impl;

import com.shilov.ecommerce.productservice.document.Product;
import com.shilov.ecommerce.productservice.dto.ProductCreateDto;
import com.shilov.ecommerce.productservice.dto.ProductDto;
import com.shilov.ecommerce.productservice.dto.ProductUpdateDto;
import com.shilov.ecommerce.productservice.dto.StockAdjustmentDto;
import com.shilov.ecommerce.productservice.exception.ProductException;
import com.shilov.ecommerce.productservice.mapper.ProductMapper;
import com.shilov.ecommerce.productservice.repository.ProductRepository;
import com.shilov.ecommerce.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Override
    public ProductDto createProduct(ProductCreateDto productCreateDto) {

        validateBySku(productCreateDto.getSku());

        Product product = productMapper.toDocument(productCreateDto);

        return productMapper.toDto(productRepository.save(product));
    }

    @Override
    public ProductDto updateProduct(String id, ProductUpdateDto productUpdateDto) {

        Product product = findOrThrow(id);

        productMapper.fromUpdateDto(productUpdateDto, product);

        return productMapper.toDto(productRepository.save(product));
    }

    @Override
    public ProductDto getProductById(String id) {
        return productMapper.toDto(findOrThrow(id));
    }

    @Override
    public Page<ProductDto> getProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(productMapper::toDto);
    }

    @Override
    public ProductDto adjustStock(String id, StockAdjustmentDto stockAdjustmentDto) {
        Product product = findOrThrow(id);

        int newStockQuantity = product.getStockQuantity() + stockAdjustmentDto.getDelta();
        if (newStockQuantity < 0) {
            throw ProductException.invalidStockAdjustment();
        }

        product.setStockQuantity(newStockQuantity);
        return productMapper.toDto(productRepository.save(product));
    }

    private void validateBySku(String sku) {
        if (productRepository.existsBySku(sku)) {
            throw ProductException.skuConflict();
        }
    }

    private Product findOrThrow(String id) {
        return productRepository.findById(id).orElseThrow(ProductException::notFound);
    }

}
