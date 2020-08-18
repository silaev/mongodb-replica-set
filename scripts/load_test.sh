#!/usr/bin/env bash

cd ..

dir="log"
if [ -d "$dir" ]; then
    rm -rfv ${dir:?}/*
else
    mkdir $dir
fi

q=0; while [[ q -lt $1 ]]; do ((q++)); echo "$q"; ./gradlew clean integrationTest -DmongoReplicaSetProperties.mongoDockerImageName=mongo:$2 --no-daemon --parallel > log/log$q.log; done
