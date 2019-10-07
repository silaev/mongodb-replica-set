package com.silaev.mongodb.replicaset.core;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.Preconditions;

import java.util.Objects;
import java.util.Optional;

import static java.lang.String.format;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

/**
 * Taken and slightly modified from {@link org.junit.jupiter.api.condition.EnabledIfSystemProperty}
 * <p>
 * {@link ExecutionCondition} for {@link EnabledIfSystemPropertyExistsAndMatches @EnabledIfSystemPropertyExistsAndMatches}.
 *
 * @see EnabledIfSystemProperty
 */
class EnabledIfSystemPropertyExistsAndMatchesCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED_BY_DEFAULT = enabled(
        "@EnabledIfSystemProperty is not present");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<EnabledIfSystemPropertyExistsAndMatches> optional = findAnnotation(context.getElement(),
            EnabledIfSystemPropertyExistsAndMatches.class);

        if (!optional.isPresent()) {
            return ENABLED_BY_DEFAULT;
        }

        EnabledIfSystemPropertyExistsAndMatches annotation = optional.get();
        String name = annotation.named().trim();
        String regex = annotation.matches();
        Preconditions.notBlank(name, () -> "The 'named' attribute must not be blank in " + annotation);
        Preconditions.notBlank(regex, () -> "The 'matches' attribute must not be blank in " + annotation);
        String actual = System.getProperty(name);

        // Nothing to match against?
        if (Objects.isNull(actual)) {
            return enabled(format("System property [%s] is enabled by default", name));
        }
        if (actual.matches(regex)) {
            return enabled(
                format("System property [%s] with value [%s] matches regular expression [%s]", name, actual, regex));
        }
        return disabled(
            format("System property [%s] with value [%s] does not match regular expression [%s]", name, actual, regex));
    }

}
