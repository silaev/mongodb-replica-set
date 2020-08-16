package com.github.silaev.mongodb.replicaset.model;

import lombok.Value;

/**
 * Immutable class to load data via SnakeYml.
 * Describes a mongo node to use in public API.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
public class MongoNode {
    String ip;
    Integer port;
    Double health;
    ReplicaSetMemberState state;
}
