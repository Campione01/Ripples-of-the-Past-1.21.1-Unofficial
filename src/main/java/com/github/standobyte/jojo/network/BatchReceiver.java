package com.github.standobyte.jojo.network;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class BatchReceiver {
	private OptionalInt batchesCount = OptionalInt.empty();
	private final Int2ObjectMap<ByteBuffer> batchesReceived = new Int2ObjectArrayMap<>();

	public ByteBuffer receive(BatchSender.Batch receivedBatch) {
		if (receivedBatch.batchStart != 0 || receivedBatch.batchSize != receivedBatch.dataBatch.length) {
			throw new IllegalArgumentException("Only full packet batches can be received");
		}
		return receiveBatch(receivedBatch.dataBatch, receivedBatch.batchIndex, receivedBatch.isLastBatch);
	}

	public ByteBuffer receiveBatch(byte[] batch, int batchIndex, boolean isLastBatch) {
		if (batchesReceived.containsKey(batchIndex)) {
			throw new IllegalStateException("Already received photo batch " + batchIndex);
		}
		if (isLastBatch) {
			if (batchesCount.isPresent()) {
				throw new IllegalStateException("Photo batch count was already set");
			}
			batchesCount = OptionalInt.of(batchIndex + 1);
		}
		else if (batchesCount.isPresent() && batchIndex >= batchesCount.getAsInt()) {
			throw new IllegalStateException("Received photo batch past the last batch");
		}

		batchesReceived.put(batchIndex, ByteBuffer.wrap(batch));
		if (batchesCount.isPresent() && batchesReceived.size() == batchesCount.getAsInt()) {
			int fullSize = batchesReceived.values().stream().mapToInt(ByteBuffer::capacity).sum();
			ByteBuffer full = ByteBuffer.allocate(fullSize);
			for (int i = 0; i < batchesCount.getAsInt(); i++) {
				full.put(batchesReceived.get(i));
			}
			full.flip();
			return full;
		}
		return null;
	}

	public static byte[] byteBufferToArray(ByteBuffer buf) {
		buf.rewind();
		byte[] arr = new byte[buf.remaining()];
		buf.get(arr);
		return arr;
	}
}
