package com.shilov.ecommerce.productservice.mapper;

import com.shilov.ecommerce.productservice.document.Product;
import com.shilov.ecommerce.productservice.dto.ProductCreateDto;
import com.shilov.ecommerce.productservice.dto.ProductDto;
import com.shilov.ecommerce.productservice.dto.ProductUpdateDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    Product toDocument(ProductCreateDto productCreateDto);

    ProductDto toDto(Product product);

    void fromUpdateDto(ProductUpdateDto productUpdateDto, @MappingTarget Product product);

}
