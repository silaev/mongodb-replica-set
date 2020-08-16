package com.github.silaev.mongodb.replicaset.model;

import lombok.Value;

/**
 * Immutable class to keep a Mongo Db version.
 *
 * @author Konstantin Silaev
 */
@Value(staticConstructor = "of")
public class MongoDbVersion {
    int major;
    int minor;
    int patch;
}
