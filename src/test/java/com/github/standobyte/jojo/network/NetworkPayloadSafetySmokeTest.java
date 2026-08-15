package com.github.standobyte.jojo.network;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.standobyte.jojo.api.network.AbilityNetworkDiagnosticsSmokeTest;
import com.github.standobyte.jojo.client.polaroid.PhotoCacheContractSmokeTest;
import com.github.standobyte.jojo.item.polaroid.PhotoUploadContractSmokeTest;
import com.github.standobyte.jojo.network.c2s.AbilityInputFailureLogLimiterSmokeTest;
import com.github.standobyte.jojo.network.c2s.ServerboundPayloadContractSmokeTest;
import com.github.standobyte.jojo.network.s2c.ClientboundPayloadContractSmokeTest;
import com.github.standobyte.jojo.network.s2c.TrPowerDataPacketSmokeTest;
import com.github.standobyte.jojo.powersystem.ability.input.AbilityInputTransactionSmokeTest;
import com.github.standobyte.jojo.powersystem.entityaction.ActionGenerationSequenceSmokeTest;
import com.github.standobyte.jojo.powersystem.entityaction.netcode.ClientEntityActionSyncQueueSmokeTest;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.packet.ExtendedContainerClickContractSmokeTest;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob.MobControlPayloadContractSmokeTest;
import com.github.standobyte.jojo.subsystems.entity_useitem.StandItemUseProtocolSmokeTest;
import com.github.standobyte.jojo.subsystems.movement_input_sync.MovementInputPayloadContractSmokeTest;
import com.github.standobyte.jojo.util.functions_network.NetworkUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPayloadContractSmokeTest;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.phys.Vec3;

public final class NetworkPayloadSafetySmokeTest {
	private NetworkPayloadSafetySmokeTest() {}

	public static void main(String[] args) {
		NetworkProtocolNegotiationSmokeTest.run();
		testSharedDecodeBounds();
		testBoundedCollectionCodec();
		testBatchTransport();
		PhotoUploadContractSmokeTest.run();
		PhotoCacheContractSmokeTest.run();
		ServerboundPayloadContractSmokeTest.run();
		ClientboundPayloadContractSmokeTest.run();
		TrPowerDataPacketSmokeTest.run();
		MovementInputPayloadContractSmokeTest.run();
		MobControlPayloadContractSmokeTest.run();
		ExtendedContainerClickContractSmokeTest.run();
		HamonPayloadContractSmokeTest.run();
		AbilityNetworkDiagnosticsSmokeTest.run();
		AbilityInputFailureLogLimiterSmokeTest.run();
		ClientNetworkFailureLogLimiterSmokeTest.run();
		DeferredPacketDataSmokeTest.run();
		AbilityInputTransactionSmokeTest.run();
		ActionGenerationSequenceSmokeTest.run();
		ClientEntityActionSyncQueueSmokeTest.run();
		StandItemUseProtocolSmokeTest.run();
	}

	private static void testBoundedCollectionCodec() {
		FriendlyByteBuf valid = new FriendlyByteBuf(Unpooled.buffer());
		try {
			NetworkUtil.writeCollection(
					valid, List.of(3, 7), ByteBufCodecs.VAR_INT, 2);
			List<Integer> decoded = NetworkUtil.readCollection(
					valid, ByteBufCodecs.VAR_INT, 2);
			check(decoded.equals(List.of(3, 7)),
					"bounded network collection must round-trip valid entries");
		}
		finally {
			valid.release();
		}

		FriendlyByteBuf outboundTooLarge = new FriendlyByteBuf(Unpooled.buffer());
		try {
			expectThrows(IllegalArgumentException.class,
					() -> NetworkUtil.writeCollection(
							outboundTooLarge, List.of(1, 2, 3),
							ByteBufCodecs.VAR_INT, 2),
					"oversized collection must be rejected before encoding");
		}
		finally {
			outboundTooLarge.release();
		}

		FriendlyByteBuf inboundTooLarge = new FriendlyByteBuf(Unpooled.buffer());
		try {
			inboundTooLarge.writeInt(3);
			expectThrows(DecoderException.class,
					() -> NetworkUtil.readCollection(
							inboundTooLarge, ByteBufCodecs.VAR_INT, 2),
					"oversized collection header must be rejected before iteration");
		}
		finally {
			inboundTooLarge.release();
		}
	}

	private static void testSharedDecodeBounds() {
		check(NetworkPayloadValidation.requireCollectionSize(
				0, 3, "test") == 0, "zero collection size must be accepted");
		check(NetworkPayloadValidation.requireCollectionSize(
				3, 3, "test") == 3, "maximum collection size must be accepted");
		expectThrows(DecoderException.class,
				() -> NetworkPayloadValidation.requireCollectionSize(-1, 3, "test"),
				"negative collection size must be rejected");
		expectThrows(DecoderException.class,
				() -> NetworkPayloadValidation.requireCollectionSize(4, 3, "test"),
				"oversized collection must be rejected");
		expectThrows(DecoderException.class,
				() -> NetworkPayloadValidation.requireCollectionSize(3, 3, 2, "test"),
				"collection count larger than remaining bytes must be rejected");

		Vec3 finite = new Vec3(1.0D, -2.0D, 3.0D);
		check(NetworkPayloadValidation.requireFinite(finite, "test") == finite,
				"finite position must be preserved");
		expectThrows(DecoderException.class,
				() -> NetworkPayloadValidation.requireFinite(
						new Vec3(Double.NaN, 0.0D, 0.0D), "test"),
				"NaN position must be rejected");
		expectThrows(DecoderException.class,
				() -> NetworkPayloadValidation.requireFinite(
						new Vec3(0.0D, Double.POSITIVE_INFINITY, 0.0D), "test"),
				"infinite position must be rejected");

		check(NetworkPayloadValidation.requireOutboundCollectionSize(
				3, 3, "test") == 3, "maximum outbound collection must be accepted");
		expectThrows(IllegalArgumentException.class,
				() -> NetworkPayloadValidation.requireOutboundCollectionSize(4, 3, "test"),
				"oversized outbound collection must be rejected before encoding");
		check(NetworkPayloadValidation.requireUtfLength(
				"valid", 5, "test").equals("valid"),
				"bounded outbound string must be preserved");
		expectThrows(IllegalArgumentException.class,
				() -> NetworkPayloadValidation.requireUtfLength("toolong", 5, "test"),
				"oversized outbound string must be rejected before encoding");
	}

