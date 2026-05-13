package com.tansoflow.tansocore.model.event.events.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventIngestionResponse {
    private Boolean usageLimitExceeded;
    private String message;
}
