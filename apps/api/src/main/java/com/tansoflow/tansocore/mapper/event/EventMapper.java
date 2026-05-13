package com.tansoflow.tansocore.mapper.event;

import com.tansoflow.tansocore.entity.Event;
import com.tansoflow.tansocore.model.event.events.EventDto;
import com.tansoflow.tansocore.model.event.events.request.EventRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface EventMapper {
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    Event eventDtoToEventEntity(EventDto eventDto);

    @Mapping(target = "accountId", source = "account.id")
    @Mapping(target = "featureKey", ignore = true)
    @Mapping(target = "customerReferenceId", ignore = true)
    @Mapping(target = "stripeCustomerId", ignore = true)
    EventDto eventEntityToEventDto(Event event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "ingestError", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    @Mapping(target = "eventType", ignore = true)
    @Mapping(target = "costUnit", ignore = true)
    @Mapping(target = "revenueUnit", ignore = true)
    @Mapping(target = "usageUnitType", ignore = true)
    @Mapping(target = "properties", ignore = true)
    @Mapping(target = "context", ignore = true)
    @Mapping(target = "customerIsNative", ignore = true)
    @Mapping(target = "featureIsNative", ignore = true)
    @Mapping(target = "subscriptionIsNative", ignore = true)
    @Mapping(target = "entitlementIsNative", ignore = true)
    @Mapping(target = "invoiceIsNative", ignore = true)
    @Mapping(target = "model", source = "costInput.model")
    @Mapping(target = "modelProvider", source = "costInput.modelProvider")
    @Mapping(target = "costUnits", source = "costInput.costUnits")
    @Mapping(target = "inputTokens", source = "costInput.inputTokens")
    @Mapping(target = "outputTokens", source = "costInput.outputTokens")
    EventDto eventRequestToEventDto(EventRequest eventRequest);
}
