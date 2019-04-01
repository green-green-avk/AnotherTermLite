package green_green_avk.anotherterm.utils;

public interface BytesSink {
    void feed(byte[] v);

    void invalidateSink();
}
