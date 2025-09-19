package net.seblit.packeteer;

public class BitUtil {

    /**
     * @param flags The 32 bits to check
     * @param index The bit index (0-31) to check inside flags
     * @return true if the bit inside flags at the desired index is 1, false otherwise
     */
    public static boolean isFlagSet(int flags, int index) {
        return (flags & (1 << index)) != 0;
    }

    /**
     * @param indices The bit indices (0-31) that should be set to 1
     * @return an 32 bit int with all bits at the desired indices set to 1
     */
    public static int createFlags(int... indices) {
        int result = 0;
        for (int index : indices) {
            result |= (1 << index);
        }
        return result;
    }

    /**
     * Sets a bit within flags to 1 or 0
     *
     * @param flags The initial 32 bits
     * @param index The index(0-31) of the desired bit to change
     * @param isSet Whether to set the bit to 1(true) or 0(false)
     * @return the new flags value with the desired bit set
     */
    public static int setFlag(int flags, int index, boolean isSet) {
        int bit = 1 << index;
        return isSet ? (flags | bit) : (flags & ~bit);
    }

    /**
     * @param value The 4 bytes to read from
     * @param index The index(0-7) of the desired byte
     * @return the desired byte from within value
     */
    public static byte getByteAt(int value, int index) {
        return (byte) (value >> 8 * index);
    }

    /**
     * Creates an int from low and high byte input
     *
     * @param low  The lower 8 bits
     * @param high The higher 8 bits
     * @return an int with its lower 16-bits constructed form provided low and high bytes
     */
    public static int intFrom16Bit(byte low, byte high) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }
}
