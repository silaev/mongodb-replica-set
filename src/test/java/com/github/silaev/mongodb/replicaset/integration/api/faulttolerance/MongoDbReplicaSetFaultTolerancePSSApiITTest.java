package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.MongoNode;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fault tolerance tests for Primary with Two Secondary Members (P-S-S)
 * <p>
 * To use ExecutionMode.CONCURRENT in action,
 * run ./gradlew clean integrationTest -Djunit.jupiter.execution.parallel.enabled=true
 */
@Execution(ExecutionMode.CONCURRENT)
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

    @Test
    void shouldTestFailoverBecauseOfContainerStop() {
        //GIVEN
        val mongoNodes = mongoReplicaSet.getMongoRsStatus().getMembers();
        val nodeStatesBeforeFailover = getNodeStates(mongoNodes);
        val currentMasterNode = mongoReplicaSet.getMasterMongoNode(mongoNodes);

        //WHEN: STOP NODE
        mongoReplicaSet.stopNode(currentMasterNode);
        val actualNodeStatesRightAfterStopping =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN
        //1. Before failover state
        assertThat(
            nodeStatesBeforeFailover,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, nodeStatesBeforeFailover.size());

        //2. Right after failover state
        assertThat(
            actualNodeStatesRightAfterStopping,
            anyOf(
                //a repl set status might not be updated yet
                hasItems(
                    ReplicaSetMemberState.PRIMARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                ),
                hasItems(
                    ReplicaSetMemberState.DOWN,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                )
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesRightAfterStopping.size());

        //WHEN: WAIT FOR REELECTION
        mongoReplicaSet.waitForMasterReelection(currentMasterNode);
        mongoReplicaSet.removeNodeFromReplSet(currentMasterNode);
        val actualNodeStatesAfterElection =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN: AFTER ELECTION COMPLETES
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
        //GIVEN
        val mongoNodes = mongoReplicaSet.getMongoRsStatus().getMembers();
        val nodeStatesBeforeFailover = getNodeStates(mongoNodes);
        val currentMasterNode = mongoReplicaSet.getMasterMongoNode(mongoNodes);

        //WHEN: STOP NETWORK DISCONNECT
        mongoReplicaSet.disconnectNodeFromNetwork(currentMasterNode);
        val actualNodeStatesRightAfterDisconnection =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN
        //1. Before failover state
        assertThat(
            nodeStatesBeforeFailover,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, nodeStatesBeforeFailover.size());

        //2. Right after failover state
        assertThat(
            actualNodeStatesRightAfterDisconnection,
            anyOf(
                //a repl set status might not be updated yet
                hasItems(
                    ReplicaSetMemberState.PRIMARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                ),
                hasItems(
                    ReplicaSetMemberState.DOWN,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                )
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesRightAfterDisconnection.size());

        //WHEN: WAIT FOR REELECTION
        mongoReplicaSet.waitForMasterReelection(currentMasterNode);
        val actualNodeStatesAfterElection =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN: AFTER ELECTION COMPLETES
        assertThat(
            actualNodeStatesAfterElection,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.DOWN,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesAfterElection.size());

        //WHEN: CONNECT BACK
        mongoReplicaSet.connectNodeToNetwork(currentMasterNode);
        mongoReplicaSet.waitForAllMongoNodesUp();
        val actualNodeStatesAfterConnectingBack =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

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

    @Test
    void shouldTestFailoverBecauseOfContainerKill() {
        //GIVEN
        val mongoNodes = mongoReplicaSet.getMongoRsStatus().getMembers();
        val nodeStatesBeforeFailover = getNodeStates(mongoNodes);
        val currentMasterNode = mongoReplicaSet.getMasterMongoNode(mongoNodes);

        //WHEN: KILL NODE
        mongoReplicaSet.killNode(currentMasterNode);
        val actualNodeStatesRightAfterKilling =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN
        //1. before failover state
        assertThat(
            nodeStatesBeforeFailover,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER, nodeStatesBeforeFailover.size());

        //2. right after failover state
        assertThat(
            actualNodeStatesRightAfterKilling,
            anyOf(
                hasItems(
                    ReplicaSetMemberState.PRIMARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                ),
                hasItems(
                    ReplicaSetMemberState.DOWN,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                )
            )
        );
        assertEquals(REPLICA_SET_NUMBER, actualNodeStatesRightAfterKilling.size());

        //WHEN: WAIT FOR REELECTION
        mongoReplicaSet.waitForMasterReelection(currentMasterNode);
        mongoReplicaSet.removeNodeFromReplSet(currentMasterNode);
        val actualNodeStatesAfterElection =
            getNodeStates(mongoReplicaSet.getMongoRsStatus().getMembers());

        //THEN: AFTER ELECTION COMPLETES
        assertThat(
            actualNodeStatesAfterElection,
            hasItems(
                ReplicaSetMemberState.PRIMARY,
                ReplicaSetMemberState.SECONDARY
            )
        );
        assertEquals(REPLICA_SET_NUMBER - 1, actualNodeStatesAfterElection.size());
    }

    @NotNull
    private List<ReplicaSetMemberState> getNodeStates(List<MongoNode> mongoNodes) {
        return mongoNodes.stream()
            .map(MongoNode::getState)
            .collect(Collectors.toList());
    }
}
