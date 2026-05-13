package com.tansoflow.tansocore.mapper.monetization;

import com.tansoflow.tansocore.entity.Subscription;
import com.tansoflow.tansocore.model.subscription.SubscriptionDto;
import com.tansoflow.tansocore.model.subscription.request.SubscriptionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SubscriptionMapper {
    @Mapping(target = "id", source = "id")
    @Mapping(target = "customer.customerReferenceId", source = "customer.externalClientCustomerId")
    @Mapping(target = "isActive", source = "isActive")
    @Mapping(target = "intervalMonths", source = "intervalMonths")
    @Mapping(target = "plan", source = "plan")
    @Mapping(target = "gracePeriodDays", source = "gracePeriodDays")
    @Mapping(target = "currentPeriodStart", source = "currentPeriodStart")
    @Mapping(target = "currentPeriodEnd", source = "currentPeriodEnd")
    @Mapping(target = "cancelMode", source = "cancelMode")
    @Mapping(target = "cancelEffectiveAt", source = "cancelEffectiveAt")
    @Mapping(target = "cancelledAt", source = "cancelledAt")
    @Mapping(target = "billingAnchorDay", source = "billingAnchorDay")
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "scheduledChange", ignore = true)
    SubscriptionDto subscriptionEntityToSubscriptionDto(Subscription subscription);

    List<SubscriptionDto> subscriptionEntityListToSubscriptionDtoList(List<Subscription> subscriptions);

    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "plan", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "archivedAt", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "gracePeriodDays", source = "gracePeriod")
    @Mapping(target = "intervalMonths", ignore = true)
    @Mapping(target = "currentPeriodStart", ignore = true)
    @Mapping(target = "currentPeriodEnd", ignore = true)
    @Mapping(target = "cancelMode", ignore = true)
    @Mapping(target = "cancelEffectiveAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    @Mapping(target = "billingAnchorDay", ignore = true)
    void updateSubscriptionEntity(SubscriptionRequest subscriptionRequest, @MappingTarget Subscription subscription);
}
