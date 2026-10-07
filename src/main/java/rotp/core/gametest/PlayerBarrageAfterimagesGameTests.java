package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.client.entityanim.barrage.AfterimageBodyTwist;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 drew player barrage afterimages in BarrageFistAfterimagesLayer with a separate model copy.
 * The port draws them at the TAIL of HumanoidModel#renderToBuffer, but the bent player path cancels at
 * HEAD, so it has to draw them itself. The render code is client-only; the wiring is read from the class files.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerBarrageAfterimagesGameTests {
	private PlayerBarrageAfterimagesGameTests() {}

	private static final String PLAYER_MIXIN = "rotp/core/mixin/client/v1_21_1_modelanim/player/HumanoidModelMixin";
	private static final String BARRAGE_MIXIN = "rotp/core/mixin/client/v1_21_1_modelanim/barrage/HumanoidModelMixin";
	private static final String SWINGS = "rotp/core/client/entityanim/barrage/BarrageSwings";
	private static final String BENDS = "rotp/core/client/entityanim/playerbend/PlayerModelBends";
	private static final String BEND_MODEL = "rotp/core/client/entityanim/playerbend/IPlayerBendModel";
	private static final String CALLBACK = "org/spongepowered/asm/mixin/injection/callback/CallbackInfo";
	private static final String POSE_STACK = "com/mojang/blaze3d/vertex/PoseStack";
	private static final String MODEL_PART = "net/minecraft/client/model/geom/ModelPart";
	private static final String TWIST = "rotp/core/client/entityanim/barrage/AfterimageBodyTwist";
	private static final String TWO_HANDED = "rotp/core/client/entityanim/barrage/TwoHandedBarrageLoopSwing";
	private static final String ANIM = "rotp/core/client/entityanim/RotpAnimDefinition";
	private static final String CLOCK = ANIM + "$ClipClock";
	private static final float HIP_PIVOT_Y = 12 / 16F;
	private static final String HELPER = "renderHumanoidAfterimages";
	private static final String[] PART_STATE = { "x", "y", "z", "xRot", "yRot", "zRot", "xScale", "yScale", "zScale", "visible" };
	private static final String[] BEND_PARTS = { "jojo_ripples$animMainBody", "jojo_ripples$animTorso", "jojo_ripples$animTorsoBend",
			"jojo_ripples$animRightArmBend", "jojo_ripples$animLeftArmBend", "jojo_ripples$animRightLegBend", "jojo_ripples$animLeftLegBend",
			"jojo_ripples$animRightItem", "jojo_ripples$animLeftItem", "jojo_ripples$animCapeBend" };

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void bentPlayerRenderDrawsAfterimagesBeforeCancel(GameTestHelper helper) {
		MethodNode render = method(classNode(PLAYER_MIXIN), "jojo_ripples$renderWithBends");
		int bent = index(render, firstCall(render, BENDS, "renderWithBends"));
		int push = index(render, firstCall(render, POSE_STACK, "pushPose"));
		int torso = index(render, firstCall(render, BENDS, "translateToAnimHand1"));
		int draw = index(render, firstCall(render, SWINGS, HELPER));
		int pop = index(render, firstCall(render, POSE_STACK, "popPose"));
		int cancel = index(render, firstCall(render, CALLBACK, "cancel"));
		helper.assertTrue(bent < draw && draw < cancel,
				"the bent player path must draw the barrage afterimages after the body and before ci.cancel()");
		helper.assertTrue(push < torso && torso < draw && draw < pop,
				"the afterimages must be drawn in the torso frame of the bent arms, inside a pushed pose");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tailHookDrawsHumanoidAfterimages(GameTestHelper helper) {
		MethodNode tail = method(classNode(BARRAGE_MIXIN), "jojo_ripples$thenRenderBarrageSwings");
		helper.assertTrue(firstCall(tail, SWINGS, HELPER) != null, "the TAIL hook must draw humanoid afterimages through " + HELPER);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void humanoidAfterimagesDrawOnceAndRestoreModel(GameTestHelper helper) {
		ClassNode swings = classNode(SWINGS);
		MethodNode draw = method(swings, HELPER);
		int consume = -1;
		for (AbstractInsnNode insn = draw.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
					&& field.owner.equals(SWINGS) && field.name.equals("currentlyRendering")) {
				helper.assertTrue(insn.getPrevious() != null && insn.getPrevious().getOpcode() == Opcodes.ACONST_NULL,
						HELPER + " must clear currentlyRendering");
				consume = index(draw, insn);
				break;
			}
		}
		int save = index(draw, firstCall(draw, SWINGS, "saveModelState"));
		int layer = index(draw, firstCall(draw, SWINGS, "renderLayerBarrage"));
		int restore = index(draw, lastCall(draw, SWINGS, "loadModelState"));
		helper.assertTrue(consume >= 0 && consume < layer,
				"afterimages must be drawn once per entity: the layers after the body (armor) must not draw them again");
		helper.assertTrue(save < layer && layer < restore,
				"the model pose and visibility must be saved before the afterimages and restored after them");

		MethodNode parts = method(swings, "humanoidStateParts");
		helper.assertTrue(firstCall(parts, MODEL_PART, "getAllParts") != null, "all model parts must be saved");
		for (String bend : BEND_PARTS) {
			helper.assertTrue(firstCall(parts, BEND_MODEL, bend) != null, "the bend part " + bend + " must be saved too");
		}
		MethodNode save0 = method(swings, "saveModelState");
		MethodNode load0 = method(swings, "loadModelState");
		for (String field : PART_STATE) {
			helper.assertTrue(hasField(save0, Opcodes.GETFIELD, field), "saveModelState must read ModelPart." + field);
			helper.assertTrue(hasField(load0, Opcodes.PUTFIELD, field), "loadModelState must restore ModelPart." + field);
		}
		helper.succeed();
	}

	/**
	 * 1.16 drew the afterimage arm of a swing as T(offset) * R(body yaw at that swing's clip tick) in the untwisted
	 * model: KosmXPlayerBarrageAfterimagesAnim.beforeSwingsRender undid the live body yaw, rotateBody added the swing's.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afterimageArmsTakeTheBodyTwistOfTheirOwnSwing(GameTestHelper helper) {
		// the two extreme poses of punch_barrage (port 'body' bone)
		float yawA = bodyYawDeg(helper, "0.1") * Mth.DEG_TO_RAD;
		float yawB = bodyYawDeg(helper, "0.2") * Mth.DEG_TO_RAD;
		helper.assertTrue(Math.abs(yawA + yawB) < 1.0E-4F && Math.abs(yawA - yawB) > 1,
				"Fixture: punch_barrage lost its opposite body twists, got " + yawA + " and " + yawB + " rad");
		Vector3f offset = new Vector3f(-0.21F, 0.13F, -0.8F);
		Vector3f origin = null;
		for (float live : new float[] { yawA, yawB, 0, yawA * 0.37F }) {
			Matrix4f[] port = { portArmFrame(live, offset, yawA), portArmFrame(live, offset, yawB) };
			float[] swing = { yawA, yawB };
			for (int i = 0; i < port.length; i++) {
				Matrix4f donor = donorArmFrame(-live, offset, -swing[i]);
				helper.assertTrue(port[i].equals(donor, 1.0E-5F),
						"1.16 draws the afterimage arm of a swing in the body twist of that swing (" + deg(swing[i])
								+ " deg here, live body at " + deg(live) + " deg): the arm points "
								+ deg(armAngle(port[i], donor)) + " deg away from the 1.16 direction and starts "
								+ port[i].transformPosition(new Vector3f()).distance(donor.transformPosition(new Vector3f()))
								+ " blocks away from the 1.16 shoulder offset");
			}
			float between = armAngle(port[0], port[1]);
			helper.assertTrue(Math.abs(between - Math.abs(yawA - yawB)) < 1.0E-4F,
					"1.16: afterimage arms of the two extreme swings point " + deg(Math.abs(yawA - yawB))
							+ " deg apart, got " + deg(between) + " deg (live body at " + deg(live) + " deg)");
			Vector3f at = port[0].transformPosition(new Vector3f());
			helper.assertTrue(origin == null || at.distance(origin) < 1.0E-5F,
					"1.16: the live body twist does not move the afterimages, got " + at + " against " + origin);
			origin = at;
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afterimageBodyTwistIsWiredIntoTheRender(GameTestHelper helper) {
		MethodNode all = method(classNode(SWINGS), HELPER);
		int push = index(all, firstCall(all, POSE_STACK, "pushPose"));
		int liveBody = index(all, firstCall(all, BEND_MODEL, "jojo_ripples$animMainBody"));
		int leave = index(all, firstCall(all, TWIST, "leaveCurrent"));
		int mul = index(all, firstCall(all, POSE_STACK, "mulPose"));
		int layer = index(all, firstCall(all, SWINGS, "renderLayerBarrage"));
		int pop = index(all, lastCall(all, POSE_STACK, "popPose"));
		helper.assertTrue(push < liveBody && liveBody < leave && leave < mul && mul < layer && layer < pop,
				HELPER + " must take the live body twist off the pose once, before any swing poses the model");

		MethodNode swing = method(classNode(TWO_HANDED), "poseAndRender");
		int translate = index(swing, firstCall(swing, POSE_STACK, "translate"));
		int animate = index(swing, firstCall(swing, ANIM, "animate"));
		int swingBody = index(swing, firstCall(swing, BEND_MODEL, "jojo_ripples$animMainBody"));
		int take = index(swing, firstCall(swing, TWIST, "takeSwing"));
		int twist = index(swing, firstCall(swing, POSE_STACK, "mulPose"));
		int render = index(swing, firstCall(swing, MODEL_PART, "render"));
		helper.assertTrue(translate < animate && animate < swingBody && swingBody < take && take < twist && twist < render,
				"an afterimage arm must be moved to its offset, posed for its swing and then turned by that pose's body twist");
		helper.succeed();
	}

	// 1.16 KosmXPlayerBarrageAnim.poseModel: getBarrageEffectLoopingTick; the times are in PlayerBarrageLoopClockGameTests
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void afterimageArmIsPosedForTheClipTickOfItsSwing(GameTestHelper helper) {
		MethodNode swing = method(classNode(TWO_HANDED), "poseAndRender");
		int time = index(swing, firstCall(swing, CLOCK, "afterimageSeconds"));
		int animate = index(swing, firstCall(swing, ANIM, "animate"));
		helper.assertTrue(time < animate,
				"a ping-pong barrage clip must pose an afterimage arm at the clip time of its own swing");
		helper.succeed();
	}

	/**
	 * 1.16 KosmXPlayerBarrageAfterimagesAnim: 3 afterimages a tick (swingsToAdd) spread by up to 0.625 (maxSwingOffset).
	 * The port counts barrageSwingsPerSecond / 20 a tick and spreads by 1 - barragePrecision / 40; a Stand overwrites
	 * both with its stats, so the defaults are the player's.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerAfterimagesKeepThe116CountAndSpread(GameTestHelper helper) {
		MethodNode init = method(classNode(SWINGS), "<init>");
		float perSecond = floatDefault(init, "barrageSwingsPerSecond");
		float precision = floatDefault(init, "barragePrecision");
		helper.assertTrue(Math.abs(perSecond / 20F - 3F) < 1.0E-5F,
				"1.16 adds 3 player barrage afterimages a tick, the port adds " + perSecond / 20F);
		helper.assertTrue(Math.abs(1 - precision / 40F - 0.625F) < 1.0E-5F,
				"1.16 spreads player barrage afterimages by up to 0.625, the port by up to " + (1 - precision / 40F));
		helper.succeed();
	}

	private static float floatDefault(MethodNode init, String field) {
		for (AbstractInsnNode insn = init.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode put && put.getOpcode() == Opcodes.PUTFIELD
					&& put.owner.equals(SWINGS) && put.name.equals(field)
					&& insn.getPrevious() instanceof LdcInsnNode ldc && ldc.cst instanceof Float value) {
				return value;
			}
		}
		throw fail("BarrageSwings has no constant default for " + field);
	}

	// LivingRenderer model flip, then PlayerModelBends.translateToAnimHand1 (body bone about the hip),
	// BarrageSwings.renderHumanoidAfterimages and TwoHandedBarrageLoopSwing.poseAndRender
	private static Matrix4f portArmFrame(float liveBodyYaw, Vector3f offset, float swingBodyYaw) {
		return new Matrix4f().scale(-1, -1, 1).translate(0, -1.501F, 0)
				.translate(0, HIP_PIVOT_Y, 0).rotateY(liveBodyYaw).translate(0, -HIP_PIVOT_Y, 0)
				.rotate(AfterimageBodyTwist.leaveCurrent(liveBodyYaw))
				.translate(offset)
				.rotate(AfterimageBodyTwist.takeSwing(swingBodyYaw));
	}

	// 1.16 player-animation-lib setupRotations (whole body, before the flip), beforeSwingsRender,
	// ArmBarrageSwing.poseAndRender and KosmXPlayerBarrageAnim.rotateBody
	private static Matrix4f donorArmFrame(float liveTorsoYaw, Vector3f offset, float swingTorsoYaw) {
		return new Matrix4f().rotateY(liveTorsoYaw)
				.scale(-1, -1, 1).translate(0, -1.501F, 0)
				.rotateY(liveTorsoYaw)
				.translate(offset)
				.rotateY(-swingTorsoYaw);
	}

	private static float armAngle(Matrix4f a, Matrix4f b) {
		return a.transformDirection(new Vector3f(0, 0, -1)).angle(b.transformDirection(new Vector3f(0, 0, -1)));
	}

	private static float deg(float radians) {
		return radians * Mth.RAD_TO_DEG;
	}

	private static float bodyYawDeg(GameTestHelper helper, String time) {
		String path = "/assets/jojo_ripples/animations/hamon.animation.json";
		try (InputStream in = PlayerBarrageAfterimagesGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing asset " + path);
			JsonObject clip = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject()
					.getAsJsonObject("animations").getAsJsonObject("punch_barrage");
			JsonObject key = clip.getAsJsonObject("bones").getAsJsonObject("body").getAsJsonObject("rotation")
					.getAsJsonObject(time);
			helper.assertTrue(key != null, "Fixture: punch_barrage has no body rotation key at " + time + " s");
			return key.getAsJsonArray("vector").get(1).getAsFloat();
		}
		catch (IOException e) {
			throw fail("could not read " + path + ": " + e);
		}
	}

	private static boolean hasField(MethodNode method, int opcode, String name) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode field && field.getOpcode() == opcode
					&& field.owner.equals(MODEL_PART) && field.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	private static int index(MethodNode method, AbstractInsnNode insn) {
		if (insn == null) {
			return Integer.MIN_VALUE / 2;
		}
		return method.instructions.indexOf(insn);
	}

	private static MethodInsnNode firstCall(MethodNode method, String owner, String name) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
				return call;
			}
		}
		throw fail(method.name + " does not call " + owner + "." + name);
	}

	private static MethodInsnNode lastCall(MethodNode method, String owner, String name) {
		for (AbstractInsnNode insn = method.instructions.getLast(); insn != null; insn = insn.getPrevious()) {
			if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
				return call;
			}
		}
		throw fail(method.name + " does not call " + owner + "." + name);
	}

	private static MethodNode method(ClassNode owner, String name) {
		for (MethodNode method : owner.methods) {
			if (method.name.equals(name)) {
				return method;
			}
		}
		throw fail("no method " + owner.name + "." + name);
	}

	private static ClassNode classNode(String internalName) {
		InputStream found = PlayerBarrageAfterimagesGameTests.class.getResourceAsStream("/" + internalName + ".class");
		if (found == null) {
			found = PlayerBarrageAfterimagesGameTests.class.getClassLoader().getResourceAsStream(internalName + ".class");
		}
		try (InputStream in = found) {
			if (in == null) {
				throw fail("no class file for " + internalName);
			}
			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
			return node;
		}
		catch (IOException e) {
			throw fail("could not read " + internalName + ": " + e);
		}
	}

	private static GameTestAssertException fail(String message) {
		return new GameTestAssertException(message);
	}
}
