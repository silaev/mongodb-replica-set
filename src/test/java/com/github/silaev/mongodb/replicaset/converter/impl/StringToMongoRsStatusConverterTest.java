package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.github.silaev.mongodb.replicaset.service.ResourceService;
import com.github.silaev.mongodb.replicaset.service.impl.ResourceServiceImpl;
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
    private final ResourceService resourceService = new ResourceServiceImpl();

    @Test
    void shouldConvert() {
        // GIVEN
        val rsStatus = resourceService.getString(
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
}
