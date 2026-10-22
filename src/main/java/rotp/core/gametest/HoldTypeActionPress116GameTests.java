package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MRDetectorEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.syncdata.TrActionSynchedDataPacket;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_grab.LivingComponentGrab;
import rotp.core.subsystems.itemtracking.OriginalItemPosComponent;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.reflection.CommonReflection;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 hold-type Stand actions (Action.Builder#holdType: held from the press, nothing to fire): StandEntityAction
 * .heldAllowsOtherAction was true, so PowerBaseImpl.checkRequirements let any other action of the power through the hold;
 * an action the Stand performs went through checkTaskCancelling, where isCancelable (a hold, not in its recovery, no
 * recovery for a pressed action) let it replace the task at once, and every action that went off ended the hold
 * (stopHeldAction(false) after a click, setHeldAction before a hold). Presses 1.16 refused (the hold's own action, any
 * action during a hold-to-fire charge, a player's held action while the power held one, which its client never sent) stay
 * in the port's input buffer (owner decision N2).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HoldTypeActionPress116GameTests {
	private static final short HOLD_KEY = 41;
	private static final short OTHER_KEY = 42;
	private static final short SECOND_BINDING_KEY = 43;

	private HoldTypeActionPress116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHoldTypeStandActionLetsAnotherStandActionThroughAtOnceAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HoldTypeMagiciansRed", "magicians_red", 4)) {
			s.unlock("red_bind", "flame_burst", "fireball", "crossfire_hurricane");
			s.goesThrough("red_bind", "punch", InputMethod.CLICK);
			s.goesThrough("flame_burst", "heavy_punch", InputMethod.CLICK);

			// 1.16 refused the hold's own action pressed again (onClickAction, getHeldAction() == action): buffered (N2)
			EntityActionInstance redBind = s.hold("red_bind");
			BufferingState again = s.press("red_bind", InputMethod.HOLD, SECOND_BINDING_KEY);
			helper.assertTrue(again.shouldBuffer && !again.isActionSuccess && s.standAction.getAction() == redBind
					&& redBind.getPhase() == ActionPhase.PERFORM,
					"1.16 refused the Red Bind pressed again by a second binding during its own hold, which the input buffer"
							+ " keeps: buffered " + again.shouldBuffer + ", the Stand's action " + s.standAction.getAction());
			AbilityInput.keyRelease(SECOND_BINDING_KEY, s.user);

			// 1.16's client sent no press of a held action while the power held one (onActionClick): buffered (N2)
			BufferingState heldPress = s.press("flame_burst", InputMethod.HOLD, OTHER_KEY);
			helper.assertTrue(heldPress.shouldBuffer && !heldPress.isActionSuccess && s.standAction.getAction() == redBind
					&& redBind.getPhase() == ActionPhase.PERFORM,
					"1.16's client sent no flame burst (a held action) pressed while the Red Bind was held, which the input"
							+ " buffer keeps: buffered " + heldPress.shouldBuffer + ", the Stand's action "
							+ s.standAction.getAction());
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			s.clear();

			// a Red Bind no key holds any more (one let go at a finisher with a landed bind stays) took a held press, but
			// not its own (canClickDuringTask: not chainable)
			redBind = s.hold("red_bind");
			s.input().heldKeys.remove(HOLD_KEY);
			BufferingState ownLetGo = s.press("red_bind", InputMethod.HOLD, SECOND_BINDING_KEY);
			helper.assertTrue(ownLetGo.shouldBuffer && s.standAction.getAction() == redBind,
					"1.16 refused the Red Bind pressed again during its own task, which the input buffer keeps: buffered "
							+ ownLetGo.shouldBuffer + ", the Stand's action " + s.standAction.getAction());
			AbilityInput.keyRelease(SECOND_BINDING_KEY, s.user);
			BufferingState afterLetGo = s.press("flame_burst", InputMethod.HOLD, OTHER_KEY);
			EntityActionInstance flameBurst = s.standAction.getAction();
			helper.assertTrue(afterLetGo.isActionSuccess && redBind.isOver() && flameBurst != null
					&& flameBurst.ability == s.power.getAbility("flame_burst"),
					"1.16 let a held flame burst through a Red Bind its key no longer held (the power held no action):"
							+ " started " + afterLetGo.isActionSuccess + ", the Stand's action " + flameBurst);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			s.clear();

			// 1.16 isCancelable: a hold with a recovery queued the pressed action
			redBind = s.hold("red_bind");
			redBind.phasesLength.put(ActionPhase.RECOVERY, 20F);
			BufferingState queued = s.press("punch", InputMethod.CLICK, OTHER_KEY);
			helper.assertTrue(queued.shouldBuffer && s.standAction.getAction() == redBind,
					"1.16 queued a punch pressed during a hold-type action with a recovery (isCancelable): buffered "
							+ queued.shouldBuffer + ", the Stand's action " + s.standAction.getAction());
			s.clear();

			// a hold-to-fire charge refused other actions in 1.16 (heldAllowsOtherAction false): buffered (N2)
			EntityActionInstance charge = s.hold("crossfire_hurricane");
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE,
					"setup: Crossfire Hurricane is not charging: " + charge.getPhase());
			BufferingState duringCharge = s.press("punch", InputMethod.CLICK, OTHER_KEY);
			helper.assertTrue(duringCharge.shouldBuffer && s.standAction.getAction() == charge,
					"1.16 refused a punch pressed while Crossfire Hurricane charged (hold-to-fire): buffered "
							+ duringCharge.shouldBuffer + ", the Stand's action " + s.standAction.getAction());
			s.clear();

			// an action that is not held (the fireball) was not cancelable in 1.16: the punch waits behind it
			EntityActionInstance fireball = s.start("fireball");
			BufferingState afterFireball = s.press("punch", InputMethod.CLICK, OTHER_KEY);
			helper.assertTrue(afterFireball.shouldBuffer && s.standAction.getAction() == fireball,
					"1.16 queued a punch pressed during the fireball (not a hold): buffered " + afterFireball.shouldBuffer
							+ ", the Stand's action " + s.standAction.getAction());
			s.clear();
		}
		try (Scene s = new Scene(helper, "HoldTypeHierophant", "hierophant_green", 4)) {
			s.unlock("grapple", "emerald_splash");
			s.goesThrough("grapple", "emerald_splash", InputMethod.HOLD);
		}
		try (Scene s = new Scene(helper, "HoldTypeGoldExperience", "gold_experience", 4)) {
			s.unlock("life_detector");
			s.goesThrough("life_detector", "punch", InputMethod.CLICK);
		}
		try (Scene s = new Scene(helper, "HoldTypeCrazyDiamond", "crazy_diamond", 4)) {
			s.unlock("restore_terrain", "block_anchor");
			s.goesThrough("restore_terrain", "punch", InputMethod.CLICK);
			ItemStack anchor = new ItemStack(Items.COBBLESTONE);
			anchor.set(ModItemDataComponents.ORIGINAL_POS,
					new OriginalItemPosComponent(BlockPos.containing(s.origin).above(3), helper.getLevel().dimension()));
			s.user.setItemInHand(InteractionHand.OFF_HAND, anchor);
			s.goesThrough("block_anchor", "punch", InputMethod.CLICK);
		}
		helper.succeed();
	}

	/*
	 * 1.16's client sent no press of a held action while the power held one (ActionsOverlayGui.onActionClick), also where
	 * the held action let every other action replace it at once (the guard, Star Platinum's zoom and inhale, Crazy
	 * Diamond's heal, revert and repair): the input buffer keeps such a press (N2) and starts it once the hold is let go;
	 * a click goes through the hold as before, and so does a mob's held press.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPlayersHeldPressWaitsWhileTheStandPowerHoldsAnActionAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldPressStarPlatinum", "star_platinum", 4)) {
			s.unlock("inhale", "enhanced_eyesight");
			s.heldPressWaits("guard", "barrage");
			s.heldPressWaits("enhanced_eyesight", "barrage");
			s.heldPressWaits("inhale", "guard");
			s.goesThrough("guard", "punch", InputMethod.CLICK);

			// let go, the guard holds nothing: the barrage still held goes through
			EntityActionInstance guard = s.hold("guard");
			BufferingState waiting = s.press("barrage", InputMethod.HOLD, OTHER_KEY);
			helper.assertTrue(waiting.shouldBuffer && s.standAction.getAction() == guard, "setup: the barrage pressed during the"
					+ " held guard was not buffered");
			List<Ability> replays = new ArrayList<>();
			Consumer<RipplesAbilityKeyPressEvent> listener = event -> {
				if (event.getEntity() == s.user && event.isBufferedReplay()) {
					replays.add(event.getAbility());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RipplesAbilityKeyPressEvent.class, listener);
			try {
				s.input().inputBuffer.tickInputBuffer(s.input());
			}
			finally {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			helper.assertTrue(s.standAction.getAction() == guard && guard.getPhase() == ActionPhase.PERFORM && replays.isEmpty(),
					"the buffered barrage waits while the guard is held, and is not tried again before it can start (1.16"
							+ " StandEntity.checkInputBuffer): the Stand's action " + s.standAction.getAction() + ", replays "
							+ replays);
			AbilityInput.keyRelease(HOLD_KEY, s.user);
			helper.assertTrue(guard.isOver() || guard.getPhase() == ActionPhase.RECOVERY, "setup: the guard let go still holds: "
					+ guard.getPhase());
			s.input().inputBuffer.tickInputBuffer(s.input());
			EntityActionInstance barrage = s.standAction.getAction();
			helper.assertTrue(barrage != null && barrage.ability == s.power.getAbility("barrage") && s.isHeldByKey(OTHER_KEY),
					"the barrage key still held once the guard was let go starts the barrage (the power holds nothing): the"
							+ " Stand's action " + barrage);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			s.clear();
		}
		try (Scene s = new Scene(helper, "HeldPressCrazyDiamond", "crazy_diamond", 4)) {
			s.unlock("heal", "revert_state", "repair_item");
			Creeper creeper = EntityType.CREEPER.create(helper.getLevel());
			helper.assertTrue(creeper != null, "setup: could not create the creeper");
			try {
				creeper.setNoAi(true);
				creeper.setNoGravity(true);
				creeper.moveTo(s.origin.x, s.origin.y, s.origin.z + 2.0D, 180.0F, 0.0F);
				helper.assertTrue(helper.getLevel().addFreshEntity(creeper), "setup: could not add the creeper");
				CommonReflection.setCreeperPowered(creeper, true);
				creeper.setHealth(creeper.getMaxHealth() - 6.0F);
				LivingComponentAction.getComponent(s.user).entityAim.setTarget(new ActionTarget(creeper));
				s.heldPressWaits("heal", "revert_state");
				s.heldPressWaits("revert_state", "heal");
				ItemStack sword = new ItemStack(Items.IRON_SWORD);
				sword.setDamageValue(sword.getMaxDamage() - 10);
				s.user.setItemInHand(InteractionHand.OFF_HAND, sword);
				s.heldPressWaits("repair_item", "heal");
			}
			finally {
				creeper.discard();
			}
		}

		// a mob's held press went to the server, through the guard
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Star Platinum");
			power.setResolveLevel(4);
			helper.assertTrue(type.summon(zombie, power), "setup: could not summon the zombie's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.summonLockTicks = 0;
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			power.setStamina(power.getMaxStamina());
			AbilityInput.keyPressMob(power.getAbility("guard"), zombie, null, InputMethod.HOLD);
			EntityActionInstance guard = standAction.getAction();
			helper.assertTrue(guard != null && guard.ability == power.getAbility("guard"), "setup: the zombie's guard is not held: "
					+ guard);
			AbilityInput.keyPressMob(power.getAbility("barrage"), zombie, null, InputMethod.HOLD);
			EntityActionInstance now = standAction.getAction();
			helper.assertTrue(now != null && now != guard && now.ability == power.getAbility("barrage"), "1.16 let a mob's"
					+ " barrage through its Stand's held guard at once: the Stand's action " + now);
		}
		finally {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/*
	 * 1.16's client sent no time stop (hold-to-fire) pressed while the power held the guard, a barrage, Star Platinum's zoom
	 * or inhale (ActionsOverlayGui.onActionClick), so the hold went on and no charge started (TimeStop.startedHolding stopped
	 * the Stand's task only for a press the server got): the press waits in the input buffer (N2) instead of the time stop
	 * ending the hold before its own press is held back, and its charge starts once the hold is let go. A mob's time stop,
	 * pressed on the server in 1.16 too, still ends its Stand's guard or barrage at once, its charge in the mob's
	 * Stand-power slot (its power held it). No charge here is let go (no time stop starts).
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aTimeStopPressedWhileTheStandPowerHoldsAnActionWaitsAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldTimeStopStarPlatinum", "star_platinum", 4)) {
			s.unlock("time_stop", "inhale", "enhanced_eyesight");
			for (String held : new String[] { "guard", "barrage", "enhanced_eyesight", "inhale" }) {
				s.timeStopWaits(held);
			}
		}
		try (Scene s = new Scene(helper, "HeldTimeStopTheWorld", "the_world", 2)) {
			s.unlock("time_stop");
			for (String held : new String[] { "guard", "barrage" }) {
				s.timeStopWaits(held);
			}
		}

		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		LivingComponentAction standAction = null;
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Star Platinum");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove("time_stop");
			helper.assertTrue(type.summon(zombie, power), "setup: could not summon the zombie's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.summonLockTicks = 0;
			standAction = LivingComponentAction.getComponent(stand);
			LivingComponentAction zombieSlot = LivingComponentAction.getComponent(zombie, PowerClass.STAND);
			// the barrage gives way to no other ability before its recovery: the time stop ends it itself (startedHolding)
			for (String held : new String[] { "guard", "barrage" }) {
				power.setStamina(power.getMaxStamina());
				AbilityInput.keyPressMob(power.getAbility(held), zombie, null, InputMethod.HOLD);
				EntityActionInstance hold = standAction.getAction();
				helper.assertTrue(hold != null && hold.ability == power.getAbility(held) && hold.getPhase() == ActionPhase.PERFORM,
						"setup: the zombie's " + held + " is not held: " + hold);
				AbilityInput.keyPressMob(power.getAbility("time_stop"), zombie, null, InputMethod.HOLD);
				EntityActionInstance now = zombieSlot.getAction();
				helper.assertTrue(now != null && now.ability == power.getAbility("time_stop")
						&& now.getPhase() == ActionPhase.BUTTON_CHARGE && hold.isOver() && standAction.getAction() == null,
						"1.16 let a mob's time stop end its Stand's held " + held + " at once (a mob pressed on the server) and its"
								+ " power held the charge: the zombie's Stand-power action " + now + ", the Stand's "
								+ standAction.getAction() + ", the " + held + " over " + hold.isOver());
				zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				standAction.setAction(null, zombie, SyncType.NO_SYNC);
				zombieSlot.setAction(null, zombie, SyncType.NO_SYNC);
			}
		}
		finally {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (standAction != null) {
				standAction.setAction(null, zombie, SyncType.NO_SYNC);
				LivingComponentAction.getComponent(zombie, PowerClass.STAND).setAction(null, zombie, SyncType.NO_SYNC);
			}
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	/*
	 * 1.16 PowerBaseImpl.checkRequirements refused, without a message, every other action of the power while it held an
	 * action that let none through (heldAllowsOtherAction false: a hold-to-fire charge), the ones the Stand does not perform
	 * too: Magician's Red's detector during Crossfire Hurricane's charge is refused, not buffered, and goes off again once
	 * the charge is let go; the same for Gold Experience's lifeform choice during its charged heavy punch (a port charge);
	 * the manual control key, which was no 1.16 action, still works.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandAbilityTheStandDoesNotPerformIsRefusedDuringAChargeAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "ChargeRefusesDetector", "magicians_red", 4)) {
			s.unlock("crossfire_hurricane", "mr_detector");
			EntityActionInstance charge = s.hold("crossfire_hurricane");
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: Crossfire Hurricane is not charging: "
					+ charge.getPhase());
			s.refusedDuring(charge, "mr_detector");
			helper.assertTrue(s.detectors().isEmpty(), "1.16 refused the detector during the charge, yet one was placed");

			BufferingState manual = s.press("manual_control", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(manual.isActionSuccess && s.stand.isManuallyControlled(), "the manual control key, no action in"
					+ " 1.16, works during the charge: went off " + manual.isActionSuccess);
			StandEntityManualControlToggle.off(helper.getLevel(), s.stand, false);

			AbilityInput.keyRelease(HOLD_KEY, s.user);
			BufferingState after = s.press("mr_detector", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(after.isActionSuccess && !s.detectors().isEmpty(), "the detector goes off once the charge is let"
					+ " go (the power holds nothing): went off " + after.isActionSuccess);
			s.detectors().forEach(MRDetectorEntity::discard);
			s.clear();
		}
		try (Scene s = new Scene(helper, "ChargeRefusesLifeform", "gold_experience", 4)) {
			EntityActionInstance charge = s.hold("heavy_charged");
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: the charged heavy punch is not charging: "
					+ charge.getPhase());
			s.refusedDuring(charge, "choose_lifeform");
			s.clear();
		}
		helper.succeed();
	}

	/*
	 * 1.16 StandEntityAction.isCancelable held for a hold-to-fire Stand action once it fired too (no longer the power's
	 * held action), in its windup and perform, if it had no recovery: a punch pressed then replaced Crossfire Hurricane at
	 * once, where the port buffered it. Crossfire Hurricane is given a perform here, and a windup in the rows that name one
	 * (its own are none and one tick); a perform it fired straight into gives way after its first tick (the shot), one that
	 * came after a windup from its start, and a recovery keeps the punch waiting.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandChargeThatFiredLetsAnotherStandActionThroughAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "FiredChargeMagiciansRed", "magicians_red", 4)) {
			s.unlock("crossfire_hurricane");
			s.punchAfterFire(ActionPhase.WINDUP, 0, 10F, 0F, true);
			s.punchAfterFire(ActionPhase.PERFORM, 0, 0F, 0F, false);
			s.punchAfterFire(ActionPhase.PERFORM, 0, 10F, 0F, true);
			s.punchAfterFire(ActionPhase.PERFORM, 1, 0F, 0F, true);
			s.punchAfterFire(ActionPhase.PERFORM, 1, 10F, 0F, true);
			s.punchAfterFire(ActionPhase.WINDUP, 0, 10F, 10F, false);
		}
		helper.succeed();
	}

	/*
	 * 1.16 moved a fired hold-to-fire task with a windup (standWindupDuration) into its perform at the end of the windup's
	 * last tick and performed it on the next (StandEntityTask.tick, moveToPhase), so a press between the two cancelled it
	 * before the shot (isCancelable: not in its recovery, none to come). The port keeps the shot of a perform the charge
	 * fired straight into (EntityActionInstance.isFiredStandCharge). Crossfire Hurricane held to its full charge and ticked
	 * as the Stand ticks it, with a windup of 3 or none: at its perform's start a punch replaces it and no shot is fired, or
	 * waits and the shot follows on the next tick. A port charge whose windup is its held phase (the charged heavy punch,
	 * given no recovery here) fires straight into its perform as it is let go.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPressAtTheStartOfAPerformAfterAFiredChargesWindupCancelsItAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "FiredChargeWindupMagiciansRed", "magicians_red", 4)) {
			s.unlock("crossfire_hurricane");
			s.punchAtPerformStart(3F, true);
			s.punchAtPerformStart(0F, false);
			s.heldWindupFiresStraight("heavy_charged");
		}
		helper.succeed();
	}

	/*
	 * 1.16 ticked the Stand's task in the Stand's own tick (StandEntity.tick, the level's entity loop) and its user's
	 * power in the player tick after the levels (PlayerTickEvent): tickHeldAction fired a holdToFire(20, false) charge
	 * (Crossfire Hurricane) as its held count reached 20, in the 20th tick from the press, after the Stand's 20th charge
	 * tick, and StandEntityAction.perform only moved the task on (moveToPhase), so the Stand shot in its next tick, the
	 * 21st. A Stand ability's charge its user performs (an add-on's, here Crossfire Hurricane set on its user, which then
	 * shoots nothing) performs in the 20th tick, as 1.16's power performed a StandAction.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aChargeTheStandPerformsShootsInTheStandsTickAfterItFiresAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "ChargeFireTickMagiciansRed", "magicians_red", 4)) {
			s.unlock("crossfire_hurricane");
			int shotTick = s.crossfireShotTick();
			helper.assertTrue(shotTick == 21, "1.16's Stand shot a held Crossfire Hurricane (holdToFire(20, false)) in its"
					+ " tick after the 20th from the press, in which the hold fired: shot in tick " + shotTick);
			int performTick = s.userPerformedChargeTick("crossfire_hurricane");
			helper.assertTrue(performTick == 20, "a Stand ability's held charge its user performs performs in the 20th tick"
					+ " from its press, as 1.16's power performed a StandAction there: performed in tick " + performTick);
		}
		helper.succeed();
	}

	/*
	 * 1.16 TimeStop was a StandAction its user's power performed: tickHeldAction fired the holdToFire(30, false) hold of
	 * The World's time stop in the 30th tick from the press and stopped time in that tick, also with The World out, its
	 * charge in its user's Stand-power slot. The stop is moved far from every test cell and taken out before the test
	 * returns.
	 */
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aTimeStopTheStandChargesStopsTimeInTheTickItFiresAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "TimeStopFireTickTheWorld", "the_world", 2)) {
			s.unlock("time_stop");
			int stopTick = s.timeStopStartTick();
			helper.assertTrue(stopTick == 30, "1.16 stopped time in the 30th tick from the press of The World's time stop"
					+ " (holdToFire(30, false)), the Stand charging it: stopped in tick " + stopTick);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aMobsStandActionGoesThroughItsHoldTypeActionAsIn116(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Magician's Red");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove("red_bind");
			power.getCurTypeData()._lockedAbilities.remove("flame_burst");
			helper.assertTrue(type.summon(zombie, power), "setup: could not summon the zombie's Magician's Red");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the zombie's Stand is missing");
			stand.summonLockTicks = 0;
			LivingComponentAction standAction = LivingComponentAction.getComponent(stand);
			// a punch (a click in 1.16), then a flame burst (a held action, which a mob pressed on the server too)
			for (String pressed : new String[] { "punch", "flame_burst" }) {
				power.setStamina(power.getMaxStamina());
				AbilityInput.keyPressMob(power.getAbility("red_bind"), zombie, null, InputMethod.HOLD);
				EntityActionInstance redBind = standAction.getAction();
				helper.assertTrue(redBind != null && redBind.ability == power.getAbility("red_bind")
						&& redBind.getPhase() == ActionPhase.PERFORM, "setup: the zombie's Red Bind is not held: " + redBind);
				AbilityInput.keyPressMob(power.getAbility(pressed), zombie, null,
						pressed.equals("punch") ? InputMethod.CLICK : InputMethod.HOLD);
				EntityActionInstance now = standAction.getAction();
				helper.assertTrue(now != null && now != redBind && now.ability == power.getAbility(pressed)
						&& redBind.isOver(), "1.16 let a mob's " + pressed + " through its Stand's Red Bind hold at once (a"
								+ " mob's press cannot wait in the buffer, so it was lost): the Stand's action " + now
								+ ", the Red Bind over " + redBind.isOver());
				zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
				standAction.setAction(null, zombie, SyncType.NO_SYNC);
			}
		}
		finally {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandPressThatDoesNotReplaceAHoldTypeActionEndsItsHoldAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HoldTypeDetector", "magicians_red", 4)) {
			s.unlock("red_bind", "mr_detector");
			for (String held : new String[] { "red_bind", "guard" }) {
				EntityActionInstance hold = s.hold(held);
				BufferingState detector = s.press("mr_detector", InputMethod.CLICK, OTHER_KEY);
				AbilityInput.keyRelease(OTHER_KEY, s.user);
				helper.assertTrue(detector.isActionSuccess && !s.detectors().isEmpty(),
						"setup: the detector pressed during " + held + " did not go off");
				helper.assertTrue(hold.isOver() || hold.getPhase() == ActionPhase.RECOVERY,
						"1.16 ended the " + held + " hold once the detector pressed during it went off (stopHeldAction(false)):"
								+ " it is still in " + hold.getPhase());
				helper.assertTrue(!s.isHeldByKey(HOLD_KEY),
						"1.16 ended the " + held + " hold, so its key held nothing more, but it still holds " + hold);
				s.detectors().forEach(MRDetectorEntity::discard);
				s.clear();
			}

			// the manual control key was no action in 1.16 (StandUtil.setManualControl) and left a hold as it was
			EntityActionInstance redBind = s.hold("red_bind");
			BufferingState manual = s.press("manual_control", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(manual.isActionSuccess && s.stand.isManuallyControlled(),
					"setup: the manual control key did not take the Stand over");
			helper.assertTrue(redBind.getPhase() == ActionPhase.PERFORM && s.standAction.getAction() == redBind
					&& s.isHeldByKey(HOLD_KEY), "1.16 kept the Red Bind held when its user took the Stand over (the manual"
							+ " control key was no action): phase " + redBind.getPhase());
			StandEntityManualControlToggle.off(helper.getLevel(), s.stand, false);
			s.clear();
		}
		// 1.16 StandEntityMeleeBarrage was a holdType action too: the hold went into its recovery
		try (Scene s = new Scene(helper, "HoldTypeBarrage", "gold_experience", 4)) {
			EntityActionInstance barrage = s.hold("barrage");
			BufferingState choose = s.press("choose_lifeform", InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, s.user);
			helper.assertTrue(choose.isActionSuccess, "setup: choosing a lifeform during the barrage did not go off");
			helper.assertTrue((barrage.isOver() || barrage.getPhase() == ActionPhase.RECOVERY) && !s.isHeldByKey(HOLD_KEY),
					"1.16 sent the barrage into its recovery once a lifeform choice pressed during it went off: phase "
							+ barrage.getPhase() + ", its key holds it " + s.isHeldByKey(HOLD_KEY));
			s.clear();
		}
		helper.succeed();
	}

	/*
	 * The grab chain is the port's own (1.16 had no Stand grab), so its release, no 1.16 action, keeps the port's rule from
	 * before the 1.16 hold rules (endsOtherHoldOnPress false, as the manual control key): pressed during a held grab throw,
	 * charging or held ready to throw, it is not refused and drops the mob, and the throw ends without a throw on its next
	 * held recheck; pressed during a held hold-type action (Star Platinum's zoom), it drops the mob and the zoom stays held.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theGrabReleaseDropsTheMobDuringAHeldGrabThrowAndEndsNoOtherHold(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "GrabReleaseStarPlatinum", "star_platinum", 4)) {
			s.unlock("grab_throw", "grab_release", "enhanced_eyesight");
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "setup: could not create the cow");
			LivingComponentGrab standGrab = s.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			try {
				cow.setNoAi(true);
				cow.setNoGravity(true);
				cow.moveTo(s.origin.x, s.origin.y, s.origin.z + 1.5D, 180.0F, 0.0F);
				helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: could not add the cow");
				for (ActionPhase heldIn : new ActionPhase[] { ActionPhase.BUTTON_CHARGE, ActionPhase.WINDUP }) {
					standGrab.setGrabTarget(cow);
					helper.assertTrue(standGrab.getGrabbedEntity() == cow, "setup: the Stand did not grab the cow");
					EntityActionInstance throwing = s.hold("grab_throw");
					for (int tick = 0; tick < 40 && throwing.getPhase() != heldIn; tick++) {
						s.standAction.tick();
					}
					helper.assertTrue(throwing.getPhase() == heldIn && s.standAction.getAction() == throwing,
							"setup: the held grab throw is in " + throwing.getPhase() + ", not " + heldIn);
					AbilityConditionCheck release = s.resolve("grab_release");
					BufferingState pressed = s.press("grab_release", InputMethod.CLICK, OTHER_KEY);
					AbilityInput.keyRelease(OTHER_KEY, s.user);
					helper.assertTrue(release != null && release.conditionCheck.isPositive() && pressed.isActionSuccess
							&& standGrab.getGrabbedEntity() == null && s.standAction.getAction() == throwing,
							"the port's grab release, no 1.16 action, goes off during the held grab throw (" + heldIn + ") and"
									+ " drops the mob: its key " + (release != null ? release.conditionCheck : null) + ", went off "
									+ pressed.isActionSuccess + ", the Stand holds " + standGrab.getGrabbedEntity());
					s.standAction.tick();
					KnockbackCollisionImpact impact = KnockbackCollisionImpact.getHandler(cow);
					helper.assertTrue(throwing.isOver() && s.standAction.getAction() == null && (impact == null || !impact.isActive()),
							"the grab throw (" + heldIn + ") whose mob was let go ends without a throw: the throw in "
									+ throwing.getPhase() + ", the Stand's action " + s.standAction.getAction() + ", the cow thrown "
									+ (impact != null && impact.isActive()));
					s.clear();
				}

				standGrab.setGrabTarget(cow);
				EntityActionInstance zoom = s.hold("enhanced_eyesight");
				BufferingState pressed = s.press("grab_release", InputMethod.CLICK, OTHER_KEY);
				AbilityInput.keyRelease(OTHER_KEY, s.user);
				helper.assertTrue(pressed.isActionSuccess && standGrab.getGrabbedEntity() == null,
						"setup: the grab release during the zoom did not go off");
				helper.assertTrue(s.standAction.getAction() == zoom && zoom.getPhase() == ActionPhase.PERFORM && s.isHeldByKey(HOLD_KEY),
						"the port's grab release, no 1.16 action, ends no held action (as the manual control key): the zoom in "
								+ zoom.getPhase() + ", the Stand's action " + s.standAction.getAction() + ", its key holds it "
								+ s.isHeldByKey(HOLD_KEY));
				s.clear();
			}
			finally {
				standGrab.setGrabTarget(null);
				cow.discard();
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16 cleared the power's held action on both sides as its key went up (InputHandler.stopHeldAction, PowerBaseImpl
	 * .stopHeldAction). A port charge let go before it is full (the charged heavy punch, the grab throw) charges on and fires
	 * at its end, but is held no more: on the server, whose key is gone, and on the user's client, which goes by the action
	 * it was sent: the copy decoded as the client decodes it, with the synced data the server sends it, no longer counts as
	 * held, so the client lets through the plain abilities the server already takes (Magician's Red's detector).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPortChargeLetGoDuringItsChargeIsHeldNoMoreOnTheUsersClientAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "LetGoChargeMagiciansRed", "magicians_red", 4)) {
			s.unlock("heavy_charged", "mr_detector");
			s.letGoChargeIsHeldNoMore("heavy_charged", "mr_detector");
		}
		try (Scene s = new Scene(helper, "LetGoChargeStarPlatinum", "star_platinum", 4)) {
			s.unlock("grab_throw");
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "setup: could not create the cow");
			LivingComponentGrab standGrab = s.stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			try {
				cow.setNoAi(true);
				cow.setNoGravity(true);
				cow.moveTo(s.origin.x, s.origin.y, s.origin.z + 1.5D, 180.0F, 0.0F);
				helper.assertTrue(helper.getLevel().addFreshEntity(cow), "setup: could not add the cow");
				standGrab.setGrabTarget(cow);
				s.letGoChargeIsHeldNoMore("grab_throw", null);
			}
			finally {
				standGrab.setGrabTarget(null);
				cow.discard();
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16 cleared the held action on both sides as its key went up, stun or not. A port charge let go in its charge is held
	 * no more on the user's client by the synced data its server sends: while its Stand is stunned (its action not ticked)
	 * or summon-locked, the Stand's tick still sends that data, so the client does not keep the let-go charge held for the
	 * whole stun. What the user's connection receives is given to the client's copy.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPortChargeLetGoWhileItsStandWaitsIsHeldNoMoreOnTheUsersClientAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "LetGoWaitingChargeMagiciansRed", "magicians_red", 4, true)) {
			s.unlock("heavy_charged");
			for (boolean stunned : new boolean[] { true, false }) {
				s.letGoChargeIsSentWhileTheStandWaits("heavy_charged", stunned);
			}
		}
		helper.succeed();
	}

	/*
	 * A held charge takes its press's cooldown as it fires and none if it ends unfired (1.16 PowerBaseImpl.performAction for
	 * a hold-to-fire action): a port charge let go before it is full, held no more, still takes it only as it fires at its
	 * end, not at the release, and none if its release check stops it there. The charged heavy punch is given a cooldown
	 * here (it has none) and has it taken back after.
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aPortChargeLetGoDuringItsChargeTakesItsCooldownOnlyAsItFires(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "LetGoChargeCooldown", "star_platinum", 4)) {
			s.unlock("heavy_charged");
			Ability charge = s.power.getAbility("heavy_charged");
			Object[] noCooldown = cooldownOf(charge);
			try {
				charge.cooldown(0, 40, 1.0F);
				for (boolean fires : new boolean[] { true, false }) {
					s.power.setAbilityCooldown("heavy_charged", 0, 0);
					EntityActionInstance held = s.hold("heavy_charged");
					AbilityInput.keyRelease(HOLD_KEY, s.user);
					s.standAction.tick();
					helper.assertTrue(held.getPhase() == ActionPhase.BUTTON_CHARGE
							&& s.power.getAbilityCooldown("heavy_charged") == 0, "the charged heavy punch let go in its charge"
									+ " takes no cooldown before it fires: phase " + held.getPhase() + ", cooldown "
									+ s.power.getAbilityCooldown("heavy_charged"));
					if (!fires) {
						// its release check at the charge's end fails ("not_unlocked")
						s.power.getCurTypeData()._lockedAbilities.add("heavy_charged");
					}
					for (int tick = 0; tick < 60 && held.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
						s.standAction.tick();
					}
					int cooldown = s.power.getAbilityCooldown("heavy_charged");
					helper.assertTrue(fires ? held.getPhase() == ActionPhase.PERFORM && cooldown > 0 : held.isOver() && cooldown == 0,
							"the charged heavy punch let go in its charge takes its cooldown as it fires at its end, and none if"
									+ " its release check stops it there (fires " + fires + "): phase " + held.getPhase()
									+ ", cooldown " + cooldown);
					s.power.getCurTypeData()._lockedAbilities.remove("heavy_charged");
					s.clear();
				}
			}
			finally {
				restoreCooldown(charge, noCooldown);
			}
		}
		helper.succeed();
	}

	/*
	 * 1.16's HUD drew each slot from PowerBaseImpl.checkRequirements, which refused every other action of the power while
	 * its held action let none through (heldAllowsOtherAction false: a charge, a hold that was no holdType Stand action), so
	 * their slots were drawn unavailable until the release. The port holds the Stand-performed presses back in its buffer
	 * (N2) instead of refusing them, and draws their slots as 1.16 did (Ability#isHeldBackByHeldAction, the HUD's
	 * drawnCondition); a hold-type hold, which let them through, leaves every slot as it is, and so does a press that was no
	 * 1.16 action (manual control) or that the held action gives way to (a punch cutting the port's charged heavy punch).
	 */
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theSlotsAHeldChargeHoldsBackAreDrawnUnavailableAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldBackSlotsMagiciansRed", "magicians_red", 4)) {
			s.unlock("crossfire_hurricane", "red_bind", "flame_burst", "mr_detector");
			EntityActionInstance charge = s.hold("crossfire_hurricane");
			helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: Crossfire Hurricane is not charging: "
					+ charge.getPhase());
			s.drawnUnavailable("Crossfire Hurricane's charge", true, "punch", "heavy_punch", "red_bind", "flame_burst",
					"mr_detector");
			s.drawnUnavailable("Crossfire Hurricane's charge", false, "manual_control");
			helper.assertFalse(s.power.getAbility("crossfire_hurricane").isHeldBackByHeldAction(s.user),
					"1.16 refused only the other actions of the power, not the held one, while it was charging");
			BufferingState punch = s.press("punch", InputMethod.CLICK, OTHER_KEY);
			helper.assertTrue(punch.shouldBuffer && !punch.isActionSuccess && s.standAction.getAction() == charge,
					"the punch drawn unavailable during the charge still waits in the input buffer (N2): buffered "
							+ punch.shouldBuffer + ", started " + punch.isActionSuccess);
			s.clear();

			EntityActionInstance redBind = s.hold("red_bind");
			s.drawnUnavailable("the held Red Bind (hold-type)", false, "punch", "heavy_punch", "flame_burst", "mr_detector",
					"crossfire_hurricane");
			helper.assertTrue(s.standAction.getAction() == redBind, "setup: the Red Bind is no longer held");
			s.clear();
		}
		try (Scene s = new Scene(helper, "HeldBackSlotsStarPlatinum", "star_platinum", 4)) {
			s.unlock("heavy_charged");
			EntityActionInstance charged = s.hold("heavy_charged");
			helper.assertTrue(charged.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: the charged heavy punch is not charging: "
					+ charged.getPhase());
			s.drawnUnavailable("the charged heavy punch's charge", true, "barrage");
			s.drawnUnavailable("the charged heavy punch's charge", false, "punch", "heavy_punch");
			s.clear();

			s.hold("guard");
			s.drawnUnavailable("the held guard (hold-type)", false, "punch", "heavy_punch", "barrage");
			s.clear();
		}
		helper.succeed();
	}

	private static final String[] COOLDOWN_FIELDS = { "cooldownTicks", "cooldownTechnicalTicks", "cooldownAdditionalTicks",
			"cooldownResolveMultiplier", "cooldownResolveMultiplierApplies" };

	private static Object[] cooldownOf(Ability ability) {
		try {
			Object[] values = new Object[COOLDOWN_FIELDS.length];
			for (int i = 0; i < COOLDOWN_FIELDS.length; i++) {
				Field field = Ability.class.getDeclaredField(COOLDOWN_FIELDS[i]);
				field.setAccessible(true);
				values[i] = field.get(ability);
			}
			return values;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: cannot read the ability's cooldown", e);
		}
	}

	private static void restoreCooldown(Ability ability, Object[] values) {
		try {
			for (int i = 0; i < COOLDOWN_FIELDS.length; i++) {
				Field field = Ability.class.getDeclaredField(COOLDOWN_FIELDS[i]);
				field.setAccessible(true);
				field.set(ability, values[i]);
			}
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("cannot put the ability's cooldown back", e);
		}
	}

	// the action as the user's client holds it: decoded from the action the server sends it (EntityActionInstance.encode)
	private static EntityActionInstance clientCopy(GameTestHelper helper, EntityActionInstance action, StandEntity stand) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, action);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy != null && copy.getClass() == action.getClass(), "setup: the action decodes as " + copy);
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(copy, stand);
			return copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("setup: cannot give the client's copy its Stand", e);
		}
		finally {
			buf.release();
		}
	}

	// the action's synced data the server sends the clients tracking the Stand on its next tick (tickSyncDirtyData)
	private static void receiveSyncedData(EntityActionInstance client, EntityActionInstance server) {
		if (server.synchedData.getDataSyncher() == null) {
			return;
		}
		List<SynchedEntityData.DataValue<?>> sent = server.synchedData.getDataSyncher().syncDirtyData();
		if (sent != null) {
			client.synchedData.getDataSyncher().assignValues(sent);
		}
	}

	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final StandType standType;
		private final Vec3 origin;
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;
		@Nullable private RecordingConnection recording;

		private Scene(GameTestHelper helper, String name, String standId, int resolveLevel) {
			this(helper, name, standId, resolveLevel, false);
		}

		// recordsPackets: the user's connection keeps what the server sends it, the Stand's tracking packets included
		private Scene(GameTestHelper helper, String name, String standId, int resolveLevel, boolean recordsPackets) {
			this.helper = helper;
			user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			try {
				helper.assertTrue(standType != null, "Missing Stand type " + standId);
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				user.getAbilities().instabuild = false;
				if (recordsPackets) {
					recording = new RecordingConnection(user);
					user.connection = recording;
				}
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				if (recording != null) {
					// chunks a fake player never receives: its Stand is tracked by it only in chunks it holds
					ChunkPos at = user.chunkPosition();
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							recording.chunkSender.dropChunk(user, new ChunkPos(at.x + dx, at.z + dz));
						}
					}
				}
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
				power.setResolveLevel(resolveLevel);
				helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned " + standId + " is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
				stand.summonLockTicks = 0;
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// as a player who can press them has them; the scene does not run the user's tick
		private void unlock(String... names) {
			for (String name : names) {
				power.getCurTypeData()._lockedAbilities.remove(name);
				helper.assertTrue(power.getAbility(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
			}
		}

		/*
		 * The hold is pressed and kept held; the other action pressed by another key goes off at once and replaces it,
		 * neither buffered nor waiting for the hold's key.
		 */
		private void goesThrough(String holdName, String otherName, InputMethod otherInput) {
			EntityActionInstance hold = hold(holdName);
			BufferingState pressed = press(otherName, otherInput, OTHER_KEY);
			EntityActionInstance now = standAction.getAction();
			helper.assertTrue(pressed.isActionSuccess && !pressed.shouldBuffer && now != null && now != hold
					&& now.ability == power.getAbility(otherName) && hold.isOver(),
					"1.16 let " + otherName + " pressed during the hold-type " + holdName + " through at once"
							+ " (heldAllowsOtherAction, isCancelable): started " + pressed.isActionSuccess + ", buffered "
							+ pressed.shouldBuffer + ", the Stand's action " + now + ", the " + holdName + " over "
							+ hold.isOver() + " in " + hold.getPhase());
			AbilityInput.keyRelease(OTHER_KEY, user);
			clear();
		}

		/*
		 * The hold, whose canBeCancelledInto would let the other action replace it, is pressed and kept held; the other
		 * action, which holds too, pressed by another key waits in the buffer and the hold goes on.
		 */
		private void heldPressWaits(String holdName, String otherName) {
			EntityActionInstance hold = hold(holdName);
			helper.assertTrue(hold.canBeCancelledInto((EntityActionType) power.getAbility(otherName))
					&& ((EntityActionAbility) power.getAbility(otherName)).isHoldAction(), "setup: the held " + holdName
							+ " would not let " + otherName + " replace it, or " + otherName + " does not hold");
			BufferingState pressed = press(otherName, InputMethod.HOLD, OTHER_KEY);
			helper.assertTrue(pressed.shouldBuffer && !pressed.isActionSuccess && standAction.getAction() == hold
					&& hold.getPhase() == ActionPhase.PERFORM && isHeldByKey(HOLD_KEY),
					"1.16's client sent no " + otherName + " (a held action) pressed while " + holdName + " was held, which the"
							+ " input buffer keeps: buffered " + pressed.shouldBuffer + ", started " + pressed.isActionSuccess
							+ ", the Stand's action " + standAction.getAction() + ", the " + holdName + " in " + hold.getPhase());
			AbilityInput.keyRelease(OTHER_KEY, user);
			clear();
		}

		/*
		 * The hold is pressed and kept held, then the time stop by another key: it waits in the buffer and the hold goes on
		 * with its key. Once the hold is let go, the buffer, tried every tick, starts the time stop's charge (in its user's
		 * Stand-power slot, 1.16's power held it) as soon as the hold (and its recovery) no longer holds it back, its key still
		 * down. The charge is dropped without a release.
		 */
		private void timeStopWaits(String holdName) {
			EntityActionInstance hold = hold(holdName);
			BufferingState pressed = press("time_stop", InputMethod.HOLD, OTHER_KEY);
			helper.assertTrue(pressed.shouldBuffer && !pressed.isActionSuccess && standAction.getAction() == hold
					&& hold.getPhase() == ActionPhase.PERFORM && isHeldByKey(HOLD_KEY),
					"1.16's client sent no time stop (hold-to-fire) pressed while " + holdName + " was held, so the hold went on:"
							+ " buffered " + pressed.shouldBuffer + ", started " + pressed.isActionSuccess + ", the Stand's action "
							+ standAction.getAction() + ", the " + holdName + " in " + hold.getPhase());
			AbilityInput.keyRelease(HOLD_KEY, user);
			EntityActionInstance timeStop = null;
			for (int tick = 0; tick < 100 && timeStop == null; tick++) {
				resolve("time_stop");
				input().inputBuffer.tickInputBuffer(input());
				EntityActionInstance now = slotOf("time_stop").getAction();
				if (now != null && now.ability == power.getAbility("time_stop")) {
					timeStop = now;
				}
				else {
					standAction.tick();
				}
			}
			helper.assertTrue(timeStop != null && timeStop.getPhase() == ActionPhase.BUTTON_CHARGE && hold.isOver()
					&& isHeldByKey(OTHER_KEY) && input().heldKeys.get(OTHER_KEY).action == timeStop,
					"the time stop key still held once the " + holdName + " was let go starts its charge from the buffer: the"
							+ " user's Stand-power action " + slotOf("time_stop").getAction() + ", the Stand's "
							+ standAction.getAction() + ", the " + holdName + " over " + hold.isOver());
			clear();
		}

		/*
		 * The charge is pressed and kept held, then let go in its charge: it charges on (and fires at its end), held no more by
		 * the server and by the user's client's copy of it, which takes what the server sends it; the plain ability, if any,
		 * refused during the held charge, is taken by the server once it is let go.
		 */
		private void letGoChargeIsHeldNoMore(String chargeName, @Nullable String plainName) {
			EntityActionAbility charge = (EntityActionAbility) power.getAbility(chargeName);
			EntityActionInstance held = hold(chargeName);
			helper.assertTrue(held.getPhase() == ActionPhase.BUTTON_CHARGE, "setup: the held " + chargeName + " is in "
					+ held.getPhase());
			EntityActionInstance client = clientCopy(helper, held, stand);
			receiveSyncedData(client, held);
			helper.assertTrue(charge.isHeldSincePress(client) && AbilityInput.getHeldAction(user, PowerClass.STAND) == held,
					"setup: the " + chargeName + " held by its key is not held on both sides: the client's copy "
							+ charge.isHeldSincePress(client) + ", the server's held action " + AbilityInput.getHeldAction(user, PowerClass.STAND));
			if (plainName != null) {
				AbilityConditionCheck during = resolve(plainName);
				helper.assertTrue(during != null && during.conditionCheck == ConditionCheck.NEGATIVE, "setup: " + plainName
						+ " is not refused during the held " + chargeName);
			}
			AbilityInput.keyRelease(HOLD_KEY, user);
			receiveSyncedData(client, held);
			helper.assertTrue(held.getPhase() == ActionPhase.BUTTON_CHARGE && standAction.getAction() == held,
					"setup: the " + chargeName + " let go in its charge does not charge on: " + held.getPhase());
			helper.assertTrue(!charge.isHeldSincePress(client) && !charge.isHeldSincePress(held)
					&& AbilityInput.getHeldAction(user, PowerClass.STAND) == null,
					"1.16 cleared the held action on both sides as its key went up: the user's client's copy of the " + chargeName
							+ " let go in its charge, with what the server sends it, counts as held " + charge.isHeldSincePress(client)
							+ ", the server's " + charge.isHeldSincePress(held) + ", the server's held action "
							+ AbilityInput.getHeldAction(user, PowerClass.STAND));
			if (plainName != null) {
				AbilityConditionCheck after = resolve(plainName);
				helper.assertTrue(after != null && after.conditionCheck.isPositive(), "the server takes " + plainName
						+ " once the " + chargeName + " is let go: " + (after != null ? after.conditionCheck : null));
			}
			for (int tick = 0; tick < 60 && held.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
				standAction.tick();
			}
			helper.assertTrue(held.getPhase() == ActionPhase.PERFORM, "the " + chargeName + " let go in its charge fires at its"
					+ " end: " + held.getPhase());
			clear();
		}

		/*
		 * The charge is pressed and kept held, then let go in its charge while its Stand is stunned or summon-locked: the
		 * release sets the flag the user's client goes by, and the Stand's tick, which does not tick the action then, still
		 * sends it; the user's client's copy, given what the user's connection received, no longer counts the charge held.
		 */
		private void letGoChargeIsSentWhileTheStandWaits(String chargeName, boolean stunned) {
			EntityActionAbility charge = (EntityActionAbility) power.getAbility(chargeName);
			EntityActionInstance held = hold(chargeName);
			EntityActionInstance client = clientCopy(helper, held, stand);
			receiveSyncedData(client, held);
			String waits = stunned ? "stunned" : "summon-locked";
			helper.assertTrue(held.getPhase() == ActionPhase.BUTTON_CHARGE && charge.isHeldSincePress(client)
					&& held instanceof StandEntityChargedHeavy && recording != null,
					"setup: the held " + chargeName + " is not held on its user's client: " + held.getPhase());
			if (stunned) {
				stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			}
			else {
				stand.summonLockTicks = 20;
			}
			try {
				AbilityInput.keyRelease(HOLD_KEY, user);
				boolean flagSetUnsent = ((StandEntityChargedHeavy) held).isReleasedInCharge()
						&& held.synchedData.getDataSyncher().isDirty();
				recording.packets.clear();
				standAction.tick();
				List<SynchedEntityData.DataValue<?>> sent = recording.actionDataSentFor(stand);
				if (sent != null) {
					client.synchedData.getDataSyncher().assignValues(sent);
				}
				List<SynchedEntityData.DataValue<?>> unsent = held.synchedData.getDataSyncher().syncDirtyData();
				helper.assertTrue(flagSetUnsent && held.getPhase() == ActionPhase.BUTTON_CHARGE && standAction.getAction() == held
						&& sent != null && unsent == null && !charge.isHeldSincePress(client),
						"1.16 cleared the held action on both sides as its key went up: the " + chargeName + " let go in its charge"
								+ " while its Stand is " + waits + " (" + held.getPhase() + "): the release set its flag, unsent, "
								+ flagSetUnsent + "; the Stand's tick sent its user " + sent + " and left unsent " + unsent
								+ "; the user's client's copy, given what it received, counts it held " + charge.isHeldSincePress(client));
			}
			finally {
				if (stunned) {
					stand.removeEffect(ModStatusEffects.STUN);
				}
				else {
					stand.summonLockTicks = 0;
				}
				clear();
			}
		}

		/*
		 * Each key, resolved as the HUD resolves it, is drawn unavailable (its check refused, or held back by the held action
		 * as the HUD's drawnCondition asks) or as its check alone draws it, while the given hold is held.
		 */
		private void drawnUnavailable(String during, boolean unavailable, String... names) {
			for (String name : names) {
				AbilityConditionCheck key = resolve(name);
				helper.assertTrue(key != null, "setup: no key gives " + name);
				boolean heldBack = key.ability.isHeldBackByHeldAction(user);
				boolean drawnUnavailable = !key.conditionCheck.isPositive() || heldBack;
				helper.assertTrue(unavailable ? heldBack : !drawnUnavailable, (unavailable
								? "1.16 drew " + name + " unavailable during " + during + " (checkRequirements refused every other"
										+ " action while the held one let none through)"
								: "1.16 drew " + name + " as its own check drew it during " + during)
								+ ": held back " + heldBack + ", its check " + (key.conditionCheck.isPositive() ? "usable" : "refused"));
			}
		}

		// the other ability, pressed by another key during the held charge, is refused without a message and not buffered
		private void refusedDuring(EntityActionInstance charge, String otherName) {
			AbilityConditionCheck key = resolve(otherName);
			BufferingState pressed = press(otherName, InputMethod.CLICK, OTHER_KEY);
			AbilityInput.keyRelease(OTHER_KEY, user);
			helper.assertTrue(key != null && key.conditionCheck == ConditionCheck.NEGATIVE && !pressed.isActionSuccess
					&& !pressed.shouldBuffer && standAction.getAction() == charge && charge.getPhase() == ActionPhase.BUTTON_CHARGE
					&& isHeldByKey(HOLD_KEY), "1.16 refused " + otherName + ", silently, while the power held a charge"
							+ " (heldAllowsOtherAction false): its key " + (key != null ? key.conditionCheck : null) + ", went off "
							+ pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer + ", the charge in " + charge.getPhase());
		}

		/*
		 * Crossfire Hurricane held, then past its charge as if it had fired (its key still down), in the given phase and
		 * tick, with the given windup and recovery and a perform of 10; a punch pressed by another key replaces it at once
		 * or waits in the buffer.
		 */
		private void punchAfterFire(ActionPhase phase, int tick, float windup, float recovery, boolean goesThrough) {
			power.setAbilityCooldown("crossfire_hurricane", 0, 0);
			EntityActionInstance charge = hold("crossfire_hurricane");
			charge.phasesLength.put(ActionPhase.WINDUP, windup);
			charge.phasesLength.put(ActionPhase.PERFORM, 10F);
			charge.phasesLength.put(ActionPhase.RECOVERY, recovery);
			charge.setPhase(phase, tick);
			helper.assertTrue(charge.getPhase() == phase && standAction.getAction() == charge, "setup: the fired Crossfire"
					+ " Hurricane is in " + charge.getPhase());
			BufferingState pressed = press("punch", InputMethod.CLICK, OTHER_KEY);
			EntityActionInstance now = standAction.getAction();
			boolean replaced = pressed.isActionSuccess && !pressed.shouldBuffer && now != charge && charge.isOver()
					&& now != null && now.ability == power.getAbility("punch");
			boolean waits = pressed.shouldBuffer && !pressed.isActionSuccess && now == charge && !charge.isOver();
			helper.assertTrue(goesThrough ? replaced : waits, (goesThrough
					? "1.16 let a punch replace a hold-to-fire Stand action that had fired and had no recovery, at once (isCancelable)"
					: "a punch pressed during the fired Crossfire Hurricane waits")
					+ ": phase " + phase + " tick " + tick + ", windup " + windup + ", recovery " + recovery + ", started "
					+ pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer + ", the Stand's action " + now);
			AbilityInput.keyRelease(OTHER_KEY, user);
			clear();
		}

		/*
		 * Crossfire Hurricane with the given windup, held to its full charge and ticked as the Stand ticks it, its key still
		 * down, to the start of its perform; a punch pressed then by another key replaces it before its shot, or waits while
		 * the shot goes off on the next tick.
		 */
		// the tick from the press (the press's own tick the first) in which the held charge's shot is out
		private int crossfireShotTick() {
			power.setAbilityCooldown("crossfire_hurricane", 0, 0);
			EntityActionInstance charge = hold("crossfire_hurricane");
			charge.phasesLength.put(ActionPhase.WINDUP, 0F);
			int shotTick = -1;
			for (int tick = 1; tick <= 60 && shotTick < 0; tick++) {
				standAction.tick();
				if (!shots().isEmpty()) {
					shotTick = tick;
				}
			}
			shots().forEach(shot -> shot.discard());
			clear();
			return shotTick;
		}

		// the tick from the press (the press's own tick the first) in which the charge, set on its user and held, performs
		private int userPerformedChargeTick(String name) {
			EntityActionAbility ability = (EntityActionAbility) power.getAbility(name);
			EntityActionInstance charge = ability.initActionOnAbilityUse(helper.getLevel(), user, user, null);
			LivingComponentAction userAction = LivingComponentAction.getComponent(user);
			userAction.setAction(charge, user, SyncType.NO_SYNC);
			input().heldKeys.put(HOLD_KEY, new HeldInputEntry(HOLD_KEY, 1L, PowerClass.STAND, charge));
			try {
				helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && charge.getPerformer() == user,
						"setup: the " + name + " set on its user is not charging: " + charge.getPhase());
				int performTick = -1;
				for (int tick = 1; tick <= 60 && performTick < 0; tick++) {
					userAction.tick();
					// its perform's first tick ends it: no Stand shoots
					if (charge.isOver() || charge.getPhase() == ActionPhase.PERFORM && charge.getPhaseTick() >= 1) {
						performTick = tick;
					}
				}
				return performTick;
			}
			finally {
				input().heldKeys.clear();
				userAction.setAction(null, user, SyncType.NO_SYNC);
			}
		}

		// the tick from the press (the press's own tick the first) in which the held time stop's stop is in place
		private int timeStopStartTick() {
			ChunkPos farAway = new ChunkPos(50600, 50600);
			Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
				if (event.getInstance().userId() == user.getId()) {
					event.setInstance(event.getInstance().withArea(farAway, 1));
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolate);
			try {
				EntityActionInstance charge = hold("time_stop");
				LivingComponentAction userSlot = LivingComponentAction.getComponent(user, PowerClass.STAND);
				helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && charge.getCurPhaseLength() == 30
						&& charge.getPerformer() == stand && userSlot.getAction() == charge, "setup: The World's 30-tick time"
								+ " stop, The World out, is not charging in its user's Stand-power slot: " + charge.getPhase() + " "
								+ charge.getCurPhaseLength() + ", performer " + charge.getPerformer());
				int stopTick = -1;
				for (int tick = 1; tick <= 60 && stopTick < 0; tick++) {
					userSlot.tick();
					if (helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId()).isPresent()) {
						stopTick = tick;
					}
				}
				return stopTick;
			}
			finally {
				NeoForge.EVENT_BUS.unregister(isolate);
				helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
				user.removeEffect(ModStatusEffects.TIME_STOP);
				clear();
			}
		}

		private void punchAtPerformStart(float windup, boolean goesThrough) {
			power.setAbilityCooldown("crossfire_hurricane", 0, 0);
			EntityActionInstance charge = hold("crossfire_hurricane");
			charge.phasesLength.put(ActionPhase.WINDUP, windup);
			List<ActionPhase> phases = new ArrayList<>();
			for (int tick = 0; tick < 60 && charge.getPhase() != ActionPhase.PERFORM && !charge.isOver(); tick++) {
				standAction.tick();
				if (phases.isEmpty() || phases.get(phases.size() - 1) != charge.getPhase()) {
					phases.add(charge.getPhase());
				}
			}
			helper.assertTrue(charge.getPhase() == ActionPhase.PERFORM && charge.getPhaseTick() < 1
					&& standAction.getAction() == charge && shots().isEmpty()
					&& phases.contains(ActionPhase.WINDUP) == windup > 0, "setup: Crossfire Hurricane with a windup of " + windup
							+ ", held to its full charge, is not at its perform's start before its shot: phases " + phases + ", "
							+ charge.getPhase() + " tick " + charge.getPhaseTick() + ", shots " + shots().size());
			BufferingState pressed = press("punch", InputMethod.CLICK, OTHER_KEY);
			EntityActionInstance now = standAction.getAction();
			boolean replaced = pressed.isActionSuccess && !pressed.shouldBuffer && now != charge && charge.isOver()
					&& now != null && now.ability == power.getAbility("punch");
			boolean waits = pressed.shouldBuffer && !pressed.isActionSuccess && now == charge && !charge.isOver();
			standAction.tick();
			int shots = shots().size();
			helper.assertTrue(goesThrough ? replaced && shots == 0 : waits && shots == 1, (goesThrough
					? "1.16 performed a fired task's windup and then, a tick later, its perform, so a press at the perform's"
							+ " start cancelled it before the shot (isCancelable)"
					: "the port keeps the shot of a perform the charge fired straight into, so a press at its start waits"
							+ " and the shot follows on the next tick")
					+ ": windup " + windup + ", started " + pressed.isActionSuccess + ", buffered " + pressed.shouldBuffer
					+ ", the Stand's action " + now + ", shots after the next tick " + shots);
			AbilityInput.keyRelease(OTHER_KEY, user);
			shots().forEach(shot -> shot.discard());
			clear();
		}

		/*
		 * The charge, whose windup is its held phase, held past its charge and let go: it fires straight into its perform,
		 * which (given no recovery here) gives way to other actions only after its first tick, as one fired without a windup.
		 */
		private void heldWindupFiresStraight(String chargeName) {
			EntityActionInstance charge = hold(chargeName);
			charge.phasesLength.put(ActionPhase.RECOVERY, 0F);
			for (int tick = 0; tick < 60 && charge.getPhase() == ActionPhase.BUTTON_CHARGE; tick++) {
				standAction.tick();
			}
			EntityActionAbility ability = (EntityActionAbility) power.getAbility(chargeName);
			helper.assertTrue(charge.getPhase() == ActionPhase.WINDUP && ability.isHeldSincePress(charge),
					"setup: the " + chargeName + " held past its charge is not in its held windup: " + charge.getPhase());
			AbilityInput.keyRelease(HOLD_KEY, user);
			helper.assertTrue(charge.getPhase() == ActionPhase.PERFORM && charge.getPhaseTick() < 1
					&& !ability.isHeldSincePress(charge), "setup: the " + chargeName + " let go in its held windup is not at its"
							+ " perform's start: " + charge.getPhase() + " tick " + charge.getPhaseTick());
			boolean atStart = charge.givesWayToOtherAbilities();
			standAction.tick();
			boolean afterFirstTick = charge.getPhase() == ActionPhase.PERFORM && charge.givesWayToOtherAbilities();
			helper.assertTrue(!atStart && afterFirstTick, "a windup the action holds is its charge, not a windup between the"
					+ " charge and the perform: the " + chargeName + " let go in it fired straight into its perform, which gives"
					+ " way at its start " + atStart + ", after its first tick " + afterFirstTick + " (" + charge.getPhase()
					+ " tick " + charge.getPhaseTick() + ")");
			clear();
		}

		private List<MRCrossfireHurricaneEntity> shots() {
			return new ArrayList<>(helper.getLevel().getEntitiesOfClass(MRCrossfireHurricaneEntity.class,
					user.getBoundingBox().inflate(16.0D)));
		}

		// pressed as a held key and kept held
		private EntityActionInstance hold(String name) {
			power.setStamina(power.getMaxStamina());
			BufferingState state = press(name, InputMethod.HOLD, HOLD_KEY);
			EntityActionInstance action = slotOf(name).getAction();
			helper.assertTrue(state.isActionSuccess && action != null && action.ability == power.getAbility(name)
					&& isHeldByKey(HOLD_KEY), "setup: " + name + " is not held: started " + state.isActionSuccess
							+ ", its slot's action " + action);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM || action.getPhase() == ActionPhase.BUTTON_CHARGE,
					"setup: the held " + name + " is in " + action.getPhase());
			return action;
		}

		// set on the Stand as a press sets it, without a key
		private EntityActionInstance start(String name) {
			Ability ability = power.getAbility(name);
			EntityActionInstance action = ((EntityActionAbility) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
			standAction.setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(standAction.getAction() == action, "setup: " + name + " is not the Stand's action");
			return action;
		}

		// as ClAbilityInputPacket handles a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name, InputMethod inputMethod, short key) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(key, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			return state;
		}

		private boolean isHeldByKey(short key) {
			HeldInput held = input().heldKeys.containsKey(key) ? input().heldKeys.get(key).action : null;
			return held != null;
		}

		private EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		private List<MRDetectorEntity> detectors() {
			return new ArrayList<>(helper.getLevel().getEntitiesOfClass(MRDetectorEntity.class,
					user.getBoundingBox().inflate(8.0D)));
		}

		// a press left buffered stays there: the test does not tick, and the next press decides by the Stand's action
		private void clear() {
			input().heldKeys.clear();
			standAction.setAction(null, user, SyncType.NO_SYNC);
			LivingComponentAction.getComponent(user, PowerClass.STAND).setAction(null, user, SyncType.NO_SYNC);
		}

		// a port of a 1.16 StandAction (the time stop) runs in its user's Stand-power slot, the Stand's tasks in its own
		private LivingComponentAction slotOf(String name) {
			return ((EntityActionAbility) power.getAbility(name)).performsInUsersStandPowerSlot()
					? LivingComponentAction.getComponent(user, PowerClass.STAND) : standAction;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// a fake player's connection that keeps what the server sends it
	private static final class RecordingConnection extends ServerGamePacketListenerImpl {
		final List<Packet<?>> packets = new ArrayList<>();

		RecordingConnection(FakePlayer player) {
			super(player.getServer(), player.connection.getConnection(), player,
					CommonListenerCookie.createInitial(player.getGameProfile(), false));
		}

		// the synced data of the performer's action it received (TrActionSynchedDataPacket), null for none
		@Nullable
		List<SynchedEntityData.DataValue<?>> actionDataSentFor(StandEntity performer) {
			List<SynchedEntityData.DataValue<?>> values = null;
			for (Packet<?> packet : packets) {
				if (packet instanceof ClientboundCustomPayloadPacket custom
						&& custom.payload() instanceof TrActionSynchedDataPacket data && data.entityId() == performer.getId()) {
					if (values == null) {
						values = new ArrayList<>();
					}
					values.addAll(data.packedItems());
				}
			}
			return values;
		}

		@Override
		public void send(Packet<?> packet) {
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(this::send);
			}
			else {
				packets.add(packet);
			}
		}

		@Override
		public void send(Packet<?> packet, PacketSendListener listener) {
			send(packet);
		}
	}

}
