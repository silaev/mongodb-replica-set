package com.silaev.mongodb.replicaset.integration.api;

import com.mongodb.reactivestreams.client.MongoClients;
import com.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.silaev.mongodb.replicaset.converter.impl.VersionConverter;
import com.silaev.mongodb.replicaset.model.MongoNode;
import com.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.silaev.mongodb.replicaset.util.CollectionUtils;
import com.silaev.mongodb.replicaset.util.StringUtils;
import com.silaev.mongodb.replicaset.util.SubscriberHelperUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
abstract class BaseMongoDbReplicaSetApiITTest {
    void shouldTestRsStatus(
        final MongoDbReplicaSet replicaSet,
        final int replicaSetNumber
    ) {
        log.debug("currentThread: {}", Thread.currentThread().getName());

        // GIVEN
        //replicaSet
        val mongoRsUrl = replicaSet.getReplicaSetUrl();
        val mongoRsStatus = replicaSet.getMongoRsStatus();
        assertNotNull(mongoRsUrl);
        assertNotNull(mongoRsStatus);

        val mongoReactiveClient = MongoClients.create(mongoRsUrl);
        val db = mongoReactiveClient.getDatabase("admin");

        // WHEN + THEN
        try {
            val subscriber = getSubscriber(
                db.runCommand(new Document("replSetGetStatus", 1))
            );
            val document = getDocument(subscriber.getReceived());
            assertEquals(Double.valueOf("1"), document.get("ok", Double.class));
            val mongoNodesActual = extractMongoNodes(document.getList("members", Document.class));

            assertTrue(
                CollectionUtils.isEqualCollection(
                    mongoRsStatus.getMembers(),
                    mongoNodesActual
                )
            );
            assertEquals(
                replicaSetNumber + (replicaSet.getAddArbiter() ? 1 : 0),
                mongoNodesActual.size());
        } finally {
            mongoReactiveClient.close();
        }
    }

    private List<MongoNode> extractMongoNodes(final List<Document> members) {
        return members.stream()
            .map(this::getMongoNode)
            .collect(Collectors.toList());
    }

    private MongoNode getMongoNode(final Document document) {
        val addresses =
            StringUtils.getArrayByDelimiter(document.getString("name"));
        return MongoNode.of(
            addresses[0],
            Integer.parseInt(addresses[1]),
            document.getDouble("health"),
            ReplicaSetMemberState.getByValue(document.getInteger("state"))
        );
    }

    @NotNull
    private Document getDocument(final List<Document> documents) {
        return documents.get(0);
    }

    void shouldTestVersionAndDockerImageName(final MongoDbReplicaSet replicaSet) {
        log.debug("currentThread: {}", Thread.currentThread().getName());

        // GIVEN
        val mongoRsUrl = replicaSet.getReplicaSetUrl();
        val mongoRsStatus = replicaSet.getMongoRsStatus();
        assertNotNull(mongoRsUrl);
        assertNotNull(mongoRsStatus);
        val dockerImageName = replicaSet.mongoDockerImageName();
        assertNotNull(dockerImageName);
        val mongoClient = MongoClients.create(mongoRsUrl);
        val db = mongoClient.getDatabase("test");

        // WHEN + THEN
        try {
            val subscriber = getSubscriber(
                db.runCommand(new Document("buildInfo", 1))
            );
            val version = getDocument(subscriber.getReceived()).getString("version");
            val versionExpected =
                dockerImageName.substring(dockerImageName.indexOf(":") + 1);
            assertEquals(
                versionExpected,
                version
            );
            assertEquals(
                new VersionConverter().convert(versionExpected),
                mongoRsStatus.getVersion()
            );
        } finally {
            mongoClient.close();
        }
    }

    @SneakyThrows
    private SubscriberHelperUtils.PrintDocumentSubscriber getSubscriber(
        final Publisher<Document> command
    ) {
        val subscriber = new SubscriberHelperUtils.PrintDocumentSubscriber();
        command.subscribe(subscriber);
        subscriber.await();
        return subscriber;
    }

    void shouldTestEnabled(final MongoDbReplicaSet replicaSet) {
        log.debug("currentThread: {}", Thread.currentThread().getName());

        // GIVEN
        val mongoRsUrl = replicaSet.getReplicaSetUrl();
        val mongoRsStatus = replicaSet.getMongoRsStatus();

        // WHEN
        // replicaSet is initialized

        // THEN
        assertNotNull(mongoRsUrl);
        assertNotNull(mongoRsStatus);
        assertTrue(replicaSet.isEnabled());
    }
}
