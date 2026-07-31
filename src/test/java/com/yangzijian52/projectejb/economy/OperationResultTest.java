package com.yangzijian52.projectejb.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OperationResultTest {
    @Test
    void builderCreatesImmutableSuccessResult() {
        OperationResult result = OperationResult.builder(OperationResult.Code.BOUGHT)
                .put("amount", 64)
                .put("emc", "8,192")
                .build();

        assertTrue(result.success());
        assertEquals(64, result.values().get("amount"));
        assertEquals("general.bought", result.code().messageKey());
    }

    @Test
    void failureCodesAreReportedAsFailures() {
        assertFalse(OperationResult.of(OperationResult.Code.INVENTORY_FULL).success());
        assertFalse(OperationResult.of(OperationResult.Code.DATABASE_ERROR).success());
    }
}
