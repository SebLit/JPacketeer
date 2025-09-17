package net.seblit.packeteer;

import org.jetbrains.annotations.Nullable;

/**
 * A Packet that was received by a {@link Client}. Implementations must override {@link #process(byte...)}
 * */
public abstract class IncomingPacket extends Packet{
    /**
     * Creates a new instance with the provided configuration
     * @param type The type of the packet
     * @param version The version of the packet
     * @param flags The flags of the packet
     * */
    public IncomingPacket(byte type, byte version, byte flags) {
        super(type, version, flags);
    }

    /**
     * Called when this packet was received successfully and is now being processed
     * @param payload The received payload for this packet. May be empty or null if none was received
     * @throws ProcessingException if any error occur during processing
     * */
    public abstract void process(byte @Nullable ... payload) throws ProcessingException;

}
