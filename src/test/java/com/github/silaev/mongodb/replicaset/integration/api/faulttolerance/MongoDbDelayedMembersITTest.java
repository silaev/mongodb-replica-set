package com.github.silaev.mongodb.replicaset.integration.api.faulttolerance;

import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import com.github.silaev.mongodb.replicaset.core.EnabledIfSystemPropertyEnabledByDefault;
import com.github.silaev.mongodb.replicaset.core.IntegrationTest;
import com.github.silaev.mongodb.replicaset.model.ReplicaSetMemberState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Simulates a situation when a replica set with a delayed member
 * is reconfigured to a non-delayed one with or without losing primary first.
 *
 * @author Konstantin Silaev on 5/8/2020
 */
@IntegrationTest
@Slf4j
@EnabledIfSystemPropertyEnabledByDefault(
    named = "mongoReplicaSetProperties.mongoDockerImageName",
    matches = "^mongo:4.*"
)
class MongoDbDelayedMembersITTest {
    @Test
    void shouldTestDelayedMembersBecomingSecondary() {
        try (
            final MongoDbReplicaSet mongoReplicaSet = MongoDbReplicaSet.builder()
                .replicaSetNumber(4)
                .slaveDelayTimeout(50000)
                .slaveDelayNumber(1)
                .build()
        ) {
            mongoReplicaSet.start();

            val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
            assertNotNull(mongoRsUrlPrimary);
            mongoReplicaSet.reconfigureReplSetToDefaults();

            assertThat(
                mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers()),
                hasItems(
                    ReplicaSetMemberState.PRIMARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY
                )
            );
        }
    }

    /**
     * Disabled as unstable
     */
    @Test
    @Disabled
    void shouldTestDelayedMemberMightBecomeSecondary() {
        try (
            final MongoDbReplicaSet mongoReplicaSet = MongoDbReplicaSet.builder()
                .replicaSetNumber(4)
                .addToxiproxy(true)
                .slaveDelayTimeout(50000)
                .slaveDelayNumber(1)
                .build()
        ) {
            mongoReplicaSet.start();

            val mongoRsUrlPrimary = mongoReplicaSet.getReplicaSetUrl();
            assertNotNull(mongoRsUrlPrimary);
            val members = mongoReplicaSet.getMongoRsStatus().getMembers();
            val masterNode = mongoReplicaSet.getMasterMongoNode(members);

            mongoReplicaSet.disconnectNodeFromNetwork(masterNode);
            mongoReplicaSet.waitForMongoNodesDown(1);
            mongoReplicaSet.waitForMasterReelection(masterNode);
            mongoReplicaSet.reconfigureReplSetToDefaults();

            assertThat(
                mongoReplicaSet.nodeStates(mongoReplicaSet.getMongoRsStatus().getMembers()),
                hasItems(
                    ReplicaSetMemberState.PRIMARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.SECONDARY,
                    ReplicaSetMemberState.DOWN
                )
            );
        }
    }
}
