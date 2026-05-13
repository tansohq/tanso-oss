package com.tansoflow.tansocore.mapper.stripe;

import com.tansoflow.tansocore.entity.StripeCustomer;
import com.tansoflow.tansocore.model.data.stripe.StripeCustomerDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface StripeMapper {


    @Mapping(target = "customerId", source = "customer.id")
    @Mapping(target = "stripeCustomerId", source = "id")
    @Mapping(target = "accountId", source = "account.id")
    StripeCustomerDto stripeCustomerEntityToStripeCustomerDto(StripeCustomer stripeCustomer);

}