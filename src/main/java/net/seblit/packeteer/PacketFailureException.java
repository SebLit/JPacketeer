package net.seblit.packeteer;

/**
 * This type of exception is thrown if the receiver of a packet couldn't process it and replied with an acknowledgement including the failure flag.<br>
 * @see Client#send(Packet, byte...)
 * */
public class PacketFailureException extends NetworkException {

    public PacketFailureException() {
    }

    public PacketFailureException(String message) {
        super(message);
    }

    public PacketFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public PacketFailureException(Throwable cause) {
        super(cause);
    }

    public PacketFailureException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
