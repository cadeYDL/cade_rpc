package org.cade.rpc.compress;

public class NoneCompression implements Compression{
    @Override
    public byte[] compress(byte[] data) {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data) {
        return data;
    }
}
