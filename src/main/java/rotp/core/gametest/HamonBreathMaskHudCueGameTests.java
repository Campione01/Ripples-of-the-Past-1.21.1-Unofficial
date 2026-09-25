package rotp.core.gametest;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonData.tickBreathStability (:313-347): with the Breath Control Mask on, the local player's Hamon bar
 * pulsed red (4 cycles at zero handicap, 999999 while stability drained) and the overlay asked to breathe through
 * the mask when the drain ran 400+ ticks past the limit or neared the 20% floor, stability stayed above 0 and
 * Hamon Breath passed its conditions. Breathing (:257-262) and full stability (:375-378) cleared the pulse.
 * The HUD calls are client-only, so their call sites in HamonData are read as bytecode.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonBreathMaskHudCueGameTests {

	private HamonBreathMaskHudCueGameTests() {}

	private static final String DATA = "rotp/core/impl/powers/hamon/HamonData";
	private static final String HUD = "rotp/core/client/ui/hud_power/PowerHud";
	private static final String PROXY = "rotp/core/client/ClientProxy";
	private static final String POWER = "Lrotp/core/powersystem/Power;";
	private static final String LIVING = "Lnet/minecraft/world/entity/LivingEntity;";
	private static final String BREATH_STABILITY_DESC = "(" + LIVING + POWER + ")V";
	private static final String CLIENT_PLAYER = PROXY + ".getClientPlayer()Lnet/minecraft/world/entity/player/Player;";
	private static final String CYCLES = DATA + ".breathMaskHighlightCycles(F)I";
	private static final String TRIGGER = HUD + ".triggerHamonRedHighlight(I)V";
	private static final String RESET = HUD + ".resetHamonRedHighlight()V";
	private static final String PROMPT = DATA + ".shouldPromptMaskHamonBreath(FIFZFLjava/util/function/BooleanSupplier;)Z";
	private static final String OVERLAY = PROXY + ".setOverlayMessage(Lnet/minecraft/network/chat/Component;Z)V";
	private static final String USABLE = DATA + ".isHamonBreathUsable(" + POWER + ")Z";
	private static final String SET_STABILITY = DATA + ".setBreathStability(F)V";
	private static final String MAX_STABILITY = DATA + ".getMaxBreathStability()F";
	private static final String RESTORE_STAB = "hamon.breath_control_mask.restore_stab";

	private static final int LDC = 0x12;
	private static final int LDC_W = 0x13;
	private static final int ILOAD = 0x15;
	private static final int ISTORE = 0x36;

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void maskRedPulseCycles(GameTestHelper helper) {
		check(helper, HamonData.breathMaskHighlightCycles(0.0F) == 4, "zero handicap must pulse 4 cycles");
		check(helper, HamonData.breathMaskHighlightCycles(-0.01F) == 999999, "a draining mask must pulse until reset");
		check(helper, HamonData.breathMaskHighlightCycles(-1.0F) == 999999, "full drain must pulse until reset");
		check(helper, HamonData.breathMaskHighlightCycles(0.5F) == 0, "slowed recovery must not pulse");
		check(helper, HamonData.breathMaskHighlightCycles(1.0F) == 0, "full recovery must not pulse");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void maskBreathPromptCondition(GameTestHelper helper) {
		float limit = 400.0F;
		check(helper, prompt(-0.5F, 801, limit, false, 10.0F, true), "401 ticks past the limit must prompt");
		check(helper, !prompt(-0.5F, 800, limit, false, 10.0F, true), "exactly 400 ticks past the limit must not prompt");
		check(helper, prompt(-0.5F, 500, limit, true, 10.0F, true), "stability near the 20% floor must prompt");
		check(helper, !prompt(-0.5F, 500, limit, false, 10.0F, true), "early drain above the floor must not prompt");
		check(helper, !prompt(-0.5F, 900, limit, true, 0.0F, true), "empty stability must not prompt");
		check(helper, !prompt(-0.5F, 900, limit, true, 10.0F, false), "unusable Hamon Breath must not prompt");
		check(helper, !prompt(0.0F, 900, limit, true, 10.0F, true), "no drain (handicap 0) must not prompt");
		check(helper, !prompt(0.5F, 900, limit, true, 10.0F, true), "recovery must not prompt");
		// 1.16 checked the ability last
		AtomicInteger asked = new AtomicInteger();
		HamonData.shouldPromptMaskHamonBreath(-0.5F, 900, limit, true, 0.0F, () -> asked.incrementAndGet() > 0);
		check(helper, asked.get() == 0, "Hamon Breath conditions were checked although stability was empty");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void maskPromptNeedsUsableBreath(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the mask prompt test player");
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			user.setAirSupply(user.getMaxAirSupply());
			check(helper, HamonData.isHamonBreathUsable(power), "Hamon Breath must be usable with full air");
			user.setAirSupply(0);
			check(helper, !HamonData.isHamonBreathUsable(power), "Hamon Breath must not be usable without air");
			check(helper, !HamonData.isHamonBreathUsable(null), "no power means no Hamon Breath");
			helper.succeed();
		}
		finally {
			user.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void maskRedPulseWiredIntoBreathStability(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, DATA, "tickBreathStability", BREATH_STABILITY_DESC);
		int cycles = find(code, 0, CYCLES);
		int trigger = find(code, cycles + 1, TRIGGER);
		check(helper, cycles >= 0 && trigger >= 0, "tickBreathStability no longer pulses the Hamon bar red under the mask");
		// pulse length is the helper's result: istore n right after it, iload n right before the trigger
		Insn store = code.get(cycles + 1);
		Insn load = code.get(trigger - 1);
		check(helper, store.op() == ISTORE && load.op() == ILOAD && store.constant().equals(load.constant()),
				"the mask red pulse no longer takes its cycles from breathMaskHighlightCycles");
		int gate = find(code, 0, CLIENT_PLAYER);
		check(helper, gate >= 0 && gate < cycles, "the mask red pulse is no longer limited to the local player");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void maskBreathPromptWiredIntoBreathStability(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, DATA, "tickBreathStability", BREATH_STABILITY_DESC);
		int prompt = find(code, 0, PROMPT);
		int message = findLdc(code, prompt + 1, RESTORE_STAB);
		int overlay = find(code, message + 1, OVERLAY);
		check(helper, prompt >= 0 && message >= 0 && overlay >= 0, "tickBreathStability no longer asks to breathe through the mask");
		// the local-player flag (stored right after getClientPlayer) is read again between the pulse and the prompt
		int cue = findOp(code, find(code, 0, CLIENT_PLAYER) + 1, ISTORE);
		int pulse = find(code, 0, TRIGGER);
		check(helper, cue >= 0 && pulse >= 0 && loads(code, pulse + 1, prompt, code.get(cue).constant()),
				"the mask prompt is no longer limited to the local player");
		// the prompt's last condition is Hamon Breath being usable for the user's power
		List<Insn> usable = methodCode(helper, DATA, "lambda$tickBreathStability$", "(" + POWER + ")Z");
		check(helper, find(usable, 0, USABLE) >= 0, "the mask prompt no longer checks that Hamon Breath is usable");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void maskRedPulseClearedAtFullStability(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, DATA, "tickBreathStability", BREATH_STABILITY_DESC);
		int set = find(code, 0, SET_STABILITY);
		int full = find(code, set + 1, MAX_STABILITY);
		int reset = find(code, full + 1, RESET);
		check(helper, set >= 0 && reset >= 0, "breath stability no longer clears the mask red pulse");
		check(helper, full >= 0, "the mask red pulse is cleared before breath stability is full");
		int gate = find(code, set + 1, CLIENT_PLAYER);
		check(helper, gate >= 0 && gate < reset, "the full-stability reset is no longer limited to the local player");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void breathStartClearsMaskRedPulse(GameTestHelper helper) {
		List<Insn> code = methodCode(helper, DATA, "tickHamonBreath", "(" + LIVING + ")F");
		int reset = find(code, 0, RESET);
		check(helper, reset >= 0, "starting Hamon Breath no longer clears the Hamon bar red pulse");
		int gate = find(code, 0, CLIENT_PLAYER);
		check(helper, gate >= 0 && gate < reset, "the breath-start reset is no longer limited to the local player");
		helper.succeed();
	}

	private static boolean prompt(float handicap, int ticks, float limit, boolean low, float after, boolean usable) {
		return HamonData.shouldPromptMaskHamonBreath(handicap, ticks, limit, low, after, () -> usable);
	}

	private static void check(GameTestHelper helper, boolean ok, String message) {
		helper.assertTrue(ok, message);
	}

	// first member ref equal to target at or after from, -1 if none
	private static int find(List<Insn> code, int from, String target) {
		for (int i = Math.max(from, 0); i < code.size(); i++) {
			if (target.equals(code.get(i).ref())) {
				return i;
			}
		}
		return -1;
	}

	private static int findOp(List<Insn> code, int from, int op) {
		for (int i = Math.max(from, 0); i < code.size(); i++) {
			if (code.get(i).op() == op) {
				return i;
			}
		}
		return -1;
	}

	// an iload of the local in [from, to)
	private static boolean loads(List<Insn> code, int from, int to, Object local) {
		for (int i = Math.max(from, 0); i < Math.min(to, code.size()); i++) {
			if (code.get(i).op() == ILOAD && local.equals(code.get(i).constant())) {
				return true;
			}
		}
		return false;
	}

	private static int findLdc(List<Insn> code, int from, Object value) {
		for (int i = Math.max(from, 0); i < code.size(); i++) {
			if (code.get(i).isLdc() && value.equals(code.get(i).constant())) {
				return i;
			}
		}
		return -1;
	}

	// iload/istore carry their local index as the constant
	private record Insn(int op, String ref, Object constant) {
		boolean isLdc() {
			return op == LDC || op == LDC_W;
		}
	}

	// decodes the Code attribute of one method (a name ending in '$' matches as a prefix, for lambdas)
	private static List<Insn> methodCode(GameTestHelper helper, String className, String name, String desc) {
		String path = className + ".class";
		InputStream raw = HamonBreathMaskHudCueGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HamonBreathMaskHudCueGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		boolean prefix = name.endsWith("$");
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
				boolean match = (prefix ? mName.startsWith(name) : name.equals(mName)) && desc.equals(mDesc);
				int attrs = in.readUnsignedShort();
				for (int t = 0; t < attrs; t++) {
					String attr = utf[in.readUnsignedShort()];
					byte[] body = new byte[in.readInt()];
					in.readFully(body);
					if (match && "Code".equals(attr)) {
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
			int kind = op;
			String ref = null;
			Object constant = null;
			if ((op >= 0xB2 && op <= 0xB9) || op == 0xBB || op == 0xC0 || op == 0xC1) {
				ref = refs[((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)];
			}
			else if (op == LDC) {
				constant = values[code[pc + 1] & 0xFF];
			}
			else if (op == LDC_W) {
				constant = values[((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF)];
			}
			else if (op == ILOAD || op == ISTORE) {
				constant = code[pc + 1] & 0xFF;
			}
			else if (op >= 0x1A && op <= 0x1D) {
				kind = ILOAD;
				constant = op - 0x1A;
			}
			else if (op >= 0x3B && op <= 0x3E) {
				kind = ISTORE;
				constant = op - 0x3B;
			}
			out.add(new Insn(kind, ref, constant));
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
