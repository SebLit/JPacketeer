package net.seblit.packeteer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Mockito.*;

public class IntegrationTest {

    private Client sender;
    private Client receiver;

    @BeforeEach
    public void setup() throws NetworkException, ProcessingException {
        PacketFactory factory = mock(PacketFactory.class);
        when(factory.create(anyByte(), anyByte(), anyByte(), anyByte())).thenAnswer(invocationOnMock -> {
            IncomingPacket packet = mock(IncomingPacket.class);
            when(packet.getType()).thenReturn(invocationOnMock.getArgument(1));
            when(packet.getVersion()).thenReturn(invocationOnMock.getArgument(2));
            when(packet.getFlags()).thenReturn(invocationOnMock.getArgument(3));
            return packet;
        });
        Queue<Byte> senderInput = new LinkedBlockingQueue<>();
        Queue<Byte> receiverInput = new LinkedBlockingQueue<>();
        long readTimeout = 500;
        NetworkAdapter adapterA = mockAdapter(senderInput, receiverInput, readTimeout);
        NetworkAdapter adapterB = mockAdapter(receiverInput, senderInput, readTimeout);
        sender = new Client((byte) 1, 2, adapterA, factory);
        receiver = new Client((byte) 1, 2, adapterB, factory);
    }

    @Test
    public void unsignedTest(){
        byte val = 0;
        for(int i = 0; i < 300; i++){
            System.out.print(val);
            System.out.println(", "+BitUtil.getUnsigned(val));
            val++;
        }
    }

    @Test
    public void testTransmission() throws InterruptedException {
        Packet packet = new Packet((byte) 1, (byte) 1, (byte) BitUtil.createFlags(0));
        int packetCount = 500;
        TrnasmissionRunnable senderRunnable = new TrnasmissionRunnable(sender, packet, null, packetCount, false);
        TrnasmissionRunnable receiverRunnable = new TrnasmissionRunnable(receiver, packet, null, packetCount, true);

        Thread senderThread = new Thread(senderRunnable);
        senderThread.setName("Sender");
        Thread receiverThread = new Thread(receiverRunnable);
        receiverThread.setName("Receiver");
        senderThread.start();
        receiverThread.start();
        senderThread.join();
        receiverThread.join();

        Assertions.assertTrue(senderRunnable.success);
        Assertions.assertTrue(receiverRunnable.success);
    }

    private NetworkAdapter mockAdapter(Queue<Byte> ownerInput, Queue<Byte> partnerInput, long readTimeout) throws NetworkException {
        NetworkAdapter adapter = mock(NetworkAdapter.class);
        doAnswer(invocationOnMock -> {
            try {
                byte[] outgoingData = (byte[]) invocationOnMock.getRawArguments()[0];
                for (byte data : outgoingData) {
                    synchronized (partnerInput) {
                        partnerInput.add(data);
                        partnerInput.notifyAll();
                    }
                }
            } catch (Exception ex) {
                throw new NetworkException(ex);
            }
            return null;
        }).when(adapter).write(any(byte[].class));
        when(adapter.read(anyInt())).thenAnswer(invocationOnMock -> {
            try {
                byte[] readData = new byte[(int) invocationOnMock.getArgument(0)];
                for (int i = 0; i < readData.length; i++) {
                    synchronized (ownerInput) {
                        if (ownerInput.isEmpty()) {
                            ownerInput.wait(readTimeout);
                        }
                    }
                    readData[i] = ownerInput.remove();
                }
                return readData;
            } catch (Exception ex) {
                throw new NetworkException(ex);
            }
        });
        return adapter;
    }

    private static class TrnasmissionRunnable implements Runnable {

        private final Client client;
        private final Packet packet;
        private final byte[] payload;
        private final int packetCount;
        private boolean success;
        private final boolean receiving;

        public TrnasmissionRunnable(Client client, Packet packet, byte[] payload, int packetCount, boolean receiving) {
            this.client = client;
            this.packet = packet;
            this.payload = payload;
            this.packetCount = packetCount;
            this.receiving = receiving;
        }

        @Override
        public void run() {
            for (int i = 0; i < packetCount; i++) {
                try {
                    if (receiving) {
                        client.receive();
                    } else {
                        client.send(packet, payload);
                    }
                } catch (Exception ex) {
                    Assertions.fail(new Exception("Failed at packet #" + i, ex));
                    return;
                }
            }
            success = true;
        }
    }

}
