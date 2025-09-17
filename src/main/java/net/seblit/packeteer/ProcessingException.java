package net.seblit.packeteer;

/**
 * This type of Exception is thrown if any errors occur while creating or processing {@link IncomingPacket}s
 * @see Client#receive()
 * */
public class ProcessingException extends Exception{

    public ProcessingException() {
    }

    public ProcessingException(String message) {
        super(message);
    }

    public ProcessingException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProcessingException(Throwable cause) {
        super(cause);
    }

    public ProcessingException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
