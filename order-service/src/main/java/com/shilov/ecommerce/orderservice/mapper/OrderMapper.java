package com.shilov.ecommerce.orderservice.mapper;

import com.shilov.ecommerce.orderservice.dto.OrderItemResponseDto;
import com.shilov.ecommerce.orderservice.dto.OrderResponseDto;
import com.shilov.ecommerce.orderservice.entity.Order;
import com.shilov.ecommerce.orderservice.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface OrderMapper {

    OrderResponseDto toDto(Order order);

    @Mapping(
            target = "lineTotal",
            expression = "java(orderItem.getUnitPrice().multiply(java.math.BigDecimal.valueOf(orderItem.getQuantity())))"
    )
    OrderItemResponseDto toItemDto(OrderItem orderItem);

}
