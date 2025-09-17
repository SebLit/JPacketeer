package net.seblit.packeteer;

/**
 * This Exception is thrown by {@link Client#send(Packet, byte...)} if a packet failed to receive an acknowledgement
 * */
public class SendTimeoutException extends NetworkException{

    public SendTimeoutException() {
    }

    public SendTimeoutException(String message) {
        super(message);
    }

    public SendTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }

    public SendTimeoutException(Throwable cause) {
        super(cause);
    }

    public SendTimeoutException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
