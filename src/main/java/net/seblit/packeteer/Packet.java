package net.seblit.packeteer;

/**
 * Represents a packet that can be sent or received by a {@link Client}. For receiving Packets see also {@link IncomingPacket}.<br>
 * Note that packet type 0 ({@link Packet#TYPE_ACK}) and flag 0 are reserved for acknowledgements and may not be used for any other purposes.
 * */
public class Packet {

    /**
     * Packet type for acknowledgements
     * */
    public static final byte TYPE_ACK = 0;

    private final byte type;
    private final byte version;
    private final byte flags;

    /**
     * Creates a new instance
     * @param type The type of the packet. Note that type 0 ({@link Packet#TYPE_ACK}) is reserved for acknowledgements
     * @param version The version of the packet
     * @param flags The flags of the packet. Note that flag 0 is reserved for acknowledgements
     * */
    public Packet(byte type, byte version, byte flags){
        this.type = type;
        this.version = version;
        this.flags = flags;
    }

    /**
     * @return the type of this packet
     * */
    public byte getType() {
        return type;
    }

    /**
     * @return the version of this packet
     * */
    public byte getVersion() {
        return version;
    }

    /**
     * @return the flags of this packet
     * */
    public byte getFlags() {
        return flags;
    }

    /**
     * @param index The index (0-7) of the flag
     * @return whether the flag at the desired index is set or not
     * */
    public boolean isFlagSet(int index){
        return BitUtil.isFlagSet(flags, index);
    }

}
