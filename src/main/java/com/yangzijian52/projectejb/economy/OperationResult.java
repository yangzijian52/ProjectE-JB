package com.yangzijian52.projectejb.economy;

import java.util.LinkedHashMap;
import java.util.Map;

public record OperationResult(Code code, Map<String, Object> values) {
    public static OperationResult of(Code code) {
        return new OperationResult(code, Map.of());
    }

    public static Builder builder(Code code) {
        return new Builder(code);
    }

    public boolean success() {
        return code.success;
    }

    public enum Code {
        SOLD(true, "general.sold"),
        LEARNED(true, "general.learned"),
        LEARNED_MANY(true, "general.learned-many"),
        BOUGHT(true, "general.bought"),
        PAID(true, "general.paid"),
        EMPTY_HAND(false, "errors.empty-hand"),
        UNSAFE_ITEM(false, "errors.unsafe-item"),
        NO_EMC_VALUE(false, "errors.no-emc-value"),
        NOTHING_TO_SELL(false, "errors.nothing-to-sell"),
        NOTHING_TO_LEARN(false, "errors.nothing-to-learn"),
        ALREADY_LEARNED(false, "errors.already-learned"),
        NOT_LEARNED(false, "errors.not-learned"),
        INSUFFICIENT_EMC(false, "errors.insufficient-emc"),
        INVENTORY_FULL(false, "errors.inventory-full"),
        CANNOT_PAY_SELF(false, "errors.cannot-pay-self"),
        TRANSFER_TOO_SMALL(false, "errors.transfer-too-small"),
        DATABASE_ERROR(false, "errors.database");

        private final boolean success;
        private final String messageKey;

        Code(boolean success, String messageKey) {
            this.success = success;
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public static final class Builder {
        private final Code code;
        private final Map<String, Object> values = new LinkedHashMap<>();

        private Builder(Code code) {
            this.code = code;
        }

        public Builder put(String key, Object value) {
            values.put(key, value);
            return this;
        }

        public OperationResult build() {
            return new OperationResult(code, Map.copyOf(values));
        }
    }
}
