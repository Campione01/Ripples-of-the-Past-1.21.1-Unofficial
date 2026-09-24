package rotp.core.subsystems.timestop;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 reached a time stop's blink with SHIFT on the time stop slot (TimeResume#replaceAction). The port
 * gives the blink its own special-wheel slot instead (owner boundary: Stand variants are direct wheel
 * entries, no Shift/Ctrl variations), and the time stop key resumes the user's own stopped time. The blink
 * cost 0.8 of its own base time stop's staminaCost / staminaCostTick (TimeStopInstant), turned the user
 * toward an entity target, and the TS punch trained its time stop in whole points only (TheWorldTSHeavyAttack).
 */
public final class TimeStopBlinkInputSmokeTest {
	private static final ResourceLocation SHADOW = id("rotp_shadowtheworld", "shadowtheworld");
	private static final ResourceLocation THE_WORLD = id("jojo_ripples", "the_world");
	private static final String SHADOW_TS = "shadow_world_time_stop";
	private static final String SHADOW_BLINK = "shadow_world_ts_blink";
	private static final String BLINK_SLOT = ".addToHotbar(\"time_stop_blink\", 0, InputMethod.CLICK)";
	private static final String TIME_STOP_SLOT = ".addToHotbar(\"time_stop\", 0, InputMethod.HOLD)";
	private static final float EPSILON = 0.0001F;

