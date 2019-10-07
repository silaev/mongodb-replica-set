package com.silaev.mongodb.replicaset.exception;

/**
 * @author Konstantin Silaev
 */
public class IncorrectUserInputException extends RuntimeException {

    public IncorrectUserInputException(String errorMessage) {
        super(errorMessage);
    }
}
