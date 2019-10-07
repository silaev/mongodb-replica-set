package com.silaev.mongodb.replicaset.service;

import java.io.InputStream;

public interface ResourceService {
    InputStream getResourceIO(String fileName);
}
