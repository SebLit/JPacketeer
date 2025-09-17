package net.seblit.packeteer;

import org.jetbrains.annotations.NotNull;

/**
 * Used by a {@link Client} to create {@link IncomingPacket}s when receiving packets
 * */
public interface PacketFactory {

    /**
     * Creates an {@link IncomingPacket} based off the received information
     * @param protocolVersion The protocol version used by the client that transmitted the packet
     * @param type The type of the packet
     * @param version The version of the packet
     * @param flags The flags of the packet
     * @throws ProcessingException if for any reason the packet couldn't be created
     * */
    @NotNull
    IncomingPacket create(byte protocolVersion, byte type, byte version, byte flags) throws ProcessingException;

}
