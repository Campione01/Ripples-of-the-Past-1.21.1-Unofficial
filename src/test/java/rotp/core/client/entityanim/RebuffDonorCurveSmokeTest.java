package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.powersystem.entityaction.ActionPhase;

/**
 * Independent scalar oracle, not a renderer/visual acceptance test.
 * Fixture: canonical 1.16.5-0.2.2.2 JAR, assets/jojo/player_animation/hamon/rebuff_overdrive.json,
 * author dikiytechies; the original bytes and metadata are retained under src/test/resources.
 * Semantics: donor KosmXKeyframeAnimPlayer.Axis/RotationAxis, player-animation-lib 0.4.0
 * AnimationJson, KeyframeAnimation and Easing (source SHA256 29ba1adb...0bb42be).
 * No current animation expressions or current interpolation helpers supply expected values.
 */
public final class RebuffDonorCurveSmokeTest {
	private static final String FIXTURE = "/rotp/fixtures/rebuff_overdrive-1.16.5.json";
	private static final String FIXTURE_SHA256 =
			"fa55f50c053a9f0b79df35050b7bd35654a066b0cb1d80f54a45d6233c19b304";
	private static final Path CURRENT_ASSET = Path.of(
			"src/main/resources/assets/jojo_ripples/animations/hamon.animation.json");
	private static final String[] FIELDS = { "x", "y", "z", "pitch", "yaw", "roll", "bend" };
	private static final List<Part> PARTS = List.of(
			new Part("head", "head", 0, 0, 0, null),
			new Part("torso", "body", 0, 0, 0, "torso_bend"),
			new Part("leftArm", "left_arm", 5, 2, 0, "left_arm_bend"),
			new Part("rightArm", "right_arm", -5, 2, 0, "right_arm_bend"),
			new Part("leftLeg", "left_leg", 1.9F, 12, -0.1F, "left_leg_bend"),
			new Part("rightLeg", "right_leg", -1.9F, 12, 0.1F, "right_leg_bend"));

