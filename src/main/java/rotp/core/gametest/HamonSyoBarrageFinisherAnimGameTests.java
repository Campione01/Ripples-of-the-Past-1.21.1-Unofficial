package rotp.core.gametest;

import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.core.JojoMod;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 S.Y.O. Barrage: the finishing punch replaced the layer's clip (KosmXSYOBHandler.setFinisherAnim), so
 * syo_barrage_finisher played from its first tick, and nothing stopped it when the action ended 10 ticks
 * later: its held end pose stayed. The barrage clip before it was a fresh KosmXPlayerBarrageAnim, started when the
 * charge was released. The port samples a clip at the time the action hands over.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonSyoBarrageFinisherAnimGameTests {
	private static final int MAX_BARRAGE_DURATION = 70;
	private static final int FINISHING_PUNCH_DURATION = 10;

	private HamonSyoBarrageFinisherAnimGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void finisherClipPlaysFromItsFirstTick(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the S.Y.O. Barrage test player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Ability found = power.getAbility("sunlight_yellow_overdrive_barrage");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered sunlight_yellow_overdrive_barrage");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);

			// no key holds it: it fires at the full charge
			tickUntil(helper, user, component, action, 70, () -> action.getPhase() == ActionPhase.PERFORM, "the barrage never fired");
			LivingAnimState anim = new LivingAnimState();
			action.extractAnim(anim, user, 0.0F);
			helper.assertTrue("punch_barrage".equals(anim.animId.name()),
					"the barrage loop must play punch_barrage, got " + anim.animId.name());
			// 1.16 createContinuousActionInstance started a fresh KosmXPlayerBarrageAnim (clip tick 0) when the barrage began
			float barrageStart = action.getPhaseTick();
			helper.assertTrue(Math.abs(anim.time - barrageStart) < 0.01F && anim.time < 2.0F,
					"the barrage clip must start at its first tick when the barrage starts, got clip tick " + anim.time
					+ " at barrage tick " + barrageStart);
			tick(user, component, 3);
			action.extractAnim(anim, user, 0.5F);
			helper.assertTrue("punch_barrage".equals(anim.animId.name())
					&& Math.abs(anim.time - (action.getPhaseTick() + 0.5F)) < 0.01F
					&& Math.abs(action.getPhaseTick() - barrageStart - 3.0F) < 0.01F,
					"3 ticks into the barrage its clip must be 3 ticks further, got clip tick " + anim.time
					+ " at barrage tick " + action.getPhaseTick());

			tickUntil(helper, user, component, action, MAX_BARRAGE_DURATION + 5,
					() -> "syo_barrage_finisher".equals(action.getEntityAnim().name()), "the finishing punch never started");
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "the finishing punch belongs to the perform phase");
			float finisherStart = action.getPhaseTick();
			action.extractAnim(anim, user, 0.0F);
			helper.assertTrue(Math.abs(anim.time - (action.getPhaseTick() - MAX_BARRAGE_DURATION)) < 0.01F && anim.time < 2.0F,
					"the finisher clip must start at its first tick when the finishing punch starts, got clip tick " + anim.time);

			tick(user, component, 4);
			helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
					"the finishing punch lasts 10 ticks: phase=" + action.getPhase());
			action.extractAnim(anim, user, 0.5F);
			helper.assertTrue("syo_barrage_finisher".equals(anim.animId.name())
					&& Math.abs(anim.time - (action.getPhaseTick() - MAX_BARRAGE_DURATION + 0.5F)) < 0.01F
					&& Math.abs(action.getPhaseTick() - finisherStart - 4.0F) < 0.01F,
					"4 ticks into the finishing punch the finisher clip must be 4 ticks further, got clip tick " + anim.time);

			tickUntil(helper, user, component, action, FINISHING_PUNCH_DURATION + 2,
					() -> action.getPhase() == ActionPhase.RECOVERY, "the barrage never reached its recovery");
			tick(user, component, 2);
			helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.RECOVERY,
					"the recovery lasts 6 ticks: phase=" + action.getPhase());
			action.extractAnim(anim, user, 0.0F);
			helper.assertTrue("syo_barrage_finisher".equals(anim.animId.name()),
					"1.16 kept the finisher pose after the punch; the recovery plays " + anim.animId.name());
			helper.assertTrue(Math.abs(anim.time - (FINISHING_PUNCH_DURATION + action.getPhaseTick())) < 0.01F,
					"the finisher clip must run on through the recovery, got clip tick " + anim.time
					+ " at recovery tick " + action.getPhaseTick());
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}

	/**
	 * 1.16 syo_barrage_finisher.json: endTick 20. The action ended 10 ticks into the clip and nothing stopped it
	 * (the only stopAnim caller is the held-action stop of the charge), so it played its last 10 ticks afterwards.
	 * The port's action shows the clip through its 6-tick recovery (clip tick 16); the rest must still play.
	 */
	@GameTest(template = "empty", timeoutTicks = 80)
	public static void finisherClipRunsOnToItsEndTickAfterTheAction(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		user.setNoGravity(true);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the S.Y.O. Barrage test player");
		LivingComponentAction component = LivingComponentAction.getComponent(user);
		try {
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			Ability found = power.getAbility("sunlight_yellow_overdrive_barrage");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered sunlight_yellow_overdrive_barrage");
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);

			helper.assertTrue(hamon.getSyoFinisherTailClipTick(user, 0.0F) < 0.0F, "no finisher clip is left over before the barrage");
			tickUntil(helper, user, component, action, 70 + MAX_BARRAGE_DURATION + FINISHING_PUNCH_DURATION + 2,
					() -> action.getPhase() == ActionPhase.RECOVERY, "the barrage never reached its recovery");
			LivingAnimState anim = new LivingAnimState();
			float lastShown = -1.0F;
			for (int i = 0; i < 20 && component.getAction() == action; i++) {
				helper.assertTrue(hamon.getSyoFinisherTailClipTick(user, 0.0F) < 0.0F,
						"no finisher clip is left over while the action still shows it");
				action.extractAnim(anim, user, 0.0F);
				lastShown = anim.time;
				tick(user, component, 1);
			}
			helper.assertTrue(component.getAction() == null && action.isOver() && Math.abs(lastShown - 15.0F) < 0.01F,
					"Fixture: the action ends after showing finisher clip tick 15, got " + lastShown + ", phase=" + action.getPhase());

			for (int clipTick = 16; clipTick < 20; clipTick++) {
				float left = hamon.getSyoFinisherTailClipTick(user, 0.0F);
				helper.assertTrue(Math.abs(left - clipTick) < 0.01F,
						"1.16 let the finisher clip run on to its end tick 20 after the action: " + (clipTick - 16)
								+ " ticks after the port's action ended it must be at clip tick " + clipTick + ", got " + left);
				float midFrame = hamon.getSyoFinisherTailClipTick(user, 0.5F);
				helper.assertTrue(Math.abs(midFrame - (clipTick + 0.5F)) < 0.01F,
						"the finisher clip advances with the partial tick, got " + midFrame + " at clip tick " + clipTick + " + 0.5");
				user.tickCount++;
			}
			float over = hamon.getSyoFinisherTailClipTick(user, 0.0F);
			helper.assertTrue(over < 0.0F, "the finisher clip ends at its end tick 20, got clip tick " + over);
			helper.succeed();
		}
		finally {
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}

	private static void tick(Player user, LivingComponentAction component, int count) {
		// The real action lifecycle, without entity physics or passive Hamon regeneration.
		for (int i = 0; i < count; i++) {
			user.tickCount++;
			component.tick();
		}
	}

	private static void tickUntil(GameTestHelper helper, Player user, LivingComponentAction component,
			EntityActionInstance action, int maxTicks, java.util.function.BooleanSupplier done, String failure) {
		for (int i = 0; i < maxTicks && !done.getAsBoolean(); i++) {
			helper.assertTrue(component.getAction() == action, failure + ": the action ended in phase " + action.getPhase());
			tick(user, component, 1);
		}
		helper.assertTrue(done.getAsBoolean(), failure + ": phase=" + action.getPhase() + " tick=" + action.getPhaseTick());
	}
}
