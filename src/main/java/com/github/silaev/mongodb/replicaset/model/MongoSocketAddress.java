package com.github.silaev.mongodb.replicaset.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable class representing a socket address for a mongo node.
 *
 * @author Konstantin Silaev
 */
@EqualsAndHashCode(of = {"ip", "mappedPort" })
@Builder
@Getter
@ToString
public final class MongoSocketAddress {
    private final String ip;
    private final Integer replSetPort;
    private final Integer mappedPort;
}
