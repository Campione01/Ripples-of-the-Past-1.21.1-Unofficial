package rotp.core.gametest;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import rotp.core.api.RotpAddonApi;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.vampirism.VampirismData;
import rotp.core.impl.powers.vampirism.VampirismState;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ActionsOverlayGui.renderActionIcon (:1156-1162, :1224-1227): a slot showing the power's held action was filled
 * green behind its icon (fillSingleRect 0, 255, 0, 127) once that action had been held for its getHoldDurationToFire
 * (heldReadyToFire), and the frame of a selected slot was green (HotbarRenderer.renderSlotSelection greenSelection).
 * Only a hold that went on past that length could show it: holdToFire(n, true), or holdToFire(n, false) with
 * holdType() (Sendo Overdrive, Sunlight Yellow Overdrive, Scarlet Overdrive, the S.Y.O. Barrage, Divine Sandstorm,
 * Atmospheric Rift, Blade Dash, Giant Carthwheel Prison, Self Detonation, Space Ripper Stingy Eyes); a charge that fired
 * on its n-th held tick never did. The HUD asks Ability#isHeldReadyToFire, which these tests ask of the real actions,
 * held by a key and ticked on the server (the client reads the same synced phases). PowerHudControlsElement is
 * client-only, so its wiring is pinned from its bytecode, read as a resource instead of being loaded.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldReadyToFireSlot116GameTests {
	private static final short KEY = 11;
	private static final String HUD = "rotp/core/client/ui/hud_power/PowerHudControlsElement";

	private HeldReadyToFireSlot116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void hamonTechniquesHeldPastTheirChargeAreReadyToFireAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldReadyHamon")) {
			HamonData hamon = s.hamon(ModHamonSkills.SENDO_OVERDRIVE.get(), ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE.get(),
					ModHamonSkills.SCARLET_OVERDRIVE.get(), ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get());
			// the block Sendo Overdrive sends its wave into, three blocks ahead
			for (int y = 2; y <= 6; y++) {
				helper.setBlock(new BlockPos(2, y, 5), Blocks.STONE.defaultBlockState());
			}
			// 1.16 holdToFire(30, true)
			s.refill(hamon);
			assertReadyFromItsChargeUntilReleased(helper, s, "sendo_overdrive", 30, "sunlight_yellow_overdrive");
			// holdToFire(10, true).holdType(40), getHoldDurationMax Integer.MAX_VALUE
			s.refill(hamon);
			assertReadyFromItsChargeUntilReleased(helper, s, "sunlight_yellow_overdrive", 10, "sendo_overdrive");
			// holdToFire(8, true).holdType(32), a Sunlight Yellow Overdrive
			s.refill(hamon);
			assertReadyFromItsChargeUntilReleased(helper, s, "scarlet_overdrive", 8, "sunlight_yellow_overdrive");
			// holdToFire(60, false) and holdType() in its constructor
			s.refill(hamon);
			assertReadyFromItsChargeUntilReleased(helper, s, "sunlight_yellow_overdrive_barrage", 60, "sendo_overdrive");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void pillarManMovesHeldPastTheirChargeAreReadyToFireAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldReadyPillarMan")) {
			PillarmanData data = s.pillarman();
			// holdToFire(40, true) and holdType(): from the 40th held tick it fires its waves until let go
			s.pillarmanMode(data, PillarmanMode.WIND);
			assertReadyFromItsChargeUntilReleased(helper, s, "pillarman_divine_sandstorm", 40, "pillarman_atmospheric_rift");
			s.pillarmanMode(data, PillarmanMode.WIND);
			assertReadyFromItsChargeUntilReleased(helper, s, "pillarman_atmospheric_rift", 40, "pillarman_divine_sandstorm");
			// holdToFire(10, true).holdType(40), getHoldDurationMax Integer.MAX_VALUE; it needs its user on the ground
			s.pillarmanMode(data, PillarmanMode.LIGHT);
			s.user.setOnGround(true);
			assertReadyFromItsChargeUntilReleased(helper, s, "pillarman_blade_dash_attack", 10, "pillarman_divine_sandstorm");
			// holdToFire(30, false) and holdToFire(60, false) with holdType() in their constructors; they cost 125 and 150
			s.pillarmanMode(data, PillarmanMode.HEAT);
			data.setEnergy(s.user, 300.0F);
			assertReadyFromItsChargeUntilReleased(helper, s, "pillarman_giant_carthwheel_prison", 30, "pillarman_self_detonation");
			s.pillarmanMode(data, PillarmanMode.HEAT);
			data.setEnergy(s.user, 300.0F);
			assertReadyFromItsChargeUntilReleased(helper, s, "pillarman_self_detonation", 60, "pillarman_giant_carthwheel_prison");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void vampireSpaceRipperHeldPastItsChargeIsReadyToFireAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldReadyVampire")) {
			s.vampire(500.0F);
			// holdToFire(20, true)
			assertReadyFromItsChargeUntilReleased(helper, s, "vampirism_space_ripper_stingy_eyes", 20, null);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void chargesThatFireAsTheyEndAreNeverReadyToFireAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldNeverReadyPillarMan")) {
			PillarmanData data = s.pillarman();
			// 1.16 holdToFire(40, false): the hold reached its length and fired on its 40th tick
			s.pillarmanMode(data, PillarmanMode.NONE);
			EntityActionInstance stoneForm = assertNeverReadyWhileHeld(helper, s, "pillarman_stone_form", 45);
			helper.assertTrue(data.isStoneFormEnabled() || stoneForm.getPhase() != ActionPhase.BUTTON_CHARGE,
					"setup: the held Stone Form did not fire at the end of its 40-tick charge: " + describe(stoneForm));
		}
		try (Scene s = new Scene(helper, "HeldNeverReadyStand")) {
			s.stand("magicians_red");
			// 1.16 holdToFire(20, false): Crossfire Hurricane fired on its 20th held tick
			s.unlock("crossfire_hurricane");
			EntityActionInstance crossfire = assertNeverReadyWhileHeld(helper, s, "crossfire_hurricane", 25);
			helper.assertTrue(crossfire.isOver() || crossfire.getPhase() != ActionPhase.BUTTON_CHARGE,
					"setup: the held Crossfire Hurricane did not fire at the end of its 20-tick charge: " + describe(crossfire));
			// the port's charged heavy punch fires a charge let go early too, so no hold makes it ready to fire
			s.unlock("heavy_charged");
			EntityActionInstance charged = assertNeverReadyWhileHeld(helper, s, "heavy_charged", 40);
			helper.assertTrue(!charged.isOver() && charged.getPhase() == ActionPhase.WINDUP,
					"setup: the held charged heavy punch is not waiting for its release past its charge: " + describe(charged));
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void hudFillsAndFramesTheHeldReadySlotGreenAsIn116(GameTestHelper helper) {
		List<String> fill = codeRefs(helper, HUD, "renderHeldReadyToFire");
		helper.assertTrue(fill.contains("rotp/core/powersystem/ability/Ability.isHeldReadyToFire(Lnet/minecraft/world/entity/LivingEntity;)Z"),
				"The HUD no longer asks Ability.isHeldReadyToFire for its slots (1.16 heldReadyToFire)");
		helper.assertTrue(calls(fill, "net/minecraft/client/gui/GuiGraphics.fill(") && fill.contains("#" + 0x7F00FF00),
				"1.16 fillSingleRect(0, 255, 0, 127): the held-ready slot is no longer filled translucent green");

		List<String> render = codeRefs(helper, HUD, "renderControls");
		int asked = 0;
		int drawn = 0;
		boolean askedFirst = true;
		for (String ref : render) {
			if (ref.startsWith(HUD + ".renderHeldReadyToFire(")) {
				asked++;
			}
			else if (ref.startsWith(HUD + ".renderAbility(")) {
				drawn++;
				askedFirst &= asked == drawn;
			}
		}
		helper.assertTrue(drawn == 2 && asked == 2 && askedFirst,
				"1.16 filled a held-ready slot behind its icon: each slot loop (key binds, hotbars) must draw the green"
						+ " before the icon (" + asked + " fills, " + drawn + " icons, fill first " + askedFirst + ")");
		helper.assertTrue(count(render, "#" + 0xFF00FF00) == 2 && count(render, "net/minecraft/util/FastColor$ARGB32.multiply(") == 2,
				"1.16 renderSlotSelection(greenSelection = heldReadyToFire || ...): the frame of a selected key bind and of"
						+ " the selected hotbar slot must turn green, at the frame's alpha");

		helper.assertTrue(RotpAddonApi.supportsFeature("ability_held_ready_to_fire_v1")
				&& "ability_held_ready_to_fire_v1".equals(RotpAddonApi.FEATURE_ABILITY_HELD_READY_TO_FIRE_V1),
				"The core does not advertise Ability#isHeldReadyToFire as feature ability_held_ready_to_fire_v1, so an add-on"
						+ " overriding it cannot require it");
		helper.succeed();
	}

	/*
	 * 1.16 renderActionIcon drew a slot from checkRequirements, which refused every other action of the power while its held
	 * action let none through: dimmed (brightness 0.2, alpha 0.5) and, greenSelection reading the same result, without a
	 * finisher's green frame. The HUD draws a slot whose press the Stand power's held action holds back
	 * (Ability#isHeldBackByHeldAction, HoldTypeActionPress116GameTests) the same, in the key-bound slots, the hotbars and the
	 * selection wheel: abilityColor and both selection frames go by drawnCondition, which asks it.
	 */
	@GameTest(template = "empty")
	public static void hudDrawsASlotTheHeldActionHoldsBackUnavailableAsIn116(GameTestHelper helper) {
		List<String> drawn = codeRefs(helper, HUD, "drawnCondition");
		helper.assertTrue(drawn.contains("rotp/core/powersystem/ability/Ability.isHeldBackByHeldAction(Lnet/minecraft/world/entity/LivingEntity;)Z"),
				"The HUD's drawn condition no longer asks Ability.isHeldBackByHeldAction (1.16 checkRequirements, heldAllowsOtherAction)");
		helper.assertTrue(calls(codeRefs(helper, HUD, "abilityColor"), HUD + ".drawnCondition("),
				"1.16 dimmed a slot the held action held back: abilityColor no longer goes by drawnCondition");
		List<String> render = codeRefs(helper, HUD, "renderControls");
		helper.assertTrue(count(render, HUD + ".drawnCondition(") == 2 && !calls(render,
				"rotp/core/powersystem/ability/condition/AvailableAbilities$AbilityConditionCheck.conditionCheck:"),
				"1.16 greenSelection(power, result) read the same result: both selection frames (key binds, hotbars) must go by"
						+ " drawnCondition, not the bare condition check");
		helper.succeed();
	}

	// held by its key from the press: not ready one tick short of its charge, ready from it while held, not once let go;
	// another ability of the power is not ready meanwhile
	private static void assertReadyFromItsChargeUntilReleased(GameTestHelper helper, Scene s, String name, int ticksToFire,
			@Nullable String other) {
		Ability ability = s.ability(name);
		EntityActionInstance action = s.start(name);
		s.tick(ticksToFire - 1);
		helper.assertTrue(!action.isOver(), "setup: the held " + name + " ended before its charge: " + describe(action));
		helper.assertFalse(ability.isHeldReadyToFire(s.user), "1.16 drew no green for " + name + " held " + (ticksToFire - 1)
				+ " ticks, short of its " + ticksToFire + ": it is ready to fire (" + describe(action) + ")");
		s.tick(1);
		helper.assertTrue(ability.isHeldReadyToFire(s.user), "1.16 filled the slot of " + name + " green once it had been held"
				+ " its " + ticksToFire + " ticks (heldReadyToFire): it is not ready to fire (" + describe(action) + ")");
		if (other != null) {
			helper.assertFalse(s.ability(other).isHeldReadyToFire(s.user), "1.16 filled only the slots showing the held action: "
					+ other + " is ready to fire while " + name + " is held (" + describe(action) + ")");
		}
		s.tick(5);
		helper.assertTrue(ability.isHeldReadyToFire(s.user), "1.16 kept the slot of " + name + " green while it was still held"
				+ " past its charge: it is not ready to fire " + (ticksToFire + 5) + " ticks in (" + describe(action) + ")");
		s.release();
		helper.assertFalse(ability.isHeldReadyToFire(s.user), "1.16 ended the green of " + name + " when letting go ended"
				+ " its hold: it is still ready to fire (" + describe(action) + ")");
		s.clear();
	}

	private static EntityActionInstance assertNeverReadyWhileHeld(GameTestHelper helper, Scene s, String name, int ticks) {
		Ability ability = s.ability(name);
		EntityActionInstance action = s.start(name);
		for (int tick = 1; tick <= ticks; tick++) {
			s.tick(1);
			helper.assertFalse(ability.isHeldReadyToFire(s.user), "1.16 drew no green for " + name + ", whose hold did not go"
					+ " on past its charge: it is ready to fire after " + tick + " held ticks (" + describe(action) + ")");
		}
		return action;
	}

	private static String describe(EntityActionInstance action) {
		return action.isOver() ? "the action ended" : "phase " + action.getPhase() + " tick " + action.getPhaseTick()
				+ " of " + action.getCurPhaseLength();
	}

	private static boolean calls(List<String> refs, String prefix) {
		return refs.stream().anyMatch(ref -> ref.startsWith(prefix));
	}

	private static int count(List<String> refs, String prefix) {
		return (int) refs.stream().filter(ref -> ref.startsWith(prefix)).count();
	}

	// refs in the code of every method with this name, in code order: owner.name+desc, fields owner.name:desc, ldc "text"
	// and #int; other class-file pins of the package read them too
	static List<String> codeRefs(GameTestHelper helper, String className, String method) {
		String path = className + ".class";
		InputStream raw = HeldReadyToFireSlot116GameTests.class.getResourceAsStream("/" + path);
		if (raw == null) {
			raw = HeldReadyToFireSlot116GameTests.class.getClassLoader().getResourceAsStream(path);
		}
		helper.assertTrue(raw != null, "Missing class file " + path);
		List<String> found = new ArrayList<>();
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
	private static void addCodeRefs(byte[] body, String[] entry, List<String> found) {
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

	/** A player looking south (+z) with no gravity, with a non-Stand power or a summoned Stand, holding one key. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		@Nullable private Power<?> power;
		@Nullable private PowerClass<?> powerClass;
		@Nullable private StandType standType;
		@Nullable private StandEntity stand;
		private LivingComponentAction performerActions;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add " + name);
			performerActions = LivingComponentAction.getComponent(user);
		}

		private PlayerPower playerPower(PlayerPowerType<?> type) {
			PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(user);
			playerPower.setPowerType(type);
			power = playerPower;
			powerClass = PowerClass.PLAYER_POWER;
			return playerPower;
		}

		private HamonData hamon(HamonSkill... skills) {
			playerPower(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			for (HamonSkill skill : skills) {
				hamon.learnSkill(skill);
				helper.assertTrue(hamon.isSkillLearned(skill), "setup: could not grant " + skill);
			}
			return hamon;
		}

		private void refill(HamonData hamon) {
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
		}

		private PillarmanData pillarman() {
			playerPower(ModPlayerPowers.PILLAR_MAN.get());
			PillarmanData data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2, user);
			return data;
		}

		private void pillarmanMode(PillarmanData data, PillarmanMode mode) {
			data.setMode(mode, user);
			data.setEnergy(user, 100.0F);
			helper.assertTrue(!data.isStoneFormEnabled(), "setup: Pillar Man stone form is on");
		}

		private void vampire(float blood) {
			playerPower(ModPlayerPowers.VAMPIRISM.get());
			VampirismData data = PlayerPower.getPowerData(user, ModPlayerPowers.VAMPIRISM).orElseThrow();
			data.setVampireFullPower(true, user);
			VampirismState.get(user).blood().setCurrent(blood);
			data.setBloodLevel(VampirismState.get(user).blood().current());
			helper.assertTrue(data.getCuringStage(user) == 0 && data.hasBlood(user, blood),
					"setup: the vampire is being cured or has not got its blood: stage " + data.getCuringStage(user));
		}

		private void stand(String standId) {
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
			StandPower standPower = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(standPower, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant " + standId + ": " + inserted.status());
			power = standPower;
			powerClass = PowerClass.STAND;
			standPower.setResolveLevel(4);
			helper.assertTrue(standType.summon(user, standPower), "Could not summon " + standId);
			stand = standPower.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned " + standId + " entity is missing");
			// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
			stand.summonLockTicks = 0;
			standPower.setStamina(standPower.getMaxStamina());
			performerActions = LivingComponentAction.getComponent(stand);
		}

		// as a player who can press it has it; the scene does not run the user's tick, whose Resolve rule unlocks skills
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
			helper.assertTrue(ability(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
		}

		private EntityActionAbility ability(String name) {
			Ability found = power != null ? power.getAbility(name) : null;
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			return (EntityActionAbility) found;
		}

		// set on its performer as a press sets it, and held by its key
		private EntityActionInstance start(String name) {
			LivingEntity performer = stand != null ? stand : user;
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, performer, null);
			helper.assertTrue(action != null, "setup: " + name + " made no action");
			performerActions.setAction(null, user, SyncType.NO_SYNC);
			performerActions.setAction(action, user, SyncType.NO_SYNC);
			EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, powerClass, action));
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				performerActions.tick();
			}
		}

		private void release() {
			AbilityInput.keyRelease(KEY, user);
		}

		private void clear() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			performerActions.setAction(null, SyncType.NO_SYNC);
		}

		@Override
		public void close() {
			clear();
			if (stand != null && power instanceof StandPower standPower && standPower.isSummoned() && standType != null) {
				standType.forceUnsummon(user, standPower);
			}
			user.removeAllEffects();
			user.discard();
		}
	}
}
