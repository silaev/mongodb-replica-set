package com.github.silaev.mongodb.replicaset.util;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;

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

    public static MongoCollection<Document> getCollection(
        final MongoClient mongoClient,
        final String dbName, final String collectionName
    ) {
        return mongoClient.getDatabase(dbName).getCollection(collectionName);
    }
}
