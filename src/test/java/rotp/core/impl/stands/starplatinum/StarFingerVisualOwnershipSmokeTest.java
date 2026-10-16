package rotp.core.impl.stands.starplatinum;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.core.client.entityanim.RotpAnimDefinition;
import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.client.entityrender.parsemodel.gecko.GeckoModelFormat;

import net.minecraft.client.model.geom.ModelPart;

public final class StarFingerVisualOwnershipSmokeTest {
	private static final Set<String> PRODUCTION_BONES = Set.of(
			"body_rot", "body", "torso", "torso_bend", "head", "head_rot",
			"left_arm", "left_arm_bend", "right_arm", "right_arm_bend",
			"left_leg", "left_leg_bend", "right_leg", "right_leg_bend",
			"hidden.finger");
	private static final Set<String> LEGACY_BONES = Set.of(
			"body", "left_arm_xrot", "left_arm_bend", "right_arm_xrot",
			"right_arm_bend", "left_leg_xrot", "left_leg_bend",
			"right_leg_xrot", "right_leg_bend", "front_fabric",
			"back_fabric", "torso_bend", "root", "head", "left_arm",
			"right_arm", "left_leg", "right_leg");

	private StarFingerVisualOwnershipSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		Path root = Path.of(System.getProperty("user.dir"));
		Path animationPath = root.resolve(
				"src/main/resources/assets/jojo_ripples/stand_skins/"
				+ "star_platinum/assets/jojo_ripples/animations/"
				+ "star_platinum.animation.json");
		JsonObject animations = requireObject(
				readObject(animationPath), "animations");

		JsonObject production = requireObject(animations, "star_finger");
		JsonObject productionBones = requireObject(production, "bones");
		JsonObject finger = requireObject(productionBones, "hidden.finger");
		JsonObject fingerScale = requireObject(finger, "scale");
		check(fingerScale.keySet().equals(Set.of(
				"0.0", "0.125", "0.625", "0.75", "1.25"))
				&& "query.extendablePartLength + 1".equals(
						fingerScale.getAsJsonObject("0.625")
								.getAsJsonArray("vector").get(1).getAsString())
				&& "query.extendablePartLength + 1".equals(
						fingerScale.getAsJsonObject("0.75")
								.getAsJsonArray("vector").get(1).getAsString()),
				"star_finger must extend the right-arm model finger");
		check(PRODUCTION_BONES.equals(productionBones.keySet()),
				"star_finger pose channels changed unexpectedly");
		check(production.has("animation_length")
				&& production.get("animation_length").getAsFloat() == 1.3F,
				"star_finger animation length changed");

		JsonObject timeline = requireObject(production, "timeline");
		check(timeline.keySet().equals(Set.of("0.0", "0.125", "0.75"))
				&& "phase = WINDUP;".equals(
						timeline.get("0.0").getAsString())
				&& "phase = PERFORM;".equals(
						timeline.get("0.125").getAsString())
				&& "phase = RECOVERY;".equals(
						timeline.get("0.75").getAsString()),
				"star_finger phase timeline changed");

		JsonObject legacy = requireObject(animations, "starFinger");
		JsonObject legacyBones = requireObject(legacy, "bones");
		check(legacy.keySet().equals(Set.of("loop", "bones"))
				&& "hold_on_last_frame".equals(
						legacy.get("loop").getAsString())
				&& LEGACY_BONES.equals(legacyBones.keySet())
				&& !legacyBones.has("hidden.finger"),
				"legacy starFinger compatibility animation changed");
		legacyPoseComposesLike116(root, legacy);

