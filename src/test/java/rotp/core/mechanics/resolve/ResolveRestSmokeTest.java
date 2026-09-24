package rotp.core.mechanics.resolve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import rotp.core.core.JojoMod;
import com.google.common.collect.TreeMultiset;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.16 ResolveCounter, the parts the port had lost:
 * <ul>
 * <li>one no-decay counter for the value and the boosts, counted twice a tick with the Stand unsummoned;</li>
 * <li>RESOLVE_DMG_REDUCTION 0.6667, and a vampire's constant half missing-health boost (5);</li>
 * <li>resolve records (10 values fights reached) that multiply the points below them, the max achieved value on
 * the bar, and alwaysResetOnDeath;</li>
 * <li>StandType.getTargetResolveMultiplier (tier 5, Star Platinum and The World 6);</li>
 * <li>an updated Resolve effect ends and starts again (onEffectUpdated re-applied the attribute modifiers);</li>
 * <li>the STAND_MAX trigger and its jojo/stand_max advancement.</li>
 * </ul>
 */
public final class ResolveRestSmokeTest {
	private ResolveRestSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyNoDecayCountdown();
		verifyConstants();
		verifyRecords();
		verifyDeathReset();
		verifyTargetTier();
		verifyStandMax();
		verifySourceContract();
	}

	private static void verifyNoDecayCountdown() {
		check(ResolveCounter.countDownNoDecayTicks(400, true) == 399, "a summoned Stand counts once a tick");
		check(ResolveCounter.countDownNoDecayTicks(400, false) == 398, "an unsummoned Stand counts twice a tick");
		check(ResolveCounter.countDownNoDecayTicks(1, false) == 0 && ResolveCounter.countDownNoDecayTicks(2, false) == 0,
				"the second count only runs while ticks are left");
		check(ResolveCounter.countDownNoDecayTicks(0, false) == 0, "no ticks stay no ticks");
		check(ticksToRunOut(400, false) == 200 && ticksToRunOut(400, true) == 400,
				"400 no-decay ticks last 10 s unsummoned and 20 s summoned, as in 1.16");
	}

	private static int ticksToRunOut(int ticks, boolean summoned) {
		int count = 0;
		while (ticks > 0) {
			ticks = ResolveCounter.countDownNoDecayTicks(ticks, summoned);
			count++;
		}
		return count;
	}

	private static void verifyConstants() {
		check(ResolveCounter.RESOLVE_DMG_REDUCTION == 0.6667F, "Resolve damage reduction is 0.6667 in 1.16");
		check(ResolveCounter.BOOST_MISSING_HP_MAX / 2 == 5.0F, "a vampire's boost from getting attacked is 5 in 1.16");
		check(ResolveCounter.MAX_RESOLVE_RECORDS == 10, "1.16 kept 10 resolve records");
	}

	private static void verifyRecords() {
		TreeMultiset<Float> records = TreeMultiset.create();
		check(ResolveCounter.multiplyRecords(records, 1000, 100) == 100, "no record, no multiplier");
		records.add(5000.0F);
		check(ResolveCounter.multiplyRecords(records, 1000, 100) == 300,
				"points below a record reached once count 1 + 2 times");
		records.add(5000.0F);
		check(ResolveCounter.multiplyRecords(records, 1000, 100) == 400,
				"a record reached twice counts 1 + 3 times");
		records.clear();
		records.add(1050.0F);
		check(ResolveCounter.multiplyRecords(records, 1000, 100) == 200,
				"only the part below the record is multiplied");
		records.add(900.0F);
		records.add(1000.0F);
		check(ResolveCounter.multiplyRecords(records, 1000, 100) == 200,
				"records at or below the current value do not multiply");

		TreeMultiset<Float> capped = TreeMultiset.create();
		for (int i = 1; i <= 10; i++) {
			check(ResolveCounter.addResolveRecord(capped, i * 100.0F), "a record under the cap must be kept");
		}
		check(!ResolveCounter.addResolveRecord(capped, 50.0F) && capped.size() == 10,
				"a full record list refuses a value below its smallest record");
		check(ResolveCounter.addResolveRecord(capped, 2000.0F) && capped.size() == 10 && !capped.contains(100.0F),
				"a full record list drops its smallest record for a higher one");
		check(ResolveCounter.maxResolveRecord(capped) == 2000.0F && ResolveCounter.maxResolveRecord(TreeMultiset.create()) == 0,
				"the max achieved value is the highest record, 0 with none");

		ResolveCounter counter = new ResolveCounter();
		counter.resolveRecords.add(3000.0F);
		counter.resolveRecords.add(3000.0F);
		counter.saveNextRecord = false;
		counter.maxAchievedValue = 3000.0F;
		CompoundTag nbt = counter.writeNBT();
		ResolveCounter read = new ResolveCounter();
		read.readNBT(nbt);
		check(read.resolveRecords.count(3000.0F) == 2 && !read.saveNextRecord && read.maxAchievedValue == 3000.0F,
				"the records, SaveNextRecord and MaxAchieved must be saved");
		ResolveCounter old = new ResolveCounter();
		old.readNBT(new CompoundTag());
		check(old.resolveRecords.isEmpty() && old.saveNextRecord && old.maxAchievedValue == 0,
				"a save from before the records starts with none and saves the next one");
	}

	private static void verifyDeathReset() {
		ResolveCounter prev = new ResolveCounter();
		prev.resolveLerp.set(4000.0F, false);
		prev.noResolveDecayTicks = 250;
		prev.resolveRecords.add(1000.0F);
		prev.saveNextRecord = false;
		prev.boostAttack = 3;

		ResolveCounter kept = new ResolveCounter();
		kept.copyValues(prev, false);
		check(kept.getResolveValue() == 4000.0F && kept.noResolveDecayTicks == 250 && kept.boostAttack == 3
				&& kept.resolveRecords.count(1000.0F) == 1 && !kept.saveNextRecord,
				"a clone without a death keeps everything");

		ResolveCounter dead = new ResolveCounter();
		dead.copyValues(prev, true);
		check(dead.getResolveValue() == 0 && dead.noResolveDecayTicks == 0 && dead.boostAttack == 1,
				"1.16 alwaysResetOnDeath empties the value, its no-decay ticks and the boosts");
		check(dead.resolveRecords.count(4000.0F) == 1 && dead.resolveRecords.count(1000.0F) == 1
				&& dead.maxAchievedValue == 4000.0F && dead.saveNextRecord,
				"the value at death becomes a record and the max achieved value");
		check(prev.getResolveValue() == 4000.0F, "the death reset must not touch the old player's counter");
	}

	private static void verifyTargetTier() {
		check(ResolveCounter.getResolveMultiplierTier((ResourceLocation) null) == 5
				&& ResolveCounter.getResolveMultiplierTier(JojoMod.resLoc("magicians_red")) == 5,
				"an ordinary Stand is tier 5");
		check(ResolveCounter.getResolveMultiplierTier(JojoMod.resLoc("star_platinum")) == 6
				&& ResolveCounter.getResolveMultiplierTier(JojoMod.resLoc("the_world")) == 6,
				"Star Platinum and The World are tier 6");
		ResourceLocation addon = ResourceLocation.fromNamespaceAndPath("resolve_rest_test", "tier_stand");
		ResolveCounter.addAttackerResolveMultTier(addon, 10);
		check(ResolveCounter.getResolveMultiplierTier(addon) == 15, "an add-on can raise its Stand's tier");
		check(ResolveCounter.standTargetResolveMultiplier(5, 5) == 1, "two ordinary Stands give 1");
		check(ResolveCounter.standTargetResolveMultiplier(6, 5) == 2, "hitting Star Platinum or The World gives 2");
		check(ResolveCounter.standTargetResolveMultiplier(5, 6) == 1, "the multiplier never drops below 1");
		check(ResolveCounter.standTargetResolveMultiplier(15, 5) == 11, "a tier 15 target gives 11");
		check(ResolveCounter.standTargetResolveMultiplier(5, null) == 6, "an attacker with no Stand leaves tier + 1");
	}

	private static void verifyStandMax() {
		check(ResolveAdvancements.reachesStandMax(4, 4, true) && ResolveAdvancements.reachesStandMax(5, 4, true),
				"a level at the maximum fires STAND_MAX");
		check(!ResolveAdvancements.reachesStandMax(3, 4, true), "a lower level does not");
		check(!ResolveAdvancements.reachesStandMax(4, 4, false), "a Stand without resolve does not");
		check("jojo_ripples:stand_max".equals(ResolveAdvancements.STAND_MAX_ID.toString()), "the 1.16 trigger name");

		Path root = Path.of(System.getProperty("user.dir"));
		JsonObject advancement = JsonParser.parseString(read(root.resolve(
				"src/main/resources/data/jojo_ripples/advancement/jojo/stand_max.json"))).getAsJsonObject();
		check("jojo_ripples:jojo/resolve".equals(advancement.get("parent").getAsString()),
				"stand_max follows the resolve advancement, as in 1.16");
		check(advancement.getAsJsonObject("rewards").get("experience").getAsInt() == 250, "stand_max gives 250 xp");
		check("jojo_ripples:stand_max".equals(advancement.getAsJsonObject("criteria").getAsJsonObject("stand_max")
				.get("trigger").getAsString()), "stand_max waits for the STAND_MAX trigger");
		for (String lang : new String[] { "en_us", "zh_cn" }) {
			JsonObject strings = JsonParser.parseString(read(root.resolve(
					"src/main/resources/assets/jojo_ripples/lang/" + lang + ".json"))).getAsJsonObject();
			check(strings.has("advancements.jojo.stand_max.title") && strings.has("advancements.jojo.stand_max.description"),
					lang + " must name the stand_max advancement");
		}
	}

	private static void verifySourceContract() {
		Path root = Path.of(System.getProperty("user.dir"));
		String counter = squash(stripComments(read(root.resolve(
				"src/main/java/rotp/core/mechanics/resolve/ResolveCounter.java"))));
		String tickValue = between(counter, squash("private void tickResolveValue(StandPower stand, LivingEntity user)"),
				squash("public static int countDownNoDecayTicks(int ticks, boolean standSummoned)"));
		int fightEnds = tickValue.indexOf(squash("boolean fightEnds = noResolveDecayTicks == 1;"));
		int countdown = tickValue.indexOf(squash("noResolveDecayTicks = countDownNoDecayTicks(noResolveDecayTicks, stand.isSummoned());"));
		int save = tickValue.indexOf(squash("if (fightEnds && !user.level().isClientSide()) { saveResolveRecord(stand); }"));
		check(fightEnds >= 0 && countdown > fightEnds && save > countdown,
				"a record is saved on the server when the no-decay ticks run out one by one, as in 1.16");
		check(tickValue.contains(squash("if (getResolveValue() == 0) { saveNextRecord = true; }")),
				"a value that decays to 0 starts a new record");
		check(counter.contains(squash("value = multiplyRecords(resolveRecords, getResolveValue(), value);")),
				"added points must be multiplied by the records after the boosts");
		String attacked = between(counter, squash("protected float boostFromGettingAttacked(LivingEntity user)"),
				squash("public float getTotalBoostVisible(LivingEntity user)"));
		check(attacked.contains(squash("return BOOST_MISSING_HP_MAX / 2;")) && !attacked.contains(squash("return 1;")),
				"a vampire's boost from getting attacked must be 1.16's constant 5");
		String reset = between(counter, squash("public void resetResolveValue(StandPower stand)"),
				squash("public void addResolveOnAttack(StandPower stand, float dmgAmount)"));
		check(reset.contains(squash("resolveRecords.clear();")) && reset.contains(squash("maxAchievedValue = 0;")),
				"the end of a Resolve clears the records, as 1.16 resetResolveValue did");
		String addResolve = between(counter, squash("public static void addResolve(StandPower attackerStand, LivingEntity attackTarget, float dmgAmount)"),
				squash("public static float standTargetResolveMultiplier("));
		check(addResolve.contains(squash("StandPower targetStand = StandPower.get(attackTarget);"))
				&& addResolve.contains(squash("dmgAmount *= standTargetResolveMultiplier(getResolveMultiplierTier(targetStand.getPowerType().getId()),")),
				"hitting a Stand user must apply the Stand tier multiplier");
		String start = between(counter, squash("public void onResolveEffectStart(StandPower stand, LivingEntity user, MobEffectInstance resolveEffect)"),
				squash("public void onResolveEffectEnd(StandPower stand, LivingEntity user)"));
		check(start.indexOf(squash("stand.setResolveLevel(Math.min(newLevel, stand.getMaxResolveLevel()));"))
				< start.indexOf(squash("ResolveAdvancements.onResolveLevelSet(stand, Math.min(newLevel, stand.getMaxResolveLevel()));")),
				"reaching the maximum level through Resolve must fire STAND_MAX");

		String effect = squash(stripComments(read(root.resolve(
				"src/main/java/rotp/core/mechanics/resolve/ResolveModeEffect.java"))));
		String updated = between(effect, squash("public void onUpdated(LivingEntity entity, MobEffectInstance instance, @Nullable Entity source)"),
				squash("public void onRemoved(LivingEntity entity, MobEffectInstance instance)"));
		int end = updated.indexOf(squash("resolve.onResolveEffectEnd(standPower, entity);"));
		check(end >= 0 && updated.indexOf(squash("resolve.onResolveEffectStart(standPower, entity, instance);")) > end,
				"an updated Resolve must reset, then start again");

		String packet = squash(stripComments(read(root.resolve(
				"src/main/java/rotp/core/mechanics/resolve/ResolveBoostsPacket.java"))));
		check(packet.contains(squash("buf.writeFloat(packet.maxAchievedValue);"))
				&& packet.contains(squash("standPower.resolveCounter.maxAchievedValue = payload.maxAchievedValue;")),
				"the max achieved value must reach the user's client");
		String hud = squash(stripComments(read(root.resolve(
				"src/main/java/rotp/core/client/ui/hud_power/PowerHud.java"))));
		check(hud.contains(squash("resolveBarFillWidth(standPower.resolveCounter.getMaxAchievedValue() / maxResolve, width)"))
				&& hud.contains(squash("MAX_ACHIEVED_TINT);")),
				"the Resolve bar must show the max achieved value");
	}

	private static String between(String source, String startToken, String endToken) {
		int start = source.indexOf(startToken);
		int end = source.indexOf(endToken, start);
		if (start < 0 || end < 0 || end <= start) {
			throw new AssertionError("failed to locate source contract between " + startToken + " and " + endToken);
		}
		return source.substring(start, end);
	}

	private static String squash(String source) {
		return source.replaceAll("\\s+", "");
	}

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
