package com.shilov.ecommerce.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
    private String id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer stockQuantity;
    private Boolean active;
}
