package com.shilov.ecommerce.productservice.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.shilov.ecommerce.validation.StrictBooleanDeserializer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateDto {

    private String name;

    private String description;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal price;

    private String currency;

    @JsonDeserialize(using = StrictBooleanDeserializer.class)
    private Boolean active;
}
