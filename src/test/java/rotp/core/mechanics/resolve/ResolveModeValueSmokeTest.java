package rotp.core.mechanics.resolve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 1.16 ResolveCounter: while the Resolve effect is on, addResolveValue adds the boosted
 * points and then keeps the value at half or more, the value drains max / RESOLVE_EFFECT_MIN
 * per tick, and only levels below RESOLVE_EFFECT_MIN.length drain at all. The boosts do not
 * decay while the effect is on, and a heavy hit taken adds dmg * BOOST_PER_DMG_DEALT * 2.
 * 1.16 had no mode timer: the value running out ends a draining Resolve and the effect's own
 * duration, finite or infinite, ends any Resolve. The port's timer only shows what is left.
 */
public final class ResolveModeValueSmokeTest {
	private ResolveModeValueSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		for (int level = 0; level < ResolveCounter.RESOLVE_EFFECT_MIN.length; level++) {
			check(ResolveCounter.drainsResolveValue(level),
					"Resolve level " + level + " must drain like 1.16");
		}
		check(!ResolveCounter.drainsResolveValue(ResolveCounter.RESOLVE_EFFECT_MIN.length),
				"Resolve level 5 (GER's evolution) must not drain, as in 1.16");
		check(!ResolveCounter.drainsResolveValue(255),
				"a Resolve level above the table must not drain");
		check(ResolveCounter.drainsResolveValue(-1),
				"a negative level drains at the last table rate, as in 1.16");

		check(ResolveCounter.resolveModeTicksForValue(300, 10000.0F, 10000.0F) == 300,
				"a full value must keep the whole mode timer");
		check(ResolveCounter.resolveModeTicksForValue(300, 5000.0F, 10000.0F) == 150,
				"the half floor must match the old half timer");
		check(ResolveCounter.resolveModeTicksForValue(400, 2500.0F, 10000.0F) == 100,
				"a quarter value must last a quarter of the timer");
		check(ResolveCounter.resolveModeTicksForValue(300, 1.0F, 10000.0F) == 1,
				"any value left must keep at least one tick");
		check(ResolveCounter.resolveModeTicksForValue(300, 20000.0F, 10000.0F) == 300,
				"a value above max must not stretch the timer");
		check(ResolveCounter.resolveModeTicksForValue(-1, 5000.0F, 10000.0F) == 0
				&& ResolveCounter.resolveModeTicksForValue(300, 5000.0F, 0.0F) == 0,
				"no timer or no max must not invent mode ticks");

		verifyModeTimer();

		// 1.16 onGettingAttacked: dmg * 0.05 * 2, so a 10-point hit adds 1 point before boosts
		check(ResolveCounter.resolveOnGettingAttacked(10.0F) == 1.0F,
				"a heavy hit taken must add dmg * BOOST_PER_DMG_DEALT * 2 like 1.16");
		check(ResolveCounter.resolveOnGettingAttacked(8.0F) == 8.0F * ResolveCounter.BOOST_PER_DMG_DEALT * 2,
				"the resolve from a heavy hit must scale with the damage");

