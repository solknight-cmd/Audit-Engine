package com.vault.audit_engine.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Reads an amount the AI model returned, which is usually a JSON number but sometimes a string copied
 * from the document: "1,000,000", "(600,000)", "₦1,000,000", "-". Anything that still isn't a number
 * becomes null, so that one figure counts as unverified instead of failing the whole audit.
 */
public class LenientAmountDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT || parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDecimalValue();
        }
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            parser.skipChildren();
            return null;
        }
        return parse(parser.getText());
    }

    static BigDecimal parse(String text) {
        String value = text.strip();
        boolean negative = value.startsWith("(") && value.endsWith(")");
        // Keep digits, the decimal point and a leading minus; drop separators, spaces and currency signs
        String digits = value.replaceAll("[^0-9.\\-]", "");
        if (digits.isEmpty() || digits.equals("-") || digits.equals(".")) {
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(digits);
            return negative ? amount.abs().negate() : amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
