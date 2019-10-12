package com.github.silaev.mongodb.replicaset.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a mutable container for properties coming from a yml file.
 *
 * @author Konstantin Silaev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyContainer {
    private MongoReplicaSetProperties mongoReplicaSetProperties;
}
