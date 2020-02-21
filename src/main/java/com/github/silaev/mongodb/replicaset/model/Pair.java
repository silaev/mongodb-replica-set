package com.github.silaev.mongodb.replicaset.model;

import lombok.Value;

/**
 * An immutable class containing 2 values.
 * Used in order to avoid a log AbstractMap.SimpleImmutableEntry or 3rd party libraries.
 *
 * @author Konstantin Silaev on 1/29/2020
 */
@Value(staticConstructor = "of")
public class Pair<L, R> {
    private final L left;
    private final R right;
}
