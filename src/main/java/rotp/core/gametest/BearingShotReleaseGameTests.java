package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.ThrownNuggetBearingEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopState;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Bearing Shot checks for a nugget only when the key is pressed, and the Stand's hand can be emptied or swapped while
 * the key is held (the Stand item keys). Released without a nugget, the Stand recovers without a shot: no bearing, no
 * consumed item, no stamina. An empty bearing used to fly and crashed the server through a portal.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BearingShotReleaseGameTests {
	private static final short KEY = 9;

	private BearingShotReleaseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void releaseShootsOnlyANuggetStillHeld(GameTestHelper helper) {
		scene(helper, "BearingRelease", (user, power, stand, standAction, bearingShot, area) -> {
			// The hand emptied during the hold (Ctrl+F with empty hands takes the nugget back).
			releaseWith(helper, user, power, stand, standAction, bearingShot, area, ItemStack.EMPTY, "an empty hand");
			// A sword swapped into the Stand's hand during the hold is neither shot nor used up.
			ItemStack sword = releaseWith(helper, user, power, stand, standAction, bearingShot, area,
					new ItemStack(Items.IRON_SWORD), "a sword");
			helper.assertTrue(sword.is(Items.IRON_SWORD) && sword.getCount() == 1 && stand.getMainHandItem() == sword,
					"the sword in the Stand's hand was used up by the release: " + stand.getMainHandItem());

			// Control: a nugget still held is shot, used up and paid for.
			EntityActionInstance fired = hold(helper, user, power, stand, standAction, bearingShot);
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_NUGGET, 2));
			float stamina = power.getStamina();
			int bearings = bearings(helper, area);
			release(user, fired);
			helper.assertTrue(fired.getPhase() == ActionPhase.RECOVERY, "the nugget release did not recover: " + fired.getPhase());
			helper.assertTrue(bearings(helper, area) == bearings + 1, "the nugget release did not shoot one bearing");
			helper.assertTrue(stand.getMainHandItem().is(Items.IRON_NUGGET) && stand.getMainHandItem().getCount() == 1,
					"the nugget release did not use up one nugget: " + stand.getMainHandItem());
			helper.assertTrue(Math.abs(stamina - power.getStamina() - 10) < 0.01F,
					"the nugget release did not cost 10 stamina: " + stamina + " -> " + power.getStamina());
		});
	}

	// 1.16 stopHeldAction(true) rechecked the action before a released hold fired: a stunned Stand fires nothing.
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void releaseByAStunnedStandShootsNothing(GameTestHelper helper) {
		scene(helper, "BearingStunned", (user, power, stand, standAction, bearingShot, area) -> {
			EntityActionInstance held = hold(helper, user, power, stand, standAction, bearingShot);
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			helper.assertTrue(ModStatusEffects.isStunned(stand), "setup: the Stand is not stunned");
			assertNoShot(helper, user, power, stand, held, area, "a stunned Stand");
			stand.removeEffect(ModStatusEffects.STUN);
		});
	}

	// 1.16 checkConditions refused a user frozen in someone else's stopped time, and stopHeldAction(true) ran it.
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void releaseWhileFrozenInStoppedTimeShootsNothing(GameTestHelper helper) {
		scene(helper, "BearingFrozen", (user, power, stand, standAction, bearingShot, area) -> {
			// put straight into its held phase: another stop of this batch may already freeze the cell, so it cannot tick there
			EntityActionInstance held = start(helper, user, power, stand, standAction, bearingShot);
			held.setPhaseStart(ActionPhase.PERFORM);
			TimeStopState timeStops = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
			int stopId = -(user.getId() * 4 + 3);
			try {
				// A stop owned by no one: the user is not its stopper.
				helper.assertTrue(timeStops.tryPutInstance(new TimeStopState.Instance(stopId, 200, 200,
						new ChunkPos(user.blockPosition()), 1, -1, "bearing_release_frozen_test"))
						&& timeStops.shouldFreeze(user), "setup: the user did not freeze in the stopped time");
				assertNoShot(helper, user, power, stand, held, area, "a user frozen in stopped time");
			}
			finally {
				// Removed before the level ticks, so no neighbouring test is frozen.
				timeStops.removeInstance(stopId);
			}
		});
	}

	// 1.16 Stand item use left a Creative user's items alone (!abilities.instabuild).
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void creativeReleaseShootsWithoutUsingTheNugget(GameTestHelper helper) {
		scene(helper, "BearingCreative", (user, power, stand, standAction, bearingShot, area) -> {
			user.getAbilities().instabuild = true;
			helper.assertTrue(user.hasInfiniteMaterials(), "setup: the user is not in Creative");
			EntityActionInstance held = hold(helper, user, power, stand, standAction, bearingShot);
			int bearings = bearings(helper, area);
			release(user, held);
			helper.assertTrue(bearings(helper, area) == bearings + 1, "the Creative release did not shoot one bearing");
			helper.assertTrue(stand.getMainHandItem().is(Items.IRON_NUGGET) && stand.getMainHandItem().getCount() == 2,
					"a Creative user's release used up a nugget: " + stand.getMainHandItem());
		});
	}

	private interface Body {
		void run(Player user, StandPower power, StandEntity stand, LivingComponentAction standAction,
				EntityActionAbility bearingShot, AABB area);
	}

	private static void scene(GameTestHelper helper, String name, Body body) {
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32.0D);
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the bearing test player");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Star Platinum entity is missing");
			stand.summonLockTicks = 0;
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			Ability found = power.getAbility("bearing_shot");
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered bearing_shot");
			EntityActionAbility bearingShot = (EntityActionAbility) found;
			body.run(user, power, stand, standAction, bearingShot, area);
			helper.succeed();
		}
		finally {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			for (ThrownNuggetBearingEntity bearing : helper.getLevel().getEntitiesOfClass(ThrownNuggetBearingEntity.class, area)) {
				bearing.discard();
			}
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void assertNoShot(GameTestHelper helper, Player user, StandPower power, StandEntity stand,
			EntityActionInstance held, AABB area, String what) {
		float stamina = power.getStamina();
		int bearings = bearings(helper, area);
		release(user, held);
		helper.assertTrue(bearings(helper, area) == bearings, "the release by " + what + " still shot a bearing");
		helper.assertTrue(stand.getMainHandItem().is(Items.IRON_NUGGET) && stand.getMainHandItem().getCount() == 2,
				"the release by " + what + " used up a nugget: " + stand.getMainHandItem());
		helper.assertTrue(Math.abs(stamina - power.getStamina()) < 0.01F,
				"the release by " + what + " still cost stamina: " + stamina + " -> " + power.getStamina());
	}

	private static ItemStack releaseWith(GameTestHelper helper, Player user, StandPower power, StandEntity stand,
			LivingComponentAction standAction, EntityActionAbility bearingShot, AABB area, ItemStack inHand, String what) {
		EntityActionInstance held = hold(helper, user, power, stand, standAction, bearingShot);
		stand.setItemSlot(EquipmentSlot.MAINHAND, inHand);
		float stamina = power.getStamina();
		int bearings = bearings(helper, area);
		release(user, held);
		helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY,
				"the release with " + what + " did not go into recovery: " + held.getPhase());
		helper.assertTrue(bearings(helper, area) == bearings,
				"the release with " + what + " in the Stand's hand still shot a bearing");
		helper.assertTrue(Math.abs(stamina - power.getStamina()) < 0.01F,
				"the release with " + what + " still cost stamina: " + stamina + " -> " + power.getStamina());
		return stand.getMainHandItem();
	}

	// bearing_shot with a nugget, held by its key into its held PERFORM phase
	private static EntityActionInstance hold(GameTestHelper helper, Player user, StandPower power, StandEntity stand,
			LivingComponentAction standAction, EntityActionAbility bearingShot) {
		EntityActionInstance action = start(helper, user, power, stand, standAction, bearingShot);
		for (int tick = 0; tick < 30 && action.getPhase() != ActionPhase.PERFORM; tick++) {
			standAction.tick();
		}
		helper.assertTrue(standAction.getAction() == action && action.getPhase() == ActionPhase.PERFORM,
				"the bearing shot is not held: phase=" + action.getPhase());
		return action;
	}

	// bearing_shot with a nugget, set on the Stand and held by its key
	private static EntityActionInstance start(GameTestHelper helper, Player user, StandPower power, StandEntity stand,
			LivingComponentAction standAction, EntityActionAbility bearingShot) {
		standAction.setAction(null, user, SyncType.NO_SYNC);
		power.setStamina(power.getMaxStamina());
		stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_NUGGET, 2));
		EntityActionInstance action = bearingShot.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		standAction.setAction(action, user, SyncType.NO_SYNC);
		EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.STAND, action));
		return action;
	}

	private static void release(Player user, EntityActionInstance action) {
		user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
		action.onKeyRelease(user);
	}

	private static int bearings(GameTestHelper helper, AABB area) {
		return helper.getLevel().getEntitiesOfClass(ThrownNuggetBearingEntity.class, area).size();
	}
}
