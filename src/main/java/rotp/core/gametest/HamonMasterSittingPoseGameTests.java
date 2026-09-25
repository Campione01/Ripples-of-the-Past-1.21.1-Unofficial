package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonMasterModel sat the Hamon Master in the meditation clip at half speed (KosmX applier, bent
 * outer layer, cape spread on the ground). The port poses it through PreFrameEntityAnimCalc and applies
 * the pose in HamonMasterModel. Both are client-only, so their bytecode is read as a resource.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonMasterSittingPoseGameTests {
	private HamonMasterSittingPoseGameTests() {}

	private static final String CALC = "rotp/core/client/entityanim/PreFrameEntityAnimCalc";
	private static final String MODEL = "rotp/core/impl/powers/hamon/client/HamonMasterModel";
	private static final String MASTER = "rotp/core/impl/powers/hamon/entity/HamonMasterEntity";
	private static final String FILL = CALC + ".fillHamonMasterPose(L" + CALC + "$LivingAnimState;L" + MASTER + ";F)V";
	private static final String MODEL_PART = "Lnet/minecraft/client/model/geom/ModelPart;";

	private static final int LDC = 0x12;
	private static final int LDC_W = 0x13;
	private static final int GETFIELD = 0xB4;
	private static final int PUTFIELD = 0xB5;
	private static final int INVOKESPECIAL = 0xB7;
	private static final int INVOKESTATIC = 0xB8;
	private static final int INVOKEINTERFACE = 0xB9;
	private static final int INSTANCEOF = 0xC1;

	@GameTest(template = "empty")
	public static void hamonMasterGetsItsOwnPose(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, CALC, "getLivingPose",
				"(Lnet/minecraft/world/entity/LivingEntity;FZ)Lrotp/core/client/entityanim/pose/AnimFramePose;");
		boolean gate = false;
		boolean fill = false;
		for (Insn insn : code) {
			if (insn.is(INSTANCEOF, MASTER)) gate = true;
			if (insn.is(INVOKESTATIC, FILL)) fill = true;
		}
		helper.assertTrue(gate, "PreFrameEntityAnimCalc.getLivingPose no longer picks out the Hamon Master");
		helper.assertTrue(fill, "PreFrameEntityAnimCalc.getLivingPose no longer fills the Hamon Master pose");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hamonMasterPlaysMeditationAtHalfSpeed(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, CALC, "fillHamonMasterPose",
				"(L" + CALC + "$LivingAnimState;L" + MASTER + ";F)V");
		boolean clip = false;
		boolean halfSpeed = false;
		boolean seated = false;
		boolean ticks = false;
		int stateFields = 0;
		for (Insn insn : code) {
			if (insn.isLdc() && "meditation".equals(insn.constant())) clip = true;
			if (insn.isLdc() && Float.valueOf(0.5F).equals(insn.constant())) halfSpeed = true;
			if (insn.isLdc() && Float.valueOf(35.0F).equals(insn.constant())) seated = true;
			if (insn.op() == GETFIELD && insn.ref() != null && insn.ref().endsWith(".tickCountI")) ticks = true;
			if (insn.op() == PUTFIELD && insn.ref() != null && insn.ref().startsWith(CALC + "$LivingAnimState.")) stateFields++;
		}
		helper.assertTrue(clip, "The Hamon Master no longer plays the Hamon 'meditation' clip");
		helper.assertTrue(halfSpeed, "The Hamon Master clip no longer plays at the 1.16 half speed");
		helper.assertTrue(seated, "The Hamon Master no longer starts at the seated loop point (35 ticks)");
		helper.assertTrue(ticks, "The Hamon Master clip time no longer follows its tickCount");
		helper.assertTrue(stateFields >= 3, "fillHamonMasterPose must set animSet, animId and time, set " + stateFields);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hamonMasterModelAppliesPoseAndCape(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, MODEL, "setupAnim", "(L" + MASTER + ";FFFFF)V");
		int reset = -1;
		int vanilla = -1;
		int readPose = -1;
		int apply = -1;
		int playing = -1;
		int capeFirst = -1;
		int capes = 0;
		for (int i = 0; i < code.size(); i++) {
			Insn insn = code.get(i);
			String ref = insn.ref() != null ? insn.ref() : "";
			if (insn.op() == INVOKESTATIC && ref.contains("EntityRenderState.resetPose(")) reset = i;
			if (insn.op() == INVOKESPECIAL && ref.startsWith("net/minecraft/client/model/HumanoidModel.setupAnim(")) vanilla = i;
			if (insn.op() == INVOKEINTERFACE && ref.contains(".jojo_ripples$getModelPose(")) readPose = i;
			if (insn.op() == INVOKEINTERFACE && ref.contains(".jojo_ripples$setupHumanoidAnim(")) apply = i;
			if (insn.op() == INVOKEINTERFACE && ref.contains(".jojo_rippes$isPlayingAnimation(")) playing = i;
			if (insn.op() == INVOKESTATIC && ref.startsWith(MODEL + ".setDegrees(")) {
				if (capeFirst < 0) capeFirst = i;
				capes++;
			}
		}
		helper.assertTrue(reset >= 0 && reset < vanilla, "HamonMasterModel.setupAnim must reset the pose before the vanilla setup");
		helper.assertTrue(vanilla >= 0 && vanilla < readPose && readPose < apply,
				"HamonMasterModel.setupAnim must apply the precomputed pose after the vanilla setup");
		helper.assertTrue(playing > apply && capeFirst > playing,
				"HamonMasterModel.setupAnim must spread the cape only while the pose plays");
		helper.assertTrue(capes == 6, "The 1.16 sitting cape sets six cape parts, found " + capes);
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hamonMasterOuterLayerRendersBent(GameTestHelper helper) {
		List<Insn> init = methodCode(helper, MODEL, "<init>", "(" + MODEL_PART + ")V");
		int bends = 0;
		for (Insn insn : init) {
			if (insn.op() == INVOKEINTERFACE && insn.ref() != null && insn.ref().contains(".jojo_ripples$setBendBone(")) bends++;
		}
		helper.assertTrue(bends == 5, "Jacket, sleeves and pants must follow the limb bends, set " + bends);

		List<Insn> render = methodCode(helper, MODEL, "renderToBuffer",
				"(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V");
		boolean torsoBend = false;
		List<String> drawn = new ArrayList<>();
		for (Insn insn : render) {
			if (insn.is(INVOKESTATIC, "rotp/core/client/entityanim/playerbend/PlayerModelBends.rotateAndTranslateBack("
					+ MODEL_PART + "Lcom/mojang/blaze3d/vertex/PoseStack;)V")) torsoBend = true;
			if (insn.op() == GETFIELD && insn.ref() != null && insn.ref().endsWith(MODEL_PART)) {
				String field = insn.ref().substring(insn.ref().lastIndexOf('.') + 1, insn.ref().length() - MODEL_PART.length());
				drawn.add(field);
			}
		}
		helper.assertTrue(torsoBend, "The seated Hamon Master is no longer drawn through the torso bend");
		for (String part : List.of("leftLeg", "rightLeg", "body", "head", "leftArm", "rightArm",
				"hat", "jacket", "leftSleeve", "rightSleeve", "leftPants", "rightPants")) {
			helper.assertTrue(drawn.contains(part), "The seated Hamon Master no longer draws its " + part);
		}
		helper.succeed();
	}

	private record Insn(int op, String ref, Object constant) {
		boolean is(int opcode, String target) {
			return op == opcode && target.equals(ref);
		}

		boolean isLdc() {
			return op == LDC || op == LDC_W;
		}
	}

	// decodes the Code attribute of one method; member refs as owner.name+descriptor, class refs as names
	private static List<Insn> methodCode(GameTestHelper helper, String className, String name, String desc) {
		String path = className + ".class";
		InputStream raw = HamonMasterSittingPoseGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HamonMasterSittingPoseGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			Object[] values = new Object[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 3 -> values[i] = in.readInt();
				case 4 -> values[i] = Float.intBitsToFloat(in.readInt());
				case 5, 6 -> { in.readLong(); i++; }
				case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
				case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
				default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			String[] refs = new String[count];
			for (int i = 1; i < count; i++) {
				if (tag[i] == 9 || tag[i] == 10 || tag[i] == 11) {
					refs[i] = utf[a[a[i]]] + "." + utf[a[b[i]]] + utf[b[b[i]]];
				}
				else if (tag[i] == 7) {
					refs[i] = utf[a[i]];
				}
				else if (tag[i] == 8) {
					values[i] = utf[a[i]];
				}
			}
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.readUnsignedShort();
			in.skipBytes(2 * in.readUnsignedShort());
			skipMembers(in);
			int methods = in.readUnsignedShort();
			for (int m = 0; m < methods; m++) {
				in.readUnsignedShort();
				String mName = utf[in.readUnsignedShort()];
				String mDesc = utf[in.readUnsignedShort()];
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					String attr = utf[in.readUnsignedShort()];
					byte[] body = new byte[in.readInt()];
					in.readFully(body);
					if (name.equals(mName) && desc.equals(mDesc) && "Code".equals(attr)) {
						return decode(body, refs, values);
					}
				}
			}
			helper.fail("Method " + className + "." + name + desc + " not found");
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		return List.of();
	}

	private static void skipMembers(DataInputStream in) throws IOException {
		int n = in.readUnsignedShort();
		for (int i = 0; i < n; i++) {
			in.skipBytes(6);
			int attrs = in.readUnsignedShort();
			for (int t = 0; t < attrs; t++) {
				in.readUnsignedShort();
				in.skipBytes(in.readInt());
			}
		}
	}

	private static List<Insn> decode(byte[] codeAttr, String[] refs, Object[] values) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttr));
		in.skipBytes(4);
		byte[] code = new byte[in.readInt()];
		in.readFully(code);
		List<Insn> out = new ArrayList<>();
		int pc = 0;
		while (pc < code.length) {
			int op = code[pc] & 0xFF;
			String ref = null;
			Object constant = null;
			if ((op >= 0xB2 && op <= 0xB9) || op == 0xBB || op == 0xC0 || op == INSTANCEOF) {
				ref = refs[((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)];
			}
			else if (op == LDC) {
				constant = values[code[pc + 1] & 0xFF];
			}
			else if (op == LDC_W) {
				constant = values[((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)];
			}
			out.add(new Insn(op, ref, constant));
			pc += length(code, pc, op);
		}
		return out;
	}

	private static int length(byte[] code, int pc, int op) {
		switch (op) {
		case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC:
			return 2;
		case 0x11, 0x13, 0x14, 0x84, 0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7:
			return 3;
		case 0xC5:
			return 4;
		case 0xB9, 0xBA, 0xC8, 0xC9:
			return 5;
		case 0xC4:
			return (code[pc + 1] & 0xFF) == 0x84 ? 6 : 4;
		case 0xAA: {
			int p = (pc + 4) & ~3;
			int low = readInt(code, p + 4);
			int high = readInt(code, p + 8);
			return p + 12 + 4 * (high - low + 1) - pc;
		}
		case 0xAB: {
			int p = (pc + 4) & ~3;
			return p + 8 + 8 * readInt(code, p + 4) - pc;
		}
		default:
			// branches and field/method refs carry a u2 operand
			return (op >= 0x99 && op <= 0xA8) || (op >= 0xB2 && op <= 0xB8) ? 3 : 1;
		}
	}

	private static int readInt(byte[] code, int p) {
		return ((code[p] & 0xFF) << 24) | ((code[p + 1] & 0xFF) << 16) | ((code[p + 2] & 0xFF) << 8) | (code[p + 3] & 0xFF);
	}
}
