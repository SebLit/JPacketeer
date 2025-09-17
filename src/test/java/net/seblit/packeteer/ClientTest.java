package net.seblit.packeteer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClientTest {

    private static final byte[] TEST_DATA_ACK_PACKET = {
            0xF, // start low
            0xA, // start high
            1, // protocol version
            0, // messageId
            Packet.TYPE_ACK, // type
            1, // packet version
            0, // flags
            0, // payload length low
            0, // payload length high
            0, // payload checksum low,
            0, // payload checksum high
            (byte) 0b0001100, // header checksum low
            (byte) 0b1000001 // header checksum high
    };
    private static final byte[] TEST_DATA_ACK_FAILURE_PACKET = {
            0xF, // start low
            0xA, // start high
            1, // protocol version
            0, // messageId
            Packet.TYPE_ACK, // type
            1, // packet version
            0b00000010, // failure flag set to 1
            0, // payload length low
            0, // payload length high
            0, // payload checksum low,
            0, // payload checksum high
            (byte) 0b01110101, // header checksum low
            (byte) 0b10000001 // header checksum high
    };
    private static final byte[] TEST_DATA_PACKET_PAYLOAD = {
            (byte) 0b10101010, // payload first byte
            0b01010101, // payload second byte
            0b00110011 // payload third byte
    };
    private static final byte[] TEST_DATA_PACKET = {
            0xF, // start low
            0xA, // start high
            1, // protocol version
            0, // messageId
            1, // type
            1, // packet version
            0b01111111, // flags
            3, // payload length low
            0, // payload length high
            0b01011111, // payload checksum low,
            0b01100101, // payload checksum high
            (byte) 0b11110000, // header checksum low
            (byte) 0b11010100, // header checksum high
            TEST_DATA_PACKET_PAYLOAD[0],
            TEST_DATA_PACKET_PAYLOAD[1],
            TEST_DATA_PACKET_PAYLOAD[2]
    };
    private NetworkAdapter mockedAdapter;
    private PacketFactory mockedFactory;
    private IncomingPacket mockedIncomingPacket;
    private Packet mockedPacket;
    private Client client;

    @BeforeEach
    public void setup() throws ProcessingException{
        mockedAdapter = mock(NetworkAdapter.class);
        mockedFactory = mock(PacketFactory.class);
        mockedIncomingPacket = mock(IncomingPacket.class);
        mockedPacket = mock(Packet.class);

        when(mockedFactory.create(anyByte(), anyByte(), anyByte(), anyByte())).thenReturn(mockedIncomingPacket);
        when(mockedPacket.getType()).thenReturn((byte) 1);
        when(mockedPacket.getVersion()).thenReturn((byte) 1);
        when(mockedPacket.getFlags()).thenReturn((byte) 0b01100110);
        when(mockedIncomingPacket.getType()).thenReturn((byte) 1);
        when(mockedIncomingPacket.getVersion()).thenReturn((byte) 1);
        when(mockedIncomingPacket.getFlags()).thenReturn((byte) 0b01100110);

        client = new Client((byte) 1, 2, mockedAdapter, mockedFactory);
    }

    @Test
    public void testSend() throws NetworkException {
        byte messageId = client.send(mockedPacket);
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockedAdapter, atLeastOnce()).write(dataCaptor.capture());

        byte[] data = collectWrittenData(dataCaptor);
        assertEquals(13, data.length);
        assertEquals(0xF, data[0]); // start low byte
        assertEquals(0xA, data[1]); // start high byte
        assertEquals(client.getProtocolVersion(), data[2]); // protocol version
        assertEquals(messageId, data[3]);
        assertEquals(mockedPacket.getType(), data[4]);
        assertEquals(mockedPacket.getVersion(), data[5]);
        assertEquals(mockedPacket.getFlags(), data[6]);
        assertEquals(0, BitUtil.intFrom16Bit(data[7], data[8])); // payload length
        assertEquals(0, BitUtil.intFrom16Bit(data[9], data[10])); // payload checksum

        int headerChecksum = Client.createChecksum(data[2], data[3], data[4], data[5], data[6], data[7], data[8], data[9], data[10]);
        assertEquals(headerChecksum, BitUtil.intFrom16Bit(data[11], data[12]));
    }

    @Test
    public void testSend_Payload() throws NetworkException {
        int payloadLength = 10;
        byte[] payload = new byte[payloadLength];
        new Random().nextBytes(payload);
        client.send(mockedPacket, payload);
        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockedAdapter, atLeastOnce()).write(dataCaptor.capture());

        byte[] data = collectWrittenData(dataCaptor);
        assertEquals(payloadLength, BitUtil.intFrom16Bit(data[7], data[8]));
        assertArrayEquals(payload, dataCaptor.getValue());
    }

    @Test
    public void testSend_Ack() throws NetworkException {
        when(mockedPacket.isFlagSet(0)).thenReturn(true);
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(TEST_DATA_ACK_PACKET));
        client.send(mockedPacket);
    }

    @Test
    public void testSend_Failure() throws NetworkException {
        when(mockedPacket.isFlagSet(0)).thenReturn(true);
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(TEST_DATA_ACK_FAILURE_PACKET));
        assertThrows(PacketFailureException.class, () -> client.send(mockedPacket));
    }

    @Test
    public void testSend_Resend() throws NetworkException {
        ArgumentCaptor<byte[]> outputCaptor = ArgumentCaptor.forClass(byte[].class);
        when(mockedPacket.isFlagSet(0)).thenReturn(true);
        when(mockedAdapter.read(anyInt()))
                .thenThrow(new NetworkException())
                .then(new AdapterByteStream(TEST_DATA_ACK_PACKET));
        client.send(mockedPacket);
        verify(mockedAdapter, atLeastOnce()).write(outputCaptor.capture());

        // we check if a resend happened by validating, that same data has been written to adapter twice
        byte[] allWrittenData = collectWrittenData(outputCaptor);
        byte[] firstHalf = new byte[allWrittenData.length / 2];
        byte[] secondHalf = new byte[firstHalf.length];
        System.arraycopy(allWrittenData, 0, firstHalf, 0, firstHalf.length);
        System.arraycopy(allWrittenData, firstHalf.length, secondHalf, 0, secondHalf.length);
        assertArrayEquals(firstHalf, secondHalf);
    }

    @Test
    public void testSend_ExceedResendCount() throws NetworkException {
        when(mockedPacket.isFlagSet(0)).thenReturn(true);
        when(mockedAdapter.read(anyInt())).thenThrow(new NetworkException());
        assertThrows(SendTimeoutException.class, () -> client.send(mockedPacket));
    }

    @Test
    public void testSend_MessageIdWrapAround() throws NetworkException {
        for (int i = 0; i <= Byte.MAX_VALUE; i++) {
            assertEquals((byte) i, client.send(mockedPacket));
        }
        assertEquals(Byte.MIN_VALUE, client.send(mockedPacket));
    }

    @Test
    public void testReceive() throws NetworkException, ProcessingException {
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(TEST_DATA_PACKET));
        client.receive();

        ArgumentCaptor<byte[]> dataCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<Byte> protocolCaptor = ArgumentCaptor.forClass(Byte.class);
        ArgumentCaptor<Byte> typeCaptor = ArgumentCaptor.forClass(Byte.class);
        ArgumentCaptor<Byte> versionCaptor = ArgumentCaptor.forClass(Byte.class);
        ArgumentCaptor<Byte> flagsCaptor = ArgumentCaptor.forClass(Byte.class);

        verify(mockedFactory).create(protocolCaptor.capture(), typeCaptor.capture(), versionCaptor.capture(), flagsCaptor.capture());
        verify(mockedIncomingPacket).process(dataCaptor.capture());

        assertEquals(TEST_DATA_PACKET[2], protocolCaptor.getValue());
        assertEquals(TEST_DATA_PACKET[4], typeCaptor.getValue());
        assertEquals(TEST_DATA_PACKET[5], versionCaptor.getValue());
        assertEquals(TEST_DATA_PACKET[6], flagsCaptor.getValue());
        assertArrayEquals(TEST_DATA_PACKET_PAYLOAD, dataCaptor.getValue());
    }

    @Test
    public void testReceive_Ack() throws NetworkException, ProcessingException {
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(TEST_DATA_ACK_PACKET));
        client.receive();
        verify(mockedFactory, never()).create(anyByte(), anyByte(), anyByte(), anyByte());
    }

    @Test
    public void testReceive_InvalidHeader() throws NetworkException {
        byte[] data = new byte[TEST_DATA_PACKET.length];
        System.arraycopy(TEST_DATA_PACKET, 0, data, 0, data.length);
        data[5] = 2; // change packet type to 2 without adjusting checksum
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(data));

        assertThrows(NetworkException.class, client::receive);
    }

    @Test
    public void testReceive_InvalidPayload() throws NetworkException, ProcessingException {
        byte[] data = new byte[TEST_DATA_PACKET.length];
        System.arraycopy(TEST_DATA_PACKET, 0, data, 0, data.length);
        data[13] = 0; // change first payload byte without adjusting checksum
        when(mockedAdapter.read(anyInt())).then(new AdapterByteStream(data));

        client.receive();
        verify(mockedFactory, never()).create(anyByte(), anyByte(), anyByte(), anyByte());
    }

    @Test
    public void testChecksum() {
        byte[] data = {0b0, 0b1, 0b10, 0b11};
        int expected = 41232;
        assertEquals(expected, Client.createChecksum(data));
    }

    @Test
    public void testChecksum_Empty() {
        byte[] nullArray = null;
        assertEquals(0, Client.createChecksum(nullArray));
        assertEquals(0, Client.createChecksum());
    }

    private static class AdapterByteStream implements Answer<byte[]> {

        private final byte[] data;
        private int currentIndex = 0;

        private AdapterByteStream(byte[] data) {
            this.data = data;
        }

        @Override
        public byte[] answer(InvocationOnMock invocationOnMock) throws Throwable {
            int count = invocationOnMock.getArgument(0);
            if (count + currentIndex > data.length) {
                throw new NetworkException("No more data available -> simulate read timeout");
            }
            byte[] result = new byte[count];
            System.arraycopy(data, currentIndex, result, 0, count);
            currentIndex += count;
            return result;
        }
    }

    private byte[] collectWrittenData(ArgumentCaptor<byte[]> dataCaptor) {
        int totalLength = dataCaptor.getAllValues().stream().mapToInt(a -> a.length).sum();
        byte[] result = new byte[totalLength];
        int pos = 0;
        for (byte[] chunk : dataCaptor.getAllValues()) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return result;
    }

}
