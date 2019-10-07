package com.silaev.mongodb.replicaset.model;

import lombok.Value;

import java.util.List;

/**
 * Immutable class to load data via SnakeYml.
 * Describing a mongo cluster to use in public API.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
public class MongoRsStatus {
    private final Integer status;
    private final MongoDbVersion version;
    private final List<MongoNode> members;
}
