package org.cade.rpc.compress;


public class NoneCompression implements Compression{
    @Override
    public String getName() {
        return "none";
    }

    @Override
    public int code() {
        return 0;
    }

    @Override
    public byte[] compress(byte[] data) {
        return data;
    }

    @Override
    public byte[] decompress(byte[] data) {
        return data;
    }
}