	private static void testBatchTransport() {
		int dataSize = BatchSender.DEFAULT_MAX_PAYLOAD_SIZE * 2 + 17;
		byte[] storedPhoto = new byte[dataSize];
		for (int i = 0; i < storedPhoto.length; i++) {
			storedPhoto[i] = (byte) (i * 31 + 7);
		}

		CollectingBatchSender sender = new CollectingBatchSender(storedPhoto);
		sender.sendAll();
		check(sender.finishedSending(), "batch sender must finish");
		check(sender.batches.size() == 3,
				"legacy-sized stored photo must split into the expected batches");

		BatchReceiver receiver = new BatchReceiver();
		ByteBuffer result = receiver.receive(sender.batches.get(2));
		check(result == null, "out-of-order last batch must wait for earlier batches");
		result = receiver.receive(sender.batches.get(0));
		check(result == null, "partial photo must not be published");
		result = receiver.receive(sender.batches.get(1));
		check(result != null, "complete out-of-order photo must be reassembled");
		check(Arrays.equals(storedPhoto, BatchReceiver.byteBufferToArray(result)),
				"normal stored photo bytes must survive batch transfer unchanged");

		CollectingBatchSender emptySender = new CollectingBatchSender(new byte[0]);
		emptySender.sendAll();
		ByteBuffer empty = new BatchReceiver().receive(emptySender.batches.get(0));
		check(empty != null && empty.remaining() == 0,
				"empty payload must remain a valid single-batch transfer");

		BatchSender.Batch.validateDecodedBatch(
				0, BatchSender.DEFAULT_MAX_PAYLOAD_SIZE,
				BatchSender.DEFAULT_MAX_PAYLOAD_SIZE);
		expectThrows(DecoderException.class,
				() -> BatchSender.Batch.validateDecodedBatch(-1, 0, 0),
				"negative batch index must be rejected before allocation");
		expectThrows(DecoderException.class,
				() -> BatchSender.Batch.validateDecodedBatch(
						0, BatchSender.DEFAULT_MAX_PAYLOAD_SIZE + 1,
						BatchSender.DEFAULT_MAX_PAYLOAD_SIZE + 1),
				"oversized batch must be rejected before allocation");
		expectThrows(DecoderException.class,
				() -> BatchSender.Batch.validateDecodedBatch(0, 8, 7),
				"truncated batch must be rejected before allocation");

		BatchReceiver duplicateReceiver = new BatchReceiver();
		BatchSender.Batch first = new BatchSender.Batch(0, false, new byte[] {1});
		duplicateReceiver.receive(first);
		expectThrows(IllegalStateException.class,
				() -> duplicateReceiver.receive(first),
				"duplicate batch index must be rejected");

		BatchReceiver impossibleOrder = new BatchReceiver();
		impossibleOrder.receive(new BatchSender.Batch(1, false, new byte[] {1}));
		expectThrows(IllegalStateException.class,
				() -> impossibleOrder.receive(
						new BatchSender.Batch(0, true, new byte[] {2})),
				"last batch declaration before an existing higher index must be rejected");

		BatchReceiver totalLimit = new BatchReceiver();
		byte[] fullBatch = new byte[BatchSender.DEFAULT_MAX_PAYLOAD_SIZE];
		for (int i = 0; i < 32; i++) {
			totalLimit.receiveBatch(fullBatch, i, false);
		}
		expectThrows(IllegalStateException.class,
				() -> totalLimit.receiveBatch(new byte[5000], 32, true),
				"aggregate batch data above the photo limit must be rejected");
		expectThrows(IllegalArgumentException.class,
				() -> new CollectingBatchSender(
						new byte[BatchSender.MAX_DATA_SIZE + 1]),
				"sender must reject payloads above the receiver limit");
		expectThrows(IllegalArgumentException.class,
				() -> new CollectingBatchSender(new byte[34], 1),
				"sender must reject a custom split that exceeds the receiver batch-count limit");
	}

	private static final class CollectingBatchSender extends BatchSender {
		private final List<Batch> batches = new ArrayList<>();

		private CollectingBatchSender(byte[] data) {
			super(data);
		}

		private CollectingBatchSender(byte[] data, int maxPayloadSize) {
			super(data, maxPayloadSize);
		}

		@Override
		protected void sendBatch(Batch batch) {
			byte[] packetData = Arrays.copyOfRange(
					batch.dataBatch, batch.batchStart, batch.batchStart + batch.batchSize);
			batches.add(new Batch(batch.batchIndex, batch.isLastBatch, packetData));
		}
	}

	private static void expectThrows(
			Class<? extends Throwable> expected, Runnable action, String message) {
		try {
			action.run();
		}
		catch (Throwable actual) {
			if (expected.isInstance(actual)) {
				return;
			}
			throw new AssertionError(message, actual);
		}
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
