package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.converter.Converter;
import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.MongoSocketAddress;

/**
 * Converts MongoNode to MongoSocketAddress without a replication port
 * so that to search in hash maps.
 *
 * @author Konstantin Silaev on 2/24/2020
 */
public class MongoNodeToMongoSocketAddressConverter
    implements Converter<MongoNode, MongoSocketAddress> {
    @Override
    public MongoSocketAddress convert(MongoNode mongoNode) {
        if (mongoNode == null) {
            return null;
        }
        return MongoSocketAddress.builder()
            .ip(mongoNode.getIp())
            .mappedPort(mongoNode.getPort())
            .build();
    }
}
