package com.silaev.mongodb.replicaset.service.impl;

import com.silaev.mongodb.replicaset.service.ResourceService;

import java.io.InputStream;

/**
 * Get resource as a stream making it possible to mock such a call.
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
