package com.geofleet.tracking.exception;

public class KafkaProcessingException extends RuntimeException {
    public KafkaProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}