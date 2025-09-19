package net.seblit.packeteer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link Client} that can send and read {@link Packet}s through a {@link NetworkAdapter}.
 * For creation of {@link IncomingPacket}s a {@link PacketFactory} is used.<br>
 * <br>
 * This Client also provides support for packet acknowledgement and data validation via check sum. Note that the tracking window
 * for transmission states is limited to 32 transmissions. Producing transmissions further than 32 apart from another will
 * lead to state loss and effected transmissions will be considered failed.
 */
public class Client {

    private static final byte START_BYTE_LOW = 0xF;
    private static final byte START_BYTE_HIGH = 0xA;
    private final byte protocolVersion;
    private final NetworkAdapter adapter;
    private final PacketFactory factory;
    private final int maxSendAttempts;
    private byte messageCount = 0;
    private int pendingStates = 0;
    private int failureStates = 0;

    /**
     * Creates a new instance of {@link Client}
     *
     * @param protocolVersion The protocol version header sent with every {@link Packet}
     * @param maxSendAttempts Count of attempts made to send a packet with acknowledgement
     * @param adapter         The {@link NetworkAdapter} used to send and receive data
     * @param factory         The {@link PacketFactory} used to create {@link IncomingPacket}s when receiving data
     */
    public Client(byte protocolVersion, int maxSendAttempts, @NotNull NetworkAdapter adapter, @NotNull PacketFactory factory) {
        this.protocolVersion = protocolVersion;
        this.adapter = adapter;
        this.factory = factory;
        this.maxSendAttempts = maxSendAttempts;
    }

    /**
     * @return the protocol version used by this client
     */
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * @return the count of attempts made to send a packet with acknowledgement
     */
    public int getMaxSendAttempts() {
        return maxSendAttempts;
    }

    /**
     * Writes a packet to the underlying {@link NetworkAdapter}. RW operations are synchronized and this method
     * may block when others access the same adapter. Transmissions are structured as follows<br>
     * <br>
     * <h1>Initialization</h1>
     * Before transmission, a sequential wrap-around one byte messageId is generated to identify each message.<br>
     * For header and payload (if provided) a 16-bit CRC-16/IBM Checksum is generated. It uses following parameters
     * <li>Initial value: 0x0</li>
     * <li>Polynomial: 0xA001</li>
     * <li>Processing order: LSB to MSB</li>
     * <li>Input reflection: Yes</li>
     * <li>Output reflection: No</li>
     * <li>Final XOR: 0x0000</li>
     * <h1>Transmission</h1>
     * <h2>Start bits</h1>
     * Every communication begins with the two bytes 0xF and 0xA. These serve as a marker for the receiver to detect the beginning
     * of received data.
     * <h2>Header</h1>
     * Headers are made up of the following 9 bytes
     * <li>Protocol version</li>
     * <li>messageId</li>
     * <li>{@link Packet#getType()}</li>
     * <li>{@link Packet#getVersion()}</li>
     * <li>{@link Packet#getFlags()}</li>
     * <li>Lower byte of 16-bit payload length</li>
     * <li>Higher byte of 16-bit payload length</li>
     * <li>Lower byte of 16-bit payload CRC16 checksum</li>
     * <li>Higher byte of 16-bit payload CRC16 checksum</li>
     * <h2>Header checksum</h1>
     * Low, then high byte of 16-bit CRC16 header checksum derived of the above-mentioned Header data
     * <h2>Payload</h1>
     * Payloads are optional. If a payload has been provided, it will be attached to the end of communication
     * <h1>Acknowledgement & resend</h1>
     * Optional, only applied if the acknowledgement flag (flag 0) of the Packet is set.<br>
     * Calls {@link #receive()} until acknowledgement for the transmitted messageId is received. Note that this may also
     * receive other types of packets that will be processed as specified by {@link #receive()}.<br>
     * If any {@link NetworkException} should occur while waiting for acknowledgement the transmission will be considered
     * failed and the entire transmission will be resent until {@link #getMaxSendAttempts()} is reached.<br>
     * If acknowledgement wasn't received after using up all send attempts, a {@link SendTimeoutException} is thrown.<br>
     * If acknowledgement is received and flagged as processing failure (see {@link #receive()}), a {@link PacketFailureException} is thrown.<br>
     * If this transmission is more than 32 transmissions apart from the most recent transmission a {@link SendTimeoutException} is thrown.
     *
     * @param packet  The Packet to transmit
     * @param payload Optional, the payload to transmit. Pass null or empty to ignore. May not be larger than 64Kib since length
     *                is transmitted as 16-bit integer byte count
     * @return the messageId that was generated for this transmission
     * @throws SendTimeoutException   if the packet required acknowledgement but didn't receive it
     * @throws PacketFailureException if the packet received acknowledgement with the failure flag set
     * @throws NetworkException       if the underlying {@link NetworkAdapter} threw any
     */
    public byte send(@NotNull Packet packet, byte @Nullable ... payload) throws NetworkException {
        byte messageId;
        synchronized (this) {
            messageId = messageCount;
            messageCount++;
            markAckPending(messageId, true, false);
        }
        send(messageId, packet, payload);
        return messageId;
    }

