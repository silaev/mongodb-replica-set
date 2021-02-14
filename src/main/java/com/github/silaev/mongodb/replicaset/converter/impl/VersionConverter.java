package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.converter.Converter;
import com.github.silaev.mongodb.replicaset.model.MongoDbVersion;
import lombok.val;

import java.util.Objects;

/**
 * Converts a string to an instance of MongoDbVersion following Semantic Versioning.
 *
 * @author Konstantin Silaev
 */
public class VersionConverter implements Converter<String, MongoDbVersion> {
    @Override
    public MongoDbVersion convert(String source) {
        if (Objects.isNull(source)) {
            throw new IllegalArgumentException("Version is not supposed to be null");
        }
        val strings = source.split("\\.");
        if (strings.length < 2) {
            throw new IllegalArgumentException(
                String.format(
                    "Mongo DB version %s should have at least major and minor parts",
                    source
                )
            );
        }
        return MongoDbVersion.of(
            Integer.parseInt(strings[0]),
            Integer.parseInt(strings[1]),
            strings.length == 3 ? Integer.parseInt(strings[2]) : 0
        );
    }
}
