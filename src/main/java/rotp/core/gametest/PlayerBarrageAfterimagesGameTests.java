package rotp.core.gametest;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
