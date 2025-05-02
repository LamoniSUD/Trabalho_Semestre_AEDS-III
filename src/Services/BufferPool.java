package Utils;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

public class BufferPool {
    private final BlockingQueue<ByteBuffer> pool;
    private final int bufferSize;

    public BufferPool(int capacity, int bufferSize) {
        this.pool = new ArrayBlockingQueue<>(capacity);
        this.bufferSize = bufferSize;
        initializePool();
    }

    private void initializePool() {
        for (int i = 0; i < pool.remainingCapacity(); i++) {
            pool.offer(ByteBuffer.allocateDirect(bufferSize));
        }
    }

    public ByteBuffer borrowBuffer() throws InterruptedException {
        ByteBuffer buffer = pool.take();
        buffer.clear(); // Prepara para reuso
        return buffer;
    }

    public void returnBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            pool.offer(buffer);
        }
    }

    public int availableBuffers() {
        return pool.size();
    }
}