package net.seblit.packeteer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

public class BitUtilTest {

    @Test
    public void testIsFlagSet() {
        int allFlags = 0b11111111;
        int noFlags = 0b00000000;
        int evenFlags = 0b01010101;
        int unevenFlags = 0b10101010;
        for (int bit = 0; bit < 8; bit++) {
            assertTrue(BitUtil.isFlagSet(allFlags, bit));
            assertFalse(BitUtil.isFlagSet(noFlags, bit));
            assertEquals(bit % 2 == 0, BitUtil.isFlagSet(evenFlags, bit));
            assertEquals(bit % 2 != 0, BitUtil.isFlagSet(unevenFlags, bit));
        }
    }

    @Test
    public void testCreateFlags() {
        byte allFlags = (byte) 0b11111111;
        byte noFlags = 0b00000000;
        byte unevenFlags = (byte) 0b10101010;
        byte evenFlags = 0b01010101;

        assertEquals(allFlags, BitUtil.createFlags(0, 1, 2, 3, 4, 5, 6, 7));
        assertEquals(noFlags, BitUtil.createFlags());
        assertEquals(unevenFlags, BitUtil.createFlags(1, 3, 5, 7));
        assertEquals(evenFlags, BitUtil.createFlags(0, 2, 4, 6));
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
