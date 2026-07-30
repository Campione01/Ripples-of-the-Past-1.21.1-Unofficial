package com.github.standobyte.jojo.network;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class BatchReceiver {
	private OptionalInt batchesCount = OptionalInt.empty();
	private final Int2ObjectMap<ByteBuffer> batchesReceived = new Int2ObjectArrayMap<>();
	private int receivedBytes;

	public ByteBuffer receive(BatchSender.Batch receivedBatch) {
		if (receivedBatch.batchStart != 0 || receivedBatch.batchSize != receivedBatch.dataBatch.length) {
			throw new IllegalArgumentException("Only full packet batches can be received");
		}
		return receiveBatch(receivedBatch.dataBatch, receivedBatch.batchIndex, receivedBatch.isLastBatch);
	}

	public ByteBuffer receiveBatch(byte[] batch, int batchIndex, boolean isLastBatch) {
		if (batchIndex < 0 || batchIndex >= BatchSender.MAX_BATCH_COUNT) {
			throw new IllegalArgumentException("Invalid batch index " + batchIndex);
		}
		if (batch.length > BatchSender.DEFAULT_MAX_PAYLOAD_SIZE) {
			throw new IllegalArgumentException("Batch exceeds "
					+ BatchSender.DEFAULT_MAX_PAYLOAD_SIZE + " bytes");
		}
		if (batchesReceived.containsKey(batchIndex)) {
			throw new IllegalStateException("Already received batch " + batchIndex);
		}
		if (isLastBatch) {
			if (batchesCount.isPresent()) {
				throw new IllegalStateException("Batch count was already set");
			}
			int count = batchIndex + 1;
			for (int receivedIndex : batchesReceived.keySet()) {
				if (receivedIndex >= count) {
					throw new IllegalStateException("Received batch past the declared last batch");
				}
			}
			batchesCount = OptionalInt.of(count);
		}
		else if (batchesCount.isPresent() && batchIndex >= batchesCount.getAsInt()) {
			throw new IllegalStateException("Received batch past the last batch");
		}

		if (batch.length > BatchSender.MAX_DATA_SIZE - receivedBytes) {
			throw new IllegalStateException("Batch data exceeds "
					+ BatchSender.MAX_DATA_SIZE + " bytes");
		}

		batchesReceived.put(batchIndex, ByteBuffer.wrap(batch));
		receivedBytes += batch.length;
		if (batchesCount.isPresent() && batchesReceived.size() == batchesCount.getAsInt()) {
			ByteBuffer full = ByteBuffer.allocate(receivedBytes);
			for (int i = 0; i < batchesCount.getAsInt(); i++) {
				ByteBuffer received = batchesReceived.get(i);
				if (received == null) {
					throw new IllegalStateException("Missing batch " + i);
				}
				full.put(received);
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
