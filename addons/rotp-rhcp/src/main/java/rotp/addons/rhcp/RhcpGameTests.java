package rotp.addons.rhcp;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionSurface;
import rotp.core.api.client.render.AbilitySelectionVisualQuery;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityAttributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.util.functions.DamageUtil;
import com.mojang.authlib.GameProfile;

import rotp.addons.rhcp.ability.RhcpAbility;
import rotp.addons.rhcp.ability.RhcpPunchAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.init.ModStatusEffects;
import rotp.core.customobjects.DamageSourceModified;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.state.RhcpState;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import rotp.core.init.ModDataAttachmentTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(RhcpAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RhcpGameTests {
    @GameTest(template = "empty", batch = "rhcp_finisher_blocked", timeoutTicks = 80)
    public static void aShieldBlockedEntityStillAddsTheHitAttemptFinisherBonus(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-blocked", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        RhcpStandEntity stand = RhcpService.activeStand(user);
        Zombie blocker = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(5, 2, 1));
        blocker.setNoGravity(true);
        blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        blocker.startUsingItem(InteractionHand.OFF_HAND);
        helper.runAfterDelay(8, () -> {
            try {
            user.removeEffect(ModStatusEffects.RESOLVE);
            blocker.setYRot(0.0F);
            blocker.setYHeadRot(0.0F);
            blocker.setXRot(0.0F);
            stand.moveTo(blocker.getX(), blocker.getY(), blocker.getZ() + 1.5D, 180.0F, 0.0F);
            helper.assertTrue(blocker.isBlocking(), "The shield fixture is not blocking.");
            RhcpPunchAbility ability = (RhcpPunchAbility) power.getAbility("rhcp_punch");
            EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.TRACKING_AND_SELF);
            stand.setFinisherMeter(0.1F);
            float health = blocker.getHealth();
            invokeHitAttempt(action, blocker, helper.getLevel(), stand);
            helper.assertTrue(blocker.getHealth() == health, "The shield did not block the punch.");
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.2F) < 0.0001F,
                    "A shield-blocked punch lost its hit-attempt finisher contribution.");
            } finally {
            blocker.discard();
            cleanup(user, power, "RHCP blocked finisher GameTest complete");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "rhcp_finisher_attempt", timeoutTicks = 80)
    public static void anIframedEntityStillAddsTheHitAttemptFinisherBonus(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-finisher", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        RhcpStandEntity stand = RhcpService.activeStand(user);
        IronGolem target = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, new BlockPos(5, 2, 1));
        try {
            user.removeEffect(ModStatusEffects.RESOLVE);
            RhcpPunchAbility ability = (RhcpPunchAbility) power.getAbility("rhcp_punch");
            EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.TRACKING_AND_SELF);
            stand.setFinisherMeter(0.0F);
            // No entity target: the perform-start contribution is still the donor's 0.1.
            action.actionPerformStart();
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - 0.1F) < 0.0001F,
                    "A missed punch changed the perform-start finisher contribution.");
            float initialHealth = target.getHealth();
            DamageSource firstHit = action.makePunchDamageSource();
            ((DamageSourceModified) firstHit).jojo_ripples$setStandInvulTicks(20);
            helper.assertTrue(stand.hurtWithStandAttack(target, firstHit, 30.0F)
                            && target.getHealth() < initialHealth,
                    "The priming hit did not establish Stand attack immunity frames.");
            float health = target.getHealth();
            float meter = stand.getFinisherMeter();
            invokeHitAttempt(action, target, helper.getLevel(), stand);
            helper.assertTrue(target.getHealth() == health,
                    "The second punch bypassed the Stand attack immunity frames.");
            helper.assertTrue(Math.abs(stand.getFinisherMeter() - meter - 0.1F) < 0.0001F,
                    "A punch refused by immunity frames lost its hit-attempt finisher contribution.");
        } finally {
            target.discard();
            cleanup(user, power, "RHCP finisher GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 Counterattack.afterAttack: a Stand the attackAlt counter hit and
    // did not kill gets setTaskTarget(new ActionTarget(null)); its punch no
    // longer holds the cow and it looks along its user's look again.
    @GameTest(template = "empty", batch = "rhcp_counter_drop_aim", timeoutTicks = 80)
    public static void aStandTheCounterHitDropsItsTaskTargetAsIn116(GameTestHelper helper) {
        ServerPlayer spUser = mockPlayer(helper, "rhcp-drop-sp", new BlockPos(1, 2, 1));
        spUser.setGameMode(GameType.SURVIVAL);
        StandPower spPower = StandPower.get(spUser);
        helper.assertTrue(spPower != null, "The mock player has no Stand power attachment.");
        spPower.setStand(ModStands.STAR_PLATINUM.get());
        spPower.setStamina(spPower.getMaxStamina());
        helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(spUser, spPower),
                "Star Platinum could not be summoned.");
        Cow cow = standingCow(helper, spUser.position().add(1.5D, 0.0D, 0.0D));
        try {
            StandEntity starPlatinum = spPower.getSummonedStandEntity();
            helper.assertTrue(starPlatinum != null, "Setup: no Star Platinum.");
            starPlatinum.summonLockTicks = 0;
            LivingComponentAction.getComponent(spUser).entityAim.setTarget(new ActionTarget(cow));
            LivingComponentAction.getComponent(starPlatinum).entityAim.setTarget(new ActionTarget(cow));
            Ability punch = spPower.getAbility("punch");
            helper.assertTrue(punch instanceof EntityActionType, "Star Platinum has no punch.");
            EntityActionInstance action = ((EntityActionType) punch).initActionOnAbilityUse(
                    helper.getLevel(), spUser, starPlatinum, null);
            LivingComponentAction.getComponent(starPlatinum).setAction(action, spUser, SyncType.NO_SYNC);
            helper.assertTrue(starPlatinum.getCurStandAction() == action
                            && action.getTaskTarget(helper.getLevel()).getEntity() == cow,
                    "Setup: Star Platinum's punch holds " + action.getTaskTarget(helper.getLevel())
                            + ", not the cow.");
            turnIdleUser(spUser, 0.0F, 0.0F);
            invokeDropAim(starPlatinum);
            helper.assertTrue(action.getTaskTarget(helper.getLevel()).getType()
                            == ActionTarget.TargetType.EMPTY,
                    "1.16 Counterattack.afterAttack dropped the task target of the Stand it hit;"
                            + " its punch still holds " + action.getTaskTarget(helper.getLevel()) + ".");
            starPlatinum.updatePosition(spUser);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(starPlatinum.getYRot() - spUser.getYRot())) < 0.01F,
                    "1.16 turned the Stand the counter hit back along its user's look (yaw "
                            + spUser.getYRot() + "); it faces yaw " + starPlatinum.getYRot() + ".");
        } finally {
            cow.discard();
            cleanup(spUser, spPower, "RHCP counter drop aim GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPCounterattack.onTaskSet: setTaskTarget(getTargetHit), so the
    // counter's task holds the attacker it answers, not what the user aimed
    // at. RHCP left at a fixed spot turns to it (StandEntityTask.rotateStand);
    // under manual control it faces it while the counter winds up
    // (RHCPEntity.defaultRotation).
    @GameTest(template = "empty", batch = "rhcp_counter_faces_fixed",
            timeoutTicks = 100)
    public static void aCounterOfAFixedRhcpFacesTheAttackerAsIn116(
            GameTestHelper helper) {
        checkCounterFacesAttacker(helper, false);
    }

    @GameTest(template = "empty", batch = "rhcp_counter_faces_manual",
            timeoutTicks = 100)
    public static void aManualCounterFacesTheAttackerWhileWindingUpAsIn116(
            GameTestHelper helper) {
        checkCounterFacesAttacker(helper, true);
    }

    private static void checkCounterFacesAttacker(GameTestHelper helper,
            boolean manual) {
        String name = manual ? "rhcp-face-manual" : "rhcp-face-fixed";
        ServerPlayer user = mockPlayer(helper, name, new BlockPos(1, 2, 1));
        ServerPlayer attackerUser = mockPlayer(
                helper, name + "-mr", new BlockPos(4, 2, 1));
        StandPower power = summonRhcp(helper, user);
        attackerUser.setGameMode(GameType.SURVIVAL);
        StandPower attackerPower = StandPower.get(attackerUser);
        helper.assertTrue(attackerPower != null,
                "The mock attacker has no Stand power attachment.");
        attackerPower.setStand(ModStands.MAGICIANS_RED.get());
        attackerPower.setStamina(attackerPower.getMaxStamina());
        helper.assertTrue(ModStands.MAGICIANS_RED.get()
                        .summon(attackerUser, attackerPower),
                "Magician's Red could not be summoned.");
        Cow decoy = standingCow(helper, user.position().add(0.0D, 0.0D, 1.5D));
        Runnable leave = () -> {
            decoy.discard();
            cleanup(user, power, "RHCP counter facing GameTest complete");
            cleanup(attackerUser, attackerPower,
                    "RHCP counter facing GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        counterFacesAttacker(helper, user, power,
                                attackerPower, decoy, manual);
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    private static void counterFacesAttacker(GameTestHelper helper,
            ServerPlayer user, StandPower power, StandPower attackerPower,
            Cow decoy, boolean manual) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        StandEntity attacker = attackerPower.getSummonedStandEntity();
        helper.assertTrue(stand != null && attacker != null
                        && attacker.isAlive(),
                "RHCP or Magician's Red left before the counter check.");
        RhcpState state = RhcpAddon.state(user);
        state.setEnergy(2000.0F);
        state.setPotency(3);
        if (manual) {
            StandEntityManualControlToggle.on(helper.getLevel(), stand);
        }
        else {
            stand.setCanFollowUser(false);
        }
        StandManualInput.setServerShift(user, false);
        stand.moveTo(attacker.getX() - 1.5D, attacker.getY(),
                attacker.getZ(), 0.0F, 0.0F);
        LivingComponentAction.getComponent(user).entityAim
                .setTarget(new ActionTarget(decoy));
        LivingComponentAction.getComponent(stand).entityAim
                .setTarget(new ActionTarget(decoy));
        Ability dodge = power.getAbility("rhcp_dodge");
        helper.assertTrue(dodge instanceof EntityActionType
                        && dodge.checkConditions(power).isPositive(),
                "Setup: RHCP could not start its dodge.");
        EntityActionInstance dodging = ((EntityActionType) dodge)
                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
        LivingComponentAction.getComponent(stand).setAction(
                dodging, user, SyncType.TRACKING_AND_SELF);
        DamageSource hit = new DamageSource(
                DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK),
                attacker);
        helper.assertFalse(stand.hurt(hit, 4.0F),
                "Setup: RHCP took Magician's Red's hit.");
        EntityActionInstance counter = stand.getCurStandAction();
        helper.assertTrue(RhcpAbility.modeOf(counter)
                        == RhcpAbility.Mode.COUNTERATTACK
                        && counter.getPhase() == ActionPhase.WINDUP,
                "Setup: RHCP did not wind up a counterattack: "
                        + RhcpAbility.modeOf(counter));
        helper.assertTrue(counter.getTaskTarget(helper.getLevel()).getEntity()
                        == attacker,
                "1.16 RHCPCounterattack.onTaskSet gave the counter's task the"
                        + " attacker it answers; it holds "
                        + counter.getTaskTarget(helper.getLevel()) + ".");
        turnStand(stand, 180.0F);
        stand.updatePosition(user);
        assertFaces(helper, stand, attacker, (manual
                ? "1.16 RHCPEntity.defaultRotation faced the attacker while"
                        + " a manually controlled RHCP wound up its counter"
                : "1.16 StandEntityTask.rotateStand turned a RHCP left at a"
                        + " fixed spot to the attacker its counter holds"));
    }

    // 1.16 RHCPPunch.onTaskSet: the powered punch's task holds the lock
    // (getTargetHit), not what the user aimed at, and the Stand turns to it.
    @GameTest(template = "empty", batch = "rhcp_punch_lock", timeoutTicks = 80)
    public static void aPoweredPunchHoldsTheLockAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-punch-lock", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Cow decoy = standingCow(helper, user.position().add(0.0D, 0.0D, 1.5D));
        Cow lock = standingCow(helper, user.position().add(-1.5D, 0.0D, 0.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            state.setTargetId(lock.getId());
            stand.setStandCount(20);
            LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(decoy));
            LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(decoy));
            RhcpPunchAbility ability = (RhcpPunchAbility) power.getAbility("rhcp_punch");
            EntityActionInstance action = ability.initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.TRACKING_AND_SELF);
            helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == lock,
                    "1.16 RHCPPunch.onTaskSet gave the powered punch's task the lock; it holds "
                            + action.getTaskTarget(helper.getLevel()) + ".");
            turnIdleUser(user, 0.0F, 0.0F);
            stand.updatePosition(user);
            assertFaces(helper, stand, lock, "1.16 turned RHCP to the lock its punch holds");
        } finally {
            decoy.discard();
            lock.discard();
            cleanup(user, power, "RHCP punch lock GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPHeavyPunch.onTaskSet (RHCPComboPunch extends it): the task of
    // the heavy and of the combo holds the lock (getTargetHit), which stays
    // held, and a RHCP away from its user turns to it, not to the press aim.
    @GameTest(template = "empty", batch = "rhcp_heavy_lock", timeoutTicks = 80)
    public static void aHeavyAndComboPunchHoldTheLockAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-heavy-lock", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Cow decoy = standingCow(helper, user.position().add(0.0D, 0.0D, 1.5D));
        Cow lock = standingCow(helper, user.position().add(-1.5D, 0.0D, 0.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            stand.setCanFollowUser(false);
            for (String name : List.of("rhcp_heavy_punch", "rhcp_combo_punch")) {
                LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.TRACKING_AND_SELF);
                state.setTargetId(lock.getId());
                stand.setStandCount(20);
                LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(decoy));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(decoy));
                EntityActionInstance action = ((EntityActionType) power.getAbility(name))
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.TRACKING_AND_SELF);
                helper.assertTrue(action.getTaskTarget(helper.getLevel()).getEntity() == lock,
                        "1.16 RHCPHeavyPunch.onTaskSet gave the task of " + name + " the lock; it holds "
                                + action.getTaskTarget(helper.getLevel()) + ".");
                helper.assertTrue(state.targetId() == lock.getId(),
                        "1.16 kept the lock (getTargetHit) through " + name + "; the lock is now "
                                + state.targetId() + ".");
                turnStand(stand, 180.0F);
                stand.updatePosition(user);
                assertFaces(helper, stand, lock, "1.16 turned a RHCP away from its user to the lock its "
                        + name + " holds");
            }
        } finally {
            decoy.discard();
            lock.discard();
            cleanup(user, power, "RHCP heavy lock GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPBarrage.onTaskSet set the lock as the task target, which a
    // barrage's task kept only as the Stand's barrage clash opponent
    // (StandEntityMeleeBarrage.checkStandTarget).
    @GameTest(template = "empty", batch = "rhcp_barrage_lock", timeoutTicks = 80)
    public static void aBarrageHoldsTheLockOnlyAsItsClashOpponentAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-barrage-lock", new BlockPos(1, 2, 1));
        ServerPlayer spUser = mockPlayer(helper, "rhcp-barrage-lock-sp", new BlockPos(4, 2, 1));
        StandPower power = summonRhcp(helper, user);
        spUser.setGameMode(GameType.SURVIVAL);
        StandPower spPower = StandPower.get(spUser);
        helper.assertTrue(spPower != null, "The mock player has no Stand power attachment.");
        spPower.setStand(ModStands.STAR_PLATINUM.get());
        spPower.setStamina(spPower.getMaxStamina());
        helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(spUser, spPower),
                "Star Platinum could not be summoned.");
        Cow decoy = standingCow(helper, user.position().add(0.0D, 0.0D, 1.5D));
        Cow lockCow = standingCow(helper, user.position().add(-1.5D, 0.0D, 0.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            StandEntity starPlatinum = spPower.getSummonedStandEntity();
            helper.assertTrue(stand != null && starPlatinum != null, "Setup: RHCP or Star Platinum is missing.");
            starPlatinum.setNoGravity(true);
            starPlatinum.moveTo(user.getX() + 1.5D, user.getY(), user.getZ(), 0.0F, 0.0F);
            RhcpState state = RhcpAddon.state(user);
            stand.setCanFollowUser(false);
            setClashOpponent(stand, starPlatinum);
            helper.assertTrue(stand.barrageClashOpponent().orElse(null) == starPlatinum,
                    "Setup: Star Platinum is not RHCP's barrage clash opponent.");
            for (LivingEntity lock : List.<LivingEntity>of(starPlatinum, lockCow)) {
                LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.TRACKING_AND_SELF);
                state.setTargetId(lock.getId());
                stand.setStandCount(20);
                LivingComponentAction.getComponent(user).entityAim.setTarget(new ActionTarget(decoy));
                LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(decoy));
                EntityActionInstance barrage = ((EntityActionType) power.getAbility("rhcp_barrage"))
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(barrage, user, SyncType.TRACKING_AND_SELF);
                ActionTarget held = barrage.getTaskTarget(helper.getLevel());
                if (lock == starPlatinum) {
                    helper.assertTrue(held.getEntity() == starPlatinum,
                            "1.16 RHCPBarrage.onTaskSet gave the barrage's task the lock that is its clash"
                                    + " opponent; it holds " + held + ".");
                    turnStand(stand, 180.0F);
                    stand.updatePosition(user);
                    assertFaces(helper, stand, starPlatinum,
                            "1.16 turned a RHCP away from its user to the clash opponent its barrage holds");
                }
                else {
                    helper.assertTrue(held.getType() == ActionTarget.TargetType.EMPTY,
                            "1.16 StandEntityMeleeBarrage.checkStandTarget refused a lock that is not the clash"
                                    + " opponent; the barrage's task holds " + held + ".");
                }
            }
        } finally {
            decoy.discard();
            lockCow.discard();
            cleanup(user, power, "RHCP barrage lock GameTest complete");
            cleanup(spUser, spPower, "RHCP barrage lock GameTest complete");
        }
        helper.succeed();
    }

    private static void setClashOpponent(StandEntity stand, LivingEntity opponent) {
        try {
            var set = StandEntity.class.getDeclaredMethod("setBarrageClashOpponent", Entity.class);
            set.setAccessible(true);
            set.invoke(stand, opponent);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not set the barrage clash opponent", failure);
        }
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery hurt the task's target
    // only if the last punch dealt more than 0 damage to a living entity: a
    // heavy that struck the lock without hurting it (here an invulnerable
    // one) left the follow-up with nothing to zap. The electrocute was a
    // StandEntityActionModifier (094 InitStands :77, RHCPHeavyPunch
    // .getRecoveryFollowup): its press added it to the heavy's task
    // (StandEntityActionModifier.perform, task.addModifierAction), the heavy
    // stayed the Stand's task through its own recovery, and the modifier
    // zapped on every recovery tick after the press (StandEntityTask.tick).
    @GameTest(template = "empty", batch = "rhcp_electrocute_unhurt", timeoutTicks = 200)
    public static void anElectrocuteAfterAHeavyThatDidNotHurtDoesNothingAsIn116(GameTestHelper helper) {
        checkElectrocuteAfterHeavy(helper, true);
    }

    @GameTest(template = "empty", batch = "rhcp_electrocute_hurt", timeoutTicks = 200)
    public static void anElectrocuteAfterAHeavyThatHurtZapsAsIn116(GameTestHelper helper) {
        checkElectrocuteAfterHeavy(helper, false);
    }

    private static void checkElectrocuteAfterHeavy(GameTestHelper helper, boolean unhurtable) {
        String name = unhurtable ? "rhcp-electro-unhurt" : "rhcp-electro-hurt";
        ServerPlayer user = mockPlayer(helper, name, new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        IronGolem lock = EntityType.IRON_GOLEM.create(helper.getLevel());
        helper.assertTrue(lock != null, "Could not make an iron golem.");
        Vec3 at = user.position().add(0.0D, 0.0D, 2.0D);
        lock.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
        lock.setNoAi(true);
        lock.setNoGravity(true);
        lock.setInvulnerable(unhurtable);
        helper.getLevel().addFreshEntity(lock);
        boolean[] zapped = {false};
        int[] zaps = {0};
        int[] expectedZaps = {0};
        float[] lastHealth = {0.0F};
        EntityActionInstance[] pressedIn = {null};
        String[] afterPress = {null};
        Runnable leave = () -> {
            lock.discard();
            cleanup(user, power, "RHCP electrocute GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        state.setTargetId(lock.getId());
                        stand.setStandCount(100);
                        EntityActionInstance heavy = ((EntityActionType) power.getAbility("rhcp_heavy_punch"))
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        LivingComponentAction.getComponent(stand).setAction(
                                heavy, user, SyncType.TRACKING_AND_SELF);
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> {
                    EntityActionInstance action = activeAction(user);
                    helper.assertTrue(RhcpAbility.modeOf(action) == RhcpAbility.Mode.HEAVY_PUNCH,
                            "The heavy punch ended before its recovery.");
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                            "The heavy punch is still in " + action.getPhase() + ".");
                })
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        EntityActionInstance heavy = activeAction(user);
                        helper.assertTrue(heavy.punchedTarget != null
                                        && heavy.punchedTarget.getEntity() == lock
                                        && state.targetId() == lock.getId(),
                                "Setup: the heavy did not strike the lock; it punched "
                                        + heavy.punchedTarget + ", the lock is " + state.targetId() + ".");
                        helper.assertTrue((stand.lastHeavyDamage() > 0.0F) != unhurtable,
                                "Setup: the heavy dealt " + stand.lastHeavyDamage() + " to the "
                                        + (unhurtable ? "unhurtable" : "hurtable") + " lock.");
                        Ability electrocute = power.getAbility("rhcp_electrocute_target");
                        helper.assertTrue(electrocute != null && electrocute.checkConditions(power).isPositive(),
                                "Setup: the electrocute follow-up is not open in the heavy's recovery.");
                        expectedZaps[0] = (int) (heavy.phasesLength.getFloat(ActionPhase.RECOVERY)
                                - heavy.getPhaseTick());
                        lastHealth[0] = lock.getHealth();
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress((short) 77, electrocute, user,
                                new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK, 0.0F,
                                pressed, electrocute.abilityId);
                        if (!pressed.isActionSuccess || activeAction(user) != heavy || heavy.isOver()) {
                            afterPress[0] = "accepted " + pressed.isActionSuccess + ", RHCP's action "
                                    + phaseOf(user) + ", the heavy over " + heavy.isOver();
                        }
                        pressedIn[0] = heavy;
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> {
                    EntityActionInstance heavy = pressedIn[0];
                    if (heavy == null || afterPress[0] != null) {
                        return;
                    }
                    zapped[0] |= lock.hasEffect(ModStatusEffects.IMMOBILIZE);
                    float health = lock.getHealth();
                    if (health < lastHealth[0]) {
                        zaps[0]++;
                    }
                    lastHealth[0] = health;
                    if (!heavy.isOver() && activeAction(user) != heavy) {
                        afterPress[0] = "RHCP's action became " + phaseOf(user) + " before the heavy's recovery ended";
                        return;
                    }
                    helper.assertTrue(heavy.isOver(), "Waiting for the heavy's recovery to end.");
                })
                .thenExecute(() -> {
                    try {
                        helper.assertTrue(afterPress[0] == null,
                                "1.16 added the electrocute to the heavy's task as a modifier (it started no task), so the"
                                        + " heavy went on through its recovery: " + afterPress[0]);
                        helper.assertTrue(zapped[0] != unhurtable,
                                unhurtable
                                        ? "1.16 RHCPElectrocuteTarget.standTickRecovery zapped only after a punch that"
                                                + " dealt damage; the electrocute zapped a lock the heavy did not hurt."
                                        : "1.16 RHCPElectrocuteTarget.standTickRecovery zapped the lock the heavy"
                                                + " hurt; it did not.");
                        helper.assertTrue(zaps[0] == (unhurtable ? 0 : expectedZaps[0]),
                                "1.16 RHCPElectrocuteTarget zapped on each of the heavy's " + expectedZaps[0]
                                        + " recovery ticks after the press" + (unhurtable ? " (none here: the heavy did"
                                        + " not hurt)" : "") + "; the port zapped " + zaps[0] + " times");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    private static final short FOLLOW_UP_KEY = 78;

    // 1.16 StandAction.replaceActionKostyl gave the heavy key the electrocute
    // follow-up (RHCPHeavyPunch.getRecoveryFollowup) whenever the heavy was
    // the Stand's task, in its windup and perform too, lock or not, until the
    // modifier was on the task (canSetFollowUp: !hasModifierAction, which the
    // clients learned from TrStandTaskModifierPacket); the modifier then
    // zapped on every tick of the heavy's recovery
    // (RHCPElectrocuteTarget.standTickRecovery).
    @GameTest(template = "empty", batch = "rhcp_electrocute_whole_task", timeoutTicks = 200)
    public static void theElectrocuteFollowUpIsOfferedThroughTheWholeHeavyAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-electro-task", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        for (String row : List.of("windup", "perform_start")) {
                            checkElectrocuteThroughHeavy(helper, user, power, row);
                        }
                    }
                    finally {
                        cleanup(user, power, "RHCP electrocute whole task GameTest complete");
                    }
                })
                .thenSucceed();
    }

    private static void checkElectrocuteThroughHeavy(GameTestHelper helper, ServerPlayer user,
            StandPower power, String row) {
        IronGolem lock = EntityType.IRON_GOLEM.create(helper.getLevel());
        helper.assertTrue(lock != null, "Could not make an iron golem.");
        Vec3 at = user.position().add(0.0D, 0.0D, 2.0D);
        lock.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
        lock.setNoAi(true);
        lock.setNoGravity(true);
        helper.getLevel().addFreshEntity(lock);
        RhcpStandEntity stand = RhcpService.activeStand(user);
        LivingComponentAction component = LivingComponentAction.getComponent(stand);
        try {
            RhcpState state = RhcpAddon.state(user);
            Ability heavyKey = power.getAbility("rhcp_heavy_punch");
            Ability electrocute = power.getAbility("rhcp_electrocute_target");
            component.setAction(null, user, SyncType.TRACKING_AND_SELF);
            stand.setAttackAlternate(false);
            state.setTargetId(lock.getId());
            stand.setStandCount(100);
            EntityActionInstance heavy = ((EntityActionType) heavyKey)
                    .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
            component.setAction(heavy, user, SyncType.TRACKING_AND_SELF);
            // the heavy strikes the lock it was set with; the key needs none
            state.setTargetId(-1);
            if (row.equals("perform_start")) {
                for (int i = 0; i < 60 && heavy.getPhase() == ActionPhase.WINDUP; i++) {
                    heavy._tickAction();
                }
            }
            ActionPhase phase = row.equals("windup") ? ActionPhase.WINDUP : ActionPhase.PERFORM;
            helper.assertTrue(heavy.getPhase() == phase && heavy.getPhaseTick() < 1 && heavy.punchedTarget == null,
                    "Setup (" + row + "): the heavy is in " + heavy.getPhase() + " tick " + heavy.getPhaseTick()
                            + ", punched " + heavy.punchedTarget);
            AbilityConditionCheck offered = resolve(power, heavyKey);
            helper.assertTrue(offered != null && offered.ability == electrocute && offered.conditionCheck.isPositive(),
                    "1.16 gave the heavy key the electrocute follow-up in the heavy's " + row
                            + " (replaceActionKostyl asked no phase); the key gives " + describe(offered));
            EntityActionInstance client = clientCopy(helper, heavy, stand);
            receiveSyncedData(client, heavy);
            AbilityConditionCheck clientOffered = resolveWith(power, stand, heavyKey, client);
            helper.assertTrue(clientOffered != null && clientOffered.ability == electrocute,
                    "1.16's client showed the electrocute on the heavy key in the heavy's " + row + "; the client's"
                            + " copy of the heavy gives " + describe(clientOffered));
            assertFollowUpDrawnUsable(helper, power, stand, heavyKey, client, user,
                    "the electrocute in the heavy's " + row);

            helper.assertTrue(AbilityInput.withConditionCheck(offered, user, InputMethod.CLICK),
                    "the electrocute pressed in the heavy's " + row + " is refused at its preflight");
            BufferingState pressed = BufferingState.clickCanBuffer();
            AbilityInput.keyPress(FOLLOW_UP_KEY, electrocute, user, new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.CLICK, 0.0F, pressed, heavyKey.abilityId);
            AbilityInput.keyRelease(FOLLOW_UP_KEY, user);
            helper.assertTrue(pressed.isActionSuccess && component.getAction() == heavy && heavy.getPhase() == phase,
                    "1.16 added the electrocute pressed in the heavy's " + row + " to the heavy's task: accepted "
                            + pressed.isActionSuccess + ", RHCP's action " + phaseOf(user));

            AbilityConditionCheck taken = resolve(power, heavyKey);
            helper.assertTrue(taken != null && taken.ability == heavyKey && !electrocute.checkConditions(power).isPositive(),
                    "1.16 took the electrocute off the heavy key once it was on the heavy's task (canSetFollowUp);"
                            + " the key gives " + describe(taken) + " in the heavy's " + row);
            receiveSyncedData(client, heavy);
            AbilityConditionCheck clientTaken = resolveWith(power, stand, heavyKey, client);
            helper.assertTrue(clientTaken != null && clientTaken.ability == heavyKey,
                    "1.16 told the clients the electrocute was on the heavy's task, so the key left it there too;"
                            + " the client's copy of the heavy gives " + describe(clientTaken) + " in its " + row);
            assertSelectionNotGreen(helper, power, electrocute, electrocute.checkConditions(power),
                    "the electrocute once it is on the heavy's task (" + row + ")");
            assertSelectionNotGreen(helper, power, heavyKey, ConditionCheck.POSITIVE, "the heavy punch key");

            int recoveryTicks = 0;
            int zaps = 0;
            for (int i = 0; i < 200 && !heavy.isOver(); i++) {
                boolean recovery = heavy.getPhase() == ActionPhase.RECOVERY;
                float before = lock.getHealth();
                heavy._tickAction();
                if (recovery) {
                    recoveryTicks++;
                    if (lock.getHealth() < before) {
                        zaps++;
                    }
                }
            }
            helper.assertTrue(heavy.punchedTarget != null && heavy.punchedTarget.getEntity() == lock
                            && stand.lastHeavyDamage() > 0.0F,
                    "Setup (" + row + "): the heavy did not hurt the lock: " + heavy.punchedTarget);
            helper.assertTrue(recoveryTicks > 0 && zaps == recoveryTicks,
                    "1.16's electrocute pressed in the heavy's " + row + " zapped on each of the heavy's "
                            + recoveryTicks + " recovery ticks; the port zapped " + zaps + " times");
        }
        finally {
            component.setAction(null, user, SyncType.TRACKING_AND_SELF);
            lock.discard();
        }
    }

    // 1.16 RHCPComboPunch.getRecoveryFollowup: the heavy key gave the chase
    // while the combo was the Stand's task until the chase was on it, which
    // the clients learned (TrStandTaskModifierPacket); then it gave the
    // running combo again.
    @GameTest(template = "empty", batch = "rhcp_chase_taken", timeoutTicks = 80)
    public static void theChaseFollowUpLeavesTheKeyOnceTakenOnTheClientTooAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-chase-taken", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    RhcpStandEntity stand = RhcpService.activeStand(user);
                    LivingComponentAction component = LivingComponentAction.getComponent(stand);
                    try {
                        RhcpAddon.state(user).setPotency(4);
                        Ability heavyKey = power.getAbility("rhcp_heavy_punch");
                        Ability combo = power.getAbility("rhcp_combo_punch");
                        Ability chase = power.getAbility("rhcp_chase_target");
                        EntityActionInstance running = ((EntityActionType) combo)
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        component.setAction(running, user, SyncType.TRACKING_AND_SELF);
                        helper.assertTrue(running.getPhase() == ActionPhase.WINDUP,
                                "Setup: the combo is in " + running.getPhase());
                        AbilityConditionCheck offered = resolve(power, heavyKey);
                        helper.assertTrue(offered != null && offered.ability == chase
                                        && offered.conditionCheck.isPositive(),
                                "1.16 gave the heavy key the chase while the combo ran; it gives " + describe(offered));
                        EntityActionInstance client = clientCopy(helper, running, stand);
                        receiveSyncedData(client, running);
                        assertFollowUpDrawnUsable(helper, power, stand, heavyKey, client, user,
                                "the chase in the combo's windup");
                        helper.assertTrue(AbilityInput.withConditionCheck(offered, user, InputMethod.CLICK),
                                "the chase is refused at its preflight");
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress(FOLLOW_UP_KEY, chase, user, new FriendlyByteBuf(Unpooled.buffer()),
                                InputMethod.CLICK, 0.0F, pressed, heavyKey.abilityId);
                        AbilityInput.keyRelease(FOLLOW_UP_KEY, user);
                        helper.assertTrue(pressed.isActionSuccess && component.getAction() == running,
                                "Setup: the chase was not added to the combo: accepted " + pressed.isActionSuccess
                                        + ", RHCP's action " + phaseOf(user));
                        AbilityConditionCheck taken = resolve(power, heavyKey);
                        helper.assertTrue(taken != null && taken.ability == combo,
                                "1.16 gave the heavy key the running combo once the chase was on it; it gives "
                                        + describe(taken));
                        receiveSyncedData(client, running);
                        AbilityConditionCheck clientTaken = resolveWith(power, stand, heavyKey, client);
                        helper.assertTrue(clientTaken != null && clientTaken.ability == combo,
                                "1.16 told the clients the chase was on the combo's task, so the key gave the combo"
                                        + " there too; the client's copy of the combo gives " + describe(clientTaken));
                        assertSelectionNotGreen(helper, power, chase, chase.checkConditions(power),
                                "the chase once it is on the combo's task");
                        assertSelectionNotGreen(helper, power, combo, ConditionCheck.POSITIVE, "the combo key");
                        assertSelectionNotGreen(helper, power, power.getAbility("rhcp_dodge"), ConditionCheck.POSITIVE,
                                "another RHCP move");
                    }
                    finally {
                        component.setAction(null, user, SyncType.TRACKING_AND_SELF);
                        cleanup(user, power, "RHCP chase taken GameTest complete");
                    }
                })
                .thenSucceed();
    }

    // 1.16 InitStands :75, :77 built the chase and the electrocute with no
    // partsRequired, so canSetFollowUp's checkRequirements asked for no Stand
    // part; the heavy key first went by the lost parts
    // (StandEntityHeavyAttack.getFinisherVariationIfPresent) and only the
    // attack it went to offered its own follow-up (replaceActionKostyl).
    @GameTest(template = "empty", batch = "rhcp_follow_up_parts", timeoutTicks = 80)
    public static void theFollowUpsNeedNoStandPartAndFollowTheHeavyKeysPartRouteAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-follow-parts", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    RhcpStandEntity stand = RhcpService.activeStand(user);
                    LivingComponentAction component = LivingComponentAction.getComponent(stand);
                    StandInstance instance = power.getStandInstance().orElse(null);
                    helper.assertTrue(instance != null, "Setup: RHCP has no Stand instance");
                    Ability heavyKey = power.getAbility("rhcp_heavy_punch");
                    Ability combo = power.getAbility("rhcp_combo_punch");
                    Ability chase = power.getAbility("rhcp_chase_target");
                    Ability electrocute = power.getAbility("rhcp_electrocute_target");
                    List<String> wrong = new ArrayList<>();
                    try {
                        RhcpAddon.state(user).setPotency(4);
                        stand.setAttackAlternate(false);

                        // a heavy whose Stand lost its arms and body: the combo needs the body, so the key
                        // stays on the heavy, which gives its electrocute
                        EntityActionInstance heavy = ((EntityActionType) heavyKey)
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        component.setAction(heavy, user, SyncType.TRACKING_AND_SELF);
                        instance.removePart(StandPart.ARMS);
                        instance.removePart(StandPart.MAIN_BODY);
                        AbilityConditionCheck offered = resolve(power, heavyKey);
                        if (offered == null || offered.ability != electrocute || !offered.conditionCheck.isPositive()) {
                            wrong.add("heavy, arms and body lost: 1.16 gave the key the electrocute, usable; the port "
                                    + describe(offered) + " " + electrocute.checkConditions(power).getWarning());
                        }
                        else {
                            BufferingState pressed = BufferingState.clickCanBuffer();
                            AbilityInput.keyPress(FOLLOW_UP_KEY, electrocute, user,
                                    new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK, 0.0F, pressed,
                                    heavyKey.abilityId);
                            AbilityInput.keyRelease(FOLLOW_UP_KEY, user);
                            if (!pressed.isActionSuccess || component.getAction() != heavy) {
                                wrong.add("heavy, arms and body lost: 1.16 added the electrocute to the heavy; the"
                                        + " press was accepted " + pressed.isActionSuccess + ", RHCP's action "
                                        + phaseOf(user));
                            }
                        }
                        instance.addPart(StandPart.MAIN_BODY);
                        // only the arms lost: the key goes to the combo, which offers no electrocute
                        component.setAction(null, user, SyncType.TRACKING_AND_SELF);
                        heavy = ((EntityActionType) heavyKey)
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        component.setAction(heavy, user, SyncType.TRACKING_AND_SELF);
                        offered = resolve(power, heavyKey);
                        if (offered == null || offered.ability != combo) {
                            wrong.add("heavy, arms lost: 1.16 gave the key the combo (the heavy needs the arms);"
                                    + " the port " + describe(offered));
                        }
                        instance.addPart(StandPart.ARMS);

                        // a combo whose Stand lost its body: the key stays on the heavy, so no chase, though the
                        // chase itself asks for no part
                        component.setAction(null, user, SyncType.TRACKING_AND_SELF);
                        EntityActionInstance running = ((EntityActionType) combo)
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        component.setAction(running, user, SyncType.TRACKING_AND_SELF);
                        instance.removePart(StandPart.MAIN_BODY);
                        if (!chase.checkConditions(power).isPositive()) {
                            wrong.add("combo, body lost: 1.16's chase asked for no Stand part (canSetFollowUp); the"
                                    + " port's chase is refused " + chase.checkConditions(power).getWarning());
                        }
                        offered = resolve(power, heavyKey);
                        if (offered == null || offered.ability != heavyKey) {
                            wrong.add("combo, body lost: 1.16 gave the key the heavy (the combo needs the body),"
                                    + " which offers no chase; the port " + describe(offered));
                        }
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress(FOLLOW_UP_KEY, heavyKey, user, new FriendlyByteBuf(Unpooled.buffer()),
                                InputMethod.CLICK, 0.0F, pressed, heavyKey.abilityId);
                        AbilityInput.keyRelease(FOLLOW_UP_KEY, user);
                        EntityActionInstance after = component.getAction();
                        if (after == running || after == null || after.ability != heavyKey) {
                            wrong.add("combo, body lost, potency 4: 1.16's heavy key pressed in the combo was the"
                                    + " heavy, which cancelled the combo (isCancelable), not a chase; RHCP's action is "
                                    + (after == running ? "still the combo" : phaseOf(user)));
                        }
                        helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
                    }
                    finally {
                        instance.addPart(StandPart.ARMS);
                        instance.addPart(StandPart.MAIN_BODY);
                        component.setAction(null, user, SyncType.TRACKING_AND_SELF);
                        cleanup(user, power, "RHCP follow-up parts GameTest complete");
                    }
                })
                .thenSucceed();
    }

    // the action as the user's client holds it: decoded from what the server
    // sends (EntityActionInstance.encode), with its Stand
    private static EntityActionInstance clientCopy(GameTestHelper helper, EntityActionInstance action,
            StandEntity stand) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            EntityActionInstance.encode(buf, action);
            EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
            helper.assertTrue(copy != null && copy.getClass() == action.getClass(),
                    "Setup: the action decodes as " + copy);
            Field performer = EntityActionInstance.class.getDeclaredField("performer");
            performer.setAccessible(true);
            performer.set(copy, stand);
            return copy;
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Setup: cannot give the client's copy its Stand", e);
        }
        finally {
            buf.release();
        }
    }

    // the synced data the server sends the clients tracking the Stand on its
    // next tick (SyncActionInstanceData.tickSyncDirtyData)
    private static void receiveSyncedData(EntityActionInstance client, EntityActionInstance server) {
        List<SynchedEntityData.DataValue<?>> sent = server.synchedData.getDataSyncher().syncDirtyData();
        if (sent != null) {
            client.synchedData.getDataSyncher().assignValues(sent);
        }
    }

    // the key as the client resolves it, with its copy as the Stand's action
    private static AbilityConditionCheck resolveWith(StandPower power, StandEntity stand, Ability key,
            EntityActionInstance clientAction) {
        return withStandAction(stand, clientAction, () -> resolve(power, key));
    }

    private static <T> T withStandAction(StandEntity stand, EntityActionInstance clientAction, Supplier<T> read) {
        LivingComponentAction component = LivingComponentAction.getComponent(stand);
        try {
            Field field = LivingComponentAction.class.getDeclaredField("action");
            field.setAccessible(true);
            Object serverAction = field.get(component);
            field.set(component, clientAction);
            try {
                return read.get();
            }
            finally {
                field.set(component, serverAction);
            }
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Setup: cannot put the client's copy on the Stand", e);
        }
    }

    // 1.16 drew the chase and the electrocute, StandEntityActionModifiers (094 InitStands :75, :77) that a press puts on
    // the running heavy at once (StandEntityActionModifier.perform :21-26), by their requirements: only a
    // StandEntityAction was ever queued behind the Stand's task and drawn at 0.75 (StandEntityAction.checkTaskCancelling
    // :127-143, ActionsOverlayGui :1165-1175)
    private static void assertFollowUpDrawnUsable(GameTestHelper helper, StandPower power, StandEntity stand,
            Ability key, EntityActionInstance clientAction, LivingEntity user, String row) {
        AbilityConditionCheck server = resolve(power, key);
        ConditionCheck drawn = server != null ? server.drawnCondition(user) : null;
        ConditionCheck clientDrawn = withStandAction(stand, clientAction, () -> {
            AbilityConditionCheck client = resolve(power, key);
            return client != null ? client.drawnCondition(user) : null;
        });
        helper.assertTrue(drawn == ConditionCheck.POSITIVE && clientDrawn == ConditionCheck.POSITIVE,
                "1.16 drew " + row + " usable (a modifier taken at the press, never queued); the port draws it "
                        + drawnName(drawn) + " on the server and " + drawnName(clientDrawn) + " on the client");
        helper.assertTrue(selectionTint(server.ability, power, drawn).equals(OptionalInt.of(0xFF00FF00))
                        && selectionTint(server.ability, power, clientDrawn).equals(OptionalInt.of(0xFF00FF00)),
                "1.16 framed " + row + " green while selected and usable (StandEntityActionModifier.greenSelection);"
                        + " the port frames it " + selectionTint(server.ability, power, drawn));
    }

    private static OptionalInt selectionTint(Ability ability, StandPower power, ConditionCheck drawn) {
        return RhcpAbility.followUpSelectionTint(new AbilitySelectionVisualQuery(
                ability, power, drawn, AbilitySelectionSurface.HOTBAR_SELECTED));
    }

    // a selected follow-up that is not usable, and any other move, kept the white frame
    private static void assertSelectionNotGreen(GameTestHelper helper, StandPower power, Ability ability,
            ConditionCheck drawn, String row) {
        OptionalInt tint = selectionTint(ability, power, drawn);
        helper.assertTrue(tint.isEmpty(),
                "1.16 framed " + row + " white (not a usable follow-up); the port frames it " + tint);
    }

    private static String drawnName(ConditionCheck drawn) {
        return drawn == null ? "nothing" : drawn == ConditionCheck.POSITIVE ? "usable"
                : drawn == ConditionCheck.NEGATIVE_QUEUED ? "queued (0.75)" : "greyed";
    }

    private static String describe(AbilityConditionCheck container) {
        return container == null ? "nothing" : container.ability.name() + " ("
                + (container.conditionCheck.isPositive() ? "usable" : "refused") + ")";
    }

    // The core heavy performs for 1 tick (K5-30), as 1.16's StandEntityHeavyAttack
    // builder did: the hit and the move into the recovery come in one tick, so
    // the electrocute follow-up is open the tick after the hit (1.16 offered
    // it while the heavy was the Stand's task, StandAction.replaceActionKostyl).
    @GameTest(template = "empty", batch = "rhcp_electrocute_window", timeoutTicks = 200)
    public static void theElectrocuteFollowUpIsOpenTheTickAfterTheHeavyHitsAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-electro-window", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        IronGolem lock = EntityType.IRON_GOLEM.create(helper.getLevel());
        helper.assertTrue(lock != null, "Could not make an iron golem.");
        Vec3 at = user.position().add(0.0D, 0.0D, 2.0D);
        lock.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
        lock.setNoAi(true);
        lock.setNoGravity(true);
        helper.getLevel().addFreshEntity(lock);
        Runnable leave = () -> {
            lock.discard();
            cleanup(user, power, "RHCP electrocute window GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        state.setTargetId(lock.getId());
                        stand.setStandCount(100);
                        EntityActionInstance heavy = ((EntityActionType) power.getAbility("rhcp_heavy_punch"))
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        LivingComponentAction.getComponent(stand).setAction(
                                heavy, user, SyncType.TRACKING_AND_SELF);
                        Ability electrocute = power.getAbility("rhcp_electrocute_target");
                        helper.assertTrue(electrocute != null, "RHCP has no electrocute follow-up.");
                        helper.assertTrue(heavy.getPhase() == ActionPhase.WINDUP,
                                "Setup: the heavy did not start in its windup: " + heavy.getPhase());
                        for (int i = 0; i < 60 && heavy.getPhase() == ActionPhase.WINDUP; i++) {
                            heavy._tickAction();
                        }
                        helper.assertTrue(heavy.getPhase() == ActionPhase.PERFORM && heavy.getPhaseTick() < 1
                                        && heavy.punchedTarget == null,
                                "Setup: the heavy is not at its perform's start before the hit: " + heavy.getPhase()
                                        + " tick " + heavy.getPhaseTick() + ", punched " + heavy.punchedTarget);
                        heavy._tickAction();
                        helper.assertTrue(heavy.punchedTarget != null && heavy.punchedTarget.getEntity() == lock,
                                "Setup: the heavy's perform did not strike the lock: " + heavy.punchedTarget);
                        helper.assertTrue(heavy.getPhase() == ActionPhase.RECOVERY && heavy.getPhaseTick() < 1,
                                "1.16's heavy performed for one tick, so it was in its recovery at the end of the"
                                        + " tick it hit: the port's is in " + heavy.getPhase() + " tick "
                                        + heavy.getPhaseTick());
                        helper.assertTrue(electrocute.checkConditions(power).isPositive(),
                                "the electrocute follow-up is not open at the start of the heavy's recovery, the tick"
                                        + " after its hit");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery :90: every zap paid
    // consumeEnergy(1.0 + 0.1 / 0.2 / 0.3 above potency 1 / 2 / 3, 0, true),
    // with no Resolve scaling, so potency 5 paid what potency 4 did.
    @GameTest(template = "empty", batch = "rhcp_electrocute_cost", timeoutTicks = 80)
    public static void anElectrocuteZapPaysTheDonorsEnergyAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-electro-cost", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Cow target = standingCow(helper, user.position().add(0.0D, 0.0D, 2.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            stand.setLastHeavyDamage(1.0F);
            float[] donorCost = {1.0F, 1.1F, 1.2F, 1.3F, 1.3F};
            for (int resolve : new int[] {power.getResolveLevel(), 1}) {
                power.setResolveLevel(resolve);
                for (int potency = 1; potency <= 5; potency++) {
                    state.setEnergy(1000.0F);
                    state.setPotency(potency);
                    target.setHealth(target.getMaxHealth());
                    helper.assertTrue(state.potency() == potency
                                    && RhcpService.electrocuteTarget(user, target, false),
                            "Setup: no zap at potency " + potency + ", Resolve " + resolve + ".");
                    float paid = 1000.0F - state.energy();
                    helper.assertTrue(Math.abs(paid - donorCost[potency - 1]) < 1.0E-3F,
                            "1.16 RHCPElectrocuteTarget paid " + donorCost[potency - 1]
                                    + " energy a zap at potency " + potency + " (Resolve " + resolve
                                    + "); the port paid " + paid);
                }
            }
        } finally {
            target.discard();
            cleanup(user, power, "RHCP electrocute cost GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery :91-104: on the heavy's
    // last recovery tick a manually controlled RHCP less than 2 blocks from
    // its recent block (the last block it entered or left, RHCPExit :66 and
    // EnterTheBlock :269; its own block until then) went back into it if it
    // was a conductor and carried the zapped target along (setTargetHit(id,
    // true)). Without manual control nothing moved.
    @GameTest(template = "empty", batch = "rhcp_electrocute_drag_left", timeoutTicks = 200)
    public static void aManualElectrocuteDragsItsTargetIntoTheConductorRhcpLeftAsIn116(GameTestHelper helper) {
        checkElectrocuteDrag(helper, true, true);
    }

    @GameTest(template = "empty", batch = "rhcp_electrocute_drag_own", timeoutTicks = 200)
    public static void aManualElectrocuteDragsItsTargetIntoTheConductorRhcpStandsInAsIn116(GameTestHelper helper) {
        checkElectrocuteDrag(helper, true, false);
    }

    @GameTest(template = "empty", batch = "rhcp_electrocute_drag_auto", timeoutTicks = 200)
    public static void anElectrocuteWithoutManualControlDragsNothingAsIn116(GameTestHelper helper) {
        checkElectrocuteDrag(helper, false, false);
    }

    private static void checkElectrocuteDrag(GameTestHelper helper, boolean manual, boolean leftConductor) {
        String name = "rhcp-drag-" + (manual ? "manual" : "auto") + (leftConductor ? "-left" : "-own");
        ServerPlayer user = mockPlayer(helper, name, new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Level level = helper.getLevel();
        IronGolem lock = EntityType.IRON_GOLEM.create(level);
        helper.assertTrue(lock != null, "Could not make an iron golem.");
        lock.setNoAi(true);
        lock.setNoGravity(true);
        List<BlockPos> placed = new ArrayList<>();
        BlockPos[] conductor = {null};
        boolean[] zapped = {false};
        EntityActionInstance[] pressedIn = {null};
        String[] afterPress = {null};
        String[] early = {null};
        Runnable leave = () -> {
            lock.discard();
            for (BlockPos pos : placed) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            cleanup(user, power, "RHCP electrocute drag GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        if (manual) {
                            StandEntityManualControlToggle.on(level, stand);
                            helper.assertTrue(stand.isManuallyControlled(), "Setup: RHCP is not manually controlled.");
                        }
                        if (leftConductor) {
                            // 1.16 RHCPExit left RHCP where it was, and only
                            // from a block that is not a full cube: a wire.
                            BlockPos wire = stand.blockPosition();
                            for (BlockPos floor : List.of(wire.below(), wire.below().east())) {
                                if (level.getBlockState(floor).isAir()) {
                                    level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                                    placed.add(floor);
                                }
                            }
                            level.setBlockAndUpdate(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
                            placed.add(0, wire);
                            conductor[0] = wire;
                            state.enterConductor(wire);
                            RhcpService.teleportStand(stand, RhcpService.conductorSpot(wire));
                            RhcpService.leaveConductor(user);
                            helper.assertTrue(!state.inConductor() && stand.recentBlock().equals(wire),
                                    "Setup: RHCP did not leave the wire: its recent block is "
                                            + stand.recentBlock() + ".");
                            // its own block is no longer the wire it left
                            RhcpService.teleportStand(stand, stand.position().add(1.0D, 0.0D, 0.0D));
                        }
                        Vec3 at = stand.position().add(-0.3D, 0.0D, 2.0D);
                        lock.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
                        level.addFreshEntity(lock);
                        state.setTargetId(lock.getId());
                        stand.setStandCount(100);
                        EntityActionInstance heavy = ((EntityActionType) power.getAbility("rhcp_heavy_punch"))
                                .initActionOnAbilityUse(level, user, stand, null);
                        LivingComponentAction.getComponent(stand).setAction(
                                heavy, user, SyncType.TRACKING_AND_SELF);
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> {
                    EntityActionInstance action = activeAction(user);
                    helper.assertTrue(RhcpAbility.modeOf(action) == RhcpAbility.Mode.HEAVY_PUNCH,
                            "The heavy punch ended before its recovery.");
                    helper.assertTrue(action.getPhase() == ActionPhase.RECOVERY,
                            "The heavy punch is still in " + action.getPhase() + ".");
                })
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        EntityActionInstance heavy = activeAction(user);
                        helper.assertTrue(heavy.punchedTarget != null && heavy.punchedTarget.getEntity() == lock
                                        && stand.lastHeavyDamage() > 0.0F,
                                "Setup: the heavy did not hurt the lock; it punched " + heavy.punchedTarget + ".");
                        if (!leftConductor) {
                            BlockPos wire = stand.blockPosition();
                            if (level.getBlockState(wire.below()).isAir()) {
                                level.setBlockAndUpdate(wire.below(), Blocks.STONE.defaultBlockState());
                                placed.add(wire.below());
                            }
                            level.setBlockAndUpdate(wire, Blocks.REDSTONE_WIRE.defaultBlockState());
                            placed.add(0, wire);
                            conductor[0] = wire;
                            helper.assertTrue(RhcpService.isConductor(level.getBlockState(wire)),
                                    "Setup: the wire under RHCP is not a conductor.");
                        }
                        Ability electrocute = power.getAbility("rhcp_electrocute_target");
                        BufferingState pressed = BufferingState.clickCanBuffer();
                        AbilityInput.keyPress((short) 77, electrocute, user,
                                new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK, 0.0F,
                                pressed, electrocute.abilityId);
                        if (!pressed.isActionSuccess || activeAction(user) != heavy) {
                            afterPress[0] = "accepted " + pressed.isActionSuccess + ", RHCP's action "
                                    + phaseOf(user);
                        }
                        pressedIn[0] = heavy;
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenWaitUntil(() -> {
                    EntityActionInstance heavy = pressedIn[0];
                    if (heavy == null || afterPress[0] != null) {
                        return;
                    }
                    zapped[0] |= lock.hasEffect(ModStatusEffects.IMMOBILIZE);
                    if (early[0] == null && !heavy.isOver() && RhcpAddon.state(user).inConductor()) {
                        early[0] = "RHCP was in the conductor with " + heavy.getPhaseTicksLeft()
                                + " recovery ticks left";
                    }
                    helper.assertTrue(heavy.isOver(), "Waiting for the heavy's recovery to end.");
                })
                .thenExecute(() -> {
                    try {
                        RhcpState state = RhcpAddon.state(user);
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        helper.assertTrue(afterPress[0] == null && zapped[0],
                                "Setup: the electrocute did not zap the lock: " + afterPress[0]);
                        helper.assertTrue(early[0] == null,
                                "1.16 RHCPElectrocuteTarget dragged on the heavy's last recovery tick"
                                        + " (task.getTicksLeft() <= 1): " + early[0]);
                        String where = "RHCP in a conductor " + state.inConductor() + " at " + state.conductorPos()
                                + ", RHCP at " + stand.position() + ", the carried target " + state.carriedTargetId()
                                + " (the lock " + lock.getId() + ") at " + lock.position() + ", the conductor "
                                + conductor[0];
                        if (manual) {
                            helper.assertTrue(state.inConductor() && state.conductorPos().equals(conductor[0])
                                            && state.carriedTargetId() == lock.getId()
                                            && stand.position().distanceTo(
                                                    RhcpService.conductorSpot(conductor[0])) < 1.0E-6D
                                            && lock.position().distanceTo(
                                                    RhcpService.conductorSpot(conductor[0])) < 1.0E-6D,
                                    "1.16 RHCPElectrocuteTarget took the zapped target into the conductor "
                                            + (leftConductor ? "RHCP had left" : "RHCP stood in")
                                            + " on the heavy's last recovery tick: " + where);
                        }
                        else {
                            helper.assertTrue(!state.inConductor() && state.carriedTargetId() < 0,
                                    "1.16 RHCPElectrocuteTarget dragged only under manual control: " + where);
                        }
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // 1.16 RHCPGettingIntoTheMechanism.EnterTheBlock :270-271 put RHCP on the
    // conductor's bottom centre, 0.01 up; RHCPExit.standPerform :62-68 left
    // it where it was (only its pose and recent block changed), and its
    // checkSpecificConditions :49-50 refused to leave a full cube with
    // 'full_block_rhcp'.
    @GameTest(template = "empty", batch = "rhcp_conductor_spots", timeoutTicks = 80)
    public static void rhcpEntersAConductorAtItsFootAndLeavesInPlaceAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-conductor-spots", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Level level = helper.getLevel();
        List<BlockPos> placed = new ArrayList<>();
        BlockPos[] repeater = {null};
        Runnable leave = () -> {
            RhcpAddon.state(user).leaveConductor();
            for (BlockPos pos : placed) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            cleanup(user, power, "RHCP conductor spots GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        repeater[0] = user.blockPosition().offset(0, 0, 2);
                        for (BlockPos floor : List.of(repeater[0].below(), repeater[0].below().east())) {
                            if (level.getBlockState(floor).isAir()) {
                                level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
                                placed.add(floor);
                            }
                        }
                        level.setBlockAndUpdate(repeater[0], Blocks.REPEATER.defaultBlockState());
                        placed.add(0, repeater[0]);
                        user.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(
                                repeater[0].getX() + 0.5D, repeater[0].getY() + 0.06D, repeater[0].getZ() + 0.5D));
                        helper.assertTrue(!stand.isManuallyControlled()
                                        && RhcpService.enterConductor(user)
                                        && state.inConductor() && state.conductorPos().equals(repeater[0]),
                                "Setup: RHCP did not enter the repeater under the crosshair: in a conductor "
                                        + state.inConductor() + " at " + state.conductorPos() + ".");
                        assertConductorSpot(helper, stand, repeater[0], "as it entered");
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        helper.assertTrue(state.inConductor(), "Setup: RHCP left the repeater by itself.");
                        assertConductorSpot(helper, stand, repeater[0], "two ticks in");
                        Ability exit = power.getAbility("rhcp_exit");
                        helper.assertTrue(exit != null && exit.checkConditions(power).isPositive(),
                                "Setup: the exit is refused in a repeater.");
                        Vec3 inside = stand.position();
                        RhcpService.leaveConductor(user);
                        helper.assertTrue(!state.inConductor()
                                        && stand.position().distanceTo(inside) < 1.0E-6D
                                        && stand.recentBlock().equals(repeater[0]),
                                "1.16 RHCPExit left RHCP where it was in the block it left (:62-68), which"
                                        + " became its recent block: it moved "
                                        + stand.position().subtract(inside) + ", recent block "
                                        + stand.recentBlock() + ", the repeater " + repeater[0] + ".");

                        BlockPos lamp = repeater[0].east().above();
                        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
                        placed.add(0, lamp);
                        state.enterConductor(lamp);
                        RhcpService.teleportStand(stand, new Vec3(
                                lamp.getX() + 0.5D, lamp.getY() + 0.01D, lamp.getZ() + 0.5D));
                        var full = exit.checkConditions(power);
                        helper.assertTrue(!full.isPositive() && full.getWarning() != null
                                        && full.getWarning().getContents() instanceof TranslatableContents warning
                                        && warning.getKey().equals(
                                                "jojo.message.action_condition.full_block_rhcp"),
                                "1.16 RHCPExit refused to leave a full block with 'full_block_rhcp'"
                                        + " (checkSpecificConditions :49-50): the exit from a lamp is "
                                        + (full.isPositive() ? "allowed" : "refused with " + full.getWarning()) + ".");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // 1.16 RHCPGettingIntoTheMechanism (TargetRequirement.BLOCK, :157-158) took
    // the block the crosshair was on, which meets blocks by their outlines,
    // and a BLOCK action kept it (StandEntityAction.overrideVanillaMouseTarget
    // works only for an action that takes an entity): RHCP went into a
    // redstone torch under the crosshair, a block without a collision shape,
    // not into the stone under it.
    @GameTest(template = "empty", batch = "rhcp_conductor_outline", timeoutTicks = 80)
    public static void rhcpEntersARedstoneTorchUnderTheCrosshairAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-conductor-outline", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Level level = helper.getLevel();
        List<BlockPos> placed = new ArrayList<>();
        Runnable leave = () -> {
            RhcpAddon.state(user).leaveConductor();
            for (BlockPos pos : placed) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            cleanup(user, power, "RHCP conductor outline GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        BlockPos torch = user.blockPosition().offset(0, 0, 2);
                        if (level.getBlockState(torch.below()).isAir()) {
                            level.setBlockAndUpdate(torch.below(), Blocks.STONE.defaultBlockState());
                            placed.add(torch.below());
                        }
                        level.setBlockAndUpdate(torch, Blocks.REDSTONE_TORCH.defaultBlockState());
                        placed.add(0, torch);
                        helper.assertTrue(level.getBlockState(torch).is(Blocks.REDSTONE_TORCH)
                                        && level.getBlockState(torch).getCollisionShape(level, torch).isEmpty(),
                                "Setup: no redstone torch without a collision shape at " + torch + ".");
                        user.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(
                                torch.getX() + 0.5D, torch.getY() + 0.3D, torch.getZ() + 0.5D));
                        helper.assertTrue(!stand.isManuallyControlled()
                                        && RhcpService.enterConductor(user)
                                        && state.inConductor() && torch.equals(state.conductorPos()),
                                "1.16 RHCP went into the redstone torch under the crosshair (a BLOCK action"
                                        + " kept the pressed block): in a conductor " + state.inConductor()
                                        + " at " + state.conductorPos() + ", the torch at " + torch + ".");
                        assertConductorSpot(helper, stand, torch, "as it entered the redstone torch");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // 1.16 RHCPGettingIntoTheMechanism was pressed at the mouse target, the
    // vanilla pick (4.5 blocks of block reach in Survival, an entity before
    // the block within 3 taken instead, which a BLOCK action refused); with
    // RHCP out a pick that met nothing became RHCP's precision ray from the
    // user, 5 blocks (EntityStandType.clientHitResult), never RHCP's own
    // look; Action.checkRangeAndTarget wanted the block within 10 blocks of the
    // performer, RHCP while it is out, else 'target_too_far'; and its
    // checkSpecificConditions (:24-33) refused a block that is no
    // conductor, silently.
    @GameTest(template = "empty", batch = "rhcp_conductor_press", timeoutTicks = 80)
    public static void rhcpConductorPressTakesTheVanillaPickAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-conductor-press", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Level level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(3)
                .thenExecute(() -> {
                    Cow cow = null;
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        Ability enter = power.getAbility("rhcp_getting_into_the_mechanism");
                        helper.assertTrue(enter != null && !stand.isManuallyControlled()
                                        && RhcpAddon.state(user).powered(),
                                "Setup: no enter press, or RHCP is not powered or is manually controlled.");
                        user.setYRot(0.0F);
                        user.setYHeadRot(0.0F);
                        user.setXRot(0.0F);
                        BlockPos eyeBlock = BlockPos.containing(user.getEyePosition());
                        Vec3 standAt = stand.position();
                        // name, block, distance south, cow before it, RHCP 12 blocks behind, accepted, warning
                        Object[][] rows = {
                                { "a redstone lamp 3 blocks ahead", Blocks.REDSTONE_LAMP, 3, false, false, true, null },
                                { "a redstone lamp 6 blocks ahead, past the 4.5-block pick and the 5-block ray",
                                        Blocks.REDSTONE_LAMP, 6, false, false, false, null },
                                { "stone 3 blocks ahead, no conductor", Blocks.STONE, 3, false, false, false, null },
                                { "a redstone lamp 3 blocks ahead behind a cow 1.5 blocks ahead",
                                        Blocks.REDSTONE_LAMP, 3, true, false, false, null },
                                { "a redstone lamp 3 blocks ahead with RHCP 12 blocks behind its user",
                                        Blocks.REDSTONE_LAMP, 3, false, true, false,
                                        "jojo.message.action_condition.target_too_far" } };
                        for (Object[] row : rows) {
                            BlockPos block = eyeBlock.south((Integer) row[2]);
                            level.setBlockAndUpdate(block, ((net.minecraft.world.level.block.Block) row[1])
                                    .defaultBlockState());
                            if ((Boolean) row[3]) {
                                cow = standingCow(helper, user.getEyePosition().add(0.0D, -0.7D, 1.5D));
                            }
                            if ((Boolean) row[4]) {
                                RhcpService.teleportStand(stand, user.position().add(0.0D, 0.0D, -12.0D));
                            }
                            user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
                            var check = enter.checkConditions(power);
                            String warning = check.getWarning() != null
                                    && check.getWarning().getContents() instanceof TranslatableContents key
                                    ? key.getKey() : null;
                            boolean entered = RhcpService.enterConductor(user);
                            if (check.isPositive() != (Boolean) row[5] || entered != (Boolean) row[5]
                                    || !java.util.Objects.equals(warning, row[6])) {
                                wrong.add(row[0] + ": 1.16 " + ((Boolean) row[5] ? "entered it"
                                        : "refused the press" + (row[6] != null ? " with " + row[6] : " silently"))
                                        + "; the port's press is " + (check.isPositive() ? "accepted" : "refused")
                                        + " (warning " + warning + "), RHCP entered " + entered);
                            }
                            if (entered) {
                                RhcpService.leaveConductor(user);
                            }
                            RhcpService.teleportStand(stand, standAt);
                            if (cow != null) {
                                cow.discard();
                                cow = null;
                            }
                            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
                        }
                        pressBehindAMissedPick(helper, user, power, stand, enter, wrong);
                    }
                    finally {
                        if (cow != null) {
                            cow.discard();
                        }
                        cleanup(user, power, "RHCP conductor press GameTest complete");
                    }
                    helper.assertTrue(wrong.isEmpty(), "RHCP conductor press: " + wrong);
                })
                .thenSucceed();
    }

    // A redstone lamp whose near face is 4.75 blocks from the eyes, past the
    // 4.5-block pick and within the 5-block precision ray: entered with RHCP
    // out, with the aim assist on and off; a lamp only RHCP's own look meets
    // is refused; with RHCP away the pick alone is taken.
    private static void pressBehindAMissedPick(GameTestHelper helper, ServerPlayer user, StandPower power,
            RhcpStandEntity stand, Ability enter, List<String> wrong) {
        Level level = helper.getLevel();
        var settings = user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
        boolean aimAssist = settings.standAimAssist;
        Vec3 userAt = user.position();
        Vec3 standAt = stand.position();
        user.setPos(userAt.x, userAt.y, Math.floor(userAt.z) + 0.25D);
        BlockPos lamp = BlockPos.containing(user.getEyePosition()).south(5);
        BlockPos standLamp = null;
        try {
            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(lamp));
            double face = lamp.getZ() - user.getEyePosition().z;
            helper.assertTrue(face > 4.6D && face < 4.9D && RhcpService.activeStand(user) == stand,
                    "Setup: the lamp's near face is " + face + " blocks from the eyes, RHCP out "
                            + (RhcpService.activeStand(user) == stand) + ".");
            for (boolean assist : new boolean[] { true, false }) {
                settings.standAimAssist = assist;
                boolean accepted = enter.checkConditions(power).isPositive();
                boolean entered = RhcpService.enterConductor(user);
                if (!accepted || !entered || !lamp.equals(RhcpAddon.state(user).conductorPos())) {
                    wrong.add("a redstone lamp 4.75 blocks ahead, aim assist " + assist + ": 1.16 took RHCP's"
                            + " precision ray behind the missed pick and entered it; the port's press is "
                            + (accepted ? "accepted" : "refused") + ", RHCP entered " + entered);
                }
                if (entered) {
                    RhcpService.leaveConductor(user);
                }
                RhcpService.teleportStand(stand, standAt);
            }
            settings.standAimAssist = aimAssist;
            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());

            // RHCP 2 blocks east looking south at a lamp 2 blocks ahead of it, the user looking straight up
            RhcpService.teleportStand(stand, user.position().add(2.0D, 0.0D, 0.0D));
            stand.setYRot(0.0F);
            stand.setYHeadRot(0.0F);
            stand.yHeadRotO = 0.0F;
            stand.setXRot(0.0F);
            stand.xRotO = 0.0F;
            standLamp = BlockPos.containing(stand.getEyePosition()).south(2);
            level.setBlockAndUpdate(standLamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            user.setXRot(-90.0F);
            user.xRotO = -90.0F;
            BlockHitResult standRay = level.clip(new ClipContext(stand.getEyePosition(),
                    stand.getEyePosition().add(stand.getLookAngle().scale(5.0D)), ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, stand));
            helper.assertTrue(standLamp.equals(standRay.getBlockPos()),
                    "Setup: RHCP's own look meets " + standRay.getBlockPos() + ", not the lamp " + standLamp + ".");
            boolean ownLookAccepted = enter.checkConditions(power).isPositive();
            boolean ownLookEntered = RhcpService.enterConductor(user);
            if (ownLookAccepted || ownLookEntered) {
                wrong.add("a lamp only RHCP's own look meets: 1.16's mouse target was the user's, a miss, so"
                        + " the press was refused; the port's press is accepted " + ownLookAccepted
                        + ", RHCP entered " + ownLookEntered);
                if (ownLookEntered) {
                    RhcpService.leaveConductor(user);
                }
            }
            RhcpService.teleportStand(stand, standAt);
            level.setBlockAndUpdate(standLamp, Blocks.AIR.defaultBlockState());
            standLamp = null;

            level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
            user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(lamp));
            RhcpAddon.RHCP.get().forceUnsummon(user, power);
            helper.assertTrue(RhcpService.activeStand(user) == null, "Setup: RHCP is still out.");
            BlockPos away = RhcpService.pressedBlock(user);
            if (away != null) {
                wrong.add("a redstone lamp 4.75 blocks ahead with RHCP away: 1.16 took the vanilla pick alone,"
                        + " a miss; the port presses " + away);
            }
        }
        finally {
            settings.standAimAssist = aimAssist;
            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());
            if (standLamp != null) {
                level.setBlockAndUpdate(standLamp, Blocks.AIR.defaultBlockState());
            }
        }
    }

    // 1.16 RHCPGettingIntoTheMechanism was a TargetRequirement.BLOCK
    // StandEntityAction: its task kept the pressed block
    // (StandEntityTask.makeServerSideTask / keepTarget) and
    // StandEntityTask.rotateStand turned RHCP, not manually controlled, to
    // the block's centre (rotateStandTowardsTarget) through the perform.
    @GameTest(template = "empty", batch = "rhcp_conductor_task_target", timeoutTicks = 80)
    public static void theConductorPressKeepsThePressedBlockAsItsTaskTargetAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-conductor-task", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Level level = helper.getLevel();
        BlockPos[] lamp = {null};
        EntityActionInstance[] pressed = {null};
        Runnable leave = () -> {
            if (RhcpAddon.state(user).inConductor()) {
                RhcpService.leaveConductor(user);
            }
            if (lamp[0] != null) {
                level.setBlockAndUpdate(lamp[0], Blocks.AIR.defaultBlockState());
            }
            cleanup(user, power, "RHCP conductor task target GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        Ability enter = power.getAbility("rhcp_getting_into_the_mechanism");
                        helper.assertTrue(enter != null && !stand.isManuallyControlled()
                                        && RhcpAddon.state(user).powered(),
                                "Setup: no enter press, or RHCP is not powered or is manually controlled.");
                        user.setYRot(0.0F);
                        user.setYHeadRot(0.0F);
                        user.setXRot(0.0F);
                        lamp[0] = BlockPos.containing(user.getEyePosition()).south(3);
                        level.setBlockAndUpdate(lamp[0], Blocks.REDSTONE_LAMP.defaultBlockState());
                        user.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(lamp[0]));
                        BufferingState buffering = press(helper, user, power, enter, (short) 78,
                                InputMethod.CLICK);
                        EntityActionInstance action = activeAction(user);
                        ActionTarget task = action != null ? action.getTaskTarget(level) : ActionTarget.EMPTY;
                        helper.assertTrue(buffering.isActionSuccess && action != null && action.ability == enter
                                        && task.getType() == ActionTarget.TargetType.BLOCK
                                        && lamp[0].equals(task.getBlockPos()),
                                "1.16 RHCPGettingIntoTheMechanism's task kept the pressed block (a BLOCK action):"
                                        + " pressed " + buffering.isActionSuccess + ", action "
                                        + (action != null ? action.ability : null) + ", task target "
                                        + task.getType() + " " + task.getBlockPos() + ", the lamp " + lamp[0] + ".");
                        pressed[0] = action;
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        EntityActionInstance action = activeAction(user);
                        ActionTarget task = action != null ? action.getTaskTarget(level) : ActionTarget.EMPTY;
                        helper.assertTrue(action == pressed[0] && action.getPhase() == ActionPhase.PERFORM
                                        && lamp[0].equals(task.getBlockPos()),
                                "1.16 kept the pressed block through the 2-tick perform: action "
                                        + (action != null ? action.ability + " " + action.getPhase() : null)
                                        + ", task target " + task.getType() + " " + task.getBlockPos() + ".");
                        boolean turned = stand.lookAtTaskTarget();
                        Vec3 toCentre = Vec3.atCenterOf(lamp[0]).subtract(stand.getEyePosition());
                        double facing = toCentre.lengthSqr() < 1.0E-6D ? 1.0D
                                : stand.getViewVector(1.0F).dot(toCentre.normalize());
                        helper.assertTrue(turned && facing > 0.999D,
                                "1.16 StandEntityTask.rotateStand turned RHCP, not manually controlled, to the"
                                        + " pressed block's centre: turned " + turned + ", facing " + facing + ".");
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    try {
                        RhcpStandEntity stand = RhcpService.activeStand(user);
                        RhcpState state = RhcpAddon.state(user);
                        helper.assertTrue(activeAction(user) != pressed[0] && state.inConductor()
                                        && lamp[0].equals(state.conductorPos()),
                                "Setup: the press did not take RHCP into the lamp: in a conductor "
                                        + state.inConductor() + " at " + state.conductorPos() + ".");
                        assertConductorSpot(helper, stand, lamp[0], "after the press");
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    private static void assertConductorSpot(GameTestHelper helper, RhcpStandEntity stand,
            BlockPos block, String when) {
        Vec3 spot = new Vec3(block.getX() + 0.5D, block.getY() + 0.01D, block.getZ() + 0.5D);
        helper.assertTrue(stand.position().distanceTo(spot) < 1.0E-6D,
                "1.16 RHCPGettingIntoTheMechanism.EnterTheBlock put RHCP on the conductor's bottom"
                        + " centre, 0.01 up (:270-271), " + when + ": it is "
                        + stand.position().subtract(spot) + " from there.");
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery :82-90 zapped the heavy's
    // living task target whenever the last punch had hurt: it asked for no
    // power, no reach and nothing about conductors, and an empty battery
    // paid the rest from stamina (RHCPEntity.consumeEnergy :224-226, 15
    // stamina a 10 energy).
    @GameTest(template = "empty", batch = "rhcp_electrocute_gates", timeoutTicks = 80)
    public static void anElectrocuteZapNeedsOnlyAHurtLivingTargetAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-electro-gates", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Cow target = standingCow(helper, user.position().add(0.0D, 0.0D, 2.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            stand.setLastHeavyDamage(1.0F);
            List<String> wrong = new ArrayList<>();

            state.setPotency(0);
            state.setEnergy(0.0F);
            power.setStamina(power.getMaxStamina());
            float stamina = power.getStamina();
            float health = target.getHealth();
            boolean zapped = RhcpService.electrocuteTarget(user, target, false);
            float paid = stamina - power.getStamina();
            if (!zapped || target.getHealth() >= health || Math.abs(paid - 1.5F) > 1.0E-3F) {
                wrong.add("empty battery, potency 0: zapped " + zapped + ", health " + health + " -> "
                        + target.getHealth() + ", stamina paid " + paid + " (1.16: 1.5)");
            }

            state.setPotency(2);
            state.setEnergy(1000.0F);
            Vec3 far = stand.position().add(0.0D, 0.0D, 8.0D);
            target.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
            target.setHealth(target.getMaxHealth());
            health = target.getHealth();
            zapped = RhcpService.electrocuteTarget(user, target, false);
            if (!zapped || target.getHealth() >= health) {
                wrong.add("target " + stand.distanceTo(target) + " blocks from RHCP: zapped " + zapped);
            }

            state.enterConductor(stand.blockPosition());
            target.setHealth(target.getMaxHealth());
            health = target.getHealth();
            zapped = RhcpService.electrocuteTarget(user, target, false);
            state.leaveConductor();
            if (!zapped || target.getHealth() >= health) {
                wrong.add("RHCP in a conductor: zapped " + zapped);
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 RHCPElectrocuteTarget zapped a living target the heavy had hurt, with no power,"
                            + " reach or conductor gate, the battery's lack paid from stamina: " + wrong);
        } finally {
            target.discard();
            cleanup(user, power, "RHCP electrocute gates GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPEntity.readAdditionalSaveData / addAdditionalSaveData
    // (:1265-1275) kept the recent block in the Stand entity's NBT.
    @GameTest(template = "empty", batch = "rhcp_recent_block_nbt", timeoutTicks = 40)
    public static void rhcpSavesItsRecentBlockAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-recent-block", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            BlockPos recent = stand.blockPosition().offset(3, 1, -2);
            stand.setRecentBlock(recent);
            CompoundTag saved = stand.saveWithoutId(new CompoundTag());
            RhcpStandEntity loaded = (RhcpStandEntity) stand.getType().create(helper.getLevel());
            loaded.load(saved);
            helper.assertTrue(loaded.recentBlock().equals(recent),
                    "1.16 RHCPEntity saved its recent block (RecentBlock, :1265-1275): RHCP reloaded with "
                            + loaded.recentBlock() + ", saved " + recent + ".");
            stand.setRecentBlock(null);
            CompoundTag none = stand.saveWithoutId(new CompoundTag());
            RhcpStandEntity fresh = (RhcpStandEntity) stand.getType().create(helper.getLevel());
            fresh.load(none);
            helper.assertTrue(!none.contains("RecentBlock")
                            && fresh.recentBlock().equals(fresh.blockPosition()),
                    "1.16 saved no recent block when RHCP had none, and getRecentBlock fell back to its"
                            + " own block: reloaded " + fresh.recentBlock() + ".");
        } finally {
            cleanup(user, power, "RHCP recent block GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPChaseTarget did not turn RHCP: StandEntity.tick did, every
    // tick of the chase. Without a task target and not manually controlled,
    // RHCPEntity.defaultRotation took its user's look; under manual control
    // it faced the lock.
    @GameTest(template = "empty", batch = "rhcp_chase_faces", timeoutTicks = 80)
    public static void aChasingRhcpTurnsEveryTickAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-chase-face", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Cow lock = standingCow(helper, user.position().add(6.0D, 0.0D, 3.0D));
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            RhcpState state = RhcpAddon.state(user);
            state.setEnergy(2000.0F);
            state.setPotency(4);
            state.setTargetId(lock.getId());
            stand.setStandCount(20);
            turnIdleUser(user, 30.0F, 30.0F);
            stand.setChasing(true);
            turnStand(stand, 180.0F);
            stand.updatePosition(user);
            helper.assertTrue(stand.isChasing()
                            && Math.abs(Mth.wrapDegrees(stand.getYRot() - user.getYRot())) < 0.01F,
                    "1.16 RHCPEntity.defaultRotation turned a chasing RHCP with no task target"
                            + " along its user's look (yaw " + user.getYRot() + "); chasing "
                            + stand.isChasing() + ", yaw " + stand.getYRot() + ".");
            StandEntityManualControlToggle.on(helper.getLevel(), stand);
            stand.setChasing(true);
            turnStand(stand, 180.0F);
            stand.updatePosition(user);
            helper.assertTrue(stand.isChasing() && stand.isManuallyControlled(),
                    "Setup: the manually controlled RHCP stopped chasing.");
            assertFaces(helper, stand, lock,
                    "1.16 RHCPEntity.defaultRotation faced the lock a manually controlled RHCP chases");
        } finally {
            lock.discard();
            cleanup(user, power, "RHCP chase facing GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 GameplayEventHandler: a dodging RHCP 1.5 to 3 points faster than
    // the Stand that hit it dashes behind it with Shift, then
    // setRot(attacker.yRot, rhcp.xRot).
    @GameTest(template = "empty", batch = "rhcp_dodge_turn", timeoutTicks = 60)
    public static void aDodgeDashBehindTakesTheAttackersYawAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-dodge-turn", new BlockPos(1, 2, 1));
        ServerPlayer spUser = mockPlayer(helper, "rhcp-dodge-turn-sp", new BlockPos(4, 2, 1));
        StandPower power = summonRhcp(helper, user);
        spUser.setGameMode(GameType.SURVIVAL);
        StandPower spPower = StandPower.get(spUser);
        helper.assertTrue(spPower != null, "The mock player has no Stand power attachment.");
        spPower.setStand(ModStands.STAR_PLATINUM.get());
        spPower.setStamina(spPower.getMaxStamina());
        helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(spUser, spPower),
                "Star Platinum could not be summoned.");
        helper.runAfterDelay(5, () -> {
            try {
                RhcpStandEntity stand = RhcpService.activeStand(user);
                StandEntity starPlatinum = spPower.getSummonedStandEntity();
                helper.assertTrue(stand != null && starPlatinum != null,
                        "RHCP or Star Platinum left before the dodge check.");
                RhcpState state = RhcpAddon.state(user);
                state.setEnergy(2000.0F);
                state.setPotency(4);
                double difference = RhcpService.speedDifference(stand, starPlatinum);
                helper.assertTrue(difference > 1.5D && difference <= 3.0D,
                        "Setup: RHCP is not 1.5 to 3 points faster:" + speeds(stand, starPlatinum));
                StandEntityManualControlToggle.on(helper.getLevel(), stand);
                StandManualInput.setServerShift(user, true);
                Ability dodge = power.getAbility("rhcp_dodge");
                EntityActionInstance dodging = ((EntityActionType) dodge)
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(
                        dodging, user, SyncType.TRACKING_AND_SELF);
                starPlatinum.setYRot(90.0F);
                turnStand(stand, 0.0F);
                DamageSource hit = new DamageSource(
                        DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK),
                        starPlatinum);
                helper.assertTrue(RhcpService.dodgeStandHit(user, stand, hit)
                                && RhcpAbility.modeOf(stand.getCurStandAction()) == RhcpAbility.Mode.DODGE,
                        "Setup: RHCP did not dodge Star Platinum's hit with a dash.");
                helper.assertTrue(Math.abs(Mth.wrapDegrees(stand.getYRot() - 90.0F)) < 0.01F,
                        "1.16 turned RHCP to Star Platinum's yaw 90 after its dodge dash behind it;"
                                + " it faces yaw " + stand.getYRot() + ".");
            } finally {
                StandManualInput.setServerShift(user, false);
                cleanup(user, power, "RHCP dodge turn GameTest complete");
                cleanup(spUser, spPower, "RHCP dodge turn GameTest complete");
            }
            helper.succeed();
        });
    }

    // 1.16 GameplayEventHandler:126-131: after that dash behind, the Stand
    // that hit RHCP lost its task target (setTaskTarget(new ActionTarget(null)))
    // and a mob holding integrated_stand lost its target (setTarget(null)).
    @GameTest(template = "empty", batch = "rhcp_dodge_drop", timeoutTicks = 60)
    public static void aDodgeDashBehindDropsTheAttackersTargetAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-dodge-drop", new BlockPos(1, 2, 1));
        ServerPlayer spUser = mockPlayer(helper, "rhcp-dodge-drop-sp", new BlockPos(4, 2, 1));
        StandPower power = summonRhcp(helper, user);
        spUser.setGameMode(GameType.SURVIVAL);
        StandPower spPower = StandPower.get(spUser);
        helper.assertTrue(spPower != null, "The mock player has no Stand power attachment.");
        spPower.setStand(ModStands.STAR_PLATINUM.get());
        spPower.setStamina(spPower.getMaxStamina());
        helper.assertTrue(ModStands.STAR_PLATINUM.get().summon(spUser, spPower),
                "Star Platinum could not be summoned.");
        Cow cow = standingCow(helper, spUser.position().add(1.5D, 0.0D, 0.0D));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 5));
        helper.runAfterDelay(5, () -> {
            try {
                RhcpStandEntity stand = RhcpService.activeStand(user);
                StandEntity starPlatinum = spPower.getSummonedStandEntity();
                helper.assertTrue(stand != null && starPlatinum != null,
                        "RHCP or Star Platinum left before the dodge check.");
                starPlatinum.summonLockTicks = 0;
                RhcpState state = RhcpAddon.state(user);
                state.setEnergy(2000.0F);
                state.setPotency(4);
                StandEntityManualControlToggle.on(helper.getLevel(), stand);
                StandManualInput.setServerShift(user, true);
                Ability dodge = power.getAbility("rhcp_dodge");

                LivingComponentAction.getComponent(spUser).entityAim.setTarget(new ActionTarget(cow));
                LivingComponentAction.getComponent(starPlatinum).entityAim.setTarget(new ActionTarget(cow));
                EntityActionInstance punch = ((EntityActionType) spPower.getAbility("punch"))
                        .initActionOnAbilityUse(helper.getLevel(), spUser, starPlatinum, null);
                LivingComponentAction.getComponent(starPlatinum).setAction(punch, spUser, SyncType.NO_SYNC);
                helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getEntity() == cow,
                        "Setup: Star Platinum's punch holds " + punch.getTaskTarget(helper.getLevel())
                                + ", not the cow.");
                double difference = RhcpService.speedDifference(stand, starPlatinum);
                helper.assertTrue(difference > 1.5D && difference <= 3.0D,
                        "Setup: RHCP is not 1.5 to 3 points faster:" + speeds(stand, starPlatinum));
                LivingComponentAction.getComponent(stand).setAction(((EntityActionType) dodge)
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null),
                        user, SyncType.TRACKING_AND_SELF);
                helper.assertTrue(RhcpService.dodgeStandHit(user, stand, new DamageSource(
                                DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK), starPlatinum))
                                && RhcpAbility.modeOf(stand.getCurStandAction()) == RhcpAbility.Mode.DODGE,
                        "Setup: RHCP did not dodge Star Platinum's hit with a dash.");
                helper.assertTrue(punch.getTaskTarget(helper.getLevel()).getType()
                                == ActionTarget.TargetType.EMPTY,
                        "1.16 GameplayEventHandler dropped the task target of the Stand RHCP dashed behind;"
                                + " its punch still holds " + punch.getTaskTarget(helper.getLevel()) + ".");

                // 1.16 RHCPDodge.noStandSpeed: a mob's speed is 16x its movement speed.
                zombie.addEffect(new MobEffectInstance(
                        ModStatusEffects.INTEGRATED_STAND, 200));
                zombie.getAttribute(Attributes.MOVEMENT_SPEED)
                        .setBaseValue((stand.getAttackSpeed() - 2.0D) / 16.0D);
                zombie.setTarget(user);
                double mobDifference = RhcpService.speedDifference(stand, zombie);
                helper.assertTrue(mobDifference > 1.5D && mobDifference <= 3.0D
                                && zombie.getTarget() == user,
                        "Setup: RHCP is not 1.5 to 3 points faster than the zombie (" + mobDifference
                                + ") or the zombie does not target its user.");
                stand.invulnerableTime = 0;
                user.invulnerableTime = 0;
                LivingComponentAction.getComponent(stand).setAction(((EntityActionType) dodge)
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null),
                        user, SyncType.TRACKING_AND_SELF);
                helper.assertTrue(RhcpService.dodgeStandHit(user, stand, new DamageSource(
                                DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK), zombie))
                                && RhcpAbility.modeOf(stand.getCurStandAction()) == RhcpAbility.Mode.DODGE,
                        "Setup: RHCP did not dodge the zombie's hit with a dash.");
                helper.assertTrue(zombie.getTarget() == null,
                        "1.16 GameplayEventHandler dropped the target of the mob RHCP dashed behind;"
                                + " the zombie still targets " + zombie.getTarget() + ".");
            } finally {
                StandManualInput.setServerShift(user, false);
                cow.discard();
                zombie.discard();
                cleanup(user, power, "RHCP dodge drop GameTest complete");
                cleanup(spUser, spPower, "RHCP dodge drop GameTest complete");
            }
            helper.succeed();
        });
    }

    private static Cow standingCow(GameTestHelper helper, Vec3 pos) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Could not make a cow.");
        cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        cow.setNoAi(true);
        cow.setNoGravity(true);
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static void turnStand(StandEntity stand, float yaw) {
        stand.setYRot(yaw);
        stand.setXRot(0.0F);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
    }

    private static void assertFaces(GameTestHelper helper, StandEntity stand,
            LivingEntity target, String claim) {
        Vec3 to = target.getEyePosition().subtract(stand.getEyePosition()).normalize();
        Vec3 look = stand.getLookAngle();
        helper.assertTrue(look.dot(to) > 0.999D,
                claim + "; it looks along " + look + " instead of " + to + ".");
    }

    private static void invokeDropAim(LivingEntity target) {
        try {
            var drop = RhcpPunchAbility.RhcpPunch.class.getDeclaredMethod("dropAim", LivingEntity.class);
            drop.setAccessible(true);
            drop.invoke(null, target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not invoke the production dropAim", failure);
        }
    }

    private static void invokeHitAttempt(EntityActionInstance action, LivingEntity target,
            Level level, StandEntity stand) {
        try {
            var hit = RhcpPunchAbility.RhcpPunch.class.getDeclaredMethod(
                    "hitEntity", ActionTarget.class, Level.class, StandEntity.class);
            hit.setAccessible(true);
            hit.invoke(action, new ActionTarget(target), level, stand);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not invoke the production hit attempt", failure);
        }
    }

    private RhcpGameTests() {}

    // 1.16 RHCPDodge: RHCP (speed 14, +1.25 a potency level past the
    // first, Resolve adds nothing) only dodges a Stand hit it is at most
    // half a point slower than, and counters only when more than half a
    // point faster. Star Platinum is 16 (19 at max Resolve), so potency 1-2
    // take the hit and potency 4 counters a Resolve 0 Star Platinum.
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void potencyOneDodgeDoesNotCounterStarPlatinum(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-dodge", new BlockPos(1, 2, 1));
        ServerPlayer spUser = mockPlayer(
                helper, "rhcp-dodge-sp", new BlockPos(4, 2, 1));
        user.setGameMode(GameType.SURVIVAL);
        // 1.16 onNewPowerGiven: a creative user (the GameTest default) gets
        // max Resolve, Star Platinum 19; survival keeps its base 16.
        spUser.setGameMode(GameType.SURVIVAL);
        StandPower power = StandPower.get(user);
        StandPower spPower = StandPower.get(spUser);
        helper.assertTrue(power != null && spPower != null,
                "The mock players have no Stand power attachment.");
        if (power == null || spPower == null) {
            return;
        }
        RhcpState state = RhcpAddon.state(user);
        state.setEnergy(2000.0F);
        state.setPotency(1);
        power.setStand(RhcpAddon.RHCP.get());
        // Max Resolve: RHCP must still take its growth back out.
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(RhcpAddon.RHCP.get().summon(user, power),
                "RHCP could not be summoned.");
        spPower.setStand(ModStands.STAR_PLATINUM.get());
        spPower.setStamina(spPower.getMaxStamina());
        helper.assertTrue(
                ModStands.STAR_PLATINUM.get().summon(spUser, spPower),
                "Star Platinum could not be summoned.");

        // A few ticks let both Stands apply their stat modifiers.
        helper.runAfterDelay(5, () -> {
            try {
                checkDodge(helper, user, power, spPower);
            }
            finally {
                cleanup(user, power, "RHCP dodge GameTest complete");
                cleanup(spUser, spPower, "RHCP dodge GameTest complete");
            }
            helper.succeed();
        });
    }

    private static void checkDodge(
            GameTestHelper helper,
            ServerPlayer user,
            StandPower power,
            StandPower spPower) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        StandEntity starPlatinum = spPower.getSummonedStandEntity();
        helper.assertTrue(stand != null && starPlatinum != null
                        && starPlatinum.isAlive(),
                "RHCP or Star Platinum left before the dodge check.");
        if (stand == null || starPlatinum == null) {
            return;
        }
        RhcpState state = RhcpAddon.state(user);
        state.setEnergy(2000.0F);
        state.setPotency(1);
        double baseSpeed = RhcpAddon.RHCP.get().getStandStats()
                .getBaseAttackSpeed();
        helper.assertTrue(
                Math.abs(stand.getAttackSpeed()
                        - baseSpeed * stand.getStandEfficiency()) < 1.0E-3D,
                "Resolve changed RHCP's potency 1 speed:"
                        + speeds(stand, starPlatinum));
        double spBaseSpeed = ModStands.STAR_PLATINUM.get().getStandStats()
                .getBaseAttackSpeed();
        helper.assertTrue(
                Math.abs(starPlatinum.getAttackSpeed()
                        - spBaseSpeed * starPlatinum.getStandEfficiency())
                        < 1.0E-3D,
                "Star Platinum is not at its Resolve 0 speed:"
                        + speeds(stand, starPlatinum));

        Ability dodge = power.getAbility("rhcp_dodge");
        helper.assertTrue(dodge instanceof EntityActionType,
                "RHCP has no rhcp_dodge action.");
        if (!(dodge instanceof EntityActionType dodgeType)) {
            return;
        }
        // 1.16 RHCPDodge.checkSpecificConditions: only under manual
        // control or with RHCP fixed away from its user.
        if (!stand.isManuallyControlled() && stand.followingUserIsEnabled()) {
            helper.assertFalse(dodge.checkConditions(power).isPositive(),
                    "RHCP could dodge while following its user.");
        }
        StandEntityManualControlToggle.on(helper.getLevel(), stand);
        helper.assertTrue(stand.isManuallyControlled(),
                "RHCP did not enter manual control.");
        helper.assertTrue(dodge.checkConditions(power).isPositive(),
                "RHCP under manual control could not start its dodge.");
        EntityActionInstance action = dodgeType.initActionOnAbilityUse(
                helper.getLevel(), user, stand, null);
        LivingComponentAction.getComponent(stand).setAction(
                action, user, SyncType.TRACKING_AND_SELF);
        helper.assertTrue(
                RhcpAbility.modeOf(stand.getCurStandAction())
                        == RhcpAbility.Mode.DODGE,
                "RHCP's dodge did not start.");

        DamageSource hit = new DamageSource(
                DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK),
                starPlatinum);
        for (int potency = 1; potency <= 2; potency++) {
            state.setPotency(potency);
            helper.assertFalse(
                    RhcpService.dodgeStandHit(user, stand, hit),
                    "At potency " + potency + " RHCP dodged Star "
                            + "Platinum's hit:" + speeds(stand, starPlatinum));
            helper.assertTrue(
                    RhcpAbility.modeOf(stand.getCurStandAction())
                            == RhcpAbility.Mode.DODGE,
                    "At potency " + potency + " RHCP answered Star "
                            + "Platinum with "
                            + RhcpAbility.modeOf(stand.getCurStandAction())
                            + ":" + speeds(stand, starPlatinum));
        }

        state.setPotency(4);
        helper.assertTrue(RhcpService.dodgeStandHit(user, stand, hit),
                "At potency 4 RHCP did not dodge Star Platinum's hit:"
                        + speeds(stand, starPlatinum));
        helper.assertTrue(
                RhcpAbility.modeOf(stand.getCurStandAction())
                                == RhcpAbility.Mode.COUNTERATTACK
                        && state.targetId() == starPlatinum.getId(),
                "At potency 4 RHCP did not counter Star Platinum:"
                        + speeds(stand, starPlatinum));
    }

    private static final short BOOST_KEY = 91;
    private static final short REDUCE_KEY = 92;

    // 1.16 RHCPReduceConsumption: Shift on the Boost slot is a click that
    // takes one potency level off; it needs potency above 1.
    @GameTest(template = "empty", batch = "rhcp_reduce_consumption",
            timeoutTicks = 100)
    public static void shiftBoostSlotReducesPotencyOnce(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-reduce", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        checkBoostSlot(helper, power);
        Ability reduce = power.getAbility("rhcp_reduce_consumption");
        helper.assertTrue(reduce instanceof RhcpAbility,
                "RHCP has no rhcp_reduce_consumption ability.");
        RhcpState state = RhcpAddon.state(user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    state.setEnergy(1000.0F);
                    state.setPotency(1);
                    helper.assertFalse(usable(power, reduce),
                            "Reduce Consumption was usable at potency 1.");
                    state.setPotency(3);
                    RhcpService.sync(user);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    BufferingState buffering = press(helper, user, power,
                            reduce, REDUCE_KEY, InputMethod.CLICK);
                    helper.assertTrue(buffering.isActionSuccess
                                    && RhcpAbility.modeOf(activeAction(user))
                                            == RhcpAbility.Mode.REDUCE_CONSUMPTION,
                            "Shift+click did not start Reduce Consumption.");
                    helper.assertTrue(power.isAbilityOnCooldown(
                                    "rhcp_reduce_consumption"),
                            "Reduce Consumption set no cooldown.");
                })
                .thenWaitUntil(() -> helper.assertTrue(state.potency() == 2,
                        "Reduce Consumption did not lower potency 3 to 2: "
                                + state.potency()))
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(state.potency() == 2 && !state.boosting(),
                            "Reduce Consumption ended at potency "
                                    + state.potency() + ", boosting "
                                    + state.boosting() + ".");
                    helper.assertTrue(RhcpAbility.modeOf(activeAction(user))
                                    != RhcpAbility.Mode.REDUCE_CONSUMPTION,
                            "Reduce Consumption never ended.");
                    cleanup(user, power, "RHCP reduce GameTest complete");
                })
                .thenSucceed();
    }

    // 1.16 RHCPBoost: held, needs energy above 100 and potency below 5,
    // adds a level on held ticks 0, 5, 10 and spends no energy.
    @GameTest(template = "empty", batch = "rhcp_boost", timeoutTicks = 120)
    public static void heldBoostRaisesPotencyEveryFiveTicks(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-boost", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Ability boost = power.getAbility("rhcp_boost");
        helper.assertTrue(boost instanceof RhcpAbility,
                "RHCP has no rhcp_boost ability.");
        RhcpState state = RhcpAddon.state(user);
        List<Integer> potencies = new ArrayList<>();
        List<Boolean> boosting = new ArrayList<>();
        float[] energyAtPress = {0.0F};
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    state.setEnergy(100.0F);
                    state.setPotency(3);
                    helper.assertFalse(usable(power, boost),
                            "Boost was usable at 100 energy.");
                    state.setEnergy(1000.0F);
                    state.setPotency(5);
                    helper.assertFalse(usable(power, boost),
                            "Boost was usable at potency 5.");
                    state.setPotency(1);
                    RhcpService.sync(user);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    energyAtPress[0] = state.energy();
                    press(helper, user, power, boost, BOOST_KEY,
                            InputMethod.HOLD);
                    helper.assertTrue(RhcpAbility.modeOf(activeAction(user))
                                    == RhcpAbility.Mode.BOOST,
                            "Holding the Boost slot did not start Boost.");
                })
                // One sample a tick; the fourth rise (held tick 15) is
                // past the window.
                .thenExecuteFor(14, () -> {
                    potencies.add(state.potency());
                    boosting.add(state.boosting());
                })
                .thenExecute(() -> {
                    checkBoostRises(helper, potencies, boosting);
                    helper.assertTrue(state.energy() >= energyAtPress[0],
                            "Boost spent energy: " + energyAtPress[0]
                                    + " -> " + state.energy());
                    AbilityInput.keyRelease(BOOST_KEY, user);
                })
                .thenIdle(8)
                .thenExecute(() -> {
                    helper.assertTrue(!state.boosting()
                                    && RhcpAbility.modeOf(activeAction(user))
                                            != RhcpAbility.Mode.BOOST,
                            "Boost kept running after its key was let go.");
                    helper.assertTrue(state.potency() == 4,
                            "Letting Boost go left potency "
                                    + state.potency() + ", not 4.");
                    cleanup(user, power, "RHCP boost GameTest complete");
                })
                .thenSucceed();
    }

    private static final short RECHARGE_KEY = 93;
    private static final short DODGE_KEY = 94;

    // 1.16 PowerBaseImpl.tickHeldAction re-ran RHCPRecharge's conditions on
    // every held tick: the hold ended once the battery was full or no source
    // was left near RHCP.
    @GameTest(template = "empty", batch = "rhcp_recharge_recheck",
            timeoutTicks = 120)
    public static void heldRechargeStopsWithoutASourceOrWithAFullBatteryAsIn116(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-recharge", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Ability recharge = power.getAbility("rhcp_recharge");
        helper.assertTrue(recharge instanceof RhcpAbility,
                "RHCP has no rhcp_recharge ability.");
        RhcpState state = RhcpAddon.state(user);
        float[] energyAtPress = {0.0F};
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    user.setItemInHand(InteractionHand.OFF_HAND,
                            new ItemStack(Items.REDSTONE_TORCH));
                    state.setEnergy(1000.0F);
                    RhcpService.sync(user);
                    energyAtPress[0] = state.energy();
                    press(helper, user, power, recharge, RECHARGE_KEY,
                            InputMethod.HOLD);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(holding(user, RhcpAbility.Mode.RECHARGE)
                                    && state.energy() > energyAtPress[0],
                            "Setup: the held Recharge is not charging from the"
                                    + " torch: " + phaseOf(user) + ", energy "
                                    + energyAtPress[0] + " -> " + state.energy());
                    user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(holding(user, RhcpAbility.Mode.RECHARGE),
                            "1.16 ended a held Recharge once no source was near"
                                    + " RHCP; it still holds: " + phaseOf(user));
                    AbilityInput.keyRelease(RECHARGE_KEY, user);
                })
                .thenWaitUntil(() -> helper.assertTrue(activeAction(user) == null,
                        "The stopped Recharge never ended: " + phaseOf(user)))
                .thenExecute(() -> {
                    user.setItemInHand(InteractionHand.OFF_HAND,
                            new ItemStack(Items.REDSTONE_TORCH));
                    press(helper, user, power, recharge, RECHARGE_KEY,
                            InputMethod.HOLD);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(holding(user, RhcpAbility.Mode.RECHARGE),
                            "Setup: the second held Recharge did not start: "
                                    + phaseOf(user));
                    state.setEnergy(RhcpState.MAX_ENERGY);
                    RhcpService.sync(user);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(holding(user, RhcpAbility.Mode.RECHARGE),
                            "1.16 ended a held Recharge once the battery was"
                                    + " full; it still holds: " + phaseOf(user));
                    AbilityInput.keyRelease(RECHARGE_KEY, user);
                    user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    cleanup(user, power, "RHCP recharge GameTest complete");
                })
                .thenSucceed();
    }

    // 1.16 PowerBaseImpl.tickHeldAction re-ran RHCPDodge's conditions on every
    // held tick: a hurt user (hurtResistantTime) ended the hold.
    @GameTest(template = "empty", batch = "rhcp_dodge_recheck",
            timeoutTicks = 100)
    public static void heldDodgeStopsWhenItsUserIsHurtAsIn116(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-dodge-hold", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Ability dodge = power.getAbility("rhcp_dodge");
        helper.assertTrue(dodge instanceof RhcpAbility,
                "RHCP has no rhcp_dodge ability.");
        RhcpState state = RhcpAddon.state(user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    RhcpStandEntity stand = RhcpService.activeStand(user);
                    state.setEnergy(2000.0F);
                    state.setPotency(3);
                    RhcpService.sync(user);
                    StandEntityManualControlToggle.on(helper.getLevel(), stand);
                    user.invulnerableTime = 0;
                    stand.invulnerableTime = 0;
                    press(helper, user, power, dodge, DODGE_KEY,
                            InputMethod.HOLD);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(holding(user, RhcpAbility.Mode.DODGE),
                            "Setup: the held Dodge did not keep going: "
                                    + phaseOf(user));
                    // Out of the world: the mock player's spawn protection
                    // refuses an ordinary hit.
                    helper.assertTrue(user.hurt(user.damageSources().fellOutOfWorld(), 1.0F)
                                    && user.invulnerableTime > 0,
                            "Setup: the user was not hurt.");
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(holding(user, RhcpAbility.Mode.DODGE),
                            "1.16 ended a held Dodge once its user had been"
                                    + " hurt; it still holds: " + phaseOf(user));
                    AbilityInput.keyRelease(DODGE_KEY, user);
                    cleanup(user, power, "RHCP dodge hold GameTest complete");
                })
                .thenSucceed();
    }

    private static final short HELD_PUNCH_KEY = 95;

    // 1.16 RHCPRecharge, RHCPBoost and RHCPDodge were holdType Stand actions
    // with getStandRecoveryTicks 3 and isCancelable "phase == RECOVERY ||
    // super": a punch pressed during the hold was queued and went off in the
    // recovery; Recharge pressed again in its own recovery was refused
    // (canClickDuringTask); another action of the power that went off ended
    // the hold (PowerBaseImpl.onClickAction stopHeldAction(false)).
    @GameTest(template = "empty", batch = "rhcp_held_hold_type",
            timeoutTicks = 120)
    public static void aPunchPressedDuringAHeldRechargeGoesOffInItsRecoveryAsIn116(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-hold-type", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        Ability recharge = power.getAbility("rhcp_recharge");
        Ability punch = power.getAbility("rhcp_punch");
        helper.assertTrue(recharge instanceof RhcpAbility && punch != null,
                "RHCP has no rhcp_recharge or rhcp_punch ability.");
        RhcpState state = RhcpAddon.state(user);
        var input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    RhcpStandEntity stand = RhcpService.activeStand(user);
                    for (String name : List.of("rhcp_boost", "rhcp_dodge", "rhcp_exit")) {
                        EntityActionInstance action = ((RhcpAbility) power.getAbility(name))
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        boolean held = !name.equals("rhcp_exit");
                        helper.assertTrue(action.isHoldTypeStandAction() == held
                                        && action.refusesOwnAbilityPressedAgain() == held,
                                "1.16 " + name + (held ? " was" : " was not") + " a holdType Stand action that was"
                                        + " not chainable: hold-type " + action.isHoldTypeStandAction()
                                        + ", refuses its own press " + action.refusesOwnAbilityPressedAgain());
                    }
                    user.setItemInHand(InteractionHand.OFF_HAND,
                            new ItemStack(Items.REDSTONE_TORCH));
                    state.setEnergy(1000.0F);
                    RhcpService.sync(user);
                    press(helper, user, power, recharge, RECHARGE_KEY,
                            InputMethod.HOLD);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(holding(user, RhcpAbility.Mode.RECHARGE),
                            "Setup: the held Recharge is not charging: " + phaseOf(user));
                    helper.assertTrue(activeAction(user).isHoldTypeStandAction(),
                            "1.16 RHCPRecharge was a holdType Stand action, ended by another action of the power"
                                    + " that went off: the Recharge must be a hold-type Stand action");
                    AbilityInput.keyRelease(RECHARGE_KEY, user);
                    helper.assertTrue(activeAction(user) != null
                                    && activeAction(user).getPhase() == ActionPhase.RECOVERY,
                            "Setup: the let-go Recharge is not in its recovery: " + phaseOf(user));
                    helper.assertFalse(usable(power, recharge),
                            "1.16 canClickDuringTask refused Recharge pressed again during its own recovery;"
                                    + " it was usable");
                })
                .thenWaitUntil(() -> helper.assertTrue(activeAction(user) == null,
                        "The let-go Recharge never ended: " + phaseOf(user)))
                .thenExecute(() -> {
                    press(helper, user, power, recharge, RECHARGE_KEY,
                            InputMethod.HOLD);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    EntityActionInstance held = activeAction(user);
                    helper.assertTrue(holding(user, RhcpAbility.Mode.RECHARGE),
                            "Setup: the second held Recharge is not charging: " + phaseOf(user));
                    BufferingState queued = press(helper, user, power, punch, HELD_PUNCH_KEY,
                            InputMethod.CLICK);
                    helper.assertTrue(queued.shouldBuffer && !queued.isActionSuccess && activeAction(user) == held
                                    && held.getPhase() == ActionPhase.PERFORM,
                            "1.16 queued a punch pressed during the held Recharge (isCancelable: a recovery of"
                                    + " 3): buffered " + queued.shouldBuffer + ", started " + queued.isActionSuccess
                                    + ", RHCP's action " + phaseOf(user));
                    AbilityInput.keyRelease(HELD_PUNCH_KEY, user);
                    AbilityInput.keyRelease(RECHARGE_KEY, user);
                    helper.assertTrue(held.getPhase() == ActionPhase.RECOVERY,
                            "Setup: the let-go Recharge is not in its recovery: " + held.getPhase());
                    input.inputBuffer.tickInputBuffer(input);
                    EntityActionInstance now = activeAction(user);
                    helper.assertTrue(now != held && now != null && now.ability == punch,
                            "1.16 let the queued punch through the Recharge's recovery (isCancelable: phase =="
                                    + " RECOVERY): RHCP's action " + phaseOf(user) + ", the Recharge over "
                                    + held.isOver());
                    user.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    cleanup(user, power, "RHCP hold-type GameTest complete");
                })
                .thenSucceed();
    }

    // 1.16 094 InitStands:75-88 and the action classes: RHCPRecharge's
    // recovery is 3 (:210-211); rhcp_getting_into_the_mechanism and rhcp_exit
    // standPerformDuration(2) with no windup, recovery or cooldown;
    // rhcp_reduce_consumption and rhcp_electric_dash set no windup or
    // recovery; rhcp_chase_target and rhcp_electrocute_target were
    // StandEntityActionModifiers without a cooldown.
    @GameTest(template = "empty", batch = "rhcp_phases", timeoutTicks = 80)
    public static void rhcpPhasesAndCooldownsAreTheDonorsAsIn116(GameTestHelper helper) {
        ServerPlayer user = mockPlayer(helper, "rhcp-phases", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenExecute(() -> {
                    RhcpStandEntity stand = RhcpService.activeStand(user);
                    // name, windup, perform, recovery
                    for (Object[] row : List.<Object[]>of(
                            new Object[] {"rhcp_recharge", 0.0F, 999999.0F, 3.0F},
                            new Object[] {"rhcp_getting_into_the_mechanism", 0.0F, 2.0F, 0.0F},
                            new Object[] {"rhcp_exit", 0.0F, 2.0F, 0.0F},
                            new Object[] {"rhcp_reduce_consumption", 0.0F, 1.0F, 0.0F},
                            new Object[] {"rhcp_electric_dash", 0.0F, 1.0F, 0.0F})) {
                        String name = (String) row[0];
                        EntityActionInstance action = ((RhcpAbility) power.getAbility(name))
                                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                        float windup = action.phasesLength.getFloat(ActionPhase.WINDUP);
                        float perform = action.phasesLength.getFloat(ActionPhase.PERFORM);
                        float recovery = action.phasesLength.getFloat(ActionPhase.RECOVERY);
                        helper.assertTrue(windup == (float) row[1] && perform == (float) row[2]
                                        && recovery == (float) row[3],
                                "1.16 " + name + " ran windup " + row[1] + ", perform " + row[2] + ", recovery "
                                        + row[3] + "; the port runs " + windup + ", " + perform + ", " + recovery);
                    }
                    for (String name : List.of("rhcp_getting_into_the_mechanism", "rhcp_exit",
                            "rhcp_chase_target", "rhcp_electrocute_target")) {
                        int cooldown = power.getAbility(name).getCooldown(power, -1);
                        helper.assertTrue(cooldown == 0,
                                "1.16 " + name + " had no cooldown, the port sets " + cooldown);
                    }
                    // 1.16 :83, :87, :88 cooldown(5, 5), cooldown(10, 40), cooldown(2400, 2400): the first part is
                    // the technical one, which Resolve (StandAction's multiplier 0) leaves.
                    for (Object[] row : List.<Object[]>of(
                            new Object[] {"rhcp_reduce_consumption", 5, 5},
                            new Object[] {"rhcp_electric_dash", 10, 40},
                            new Object[] {"rhcp_drag_into_line", 2400, 2400})) {
                        Ability ability = power.getAbility((String) row[0]);
                        int technical = (int) row[1];
                        int additional = (int) row[2];
                        user.removeEffect(ModStatusEffects.RESOLVE);
                        int plain = ability.getCooldown(power, -1);
                        user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 100));
                        int inResolve = ability.getCooldown(power, -1);
                        user.removeEffect(ModStatusEffects.RESOLVE);
                        int reported = ability.getCooldownTechnical(power);
                        helper.assertTrue(reported == technical && plain == technical + additional
                                        && inResolve == technical,
                                "1.16 " + row[0] + " was cooldown(" + technical + ", " + additional + "): technical "
                                        + technical + ", " + (technical + additional) + " ticks, " + technical
                                        + " in Resolve; the port reports technical " + reported + ", " + plain
                                        + " ticks, " + inResolve + " in Resolve");
                    }
                    cleanup(user, power, "RHCP phases GameTest complete");
                })
                .thenSucceed();
    }

    private static boolean holding(ServerPlayer user, RhcpAbility.Mode mode) {
        EntityActionInstance action = activeAction(user);
        return RhcpAbility.modeOf(action) == mode && !action.isOver()
                && action.getPhase() == ActionPhase.PERFORM;
    }

    private static String phaseOf(ServerPlayer user) {
        EntityActionInstance action = activeAction(user);
        return action == null ? "no action"
                : RhcpAbility.modeOf(action) + " " + action.getPhase();
    }

    // From potency 1: +1 within the first samples, then +1 exactly every
    // 5 samples, to 4; boosting stays on from the first rise.
    private static void checkBoostRises(GameTestHelper helper,
            List<Integer> potencies, List<Boolean> boosting) {
        List<Integer> rises = new ArrayList<>();
        int previous = 1;
        for (int i = 0; i < potencies.size(); i++) {
            int potency = potencies.get(i);
            helper.assertTrue(potency == previous || potency == previous + 1,
                    "Boost moved potency " + previous + " to " + potency
                            + ": " + potencies);
            if (potency > previous) {
                rises.add(i);
            }
            previous = potency;
        }
        helper.assertTrue(rises.size() == 3 && rises.get(0) <= 2
                        && rises.get(1) - rises.get(0) == 5
                        && rises.get(2) - rises.get(1) == 5,
                "Boost did not add a level on held ticks 0, 5 and 10: "
                        + potencies);
        for (int i = rises.get(0); i < boosting.size(); i++) {
            helper.assertTrue(boosting.get(i),
                    "RHCP stopped boosting while Boost was held: "
                            + boosting);
        }
    }

    // The Boost wheel slot: plain hold is Boost, Shift+click is Reduce
    // Consumption (1.16 RHCP shift variation).
    private static void checkBoostSlot(
            GameTestHelper helper, StandPower power) {
        ControlSchemeTemplate template =
                power.getPowerType().makeDefaultControlSchemeTemplate();
        int slots = 0;
        for (ControlSchemeTemplate.GroupTemplate group
                : template.groups.values()) {
            for (ControlSchemeTemplate.AbilitiesHotbar hotbar
                    : group.hotbars) {
                for (Map<InputKey.Modifier, Map<InputMethod, String>> slot
                        : hotbar.slots) {
                    Map<InputMethod, String> plain = slot.get(null);
                    if (plain == null || !"rhcp_boost".equals(
                            plain.get(InputMethod.HOLD))) {
                        continue;
                    }
                    slots++;
                    Map<InputMethod, String> shift =
                            slot.get(InputKey.Modifier.SHIFT);
                    helper.assertTrue(shift != null
                                    && "rhcp_reduce_consumption".equals(
                                            shift.get(InputMethod.CLICK)),
                            "Shift+click on the Boost slot is not Reduce "
                                    + "Consumption: " + slot);
                }
            }
        }
        helper.assertTrue(slots == 1,
                "RHCP has " + slots + " held Boost wheel slots, not 1.");
    }

    // 1.16 RHCPEntity.counterattack: setTask(counter, getStandActionTicks,
    // Phase.WINDUP, target). Both counters wind up for the light attack's
    // recovery ticks (5 to 3 over RHCP's speed 14 to 19), not the 1-2 ticks
    // of RHCPCounterattack.getStandWindupTicks, which only feeds ticksEvaded.
    @GameTest(template = "empty", batch = "rhcp_counter_windup",
            timeoutTicks = 100)
    public static void counterattackWindsUpForLightRecoveryTicks(
            GameTestHelper helper) {
        checkCounterWindup(helper, false, RhcpAbility.Mode.COUNTERATTACK);
    }

    // 1.16 GameplayEventHandler: with Shift, more than 3 points faster is
    // the blitz, started through the same RHCPEntity.counterattack.
    @GameTest(template = "empty", batch = "rhcp_blitz_windup",
            timeoutTicks = 100)
    public static void blitzAttackWindsUpForLightRecoveryTicks(
            GameTestHelper helper) {
        checkCounterWindup(helper, true, RhcpAbility.Mode.BLITZ_ATTACK);
    }

    // RHCP at potency 3 (speed 16.5) dodges the hit of a Resolve 0
    // Magician's Red (speed 11); one sample a tick from the counter's start.
    private static void checkCounterWindup(GameTestHelper helper,
            boolean shift, RhcpAbility.Mode counterMode) {
        String name = shift ? "rhcp-blitz" : "rhcp-counter";
        ServerPlayer user = mockPlayer(helper, name, new BlockPos(1, 2, 1));
        ServerPlayer attackerUser = mockPlayer(
                helper, name + "-mr", new BlockPos(4, 2, 1));
        StandPower power = summonRhcp(helper, user);
        attackerUser.setGameMode(GameType.SURVIVAL);
        StandPower attackerPower = StandPower.get(attackerUser);
        helper.assertTrue(attackerPower != null,
                "The mock attacker has no Stand power attachment.");
        attackerPower.setStand(ModStands.MAGICIANS_RED.get());
        attackerPower.setStamina(attackerPower.getMaxStamina());
        helper.assertTrue(ModStands.MAGICIANS_RED.get()
                        .summon(attackerUser, attackerPower),
                "Magician's Red could not be summoned.");
        List<ActionPhase> phases = new ArrayList<>();
        int[] recoveryTicks = {0};
        Runnable leave = () -> {
            cleanup(user, power, "RHCP counter GameTest complete");
            cleanup(attackerUser, attackerPower,
                    "RHCP counter GameTest complete");
        };
        helper.startSequence()
                .thenWaitUntil(() -> standTicking(helper, user))
                .thenIdle(5)
                .thenExecute(() -> {
                    try {
                        recoveryTicks[0] = dodgeIntoCounter(helper, user,
                                power, attackerPower, shift, counterMode);
                    }
                    catch (RuntimeException | Error failure) {
                        leave.run();
                        throw failure;
                    }
                })
                .thenExecuteFor(10, () -> {
                    EntityActionInstance action = activeAction(user);
                    phases.add(RhcpAbility.modeOf(action) == counterMode
                            ? action.getPhase() : null);
                })
                .thenExecute(() -> {
                    try {
                        int windup = 0;
                        while (windup < phases.size()
                                && phases.get(windup) == ActionPhase.WINDUP) {
                            windup++;
                        }
                        helper.assertTrue(windup < phases.size()
                                        && phases.get(windup)
                                                == ActionPhase.PERFORM,
                                "The " + counterMode + " did not go from"
                                        + " its windup to its punch: "
                                        + phases);
                        helper.assertTrue(windup == recoveryTicks[0]
                                        && windup == 4,
                                "The " + counterMode + " wound up for "
                                        + windup + " ticks; 1.16 winds up"
                                        + " for the light attack's "
                                        + recoveryTicks[0] + " recovery"
                                        + " ticks (4 at speed 16.5): "
                                        + phases);
                    }
                    finally {
                        leave.run();
                    }
                })
                .thenSucceed();
    }

    // The production route of a dodged hit: LivingIncomingDamageEvent,
    // RhcpEvents.onIncomingDamage, RhcpService.dodgeStandHit.
    private static int dodgeIntoCounter(GameTestHelper helper,
            ServerPlayer user, StandPower power, StandPower attackerPower,
            boolean shift, RhcpAbility.Mode counterMode) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        StandEntity attacker = attackerPower.getSummonedStandEntity();
        helper.assertTrue(stand != null && attacker != null
                        && attacker.isAlive(),
                "RHCP or Magician's Red left before the counter check.");
        RhcpState state = RhcpAddon.state(user);
        state.setEnergy(2000.0F);
        state.setPotency(3);
        String speeds = " RHCP speed " + stand.getAttackSpeed()
                + ", Magician's Red speed " + attacker.getAttackSpeed() + ".";
        helper.assertTrue(Math.abs(stand.getAttackSpeed() - 16.5D) < 1.0E-3D
                        && Math.abs(attacker.getAttackSpeed() - 11.0D)
                                < 1.0E-3D,
                "RHCP at potency 3 and a Resolve 0 Magician's Red are not"
                        + " at speed 16.5 and 11:" + speeds);
        StandEntityManualControlToggle.on(helper.getLevel(), stand);
        StandManualInput.setServerShift(user, shift);
        Ability dodge = power.getAbility("rhcp_dodge");
        helper.assertTrue(dodge instanceof EntityActionType
                        && dodge.checkConditions(power).isPositive(),
                "RHCP under manual control could not start its dodge.");
        EntityActionInstance action = ((EntityActionType) dodge)
                .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
        LivingComponentAction.getComponent(stand).setAction(
                action, user, SyncType.TRACKING_AND_SELF);
        DamageSource hit = new DamageSource(
                DamageUtil.type(helper.getLevel(), ModDamageTypes.STAND_ATTACK),
                attacker);
        helper.assertFalse(stand.hurt(hit, 4.0F),
                "RHCP took the hit of a Stand it is 5.5 points faster"
                        + " than:" + speeds);
        helper.assertTrue(RhcpAbility.modeOf(stand.getCurStandAction())
                        == counterMode,
                "RHCP answered Magician's Red with "
                        + RhcpAbility.modeOf(stand.getCurStandAction())
                        + ", not " + counterMode + ":" + speeds);
        return StandStatFormulas.getLightAttackRecovery(
                stand.getAttackSpeed(), stand.getFinisherMeter());
    }

    // 1.16 RHCPEntity.canBreakBlock: without power RHCP breaks a block of
    // hardness 1 or less that needs no tool tier, whatever its strength;
    // the Stand strength rule does not apply on top.
    @GameTest(template = "empty", batch = "rhcp_unpowered_break",
            timeoutTicks = 60)
    public static void unpoweredRhcpBreaksSoftBlocksWhateverItsStrength(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-break", new BlockPos(1, 2, 1));
        StandPower power = summonRhcp(helper, user);
        RhcpState state = RhcpAddon.state(user);
        BlockPos dirt = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos stone = helper.absolutePos(new BlockPos(3, 2, 3));
        Level level = helper.getLevel();
        try {
            state.setPotency(0);
            RhcpService.sync(user);
            RhcpStandEntity stand = RhcpService.activeStand(user);
            helper.assertTrue(stand != null, "RHCP is not summoned.");
            user.getAttribute(ModEntityAttributes.STAND_STRENGTH)
                    .setBaseValue(2.0D);
            StandEntity.updateStandStatAttributes(stand, user);
            level.setBlockAndUpdate(dirt, Blocks.DIRT.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            BlockState dirtState = level.getBlockState(dirt);
            helper.assertFalse(StandEntityPunchAbility.StandEntityPunch
                            .isBlockBreakableByStrength(
                                    stand.getAttackDamage(), dirtState,
                                    level, dirt),
                    "Setup: the strength rule lets strength "
                            + stand.getAttackDamage() + " break dirt.");
            helper.assertTrue(StandEntityPunchAbility.StandEntityPunch
                            .breakStandBlock(level, dirt, stand, false)
                            && level.getBlockState(dirt).isAir(),
                    "1.16 unpowered RHCP breaks dirt (hardness 0.5, no tool"
                            + " tier) at strength " + stand.getAttackDamage()
                            + "; the port refuses it by the strength rule.");
            helper.assertFalse(StandEntityPunchAbility.StandEntityPunch
                            .breakStandBlock(level, stone, stand, false),
                    "1.16 unpowered RHCP does not break stone, which needs"
                            + " a pickaxe.");
        } finally {
            level.setBlockAndUpdate(dirt, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.AIR.defaultBlockState());
            cleanup(user, power, "RHCP unpowered break GameTest complete");
        }
        helper.succeed();
    }

    // 1.16 RHCPEntity: idle withYOffset(-0.75, 0.5, -0.75) and arms-only
    // withYOffset(0, 0.5, 0.15), (left, up, forward) from the user's feet,
    // turned by the view yaw; 0.5 is the default y of task offsets without
    // their own.
    @GameTest(template = "empty", batch = "rhcp_idle_placement",
            timeoutTicks = 60)
    public static void idleRhcpStandsWhereTheDonorPutIt(
            GameTestHelper helper) {
        ServerPlayer user = mockPlayer(
                helper, "rhcp-idle", new BlockPos(1, 12, 1));
        user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get())
                .standSide = HumanoidArm.RIGHT;
        StandPower power = summonRhcp(helper, user);
        try {
            RhcpStandEntity stand = RhcpService.activeStand(user);
            helper.assertTrue(stand != null && stand.isFollowingUser()
                            && !stand.isArmsOnlyMode()
                            && stand.offsetFromUser.isIdle(),
                    "Setup: RHCP is not an idle full Stand following its user.");
            helper.assertTrue(stand.Y_OFFSET == 0.5D,
                    "1.16 RHCP task offsets without a y took 0.5, got "
                            + stand.Y_OFFSET);
            turnIdleUser(user, 0.0F, 50.0F);
            assertIdleAt(helper, user, stand, -0.75D, 0.5D, -0.75D,
                    "view yaw 0, body yaw 50");
            turnIdleUser(user, 90.0F, 0.0F);
            assertIdleAt(helper, user, stand, 0.75D, 0.5D, -0.75D,
                    "view yaw 90, body yaw 0");
            turnIdleUser(user, 0.0F, 50.0F);
            stand.setArmsOnlyMode();
            assertIdleAt(helper, user, stand, 0.0D, 0.5D, 0.15D,
                    "arms-only, view yaw 0");
        } finally {
            cleanup(user, power, "RHCP idle placement GameTest complete");
        }
        helper.succeed();
    }

    private static void turnIdleUser(
            ServerPlayer user, float viewYaw, float bodyYaw) {
        user.setYRot(viewYaw);
        user.yRotO = viewYaw;
        user.setYHeadRot(viewYaw);
        user.yHeadRotO = viewYaw;
        user.yBodyRot = bodyYaw;
        user.yBodyRotO = bodyYaw;
    }

    private static void assertIdleAt(GameTestHelper helper, ServerPlayer user,
            StandEntity stand, double dx, double dy, double dz, String when) {
        stand.updatePosition(user);
        Vec3 actual = stand.position().subtract(user.position());
        helper.assertTrue(Math.abs(actual.x - dx) < 1.0E-4D
                        && Math.abs(actual.y - dy) < 1.0E-4D
                        && Math.abs(actual.z - dz) < 1.0E-4D,
                "1.16 put an idle RHCP at its user's feet + (" + dx + ", "
                        + dy + ", " + dz + ") with " + when + ", got " + actual
                        + " (offset " + stand.offsetFromUser.getRelativeOffset()
                        + " " + stand.offsetFromUser.getRotations() + ").");
    }

    private static StandPower summonRhcp(
            GameTestHelper helper, ServerPlayer user) {
        user.setGameMode(GameType.SURVIVAL);
        StandPower power = StandPower.get(user);
        helper.assertTrue(power != null,
                "The mock player has no Stand power attachment.");
        power.setStand(RhcpAddon.RHCP.get());
        // Max Resolve unlocks Boost and Reduce Consumption (Resolve 2).
        power.skipProgression();
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(RhcpAddon.RHCP.get().summon(user, power),
                "RHCP could not be summoned.");
        RhcpStandEntity stand = RhcpService.activeStand(user);
        helper.assertTrue(stand != null, "RHCP was not added to the level.");
        stand.summonLockTicks = 0;
        RhcpState state = RhcpAddon.state(user);
        state.setEnergy(1000.0F);
        state.setPotency(3);
        RhcpService.sync(user);
        return power;
    }

    private static void standTicking(
            GameTestHelper helper, ServerPlayer user) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        helper.assertTrue(stand != null
                        && helper.getLevel().isPositionEntityTicking(
                                stand.blockPosition()),
                "RHCP's chunk never started ticking entities.");
    }

    private static EntityActionInstance activeAction(ServerPlayer user) {
        RhcpStandEntity stand = RhcpService.activeStand(user);
        return stand != null ? stand.getCurStandAction() : null;
    }

    // A fresh resolution: the power caches its moves within a tick.
    private static AbilityConditionCheck resolve(
            StandPower power, Ability ability) {
        AvailableAbilities available = new AvailableAbilities();
        available.update(power, power.getMoveset());
        return available.getContextVariationContainer(ability);
    }

    private static boolean usable(StandPower power, Ability ability) {
        AbilityConditionCheck input = resolve(power, ability);
        return input != null && input.conditionCheck.isPositive();
    }

    // The server half of a key: preflight, then the press, as
    // ClAbilityInputPacket runs them.
    private static BufferingState press(GameTestHelper helper,
            ServerPlayer user, StandPower power, Ability ability,
            short key, InputMethod method) {
        AbilityConditionCheck input = resolve(power, ability);
        helper.assertTrue(input != null && input.ability == ability
                        && AbilityInput.withConditionCheck(
                                input, user, method),
                "The production " + method + " preflight rejected "
                        + ability.name() + ".");
        BufferingState buffering = BufferingState.clickCanBuffer();
        AbilityInput.keyPress(key, ability, user,
                new FriendlyByteBuf(Unpooled.buffer()), method, 0.0F,
                buffering, ability.abilityId);
        return buffering;
    }

    private static String speeds(
            RhcpStandEntity stand, StandEntity starPlatinum) {
        return " RHCP speed " + stand.getAttackSpeed()
                + ", Star Platinum speed " + starPlatinum.getAttackSpeed()
                + ", difference "
                + RhcpService.speedDifference(stand, starPlatinum) + ".";
    }

    private static ServerPlayer mockPlayer(
            GameTestHelper helper, String playerName, BlockPos relative) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), playerName);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        Vec3 position = Vec3.atBottomCenterOf(helper.absolutePos(relative));
        player.setPos(position.x, position.y, position.z);
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        return player;
    }

    private static void cleanup(
            ServerPlayer player, StandPower power, String message) {
        StandEntity stand = power.getSummonedStandEntity();
        if (stand != null) {
            stand.discard();
        }
        player.connection.disconnect(Component.literal(message));
    }
}
