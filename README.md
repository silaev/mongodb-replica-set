# Java8 MongoDBReplicaSet to construct a full-featured MongoDB cluster for integration testing, reproducing production issues, learning distributed systems by the example of MongoDB   
[![Build Status](https://travis-ci.org/silaev/mongodb-replica-set.svg?branch=master)](https://travis-ci.org/silaev/mongodb-replica-set)
[![codecov](https://codecov.io/gh/silaev/mongodb-replica-set/branch/master/graph/badge.svg)](https://codecov.io/gh/silaev/mongodb-replica-set)

#### Prerequisite
- Java 8+
- Docker Desktop
- Chart shows local and remote docker support for replicaSetNumber

    replicaSetNumber | local docker host | local docker host running tests from inside a container with mapping the Docker socket | remote docker daemon | availability of an arbiter node |
    :---: | :---: |:---: | :---: | :---: |
    1 | + | + | + | - |
    from 2 to 7 (including)  | only if adding <b>127.0.0.1 dockerhost</b> to the OS host file | + | + | + |

Tip:
A single node replica set is the fastest one among others.That's the default mode for MongoDbReplicaSet.
However, to use only it, consider the [Testcontainers MongoDB module on GitHub](https://www.testcontainers.org/modules/databases/mongodb/)
    
#### Getting it
- Gradle:
```groovy
dependencies {
    testCompile("com.github.silaev:mongodb-replica-set:${LATEST_RELEASE}")
}
 ```
- Maven:
```xml
<dependencies>
    <dependency>
        <groupId>com.github.silaev</groupId>
        <artifactId>mongodb-replica-set</artifactId>
        <version>${LATEST_RELEASE}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```
Replace ${LATEST_RELEASE} with [the Latest Version Number](https://search.maven.org/search?q=g:com.github.silaev%20AND%20a:mongodb-replica-set) 
    
#### MongoDB versions that MongoReplicaSet is constantly tested against
version | transaction support |
---------- | ---------- |
3.6.14 |-|
4.0.12 |+|
4.2.0 |+|
 
#### Examples (note that MongoReplicaSet is test framework agnostic)
The example of a JUnit5 test class:
```java
import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ITTest {
    private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
            //.replicaSetNumber(3)
            //.mongoDockerImageName("mongo:4.2.0")
            //.addArbiter(true)
            //.addToxiproxy(true)
            //.awaitNodeInitAttempts(30)
            .build();

    @BeforeAll
    static void setUpAll() {
        MONGO_REPLICA_SET.start();
    }

    @AfterAll
    static void tearDownAllAll() {
        MONGO_REPLICA_SET.stop();
    }

    @Test
    void shouldTestReplicaSetUrlAndStatus() {
        assertNotNull(MONGO_REPLICA_SET.getReplicaSetUrl());
        assertNotNull(MONGO_REPLICA_SET.getMongoRsStatus());
    }
}
```
You can also use MongoReplicaSet as a non-static field. For example, to have 
its own instance of MongoReplicaSet for each test (applicable for JUnit). 
 
See more examples in the test sources [mongodb-replica-set on github](https://github.com/silaev/mongodb-replica-set/tree/master/src/test/java/com/github/silaev/mongodb/replicaset/integration)

The example of a JUnit5 test class in a Spring Boot + Spring Data application:
```java
import com.github.silaev.mongodb.replicaset.MongoDbReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
//@DataMongoTest
@ContextConfiguration(initializers = ITTest.Initializer.class)
class ITTest {
    private static final MongoDbReplicaSet MONGO_REPLICA_SET = MongoDbReplicaSet.builder()
            //.replicaSetNumber(3)
            //.mongoDockerImageName("mongo:4.2.0")
            //.addArbiter(true)
            //.addToxiproxy(true)
            //.awaitNodeInitAttempts(30)
            .build();

    @BeforeAll
    static void setUpAll() {
        MONGO_REPLICA_SET.start();
    }

    @AfterAll
    static void tearDownAllAll() {
        MONGO_REPLICA_SET.stop();
    }

    @Test
    void shouldTestReplicaSetUrlAndStatus() {
        assertNotNull(MONGO_REPLICA_SET.getReplicaSetUrl());
        assertNotNull(MONGO_REPLICA_SET.getMongoRsStatus());
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
            if (MONGO_REPLICA_SET.isEnabled()) {
                TestPropertyValues.of(
                        "spring.data.mongodb.uri: " + MONGO_REPLICA_SET.getReplicaSetUrl()
                ).applyTo(configurableApplicationContext);
            }
        }
    }
}
``` 
See a full Spring Boot + Spring Data example [wms on github](https://github.com/silaev/wms/blob/master/src/test/java/com/silaev/wms/integration/ProductControllerITTest.java/)

#### Motivation
- Cross-platform solution that doesn't depend on fixed ports;
- Testing MongoDB transactions to run against an environment close to a production one;
- Testing production issues by recreating a real MongoDB replica set (currently without shards);
- Education to newcomers to the MongoDB world (learning the behaviour of a distributed NoSQL database while 
dealing with network partitioning, analyze the election process and so on).
   
#### General info
MongoDB starting from version 4 supports multi-document transactions only on a replica set.
For example, to initialize a 3 replica set on fixed ports via Docker, one has to do the following:
1. Add `127.0.0.1 mongo1 mongo2 mongo3` to the host file of an operation system;
2. Run in terminal:
    - `docker network create mongo-cluster`
    - `docker run --name mongo1 -d --net mongo-cluster -p 50001:50001 mongo:4.0.10 mongod --replSet docker-rs --port 50001`
    - `docker run --name mongo2 -d --net mongo-cluster -p 50002:50002 mongo:4.0.10 mongod --replSet docker-rs --port 50002`
    - `docker run --name mongo3 -d --net mongo-cluster -p 50003:50003 mongo:4.0.10 mongod --replSet docker-rs --port 50003`
3. Prepare the following unix end of lines script (optionally  put it folder scripts or use rs.add on each node):
    ```js 
    rs.initiate({
        "_id": "docker-rs",
        "members": [
            {"_id": 0, "host": "mongo1:50001"},
            {"_id": 1, "host": "mongo2:50002"},
            {"_id": 2, "host": "mongo3:50003"}
        ]
    });
    ```
4. Run in terminal:
    - `docker cp scripts/ mongo1:/scripts/`
    - `docker exec -it mongo1  /bin/sh -c "mongo --port 50001 < /scripts/init.js"`

As we can see, there is a lot of operations to execute and we even didn't touch a non-fixed port approach.
That's where the MongoDbReplicaSet might come in handy. 

#### Supported features 
Feature | Description | default value | how to set | 
---------- | ----------- | ----------- | ----------- |
replicaSetNumber | The number of voting nodes in a replica set including a master node | 1 | MongoDbReplicaSet.builder() |
awaitNodeInitAttempts | The number of approximate seconds to wait for a master or an arbiter node(if addArbiter=true) | 29 starting from 0 | MongoDBReplicaSet.builder() | 
propertyFileName | yml file located on the classpath | none | MongoDbReplicaSet.builder() |
mongoDockerImageName | a MongoDB docker file name | mongo:4.0.10 | finds first set:<br/>1) MongoDbReplicaSet.builder()<br/> 2) the system property mongoReplicaSetProperties.mongoDockerImageName<br/> 3) propertyFile<br/> 4) default value | 
addArbiter | whether or not to add an arbiter node to a cluster | false | MongoDbReplicaSet.builder() |
slaveDelayTimeout | whether or not to make one master and the others as delayed members  | false | MongoDbReplicaSet.builder() |
addToxiproxy | whether or not to add a addToxiproxy container for specific fault tolerance tests | false | MongoDbReplicaSet.builder() |
enabled | whether or not MongoReplicaSet is enabled even if instantiated in a test | true | finds first set:<br/>1) the system property mongoReplicaSetProperties.enabled<br/>2) propertyFile<br/>3) default value |

a propertyFile.yml example: 
```yaml
mongoReplicaSetProperties:
  enabled: false
  mongoDockerImageName: mongo:4.1.13
```

#### License
[The MIT License (MIT)](https://github.com/silaev/mongodb-replica-set/blob/master/LICENSE/)

#### Additional links
* [mongo-replica-set-behind-firewall](https://serverfault.com/questions/815955/mongo-replica-set-behind-firewall)
* [Support different networks](https://jira.mongodb.org/browse/SERVER-1889)

#### Copyright
Copyright (c) 2020 Konstantin Silaev <silaev256@gmail.com>
