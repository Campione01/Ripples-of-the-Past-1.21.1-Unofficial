package com.github.standobyte.jojo.subsystems.entity_useitem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.standobyte.jojo.client.input.InputHandler;
import com.github.standobyte.jojo.powersystem.ability.input.InputKeyId;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState.InputGenerationTracker;
import com.github.standobyte.jojo.subsystems.entity_useitem.VanillaItemUseAsAction.ItemUseReleaseGuard;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class StandItemUseProtocolSmokeTest {
	private StandItemUseProtocolSmokeTest() {}

	public static void run() {
		verifyGenerationOrderingAndExhaustion();
		verifyAdmissionBeforeGenerationConsumption();
		verifyFixedRmbAuthorityAndActiveConflict();
		verifyHandOrderingAndFallthrough();
		verifyReplacementReleaseIsOneShot();
		verifyDisconnectCleanup();
		verifyPacketCodecRoundTrip();
		verifyProductionWiring();
	}

	private static void verifyGenerationOrderingAndExhaustion() {
		short keyId = 4;
		InputGenerationTracker generations = new InputGenerationTracker();
		check(generations.acceptNetworkPressGeneration(keyId, 2L),
				"a newer Stand item-use generation was rejected");
		check(!generations.acceptNetworkPressGeneration(keyId, 1L),
				"a stale Stand item-use generation was accepted");
		check(!generations.acceptNetworkPressGeneration(keyId, 2L),
				"a duplicate Stand item-use generation was accepted");
		check(generations.acceptNetworkPressGeneration(keyId, 3L)
				&& generations.latestInputGeneration(keyId) == 3L,
				"the next Stand item-use generation did not advance");

		InputGenerationTracker exhausted = new InputGenerationTracker();
		exhausted.observeInputGeneration(keyId, Long.MAX_VALUE);
		expectThrows(IllegalStateException.class,
				() -> exhausted.nextInputGeneration(keyId),
				"generation exhaustion must fail closed");
	}

	private static void verifyAdmissionBeforeGenerationConsumption() {
		short keyId = 4;
		InputGenerationTracker generations = new InputGenerationTracker();
		boolean spectator = ClStandClickPacket.Handler.isEligible(
				true, true, true, true, true);
		boolean automaticEmptyHand = ClStandClickPacket.Handler.isEligible(
				true, false, true, false, false);
		check(!acceptIfEligible(
				generations, spectator, keyId, 1L),
				"spectator Stand item use was admitted");
		check(!acceptIfEligible(
				generations, automaticEmptyHand, keyId, 1L),
				"automatic empty-hand Stand item use was admitted");
		check(generations.latestInputGeneration(keyId) == 0L,
				"rejected Stand item use consumed its generation");

		boolean manualEmptyHand = ClStandClickPacket.Handler.isEligible(
				true, false, true, true, false);
		check(acceptIfEligible(
				generations, manualEmptyHand, keyId, 1L),
				"manual Stand item interaction was rejected");
		boolean automaticHeldItem = ClStandClickPacket.Handler.isEligible(
				true, false, true, false, true);
		check(acceptIfEligible(
				generations, automaticHeldItem, keyId, 2L),
				"automatic held-item Stand interaction was rejected");
		check(!ClStandClickPacket.Handler.isEligible(
				false, false, true, true, true),
				"dead-player Stand item use was admitted");
		check(!ClStandClickPacket.Handler.isEligible(
				true, false, false, true, true),
				"dead-Stand item use was admitted");
	}

	private static boolean acceptIfEligible(
			InputGenerationTracker generations,
			boolean eligible,
			short keyId,
			long generation) {
		return eligible
				&& generations.acceptNetworkPressGeneration(
						keyId, generation);
	}

	private static void verifyFixedRmbAuthorityAndActiveConflict() {
		check(InputHandler.RMB.keyId() == InputKeyId.STAND_ITEM_RMB,
				"the client RMB mapping drifted from the shared wire ID");
		check(!ClStandClickPacket.Handler.hasActiveInputConflict(false, false),
				"an idle Stand item input was treated as conflicting");
		check(ClStandClickPacket.Handler.hasActiveInputConflict(true, false),
				"an active Stand item use admitted a replacement press");
		check(ClStandClickPacket.Handler.hasActiveInputConflict(false, true),
				"an occupied RMB slot admitted an overwriting Stand item press");

		InputGenerationTracker generations = new InputGenerationTracker();
		boolean accepted = !ClStandClickPacket.Handler.hasActiveInputConflict(
				true, false)
				&& generations.acceptNetworkPressGeneration(
						InputKeyId.STAND_ITEM_RMB, 1L);
		check(!accepted
				&& generations.latestInputGeneration(
						InputKeyId.STAND_ITEM_RMB) == 0L,
				"a conflicting re-press consumed the next input generation");
	}

	private static void verifyHandOrderingAndFallthrough() {
		check(ServerSideLivingClick.orderedDistinctHands().equals(
				List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND)),
				"an empty hand list did not default to vanilla hand order");
		check(ServerSideLivingClick.orderedDistinctHands(
				InteractionHand.OFF_HAND,
				InteractionHand.MAIN_HAND).equals(
						List.of(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND)),
				"explicit packet hand order was not preserved");
		check(ServerSideLivingClick.orderedDistinctHands(
				InteractionHand.MAIN_HAND,
				InteractionHand.MAIN_HAND).equals(
						List.of(InteractionHand.MAIN_HAND)),
				"a duplicate packet hand was attempted twice");

		InteractionResult[] outcomes = {
				InteractionResult.PASS,
				InteractionResult.SUCCESS
		};
		int attempts = 0;
		for (InteractionResult outcome : outcomes) {
			++attempts;
			if (!ServerSideLivingClick.shouldTryNextHand(outcome)) {
				break;
			}
		}
		check(attempts == 2,
				"MAIN_HAND PASS did not fall through to OFF_HAND");
		check(ServerSideLivingClick.shouldTryNextHand(null),
				"an empty MAIN_HAND attempt blocked OFF_HAND");
		check(!ServerSideLivingClick.shouldTryNextHand(
				InteractionResult.CONSUME),
				"a consuming MAIN_HAND result incorrectly reached OFF_HAND");
		check(ServerSideLivingClick.shouldTryNextHand(
				InteractionResult.FAIL),
				"a non-consuming item FAIL did not reach OFF_HAND");
		check(!ServerSideLivingClick.shouldUseItemWithoutTarget(
				InteractionResult.FAIL),
				"a terminal target FAIL incorrectly reached item use");
		check(ServerSideLivingClick.isTerminalTargetFailure(
				BlockHitResult.miss(
						Vec3.ZERO, Direction.UP, BlockPos.ZERO),
				InteractionResult.FAIL),
				"a target FAIL was not terminal");
		check(!ServerSideLivingClick.isTerminalTargetFailure(
				BlockHitResult.miss(
						Vec3.ZERO, Direction.UP, BlockPos.ZERO),
				InteractionResult.PASS),
				"a target PASS was treated as terminal");
	}

	private static void verifyReplacementReleaseIsOneShot() {
		AtomicInteger releases = new AtomicInteger();
		ItemUseReleaseGuard replaced = new ItemUseReleaseGuard();
		if (replaced.beginRelease()) {
			releases.incrementAndGet();
		}
		if (replaced.beginRelease()) {
			releases.incrementAndGet();
		}
		check(releases.get() == 1,
				"replacement cleanup and late RMB release ran twice");

		ItemUseReleaseGuard releasedNormally = new ItemUseReleaseGuard();
		check(releasedNormally.beginRelease()
				&& !releasedNormally.beginRelease(),
				"normal RMB release and later action clear were not one-shot");
	}

	private static void verifyDisconnectCleanup() {
		Map<Integer, Integer> held = new HashMap<>();
		Queue<Integer> releases = new ArrayDeque<>();
		Map<Integer, Integer> recent = new HashMap<>();
		List<Integer> modifiers = new ArrayList<>();
		Map<Integer, Integer> hotbars = new HashMap<>();
		Set<Integer> toggledHotbars = new HashSet<>();
		held.put(1, 1);
		releases.add(1);
		recent.put(1, 1);
		modifiers.add(1);
		hotbars.put(1, 1);
		toggledHotbars.add(1);

		InputHandler.clearDisconnectCollections(
				held, releases, recent, modifiers,
				hotbars, toggledHotbars);
		check(held.isEmpty()
				&& releases.isEmpty()
				&& recent.isEmpty()
				&& modifiers.isEmpty()
				&& hotbars.isEmpty()
				&& toggledHotbars.isEmpty(),
				"disconnect retained transient client input state");
	}

	private static void verifyPacketCodecRoundTrip() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			ClStandClickPacket original = new ClStandClickPacket(
					new HitResultSync(null),
					27L,
					InteractionHand.OFF_HAND,
					InteractionHand.MAIN_HAND);
			ClStandClickPacket.encodeBody(original, buffer);
			ClStandClickPacket decoded = ClStandClickPacket.decodeBody(buffer);
			check(decoded.inputGeneration() == original.inputGeneration()
					&& Arrays.equals(decoded.hand(), original.hand())
					&& !buffer.isReadable(),
					"Stand item-use packet body did not round-trip");
		}
		finally {
			buffer.release();
		}

		FriendlyByteBuf emptyHands = new FriendlyByteBuf(Unpooled.buffer());
		try {
			ClStandClickPacket original = new ClStandClickPacket(
					new HitResultSync(null), 28L);
			ClStandClickPacket.encodeBody(original, emptyHands);
			ClStandClickPacket decoded = ClStandClickPacket.decodeBody(emptyHands);
			check(decoded.hand().length == 0 && !emptyHands.isReadable(),
					"an empty Stand item-use hand list did not round-trip");
		}
		finally {
			emptyHands.release();
		}

		FriendlyByteBuf invalidGeneration =
				new FriendlyByteBuf(Unpooled.buffer());
		try {
			HitResultSync.STREAM_CODEC.encode(
					invalidGeneration, new HitResultSync(null));
			invalidGeneration.writeVarLong(0L);
			invalidGeneration.writeVarInt(0);
			expectThrows(DecoderException.class,
					() -> ClStandClickPacket.decodeBody(invalidGeneration),
					"non-positive Stand item-use generation was decoded");
		}
		finally {
			invalidGeneration.release();
		}

		FriendlyByteBuf oversizedInbound =
				new FriendlyByteBuf(Unpooled.buffer());
		try {
			HitResultSync.STREAM_CODEC.encode(
					oversizedInbound, new HitResultSync(null));
			oversizedInbound.writeVarLong(29L);
			oversizedInbound.writeVarInt(3);
			expectThrows(DecoderException.class,
					() -> ClStandClickPacket.decodeBody(oversizedInbound),
					"an oversized Stand item-use hand list was decoded");
		}
		finally {
			oversizedInbound.release();
		}

		FriendlyByteBuf oversizedOutbound =
				new FriendlyByteBuf(Unpooled.buffer());
		try {
			ClStandClickPacket packet = new ClStandClickPacket(
					new HitResultSync(null), 30L,
					InteractionHand.MAIN_HAND,
					InteractionHand.OFF_HAND,
					InteractionHand.MAIN_HAND);
			expectThrows(IllegalArgumentException.class,
					() -> ClStandClickPacket.encodeBody(packet, oversizedOutbound),
					"an oversized outbound Stand item-use hand list was encoded");
		}
		finally {
			oversizedOutbound.release();
		}
	}

	private static void verifyProductionWiring() {
		Path root = Path.of(System.getProperty("user.dir"));
		String packet = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/entity_useitem/ClStandClickPacket.java"));
		String clientClick = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "client/input/StandVanillaClickInput.java"));
		int heldTimer = clientClick.indexOf("new HeldKeyTimer(");
		int heldTimerCancelsRepeats = clientClick.indexOf(
				"key, true, KeyModifier.NONE)", heldTimer);
		int clientGeneration = clientClick.indexOf(
				"AbilityInput.nextInputGeneration(");
		int clientPacket = clientClick.indexOf(
				"new ClStandClickPacket(", clientGeneration);
		check(heldTimer >= 0
				&& heldTimerCancelsRepeats > heldTimer
				&& clientGeneration >= 0
				&& clientPacket > clientGeneration,
				"held Stand RMB did not suppress vanilla repeat presses");
		int eligibility = packet.indexOf("if (!isEligible(");
		int fixedRmb = packet.indexOf(
				"short internalKeyId = InputKeyId.STAND_ITEM_RMB;", eligibility);
		int conflict = packet.indexOf(
				"hasActiveInputConflict(", fixedRmb);
		int generation = packet.indexOf(
				"inputHandler.acceptNetworkPressGeneration(", conflict);
		int click = packet.indexOf(
				"ServerSideLivingClick.rightClick(", generation);
		int actionStart = packet.indexOf(
				"LivingComponentAction.getComponent(standEntity).setAction(", click);
		int failedStartCleanup = packet.indexOf(
				"standEntity.stopUsingItem();", actionStart);
		check(eligibility >= 0
				&& fixedRmb > eligibility
				&& conflict > fixedRmb
				&& generation > conflict
				&& click > generation
				&& packet.indexOf("packet.hand());", click) > click
				&& actionStart > click
				&& failedStartCleanup > actionStart,
				"Stand click key authority, conflict, generation, or hand ordering regressed");

		String clickSource = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/entity_useitem/ServerSideLivingClick.java"));
		int handLoop = clickSource.indexOf(
				"for (InteractionHand hand : orderedDistinctHands(hands))");
		int entityAt = clickSource.indexOf(
				"CommonHooks.onInteractEntityAt(", handLoop);
		int entityAtDecision = clickSource.indexOf(
				"if (!interactionResult.consumesAction())", entityAt);
		int entityInteract = clickSource.indexOf(
				"targetEntity.interact(entityWrapper, hand)", entityAtDecision);
		int entityConsumeDecision = clickSource.indexOf(
				"if (interactionResult.consumesAction())", entityInteract);
		int itemInteractDecision = clickSource.indexOf(
				"else {", entityConsumeDecision);
		int itemInteract = clickSource.indexOf(
				"item.interactLivingEntity(", itemInteractDecision);
		int entityNormalizeDecision = clickSource.indexOf(
				"if (!interactionResult.consumesAction())", itemInteract);
		int entityNormalize = clickSource.indexOf(
				"interactionResult = InteractionResult.PASS;",
				entityNormalizeDecision);
		int targetCall = clickSource.indexOf(
				"result = _interactWithTarget(", handLoop);
		int terminalTargetDecision = clickSource.indexOf(
				"if (isTerminalTargetFailure(hitResult, result))", targetCall);
		int noTargetDecision = clickSource.indexOf(
				"shouldUseItemWithoutTarget(result)", terminalTargetDecision);
		int continueDecision = clickSource.indexOf(
				"shouldTryNextHand(result)", handLoop);
		check(handLoop >= 0
				&& entityAt > handLoop
				&& entityAtDecision > entityAt
				&& entityInteract > entityAtDecision
				&& entityConsumeDecision > entityInteract
				&& itemInteractDecision > entityInteract
				&& itemInteract > itemInteractDecision
				&& entityNormalizeDecision > itemInteract
				&& entityNormalize > entityNormalizeDecision
				&& targetCall > handLoop
				&& terminalTargetDecision > targetCall
				&& noTargetDecision > terminalTargetDecision
				&& continueDecision > noTargetDecision,
				"Stand click target stages lost vanilla entity/block fallthrough");

		String itemUse = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "subsystems/entity_useitem/VanillaItemUseAsAction.java"));
		int cleared = itemUse.indexOf("void onActionCleared(");
		int clearedRelease = itemUse.indexOf(
				"releaseUsingItemOnce();", cleared);
		int buttonStop = itemUse.indexOf("void onButtonStopHold()");
		int buttonRelease = itemUse.indexOf(
				"releaseUsingItemOnce()", buttonStop);
		check(cleared >= 0
				&& clearedRelease > cleared
				&& buttonStop > clearedRelease
				&& buttonRelease > buttonStop,
				"Stand item-use removal and RMB release lost their shared guard");
		String component = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/entityaction/LivingComponentAction.java"));
		int removePrevious = component.indexOf(
				"this.action._beforeActionRemoved(action);");
		int installReplacement = component.indexOf(
				"assignAction(action,", removePrevious);
		String instance = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "powersystem/entityaction/EntityActionInstance.java"));
		int removalCallback = instance.indexOf(
				"void _beforeActionRemoved(");
		int clearedCallback = instance.indexOf(
				"onActionCleared(newAction);", removalCallback);
		check(removePrevious >= 0
				&& installReplacement > removePrevious
				&& removalCallback >= 0
				&& clearedCallback > removalCallback,
				"action replacement no longer invokes Stand item-use cleanup");

		String input = read(root.resolve(
				"src/main/java/com/github/standobyte/jojo/"
				+ "client/input/InputHandler.java"));
		int registration = input.indexOf(
				"NeoForge.EVENT_BUS.register(instance);");
		int logout = input.indexOf(
				"onLogout(ClientPlayerNetworkEvent.LoggingOut event)");
		int cleanup = input.indexOf(
				"clearDisconnectedInputState();", logout);
		int cleanupMethod = input.indexOf(
				"private void clearDisconnectedInputState()", cleanup);
		int cleanupEnd = input.indexOf(
				"@ApiStatus.Internal", cleanupMethod);
		String cleanupBody = cleanupMethod >= 0 && cleanupEnd > cleanupMethod
				? input.substring(cleanupMethod, cleanupEnd) : "";
		check(registration >= 0
				&& logout > registration
				&& cleanup > logout
				&& cleanupBody.contains("_heldKeys")
				&& cleanupBody.contains("keyReleaseEventQueue")
				&& cleanupBody.contains("_recentlyPressed")
				&& cleanupBody.contains("modifiersQueue")
				&& cleanupBody.contains("hotbarsSelection")
				&& cleanupBody.contains("toggledHotbarsSelection")
				&& cleanupBody.contains("hamonDoubleShift.reset()")
				&& cleanupBody.contains("curPowerClassToggle = null")
				&& cleanupBody.contains("lastActionKey = null")
				&& cleanupBody.contains("inputsDisabled = false"),
				"client logout lost transient input cleanup");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
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

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
