package com.silaev.mongodb.replicaset.converter;

import java.io.InputStream;

/**
 * Converts a yml file to a proper dto.
 *
 * @author Konstantin Silaev
 */
public interface YmlConverter {
    <T> T unmarshal(Class<T> clazz, final InputStream io);
}
