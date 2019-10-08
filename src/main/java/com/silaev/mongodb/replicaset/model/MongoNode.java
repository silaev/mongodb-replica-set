package com.silaev.mongodb.replicaset.model;

import lombok.Value;

/**
 * Immutable class to load data via SnakeYml.
 * Describes a mongo node to use in public API.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
public final class MongoNode {
    private final String ip;
    private final Integer port;
    private final Double health;
    private final ReplicaSetMemberState state;
}
