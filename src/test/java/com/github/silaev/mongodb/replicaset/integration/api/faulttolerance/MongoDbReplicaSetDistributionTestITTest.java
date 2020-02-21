package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.Pair;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import lombok.val;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Two data centers: two members to Data Center 1 and one member to Data Center 2.
 * If one of the members of the replica set is an arbiter, distribute the arbiter to Data Center 1 with a data-bearing member.
 *
 * @author Konstantin Silaev on 1/28/2020
 */
@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class MongoDbReplicaSetDistributionTestITTest {
    private static final int REPLICA_SET_NUMBER = 2;

    private final MongoDbReplicaSet mongoReplicaSet = MongoDbReplicaSet.builder()
        .replicaSetNumber(REPLICA_SET_NUMBER)
        .addArbiter(true)
        .build();

    @BeforeEach
    void setUp() {
        mongoReplicaSet.start();
    }

    @AfterEach
    void tearDown() {
        mongoReplicaSet.stop();
    }

    /**
     * If Data Center 1 goes down, the replica set becomes read-only.
     */
    @Test
    void shouldTestReadOnlySecondaryAfterPrimaryAndArbiterDisconnection() {
        //GIVEN
        val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
        val mongoRsUrlPrimaryPreferred = mongoReplicaSet.getReplicaSetUrl(
            ReadPreference.primaryPreferred().getName()
        );
        val mongoRsUrlSecondary = mongoReplicaSet.getReplicaSetUrl(
            ReadPreference.secondary().getName()
        );
        assertNotNull(mongoRsUrlPrimary);
        val mongoSyncClient = com.mongodb.client.MongoClients.create(mongoRsUrlPrimary);
        val docPair = Pair.of("abc", 5000);
        val doc = new Document(docPair.getLeft(), docPair.getRight());
        val dbName = "test";
        final String collectionName = "foo";
        val collection = mongoSyncClient.getDatabase(dbName).getCollection(collectionName);
        collection.withWriteConcern(WriteConcern.MAJORITY).insertOne(doc);
        val replicaSetMembers = mongoReplicaSet.getMongoRsStatus().getMembers();
        val masterNode = mongoReplicaSet.getMasterMongoNode(replicaSetMembers);
        val arbiterNode = mongoReplicaSet.getArbiterMongoNode(replicaSetMembers);
        val filter = Filters.eq(docPair.getLeft(), docPair.getRight());

        //WHEN
        mongoReplicaSet.disconnectNodeFromNetwork(arbiterNode);
        mongoReplicaSet.disconnectNodeFromNetwork(masterNode);
        mongoReplicaSet.waitForAllMongoNodesUp();
        final Executable executableReadOperation = () -> collection.find(filter).first();
        final Executable executableWriteOperation =
            () -> collection.insertOne(new Document("xyz", 100));

        //THEN
        //1. primary is the default mode. All operations read from the current replica set primary.
        assertThrows(MongoSocketReadException.class, executableReadOperation);

        //2. secondary is not writeable.
        assertThrows(MongoTimeoutException.class, executableWriteOperation);

        //3. in most situations, operations read from the primary but if it is unavailable,
        // operations read from secondary members.
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
        mongoReplicaSet.connectNodeToNetworkWithReconfiguration(masterNode);
        mongoReplicaSet.connectNodeToNetwork(arbiterNode);
        mongoReplicaSet.waitForAllMongoNodesUp();
        assertThat(
            mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers()),
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.ARBITER
            )
        );
    }

    /**
     * If Data Center 2 goes down, the replica set remains writeable as the members in Data Center 1 can hold an election.
     */
    @Test
    void shouldTestWriteablePrimaryAfterSecondaryDisconnection() {
        //GIVEN
        val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
        val mongoRsUrlSecondary = mongoReplicaSet.getReplicaSetUrl(
            ReadPreference.secondary().getName()
        );
        assertNotNull(mongoRsUrlPrimary);
        val mongoSyncClient = com.mongodb.client.MongoClients.create(mongoRsUrlPrimary);
        val docPair = Pair.of("abc", 5000);
        val doc = new Document(docPair.getLeft(), docPair.getRight());
        val dbName = "test";
        final String collectionName = "foo";
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
        mongoReplicaSet.waitForAllMongoNodesUp();
        collection.insertOne(newDoc);
        final Executable executableReadPreferenceOnCollection = () -> collection
            .withReadPreference(ReadPreference.secondary())
            .find(filterDoc)
            .first();
        final Executable executableReadPreferenceUrlConnection = () -> MongoClients.create(mongoRsUrlSecondary)
            .getDatabase(dbName).getCollection(collectionName)
            .find(filterDoc).first();

        //THEN
        //1. read operations are supported.
        assertEquals(doc, collection.find(filterDoc).first());
        //2. write operations are supported.
        assertEquals(newDoc, collection.find(filterNewDoc).first());

        //3. secondary is unavailable.
        assertThrows(MongoSocketOpenException.class, executableReadPreferenceOnCollection);
        assertThrows(MongoTimeoutException.class, executableReadPreferenceUrlConnection
        );

        //BRING A DISCONNECTED NODE BACK
        mongoReplicaSet.connectNodeToNetwork(secondaryNode);
        mongoReplicaSet.waitForAllMongoNodesUp();
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
