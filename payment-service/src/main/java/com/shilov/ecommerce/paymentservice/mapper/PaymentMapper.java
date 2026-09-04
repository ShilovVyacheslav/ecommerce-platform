package com.shilov.ecommerce.paymentservice.mapper;

import com.shilov.ecommerce.paymentservice.dto.PaymentResponseDto;
import com.shilov.ecommerce.paymentservice.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface PaymentMapper {

    PaymentResponseDto toDto(Payment payment);

}
