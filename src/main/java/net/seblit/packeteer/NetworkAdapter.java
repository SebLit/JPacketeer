package net.seblit.packeteer;

import org.jetbrains.annotations.NotNull;

public interface NetworkAdapter {

    /**
     * Reads the requested amount of bytes from the source of this adapter
     * @param count The desired amount of bytes
     * @return a byte[] of the same size as the requested byte amount containing the read bytes
     * @throws NetworkException if any error occur while reading
     * */
    byte @NotNull [] read(int count) throws NetworkException;

    /**
     * Writes the provided bytes to the output of this adapter
     * @param data A byte[] containing the data to be written. If empty, this call is to be ignored
     * @throws NetworkException if any error occur while writing
     * */
    void write(byte... data) throws NetworkException;

}
