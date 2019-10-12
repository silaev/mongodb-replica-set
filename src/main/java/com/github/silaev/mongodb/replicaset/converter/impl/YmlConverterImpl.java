package com.github.silaev.mongodb.replicaset.converter.impl;

import com.github.silaev.mongodb.replicaset.converter.YmlConverter;
import lombok.val;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.InputStream;

/**
 * Converts an input stream representing a yml file to a proper dto.
 *
 * @author Konstantin Silaev
 */
public class YmlConverterImpl implements YmlConverter {
    /**
     * Unmarshals an input stream into an instance of clazz
     *
     * @param clazz a target class
     * @param io    an input stream representing a yml file
     * @param <T>   a type parameter for target class
     * @return T an instance of a target class
     */
    public <T> T unmarshal(Class<T> clazz, final InputStream io) {
        val representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);
        Yaml yaml = new Yaml(
            new Constructor(clazz),
            representer
        );
        return yaml.load(io);
    }
}
