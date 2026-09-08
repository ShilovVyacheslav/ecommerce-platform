package com.shilov.ecommerce.orderservice.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ProductSnapshotDto {
    private String id;
    private String name;
    private BigDecimal price;
    private String currency;
    private Boolean active;
}
