package com.silaev.mongodb.replicaset.model;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Immutable class representing a socket address for a mongo node.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(of = {"ip", "replSetPort", "mappedPort"})
public final class MongoSocketAddress {
    private final String ip;
    private final int replSetPort;
    private final int mappedPort;
}
