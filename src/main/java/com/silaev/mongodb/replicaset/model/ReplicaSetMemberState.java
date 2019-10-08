package com.silaev.mongodb.replicaset.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Represents states that a MongoDb node has as per:
 * <a href="https://docs.mongodb.com/manual/reference/replica-states/">Replica Set Member States</a>
 *
 * @author Konstantin Silaev on 10/3/2019
 */
@RequiredArgsConstructor
public enum ReplicaSetMemberState {
    STARTUP(0),
    PRIMARY(1),
    SECONDARY(2),
    RECOVERING(3),
    STARTUP2(5),
    UNKNOWN(6),
    ARBITER(7),
    DOWN(8),
    ROLLBACK(9),
    NOT_RECOGNIZED(Integer.MAX_VALUE);

    @Getter
    private final int value;

    public static ReplicaSetMemberState getByValue(final int value) {
        for (ReplicaSetMemberState state : values()) {
            if (state.getValue() == value) {
                return state;
            }
        }
        return NOT_RECOGNIZED;
    }
}
