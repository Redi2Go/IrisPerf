package at.redi.irisperf.client.buffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;

import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class GlMemoryManager implements Destructable {
    private int id;
    private final boolean staticData;
    private final String name;

    private final ByteBuffer buffer;
    public int index = 0;

    private int uploadBatchSize = Integer.MAX_VALUE;

    private final Deque<MemoryOwner> uploadQueue = new LinkedList<>();
    public final Queue<MemoryRegion> unusedBuffers = new LinkedList<>();

    public GlMemoryManager(String name, int byteSize, boolean staticData) {
        this.name = name;
        this.staticData = staticData;
        this.buffer = BufferUtils.createByteBuffer(byteSize);
    }

    public MemoryRegion allocate(int byteSize) {
        Iterator<MemoryRegion> memoryIterator = unusedBuffers.iterator();
        MemoryRegion region;
        while (memoryIterator.hasNext()) {
            region = memoryIterator.next();

            if (region.end - region.begin == byteSize) {
                memoryIterator.remove();
                region.allocated = true;
                return region;
            }
        }

        int begin = index;
        int end = index + byteSize;

        index += byteSize;

        return new MemoryRegion(buffer.slice(begin, end - begin).order(buffer.order()), begin, end);
    }

    public void bind(int program, int blockIndex, int bindingPointIndex) {
        ensureAllocated();

        GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, bindingPointIndex, id);

        GL43.glShaderStorageBlockBinding(program, blockIndex, bindingPointIndex);
    }

    public int findInProgram(int program) {
        ensureAllocated();

        return GL43.glGetProgramResourceIndex(program, GL43.GL_SHADER_STORAGE_BLOCK, name);
    }

    public boolean upload() {
        ensureAllocated();

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);

        for (int i = 0; i < uploadBatchSize && !uploadQueue.isEmpty(); i++) {
            MemoryOwner memoryOwner = uploadQueue.poll();
            MemoryRegion memoryRegion = memoryOwner.getMemory();

            GL15.glBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, memoryRegion.begin, memoryRegion.getBuffer());

            memoryOwner.afterUpload();
        }

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);

        return uploadQueue.isEmpty();
    }

    public void download(Consumer<ByteBuffer> downloadContext) {
        ensureAllocated();

        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);

        ByteBuffer downloadedBuffer = GL15.glMapBuffer(GL43.GL_SHADER_STORAGE_BUFFER, GL15.GL_READ_WRITE, null);
        downloadContext.accept(downloadedBuffer);

        GL15.glUnmapBuffer(GL43.GL_SHADER_STORAGE_BUFFER);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void queueUpload(MemoryOwner memoryOwner) {
        uploadQueue.addLast(memoryOwner);
    }

    public void queueUploadPriority(MemoryOwner memoryOwner) {
        uploadQueue.addFirst(memoryOwner);
    }

    public void free(MemoryRegion memoryRegion) {
        memoryRegion.allocated = false;
        unusedBuffers.add(memoryRegion);
    }

    private void ensureAllocated() {
        if (id != 0)
            return;

        id = GL15.glGenBuffers();
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, buffer.capacity(), staticData ? GL15.GL_STATIC_DRAW : GL15.GL_DYNAMIC_DRAW);
        GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void setUploadBatchSize(int uploadBatchSize) {
        this.uploadBatchSize = uploadBatchSize;
    }

    public int getCapacity() {
        return buffer.capacity();
    }

    @Override
    public void free() {
        if (id == 0)
            return;

        GL15.glDeleteBuffers(id);
    }
}
