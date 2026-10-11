package rotp.core.client.entityanim;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.joml.Vector3f;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
	private static final float DEG_TO_RAD = (float) (Math.PI / 180);
	private static final float EPSILON = 0.00001F;
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
		AnimFramePose pose = new AnimFramePose();
		Vector3f scratch = new Vector3f();
		for (float[] sample : SAMPLES) {
			animation.calcAnimPose(AnimMolangVariables.set(sample[0], 37, 0), null, 0.25F, 1, pose, scratch);
			String context = "head pitch " + sample[0];
			rotation(pose, "left_arm", sample[1], sample[2], sample[3], context);
			rotation(pose, "right_arm", sample[1], -sample[2], -sample[3], context);
			rotation(pose, "left_arm_xrot", 0, 0, 0, context);
			rotation(pose, "right_arm_xrot", 0, 0, 0, context);
			rotation(pose, "left_arm_bend", 0, 0, 60, context);
			rotation(pose, "right_arm_bend", 0, 0, -60, context);
			rotation(pose, "head_rot", sample[0], 37, 0, context);
			applied(pose, "left_arm_xrot", leftParent, 0, 0, 0, context);
			applied(pose, "right_arm_xrot", rightParent, 0, 0, 0, context);
			applied(pose, "left_arm", left, sample[1], sample[2], sample[3], context);
			applied(pose, "right_arm", right, sample[1], -sample[2], -sample[3], context);
		}
		// Reuse the parsed clip and scratch with a reversed pitch; queries must stay live.
		animation.calcAnimPose(AnimMolangVariables.set(-30, 0, 0), null, 0.25F, 1, pose, scratch);
		rotation(pose, "left_arm", -105, -7.5F, -37.5F, "live pitch reversal");
		System.out.println("Hierophant Green guard pose passed: nine donor pitch samples on parsed and baked HG bones, mirrored X/Y/Z, clamp endpoints, live pitch reversal and unchanged hold/head/bend channels");
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