		verifySourceContract();
	}

	private static void verifyModeTimer() {
		// draining: the timer is the value's own drain time, so it follows every refill
		check(ResolveCounter.nextResolveModeTimer(300, 300, true, 10000.0F, 10000.0F) == 300,
				"a full draining Resolve shows its whole drain time");
		check(ResolveCounter.nextResolveModeTimer(5, 400, true, 10000.0F, 10000.0F) == 400,
				"a soul refilling the value must stretch the shown time, not let it run out");
		check(ResolveCounter.nextResolveModeTimer(200, 400, true, 5000.0F, 10000.0F) == 200,
				"a draining Resolve shows how long its value lasts");
		check(ResolveCounter.nextResolveModeTimer(200, 400, true, 0.0F, 10000.0F) == 0,
				"an empty value shows no time left");
		// not draining: the effect duration it started with counts down; an infinite one has none
		check(ResolveCounter.nextResolveModeTimer(2900, 2900, false, 10000.0F, 10000.0F) == 2899,
				"a level 5+ Resolve counts down its effect duration");
		check(ResolveCounter.nextResolveModeTimer(-1, -1, false, 10000.0F, 10000.0F) == -1,
				"an infinite level 5+ Resolve has no countdown");
		check(ResolveCounter.nextResolveModeTimer(0, 2900, false, 10000.0F, 10000.0F) == 0,
				"a finished countdown stays finished");

		// the tooltip never shows more than the effect has left, as the ring does
		check(ResolveCounter.resolveModeTicksShown(1450, 200) == 200,
				"the shown time must not pass the effect's remaining ticks");
		check(ResolveCounter.resolveModeTicksShown(150, 2900) == 150,
				"a shorter timer is shown as is");
		check(ResolveCounter.resolveModeTicksShown(1450, -1) == 1450,
				"an infinite effect does not clamp the timer");
		check(ResolveCounter.resolveModeTicksShown(-1, 200) == -1
				&& ResolveCounter.resolveModeTicksShown(0, 200) == 0,
				"no timer stays no timer");
	}

	private static void verifySourceContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String counter = read(root.resolve(
				"src/main/java/rotp/core/mechanics/resolve/ResolveCounter.java"));
		String counterCode = stripComments(counter);

		String add = between(counterCode,
				"public void addResolveValue(StandPower stand, float resolve)",
				"public static boolean drainsResolveValue(int amplifier)");
		int modeBranch = add.indexOf("else {");
		int added = add.indexOf("float addedResolve = getResolveValue() + boostAddedValue(resolve, user);", modeBranch);
		int floored = add.indexOf("Math.max(getMaxResolveValue(stand) * 0.5F, addedResolve)", modeBranch);
		check(modeBranch >= 0 && added > modeBranch && floored > added,
				"in Resolve mode the points must be added before the half floor");
		int drainingOnly = add.indexOf("if (drainsResolveValue(resolveMode.getAmplifier())) {", floored);
		int timerSet = add.indexOf("resolveModeTimer.value = resolveModeTicksForValue(resolveModeTimer.defaultValue, getResolveValue(), getMaxResolveValue(stand));");
		check(drainingOnly > floored && timerSet > drainingOnly
				&& add.indexOf("resolveModeTimer.value", timerSet + 1) < 0
				&& !add.contains("resolveModeTimer.defaultValue / 2"),
				"only a draining Resolve's timer may follow an added value; a level 5+ timer is its effect duration");

		String tick = between(counterCode,
				"private void tickResolveValue(StandPower stand, LivingEntity user)",
				"public float getResolveValue()");
		int guard = tick.indexOf("if (!drainsResolveValue(resolveMode.getAmplifier())) {");
		check(guard >= 0
				&& guard < tick.indexOf("getMaxResolveValue(stand) / (float) RESOLVE_EFFECT_MIN[resolveLevel]"),
				"a non-draining Resolve must skip the drain");
		int emptied = tick.indexOf("if (!user.level().isClientSide() && nextResolve == 0) {");
		check(emptied > guard && tick.indexOf("user.removeEffect(ModStatusEffects.RESOLVE);", emptied) > emptied,
				"a draining Resolve must end when its value runs out, as in 1.16");
		check(count(counterCode, "removeEffect(") == 1,
				"the value running out must be the only place the counter ends a Resolve");

		String start = between(counterCode,
				"public void onResolveEffectStart(StandPower stand, LivingEntity user, MobEffectInstance resolveEffect)",
				"public void onResolveEffectEnd(StandPower stand, LivingEntity user)");
		check(start.contains("resolveEffect.is(ModStatusEffects.RESOLVE) && drainsResolveValue(resolveEffect.getAmplifier())"),
				"a non-draining Resolve must run for the effect's own duration");
		check(start.contains("resolveModeTimer.defaultValue = resolveEffect.isInfiniteDuration() ? -1 : resolveEffect.getDuration();"),
				"an infinite Resolve must have no countdown");

		String counterTick = between(counterCode,
				"public void tick(StandPower stand)",
				"private void tickResolveValue(StandPower stand, LivingEntity user)");
		int effectFlag = counterTick.indexOf("boolean resolveEffectOn = user != null && user.hasEffect(ModStatusEffects.RESOLVE);");
		int valueTick = counterTick.indexOf("tickResolveValue(stand, user);");
		int boostGuard = counterTick.indexOf("if (!resolveEffectOn) {");
		int boostCountdown = counterTick.indexOf("noBoostDecayTicks--;");
		int boostReset = counterTick.indexOf("boostAttack = 1;");
		check(effectFlag >= 0 && valueTick > effectFlag,
				"the Resolve effect must be read at the start of the tick, as in 1.16");
		check(boostGuard > valueTick && boostCountdown > boostGuard && boostReset > boostCountdown
				&& counterTick.indexOf("noBoostDecayTicks--;", boostCountdown + 1) < 0
				&& counterTick.indexOf("boostAttack = 1;", boostReset + 1) < 0,
				"boosts must neither count down nor reset while the Resolve effect is on");
		int timerTick = counterTick.indexOf("resolveModeTimer.value = nextResolveModeTimer(resolveModeTimer.value, resolveModeTimer.defaultValue,");
		check(timerTick > valueTick
				&& counterTick.indexOf("resolveMode != null && drainsResolveValue(resolveMode.getAmplifier()), curResolve, getMaxResolveValue(stand));", timerTick) > timerTick
				&& !counterTick.contains("removeEffect(")
				&& !counterTick.contains("resolveModeTimer.value--"),
				"the mode timer must only show what is left and never end a Resolve");

		String ratio = between(counterCode,
				"public float getResolveModeTimerRatio(StandPower stand, float partialTick)",
				"public int getResolveModeTicksShown(StandPower stand)");
		check(ratio.contains("if (resolveEffect.isInfiniteDuration() && !timerOn) {")
				&& ratio.contains("duration = resolveModeTicksShown(resolveModeTimer.value, duration);"),
				"the ring must stay full for an infinite Resolve and clamp the timer to the effect");
		String shown = between(counterCode,
				"public int getResolveModeTicksShown(StandPower stand)",
				"public static int resolveModeTicksShown(int timer, int effectTicks)");
		check(shown.contains("resolveModeTicksShown(resolveModeTimer.value, resolveEffect.getDuration())"),
				"the shown ticks must be the timer clamped to the effect");

		String hud = stripComments(read(root.resolve(
				"src/main/java/rotp/core/client/ui/hud_power/PowerHud.java")));
		check(hud.contains("int resolveModeTimer = standPower.resolveCounter.getResolveModeTicksShown(standPower);")
				&& !hud.contains("resolveCounter.resolveModeTimer.value"),
				"the Resolve tooltip must print the clamped time, not the raw timer");

		String attacked = between(counterCode,
				"public void onGettingAttacked(DamageSource dmgSource, float dmgAmount, StandPower stand, LivingEntity user)",
				"protected void tickBoostRemoteControl(StandPower stand)");
		int heavyHit = attacked.indexOf("if (dmgAmount >= user.getMaxHealth() * 0.4F) {");
		check(heavyHit >= 0
				&& attacked.indexOf("addResolveValue(stand, resolveOnGettingAttacked(dmgAmount));", heavyHit) > heavyHit
				&& !attacked.contains("BOOST_PER_DMG_DEALT * 10"),
				"a heavy hit taken must add the 1.16 amount");
	}

	private static int count(String source, String token) {
		int found = 0;
		for (int at = source.indexOf(token); at >= 0; at = source.indexOf(token, at + token.length())) {
			found++;
		}
		return found;
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

	// Source without comments, so a commented-out line neither passes nor trips a check.
	private static String stripComments(String source) {
		StringBuilder code = new StringBuilder(source.length());
		for (int i = 0; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '"' || c == '\'') {
				int end = literalEnd(source, i);
				code.append(source, i, end);
				i = end - 1;
			}
			else if (source.startsWith("//", i)) {
				int end = source.indexOf('\n', i);
				i = (end < 0 ? source.length() : end) - 1;
			}
			else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				i = (end < 0 ? source.length() : end + 2) - 1;
			}
			else {
				code.append(c);
			}
		}
		return code.toString();
	}

	private static int literalEnd(String source, int start) {
		char quote = source.charAt(start);
		for (int i = start + 1; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '\\') {
				i++;
			}
			else if (c == quote) {
				return i + 1;
			}
		}
		return source.length();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
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
