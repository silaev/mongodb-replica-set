package com.github.silaev.mongodb.replicaset.model;

/**
 * @author Konstantin Silaev on 3/10/2020
 */
public enum DisconnectionType {
    /**
     * Removing a network of a container.
     */
    HARD,
    /**
     * Cutting a connection between a mongo node and a proxy container (Toxiproxy).
     */
    SOFT
}
