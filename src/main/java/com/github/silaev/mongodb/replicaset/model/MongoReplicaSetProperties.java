package com.github.silaev.mongodb.replicaset.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable class to load data via an external library.
 * Describes a user defined properties constructed by SnakeYml.
 *
 * @author Konstantin Silaev
 */
@Data
@NoArgsConstructor
public class MongoReplicaSetProperties {
    private Boolean enabled;
    private String mongoDockerImageName;
}