    /**
     * Reads the next packet from the underlying {@link NetworkAdapter} and processes it. RW operations are synchronized and this method
     * may block when others access the same adapter. Incoming data is processed as follows
     * <h1>Scanning for start bytes</h1>
     * Bytes will be read until the two byte starting sequence of 0xF 0xA is detected. Once the starting sequence is found,
     * an attempt to read a header is made.
     * <h1>Reading and validating header</h1>
     * For the header 9 bytes are read. Then for the 16-bit header checksum, first the low and then the high byte are read.
     * Next The checksum for the 9 header bytes is generated and compared with the received checksum.<br>
     * If the checksums don't match the data is disposed and the process returns to scanning for the start bytes.
     * <h1>Reading and validating payload</h1>
     * Optional, only applied if payload length that was received in the header is > 0.
     * The payload bytes are read, based off the payload length. Then the checksum for all payload bytes
     * is generated and compared with the payload checksum received in the header.<br>
     * If the checksums don't match the data is disposed and this method returns. In this case a packet was detected
     * successfully but couldn't be processed due to data corruption.
     * <h1>Packet processing</h1>
     * The underlying {@link PacketFactory} is called to create the corresponding {@link IncomingPacket} for the received
     * packet information. Then {@link IncomingPacket#process(byte...)} is called to process it. Note that the payload may be null
     * or empty here if none was received.<br>
     * If the factory fails to create the packet or the packet couldn't be processed a {@link ProcessingException} is thrown.
     * <h1>Acknowledgements</h1>
     * <h2>Incoming</h2>
     * If a packet of type {@link Packet#TYPE_ACK} is received, the previous processing step is skipped. Instead this method will
     * mark the messageId of the acknowledged packet as such and return. If a call to {@link #send(Packet, byte...)} is waiting for an
     * acknowledgement this will cause it to proceed with its transmission.<br>
     * If the packet has its failure flag (flag 1) set it will also be marked as such.<br>
     * All other flags including the acknowledgement flag (flag 0) of this packet are ignored.
     * <h2>Outgoing</h2>
     * Optional, only applied if the acknowledgement flag (flag 0) of the Packet is set.<br>
     * An acknowledgement packet with the same messageId as the received packet is sent as response. Type of the packet is set to
     * {@link Packet#TYPE_ACK} and the version to 1. If the previous processing step failed, the failure flag (flag 1) is set.
     * Acknowledgements are sent without additional payload.<br>
     * Other than that the transmission behaves as defined by {@link #send(Packet, byte...)}
     *
     * @throws NetworkException    if any occur while reading from the underlying {@link NetworkAdapter}
     * @throws ProcessingException if any occur during packet creation in the factory or processing in the packet implementation
     */
    public void receive() throws NetworkException, ProcessingException {
        byte[] header = null;
        byte[] payload;
        synchronized (adapter) {
            while (header == null) {
                if (adapter.read(1)[0] != START_BYTE_LOW || adapter.read(1)[0] != START_BYTE_HIGH) {
                    continue; // wait for start bytes
                }
                header = adapter.read(9);
                byte[] receivedHeaderChecksum = adapter.read(2);
                int actualHeaderChecksum = createChecksum(header);
                if (BitUtil.intFrom16Bit(receivedHeaderChecksum[0], receivedHeaderChecksum[1]) != actualHeaderChecksum) {
                    header = null; // header invalid, clear and scan for next
                }
            }
            if (header[2] == Packet.TYPE_ACK) {
                markAckPending(header[1], false, BitUtil.isFlagSet(header[4], 1));
                return;
            } else {
                int payloadSize = BitUtil.intFrom16Bit(header[5], header[6]);
                payload = payloadSize > 0 ? adapter.read(payloadSize) : null;
                int receivedPayloadChecksum = BitUtil.intFrom16Bit(header[7], header[8]);
                int actualPayloadChecksum = createChecksum(payload);
                if (receivedPayloadChecksum != actualPayloadChecksum) {
                    // payload invalid, stop processing packet
                    return;
                }
            }
        }
        boolean success = false;
        try {
            IncomingPacket packet = factory.create(header[0], header[2], header[3], header[4]);
            packet.process(payload);
            success = true;
        } finally {
            if (BitUtil.isFlagSet(header[4], 0)) { // ack required check - flags index 0 is set to 1
                byte flags = (byte) (success ? BitUtil.createFlags() : BitUtil.createFlags(1));
                send(header[2], new Packet(Packet.TYPE_ACK, (byte) 1, flags));
            }
        }

    }

