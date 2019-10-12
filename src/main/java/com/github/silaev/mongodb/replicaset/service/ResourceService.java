package com.github.silaev.mongodb.replicaset.service;

import java.io.InputStream;

public interface ResourceService {
    InputStream getResourceIO(String fileName);
}