	private RebuffDonorCurveSmokeTest() {}

	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
		run();
		System.out.println("Rebuff donor curve smoke test passed: 41 scalar tracks at 201 poses "
				+ "(8241 comparisons), ten independent anchors and active phase-clock checks");
	}

	public static void run() throws IOException, NoSuchAlgorithmException {
		Donor donor = readDonor();
		KeyframesMolangEngine.init();
		RotpAnimDefinition animation;
		try (Reader reader = Files.newBufferedReader(CURRENT_ASSET, StandardCharsets.UTF_8)) {
			animation = ParseGeckoAnims.parseAnim(JsonParser.parseReader(reader).getAsJsonObject()
					.getAsJsonObject("animations").getAsJsonObject("rebuff_overdrive"));
		}
		// Check a real evaluated pose before metadata, so the old clip fails for its wrong motion.
		verifyIndependentAnchors(donor, animation);
		verifyAllCurves(donor, animation);
		verifyActiveClock(animation);
	}

	private static Donor readDonor() throws IOException, NoSuchAlgorithmException {
		byte[] bytes;
		try (InputStream stream = RebuffDonorCurveSmokeTest.class.getResourceAsStream(FIXTURE)) {
			check(stream != null, "missing original donor fixture " + FIXTURE);
			bytes = stream.readAllBytes();
		}
		check(bytes.length == 14131, "original donor fixture length changed");
		String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		check(FIXTURE_SHA256.equals(digest), "original donor fixture SHA256 changed: " + digest);
		JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
		check("dikiytechies".equals(root.get("author").getAsString()), "donor authorship retained");
		check("rebuff_overdrive".equals(root.get("name").getAsString()), "donor clip identity retained");
		check(!root.has("version"), "fixture uses AnimationJson's legacy version1 default");
		return new Donor(root.getAsJsonObject("emote"));
	}

	private static void verifyIndependentAnchors(Donor donor, RotpAnimDefinition animation) {
		anchor(donor, animation, "head", "pitch", 2.5F, 1.14781586237F);
		anchor(donor, animation, "rightArm", "y", 13.5F, 0.427375316620F);
		anchor(donor, animation, "leftArm", "y", 13.5F, 0.314641356468F);
		anchor(donor, animation, "leftArm", "y", 24.5F, 0.564287778516F);
		anchor(donor, animation, "torso", "y", 20.5F, -1.56866753654F);
		anchor(donor, animation, "torso", "y", 30, -2.66753411293F);
		anchor(donor, animation, "leftLeg", "z", 0, 0.2F);
		anchor(donor, animation, "leftLeg", "z", 6, 0.1F);
		anchor(donor, animation, "leftLeg", "z", 12, 0);
		anchor(donor, animation, "rightLeg", "z", 23, 0.475678533316F);
	}

	private static void anchor(Donor donor, RotpAnimDefinition animation, String sourcePart,
			String field, float tick, float expectedJsonValue) {
		Part part = PARTS.stream().filter(candidate -> candidate.source().equals(sourcePart)).findFirst().orElseThrow();
		String label = sourcePart + "." + field + " donor tick " + tick;
		float expectedRuntime = runtimeValue(part, field, donor.sample(part, field, tick));
		near(toJsonUnits(field, expectedRuntime), expectedJsonValue, "independent oracle anchor " + label);
		AnimFramePose pose = animation.calcAnimPose(null, null, tick / 20, 1, new AnimFramePose(), new Vector3f());
		near(toJsonUnits(field, actualValue(pose, part, field)), expectedJsonValue, "runtime anchor " + label);
	}

	private static void verifyAllCurves(Donor donor, RotpAnimDefinition animation) {
		AnimFramePose scratch = new AnimFramePose();
		Vector3f target = new Vector3f();
		int comparisons = 0;
		// Quarter-tick samples cover every authored integer key, fractional transitions and the held tail.
		for (int quarterTick = 0; quarterTick <= 200; quarterTick++) {
			float seconds = quarterTick / 80F;
			float donorTick = seconds * 20;
			AnimFramePose pose = animation.calcAnimPose(null, null, seconds, 1, scratch, target);
			for (Part part : PARTS) {
				for (String field : FIELDS) {
					if (field.equals("bend") && part.bendBone() == null) continue;
					float expected = runtimeValue(part, field, donor.sample(part, field, donorTick));
					near(actualValue(pose, part, field), expected,
							part.source() + "." + field + " at " + seconds + "s");
					comparisons++;
				}
				ModelPartFrame frame = requirePart(pose, part.bone());
				zero(frame.scaleOffset, part.bone() + " scale");
				if (part.bendBone() != null) {
					ModelPartFrame bend = requirePart(pose, part.bendBone());
					near(bend.rotationOffset.y(), 0, part.bendBone() + " unkeyed bend direction Y");
					near(bend.rotationOffset.z(), 0, part.bendBone() + " unkeyed bend direction Z");
					zero(bend.positionOffset, part.bendBone() + " position");
					zero(bend.scaleOffset, part.bendBone() + " scale");
				}
			}
		}
		check(comparisons == 8241, "all41 enabled scalar tracks must be sampled at all201 times");
	}

	private static void verifyActiveClock(RotpAnimDefinition animation) {
		near(animation.lengthInSeconds, 2, "active phase envelope length");
		near(animation.clock.speed, 1, "donor Rebuff has no SpeedModifier");
		check(!animation.loopBackTo.isPresent(), "Rebuff remains nonlooping");
		check(animation.animationMirror == null, "Rebuff must not gain a handed mirror");
		check(animation.instructionTimelines.phases != null, "Rebuff phase timeline");
		ActionPhase[] phases = { ActionPhase.WINDUP, ActionPhase.PERFORM, ActionPhase.RECOVERY };
		float[] starts = { 0, 0.7F, 1.2F };
		float[] durations = { 14, 10, 16 };
		int index = 0;
		for (var entry : animation.instructionTimelines.phases.getEntries()) {
			check(index < phases.length, "unexpected extra Rebuff phase");
			check(entry.getValue().phase == phases[index], "Rebuff phase order " + index);
			near(entry.getFloatKey(), starts[index], "Rebuff phase start " + phases[index]);
			index++;
		}
		check(index == phases.length, "all three active Rebuff phases remain");
		LivingAnimState state = new LivingAnimState();
		for (int phase = 0; phase < phases.length; phase++) {
			state.actionPhase = phases[phase];
			for (float elapsed : new float[] { 0, 1.5F, durations[phase] - 0.25F, durations[phase] }) {
				state.phaseTime = elapsed;
				state.phaseCompletion = elapsed / durations[phase];
				state.time = starts[phase] * 20 + elapsed;
				near(animation.getAnimTime(state), starts[phase] + elapsed / 20,
						"real phase clock " + phases[phase] + " tick " + elapsed);
			}
		}
		near(animation.getAnimTime(2.5F), 0.125F, "fractional unphased donor clock");
		near(animation.getAnimTime(50), 2.5F, "continued visual-tail clock is not clamped to envelope length");
	}

	private static float runtimeValue(Part part, String field, float source) {
		boolean body = part.source().equals("torso");
		return switch (field) {
			case "x" -> body ? -16 * source : source - part.restX();
			case "y" -> body ? -16 * source : source - part.restY();
			case "z" -> body ? 16 * source : source - part.restZ();
			case "pitch", "yaw" -> body ? -source : source;
			case "roll", "bend" -> source;
			default -> throw new AssertionError("unexpected source field " + field);
		};
	}

	private static float toJsonUnits(String field, float runtime) {
		return switch (field) {
			case "y" -> -runtime;
			case "pitch", "yaw", "roll", "bend" -> (float) Math.toDegrees(runtime);
			default -> runtime;
		};
	}

	private static float actualValue(AnimFramePose pose, Part part, String field) {
		ModelPartFrame frame = requirePart(pose, field.equals("bend") ? part.bendBone() : part.bone());
		return switch (field) {
			case "x" -> frame.positionOffset.x();
			case "y" -> frame.positionOffset.y();
			case "z" -> frame.positionOffset.z();
			case "pitch", "bend" -> frame.rotationOffset.x();
			case "yaw" -> frame.rotationOffset.y();
			case "roll" -> frame.rotationOffset.z();
			default -> throw new AssertionError("unexpected source field " + field);
		};
	}

	private static ModelPartFrame requirePart(AnimFramePose pose, String name) {
		ModelPartFrame frame = pose.getIfPresent(name);
		check(frame != null, "missing evaluated bone " + name);
		return frame;
	}

	private record Part(String source, String bone, float restX, float restY, float restZ, String bendBone) {
		float sourceDefault(String field) {
			return switch (field) {
				case "x" -> restX;
				case "y" -> restY;
				case "z" -> source.endsWith("Leg") ? 0.1F : 0;
				default -> 0;
			};
		}
	}

	private record DonorKey(int tick, float value, String ease) {}

	private static final class Donor {
		private final Map<String, List<DonorKey>> tracks = new LinkedHashMap<>();
		private final int endTick;

		Donor(JsonObject emote) {
			check(emote.get("beginTick").getAsInt() == 0, "fixture begins at0");
			endTick = emote.get("endTick").getAsInt();
			check(endTick == 80 && emote.get("stopTick").getAsInt() == 80, "fixture raw end/stop retained");
			check(emote.get("returnTick").getAsInt() == 14, "fixture attack entry retained");
			check(!emote.get("isLoop").getAsBoolean(), "fixture is nonlooping");
			check(!emote.get("degrees").getAsBoolean(), "fixture angles are radians");
			check(!emote.has("easeBeforeKeyframe"), "0.4 absent-flag default is outgoing easing");
			int scalars = 0;
			for (Part part : PARTS) {
				for (String field : FIELDS) {
					if (field.equals("bend") && part.bendBone() == null) continue;
					List<DonorKey> keys = new ArrayList<>();
					for (JsonElement element : emote.getAsJsonArray("moves")) {
						JsonObject move = element.getAsJsonObject();
						check(move.get("turn").getAsInt() == 0, "no extra rotation turns in fixture");
						JsonObject values = move.getAsJsonObject(part.source());
						if (values == null) continue;
						check(!values.has("axis"), "unkeyed donor bend direction defaults tozero");
						if (values.has(field)) {
							keys.add(new DonorKey(move.get("tick").getAsInt(), values.get(field).getAsFloat(),
									move.get("easing").getAsString().replaceFirst("^EASE", "")));
						}
					}
					keys.sort(Comparator.comparingInt(DonorKey::tick));
					check(!keys.isEmpty(), "fixture enables track " + part.source() + "." + field);
					for (int i = 1; i < keys.size(); i++) {
						check(keys.get(i - 1).tick() < keys.get(i).tick(), "strict scalar key order");
					}
					scalars += keys.size();
					tracks.put(part.source() + "." + field, keys);
				}
			}
			check(tracks.size() == 41 && scalars == 173, "complete donor field coverage");
		}

		float sample(Part part, String field, float tick) {
			check(tick >= 0 && tick < endTick, "oracle covers normal/tail playback before donor end80");
			List<DonorKey> keys = tracks.get(part.source() + "." + field);
			// The two-argument synthetic KeyFrame constructor uses INOUTSINE, not LINEAR.
			DonorKey before = new DonorKey(0, part.sourceDefault(field), "INOUTSINE");
			DonorKey last = keys.getLast();
			DonorKey after = new DonorKey(endTick, last.value(), last.ease());
			for (DonorKey key : keys) {
				if (key.tick() <= (int) tick) before = key;
				else { after = key; break; }
			}
			float fraction = (tick - before.tick()) / (after.tick() - before.tick());
			float value = before.value() + ease(before.ease(), fraction) * (after.value() - before.value());
			return switch (field) {
				case "pitch", "yaw", "roll", "bend" -> wrapRadians(value);
				default -> value;
			};
		}
	}

	private static float wrapRadians(float value) {
		double wrapped = (value + Math.PI) % (2 * Math.PI);
		if (wrapped < 0) wrapped += 2 * Math.PI;
		return (float) (wrapped - Math.PI);
	}

	private static float ease(String name, float u) {
		// These formulas come from the pinned 0.4 Easing.java, not the current Gecko easing code.
		float c2 = 1.70158F * 1.525F;
		float c5 = (float) (2 * Math.PI / 4.5);
		return switch (name) {
			case "LINEAR" -> u;
			case "INOUTSINE" -> (float) ((1 - Math.cos(Math.PI * u)) / 2);
			case "INOUTQUAD" -> (float) (u < 0.5F ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2);
			case "INOUTCUBIC" -> (float) (u < 0.5F ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2);
			case "OUTCUBIC" -> (float) (1 - Math.pow(1 - u, 3));
			case "INOUTQUINT" -> (float) (u < 0.5F ? 16 * u * u * u * u * u : 1 - Math.pow(-2 * u + 2, 5) / 2);
			case "OUTQUINT" -> (float) (1 - Math.pow(1 - u, 5));
			case "INOUTBACK" -> (float) (u < 0.5F
					? Math.pow(2 * u, 2) * ((c2 + 1) * 2 * u - c2) / 2
					: (Math.pow(2 * u - 2, 2) * ((c2 + 1) * (2 * u - 2) + c2) + 2) / 2);
			case "INOUTELASTIC" -> (float) (u == 0 ? 0 : u == 1 ? 1 : u < 0.5F
					? -Math.pow(2, 20 * u - 10) * Math.sin((20 * u - 11.125) * c5) / 2
					: Math.pow(2, -20 * u + 10) * Math.sin((20 * u - 11.125) * c5) / 2 + 1);
			default -> throw new AssertionError("unhandled donor easing " + name);
		};
	}

	private static void zero(Vector3f vector, String label) {
		near(vector.x(), 0, label + " X");
		near(vector.y(), 0, label + " Y");
		near(vector.z(), 0, label + " Z");
	}

	private static void near(float actual, float expected, String label) {
		float tolerance = 0.00001F + 8 * Math.ulp(expected);
		if (!Float.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
			throw new AssertionError(label + ": expected " + expected + ", got " + actual
					+ " (float32 tolerance " + tolerance + ")");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
