package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * HUD indicators 1.16 had and the port lost, read by PowerHud through StandUtil: the stamina bar's red flash below
 * half stamina, the Resolve level fill and the leap icon beside the hotbar. PowerHud is client-only, so its wiring
 * is pinned from its bytecode, read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HudIndicatorsGameTests {
	private static final float EPS = 1.0E-4F;
	private static final String HUD = "rotp/core/client/ui/hud_power/PowerHud";
	private static final String STAND_UTIL = "rotp/core/powersystem/standpower/StandUtil.";
	private static final String STAND_ENTITY = "rotp/core/powersystem/standpower/entity/StandEntity.";
	private static final String FILL = "net/minecraft/client/gui/GuiGraphics.fill(";

	private HudIndicatorsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void staminaBarFlashesBelowHalfWhileTheDebuffApplies(GameTestHelper helper) {
		User u = new User(helper, true);
		try {
			StandPower power = u.power;
			float max = power.getMaxStamina();
			helper.assertTrue(power.usesStamina() && max > 0, "Star Platinum has no stamina");
			power.setStamina(max);
			helper.assertFalse(StandUtil.showsStaminaDebuff(power), "Full stamina must not flash");
			power.setStamina(max * 0.5F);
			helper.assertFalse(StandUtil.showsStaminaDebuff(power), "1.16: exactly half stamina does not flash");
			power.setStamina(max * 0.4F);
			helper.assertTrue(StandUtil.showsStaminaDebuff(power), "1.16: below half stamina the stamina bar flashes red");
			helper.assertTrue(StandUtil.staminaCondition(power) < 1, "The flash must come with a weakened Stand");

			u.player.getAbilities().instabuild = true;
			helper.assertFalse(StandUtil.showsStaminaDebuff(power), "A creative user has no stamina debuff, so no flash");
			u.player.getAbilities().instabuild = false;
			helper.assertTrue(StandUtil.showsStaminaDebuff(power), "Back in survival the flash must return");
			u.player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 200, 0));
			helper.assertFalse(StandUtil.showsStaminaDebuff(power), "True Resolve lifts the stamina debuff, so no flash");
			helper.assertFalse(StandUtil.showsStaminaDebuff(null), "No Stand power, no flash");
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void resolveLevelFillIsLevelOverMaxLevel(GameTestHelper helper) {
		User u = new User(helper, false);
		try {
			StandPower power = u.power;
			int max = power.getMaxResolveLevel();
			helper.assertTrue(power.usesResolve() && max > 1, "Star Platinum has no Resolve levels");
			if (power.getResolveLevel() == 0) {
				assertClose(helper, StandUtil.resolveLevelFill(power), 0, "Resolve level 0 must show an empty level fill");
			}
			power.setResolveLevel(1);
			helper.assertTrue(power.getResolveLevel() == 1, "Could not set Resolve level 1");
			assertClose(helper, StandUtil.resolveLevelFill(power), 1.0F / max, "1.16: Resolve level 1 fills 1/" + max);
			power.setResolveLevel(max);
			assertClose(helper, StandUtil.resolveLevelFill(power), 1, "The max Resolve level must fill the whole strip");
			assertClose(helper, StandUtil.resolveLevelFill(null), 0, "No Stand power, no level fill");
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void leapIconFillsAsTheCooldownRunsOut(GameTestHelper helper) {
		// 1.16 renderLeapIcon: fill = 1 - cooldown / period, full without a period
		assertClose(helper, StandUtil.leapIconFill(0, 40), 1, "No cooldown must show a full icon");
		assertClose(helper, StandUtil.leapIconFill(10, 40), 0.75F, "A quarter of the cooldown left must fill 3/4");
		assertClose(helper, StandUtil.leapIconFill(40, 40), 0, "A fresh cooldown must show an empty icon");
		assertClose(helper, StandUtil.leapIconFill(60, 40), 0, "A cooldown past the period must not fill negative");
		assertClose(helper, StandUtil.leapIconFill(5, 0), 1, "No period must show a full icon");
		// 1.16 place: centre + 97 (right arm) or centre - 113, 20 further out past a hotbar attack indicator
		helper.assertTrue(StandUtil.leapIconX(400, true, false) == 297, "Right arm: the icon sits at centre + 97");
		helper.assertTrue(StandUtil.leapIconX(400, true, true) == 317, "Right arm, hotbar indicator: centre + 117");
		helper.assertTrue(StandUtil.leapIconX(400, false, false) == 87, "Left arm: the icon sits at centre - 113");
		helper.assertTrue(StandUtil.leapIconX(400, false, true) == 67, "Left arm, hotbar indicator: centre - 133");

		User u = new User(helper, true);
		try {
			StandPower power = u.power;
			helper.assertTrue(power.isLeapUnlocked(), "Summoned Star Platinum did not unlock the leap icon");
			int period = power.getLeapCooldownPeriod();
			helper.assertTrue(period > 0, "Summoned Star Platinum has no leap cooldown period");
			assertClose(helper, StandUtil.leapIconFill(power.getLeapCooldown(), period), 1, "Before a leap the icon must be full");
			power.onLeap();
			helper.assertTrue(power.getLeapCooldown() == period, "A leap must start the whole cooldown");
			assertClose(helper, StandUtil.leapIconFill(power.getLeapCooldown(), period), 0, "Right after a leap the icon must be empty");
		}
		finally {
			u.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void powerHudTicksAndDrawsTheStaminaFlash(GameTestHelper helper) {
		helper.assertTrue(calls(codeRefs(helper, "rotp/core/client/ClientTickHandler", "onClientTick"), HUD + ".tickHamonNoEnergyFeedback("),
				"The client tick no longer drives the HUD flash tick");
		helper.assertTrue(calls(codeRefs(helper, HUD, "tickHamonNoEnergyFeedback"), HUD + "$Stamina.tickDebuffHighlight("),
				"The HUD tick no longer counts the stamina flash");
		helper.assertTrue(calls(codeRefs(helper, HUD + "$Stamina", "tickDebuffHighlight"), STAND_UTIL + "showsStaminaDebuff("),
				"The stamina flash no longer follows StandUtil.showsStaminaDebuff");
		Set<String> render = codeRefs(helper, HUD + "$Stamina", "renderElement");
		helper.assertTrue(render.contains(HUD + "$Stamina.debuffHighlightTicks:I"), "The stamina bar no longer reads the flash ticks");
		helper.assertTrue(calls(render, "rotp/core/client/util/functions/ClientUtil.getHighlightAlpha(") && calls(render, FILL)
				&& render.contains("#" + 0xFF0000), "The stamina bar no longer draws the pulsing red fill");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void powerHudDrawsTheResolveLevelStripAndTooltipLine(GameTestHelper helper) {
		Set<String> render = codeRefs(helper, HUD + "$Resolve", "renderElement");
		helper.assertTrue(calls(render, STAND_UTIL + "resolveLevelFill(") && calls(render, FILL),
				"The Resolve icon no longer draws the level strip from StandUtil.resolveLevelFill");
		Set<String> tooltip = codeRefs(helper, HUD + "$Resolve", "checkTooltip");
		helper.assertTrue(tooltip.contains("\"ripples_hud.resolve_level\"") && calls(tooltip, HUD + "$Resolve.setLevelLine("),
				"The Resolve tooltip no longer shows the level line");
		helper.assertTrue(calls(codeRefs(helper, HUD + "$Resolve", "setLevelLine"), "java/util/List.set("),
				"The Resolve level line no longer replaces the first tooltip body line");
		for (String code : new String[] {"en_us", "zh_cn"}) {
			helper.assertTrue(lang(helper, code).has("ripples_hud.resolve_level"), code + " lacks ripples_hud.resolve_level");
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void powerHudHasTheLeapIconBesideTheHotbar(GameTestHelper helper) {
		Set<String> hud = codeRefs(helper, HUD + "$AbilityHud", "<init>");
		helper.assertTrue(hud.contains("\"leap\"") && calls(hud, HUD + "$Leap.<init>("), "The ability HUD has no leap element");
		helper.assertTrue(calls(codeRefs(helper, HUD + "$Leap", "renderElement"), STAND_UTIL + "leapIconFill("),
				"The leap icon no longer fills by StandUtil.leapIconFill");
		helper.assertTrue(calls(codeRefs(helper, HUD + "$Leap", "updateRectangle"), STAND_UTIL + "leapIconX("),
				"The leap icon no longer sits at StandUtil.leapIconX");
		helper.assertTrue(calls(codeRefs(helper, HUD + "$Leap", "getLeapState"),
				"rotp/core/powersystem/standpower/StandPower.isLeapUnlocked("), "The leap icon no longer waits for the leap unlock");
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void powerHudFinisherBarFollowsTheUnlockAndHeavyFinisherTint(GameTestHelper helper) {
		Set<String> gate = codeRefs(helper, HUD + "$Finisher", "shouldRender");
		helper.assertTrue(calls(gate, STAND_ENTITY + "isFinisherMechanicUnlocked("),
				"1.16: the finisher bar shows only once the finisher mechanic is unlocked");
		helper.assertTrue(gate.contains(STAND_ENTITY + "getFinisherMeter()F"), "1.16: the finisher bar hides with an empty meter");
		Set<String> render = codeRefs(helper, HUD + "$Finisher", "renderElement");
		// green drawn inline or through FinisherRing.tint
		String green = "#" + 0x8000FF21;
		String ring = "rotp/core/client/ui/hud_power/FinisherRing";
		helper.assertTrue(calls(render, STAND_ENTITY + "willHeavyFinisherVariationFire(") && (render.contains(green)
				|| calls(render, ring + ".tint(") && codeRefs(helper, ring, "tint").contains(green)),
				"1.16: the finisher bar turns green when the heavy finisher variation will fire");
		helper.succeed();
	}

	private static boolean calls(Set<String> refs, String prefix) {
		for (String ref : refs) {
			if (ref.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static JsonObject lang(GameTestHelper helper, String code) {
		String path = "/assets/" + JojoMod.MOD_ID + "/lang/" + code + ".json";
		try (InputStream in = HudIndicatorsGameTests.class.getResourceAsStream(path)) {
			helper.assertTrue(in != null, "Missing " + path);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	// refs in the code of every method with this name: owner.name+desc, fields owner.name:desc, ldc "text" and #int
	private static Set<String> codeRefs(GameTestHelper helper, String className, String method) {
		String path = className + ".class";
		InputStream raw = HudIndicatorsGameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HudIndicatorsGameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		Set<String> found = new HashSet<>();
		boolean seen = false;
		try (DataInputStream in = new DataInputStream(raw)) {
			helper.assertTrue(in.readInt() == 0xCAFEBABE, path + " is not a class file");
			in.readUnsignedShort();
			in.readUnsignedShort();
			int count = in.readUnsignedShort();
			String[] utf = new String[count];
			String[] entry = new String[count];
			int[] a = new int[count];
			int[] b = new int[count];
			int[] tag = new int[count];
			for (int i = 1; i < count; i++) {
				tag[i] = in.readUnsignedByte();
				switch (tag[i]) {
				case 1 -> utf[i] = in.readUTF();
				case 3 -> entry[i] = "#" + in.readInt();
				case 4 -> in.readInt();
				case 7, 8, 16, 19, 20 -> a[i] = in.readUnsignedShort();
				case 5, 6 -> { in.readLong(); i++; }
				case 9, 10, 11, 12, 17, 18 -> { a[i] = in.readUnsignedShort(); b[i] = in.readUnsignedShort(); }
				case 15 -> { in.readUnsignedByte(); a[i] = in.readUnsignedShort(); }
				default -> throw new IOException("Unknown constant tag " + tag[i]);
				}
			}
			for (int i = 1; i < count; i++) {
				if (tag[i] == 8) {
					entry[i] = "\"" + utf[a[i]] + "\"";
				}
				else if (tag[i] >= 9 && tag[i] <= 11) {
					entry[i] = utf[a[a[i]]] + "." + utf[a[b[i]]] + (tag[i] == 9 ? ":" : "") + utf[b[b[i]]];
				}
			}
			in.readFully(new byte[6]);
			in.readFully(new byte[2 * in.readUnsignedShort()]);
			// fields, then methods
			for (int part = 0; part < 2; part++) {
				int members = in.readUnsignedShort();
				for (int m = 0; m < members; m++) {
					in.readUnsignedShort();
					String name = utf[in.readUnsignedShort()];
					in.readUnsignedShort();
					int attrs = in.readUnsignedShort();
					for (int t = 0; t < attrs; t++) {
						String attr = utf[in.readUnsignedShort()];
						byte[] body = new byte[in.readInt()];
						in.readFully(body);
						if (part == 1 && method.equals(name) && "Code".equals(attr)) {
							seen = true;
							addCodeRefs(body, entry, found);
						}
					}
				}
			}
		} catch (IOException e) {
			helper.fail("Cannot read " + path + ": " + e);
		}
		helper.assertTrue(seen, "Missing method " + className + "." + method);
		return found;
	}

	// walks the instructions of a Code attribute (max_stack, max_locals, code_length, code)
	private static void addCodeRefs(byte[] body, String[] entry, Set<String> found) {
		int start = 8;
		int end = start + u4(body, 4);
		for (int pc = start; pc < end; pc += insnLength(body, pc, start)) {
			int op = body[pc] & 0xFF;
			int idx = op == 0x12 ? body[pc + 1] & 0xFF : op == 0x13 || op >= 0xB2 && op <= 0xB9 ? u2(body, pc + 1) : 0;
			if (idx > 0 && idx < entry.length && entry[idx] != null) {
				found.add(entry[idx]);
			}
		}
	}

	private static int insnLength(byte[] code, int pc, int start) {
		int op = code[pc] & 0xFF;
		if (op == 0xAA || op == 0xAB) {
			// switch operands are 4-byte aligned from the code start
			int p = start + ((pc - start + 4) & ~3);
			return (op == 0xAA ? p + 12 + (u4(code, p + 8) - u4(code, p + 4) + 1) * 4 : p + 8 + u4(code, p + 4) * 8) - pc;
		}
		if (op == 0xC4) {
			return (code[pc + 1] & 0xFF) == 0x84 ? 6 : 4;
		}
		if (op == 0x10 || op == 0x12 || op >= 0x15 && op <= 0x19 || op >= 0x36 && op <= 0x3A || op == 0xA9 || op == 0xBC) {
			return 2;
		}
		if (op == 0x11 || op == 0x13 || op == 0x14 || op == 0x84 || op >= 0x99 && op <= 0xA8 || op >= 0xB2 && op <= 0xB8
				|| op == 0xBB || op == 0xBD || op == 0xC0 || op == 0xC1 || op == 0xC6 || op == 0xC7) {
			return 3;
		}
		if (op == 0xC5) {
			return 4;
		}
		return op == 0xB9 || op == 0xBA || op == 0xC8 || op == 0xC9 ? 5 : 1;
	}

	private static int u2(byte[] b, int i) {
		return (b[i] & 0xFF) << 8 | b[i + 1] & 0xFF;
	}

	private static int u4(byte[] b, int i) {
		return u2(b, i) << 16 | u2(b, i + 2);
	}

	private static void assertClose(GameTestHelper helper, float actual, float expected, String message) {
		helper.assertTrue(Math.abs(actual - expected) < EPS, message + " (got " + actual + ", expected " + expected + ")");
	}

	// a survival mock player with Star Platinum; summoned at the max Resolve level so the leap unlocks
	private static final class User {
		final Player player;
		final StandType type;
		final StandPower power;

		User(GameTestHelper helper, boolean summonAtMaxResolve) {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not give the user Star Platinum");
			if (summonAtMaxResolve) {
				power.setResolveLevel(power.getMaxResolveLevel());
				helper.assertTrue(type.summon(player, power), "Could not summon Star Platinum");
			}
		}

		void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(player, power);
			}
			player.discard();
		}
	}
}
