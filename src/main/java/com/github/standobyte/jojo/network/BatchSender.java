package com.github.standobyte.jojo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;

public abstract class BatchSender {
	protected final byte[] data;
	protected final int maxPayloadSize;
	protected boolean finishedSending;
	private final int batchesCount;
	private int batchToSend;
	private int dataIndex;

	public BatchSender(byte[] data) {
		this(data, 32767 - 128);
	}

	public BatchSender(byte[] data, int maxPayloadSize) {
		this.data = data;
		this.maxPayloadSize = maxPayloadSize;
		this.batchesCount = data.length == 0 ? 1 : (data.length - 1) / maxPayloadSize + 1;
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
			byte[] data = new byte[batchSize];
			buf.readBytes(data);
			return new Batch(batchIndex, isLastBatch, data);
		}
	}
}
