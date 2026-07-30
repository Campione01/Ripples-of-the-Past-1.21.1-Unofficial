package com.github.standobyte.jojo.network;

import java.util.Objects;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;

public abstract class BatchSender {
	public static final int DEFAULT_MAX_PAYLOAD_SIZE = 32767 - 128;
	public static final int MAX_DATA_SIZE = 1024 * 1024;
	public static final int MAX_BATCH_COUNT = (MAX_DATA_SIZE - 1) / DEFAULT_MAX_PAYLOAD_SIZE + 1;

	protected final byte[] data;
	protected final int maxPayloadSize;
	protected boolean finishedSending;
	private final int batchesCount;
	private int batchToSend;
	private int dataIndex;

	public BatchSender(byte[] data) {
		this(data, DEFAULT_MAX_PAYLOAD_SIZE);
	}

	public BatchSender(byte[] data, int maxPayloadSize) {
		this.data = Objects.requireNonNull(data);
		if (maxPayloadSize <= 0 || maxPayloadSize > DEFAULT_MAX_PAYLOAD_SIZE) {
			throw new IllegalArgumentException("Invalid batch payload size " + maxPayloadSize);
		}
		if (data.length > MAX_DATA_SIZE) {
			throw new IllegalArgumentException("Batch data exceeds " + MAX_DATA_SIZE + " bytes");
		}
		this.maxPayloadSize = maxPayloadSize;
		int batchCount = data.length == 0 ? 1 : (data.length - 1) / maxPayloadSize + 1;
		if (batchCount > MAX_BATCH_COUNT) {
			throw new IllegalArgumentException("Batch count exceeds " + MAX_BATCH_COUNT);
		}
		this.batchesCount = batchCount;
	}

	public void sendAll() {
		while (!finishedSending) {
			sendNext();
		}
	}

	public void sendNext() {
		int batchSize = Math.min(maxPayloadSize, data.length - dataIndex);
		Batch batch = new Batch(batchToSend, batchToSend == batchesCount - 1, data, dataIndex, batchSize);
		sendBatch(batch);
		dataIndex += batchSize;
		batchToSend++;
		finishedSending = batchToSend == batchesCount;
	}

	public boolean finishedSending() {
		return finishedSending;
	}

	protected abstract void sendBatch(Batch batch);

	public static class Batch {
		public final int batchIndex;
		public final boolean isLastBatch;
		public final byte[] dataBatch;
		public final int batchStart;
		public final int batchSize;

		public Batch(int batchIndex, boolean isLastBatch, byte[] dataBatch) {
			this(batchIndex, isLastBatch, dataBatch, 0, dataBatch.length);
		}

		public Batch(int batchIndex, boolean isLastBatch, byte[] dataBatch, int batchStart, int batchSize) {
			Objects.requireNonNull(dataBatch);
			if (batchIndex < 0 || batchIndex >= MAX_BATCH_COUNT) {
				throw new IllegalArgumentException("Invalid batch index " + batchIndex);
			}
			if (batchSize < 0 || batchSize > DEFAULT_MAX_PAYLOAD_SIZE
					|| batchStart < 0 || batchStart > dataBatch.length - batchSize) {
				throw new IllegalArgumentException("Invalid batch data range");
			}
			this.batchIndex = batchIndex;
			this.isLastBatch = isLastBatch;
			this.dataBatch = dataBatch;
			this.batchStart = batchStart;
			this.batchSize = batchSize;
		}

		public void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(batchIndex);
			buf.writeBoolean(isLastBatch);
			buf.writeInt(batchSize);
			buf.writeBytes(dataBatch, batchStart, batchSize);
		}

		public static Batch read(RegistryFriendlyByteBuf buf) {
			int batchIndex = buf.readVarInt();
			boolean isLastBatch = buf.readBoolean();
			int batchSize = buf.readInt();
			validateDecodedBatch(batchIndex, batchSize, buf.readableBytes());
			byte[] data = new byte[batchSize];
			buf.readBytes(data);
			return new Batch(batchIndex, isLastBatch, data);
		}

		static void validateDecodedBatch(int batchIndex, int batchSize, int readableBytes) {
			if (batchIndex < 0 || batchIndex >= MAX_BATCH_COUNT) {
				throw new DecoderException("Invalid batch index " + batchIndex);
			}
			if (batchSize < 0 || batchSize > DEFAULT_MAX_PAYLOAD_SIZE) {
				throw new DecoderException("Invalid batch size " + batchSize);
			}
			if (batchSize > readableBytes) {
				throw new DecoderException("Truncated batch: expected " + batchSize
						+ " bytes, only " + readableBytes + " remain");
			}
		}
	}
}
