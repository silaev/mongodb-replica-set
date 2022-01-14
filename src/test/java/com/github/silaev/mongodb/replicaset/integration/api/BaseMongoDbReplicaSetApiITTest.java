package com.github.silaev.mongodb.replicaset.integration.api;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.github.silaev.mongodb.replicaset.util.CollectionUtils;
import com.github.silaev.mongodb.replicaset.util.ConnectionUtils;
import com.github.silaev.mongodb.replicaset.util.StringUtils;
import com.github.silaev.mongodb.replicaset.util.SubscriberHelperUtils;
import com.mongodb.reactivestreams.client.MongoClients;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
abstract class BaseMongoDbReplicaSetApiITTest {
    @SneakyThrows
    void shouldTestRsStatus(
        final MongoDbReplicaSet replicaSet,
        final int replicaSetNumber
    ) {
        // GIVEN
        //replicaSet
        val mongoRsUrl = replicaSet.getReplicaSetUrl();
        val mongoRsStatus = replicaSet.getMongoRsStatus();
        assertNotNull(mongoRsUrl);
        assertNotNull(mongoRsStatus);

        try (
            val mongoReactiveClient = MongoClients.create(
                ConnectionUtils.getMongoClientSettingsWithTimeout(mongoRsUrl)
            )
        ) {
            val db = mongoReactiveClient.getDatabase("admin");

            // WHEN + THEN
            val subscriber = SubscriberHelperUtils.getSubscriber(
                db.runCommand(new Document("replSetGetStatus", 1))
            );
            val document = getDocument(subscriber.get(5, TimeUnit.SECONDS));
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

    void shouldTestEnabled(final MongoDbReplicaSet replicaSet) {
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
