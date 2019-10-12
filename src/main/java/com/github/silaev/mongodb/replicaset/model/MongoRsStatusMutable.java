package com.github.silaev.mongodb.replicaset.model;

import lombok.Data;

import java.util.List;

/**
 * Mutable class to load data via an external library.
 * Describes a mongo cluster constructed by SnakeYml.
 *
 * @author Konstantin Silaev
 */
@Data
public class MongoRsStatusMutable {
    private Integer status;
    private String version;
    private List<MongoNodeMutable> members;
}
