package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.starplatinum.SPStarFingerEntity;
import rotp.core.impl.stands.starplatinum.StarFingerAbility.StarFingerInstance;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StarPlatinumStarFinger: the Stand task spawns the finger and then pays 375 stamina without a check
 * (StandEntityTask), the shout plays when the action starts (PowerBaseImpl.onClickAction), and the drawn finger
 * reaches as far as the finger the Stand's speed sends out.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarFingerStartGameTests {
	private static final short KEY = 122;

	private StarFingerStartGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void starFingerFiresOnAShortBarAndDrainsIt(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		float[] staminaAtSpawn = {-1};
		float[] staminaAfterPerform = {-1};
		List<SPStarFingerEntity> fingers = new ArrayList<>();
		Consumer<EntityJoinLevelEvent> spawn = event -> {
			if (event.getEntity() instanceof SPStarFingerEntity finger && finger.getOwner() == scene.stand) {
				fingers.add(finger);
				staminaAtSpawn[0] = scene.power.getStamina();
			}
		};
		Consumer<EntityTickEvent.Post> standTick = event -> {
			if (event.getEntity() == scene.stand && !fingers.isEmpty() && staminaAfterPerform[0] < 0) {
				staminaAfterPerform[0] = scene.power.getStamina();
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
		Runnable close = () -> {
			NeoForge.EVENT_BUS.unregister(spawn);
			NeoForge.EVENT_BUS.unregister(standTick);
			scene.close();
		};
		try {
			scene.start();
			scene.power.setStamina(100);
			AbilityConditionCheck selected = scene.select();
			helper.assertTrue(AbilityInput.withConditionCheck(selected, scene.user, InputMethod.CLICK),
					"1.16 had no stamina check: Star Finger must be admitted at 100 stamina without Resolve");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, scene.user, null, InputMethod.CLICK, 0, press,
					selected.ability.getAbilityId());
			helper.assertTrue(press.isActionSuccess && scene.stand.getCurStandAction() instanceof StarFingerInstance,
					"the admitted press did not start Star Finger");
		}
		catch (RuntimeException | Error failure) {
			close.run();
			throw failure;
		}
		helper.runAfterDelay(5, () -> {
			try {
				helper.assertTrue(fingers.size() == 1, "1.16 spawned one finger whatever the stamina, got " + fingers.size());
				helper.assertTrue(staminaAtSpawn[0] >= 100 && staminaAtSpawn[0] < 375,
						"the finger must be out before the cost is paid: stamina at spawn " + staminaAtSpawn[0]);
				helper.assertTrue(staminaAfterPerform[0] == 0,
						"the 375 cost drains a 100 bar to 0: " + staminaAfterPerform[0]);
			}
			finally {
				close.run();
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void drawnStarFingerFollowsTheStandSpeed(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		double[] speedFactor = {-1};
		List<Float> lengths = new ArrayList<>();
		Consumer<EntityJoinLevelEvent> spawn = event -> {
			if (event.getEntity() instanceof SPStarFingerEntity finger && finger.getOwner() == scene.stand) {
				speedFactor[0] = finger.getSpeedFactor();
			}
		};
		Consumer<EntityTickEvent.Post> standTick = event -> {
			if (event.getEntity() == scene.stand && scene.stand.getCurStandAction() instanceof StarFingerInstance action
					&& action.getPhase() == ActionPhase.PERFORM && action.extendableOBB().isMovingForward()) {
				lengths.add(action.extendableOBB().getLength());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityJoinLevelEvent.class, spawn);
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, standTick);
		Runnable close = () -> {
			NeoForge.EVENT_BUS.unregister(spawn);
			NeoForge.EVENT_BUS.unregister(standTick);
			scene.close();
		};
		try {
			scene.start();
			AbilityConditionCheck selected = scene.select();
			helper.assertTrue(AbilityInput.withConditionCheck(selected, scene.user, InputMethod.CLICK),
					"fixture: Star Finger press not admitted");
			BufferingState press = BufferingState.clickOnly();
			AbilityInput.keyPress(KEY, selected.ability, scene.user, null, InputMethod.CLICK, 0, press,
					selected.ability.getAbilityId());
			helper.assertTrue(press.isActionSuccess, "fixture: Star Finger did not start");
		}
		catch (RuntimeException | Error failure) {
			close.run();
			throw failure;
		}
		helper.runAfterDelay(5, () -> {
			try {
				helper.assertTrue(speedFactor[0] > 1.5, "fixture: Star Platinum's finger is not sped up by its Stand speed: "
						+ speedFactor[0]);
				helper.assertTrue(lengths.size() >= 3, "fixture: too few forward ticks " + lengths);
				float growth = lengths.get(2) - lengths.get(1);
				double expected = 0.3 * 5.5 * speedFactor[0];
				helper.assertTrue(Math.abs(growth - expected) < 0.01,
						"the drawn finger must grow like the finger the Stand's speed sends out: " + growth
								+ " per tick, expected " + expected + " lengths=" + lengths);
			}
			finally {
				close.run();
			}
			helper.succeed();
		});
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		Player user;
		StandPower power;
		StandType type;
		StandEntity stand;
		boolean closed;

		Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		// High above the test cell and looking straight up, so the finger reaches no neighbouring cell.
		void start() {
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 pos = helper.absoluteVec(new Vec3(2.5, 12, 2.5));
			user.setNoGravity(true);
			user.moveTo(pos.x, pos.y, pos.z, 0, -90);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Star Platinum user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(3);
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setXRot(-90);
			// full stamina first: the Stand's strength multipliers read it
			power.setStamina(power.getMaxStamina());
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(16);
			StandEntity.updateStandStatAttributes(stand, user);
			stand.updateStrengthMultipliers();
		}

		AbilityConditionCheck select() {
			Ability starFinger = power.getAbility("star_finger");
			AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(starFinger);
			helper.assertTrue(selected != null, "Star Finger is not in the moveset");
			return selected;
		}

		void close() {
			if (closed) return;
			closed = true;
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
				for (SPStarFingerEntity finger : level.getEntitiesOfClass(SPStarFingerEntity.class,
						new AABB(user.blockPosition()).inflate(16))) {
					finger.discard();
				}
				if (power != null && type != null && power.isSummoned()) {
					type.forceUnsummon(user, power);
				}
				user.discard();
			}
		}
	}
}
