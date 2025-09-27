package at.redi.irisperf.client.buffer;

public class SimpleMemoryOwner implements MemoryOwner {
    private MemoryRegion memory;
    private final int size;

    public SimpleMemoryOwner(GlMemoryManager memoryManager, int size) {
        this.size = size;

        allocate(memoryManager);
    }

    @Override
    public void allocate(GlMemoryManager memoryManager) {
        if (memory != null)
            free(memoryManager);

        memory = memoryManager.allocate(size);
    }

    @Override
    public void free(GlMemoryManager memoryManager) {
        memoryManager.free(memory);
        memory = null;
    }

    @Override
    public boolean update(GlMemoryManager memoryManager) {
        return false;
    }

    @Override
    public void afterUpload() {
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public MemoryRegion getMemory() {
        return memory;
    }
}
