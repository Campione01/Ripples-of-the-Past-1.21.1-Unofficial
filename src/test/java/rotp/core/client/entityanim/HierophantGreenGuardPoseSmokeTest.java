package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.core.client.entityanim.gecko.ParseGeckoAnims;
import rotp.core.client.entityanim.molang.AnimMolangQuery.AnimMolangVariables;
import rotp.core.client.entityanim.molang.KeyframesMolangEngine;
import rotp.core.client.entityanim.pose.AnimFramePose;
import rotp.core.client.entityanim.pose.AnimFramePose.ModelPartFrame;
import rotp.core.client.entityrender.parsemodel.gecko.GeckoModelFormat;

import net.minecraft.client.model.geom.ModelPart;

/** Numeric resource regression; rendered guard acceptance still needs a client. */
public final class HierophantGreenGuardPoseSmokeTest {
	private static final String ASSET = "/assets/jojo_ripples/stand_skins/hierophant_green/assets/jojo_ripples/animations/hierophant_green.animation.json";
	private static final String GEOMETRY = "/assets/jojo_ripples/stand_skins/hierophant_green/assets/jojo_ripples/geo/hierophant_green.geo.json";
	private static final String DEFAULT_ASSET = "/assets/jojo_ripples/animations/stand_default.animation.json";
	private static final float DEG_TO_RAD = (float) (Math.PI / 180);
	private static final float EPSILON = 0.00001F;
	private static final float POSE_EPSILON = 0.0001F;
	// Donor forearm: fixed angles, pivot 4 below the shoulder, box 6 long.
	private static final float[] LEFT_FOREARM = { -90, -30, 90 };
	private static final float[] RIGHT_FOREARM = { -90, 30, -90 };
	private static final float ELBOW = 4;
	private static final float FOREARM_LENGTH = 6;
	private static final String[] POSE_PARTS = {
			"forearm axis X", "forearm axis Y", "forearm axis Z",
			"forearm side X", "forearm side Y", "forearm side Z",
			"hand tip X", "hand tip Y", "hand tip Z" };
	// Independent donor oracles: head pitch, arm X, left Y, left Z (right Y/Z mirror).
	private static final float[][] SAMPLES = {
			{ -90, -120, -15, -30 },
			{ -60, -120, -15, -30 },
			{ -30, -105, -7.5F, -37.5F },
			{ -15.5F, -97.75F, -3.875F, -41.125F },
			{ 0, -90, 0, -45 },
			{ 15.5F, -82.25F, 3.875F, -41.125F },
			{ 30, -75, 7.5F, -37.5F },
			{ 60, -60, 15, -30 },
			{ 90, -60, 15, -30 }
	};

	private HierophantGreenGuardPoseSmokeTest() {}

	public static void main(String[] args) throws IOException {
		KeyframesMolangEngine.init();
		JsonObject clip;
		try (InputStream in = HierophantGreenGuardPoseSmokeTest.class.getResourceAsStream(ASSET)) {
			check(in != null, "Missing Hierophant Green animation resource");
			clip = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("block");
		}
		check(clip != null, "Missing block clip");
		check("hold_on_last_frame".equals(clip.get("loop").getAsString()), "Guard hold behavior changed");
		near(clip.get("animation_length").getAsFloat(), 0.25F, "guard clip length");
		RotpAnimDefinition animation = ParseGeckoAnims.parseAnim(clip);
		ModelPart torso;
		try (InputStream in = HierophantGreenGuardPoseSmokeTest.class.getResourceAsStream(GEOMETRY)) {
			check(in != null, "Missing Hierophant Green geometry resource");
			ModelPart root = GeckoModelFormat.parseGeckoModel(
					JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).bakeRoot();
			torso = root.getChild("stand_pos").getChild("body_rot").getChild("body")
					.getChild("torso").getChild("torso_bend");
		}
		ModelPart leftParent = torso.getChild("left_arm_xrot");
		ModelPart rightParent = torso.getChild("right_arm_xrot");
		check(leftParent.x > 0 && rightParent.x < 0, "Baked arm sides no longer match the donor");
		ModelPart left = leftParent.getChild("left_arm");
		ModelPart right = rightParent.getChild("right_arm");
		ModelPart leftBend = left.getChild("left_arm_bend");
		ModelPart rightBend = right.getChild("right_arm_bend");
		check(leftBend.x == 0 && leftBend.y == ELBOW && leftBend.z == 0
				&& rightBend.x == 0 && rightBend.y == ELBOW && rightBend.z == 0,
				"Baked elbow pivots no longer match the donor");
		AnimFramePose pose = new AnimFramePose();
		Vector3f scratch = new Vector3f();
		for (float[] sample : SAMPLES) {
			animation.calcAnimPose(AnimMolangVariables.set(sample[0], 37, 0), null, 0.25F, 1, pose, scratch);
			String context = "head pitch " + sample[0];
			rotation(pose, "left_arm", sample[1], sample[2], sample[3], context);
			rotation(pose, "right_arm", sample[1], -sample[2], -sample[3], context);
			rotation(pose, "left_arm_xrot", 0, 0, 0, context);
			rotation(pose, "right_arm_xrot", 0, 0, 0, context);
			rotation(pose, "head_rot", sample[0], 37, 0, context);
			applied(pose, "left_arm_xrot", leftParent, 0, 0, 0, context);
			applied(pose, "right_arm_xrot", rightParent, 0, 0, 0, context);
			applied(pose, "left_arm", left, sample[1], sample[2], sample[3], context);
			applied(pose, "right_arm", right, sample[1], -sample[2], -sample[3], context);
			samePose(forearm(pose, "left_arm", left, leftBend),
					donorForearm(sample[1], sample[2], sample[3], LEFT_FOREARM), context + " left");
			samePose(forearm(pose, "right_arm", right, rightBend),
					donorForearm(sample[1], -sample[2], -sample[3], RIGHT_FOREARM), context + " right");
			rotation(pose, "left_arm_bend", LEFT_FOREARM[0], LEFT_FOREARM[1], LEFT_FOREARM[2], context);
			rotation(pose, "right_arm_bend", RIGHT_FOREARM[0], RIGHT_FOREARM[1], RIGHT_FOREARM[2], context);
		}
		// Level look is the shared humanoid guard, which is written with the other arm roll.
		RotpAnimDefinition shared;
		try (InputStream in = HierophantGreenGuardPoseSmokeTest.class.getResourceAsStream(DEFAULT_ASSET)) {
			check(in != null, "Missing default Stand animation resource");
			shared = ParseGeckoAnims.parseAnim(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("block"));
		}
		AnimFramePose sharedPose = new AnimFramePose();
		shared.calcAnimPose(AnimMolangVariables.set(0, 37, 0), null, 0.25F, 1, sharedPose, new Vector3f());
		float[] sharedLeft = forearm(sharedPose, "left_arm", left, leftBend);
		float[] sharedRight = forearm(sharedPose, "right_arm", right, rightBend);
		check(sharedLeft[1] < -0.5F && sharedLeft[7] < -3, "Default guard no longer raises the left forearm");
		animation.calcAnimPose(AnimMolangVariables.set(0, 37, 0), null, 0.25F, 1, pose, scratch);
		samePose(forearm(pose, "left_arm", left, leftBend), sharedLeft, "level look against default guard left");
		samePose(forearm(pose, "right_arm", right, rightBend), sharedRight, "level look against default guard right");
		// Reuse the parsed clip and scratch with a reversed pitch; queries must stay live.
		animation.calcAnimPose(AnimMolangVariables.set(-30, 0, 0), null, 0.25F, 1, pose, scratch);
		rotation(pose, "left_arm", -105, -7.5F, -37.5F, "live pitch reversal");
		System.out.println("Hierophant Green guard pose passed: nine donor pitch samples on parsed and baked HG bones, mirrored X/Y/Z, clamp endpoints, combined arm and forearm axis, roll and hand tip against the donor and against the default guard at level look, live pitch reversal and unchanged hold/head channels");
	}

