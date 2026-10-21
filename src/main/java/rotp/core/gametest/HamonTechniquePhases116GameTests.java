package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonBubbleBarrierEntity;
import rotp.core.impl.powers.hamon.entity.HamonCutterEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16's Hamon techniques had no phases (ModHamonActions, PowerBaseImpl): a click performed at once and was over
 * (Speed Boost, Life Magnetism, Bubble Cutter, Hamon Cutter, Plant and Organism Infusion), a holdToFire(N, false) hold
 * fired as it reached N ticks and the hold was over (Hypnosis 60, Bubble Barrier 20), and a holdType hold (Tornado
 * Overdrive) ran until it was let go and left nothing after. The port's first snapshot gave them windups before a
 * click's effect, performs and recoveries after it, that kept the technique running, and the user busy, for up to 16
 * ticks 1.16 did not have.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonTechniquePhases116GameTests {
	private HamonTechniquePhases116GameTests() {}

	private static final short KEY = 41;
	private static final float HELD = 999999;

	private record Phases(String ability, float windup, float perform, float recovery, String in116) {}

	private static final List<Phases> TECHNIQUES = List.of(
			new Phases("hamon_speed_boost", 0, 1, 0, "a click, performed at once"),
			new Phases("life_magnetism", 0, 1, 0, "a click, performed at once"),
			new Phases("hypnosis", 60, 1, 0, "holdToFire(60, false), over as it fired"),
			new Phases("bubble_barrier", 20, 1, 0, "holdToFire(20, false), over as it fired"),
			new Phases("tornado_overdrive", 0, HELD, 0, "holdType, over as it was let go"),
			new Phases("bubble_cutter", 0, 1, 0, "a click, performed at once"),
			new Phases("bubble_cutter_gliding", 0, 1, 0, "a click, performed at once"),
			new Phases("hamon_cutter", 0, 1, 0, "a click, performed at once"),
			new Phases("plant_infusion", 0, 1, 0, "a click, performed at once"),
			new Phases("organism_infusion", 0, 1, 0, "a click, performed at once"));

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theTechniquesTakeTheirPhasesFrom116(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			List<String> wrong = new ArrayList<>();
			for (Phases expected : TECHNIQUES) {
				EntityActionInstance action = f.ability(expected.ability()).initActionOnAbilityUse(helper.getLevel(), f.user, f.user, null);
				float windup = action.phasesLength.getFloat(ActionPhase.WINDUP);
				float perform = action.phasesLength.getFloat(ActionPhase.PERFORM);
				float recovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
				if (windup != expected.windup() || (expected.perform() == HELD ? perform < HELD : perform != expected.perform())
						|| recovery != expected.recovery()) {
					wrong.add(expected.ability() + " (1.16 " + expected.in116() + ") windup " + windup + ", perform " + perform
							+ ", recovery " + recovery);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "1.16's Hamon techniques had no perform beyond the tick they took effect and no"
					+ " recovery: " + wrong);
			helper.succeed();
		}
	}

	// 1.16 PowerBaseImpl.onClickAction performed a click technique on the click: here on its first action tick, after
	// which nothing of it runs.
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void speedBoostTakesEffectOnItsFirstTickAndEndsAsIn116(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.hamon.learnSkill(ModHamonSkills.SPEED_BOOST.get());
			EntityActionInstance action = f.start("hamon_speed_boost", false);
			ActionPhase pressed = action.getPhase();
			f.tick(1);
			helper.assertTrue(pressed == ActionPhase.PERFORM && f.user.hasEffect(MobEffects.MOVEMENT_SPEED) && action.isOver()
					&& f.component.getAction() == null,
					"1.16 Speed Boost took effect on the click and was over: phase at the press " + pressed + ", boosted after"
							+ " one tick " + f.user.hasEffect(MobEffects.MOVEMENT_SPEED) + ", phase after it " + action.getPhase());
			helper.succeed();
		}
	}

	// 1.16 HamonCutter.perform threw its eight cutters on the click, and nothing of the technique ran after it.
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void hamonCutterThrowsOnItsFirstTickAndEndsAsIn116(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.hamon.learnSkill(ModHamonSkills.HAMON_CUTTER.get());
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.POTION));
			EntityActionInstance action = f.start("hamon_cutter", false);
			ActionPhase pressed = action.getPhase();
			f.tick(1);
			int cutters = helper.getLevel().getEntitiesOfClass(HamonCutterEntity.class, f.user.getBoundingBox().inflate(16)).size();
			helper.assertTrue(pressed == ActionPhase.PERFORM && cutters == 8 && action.isOver() && f.component.getAction() == null,
					"1.16 Hamon Cutter threw its cutters on the click and was over: phase at the press " + pressed
							+ ", cutters after one tick " + cutters + ", phase after it " + action.getPhase());
			helper.succeed();
		}
	}

	// 1.16 PowerBaseImpl.tickHeldAction fired a holdToFire(20, false) hold as it reached 20 ticks (stopHeldAction(true)) and
	// the hold was over, the key still down.
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void bubbleBarrierIsOverInTheTickItFiresAsIn116(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper)) {
			f.hamon.learnSkill(ModHamonSkills.BUBBLE_BARRIER.get());
			f.hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL, HamonData.pointsAtLevel(10), true, true);
			f.hamon.setEnergy(f.hamon.getMaxEnergy());
			f.user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
			EntityActionInstance action = f.start("bubble_barrier", true);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP && action.phasesLength.getFloat(ActionPhase.WINDUP) == 20,
					"setup: the Bubble Barrier does not charge for 20 ticks: " + action.getPhase() + " " + action.phasesLength);
			int firesOnTick = -1;
			int overOnTick = -1;
			for (int tick = 1; tick <= 40 && overOnTick < 0; tick++) {
				f.tick(1);
				if (firesOnTick < 0 && action.getPhase() == ActionPhase.PERFORM) {
					firesOnTick = tick + 1;
				}
				if (action.isOver()) {
					overOnTick = tick;
				}
			}
			helper.assertTrue(firesOnTick == 21, "setup: the held Bubble Barrier did not fire on its 21st tick: " + firesOnTick
					+ ", over on " + overOnTick);
			helper.assertTrue(overOnTick == firesOnTick && f.component.getAction() == null,
					"1.16 Bubble Barrier fired as its 20 held ticks ended and the hold was over: fired on tick " + firesOnTick
							+ ", over on tick " + overOnTick);
			helper.succeed();
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Hamon user was not added");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionAbility ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "setup: Hamon has no " + name);
			return (EntityActionAbility) found;
		}

		// as a press sets it, held by its key if the technique holds
		private EntityActionInstance start(String name, boolean heldByKey) {
			EntityActionInstance action = ability(name).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			action.setStartedByClick(!heldByKey);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (heldByKey) {
				user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys
						.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			}
			return action;
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getEntitiesOfClass(HamonBubbleBarrierEntity.class, user.getBoundingBox().inflate(16))
					.forEach(HamonBubbleBarrierEntity::discard);
			helper.getLevel().getEntitiesOfClass(HamonCutterEntity.class, user.getBoundingBox().inflate(16))
					.forEach(HamonCutterEntity::discard);
			user.removeAllEffects();
			user.discard();
		}
	}
}
