package com.luziatcode.demoworkflowengine.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleConditionEvaluatorTest {

    private final SimpleConditionEvaluator evaluator = new SimpleConditionEvaluator();

    @Test
    void matchesReturnsTrueForBlankExpression() {
        assertTrue(evaluator.matches("  ", Map.of("x", 1)));
    }

    @Test
    void matchesSupportsNumericComparisons() {
        Map<String, Object> context = Map.of("x", 10, "y", "3");

        assertTrue(evaluator.matches("x >= 10", context));
        assertTrue(evaluator.matches("x > 5", context));
        assertTrue(evaluator.matches("y <= 3", context));
        assertFalse(evaluator.matches("x < 10", context));
    }

    @Test
    void matchesSupportsEqualityChecks() {
        assertTrue(evaluator.matches("status == APPROVED", Map.of("status", "APPROVED")));
        assertFalse(evaluator.matches("status == REJECTED", Map.of("status", "APPROVED")));
    }

    @Test
    void matchesTreatsMissingNumericValueAsZero() {
        assertTrue(evaluator.matches("missing <= 0", Map.of()));
        assertFalse(evaluator.matches("missing > 0", Map.of()));
    }

    @Test
    void matchesRejectsUnsupportedExpressions() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.matches("x != 1", Map.of("x", 1))
        );

        assertTrue(exception.getMessage().contains("Unsupported condition expression"));
    }
}
