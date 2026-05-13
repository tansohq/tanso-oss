package com.tansoflow.tansocore.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema
public class Error {
    private String message;
    private String detail;

    public Error(String message, String detail) {
        this.message = message;
        this.detail = detail;
    }

    public Error(String message) {
        this.message = message;
    }
}
