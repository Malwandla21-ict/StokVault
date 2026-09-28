package com.stokvault.notification;

/** A gateway couldn't deliver a message (unreachable, rejected...). */
public class GatewayException extends Exception {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