	private TimeStopBlinkInputSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyBlinkCosts();
		verifyBlinkIsItsOwnSlot();
		verifyTsPunchLearning();
		verifyBlinkFacing();
		verifySourceWiring();
	}

	/** 1.16 TimeStopInstant: user.yRot = MathUtil.yRotDegFromVec(target.position() - blinkPos). */
	private static void verifyBlinkFacing() {
		Vec3 blinkPos = new Vec3(10.0D, 64.0D, -3.0D);
		double[][] offsets = { { 0, 0, 1 }, { 1, 0, 0 }, { -1, 0, 0 }, { 0.6, 1.5, -0.8 }, { -2.5, -1, 4 }, { 3, 0, -0.01 } };
		for (double[] offset : offsets) {
			Float yaw = TimeStopBlinkAbility.getFacingYaw(blinkPos.add(offset[0], offset[1], offset[2]), blinkPos);
			float expected = (float) -Math.atan2(offset[0], offset[2]) * (180.0F / (float) Math.PI);
			check(yaw != null, "an entity beside the blink position must turn the user");
			// Mth.atan2 (like 1.16 MathHelper.atan2) is a table approximation
			check(Math.abs(yaw - expected) < 0.01F,
					"blink yaw toward " + java.util.Arrays.toString(offset) + ": expected " + expected + ", got " + yaw);
		}
		check(Math.abs(TimeStopBlinkAbility.getFacingYaw(new Vec3(0, 0, 5), Vec3.ZERO)) < 0.01F, "south is yaw 0");
		check(Math.abs(TimeStopBlinkAbility.getFacingYaw(new Vec3(5, 0, 0), Vec3.ZERO) + 90.0F) < 0.01F, "east is yaw -90");
		check(Math.abs(TimeStopBlinkAbility.getFacingYaw(new Vec3(-5, 0, 0), Vec3.ZERO) - 90.0F) < 0.01F, "west is yaw 90");
		check(TimeStopBlinkAbility.getFacingYaw(new Vec3(0, 3, 0), Vec3.ZERO) == null,
				"a target straight above keeps the user's yaw");
		check(TimeStopBlinkAbility.getFacingYaw(ActionTarget.EMPTY, blinkPos) == null,
				"a blink without an entity target keeps the user's yaw");
	}

	private static void verifyBlinkCosts() {
		TimeStopBlinkAbility worldBlink = blink(THE_WORLD, "time_stop_blink");
		StubPower worldPower = power(timeStop(THE_WORLD, TimeStopLearning.TIME_STOP), worldBlink);
		checkClose(worldBlink.getBlinkStaminaCost(worldPower), 180.0F,
				"The World's blink costs 0.8 of 225");
		checkClose(worldBlink.getBlinkStaminaCost(worldPower),
				TimeStopLearning.getTimeStopBlinkStaminaCost(worldPower),
				"the default blink must keep the old start cost");
		checkClose(worldBlink.getBlinkStaminaCostTicking(worldPower, TimeStopLearning.TIME_STOP), 144.0F,
				"The World's untrained blink costs 9 * 100 / 5 * 0.8 a tick");
		checkClose(worldBlink.getBlinkStaminaCostTicking(worldPower, TimeStopLearning.TIME_STOP),
				TimeStopLearning.getTimeStopBlinkStaminaCostTicking(worldPower, TimeStopLearning.TIME_STOP),
				"the default blink must keep the old per-tick cost");

		// SHADOWWORLDTimeStop: staminaCost(80), staminaCostTick(3.5)
		TimeStopBlinkAbility shadowBlink = blink(SHADOW, SHADOW_BLINK)
				.setTimeStopAbilityName(SHADOW_TS)
				.setBaseTimeStopStaminaCosts(80.0F, 3.5F);
		StubPower shadowPower = power(timeStop(SHADOW, SHADOW_TS), shadowBlink);
		checkClose(shadowBlink.getBlinkStaminaCost(shadowPower), 64.0F,
				"Shadow The World's blink costs 80 * 0.8");
		checkClose(shadowBlink.getBlinkStaminaCostTicking(shadowPower, SHADOW_TS), 56.0F,
				"Shadow's untrained blink costs 3.5 * 100 / 5 * 0.8 a tick");
		shadowPower.learning().put(SHADOW_TS, 55.0F);
		checkClose(shadowBlink.getBlinkStaminaCostTicking(shadowPower, SHADOW_TS), 3.5F * 100 / 60 * 0.8F,
				"Shadow's trained blink is priced by its own time stop's 60 ticks");
		// Reworked's ReTimeStopInstant over its RE_*_TIME_STOP: staminaCost(300), staminaCostTick(8.875)
		TimeStopBlinkAbility reworkedBlink = blink(THE_WORLD, "time_stop_blink").setBaseTimeStopStaminaCosts(300.0F, 8.875F);
		StubPower reworkedPower = power(timeStop(THE_WORLD, TimeStopLearning.TIME_STOP), reworkedBlink);
		checkClose(reworkedBlink.getBlinkStaminaCost(reworkedPower), 240.0F, "Reworked's blink costs 300 * 0.8");
		checkClose(reworkedBlink.getBlinkStaminaCostTicking(reworkedPower, TimeStopLearning.TIME_STOP), 142.0F,
				"Reworked's untrained blink costs 8.875 * 100 / 5 * 0.8 a tick");
		reworkedPower.learning().put(TimeStopLearning.TIME_STOP, 95.0F);
		checkClose(reworkedBlink.getBlinkStaminaCostTicking(reworkedPower, TimeStopLearning.TIME_STOP), 7.1F,
				"Reworked's blink trained to 100 ticks costs 7.1 a tick");
		expectRejected(() -> blink(SHADOW, SHADOW_BLINK).setBaseTimeStopStaminaCosts(-1.0F, 3.5F),
				"a negative start cost");
		expectRejected(() -> blink(SHADOW, SHADOW_BLINK).setBaseTimeStopStaminaCosts(80.0F, Float.NaN),
				"a NaN per-tick cost");
	}

	private static void verifyBlinkIsItsOwnSlot() {
		TimeStopBlinkAbility worldBlink = blink(THE_WORLD, "time_stop_blink");
		check("time_stop_blink".equals(worldBlink.getSpriteName(null)),
				"the blink's own wheel slot must show the blink icon, not the time stop's");
		check(TimeStopLearning.TIME_STOP.equals(worldBlink.getTimeStopAbilityName()),
				"the core blink must follow the moveset's time_stop");
	}

	private static void verifyTsPunchLearning() {
		checkClose(TimeStopLearning.getTsPunchLearningPoints(TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, 9), 0.0F,
				"a 9-tick TS punch skip trains (int) 0.9");
		checkClose(TimeStopLearning.getTsPunchLearningPoints(TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, 10), 1.0F,
				"a 10-tick TS punch skip trains one point");
		checkClose(TimeStopLearning.getTsPunchLearningPoints(TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, 39), 3.0F,
				"a 39-tick TS punch skip trains (int) 3.9");
		checkClose(TimeStopLearning.getTsPunchLearningPoints(TimeStopLearning.STAR_PLATINUM_LEARNING_PER_TICK, 5), 1.0F,
				"the whole-point rule holds at 0.25 a tick");
		checkClose(TimeStopLearning.getTsPunchLearningPoints(TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, -5), 0.0F,
				"negative skipped ticks awarded training");
		checkClose(TimeStopLearning.getLearningPoints(TimeStopLearning.THE_WORLD_LEARNING_PER_TICK, 9), 0.9F,
				"an ended time stop still trains in fractions");
	}

	private static void verifySourceWiring() {
		Path root = Path.of(System.getProperty("user.dir")).resolve("src/main/java/rotp/core");
		for (String stand : new String[] { "StandInitTheWorld.java", "StandInitStarPlatinum.java" }) {
			String source = read(root.resolve("impl/stands/" + stand));
			check(!source.contains(".addHotbarSlotVariation(") && !source.contains("\"time_resume\""),
					stand + " must not bring back the SHIFT time resume variation");
			String hotbar = between(source, ".makeControlScheme(\"hotbar\")", ".finalizeControlScheme()");
			int timeStopSlot = hotbar.indexOf(TIME_STOP_SLOT);
			int blinkSlot = hotbar.indexOf(BLINK_SLOT);
			check(count(hotbar, TIME_STOP_SLOT) == 1 && count(hotbar, BLINK_SLOT) == 1
					&& timeStopSlot >= 0 && blinkSlot > timeStopSlot
					&& hotbar.substring(timeStopSlot + TIME_STOP_SLOT.length(), blinkSlot)
							.replaceAll("//[^\n]*", "").isBlank(),
					stand + " must give the blink its own wheel slot right after the held time stop");
			check(!source.contains("withAbility(\"time_stop_blink\")"),
					stand + " blink unlocks with its time stop, not as a skill of its own");
		}

		// the retired SHIFT resume never swaps in a blink (Batch907/938)
		String resume = read(root.resolve("impl/stands/theworld/TimeResumeAbility.java"));
		check(!between(resume, "public Ability replaceWithSubAbility(", "public ConditionCheck checkSpecificConditions(")
						.contains("blink")
				&& !resume.contains("time_stop_blink") && !resume.contains("instanceof TimeStopBlinkAbility"),
				"the time resume must not look up the blink");

		String blink = read(root.resolve("impl/stands/theworld/TimeStopBlinkAbility.java"));
		check(!blink.contains("TimeStopLearning.getTimeStopBlinkStaminaCost(power)")
				&& count(blink, "getBlinkStaminaCost(") >= 4
				&& count(blink, "getBlinkStaminaCostTicking(") >= 3,
				"the blink must charge its own base time stop's costs everywhere");
		check(between(blink, "public ConditionCheck getUnlockConditionCheck(", "public boolean isAbilityAvailable(")
						.contains("timeStop.getUnlockConditionCheck(context)"),
				"the blink must unlock exactly with its time stop");
		// ServerPlayer.teleportTo(x, y, z) sends RelativeMovement.ROTATION, so a yaw set on the server
		// never reaches the client: the blink yaw must be computed first and sent as an absolute yaw.
		String perform = between(blink, "private boolean performBlink(", "private int getMaxImpliedTicks(");
		int yawAt = perform.indexOf("Float facingYaw = getFacingYaw(target, blinkPos);");
		int teleportAt = perform.indexOf("teleportFacing(user, blinkPos, facingYaw);");
		check(yawAt >= 0 && teleportAt > yawAt && !perform.contains(".teleportTo(")
						&& !blink.contains("faceEntityTarget("),
				"the blink must pick its yaw before teleporting and teleport through teleportFacing");
		String facing = between(blink, "public static void teleportFacing(", "private static void skipTicksForStandAndUser(");
		check(facing.contains("user instanceof ServerPlayer player && !player.isFakePlayer()")
						&& facing.contains("player.connection.teleport(pos.x, pos.y, pos.z, yaw, player.getXRot(), "
								+ "EnumSet.of(RelativeMovement.X_ROT));")
						&& !facing.contains("Y_ROT") && !facing.contains("RelativeMovement.ROTATION")
						&& facing.indexOf("user.setYRot(yaw);") < facing.indexOf("user.teleportTo(pos.x, pos.y, pos.z);")
						&& facing.contains("user.setYHeadRot(yaw);") && facing.contains("user.setYBodyRot(yaw);"),
				"a player must get an absolute blink yaw; other users are turned (head and body too) before teleporting");

		String learning = read(root.resolve("subsystems/timestop/TimeStopLearning.java"));
		String punch = between(learning, "public static void onTsPunchTimeSkip(", "public static void onBlinkPunchTimeSkip(");
		check(punch.contains("getTsPunchLearningPoints(learningPerTick(standPower), ticksPassed)"),
				"the TS punch must train in whole points");
		check(between(learning, "public static void onTimeStopEnded(StandPower standPower, @Nullable Ability timeStop",
						"private static boolean canLearnFromEndedTimeStop(")
				.contains("getLearningPoints(learningPerTick(standPower), ticksPassed)"),
				"an ended time stop must keep fractional training");
		check(read(root.resolve("impl/stands/theworld/TheWorldTSPunchAbility.java"))
						.contains("TimeStopLearning.onTsPunchTimeSkip(standPower, timeStopTicks);"),
				"The World's TS punch must train through the whole-point rule");

		String ability = read(root.resolve("impl/stands/theworld/TimeStopAbility.java"));
		check(between(ability, "public HeldInput onKeyPress(", "private void clearCurrentStandAction(")
						.contains("if (hasOwnTimeStop(user)) {\n\t\t\tbufferingState.isActionSuccess = requestManualResume(level, user);"),
				"the time stop key must resume the user's own stopped time");
		check(between(ability, "private float getHoldToFireTicks(LivingEntity user)", "protected boolean shortensChargeAtResolveFour(")
						.contains("shortensChargeAtResolveFour(power)")
				&& ability.contains("return power.getPowerType() == ModStands.THE_WORLD.get();"),
				"the 20-tick Resolve 4 charge must be open to add-on TheWorldTimeStops");
	}

	private static TimeStopAbility timeStop(ResourceLocation stand, String name) {
		return new AbilityType<TimeStopAbility>(id("jojo_ripples", "time_stop"), TimeStopAbility::new)
				.createInstance(new AbilityId(null, stand, name));
	}

	private static TimeStopBlinkAbility blink(ResourceLocation stand, String name) {
		return new AbilityType<TimeStopBlinkAbility>(id("jojo_ripples", "time_stop_blink"),
				TimeStopBlinkAbility::new).createInstance(new AbilityId(null, stand, name));
	}

	private static StubPower power(Ability... abilities) {
		Map<String, Ability> byName = new LinkedHashMap<>();
		for (Ability ability : abilities) {
			byName.put(ability.name(), ability);
		}
		StubPower power = allocate(StubPower.class);
		power.moveset = new Moveset(byName, null, null);
		power.data = allocate(StubData.class);
		return power;
	}

	/** A Stand user with no entity: human limits, not creative, no policy. */
	private static final class StubPower extends StandPower {
		private Moveset moveset;
		private StubData data;

		private StubPower() {
			super(null);
		}

		Map<String, Float> learning() {
			return data.learning();
		}

		@Override
		public LivingEntity getUser() {
			return null;
		}

		@Override
		public StandType getPowerType() {
			return null;
		}

		@Override
		public boolean hasPower() {
			return true;
		}

		@Override
		public Moveset getMoveset() {
			return moveset;
		}

		@Override
		public StandTypePersistentData getCurTypeData() {
			return data;
		}
	}

	private static final class StubData extends StandTypePersistentData {
		private Map<String, Float> learning;

		private StubData() {
			super(null);
		}

		Map<String, Float> learning() {
			if (learning == null) {
				learning = new HashMap<>();
			}
			return learning;
		}

		@Override
		public float getAbilityLearningProgressPoints(String abilityName) {
			return learning().getOrDefault(abilityName, -1.0F);
		}
	}

	private static <T> T allocate(Class<T> type) {
		try {
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field singleton = unsafeClass.getDeclaredField("theUnsafe");
			singleton.setAccessible(true);
			Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
			return type.cast(allocateInstance.invoke(singleton.get(null), type));
		}
		catch (ReflectiveOperationException e) {
			throw new AssertionError("could not allocate " + type.getSimpleName(), e);
		}
	}

	private static void expectRejected(Runnable action, String what) {
		try {
			action.run();
		}
		catch (IllegalArgumentException expected) {
			return;
		}
		throw new AssertionError(what + " was accepted");
	}

	private static ResourceLocation id(String namespace, String path) {
		return ResourceLocation.fromNamespaceAndPath(namespace, path);
	}

	private static int count(String source, String token) {
		int count = 0;
		for (int index = source.indexOf(token); index >= 0; index = source.indexOf(token, index + token.length())) {
			count++;
		}
		return count;
	}

	private static String between(String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError(
					"failed to locate source contract between "
							+ startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		}
		catch (IOException error) {
			throw new AssertionError("failed to read " + path, error);
		}
	}

	private static void checkClose(float actual, float expected, String message) {
		if (Math.abs(actual - expected) > EPSILON) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
