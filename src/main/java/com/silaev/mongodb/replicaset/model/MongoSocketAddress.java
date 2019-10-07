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
public class MongoSocketAddress {
    final String ip;
    final int replSetPort;
    final int mappedPort;
}