	/** Forearm axis, side axis and hand tip (model pixels from the shoulder) through the baked parts. */
	private static float[] forearm(AnimFramePose pose, String armBone, ModelPart arm, ModelPart bend) {
		pose.getIfPresent(armBone).apply(arm);
		pose.getIfPresent(armBone + "_bend").apply(bend);
		PoseStack stack = new PoseStack();
		arm.translateAndRotate(stack);
		bend.translateAndRotate(stack);
		Matrix4f matrix = stack.last().pose();
		Vector3f axis = matrix.transformDirection(new Vector3f(0, 1, 0));
		Vector3f side = matrix.transformDirection(new Vector3f(1, 0, 0));
		Vector3f tip = matrix.transformPosition(new Vector3f(0, FOREARM_LENGTH / 16, 0)).mul(16)
				.sub(arm.x, arm.y, arm.z);
		return new float[] { axis.x, axis.y, axis.z, side.x, side.y, side.z, tip.x, tip.y, tip.z };
	}

	private static float[] donorForearm(float armX, float armY, float armZ, float[] foreArm) {
		double[][] upper = zyx(armX, armY, armZ);
		double[][] lower = multiply(upper, zyx(foreArm[0], foreArm[1], foreArm[2]));
		float[] result = new float[9];
		for (int row = 0; row < 3; row++) {
			result[row] = (float) lower[row][1];
			result[3 + row] = (float) lower[row][0];
			result[6 + row] = (float) (upper[row][1] * ELBOW + lower[row][1] * FOREARM_LENGTH);
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

	private static void samePose(float[] actual, float[] expected, String context) {
		for (int i = 0; i < POSE_PARTS.length; i++) {
			check(Float.isFinite(actual[i]) && Math.abs(actual[i] - expected[i]) <= POSE_EPSILON,
					context + " " + POSE_PARTS[i] + ": expected " + expected[i] + ", got " + actual[i]);
		}
	}

	private static void applied(AnimFramePose pose, String bone, ModelPart part, float x, float y, float z, String context) {
		pose.getIfPresent(bone).apply(part);
		near(part.xRot, x * DEG_TO_RAD, context + " baked " + bone + " X");
		near(part.yRot, y * DEG_TO_RAD, context + " baked " + bone + " Y");
		near(part.zRot, z * DEG_TO_RAD, context + " baked " + bone + " Z");
	}

	private static void rotation(AnimFramePose pose, String bone, float x, float y, float z, String context) {
		ModelPartFrame frame = pose.getIfPresent(bone);
		check(frame != null, context + " missing " + bone);
		near(frame.rotationOffset.x(), x * DEG_TO_RAD, context + " " + bone + " X");
		near(frame.rotationOffset.y(), y * DEG_TO_RAD, context + " " + bone + " Y");
		near(frame.rotationOffset.z(), z * DEG_TO_RAD, context + " " + bone + " Z");
	}

	private static void near(float actual, float expected, String message) {
		check(Float.isFinite(actual) && Math.abs(actual - expected) <= EPSILON,
				message + ": expected " + expected + ", got " + actual);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
