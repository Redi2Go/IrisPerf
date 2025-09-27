package at.redi.irisperf.client.buffer;

import java.nio.ByteBuffer;

public class MemoryRegion {
    private final ByteBuffer buffer;

    public final int begin, end;

    public boolean allocated = true;

    MemoryRegion(ByteBuffer buffer, int begin, int end) {
        this.buffer = buffer;
        this.begin = begin;
        this.end = end;
    }

    public ByteBuffer getBuffer() {
//        WorldRegistry.INSTANCE.ensureWorldThread();

        return buffer.duplicate().order(buffer.order());
    }
}
