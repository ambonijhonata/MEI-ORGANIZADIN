package com.api.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

public class DockerAvailabilityCondition implements ExecutionCondition {
    private static final ConditionEvaluationResult ENABLED =
            ConditionEvaluationResult.enabled("Docker is available.");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                return ENABLED;
            }
        } catch (Throwable ignored) {
            // Fall through to the disabled result below.
        }

        return ConditionEvaluationResult.disabled("Docker is required for integration tests.");
    }
}
