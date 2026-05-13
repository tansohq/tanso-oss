package com.tansoflow.tansocore.model.monetization.request;

import lombok.Data;

import java.util.List;

@Data
public class UuidListRequest {
    // TODO: make this more specific rather than generic.
    private List<String> ids;
}
