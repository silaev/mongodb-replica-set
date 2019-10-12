package com.github.silaev.mongodb.replicaset.core;

import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Taken and slightly modified from {@link EnabledIfSystemProperty}
 * <p>
 * As opposed to {@code @EnabledIfSystemProperty} if the specified system property is undefined,
 * the annotated class or method will be enabled.
 * Similar might be achieved by DisableIfSystemProperty with negation of a regexp.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(EnabledIfSystemPropertyExistsAndMatchesCondition.class)
public @interface EnabledIfSystemPropertyExistsAndMatches {

    /**
     * The name of the JVM system property to retrieve.
     *
     * @return the system property name; never <em>blank</em>
     * @see System#getProperty(String)
     */
    String named();

    /**
     * A regular expression that will be used to match against the retrieved
     * value of the {@link #named} JVM system property.
     *
     * @return the regular expression; never <em>blank</em>
     * @see String#matches(String)
     * @see java.util.regex.Pattern
     */
    String matches();

}