    static int createChecksum(byte... data) {
        int crc = 0x0000;
        if (data != null) {
            int polynomial = 0xA001;
            for (byte b : data) {
                crc ^= (b & 0xFF);
                for (int i = 0; i < 8; i++) {
                    if ((crc & 0x0001) != 0) {
                        crc = (crc >>> 1) ^ polynomial;
                    } else {
                        crc >>>= 1;
                    }
                }
            }
        }
        return crc & 0xFFFF;
    }

    private void send(byte messageId, Packet packet, byte... payload) throws NetworkException {
        int payloadChecksum = createChecksum(payload);
        int payloadLength = payload != null ? payload.length : 0;
        byte[] header = {
                protocolVersion,
                messageId,
                packet.getType(),
                packet.getVersion(),
                packet.getFlags(),
                BitUtil.getByteAt(payloadLength, 0),
                BitUtil.getByteAt(payloadLength, 1),
                BitUtil.getByteAt(payloadChecksum, 0),
                BitUtil.getByteAt(payloadChecksum, 1)
        };
        int headerChecksum = createChecksum(header);
        boolean requiresAck = packet.isFlagSet(0);
        boolean isSendCompleted = !requiresAck;
        int sendAttempts = 0;
        do {
            synchronized (adapter) {
                adapter.write(START_BYTE_LOW, START_BYTE_HIGH);
                adapter.write(header);
                adapter.write(BitUtil.getByteAt(headerChecksum, 0), BitUtil.getByteAt(headerChecksum, 1));
                if (payloadLength > 0) {
                    adapter.write(payload);
                }
                sendAttempts++;
            }
            if (requiresAck) {

                while (isAckPending(messageId)) {
                    try {
                        synchronized (adapter) {
                            if (!isAckPending(messageId)) {
                                break;
                            }
                            receive();
                        }
                    } catch (NetworkException | ProcessingException error) {
                        // failed to receive ack, go to resend
                        break;
                    }
                }
                isSendCompleted = !isAckPending(messageId);
            }
        } while (!isSendCompleted && sendAttempts < maxSendAttempts);
        if (!isSendCompleted) {
            throw new SendTimeoutException("Failed to receive ack after max send attempts reached");
        } else if (requiresAck && isAckFailed(messageId)) {
            throw new PacketFailureException("Recipient couldn't process packet");
        }
    }

    private synchronized void markAckPending(byte messageId, boolean pending, boolean failed) {
        int diff = (messageCount - messageId) & 0xFF;
        if (diff == 0 || diff > 32) {
            return;
        }
        int stateIndex = diff - 1;
        pendingStates = BitUtil.setFlag(pendingStates, stateIndex, pending);
        failureStates = BitUtil.setFlag(failureStates, stateIndex, failed);
    }

    private synchronized boolean isAckPending(byte messageId) throws NetworkException {
        return checkState(pendingStates, messageId);
    }

    private synchronized boolean isAckFailed(byte messageId) throws NetworkException {
        return checkState(failureStates, messageId);
    }

    private synchronized boolean checkState(int states, byte messageId) throws NetworkException {
        int diff = (messageCount - messageId) & 0xFF;
        if (diff == 0 || diff > 32) {
            throw new SendTimeoutException("Message outside of state window");
        }
        return BitUtil.isFlagSet(states, diff - 1);
    }

}
