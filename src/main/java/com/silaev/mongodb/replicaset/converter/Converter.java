package com.silaev.mongodb.replicaset.converter;

public interface Converter<S, T> {
    T convert(S source);
}
