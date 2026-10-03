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
 * Vanilla HumanoidModel.setupAnim ends with hat.copyFrom(head), but the Ripples player pose is applied
 * after it and moves the head, so the skin's hat layer must be re-synced or it floats off the head
 * (1.16 KosmX moved head and hat together). The model classes are client-only, so the bytecode of
 * OldPlayerModelJank._onAnimate and RotpAnimDefinition.animate is read as a resource.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerHatLayerAnimGameTests {
	private PlayerHatLayerAnimGameTests() {}

	private static final String JANK = "rotp/core/compat/v1_21_4/OldPlayerModelJank";
	private static final String ANIM_DEF = "rotp/core/client/entityanim/RotpAnimDefinition";
	private static final String MODEL_PART = "Lnet/minecraft/client/model/geom/ModelPart;";
	private static final String COPY_FROM = "net/minecraft/client/model/geom/ModelPart.copyFrom(" + MODEL_PART + ")V";
	private static final String ON_ANIMATE = JANK + "._onAnimate(Lnet/minecraft/client/model/HumanoidModel;)V";
	private static final String FRAME_APPLY = "rotp/core/client/entityanim/pose/AnimFramePose$ModelPartFrame.apply(" + MODEL_PART + "FZ)V";

	private static final int GETFIELD = 0xB4;
	private static final int INVOKEVIRTUAL = 0xB6;
	private static final int INVOKESTATIC = 0xB8;

	@GameTest(template = "empty")
	public static void animatedPoseResyncsHatFromHead(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, JANK, "_onAnimate", "(Lnet/minecraft/client/model/HumanoidModel;)V");
		boolean hatFromHead = false;
		for (int k = 3; k < code.size(); k++) {
			// hat.copyFrom(head): getfield hat, aload, getfield head, invokevirtual copyFrom
			if (code.get(k).is(INVOKEVIRTUAL, COPY_FROM)
					&& code.get(k - 1).isField("head")
					&& code.get(k - 3).isField("hat")) {
				hatFromHead = true;
			}
		}
		helper.assertTrue(hatFromHead,
				"OldPlayerModelJank._onAnimate does not copy the head pose into the hat layer");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void outerLayerSyncRunsAfterFrameBones(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, ANIM_DEF, "animate",
				"(Lnet/minecraft/client/model/Model;Lrotp/core/client/entityanim/pose/AnimFramePose;)V");
		int lastApply = -1;
		int sync = -1;
		for (int i = 0; i < code.size(); i++) {
			if (code.get(i).is(INVOKEVIRTUAL, FRAME_APPLY)) lastApply = i;
			if (code.get(i).is(INVOKESTATIC, ON_ANIMATE)) sync = i;
		}
		helper.assertTrue(lastApply >= 0, "RotpAnimDefinition.animate no longer applies the frame bones");
		helper.assertTrue(sync > lastApply,
				"RotpAnimDefinition.animate does not run OldPlayerModelJank._onAnimate after the frame bones");
		helper.succeed();
	}

	private record Insn(int op, String ref) {
		boolean is(int opcode, String target) {
			return op == opcode && target.equals(ref);
		}

		boolean isField(String name) {
			return op == GETFIELD && ref != null && ref.endsWith("." + name + MODEL_PART);
		}
	}

	// decodes the Code attribute of one method; member refs as owner.name+descriptor
	private static List<Insn> methodCode(GameTestHelper helper, String className, String name, String desc) {
		String path = className + ".class";
		InputStream raw = PlayerHatLayerAnimGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = PlayerHatLayerAnimGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 3, 4 -> in.readInt();
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
						return decode(body, refs);
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

	private static List<Insn> decode(byte[] codeAttr, String[] refs) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(codeAttr));
		in.skipBytes(4);
		byte[] code = new byte[in.readInt()];
		in.readFully(code);
		List<Insn> out = new ArrayList<>();
		int pc = 0;
		while (pc < code.length) {
			int op = code[pc] & 0xFF;
			String ref = null;
			if (op >= 0xB2 && op <= 0xB8) {
				ref = refs[((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)];
			}
			out.add(new Insn(op, ref));
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
