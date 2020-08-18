package com.github.silaev.mongodb.replicaset.service.impl;

import com.github.silaev.mongodb.replicaset.service.ResourceService;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Gets resource as a stream making it possible to mock such a call.
 * Also has some helper methods for the same reason.
 *
 * @author Konstantin Silaev
 */
public class ResourceServiceImpl implements ResourceService {
    public InputStream getResourceIO(final String fileName) {
        return Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(fileName);
    }

    @SneakyThrows
    public String getString(final InputStream io) {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int length;
        while ((length = io.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }
}
