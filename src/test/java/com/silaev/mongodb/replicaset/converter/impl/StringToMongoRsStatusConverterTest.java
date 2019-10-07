package com.silaev.mongodb.replicaset.converter.impl;

import com.silaev.mongodb.replicaset.model.MongoNode;
import com.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.silaev.mongodb.replicaset.service.ResourceService;
import com.silaev.mongodb.replicaset.service.impl.ResourceServiceImpl;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

class StringToMongoRsStatusConverterTest {
    private StringToMongoRsStatusConverter converter = new StringToMongoRsStatusConverter(
        new YmlConverterImpl(),
        new VersionConverter()
    );
    private ResourceService resourceService = new ResourceServiceImpl();

    @Test
    void shouldConvert() {
        // GIVEN
        val rsStatus = getString(
            resourceService.getResourceIO("rs-status.txt")
        );

        // THEN
        val mongoRsStatusActual = converter.convert(rsStatus);

        // WHEN
        assertNotNull(mongoRsStatusActual);
        assertEquals(Integer.valueOf(1), mongoRsStatusActual.getStatus());
        val members = mongoRsStatusActual.getMembers();
        assertEquals(5, members.size());
        val membersIndex = members.stream()
            .collect(Collectors.groupingBy(MongoNode::getState, Collectors.counting()));
        assertEquals(Long.valueOf(1), membersIndex.get(ReplicaSetMemberState.PRIMARY));
        assertEquals(Long.valueOf(3), membersIndex.get(ReplicaSetMemberState.SECONDARY));
        assertEquals(Long.valueOf(1), membersIndex.get(ReplicaSetMemberState.ARBITER));
    }

    @SneakyThrows
    private String getString(InputStream io) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = io.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
