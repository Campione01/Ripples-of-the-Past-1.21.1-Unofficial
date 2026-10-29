package rotp.addons.diverdown.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.diverdown.DiverDownAddon;
import rotp.addons.diverdown.DiverDownGameEvents;
import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.entity.KineticTrapEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.MathUtil;

/**
 * The 1.16 rules the wave 28 compare of Diver Down restored (049 RotP Diver Down 1.1.2.1.0 against the port).
 */
@GameTestHolder(DiverDownAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiverDownSweepGameTests {
    private static final String TEMPLATES = "rotp_dd_review_fixes";
    private static final int OWN_TICKS = 60;

    private DiverDownSweepGameTests() {}

    // 1.16 GameplayEvents.onEntityTick (049 :211-217) and SpringLegsEffect (:141-145, never ticked: the base Effect's
    // isDurationEffectTick is false): on the ground the fall distance grows by 2 x amplifier and the entity is thrown
    // straight up by (amplifier + 1) / 2; in the air nothing happens, and the effect deals no damage of its own.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_spring_legs",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void springLegsBouncesOnlyOnTheGroundAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                cow.setNoAi(true);
                cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
                cow.setHealth(40.0F);
                cow.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.0D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add a cow");
                scene.extra.add(cow);
                helper.assertTrue(DiverDownAuthority.bindEffect(cow, scene.stand,
                                DiverDownAuthority.EffectKind.SPRING_LEGS, SpringLegsAbility.DURATION_TICKS,
                                SpringLegsAbility.AMPLIFIER),
                        "Fixture: Spring Legs was not bound to the cow");

                cow.setOnGround(false);
                cow.fallDistance = 0.0F;
                cow.setDeltaMovement(0.3D, -0.2D, 0.1D);
                DiverDownGameEvents.onEntityTick(new EntityTickEvent.Post(cow));
                helper.assertTrue(cow.fallDistance == 0.0F && cow.getDeltaMovement().equals(new Vec3(0.3D, -0.2D, 0.1D)),
                        "1.16 did nothing to an entity with Spring Legs in the air; fall distance " + cow.fallDistance
                                + ", motion " + cow.getDeltaMovement());

                cow.setOnGround(true);
                cow.setDeltaMovement(0.3D, 0.0D, 0.1D);
                float health = cow.getHealth();
                DiverDownGameEvents.onEntityTick(new EntityTickEvent.Post(cow));
                helper.assertTrue(cow.getHealth() == health,
                        "1.16 Spring Legs dealt no damage of its own; health " + health + " -> " + cow.getHealth());
                helper.assertTrue(cow.fallDistance == 2.0F * SpringLegsAbility.AMPLIFIER,
                        "1.16 added twice the amplifier (2) to the fall distance on the ground; got " + cow.fallDistance);
                helper.assertTrue(cow.getDeltaMovement().equals(new Vec3(0.0D, (SpringLegsAbility.AMPLIFIER + 1) / 2.0D,
                                0.0D)),
                        "1.16 threw the entity straight up by (amplifier + 1) / 2 = 1.0 and stopped it on x and z; motion "
                                + cow.getDeltaMovement());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownDeformDash (049 :63-158) was a StandEntityHeavyAttack: onClick / onTaskSet took the finisher value
    // and spent 0.51 of the meter (1.16 core StandEntityHeavyAttack :125-142), each hit was a HeavyPunchInstance with the
    // heavy knockback 0.5 + strength / (8 - finisher x 4) (:151-157), the debuffs came before the hit, and its
    // recovery took any action (isChainable, isFreeRecovery of a finisher, StandEntityAction.canBeCanceled :507-512).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_dash_heavy",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashStrikesAsAHeavyAttackAsIn116(GameTestHelper helper) {
        // the dash carries Diver Down about 5 blocks south: those chunks must tick it
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                scene.standFacing(scene.origin, 0.0F);
                Vec3 eyes = stand.getEyePosition();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, new Vec3(eyes.x, eyes.y - 0.7D, eyes.z + 1.8D));
                cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0D);
                float[] strength = {0.0F};
                float[] expected = {0.0F};
                Consumer<LivingKnockBackEvent> knockback = event -> {
                    if (event.getEntity() == cow) {
                        strength[0] = Math.max(strength[0], event.getStrength());
                        // the hit's own 0.4, and the heavy knockback by the Stand's strength as it strikes and the
                        // finisher value its press took
                        expected[0] = 0.4F + StandStatFormulas.getHeavyAttackKnockback(stand.getAttackDamage(), 1.0F);
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(knockback));
                stand.setFinisherMeter(1.0F);
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                dash.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                helper.assertTrue(buffering.isActionSuccess && action instanceof DeformDashAbility.DeformDashAction,
                        "Fixture: the deform dash did not start");
                helper.assertTrue(stand.getLastHeavyFinisherValue() == 1.0F
                                && Math.abs(stand.getFinisherMeter() - 0.49F) < 1.0E-4F,
                        "1.16 took the finisher value (1.0) and spent 0.51 of the meter as the dash was set; value "
                                + stand.getLastHeavyFinisherValue() + ", meter " + stand.getFinisherMeter());
                Ability punch = scene.power.getAbility(DiverDownAddon.PUNCH);
                boolean[] sawRecovery = {false};
                boolean[] cancellable = {false};
                StringBuilder seen = new StringBuilder();
                boolean[] struck = {false};
                scene.poll(0, () -> {
                    seen.append(action.getPhase()).append(action.isOver() ? "/over " : " ");
                    if (!struck[0]) {
                        helper.assertTrue(cow.getHealth() < 500.0F, "The deform dash did not hit the cow before it");
                        helper.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                        && cow.hasEffect(MobEffects.WEAKNESS) && cow.hasEffect(MobEffects.DIG_SLOWDOWN),
                                "1.16 gave the dash's debuffs to the cow it struck");
                        helper.assertTrue(expected[0] > 0.0F && Math.abs(strength[0] - expected[0]) < 0.05F,
                                "1.16 knocked the struck cow back as a heavy attack, 0.5 + strength / (8 - finisher x "
                                        + "4) with the finisher value the press took (1.0, not the meter it left), on "
                                        + "top of the hit's own 0.4: " + expected[0] + "; knockback strength "
                                        + strength[0]);
                        struck[0] = true;
                        // the cow flies off with the wall impact armed; a barrier of a neighbouring test would blow up
                        cow.discard();
                    }
                    if (!sawRecovery[0] && action.getPhase() == ActionPhase.RECOVERY && !action.isOver()) {
                        sawRecovery[0] = true;
                        cancellable[0] = action.canBeCancelledInto((EntityActionType) punch);
                    }
                    helper.assertTrue(sawRecovery[0], "The deform dash has not reached its recovery yet: " + seen
                            + "; Diver Down's action " + stand.getCurStandAction());
                    if (!cancellable[0]) {
                        throw new IllegalStateException("1.16 let any action cancel the deform dash's recovery (a "
                                + "finisher's free recovery); the punch could not");
                    }
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 StandEntityHeavyAttack.punchBlock: the dash's ray met a block, which the strike punched as the heavy punch
    // punches one (HeavyPunchBlockInstance: the block punch, then the blast, which breaks the dirt beside it); and
    // StandEntity.punch turned the Stand toward each target it struck (aimWithThisOrUser, rotateTowards).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_dash_block",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashPunchesABlockAsAHeavyAttackAsIn116(GameTestHelper helper) {
        // the dash carries Diver Down about 5 blocks south: those chunks must tick it
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                Vec3 eyes = scene.origin.add(0.0D, stand.getEyeHeight(), 0.0D);
                BlockPos struck = BlockPos.containing(eyes.x, eyes.y, eyes.z + 1.6D);
                stand.moveTo(scene.origin.x, struck.getY() + 0.5D - stand.getEyeHeight(), scene.origin.z, 0.0F, 0.0F);
                stand.setYHeadRot(0.0F);
                stand.setDeltaMovement(Vec3.ZERO);
                scene.block(struck, Blocks.DIRT.defaultBlockState());
                BlockPos beside = struck.east();
                scene.block(beside, Blocks.DIRT.defaultBlockState());
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                BufferingState buffering = BufferingState.clickOnly();
                dash.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                helper.assertTrue(buffering.isActionSuccess, "Fixture: the deform dash did not start");
                scene.poll(0, () -> {
                    helper.assertTrue(helper.getLevel().getBlockState(struck).isAir(),
                            "The deform dash did not break the dirt on its way");
                    helper.assertTrue(helper.getLevel().getBlockState(beside).isAir(),
                            "1.16 punched the dirt on the dash's way as a heavy punch, whose blast broke the dirt beside "
                                    + "it; that dirt is still there");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownDeformDash.punchEntity (049 :124-137) gave the debuffs before StandEntityPunch.doHit, so a hit the
    // target's shield blocks still slowed, weakened and tired it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_dash_blocked",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashDebuffsAShieldBlockedTargetAsIn116(GameTestHelper helper) {
        // the dash carries Diver Down about 5 blocks south: those chunks must tick it
        GameTestPlacement.whenTicking(helper, 8, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                scene.standFacing(scene.origin, 0.0F);
                Vec3 eyes = stand.getEyePosition();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, new Vec3(eyes.x, eyes.y - 0.7D, eyes.z + 1.8D));
                cow.setYRot(180.0F);
                cow.setYHeadRot(180.0F);
                cow.setYBodyRot(180.0F);
                cow.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
                cow.startUsingItem(InteractionHand.MAIN_HAND);
                boolean[] pressed = {false};
                scene.poll(0, () -> {
                    if (!pressed[0]) {
                        helper.assertTrue(cow.isBlocking(), "Fixture: the cow's shield is not up yet");
                        pressed[0] = true;
                        stand.setManuallyControlled(true);
                        scene.standFacing(scene.origin, 0.0F);
                        Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                        BufferingState buffering = BufferingState.clickOnly();
                        dash.onKeyPress(helper.getLevel(), scene.owner, null, InputMethod.CLICK, 0.0F, buffering);
                        helper.assertTrue(buffering.isActionSuccess, "Fixture: the deform dash did not start");
                    }
                    helper.assertTrue(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && cow.hasEffect(MobEffects.WEAKNESS)
                                    && cow.hasEffect(MobEffects.DIG_SLOWDOWN),
                            "1.16 gave the dash's debuffs before the hit, also to a cow whose shield blocked it; health "
                                    + cow.getHealth() + ", blocking " + cow.isBlocking());
                    helper.assertTrue(cow.getHealth() == 500.0F, "Fixture: the cow's shield did not block the dash");
                });
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownDeformDash.standTickPerform (049 :106-122) punched each result of the dash's ray on a forward tick and
    // the task's target (never kept) on the first tick after; StandEntity.punch with no target aimed again
    // (aimWithThisOrUser: the user's precision ray, else the Stand's own), turned Diver Down toward what that met by up
    // to attackSpeed / 16 x 18 degrees and struck it in the Stand's reach. Rows: a forward tick whose ray meets nothing,
    // and the tick after the forward half, the user aiming at a cow off the dash line.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_dash_reaim",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashAimsAgainThroughItsUserWhenItsRayMeetsNothingAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            for (boolean secondHalf : new boolean[] {false, true}) {
                DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
                try {
                    DiverDownStandEntity stand = scene.stand;
                    LivingEntity user = scene.owner;
                    stand.setNoGravity(true);
                    // nothing to strike as the perform begins
                    scene.turn(0.0F, -90.0F);
                    Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                    EntityActionInstance action = ((EntityActionType) dash).initActionOnAbilityUse(
                            helper.getLevel(), user, stand, null);
                    action.setStartingPhase();
                    LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                    helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
                            "Fixture: the deform dash is not Diver Down's action in its perform");
                    if (secondHalf) {
                        int tick = (int) (action.getCurPhaseLength() / 2.0F) + 1;
                        action.setPhase(ActionPhase.PERFORM, tick);
                        helper.assertTrue(action.getPhase() == ActionPhase.PERFORM && action.getPhaseRatio() > 0.5F,
                                "Fixture: the dash is not past the forward half of its perform");
                    }
                    Vec3 at = user.position().add(0.0D, 0.0D, 2.0D);
                    stand.setPos(at.x, at.y, at.z);
                    stand.setYRot(0.0F);
                    stand.setXRot(0.0F);
                    stand.setYHeadRot(0.0F);
                    Cow cow = EntityType.COW.create(helper.getLevel());
                    helper.assertTrue(cow != null, "Could not create a cow");
                    scene.prepareMob(cow, new Vec3(stand.getX() + 2.0D, stand.getEyeY() - 0.7D, stand.getZ() + 0.3D));
                    user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
                    user.yRotO = user.getYRot();
                    user.xRotO = user.getXRot();
                    user.setYHeadRot(user.getYRot());
                    double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                    List<ActionTarget> onRay = HitResultUtil.clipMultipleTargets(stand, reach,
                            entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0.25D, stand.getPrecision());
                    helper.assertTrue(onRay.isEmpty(), "Fixture: the dash's ray meets " + onRay);
                    helper.assertTrue(!StandEntityPunchAbility.validatePunchTarget(stand, new ActionTarget(cow))
                            .isEmpty(helper.getLevel()), "Fixture: the cow is not in Diver Down's reach");
                    Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
                    float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
                    float yaw = Mth.clamp(Mth.degreesDifference(0.0F, MathUtil.yRotDegFromVec(toEyes)), -max, max);
                    stand.setDeltaMovement(secondHalf ? new Vec3(0.0D, 0.0D, 0.5D) : Vec3.ZERO);
                    action.actionTick();
                    String row = secondHalf ? "on the tick after its forward half" : "on a forward tick whose ray met "
                            + "nothing";
                    helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < 1.0E-3F,
                            "1.16 aimed the deform dash again through its user " + row + " and turned Diver Down toward "
                                    + "the cow the user aimed at, to yaw " + yaw + "; yaw " + stand.getYRot());
                    helper.assertTrue(cow.getHealth() < 500.0F,
                            "1.16 struck the cow its user aimed at, in Diver Down's reach, " + row + "; health "
                                    + cow.getHealth());
                    scene.close();
                } catch (Throwable error) {
                    scene.close();
                    throw error;
                }
            }
            helper.succeed();
        });
    }

    // 1.16 StandEntity.punch turned Diver Down toward each result of the dash's ray it struck (aimWithThisOrUser kept a
    // target in reach and rotateTowards turned to it), here a cow beside the dash's line that its grown hitbox puts on it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_dash_turn",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void deformDashTurnsTowardWhatItStrikesAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 3, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                DiverDownStandEntity stand = scene.stand;
                LivingEntity user = scene.owner;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                // nothing to strike as the perform begins: the Stand looks straight up
                stand.moveTo(scene.origin.x, scene.origin.y, scene.origin.z, 0.0F, -90.0F);
                Ability dash = scene.power.getAbility(DiverDownAddon.DEFORM_DASH_NAME);
                EntityActionInstance action = ((EntityActionType) dash).initActionOnAbilityUse(
                        helper.getLevel(), user, stand, null);
                action.setStartingPhase();
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.PERFORM,
                        "Fixture: the deform dash is not Diver Down's action in its perform");
                scene.standFacing(scene.origin, 0.0F);
                Vec3 eyes = stand.getEyePosition();
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                // beside the Stand's line, within the hitbox Diver Down's precision grows
                scene.prepareMob(cow, new Vec3(eyes.x - 0.9D, eyes.y - 0.7D, eyes.z + 1.8D));
                double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
                List<ActionTarget> onRay = HitResultUtil.clipMultipleTargets(stand, reach,
                        entity -> StandEntityPunchAbility.canStandHit(stand, entity), 0.25D, stand.getPrecision());
                helper.assertTrue(onRay.size() == 1 && onRay.get(0).getEntity() == cow,
                        "Fixture: the dash's ray does not meet the cow alone: " + onRay);
                Vec3 toEyes = cow.getEyePosition().subtract(stand.getEyePosition());
                float max = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
                float yaw = Mth.clamp(Mth.degreesDifference(0.0F, MathUtil.yRotDegFromVec(toEyes)), -max, max);
                action.actionTick();
                helper.assertTrue(cow.getHealth() < 500.0F, "Fixture: the deform dash did not strike the cow");
                helper.assertTrue(Math.abs(Mth.degreesDifference(stand.getYRot(), yaw)) < 1.0E-3F,
                        "1.16 turned Diver Down toward the cow its dash struck beside its line, to yaw " + yaw
                                + " (at most " + max + " a strike); yaw " + stand.getYRot());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 took Entity Phasing's target from the user's client (049 DiverDownEntityPhasing :69-71, ENTITY): GameRenderer.pick
    // reached an entity 3 blocks away at most in Survival, and only with Diver Down out did EntityStandType.clientHitResult
    // (:121-151) look again after a miss with the Stand's precision ray at the user's reach. With Diver Down away a cow
    // 4 blocks ahead was out of reach (no summon, no dive).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_phasing_away",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingWithDiverDownAwayReachesThePickAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 5, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow cow = scene.mob(4.0D, 0.5D);
                scene.unsummon();
                phasing.onClick(helper.getLevel(), scene.owner, null);
                DiverDownStandEntity summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned == null,
                        "1.16 reached an entity 3 blocks away at most with Diver Down away (the vanilla pick, no Stand "
                                + "to look again); the port summoned Diver Down into the cow 4 blocks ahead: embedded in "
                                + (summoned != null ? summoned.getEmbeddedTarget() : null));
                cow.moveTo(scene.origin.x, scene.origin.y + 0.5D, scene.origin.z + 2.8D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned != null && summoned.getEmbeddedTarget() == cow,
                        "Fixture: Diver Down was not summoned into the cow 2.8 blocks ahead");
                summoned.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // With Diver Down out, the pick met a zombie 4 blocks ahead, beyond Survival's 3, so it missed, and the Stand's
    // precision ray (StandEntity.precisionRayTrace :1726-1776) took the zombie, a hostile mob, before a cow nearer to the
    // user beside the crosshair whose hitbox Diver Down's precision grows onto it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_phasing_ray",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingTakesTheStandsPrecisionRayAfterAMissedPickAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 5, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.9D, 0.0D, 2.0D));
                Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
                helper.assertTrue(zombie != null, "Could not create a zombie");
                zombie.setNoAi(true);
                zombie.setNoGravity(true);
                zombie.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 4.0D, 0.0F, 0.0F);
                helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Could not add a zombie");
                scene.extra.add(zombie);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(scene.stand.getEmbeddedTarget() == zombie,
                        "1.16's pick missed the zombie 4 blocks ahead (beyond 3) and Diver Down's precision ray took it, "
                                + "a hostile mob, before the cow beside the crosshair; embedded in "
                                + scene.stand.getEmbeddedTarget());
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16's client picked the nearest entity under the crosshair and StandEntity.canAttack refused it silently when the
    // Stand could not attack it: the press did not pass over it to a mob behind. Here the port's kept target rules
    // (DiverDownAuthority.canAffect) refuse an invulnerable cow 2 blocks ahead, with a cow behind it.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_phasing_refused",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingRefusesAPickedTargetItMayNotTakeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 5, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                Cow shielded = scene.mob(2.0D, 0.5D);
                shielded.setInvulnerable(true);
                Cow behind = scene.mob(2.9D, 0.5D);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!scene.stand.isEmbedded(),
                        "1.16 refused the dive at the cow the crosshair met first, which it may not take, and did not "
                                + "pass over it; embedded in " + scene.stand.getEmbeddedTarget() + " (the cow behind: "
                                + (scene.stand.getEmbeddedTarget() == behind) + ")");
                shielded.setInvulnerable(false);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(scene.stand.getEmbeddedTarget() == shielded,
                        "Fixture: Diver Down did not dive into the first cow once it may take it");
                scene.stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntityPhasing (TargetRequirement.ENTITY) was a StandEntityAction, so PowerBaseImpl.checkTarget
    // (:289-313) ran StandEntityAction.overrideVanillaMouseTarget (:182-197) on the target the user's pick gave: a torch
    // (no collision shape) gave way to what Diver Down's collision ray met (StandAction.getPerformer) within its own
    // reach (MCUtil.getPickRange: its REACH_DISTANCE, 2.5). 1.16 had no walls rule, so stone stopped its pick as well,
    // and a torch was never stricter than stone. The port's dive is marked through walls (owner, 2026-10-15): its pick
    // passes a torch as it passes stone, and Diver Down's ray stands in for the torch only when that pick finds nothing.
    // A press with Diver Down away had summoned it first, not yet in the level, where its user stood
    // (StandEntityAction.onClick :204-223, EntityStandType.summon copyPosition), and a refused press took it back.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_phasing_torch",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingPressedAtATorchTakesWhatDiverDownsRayMeetsAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 5, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(0.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                DiverDownStandEntity stand = scene.stand;
                Vec3 eyes = scene.owner.getEyePosition();
                BlockPos torch = torchBefore(scene, eyes);
                // beside its user, eyes level, looking the same way: its ray runs 1.5 blocks west of the user's line,
                // beyond the hitboxes Diver Down's precision grows
                Vec3 standEyes = eyes.add(-1.5D, 0.0D, 0.0D);
                stand.setNoGravity(true);
                // a dive moves the Stand into its target; each row puts it back
                Vec3 standFeet = new Vec3(standEyes.x, standEyes.y - stand.getEyeHeight(), standEyes.z);
                scene.standFacing(standFeet, 0.0F);
                double standReach = stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
                helper.assertTrue(Math.abs(standReach - 2.5D) < 1.0E-6D,
                        "Fixture: Diver Down's reach is not 1.16's 2.5: " + standReach);
                HitResult pick = DiverDownTargeting.crosshair(scene.owner, stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(pick instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                                && hit.getBlockPos().equals(torch),
                        "Fixture: the user's pick does not meet the torch first: " + pick);

                Cow cow = scene.mob(4.0D, 0.5D);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "Through walls the dive passes a torch as it passes stone: the cow 4 blocks ahead on the user's"
                                + " line behind the torch is dived into as behind stone; the port's dive: "
                                + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(standFeet, 0.0F);

                cow.moveTo(standEyes.x, standEyes.y - 0.7D, standEyes.z + 2.0D, 0.0F, 0.0F);
                helper.assertTrue(cow.getBoundingBox().clip(standEyes, standEyes.add(0.0D, 0.0D, standReach))
                                .isPresent()
                                && cow.getBoundingBox().clip(eyes, eyes.add(0.0D, 0.0D, 6.0D)).isEmpty(),
                        "Fixture: the cow is not on Diver Down's ray alone");
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "1.16 dived into the cow Diver Down's collision ray met when the user's pick met the torch; the"
                                + " port's dive: " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(standFeet, 0.0F);

                // the same rows with stone where the torch was: stone gives way to nothing, the user's line goes past it
                scene.block(torch, Blocks.STONE.defaultBlockState());
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(!stand.isEmbedded(),
                        "Stone gives way to no collision ray (1.16 overrode only a block without a collision shape); the"
                                + " port dived into " + (stand.getEmbeddedTarget() == cow
                                        ? "the cow on Diver Down's line alone" : String.valueOf(stand.getEmbeddedTarget())));
                cow.moveTo(eyes.x, scene.origin.y + 0.5D, scene.origin.z + 4.0D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "Fixture: through walls the dive did not take the cow 4 blocks ahead behind stone: "
                                + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(standFeet, 0.0F);
                torchAt(scene, torch);

                scene.unsummon();
                // its front face 2.75 blocks before the eyes: within the user's pick of an entity, beyond Diver Down's 2.5
                cow.moveTo(eyes.x, scene.origin.y + 0.5D, eyes.z + 3.2D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                DiverDownStandEntity summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned != null && summoned.getEmbeddedTarget() == cow,
                        "With Diver Down away the through-walls pick takes the cow 2.75 blocks away behind the torch as"
                                + " behind stone; summoned " + (summoned != null) + ", embedded in "
                                + (summoned != null ? summoned.getEmbeddedTarget() : null));
                summoned.clearEmbeddedTarget();
                scene.unsummon();
                cow.moveTo(eyes.x, scene.origin.y + 0.5D, eyes.z + 2.6D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned != null && summoned.getEmbeddedTarget() == cow,
                        "1.16 summoned Diver Down for the press and its ray met the cow 2.15 blocks past the torch;"
                                + " summoned " + (summoned != null) + ", embedded in "
                                + (summoned != null ? summoned.getEmbeddedTarget() : null));
                summoned.clearEmbeddedTarget();
                scene.unsummon();
                scene.block(torch, Blocks.STONE.defaultBlockState());
                cow.moveTo(eyes.x, scene.origin.y + 0.5D, eyes.z + 3.2D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                summoned = DiverDownAddon.activeStand(scene.owner);
                helper.assertTrue(summoned != null && summoned.getEmbeddedTarget() == cow,
                        "Fixture: with Diver Down away the dive did not take the cow 2.75 blocks away behind stone;"
                                + " summoned " + (summoned != null) + ", embedded in "
                                + (summoned != null ? summoned.getEmbeddedTarget() : null));
                summoned.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // From a manually controlled Diver Down 1.16 picked from the Stand's eyes (InputHandler.pickMouseTarget, the camera
    // entity), and a pick at a torch gave way to the Stand's own collision ray (overrideVanillaMouseTarget, the Stand as
    // performer). Through walls the Stand's own grown ray passes a torch as it passes stone: a cow just beside its line
    // is dived into behind either.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_phasing_manual_torch",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void entityPhasingFromAManuallyControlledDiverDownAtATorchTakesItsRayAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 5, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                scene.look(-90.0F);
                Ability phasing = scene.power.getAbility(DiverDownAddon.ENTITY_PHASING_NAME);
                DiverDownStandEntity stand = scene.stand;
                stand.setManuallyControlled(true);
                stand.setNoGravity(true);
                Vec3 standEyes = new Vec3(scene.origin.x, scene.origin.y + scene.owner.getEyeHeight(),
                        scene.origin.z + 1.0D);
                // a dive moves the Stand into its target; each row puts it back
                Vec3 standFeet = new Vec3(standEyes.x, standEyes.y - stand.getEyeHeight(), standEyes.z);
                scene.standFacing(standFeet, 0.0F);
                BlockPos torch = torchBefore(scene, standEyes);
                HitResult pick = DiverDownTargeting.crosshair(scene.owner, stand, scene.power.getAbility(DiverDownAddon.STEAL_ITEM_NAME));
                helper.assertTrue(pick instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                                && hit.getBlockPos().equals(torch),
                        "Fixture: the manually controlled Stand's pick does not meet the torch first: " + pick);

                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                // its box 0.45 beside the Stand's line, 2 blocks ahead
                Vec3 beside = new Vec3(standEyes.x + 0.9D, standEyes.y - 0.7D, standEyes.z + 2.0D);
                scene.prepareMob(cow, beside);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "Through walls the manually controlled Diver Down's ray passes a torch as it passes stone and"
                                + " takes the cow 0.45 blocks beside its line; the port's dive: " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(standFeet, 0.0F);

                cow.moveTo(standEyes.x, standEyes.y - 0.7D, standEyes.z + 2.0D, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "1.16 dived into the cow the manually controlled Diver Down's ray met past the torch; the port's"
                                + " dive: " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.standFacing(standFeet, 0.0F);

                scene.block(torch, Blocks.STONE.defaultBlockState());
                cow.moveTo(beside.x, beside.y, beside.z, 0.0F, 0.0F);
                phasing.onClick(helper.getLevel(), scene.owner, null);
                helper.assertTrue(stand.getEmbeddedTarget() == cow,
                        "Fixture: through walls the manually controlled Diver Down did not take the cow beside its line"
                                + " behind stone: " + stand.getEmbeddedTarget());
                stand.clearEmbeddedTarget();
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // A wall torch hanging on nothing on the crosshair from these eyes, its outline 1.19 blocks before them; restored
    // with the scene's other blocks.
    private static BlockPos torchBefore(DiverDownWallsGameTests.Scene scene, Vec3 eyes) {
        BlockPos pos = BlockPos.containing(eyes).south();
        torchAt(scene, pos);
        return pos;
    }

    private static void torchAt(DiverDownWallsGameTests.Scene scene, BlockPos pos) {
        scene.placed.add(pos);
        scene.previous.add(scene.helper.getLevel().getBlockState(pos));
        scene.helper.getLevel().setBlock(pos,
                Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    // 1.16 DiverDownKineticTrap.standPerform (049 :49-62) set the trap in the block it aimed at with no spawn protection
    // or game mode guard: a user in Adventure mode sets it as well (spawn protection is guarded by the contract; the
    // GameTest server has no spawn radius).
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_trap_adventure",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void kineticTrapIsSetInAdventureModeAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement, true);
            try {
                ((ServerPlayer) scene.owner).setGameMode(GameType.ADVENTURE);
                scene.look(0.0F);
                Ability trap = scene.power.getAbility(DiverDownAddon.KINETIC_TRAP_NAME);
                scene.block(2.5D, 1, Blocks.STONE.defaultBlockState());
                List<KineticTrapEntity> set = scene.strike(trap, () -> {});
                helper.assertTrue(set.size() == 1,
                        "1.16 set the kinetic trap for a user in Adventure mode; new traps: " + set.size());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 GameplayEvents.onEntityHurt (049 :219-235): the bombed entity, hurt, sent 4 to 15 shards over a half circle of
    // yaw ((i + 1) / count / 2 x 360: none toward +x), up to 39 degrees above its own view (none downward), at 0.45 to
    // 0.65 blocks a tick (inaccuracy 1), with the still attacker's motion added.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_shard_spread",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void boneShardsFlyOverAHalfCircleAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                Cow cow = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(cow != null, "Could not create a cow");
                scene.prepareMob(cow, scene.origin.add(0.0D, 0.0D, 3.0D));
                helper.assertTrue(DiverDownAuthority.bindEffect(cow, scene.stand,
                                DiverDownAuthority.EffectKind.BONE_SHARD_BOMB, BoneShardBombAbility.DURATION_TICKS,
                                BoneShardBombAbility.AMPLIFIER),
                        "Fixture: the bone shard bomb was not bound to the cow");
                AABB around = cow.getBoundingBox().inflate(3.0D);
                List<BoneShardEntity> before = helper.getLevel().getEntitiesOfClass(BoneShardEntity.class, around);
                helper.assertTrue(cow.hurt(helper.getLevel().damageSources().mobAttack(scene.owner), 1.0F),
                        "Fixture: the bombed cow was not hurt");
                List<BoneShardEntity> shards = helper.getLevel().getEntitiesOfClass(BoneShardEntity.class, around);
                shards.removeAll(before);
                scene.extra.addAll(shards);
                helper.assertTrue(shards.size() >= 4 && shards.size() <= 15,
                        "1.16 sent 4 to 15 shards; got " + shards.size());
                for (BoneShardEntity shard : shards) {
                    Vec3 motion = shard.getDeltaMovement();
                    double speed = motion.length();
                    helper.assertTrue(speed > 0.43D && speed < 0.67D,
                            "1.16 shot each shard at 0.45 to 0.65 a tick; one flies at " + speed + " " + motion);
                    helper.assertTrue(motion.x < 0.05D,
                            "1.16 shot the shards over a half circle of yaw (0 to 180 degrees, never toward +x); one "
                                    + "flies " + motion);
                    helper.assertTrue(motion.y > -0.05D,
                            "1.16 shot the shards from the cow's view up to 39 degrees above it, none downward; one "
                                    + "flies " + motion);
                }
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 BoneShardEntity.afterBlockHit (049 :74-91): a shard that hits a block it does not break plays the block's hit
    // sound at half its pitch.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_shard_block",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void boneShardSoundsTheBlockItHitsAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 4, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                Cow origin = EntityType.COW.create(helper.getLevel());
                helper.assertTrue(origin != null, "Could not create a cow");
                scene.prepareMob(origin, scene.origin.add(2.0D, 0.0D, 0.0D));
                BlockPos stone = scene.block(3.0D, 1, Blocks.STONE.defaultBlockState());
                List<Float> pitches = new ArrayList<>();
                Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
                    if (event.getLevel() == helper.getLevel() && event.getSound() != null
                            && event.getSound().value() == SoundType.STONE.getHitSound()
                            && event.getPosition().distanceToSqr(Vec3.atCenterOf(stone)) < 1.0D) {
                        pitches.add(event.getNewPitch());
                    }
                };
                NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayLevelSoundEvent.AtPosition.class, listener);
                scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(listener));
                BoneShardEntity shard = new BoneShardEntity(origin, scene.owner, scene.stand, helper.getLevel());
                shard.setPos(scene.origin.x, stone.getY() + 0.5D, scene.origin.z + 1.0D);
                shard.setDeltaMovement(0.0D, 0.0D, 0.8D);
                helper.assertTrue(helper.getLevel().addFreshEntity(shard), "Could not add a bone shard");
                scene.extra.add(shard);
                scene.poll(0, () -> helper.assertTrue(pitches.size() == 1 && Math.abs(pitches.get(0) - 0.5F) < 1.0E-4F,
                        "1.16 played the stone's hit sound at half its pitch (0.5) where the shard hit it; heard "
                                + pitches + ", shard removed " + shard.isRemoved()));
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }

    // 1.16 DiverDownEntity.updatePosition (049 :126-142): while its task was the wall glide, Diver Down stood halfway
    // between its user and the block beside it (feet or head level, north, south, east or west), at the user's height;
    // with no block beside, it stayed where it was.
    @GameTest(template = "empty", templateNamespace = TEMPLATES, batch = "dd_sweep_glide_place",
            timeoutTicks = GameTestPlacement.WAIT_TEST_TICKS + OWN_TICKS)
    public static void wallGlidePutsDiverDownIntoTheWallAsIn116(GameTestHelper helper) {
        GameTestPlacement.whenTicking(helper, 6, placement -> {
            DiverDownWallsGameTests.Scene scene = new DiverDownWallsGameTests.Scene(helper, placement);
            try {
                // clear of the barriers that wall the test structure in
                Vec3 at = scene.origin.add(0.0D, 0.0D, 4.0D);
                scene.owner.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
                BlockPos feet = scene.owner.blockPosition();
                for (BlockPos beside : List.of(feet.north(), feet.south(), feet.east(), feet.west(),
                        feet.above().north(), feet.above().south(), feet.above().east(), feet.above().west())) {
                    helper.assertTrue(helper.getLevel().getBlockState(beside).isAir(),
                            "Fixture: a block stands beside the user at " + beside);
                }
                DiverDownStandEntity stand = scene.stand;
                Ability glide = scene.power.getAbility(DiverDownAddon.WALL_GLIDE_NAME);
                EntityActionInstance action = ((EntityActionType) glide).initActionOnAbilityUse(
                        helper.getLevel(), scene.owner, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, scene.owner, SyncType.NO_SYNC);
                helper.assertTrue(stand.getCurStandAction() == action, "Fixture: the wall glide did not start");
                Vec3 still = new Vec3(at.x + 0.3D, at.y + 0.8D, at.z - 0.6D);
                stand.setPos(still.x, still.y, still.z);
                stand.updatePosition(scene.owner);
                helper.assertTrue(stand.position().equals(still),
                        "1.16 left a gliding Diver Down where it was with no block beside its user; it moved to "
                                + stand.position());
                scene.block(feet.east(), Blocks.STONE.defaultBlockState());
                stand.updatePosition(scene.owner);
                Vec3 expected = new Vec3(at.x + 0.5D, at.y, at.z);
                helper.assertTrue(stand.position().distanceTo(expected) < 1.0E-6D,
                        "1.16 put a gliding Diver Down halfway into the stone beside its user, at " + expected
                                + "; it is at " + stand.position());
                scene.close();
                helper.succeed();
            } catch (Throwable error) {
                scene.close();
                throw error;
            }
        });
    }
}
