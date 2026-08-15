package com.github.standobyte.jojo.network;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.github.standobyte.jojo.util.functions_network.NetworkUtil;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

public final class DeferredPacketDataSmokeTest {
	private DeferredPacketDataSmokeTest() {}

	public static void run() {
		testIndependentByteCopyAndLocalRelease();
		testSemanticByteBounds();
		testDeferredSourceContracts();
	}

	private static void testSemanticByteBounds() {
		testBoundedCopy(
				NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
				"ability extra input");
		testBoundedCopy(
				NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
				"entity action");
		testOutboundBound(
				NetworkPayloadValidation.MAX_ABILITY_EXTRA_BYTES,
				"ability extra input");
		testOutboundBound(
				NetworkPayloadValidation.MAX_ENTITY_ACTION_BYTES,
				"entity action");
	}

	private static void testOutboundBound(int maxBytes, String description) {
		check(NetworkPayloadValidation.requireOutboundByteLength(
				maxBytes, maxBytes, description) == maxBytes,
				"the exact outbound semantic byte limit must be accepted");
		expectThrows(IllegalArgumentException.class,
				() -> NetworkPayloadValidation.requireOutboundByteLength(
						maxBytes + 1, maxBytes, description),
				"oversized outbound semantic bytes must fail before copying");
	}

	private static void testBoundedCopy(int maxBytes, String description) {
		FriendlyByteBuf exact = new FriendlyByteBuf(
				Unpooled.buffer(maxBytes));
		try {
			exact.writeZero(maxBytes);
			byte[] copied = NetworkUtil.extraPacketDataBytes(
					exact, maxBytes, description);
			check(copied.length == maxBytes && exact.readableBytes() == 0,
					"exact semantic byte limit must be copied and consumed");
		}
		finally {
			exact.release();
		}

		FriendlyByteBuf oversized = new FriendlyByteBuf(
				Unpooled.buffer(maxBytes + 1));
		try {
			oversized.writeZero(maxBytes + 1);
			expectThrows(DecoderException.class,
					() -> NetworkUtil.extraPacketDataBytes(
							oversized, maxBytes, description),
					"oversized deferred bytes must fail before allocation");
			check(oversized.readableBytes() == maxBytes + 1,
					"oversized deferred input must fail before consuming bytes");
		}
		finally {
			oversized.release();
		}
	}

	private static void testIndependentByteCopyAndLocalRelease() {
		FriendlyByteBuf decoderBuffer = new FriendlyByteBuf(Unpooled.buffer());
		decoderBuffer.writeBytes(new byte[] {3, 1, 4, 1, 5});
		byte[] deferred = NetworkUtil.extraPacketDataBytes(decoderBuffer);
		check(decoderBuffer.readableBytes() == 0,
				"deferred byte copy must consume the decoder remainder");
		decoderBuffer.release();
		check(decoderBuffer.refCnt() == 0,
				"decoder buffer must be independently releasable");
		check(Arrays.equals(deferred, new byte[] {3, 1, 4, 1, 5}),
				"deferred bytes must survive decoder-buffer release");

		FriendlyByteBuf local = new FriendlyByteBuf(
				Unpooled.wrappedBuffer(deferred));
		try {
			check(local.readByte() == 3 && local.readableBytes() == 4,
					"temporary deferred-data buffer must remain readable");
		}
		finally {
			local.release();
		}
		check(local.refCnt() == 0,
				"temporary deferred-data buffer must be released locally");
	}

	private static void testDeferredSourceContracts() {
		String clientInput = read(
				"src/main/java/com/github/standobyte/jojo/network/c2s/ClAbilityInputPacket.java");
		String replay = read(
				"src/main/java/com/github/standobyte/jojo/network/s2c/TrAbilityUsePacket.java");
		String actionQueue = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/entityaction/netcode/ClientEntityActionSyncQueue.java");
		String inputBuffer = read(
				"src/main/java/com/github/standobyte/jojo/powersystem/ability/input/ActionInputBuffer.java");
		String inputHandler = read(
				"src/main/java/com/github/standobyte/jojo/client/input/InputHandler.java");

		check(clientInput.contains("private final byte[] extraData")
				&& replay.contains("private final byte[] extraData"),
				"cross-thread ability payload state must be immutable byte arrays");
		check(!clientInput.contains("FriendlyByteBuf extraData")
				&& !replay.contains("RegistryFriendlyByteBuf extraData"),
				"ability packet instances must not own reference-counted buffers");
		check(clientInput.contains("extraInput.release()")
				&& replay.contains("extraInput.release()")
				&& inputBuffer.contains("replayInput.release()"),
				"all temporary ability input buffers must release in local scope");
		check(actionQueue.contains("PendingAction extends Pending<byte[]>")
				&& actionQueue.contains("actionData.clone()")
				&& actionQueue.contains("input.release()"),
				"queued action payloads must be copied bytes with local decode release");
		check(clientInput.contains("MAX_ABILITY_EXTRA_BYTES")
				&& replay.contains("MAX_ABILITY_EXTRA_BYTES")
				&& inputHandler.contains("MAX_ABILITY_EXTRA_BYTES")
				&& actionQueue.contains("MAX_ENTITY_ACTION_BYTES"),
				"ability and action deferred payloads must use semantic byte limits");
		int replayTry = inputBuffer.indexOf("try {");
		int replayAllocation = inputBuffer.indexOf(
				"replayInput = new FriendlyByteBuf", replayTry);
		int replayUse = inputBuffer.indexOf(
				"ability.onKeyPress(", replayAllocation);
		int replayClear = inputBuffer.indexOf(
				"clearFailedBufferedInput", replayUse);
		int replayRelease = inputBuffer.indexOf(
				"replayInput.release()", replayClear);
		check(replayTry >= 0 && replayAllocation > replayTry
				&& replayUse > replayAllocation && replayClear > replayUse
				&& replayRelease > replayClear,
				"buffer replay allocation, population, use, cleanup, and release must share one scope");
	}

	private static void expectThrows(
			Class<? extends Throwable> expected,
			Runnable action,
			String message) {
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

	private static String read(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
