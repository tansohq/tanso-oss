package com.tansoflow.tansocore.model.monetization.cost;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonDeserialize(using = ModelCostRate.Deserializer.class)
public class ModelCostRate {
    @JsonProperty("input")
    private BigDecimal input;

    @JsonProperty("output")
    private BigDecimal output;

    /**
     * Custom deserializer that handles both:
     * - Object: { "input": 0.003, "output": 0.006 }
     * - Scalar (number): 0.003 → ModelCostRate(input=0.003, output=ZERO) (backward compat)
     */
    public static class Deserializer extends JsonDeserializer<ModelCostRate> {
        @Override
        public ModelCostRate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            if (node.isNumber()) {
                return new ModelCostRate(node.decimalValue(), BigDecimal.ZERO);
            }
            if (node.isObject()) {
                BigDecimal input = node.has("input") && !node.get("input").isNull()
                        ? node.get("input").decimalValue() : null;
                BigDecimal output = node.has("output") && !node.get("output").isNull()
                        ? node.get("output").decimalValue() : null;
                return new ModelCostRate(input, output);
            }
            return new ModelCostRate(null, null);
        }
    }
}
