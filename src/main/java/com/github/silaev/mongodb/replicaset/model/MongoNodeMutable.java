package com.github.silaev.mongodb.replicaset.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable class to load data via an external library.
 * Describes a mongo node constructed by SnakeYml.
 *
 * @author Konstantin Silaev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MongoNodeMutable {
    private String name;
    private Double health;
    private Integer state;
    private String stateStr;
}
