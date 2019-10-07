package com.silaev.mongodb.replicaset.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CollectionUtils {

    private CollectionUtils() {

    }

    public static boolean isEqualCollection(final Collection<?> a,
                                            final Collection<?> b) {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        Set<?> set1 = new HashSet<>(a);
        Set<?> set2 = new HashSet<>(b);

        return a.size() == b.size() && set1.equals(set2);
    }
}
