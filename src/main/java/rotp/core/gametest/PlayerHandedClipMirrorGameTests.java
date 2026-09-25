package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonParser;

import rotp.core.client.entityanim.AnimationSet;
import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 KosmXHandsideMirrorModifier mirrored the handed Hamon, Pillar Man and vampire player clips for a
 * left-handed player. The port resolves those clips through a mirrored copy when the player's main arm is left.
 * The clip lookup (PreFrameEntityAnimCalc, AnimationSet.getPlayerAnim) is client-only, so its wiring is read
 * from the class files.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerHandedClipMirrorGameTests {
	private PlayerHandedClipMirrorGameTests() {}

	// action names whose clip 1.16 played through KosmXHandsideMirrorModifier
	private static final String[] MIRRORED_ACTIONS = {
			"hamon_beat", "sunlight_yellow_overdrive", "scarlet_overdrive",
			"pillarman_heavy_punch", "pillarman_blade_slash", "pillarman_blade_dash_attack",
			"pillarman_light_flash", "pillarman_light_flash_decoy",
			"vampirism_claw_lacerate", "zombie_claw_lacerate" };

	// player clips 1.16 never mirrored
	private static final String[] UNMIRRORED_ACTIONS = {
			"meditation", "punch_barrage", "syo_barrage_start", "syo_barrage_finisher", "rebuff_overdrive",
			"sendo_wave_kick_l", "wall_climb_left", "pillarman_evasion", "pillarman_blade_barrage",
			"pillarman_atmospheric_rift", "pillarman_stone_form" };

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void leftHandedPlayerMirrorsHandedClips(GameTestHelper helper) {
		Set<String> clips = new HashSet<>();
		for (String file : new String[] { "hamon", "pillar_man", "vampire" }) {
			clips.addAll(readClips(helper, "animations/" + file + ".animation.json"));
		}
		for (String name : MIRRORED_ACTIONS) {
			helper.assertTrue(AnimationSet.mirrorsForMainArm(name, HumanoidArm.LEFT, clips::contains),
					name + " must play mirrored for a left-handed player like 1.16");
			helper.assertTrue(!AnimationSet.mirrorsForMainArm(name, HumanoidArm.RIGHT, clips::contains),
					name + " must play as authored for a right-handed player");
		}
		for (String name : UNMIRRORED_ACTIONS) {
			helper.assertTrue(!AnimationSet.mirrorsForMainArm(name, HumanoidArm.LEFT, clips::contains),
					name + " was never mirrored in 1.16");
		}

		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		player.setMainArm(HumanoidArm.LEFT);
		helper.assertTrue(AnimationSet.handedClipArm(player) == HumanoidArm.LEFT,
				"A left-handed player's clips must follow the left arm");
		player.setMainArm(HumanoidArm.RIGHT);
		helper.assertTrue(AnimationSet.handedClipArm(player) == HumanoidArm.RIGHT,
				"A right-handed player's clips must follow the right arm");
		helper.assertTrue(AnimationSet.handedClipArm(null) == HumanoidArm.RIGHT,
				"No performer keeps the authored side");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void playerClipLookupFollowsMainArm(GameTestHelper helper) {
		String calc = "rotp/core/client/entityanim/PreFrameEntityAnimCalc";
		String set = "rotp/core/client/entityanim/AnimationSet";
		String arm = "Lnet/minecraft/world/entity/HumanoidArm;";
		String id = "Lrotp/core/powersystem/entityaction/ActionAnimIdentifier;";
		String def = "Lrotp/core/client/entityanim/RotpAnimDefinition;";
		String lookupDesc = "(Lnet/minecraft/resources/ResourceLocation;" + id + arm + ")" + def;
		String resolveDesc = "(" + id + arm + ")" + def;

		// the player branch of getLivingPose feeds handedClipArm(living) straight into the handed lookup
		List<Insn> pose = code(helper, calc, "getLivingPose", null);
		String handedArm = set + ".handedClipArm(Lnet/minecraft/world/entity/LivingEntity;)" + arm;
		boolean handed = false;
		for (int i = 0; i + 1 < pose.size(); i++) {
			handed |= handedArm.equals(pose.get(i).ref()) && (calc + ".getPlayerAnim" + lookupDesc).equals(pose.get(i + 1).ref());
		}
		helper.assertTrue(handed,
				"PreFrameEntityAnimCalc.getLivingPose must pick player clips with AnimationSet.handedClipArm(living)");

		// the static lookup hands its mainArm (local 2) on to the animation set
		List<Insn> lookup = code(helper, calc, "getPlayerAnim", lookupDesc);
		boolean passed = false;
		for (int i = 0; i + 1 < lookup.size(); i++) {
			passed |= lookup.get(i).local() == 2 && (set + ".getPlayerAnim" + resolveDesc).equals(lookup.get(i + 1).ref());
		}
		helper.assertTrue(passed, "PreFrameEntityAnimCalc.getPlayerAnim must pass mainArm to AnimationSet.getPlayerAnim");

		// the set decides by that mainArm and returns the cached mirrored copy
		List<Insn> resolve = code(helper, set, "getPlayerAnim", resolveDesc);
		helper.assertTrue(resolve.stream().anyMatch(insn -> insn.local() == 2)
				&& resolve.stream().anyMatch(insn -> (set + ".mirrorsForMainArm(Ljava/lang/String;" + arm
						+ "Ljava/util/function/Predicate;)Z").equals(insn.ref())),
				"AnimationSet.getPlayerAnim must ask mirrorsForMainArm with its mainArm");
		helper.assertTrue(resolve.stream().anyMatch(insn ->
				"java/util/Map.computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;".equals(insn.ref())),
				"AnimationSet.getPlayerAnim must return the mirrored copy for a left-handed player");
		helper.succeed();
	}

	private static Set<String> readClips(GameTestHelper helper, String assetPath) {
		try (InputStream in = PlayerHandedClipMirrorGameTests.class.getResourceAsStream("/assets/jojo_ripples/" + assetPath)) {
			helper.assertTrue(in != null, "Missing asset " + assetPath);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
					.getAsJsonObject().getAsJsonObject("animations").keySet();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + assetPath, e);
		}
	}

	// one decoded instruction: field/method ref as owner.name+desc, local slot of an aload (else -1)
	private record Insn(int op, String ref, int local) {}

	// instructions of the first method named name (desc null = any overload), read from its class file
	private static List<Insn> code(GameTestHelper helper, String className, String name, String desc) {
		String path = className + ".class";
		InputStream raw = PlayerHandedClipMirrorGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) raw = PlayerHandedClipMirrorGameTests.class.getClassLoader().getResourceAsStream(path);
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
			int fields = in.readUnsignedShort();
			for (int f = 0; f < fields; f++) {
				in.skipBytes(6);
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					in.readUnsignedShort();
					in.skipBytes(in.readInt());
				}
			}
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
					if (name.equals(mName) && (desc == null || desc.equals(mDesc)) && "Code".equals(attr)) {
						return decode(body, refs);
					}
				}
			}
			helper.fail("Method " + className + "." + name + (desc != null ? desc : "") + " not found");
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		return List.of();
	}

	// walks the code array instruction by instruction (switch padding and wide included)
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
			int local = -1;
			int len;
			if (op == 0xAA || op == 0xAB) {
				int p = (pc + 4) & ~3;
				len = op == 0xAA ? p - pc + 12 + 4 * (s32(code, p + 8) - s32(code, p + 4) + 1)
						: p - pc + 8 + 8 * s32(code, p + 4);
			} else if (op == 0xC4) {
				int wideOp = code[pc + 1] & 0xFF;
				len = wideOp == 0x84 ? 6 : 4;
				if (wideOp == 0x19) local = u16(code, pc + 2);
			} else {
				len = 1 + operandBytes(op);
				if (op >= 0xB2 && op <= 0xB9) {
					int idx = u16(code, pc + 1);
					if (idx > 0 && idx < refs.length) ref = refs[idx];
				} else if (op == 0x19) {
					local = code[pc + 1] & 0xFF;
				} else if (op >= 0x2A && op <= 0x2D) {
					local = op - 0x2A;
				}
			}
			out.add(new Insn(op, ref, local));
			pc += len;
		}
		return out;
	}

	private static int operandBytes(int op) {
		return switch (op) {
			case 0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3A, 0xA9, 0xBC -> 1;
			case 0x11, 0x13, 0x14, 0x84, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6, 0xB7, 0xB8, 0xBB, 0xBD, 0xC0, 0xC1, 0xC6, 0xC7 -> 2;
			case 0xC5 -> 3;
			case 0xB9, 0xBA, 0xC8, 0xC9 -> 4;
			default -> op >= 0x99 && op <= 0xA8 ? 2 : 0;
		};
	}

	private static int u16(byte[] code, int at) {
		return ((code[at] & 0xFF) << 8) | (code[at + 1] & 0xFF);
	}

	private static int s32(byte[] code, int at) {
		return (code[at] << 24) | ((code[at + 1] & 0xFF) << 16) | ((code[at + 2] & 0xFF) << 8) | (code[at + 3] & 0xFF);
	}
}
