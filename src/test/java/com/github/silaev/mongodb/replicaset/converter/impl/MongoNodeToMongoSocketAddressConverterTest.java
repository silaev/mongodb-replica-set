package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import lombok.val;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Konstantin Silaev on 2/25/2020
 */
class MongoNodeToMongoSocketAddressConverterTest {
    private final MongoNodeToMongoSocketAddressConverter converter =
        new MongoNodeToMongoSocketAddressConverter();

    @Test
    void shouldConvert() {
        //GIVEN
        final String ip = "ip";
        final int port = 27017;
        val mongoNode = MongoNode.of(ip, port, 1d, ReplicaSetMemberState.PRIMARY);

        //WHEN
        val socketAddress = converter.convert(mongoNode);

        //THEN
        assertNotNull(socketAddress);
        assertEquals(ip, socketAddress.getIp());
        assertEquals(port, socketAddress.getMappedPort());
        assertNull(socketAddress.getReplSetPort());
    }

    @Test
    void shouldConvertNullToNull() {
        //GIVEN
        final MongoNode mongoNode = null;

        //WHEN
        val socketAddress = converter.convert(mongoNode);

        //THEN
        assertNull(socketAddress);
    }
}
