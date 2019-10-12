package com.github.silaev.mongodb.replicaset.model;

import lombok.Value;

/**
 * Immutable class to keep a Mongo Db version.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
public final class MongoDbVersion {
    private final int major;
    private final int minor;
    private final int patch;
}