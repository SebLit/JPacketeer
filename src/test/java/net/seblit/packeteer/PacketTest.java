package net.seblit.packeteer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class PacketTest {

    @Test
    public void testIsFlagSet() {
        Packet allFlags = new Packet((byte) 0, (byte) 0, (byte) 0b11111111);
        Packet noFlags = new Packet((byte) 0, (byte) 0, (byte) 0b00000000);
        Packet evenFlags = new Packet((byte) 0, (byte) 0, (byte) 0b01010101);
        Packet unevenFlags = new Packet((byte) 0, (byte) 0, (byte) 0b10101010);
        for (int bit = 0; bit < 8; bit++) {
            assertTrue(allFlags.isFlagSet(bit));
            assertFalse(noFlags.isFlagSet(bit));
            assertEquals(bit % 2 == 0, evenFlags.isFlagSet(bit));
            assertEquals(bit % 2 != 0, unevenFlags.isFlagSet(bit));
        }
    }

}
