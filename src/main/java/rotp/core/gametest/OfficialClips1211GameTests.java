package rotp.core.gametest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The core Stands play the official 1.21.1 port's clip wherever it has one (StandoByte/Ripples-of-the-Past-1-21-1 at
 * 1a681255, the baseline NOTICE.md names), 1.16's poses only where it has none: every part of every official clip
 * (each bone and each top-level field) keeps the official JSON but for the differences listed here with their reasons,
 * and every core ability that has an official clip of its own name plays it but for the abilities listed with the clip
 * they play instead (Star Platinum's Star Finger, by the owner's answer of 2026-10-15).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OfficialClips1211GameTests {
	private OfficialClips1211GameTests() {}

	// generated from the official animation files: stand, clip, part, the part's digest (see digest)
	private static final String TABLE = "rotp/core/gametest/official_clips_1a681255.tsv";
	private static final int OFFICIAL_CLIPS = 59;

	private enum Change { LACKS, ADDS, CHANGES }

	private record Difference(String stand, String clip, String part, Change change, String reason) {}

	private static final String ONE_TICK = "K5-31 (d75ec4e3): RECOVERY a tick after PERFORM for 1.16's one-tick perform,"
			+ " the markers pinned by HeavyPunchPerform116GameTests";
	private static final String GRAB_ONE_TICK = "K5-30 (19170e37): the grab heavy's RECOVERY a tick after PERFORM for the"
			+ " one-tick perform, the markers pinned by HeavyPunchPerform116GameTests";
	private static final String NO_BONE = "a bone the Stand's geo lacks, absent since the first snapshot c9799c78";
	private static final String UPPER_BODY_BARRAGE = "731e5d41: an ordinary barrage moves the upper body only, this bone held";
	private static final String R148_MIRROR = "af972e0c (R148): the clip mirrored for the hand it is played with";
	private static final String R148_ARM_LOOK = "af972e0c (R148): the arms follow the look pitch up to 60 degrees";

	private static final List<Difference> DIFFERENCES = List.of(
			new Difference("star_platinum", "heavy_punch", "timeline", Change.CHANGES, ONE_TICK),
			new Difference("crazy_diamond", "heavy_punch", "timeline", Change.CHANGES, ONE_TICK),
			new Difference("crazy_diamond", "finisher", "timeline", Change.CHANGES, ONE_TICK),
			new Difference("star_platinum", "grab_uppercut", "timeline", Change.CHANGES, GRAB_ONE_TICK),
			new Difference("crazy_diamond", "grab_heavy_punch", "timeline", Change.CHANGES, GRAB_ONE_TICK),
			new Difference("crazy_diamond", "grab_finisher", "timeline", Change.CHANGES, GRAB_ONE_TICK),
			new Difference("magicians_red", "idle", "bones/left_item", Change.LACKS, NO_BONE),
			new Difference("magicians_red", "idle", "bones/right_item", Change.LACKS, NO_BONE),
			new Difference("silver_chariot", "idle", "bones/left_item", Change.LACKS, NO_BONE),
			new Difference("silver_chariot", "idle", "bones/right_item", Change.LACKS, NO_BONE),
			new Difference("crazy_diamond", "uncraft", "bones/torso_old", Change.LACKS, NO_BONE),
			new Difference("star_platinum", "barrage", "bones/back_fabric", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/front_fabric", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/head", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/left_leg", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/left_leg_bend", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/left_leg_joint", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/right_leg", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/right_leg_bend", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/right_leg_joint", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/torso", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("star_platinum", "barrage", "bones/torso_bend", Change.CHANGES, UPPER_BODY_BARRAGE),
			new Difference("crazy_diamond", "block_bullet", "timeline", Change.CHANGES, R148_MIRROR + " (LEFT, the official RIGHT)"),
			new Difference("crazy_diamond", "block_bullet", "bones/left_arm_xrot", Change.ADDS, R148_ARM_LOOK),
			new Difference("crazy_diamond", "block_bullet", "bones/right_arm_xrot", Change.ADDS, R148_ARM_LOOK),
			new Difference("crazy_diamond", "repair_item", "timeline", Change.ADDS, R148_MIRROR + " (RIGHT)"),
			new Difference("crazy_diamond", "uncraft", "timeline", Change.ADDS, R148_MIRROR + " (RIGHT)"));

	/*
	 * Each clip of the official port's seven core Stand files is in the port's file of that Stand, and each of its parts
	 * has the official digest, or differs exactly as listed; a listed difference the port no longer has is reported too,
	 * so the list stays the port's whole drift from the official clips.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCoreStandsPlayTheOfficialClipsButForTheListedDifferences(GameTestHelper helper) {
		Map<String, Map<String, Map<String, String>>> official = readTable(helper);
		int clips = official.values().stream().mapToInt(Map::size).sum();
		helper.assertTrue(clips == OFFICIAL_CLIPS, "setup: " + TABLE + " holds " + clips + " official clips, not " + OFFICIAL_CLIPS);
		List<String> wrong = new ArrayList<>();
		Set<Difference> found = new HashSet<>();
		for (Map.Entry<String, Map<String, Map<String, String>>> stand : official.entrySet()) {
			String asset = "stand_skins/" + stand.getKey() + "/assets/jojo_ripples/animations/" + stand.getKey() + ".animation.json";
			JsonObject anims = StandClipPoses116GameTests.readJson(helper, asset).getAsJsonObject("animations");
			for (Map.Entry<String, Map<String, String>> clip : stand.getValue().entrySet()) {
				JsonObject portClip = anims.getAsJsonObject(clip.getKey());
				if (portClip == null) {
					wrong.add(stand.getKey() + " " + clip.getKey() + ": no such clip in " + asset);
					continue;
				}
				Map<String, String> port = parts(portClip);
				Set<String> names = new TreeSet<>(clip.getValue().keySet());
				names.addAll(port.keySet());
				for (String part : names) {
					String want = clip.getValue().get(part);
					String got = port.get(part);
					if (got != null && got.equals(want)) {
						continue;
					}
					Change change = got == null ? Change.LACKS : want == null ? Change.ADDS : Change.CHANGES;
					Difference listed = DIFFERENCES.stream().filter(d -> d.stand().equals(stand.getKey())
							&& d.clip().equals(clip.getKey()) && d.part().equals(part)).findFirst().orElse(null);
					if (listed == null || listed.change() != change) {
						wrong.add(stand.getKey() + " " + clip.getKey() + " " + part + ": " + change + " (" + got + " where the official is "
								+ want + ")" + (listed != null ? ", listed as " + listed.change() : ", not listed"));
					}
					else {
						found.add(listed);
					}
				}
			}
		}
		for (Difference listed : DIFFERENCES) {
			if (!found.contains(listed)) {
				wrong.add(listed.stand() + " " + listed.clip() + " " + listed.part() + ": listed as " + listed.change() + " ("
						+ listed.reason() + ") but the port's clip does not differ so");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The core Stands play the official 1.21.1 clips; only the listed differences are"
				+ " allowed, each with its reason: " + wrong);
		helper.succeed();
	}

	// a core ability that does not play the official clip of its own name, the clip it plays and why
	private record PlayedInstead(String stand, String ability, String clip, String reason) {}

	private static final List<PlayedInstead> PLAYED_INSTEAD = List.of(
			new PlayedInstead("star_platinum", "star_finger", "starFinger", "the owner's answer of 2026-10-15 (final card of R770,"
					+ " call 2, owner-answers-2026-10-14.md): Star Finger as 1.16 drew it, the finger projectile for every viewer;"
					+ " the official star_finger stretches the model's hidden finger, a second finger beside the projectile, so"
					+ " the Stand holds 1.16's starFinger pose (450c7996)"));

	/*
	 * The clip a core ability plays, as the press's action asks for it: an ability with an official clip of its own name
	 * plays that clip, or the listed one; a listed ability that plays its official clip again, or has none, fails too.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theCoreAbilitiesPlayTheOfficialClipOfTheirNameButTheListedOnes(GameTestHelper helper) {
		Map<String, Map<String, Map<String, String>>> official = readTable(helper);
		List<String> wrong = new ArrayList<>();
		Set<PlayedInstead> found = new HashSet<>();
		int asked = 0;
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		for (Map.Entry<String, Map<String, Map<String, String>>> stand : official.entrySet()) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(stand.getKey()));
			helper.assertTrue(type != null, "setup: no Stand type " + stand.getKey());
			for (Map.Entry<String, Ability> entry : type.getBaseMoveset().abilities.entrySet()) {
				if (!(entry.getValue() instanceof EntityActionAbility ability)) {
					continue;
				}
				String name = entry.getKey();
				boolean officialClip = stand.getValue().containsKey(name);
				// the action a press builds for a right-handed user performing it himself (a clip may follow the hand)
				EntityActionInstance action;
				try {
					action = ability.initActionOnAbilityUse(helper.getLevel(), user, user, null);
				}
				catch (RuntimeException e) {
					action = null;
				}
				ActionAnimIdentifier anim;
				try {
					anim = ability.getEntityAnim(action);
				}
				catch (RuntimeException e) {
					if (officialClip) {
						wrong.add("setup: " + stand.getKey() + "'s " + name + " gives no clip: " + e);
					}
					continue;
				}
				if (anim == null) {
					if (officialClip) {
						wrong.add(stand.getKey() + "'s " + name + " plays no clip where the official port has " + name);
					}
					continue;
				}
				asked++;
				// an indexed clip (punch, index 1) is the file's punch2; a handed one (_left / _right) the mirrored base clip
				String played = (anim.index() > 0 ? anim.name() + (anim.index() + 1) : anim.name()).replaceFirst("_(left|right)$", "");
				PlayedInstead listed = PLAYED_INSTEAD.stream().filter(p -> p.stand().equals(stand.getKey()) && p.ability().equals(name))
						.findFirst().orElse(null);
				if (listed != null) {
					found.add(listed);
					if (!played.equals(listed.clip()) || !officialClip) {
						wrong.add(stand.getKey() + "'s " + name + " plays " + played + ", listed as playing " + listed.clip()
								+ " instead of the official " + name + " (" + listed.reason() + ")"
								+ (officialClip ? "" : ", but the official port has no " + name));
					}
				}
				else if (officialClip && !played.equals(name)) {
					wrong.add(stand.getKey() + "'s " + name + " plays " + played + " where the official port has " + name + ", not listed");
				}
			}
		}
		user.discard();
		helper.assertTrue(asked > 0, "setup: no core ability asked for a clip");
		for (PlayedInstead listed : PLAYED_INSTEAD) {
			if (!found.contains(listed)) {
				wrong.add(listed.stand() + "'s " + listed.ability() + " is listed (" + listed.reason() + ") but is no ability of its moveset");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "The core abilities play the official 1.21.1 clip of their name but the listed ones: " + wrong);
		helper.succeed();
	}

	// each top-level field of the clip and each bone (bones/<name>), with its digest
	private static Map<String, String> parts(JsonObject clip) {
		Map<String, String> parts = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> field : clip.entrySet()) {
			if (field.getKey().equals("bones")) {
				for (Map.Entry<String, JsonElement> bone : field.getValue().getAsJsonObject().entrySet()) {
					parts.put("bones/" + bone.getKey(), digest(bone.getValue()));
				}
			}
			else {
				parts.put(field.getKey(), digest(field.getValue()));
			}
		}
		return parts;
	}

	// the first 16 hex digits of the SHA-256 of the JSON as HeavyPunchPerform116GameTests.keyframeDigest writes it
	private static String digest(JsonElement element) {
		StringBuilder text = new StringBuilder();
		HeavyPunchPerform116GameTests.canonical(element, text);
		try {
			byte[] sha = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(sha).substring(0, 16);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	// stand -> clip -> part -> digest, in the table's order
	private static Map<String, Map<String, Map<String, String>>> readTable(GameTestHelper helper) {
		InputStream in = OfficialClips1211GameTests.class.getResourceAsStream("/" + TABLE);
		if (in == null) {
			in = OfficialClips1211GameTests.class.getClassLoader().getResourceAsStream(TABLE);
		}
		helper.assertTrue(in != null, "setup: " + TABLE + " is missing");
		Map<String, Map<String, Map<String, String>>> table = new LinkedHashMap<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.isBlank() || line.startsWith("#")) {
					continue;
				}
				String[] cells = line.split("\t");
				helper.assertTrue(cells.length == 4, "setup: " + TABLE + " line " + line);
				table.computeIfAbsent(cells[0], s -> new LinkedHashMap<>()).computeIfAbsent(cells[1], c -> new LinkedHashMap<>())
						.put(cells[2], cells[3]);
			}
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + TABLE, e);
		}
		return table;
	}
}
