package com.tansoflow.tansocore.model.exception;

public class PlatformModeException extends RuntimeException {
    public PlatformModeException() {
        super("This operation is not available in Observe mode.");
    }
}
