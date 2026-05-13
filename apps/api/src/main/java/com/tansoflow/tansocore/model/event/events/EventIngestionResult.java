package com.tansoflow.tansocore.model.event.events;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EventIngestionResult {
    @Builder.Default
    private boolean usageLimitExceeded = false;
    @Builder.Default
    private boolean customerAutoCreated = false;
    private String message;
}
