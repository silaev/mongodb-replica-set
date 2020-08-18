package com.github.silaev.mongodb.replicaset.service;

import java.io.InputStream;

public interface ResourceService {
    InputStream getResourceIO(final String fileName);

    String getString(final InputStream io);
}
