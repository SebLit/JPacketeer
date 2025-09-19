package net.seblit.packeteer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class BitUtilTest {

    @Test
    public void testIsFlagSet() {
        int allFlags = 0xFFFFFFFF;
        int noFlags = 0x0;
        int unevenFlags = 0xAAAAAAAA;
        int evenFlags = 0x55555555;
        for (int bit = 0; bit < 32; bit++) {
            assertTrue(BitUtil.isFlagSet(allFlags, bit));
            assertFalse(BitUtil.isFlagSet(noFlags, bit));
            assertEquals(bit % 2 == 0, BitUtil.isFlagSet(evenFlags, bit));
            assertEquals(bit % 2 != 0, BitUtil.isFlagSet(unevenFlags, bit));
        }
    }

    @Test
    public void testCreateFlags() {
        int allFlags = 0xFFFFFFFF;
        int noFlags = 0x0;
        int unevenFlags = 0xAAAAAAAA;
        int evenFlags = 0x55555555;

        assertEquals(allFlags, BitUtil.createFlags(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31));
        assertEquals(noFlags, BitUtil.createFlags());
        assertEquals(unevenFlags, BitUtil.createFlags(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31));
        assertEquals(evenFlags, BitUtil.createFlags(0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30));
    }

    @Test
    public void testSetFlag() {
        for (int bit = 0; bit < 32; bit++) {
            int singleBit = 1 << bit;
            int noFlags = 0b0;
            int allFlags = 0xFFFFFFFF;
            int expectedSet = 0b1 << bit;
            int expectedRemoved = ~singleBit;
            assertEquals(expectedSet, BitUtil.setFlag(noFlags, bit, true));
            assertEquals(expectedRemoved, BitUtil.setFlag(allFlags, bit, false));
        }
    }

    @Test
    public void testGetByteAt() {
        for (int bit = 0; bit < 32; bit++) {
            int byteIndex = bit / 8;
            int value = 1 << bit;
            byte byteValue = (byte) (value>>>byteIndex*8);
            assertEquals(byteValue, BitUtil.getByteAt(value, byteIndex));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0b0, 0b1111111111111111, 0b0011001111001100, 0b1100110000110011})
    public void testIntFrom16Bit(int value) {
        byte low = (byte) value;
        byte high = (byte) (value >> 8);
        assertEquals(value, BitUtil.intFrom16Bit(low, high));
    }

}
