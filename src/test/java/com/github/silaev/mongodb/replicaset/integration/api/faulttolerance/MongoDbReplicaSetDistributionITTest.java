package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.DisconnectionType;
import com.github.silaev.mongodb.replicaset.model.Pair;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.github.silaev.mongodb.replicaset.util.ConnectionUtils;
import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import lombok.val;
import org.bson.Document;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Simulates two data centers scenario in which two members to Data Center 1 and one member to Data Center 2.
 * If one of the members of the replica set is an arbiter, distribute the arbiter to Data Center 1 with a data-bearing member.
 *
 * @author Konstantin Silaev on 1/28/2020
 */
@IntegrationTest
class MongoDbReplicaSetDistributionITTest {
    /**
     * If Data Center 1 goes down, the replica set becomes read-only.
     * Uses hard node disconnection via whether removing a network of a container
     * or cutting a connection between a mongo node and a proxy container (Toxiproxy).
     */
    @ParameterizedTest(name = "shouldTestReadOnlySecondaryAfterPrimaryAndArbiterHardOrSoftDisconnection: {index}: disconnectionType: {0}")
    @ValueSource(strings = {"HARD", "SOFT"})
    void shouldTestReadOnlySecondaryAfterPrimaryAndArbiterHardOrSoftDisconnection(
        final DisconnectionType disconnectionType
    ) {
        //GIVEN
        val mongoDbReplicaSetBuilder = MongoDbReplicaSet.builder()
            .replicaSetNumber(2)
            .addArbiter(true);
        if (disconnectionType == DisconnectionType.SOFT) {
            mongoDbReplicaSetBuilder.addToxiproxy(true);
        }

        try (
            final MongoDbReplicaSet mongoReplicaSet = mongoDbReplicaSetBuilder.build()
        ) {
            mongoReplicaSet.start();
            val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
            val mongoRsUrlPrimaryPreferred = mongoReplicaSet.getReplicaSetUrl(
                ReadPreference.primaryPreferred().getName()
            );
            val mongoRsUrlSecondary = mongoReplicaSet.getReplicaSetUrl(
                ReadPreference.secondary().getName()
            );
            assertNotNull(mongoRsUrlPrimary);
            try (
                val mongoSyncClient = com.mongodb.client.MongoClients.create(
                    ConnectionUtils.getMongoClientSettingsWithTimeout(mongoRsUrlPrimary)
                )
            ) {
                val docPair = Pair.of("abc", 5000);
                val doc = new Document(docPair.getLeft(), docPair.getRight());
                val dbName = "test";
                val collectionName = "foo";
                val collection = mongoSyncClient.getDatabase(dbName).getCollection(collectionName);
                collection.withWriteConcern(WriteConcern.MAJORITY).insertOne(doc);
                val replicaSetMembers = mongoReplicaSet.getMongoRsStatus().getMembers();
                val masterNode = mongoReplicaSet.getMasterMongoNode(replicaSetMembers);
                val arbiterNode = mongoReplicaSet.getArbiterMongoNode(replicaSetMembers);
                val filter = Filters.eq(docPair.getLeft(), docPair.getRight());

                //WHEN
                mongoReplicaSet.disconnectNodeFromNetwork(arbiterNode);
                mongoReplicaSet.disconnectNodeFromNetwork(masterNode);

                mongoReplicaSet.waitForMongoNodesDown(2);

                final Executable executableReadOperation = () -> collection.find(filter).first();
                final Executable executableWriteOperation =
                    () -> collection.insertOne(new Document("xyz", 100));

                //THEN
                //1. Primary is the default mode. All operations read from the current replica set primary.
                //Therefore, reads are not possible (MongoSocketReadException/MongoSocketReadTimeoutException/MongoTimeoutException).
                assertThrows(MongoException.class, executableReadOperation);

                //2. Secondary is not writeable.
                assertThrows(MongoTimeoutException.class, executableWriteOperation);

                //3. Test read preference "primary preferred".
                //In most situations, operations read from the primary but if it is unavailable,
                //operations read from secondary members.
                //3.1. withReadPreference on a collection.
                assertEquals(doc, collection
                    .withReadPreference(ReadPreference.primaryPreferred())
                    .find(filter)
                    .first()
                );
                assertEquals(doc, collection
                    .withReadPreference(ReadPreference.secondary())
                    .find(filter)
                    .first()
                );

                //3.2. withReadPreference on a url connection.
                assertEquals(doc, MongoClients.create(mongoRsUrlPrimaryPreferred)
                    .getDatabase(dbName).getCollection(collectionName)
                    .find(filter).first()
                );
                assertEquals(doc, MongoClients.create(mongoRsUrlSecondary)
                    .getDatabase(dbName).getCollection(collectionName)
                    .find(filter).first()
                );

                //BRING ALL DISCONNECTED NODES BACK
                switch (disconnectionType) {
                    case HARD:
                        mongoReplicaSet.connectNodeToNetworkWithReconfiguration(masterNode);
                        mongoReplicaSet.connectNodeToNetworkWithoutRemoval(arbiterNode);
                        break;
                    case SOFT:
                        mongoReplicaSet.connectNodeToNetwork(masterNode);
                        mongoReplicaSet.connectNodeToNetwork(arbiterNode);
                        break;
                    default:
                        throw new IllegalArgumentException(String.format("Cannot find disconnectionType: %s", disconnectionType));
                }
                mongoReplicaSet.waitForAllMongoNodesUp();
                mongoReplicaSet.waitForMaster();
                assertThat(
                    mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers()),
                    hasItems(
                        ReplicaSetMemberState.PRIMARY,
                        ReplicaSetMemberState.SECONDARY,
                        ReplicaSetMemberState.ARBITER
                    )
                );
            }
        }
    }

    /**
     * If Data Center 2 goes down, the replica set remains writeable as the members
     * in Data Center 1 can hold an election.
     * <p>
     * Uses whether hard node disconnection via removing a network of a container
     * or soft one via cutting a connection between a mongo node and a proxy container (Toxiproxy).
     */
    @ParameterizedTest(name = "shouldTestWriteablePrimaryAfterSecondaryHardOrSoftDisconnection: {index}: disconnectionType: {0}")
    @ValueSource(strings = {"HARD", "SOFT"})
    void shouldTestWriteablePrimaryAfterSecondaryHardOrSoftDisconnection(
        final DisconnectionType disconnectionType
    ) {
        //GIVEN
        val mongoDbReplicaSetBuilder = MongoDbReplicaSet.builder()
            .replicaSetNumber(2)
            .addArbiter(true);
        if (disconnectionType == DisconnectionType.SOFT) {
            mongoDbReplicaSetBuilder.addToxiproxy(true);
        }
        try (
            final MongoDbReplicaSet mongoReplicaSet = mongoDbReplicaSetBuilder.build()
        ) {
            mongoReplicaSet.start();
            val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
            val mongoRsUrlSecondary = mongoReplicaSet.getReplicaSetUrl(
                ReadPreference.secondary().getName()
            );
            assertNotNull(mongoRsUrlPrimary);

            try (
                val mongoSyncClient = com.mongodb.client.MongoClients.create(
                    ConnectionUtils.getMongoClientSettingsWithTimeout(mongoRsUrlPrimary)
                )
            ) {
                val docPair = Pair.of("abc", 5000);
                val doc = new Document(docPair.getLeft(), docPair.getRight());
                val dbName = "test";
                val collectionName = "foo";
                val collection = mongoSyncClient.getDatabase(dbName).getCollection(collectionName);
                collection.withWriteConcern(WriteConcern.MAJORITY).insertOne(doc);
                val replicaSetMembers = mongoReplicaSet.getMongoRsStatus().getMembers();
                val secondaryNode = mongoReplicaSet.getSecondaryMongoNode(replicaSetMembers);
                val filterDoc = Filters.eq(docPair.getLeft(), docPair.getRight());
                val newDocPair = Pair.of("xyz", 100);
                val newDoc = new Document(newDocPair.getLeft(), newDocPair.getRight());
                val filterNewDoc = Filters.eq(newDocPair.getLeft(), newDocPair.getRight());

                //WHEN
                mongoReplicaSet.disconnectNodeFromNetwork(secondaryNode);

                mongoReplicaSet.waitForMongoNodesDown(1);
                //mongoReplicaSet.waitForAllMongoNodesUpAllowingAnyDown();

                collection.insertOne(newDoc);
                final Executable executableReadPreferenceOnCollection = () -> collection
                    .withReadPreference(ReadPreference.secondary())
                    .find(filterDoc)
                    .maxAwaitTime(5, TimeUnit.SECONDS)
                    .maxTime(5, TimeUnit.SECONDS)
                    .first();
                final Executable executableReadPreferenceUrlConnection = () ->
                    MongoClients.create(
                        ConnectionUtils.getMongoClientSettingsWithTimeout(mongoRsUrlSecondary)
                    ).getDatabase(dbName).getCollection(collectionName).find(filterDoc).first();

                //THEN
                //1. read operations are supported.
                assertEquals(doc, collection.find(filterDoc).first());
                //2. write operations are supported.
                assertEquals(newDoc, collection.find(filterNewDoc).first());

                //3. secondary is unavailable.
                assertThrows(MongoException.class, executableReadPreferenceOnCollection);
                assertThrows(MongoTimeoutException.class, executableReadPreferenceUrlConnection);

                //BRING A DISCONNECTED NODE BACK
                mongoReplicaSet.connectNodeToNetwork(secondaryNode);
                mongoReplicaSet.waitForAllMongoNodesUp();
                mongoReplicaSet.waitForMaster();
                assertThat(
                    mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers()),
                    hasItems(
                        ReplicaSetMemberState.PRIMARY,
                        ReplicaSetMemberState.SECONDARY,
                        ReplicaSetMemberState.ARBITER
                    )
                );
            }
        }
    }
}