		String ability = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/"
				+ "starplatinum/StarFingerAbility.java"));
		check(ability.contains(
				"ActionAnimIdentifier.getOrCreate(\"starFinger\", false)")
				&& !ability.contains("getOrCreate(\"star_finger\""),
				"Star Finger must hold the 1.16 starFinger pose, not the clip that stretches hidden.finger");
		check(ability.contains("new SPStarFingerEntity(stand, level())")
				&& ability.contains("addProjectileWithStandStats(starFinger)"),
				"Star Finger hit entity spawn is missing");

		String renderers = read(root.resolve(
				"src/main/java/rotp/core/client/"
				+ "ModEntityTypeRenderers.java"));
		check(renderers.contains(
				"event.registerEntityRenderer(ModEntityTypes.SP_STAR_FINGER.get(), "
				+ "SPStarFingerRenderer::new)"),
				"Star Finger renderer registration is missing");

		// 1.16 SPStarFingerRenderer drew the finger for every viewer and camera that can see Stands
		String renderer = read(root.resolve(
				"src/main/java/rotp/core/impl/stands/starplatinum/client/"
				+ "SPStarFingerRenderer.java"));
		check(!renderer.contains("getCameraType()")
				&& !renderer.contains("getCameraEntity()")
				&& renderer.contains("!entity.isInvisible() || !entity.isInvisibleTo(minecraft.player)"),
				"the Star Finger projectile must be drawn for every viewer and camera, as 1.16 drew it");
		System.out.println("Star Finger smoke test passed: 1.16 starFinger pose without hidden.finger, head and both"
				+ " forearms composed like 1.16 at 4 looks, projectile drawn for every viewer and camera");
	}

	/**
	 * 1.16 StarPlatinumModel hung the head from the root and the arms from body > upperPart, so the starFinger
	 * pose turned the arms with the body's -27.5 yaw but left the head on the look direction. The port's head
	 * hangs from the body: its composed rotation on the baked geometry must still be the donor's.
	 */
	private static void legacyPoseComposesLike116(Path root, JsonObject legacyClip) {
		KeyframesMolangEngine.init();
		RotpAnimDefinition clip = ParseGeckoAnims.parseAnim(legacyClip);
		JsonObject geo = readObject(root.resolve("src/main/resources/assets/jojo_ripples/stand_skins/"
				+ "star_platinum/assets/jojo_ripples/geo/star_platinum.geo.json"));
		float[][] looks = { { 0, 0 }, { -30, 40 }, { 45, -60 }, { 80, 120 } };
		for (float[] look : looks) {
			ModelPart model = GeckoModelFormat.parseGeckoModel(geo).bakeRoot();
			ModelPart body = model.getChild("stand_pos").getChild("body_rot").getChild("body");
			ModelPart torso = body.getChild("torso");
			ModelPart torsoBend = torso.getChild("torso_bend");
			ModelPart rightArmXRot = torsoBend.getChild("right_arm_xrot");
			ModelPart rightArm = rightArmXRot.getChild("right_arm");
			ModelPart rightBend = rightArm.getChild("right_arm_bend");
			ModelPart leftArmXRot = torsoBend.getChild("left_arm_xrot");
			ModelPart leftArm = leftArmXRot.getChild("left_arm");
			ModelPart leftBend = leftArm.getChild("left_arm_bend");

			AnimFramePose pose = clip.calcAnimPose(AnimMolangVariables.set(look[0], look[1], 0), null, 0, 1,
					new AnimFramePose(), new Vector3f());
			check(pose.getIfPresent("hidden.finger") == null,
					"starFinger animates hidden.finger: the model would draw a finger beside the projectile");
			String[] bones = { "body", "torso_bend", "head", "right_arm_xrot", "right_arm", "right_arm_bend",
					"left_arm_xrot", "left_arm", "left_arm_bend" };
			ModelPart head = torsoBend.getChild("head");
			ModelPart[] parts = { body, torsoBend, head, rightArmXRot, rightArm, rightBend,
					leftArmXRot, leftArm, leftBend };
			for (int i = 0; i < bones.length; i++) {
				ModelPartFrame frame = pose.getIfPresent(bones[i]);
				check(frame != null, "starFinger lost its 1.16 " + bones[i] + " key");
				frame.apply(parts[i]);
			}
			String at = " (head pitch " + look[0] + ", head yaw " + look[1] + ")";
			double[][] donorBody = zyx(0, -27.5F, 0);
			sameRotation(compose(body, torso, torsoBend, head), zyx(look[0], look[1], 0), "head" + at);
			sameRotation(compose(body, torso, torsoBend, rightArmXRot, rightArm, rightBend),
					multiply(donorBody, zyx(-62.5F, 0, 90)), "right forearm" + at);
			sameRotation(compose(body, torso, torsoBend, leftArmXRot, leftArm, leftBend),
					multiply(donorBody, multiply(zyx(0, 0, -45), zyx(0, 0, 35))), "left forearm" + at);
		}
	}

	private static double[][] compose(ModelPart... chain) {
		PoseStack stack = new PoseStack();
		for (ModelPart part : chain) {
			part.translateAndRotate(stack);
		}
		Matrix4f matrix = stack.last().pose();
		double[][] result = new double[3][3];
		Vector3f[] axes = { new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1) };
		for (int column = 0; column < 3; column++) {
			Vector3f axis = matrix.transformDirection(axes[column]);
			result[0][column] = axis.x;
			result[1][column] = axis.y;
			result[2][column] = axis.z;
		}
		return result;
	}

	/** The donor ModelRenderer turns around Z, then Y, then X. */
	private static double[][] zyx(float xDeg, float yDeg, float zDeg) {
		double x = Math.toRadians(xDeg), y = Math.toRadians(yDeg), z = Math.toRadians(zDeg);
		double[][] aroundX = { { 1, 0, 0 }, { 0, Math.cos(x), -Math.sin(x) }, { 0, Math.sin(x), Math.cos(x) } };
		double[][] aroundY = { { Math.cos(y), 0, Math.sin(y) }, { 0, 1, 0 }, { -Math.sin(y), 0, Math.cos(y) } };
		double[][] aroundZ = { { Math.cos(z), -Math.sin(z), 0 }, { Math.sin(z), Math.cos(z), 0 }, { 0, 0, 1 } };
		return multiply(aroundZ, multiply(aroundY, aroundX));
	}

	private static double[][] multiply(double[][] a, double[][] b) {
		double[][] result = new double[3][3];
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				for (int k = 0; k < 3; k++) result[row][column] += a[row][k] * b[k][column];
			}
		}
		return result;
	}

	private static void sameRotation(double[][] actual, double[][] expected, String what) {
		for (int row = 0; row < 3; row++) {
			for (int column = 0; column < 3; column++) {
				check(Math.abs(actual[row][column] - expected[row][column]) <= 0.0001,
						"starFinger " + what + " is not the 1.16 orientation: matrix[" + row + "][" + column
								+ "] expected " + expected[row][column] + ", got " + actual[row][column]);
			}
		}
	}

	private static JsonObject readObject(Path path) {
		try (Reader reader = Files.newBufferedReader(
				path, StandardCharsets.UTF_8)) {
			JsonElement json = JsonParser.parseReader(reader);
			check(json.isJsonObject(),
					"resource is not a JSON object: " + path);
			return json.getAsJsonObject();
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static JsonObject requireObject(JsonObject parent, String key) {
		JsonElement element = parent.get(key);
		check(element != null && element.isJsonObject(),
				"missing JSON object: " + key);
		return element.getAsJsonObject();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		}
		catch (IOException exception) {
			throw new AssertionError("failed to read " + path, exception);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
