package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.Pair;
import com.github.silaev.mongodb.replicaset.util.CollectionUtils;
import com.github.silaev.mongodb.replicaset.util.ConnectionUtils;
import com.github.silaev.mongodb.replicaset.util.SubscriberHelperUtils;
import com.mongodb.MongoTimeoutException;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Simulates a situation when 2 delayed members and one primary survives
 * primary disconnection and when are reconfigured to be a primary and a secondary.
 *
 * @author Konstantin Silaev on 5/8/2020
 */
@IntegrationTest
@Slf4j
class MongoDbDelayedMembersITTest {
    @Test
    void shouldTestDelayedMembersAfterBecomingPrimary() throws Throwable {
        //GIVEN
        try (
            final MongoDbReplicaSet mongoReplicaSet = MongoDbReplicaSet.builder()
                .replicaSetNumber(3)
                .addToxiproxy(true)
                .slaveDelayTimeout(50000)
                .build()
        ) {
            mongoReplicaSet.start();

            val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
            assertNotNull(mongoRsUrlPrimary);

            try (
                val mongoReactiveClient = MongoClients.create(
                    ConnectionUtils.getMongoClientSettingsWithTimeout(mongoRsUrlPrimary,
                        WriteConcern.W1.withJournal(true),
                        10
                    )
                )
            ) {

                val dbName = "test";
                val collectionName = "foo";
                val masterNode = mongoReplicaSet.getMasterMongoNode(
                    mongoReplicaSet.getMongoRsStatus().getMembers()
                );

                val docPair1 = Pair.of("xyz", 100);
                val doc1 = new Document(docPair1.getLeft(), docPair1.getRight());
                val filterDoc1 = Filters.eq(docPair1.getLeft(), docPair1.getRight());

                //WHEN
                val insertSub1 = new SubscriberHelperUtils.OperationSubscriber<>();
                val collection = CollectionUtils.getCollection(mongoReactiveClient, dbName, collectionName);
                collection.insertOne(doc1).subscribe(insertSub1);//non-blocking(unsure of onComplete) call
                insertSub1.await(5, TimeUnit.SECONDS);//wait for onComplete
                val receivedBeforeDisconnection = SubscriberHelperUtils.getSubscriber(collection.find(filterDoc1).first())
                    .get(5, TimeUnit.SECONDS);
                final Document doc1ActualBeforeDisconnection = receivedBeforeDisconnection.get(0);

                mongoReplicaSet.disconnectNodeFromNetwork(masterNode);
                mongoReplicaSet.reconfigureReplSetToDefaults();
                mongoReplicaSet.waitForMasterReelection(masterNode);
                final Executable executableDoc1AfterDelayedPrimary =
                    () -> SubscriberHelperUtils.getSubscriber(collection.find(filterDoc1).first())
                        .get(10, TimeUnit.SECONDS);

                //getting a new connection to perform operations
                try (
                    val mongoReactiveClientNew = MongoClients.create(
                        ConnectionUtils.getMongoClientSettingsWithTimeout(mongoReplicaSet.getReplicaSetUrl(),
                            WriteConcern.W1.withJournal(true),
                            10
                        )
                    )
                ) {
                    val collectionNew = CollectionUtils.getCollection(mongoReactiveClientNew, dbName, collectionName);
                    log.debug("Wait for docsAfterDelayedPrimaryNewConnection");
                    val docsAfterDelayedPrimaryNewConnection = SubscriberHelperUtils.getSubscriber(
                        collectionNew.find(filterDoc1).first()
                    ).get(10, TimeUnit.SECONDS);

                    //THEN
                    log.debug("assertThrows executableDoc1AfterDelayedPrimary");
                    val mongoTimeoutException = assertThrows(MongoTimeoutException.class, executableDoc1AfterDelayedPrimary);
                    assertThat(mongoTimeoutException.getMessage(), containsString("type=UNKNOWN, state=CONNECTING"));
                    assertEquals(doc1, doc1ActualBeforeDisconnection);
                    assertNotNull(docsAfterDelayedPrimaryNewConnection);
                    if (docsAfterDelayedPrimaryNewConnection.isEmpty()) {
                        log.debug("Write has not been propagated to delayed members");
                    } else {
                        assertEquals(doc1, docsAfterDelayedPrimaryNewConnection.get(0));
                    }
                }
            }
        }
    }
}
