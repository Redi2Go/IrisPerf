package at.redi.irisperf.client.buffer;

public interface MemoryOwner {
    void allocate(GlMemoryManager memoryManager);
    void free(GlMemoryManager memoryManager);
    boolean update(GlMemoryManager memoryManager);
    void afterUpload();
    int getSize();
    MemoryRegion getMemory();
}