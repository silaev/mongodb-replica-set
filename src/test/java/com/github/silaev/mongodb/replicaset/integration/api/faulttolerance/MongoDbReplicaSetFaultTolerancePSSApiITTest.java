package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.HardFailureAction;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fault tolerance tests for Primary with Two Secondary Members (P-S-S)
 */
@IntegrationTest
@Slf4j
class MongoDbReplicaSetFaultTolerancePSSApiITTest {
    private static final int REPLICA_SET_NUMBER = 3;

    private final MongoDbReplicaSet mongoReplicaSet = MongoDbReplicaSet.builder()
        .replicaSetNumber(REPLICA_SET_NUMBER)
        .build();

    @BeforeEach
    void setUp() {
        mongoReplicaSet.start();
    }

    @AfterEach
    void tearDown() {
        mongoReplicaSet.stop();
    }

    @ParameterizedTest(name = "{index}: action: {0}")
    @ValueSource(strings = {"KILL", "STOP"})
    void shouldTestFailoverBecauseOfContainerIsKilledOrStopped(
        final HardFailureAction action
    ) {
        //===STAGE 1: killing or stopping a master node.
        //GIVEN
        val mongoNodes = mongoReplicaSet.getMongoRsStatus().getMembers();
        val nodeStatesBeforeFailover = mongoReplicaSet.nodeStates(mongoNodes);
        val currentMasterNode = mongoReplicaSet.getMasterMongoNode(mongoNodes);

        //WHEN: Kill or stop the master node.
        switch (action) {
            case KILL:
                mongoReplicaSet.killNode(currentMasterNode);
                break;
            case STOP:
                mongoReplicaSet.stopNode(currentMasterNode);
                break;
            default:
                throw new IllegalArgumentException(String.format("Cannot find action: %s", action));
        }

        //THEN
        //1. Check the state of members before a failover.
        assertThat(
            nodeStatesBeforeFailover,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, nodeStatesBeforeFailover.size());

        //===STAGE 2: Surviving a failure.
        //WHEN: Wait for reelection.
        mongoReplicaSet.waitForMasterReelection(currentMasterNode);
        mongoReplicaSet.removeNodeFromReplSetConfigWithForce(currentMasterNode);
        val actualNodeStatesAfterElection =
            mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN: Check the state of members when election is over.
        assertThat(
            actualNodeStatesAfterElection,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER - 1, actualNodeStatesAfterElection.size());
    }

    @Test
    void shouldTestFailoverBecauseOfContainerNetworkDisconnect() {
        //===STAGE 1: Disconnecting a master node from its network.
        //GIVEN
        val mongoNodes = mongoReplicaSet.getMongoRsStatus().getMembers();
        val nodeStatesBeforeFailover = mongoReplicaSet.nodeStates(mongoNodes);
        val currentMasterNode = mongoReplicaSet.getMasterMongoNode(mongoNodes);

        //WHEN: Disconnect a master node from its network.
        mongoReplicaSet.disconnectNodeFromNetwork(currentMasterNode);

        //THEN
        //1. Check the state of members before the failure.
        assertThat(
            nodeStatesBeforeFailover,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, nodeStatesBeforeFailover.size());

        //===STAGE 2: Surviving a failure.
        //WHEN: Wait for reelection.
        mongoReplicaSet.waitForMasterReelection(currentMasterNode);
        val actualNodeStatesAfterElection =
            mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN: Check the state of members when election is over.
        assertThat(
            actualNodeStatesAfterElection,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.DOWN,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesAfterElection.size());

        //===STAGE 3: Connecting a disconnected node back.
        //WHEN: Connect back.
        mongoReplicaSet.connectNodeToNetwork(currentMasterNode);
        mongoReplicaSet.waitForAllMongoNodesUp();
        val actualNodeStatesAfterConnectingBack =
            mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN
        assertThat(
            actualNodeStatesAfterConnectingBack,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesAfterConnectingBack.size());
    }
}
