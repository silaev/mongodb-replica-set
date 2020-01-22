package com.github.silaev.mongodb.replicaset.service.impl;

import com.github.silaev.mongodb.replicaset.service.ResourceService;

import java.io.InputStream;

/**
 * Gets resource as a stream making it possible to mock such a call.
 *
 * @author Konstantin Silaev
 */
public class ResourceServiceImpl implements ResourceService {
    public InputStream getResourceIO(String fileName) {
        return Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(fileName);
    }
}
