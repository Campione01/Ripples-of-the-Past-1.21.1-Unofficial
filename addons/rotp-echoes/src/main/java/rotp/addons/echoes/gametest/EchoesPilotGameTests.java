package rotp.addons.echoes.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import rotp.addons.echoes.EchoesGameEvents;
import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.ability.EchoesGravityAbility;
import rotp.addons.echoes.init.EchoesDataComponents;
import rotp.addons.echoes.init.EchoesStands;
import rotp.addons.echoes.state.EchoesFreezeState;
import rotp.addons.echoes.state.EchoesGravityService;
import rotp.addons.echoes.state.EchoesUserState;
import rotp.addons.echoes.state.GravityMarkSavedData;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

@GameTestHolder("rotp_echoes_pilot")
@PrefixGameTestTemplate(false)
public final class EchoesPilotGameTests {
    private static final Vec3 MOTION = new Vec3(0.2D, 0.4D, -0.2D);
    private static final short SHORT_BAR_KEY = 171;

    private EchoesPilotGameTests() {}

    // Its near pig stands outside the test's own bounds; a default-batch neighbour's succeed() discarded it.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierField")
    public static void carrierInventoryTickAppliesDragAndPinsOnlyEligibleNeighbours(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesCarrier");
        Pig near = pig(helper, player.position().add(2.79D, 0.0D, 0.0D));
        Pig far = pig(helper, player.position().add(-3.2D, 0.0D, 0.0D));
        BlockPos water = player.blockPosition();
        helper.runAfterDelay(1, () -> {
            try {
                ItemStack expired = new ItemStack(Items.COBBLESTONE);
                expired.set(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get(), helper.getLevel().getGameTime());
                player.getInventory().setItem(0, expired);
                helper.assertTrue(expired.has(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get()),
                        "Setup: the expired stack has no infusion component");
                player.setDeltaMovement(MOTION);
                near.setDeltaMovement(MOTION);
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                helper.assertTrue(!expired.has(EchoesDataComponents.GRAVITY_INFUSED_UNTIL.get()),
                        "The server inventory tick retained an expired infusion component");
                motion(helper, player.getDeltaMovement(), MOTION, "expired infusion must not drag its carrier");
                motion(helper, near.getDeltaMovement(), MOTION, "expired infusion must not pin neighbours");
                int[] counts = {1, 2, 5, 6, 30};
                double[] factors = {0.85D, 0.7D, 0.25D, 0.2D, 0.2D};
                for (int i = 0; i < counts.length; i++) {
                    inventory(player, counts[i]);
                    player.setDeltaMovement(MOTION);
                    near.setDeltaMovement(MOTION);
                    far.setDeltaMovement(MOTION);
                    EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                    motion(helper, player.getDeltaMovement(), MOTION.scale(factors[i]), "carrier drag");
                    motion(helper, near.getDeltaMovement(), new Vec3(0.06D, -0.1D, -0.06D), "near field");
                    motion(helper, far.getDeltaMovement(), MOTION, "outside field");
                }
                // A real server player tick posts PlayerTickEvent.Post on the game bus; no gravity keeps the
                // carrier in place so the neighbour stays inside the field.
                inventory(player, 1);
                player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                player.setNoGravity(true);
                player.setDeltaMovement(Vec3.ZERO);
                near.setDeltaMovement(MOTION);
                player.doTick();
                player.setNoGravity(false);
                helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                        "A server player tick did not reach the carrier handler");
                motion(helper, near.getDeltaMovement(), new Vec3(0.06D, -0.1D, -0.06D), "player tick near field");
                for (GameType mode : new GameType[] {GameType.CREATIVE, GameType.SPECTATOR}) {
                    player.setGameMode(mode);
                    near.setDeltaMovement(MOTION);
                    EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                    motion(helper, near.getDeltaMovement(), MOTION, mode + " must not pin neighbours");
                }
                player.setGameMode(GameType.SURVIVAL);
                inventory(player, 1);
                helper.getLevel().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(water.above(), Blocks.WATER.defaultBlockState());
                player.baseTick();
                helper.assertTrue(player.isInWater(), "Setup: the carrier is not in real water");
                player.setSwimming(true);
                player.xxa = 1.0F;
                player.zza = 1.0F;
                player.setDeltaMovement(MOTION);
                EchoesGameEvents.onPlayerTick(new PlayerTickEvent.Post(player));
                motion(helper, player.getDeltaMovement(), new Vec3(0.02D, -0.8D, -0.02D), "water drag");
                helper.assertTrue(!player.isSwimming() && player.xxa == 0.0F && player.zza == 0.0F,
                        "The infused carrier kept its swimming input");
            } finally {
                near.discard();
                far.discard();
                helper.getLevel().setBlockAndUpdate(water, Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(water.above(), Blocks.AIR.defaultBlockState());
                remove(helper, player);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void finisherCleanupEndsSelfAndInfusionButKeepsTargetAndHandFreeze(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesFinisher");
        EchoesUserState state = RotpEchoes.userState(player);
        GravityMarkSavedData marks = GravityMarkSavedData.get(helper.getLevel());
        BlockPos mark = player.blockPosition().below();
        BlockPos otherMark = mark.east(2);
        UUID otherOwner = UUID.randomUUID();
        try {
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF
                            && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && player.hasEffect(MobEffects.WEAKNESS)
                            && heldJump(player, EchoesGravityService.SELF_FREEZE_JUMP_MODIFIER),
                    "Setup: Freeze Self did not start");
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !player.hasEffect(MobEffects.WEAKNESS)
                            && !heldJump(player, EchoesGravityService.SELF_FREEZE_JUMP_MODIFIER),
                    "The finisher did not release the user's self-freeze effects");
            state.activateGravityInfuse();
            inventory(player, 2);
            marks.mark(mark, player.getUUID(), helper.getLevel().getGameTime() + 600L);
            marks.mark(otherMark, otherOwner, helper.getLevel().getGameTime() + 600L);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && marks.mark(mark, helper.getLevel().getGameTime()) == null
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(0),
                                    helper.getLevel().getGameTime(), false)
                            && !EchoesGravityService.isInfused(player.getInventory().getItem(1),
                                    helper.getLevel().getGameTime(), false),
                    "The finisher left infused inventory or its owned block mark");
            GravityMarkSavedData.Mark kept = marks.mark(otherMark, helper.getLevel().getGameTime());
            helper.assertTrue(kept != null && otherOwner.equals(kept.owner()),
                    "The finisher removed a block mark owned by another player");
            UUID target = UUID.randomUUID();
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2));
            state.activateTargetFreeze(target);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_TARGET
                            && target.equals(state.frozenTarget()), "The finisher released a target freeze");
            helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && player.hasEffect(MobEffects.WEAKNESS),
                    "The finisher stripped self-freeze effects during a target freeze");
            state.activateHandFreeze(target);
            EchoesGravityService.onAct3FinisherHit(player);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.HAND_FREEZE
                            && target.equals(state.frozenTarget()), "The finisher released a hand freeze");
        } finally {
            marks.removeOwner(otherOwner);
            marks.removeOwner(player.getUUID());
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityInfuseAction.getTargetBlock cast an OUTLINE ray: a short grass in front of a wall is the block that
    // gets the mark, not the wall behind it.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void infuseMarksTheGrassItLooksAtNotTheWallBehindIt(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesInfuseGrass");
        GravityMarkSavedData marks = GravityMarkSavedData.get(helper.getLevel());
        BlockPos eyeBlock = BlockPos.containing(player.getEyePosition());
        BlockPos grass = eyeBlock.south(3);
        BlockPos wall = eyeBlock.south(6);
        try {
            player.setYRot(0.0F);
            player.setXRot(0.0F);
            helper.getLevel().setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), 3);
            helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.INFUSE);
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(marks.mark(grass, time) != null && marks.mark(wall, time) == null,
                    "Gravity Infuse looked at a short grass in front of a wall: 092's OUTLINE ray marks the grass, grass "
                            + "mark=" + marks.mark(grass, time) + ", wall mark=" + marks.mark(wall, time));
        } finally {
            marks.removeOwner(player.getUUID());
            helper.getLevel().setBlock(grass, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityFreezeSelfAction: Slowness XI, jump -10 and Weakness III (field_76437_t) while it lasts, all three
    // removed when it ends. Jump -10 kept the 1.16 user on the ground; 1.21 clamps it to Jump Boost I.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void selfFreezeWeakensItsUserAndReleasesTheWeakness(GameTestHelper helper) {
        ServerPlayer player = player(helper, "EchoesSelfFreeze");
        EchoesUserState state = RotpEchoes.userState(player);
        try {
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.FREEZE_SELF
                            && player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "Setup: Freeze Self did not start");
            helper.assertTrue(weakness != null && weakness.getAmplifier() == 2
                            && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "1.16 Freeze Self gives its user Weakness III, not Resistance; the port gave weakness "
                            + weakness + ", resistance " + player.getEffect(MobEffects.DAMAGE_RESISTANCE));
            float rise = jumpRise(player);
            helper.assertTrue(rise == 0.0F && !player.hasEffect(MobEffects.JUMP),
                    "1.16 Freeze Self's jump -10 keeps its user on the ground; the port's user jumps " + rise
                            + " with jump " + player.getEffect(MobEffects.JUMP));
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            helper.assertTrue(state.activeSkill() == EchoesUserState.ActiveSkill.NONE
                            && !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && !player.hasEffect(MobEffects.WEAKNESS)
                            && jumpRise(player) > 0.0F,
                    "Ending Freeze Self left its slowness, weakness or held jump on the user");
        } finally {
            remove(helper, player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void targetFreezeGivesTheDonorDisablerEffects(GameTestHelper helper) {
        freezeScene(helper, EchoesGravityAbility.Mode.FREEZE_TARGET);
    }

    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void handFreezeGivesTheDonorDisablerEffects(GameTestHelper helper) {
        freezeScene(helper, EchoesGravityAbility.Mode.HAND_FREEZE);
    }

    /**
     * 092 GravityFreezeAction and HandFreezeAction call DisablerTagHandler.disable: Slowness, Mining Fatigue, Weakness,
     * Jump and Hunger at 255, no Resistance and no Blindness, and DisablerTagHandler.enable removes those five. The
     * frozen target cannot walk, jump, mine or deal melee damage, its food bar drains, and it can still be hurt.
     */
    private static void freezeScene(GameTestHelper helper, EchoesGravityAbility.Mode mode) {
        ServerPlayer user = freezer(helper, "EchoesFreezer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player target = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesFrozen")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            // A potion the target already had is not the freeze's to take.
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            float digSpeed = target.getDestroySpeed(Blocks.STONE.defaultBlockState());
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());

            EchoesGravityService.perform(user, mode);
            EchoesFreezeState frozen = RotpEchoes.freezeState(target);
            helper.assertTrue(frozen.active(), "Setup: " + mode + " did not freeze the target");
            StringBuilder effects = new StringBuilder();
            for (MobEffectInstance effect : target.getActiveEffects()) {
                effects.append(' ').append(effect);
            }
            for (var effect : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.WEAKNESS,
                    MobEffects.HUNGER)) {
                MobEffectInstance active = target.getEffect(effect);
                helper.assertTrue(active != null && active.getAmplifier() == 255,
                        "1.16 " + mode + " gives Slowness, Mining Fatigue, Weakness, Jump and Hunger at 255 and no"
                                + " Blindness or Resistance; the port gave" + effects);
            }
            MobEffectInstance resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(resistance != null && resistance.getAmplifier() == 0
                            && !target.hasEffect(MobEffects.BLINDNESS) && !target.hasEffect(MobEffects.JUMP),
                    "1.16 " + mode + " adds no Resistance, Blindness or Jump Boost; the port gave" + effects);
            float health = target.getHealth();
            target.hurt(target.damageSources().generic(), 4.0F);
            helper.assertTrue(target.getHealth() < health,
                    "1.16: a target frozen by " + mode + " can be hurt; the port's took no damage");
            float rise = jumpRise(target);
            helper.assertTrue(target.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0.0D && rise == 0.0F,
                    "1.16: a target frozen by " + mode + " cannot walk or jump; speed "
                            + target.getAttributeValue(Attributes.MOVEMENT_SPEED) + ", jump " + rise);
            float frozenDigSpeed = target.getDestroySpeed(Blocks.STONE.defaultBlockState());
            helper.assertTrue(frozenDigSpeed < digSpeed * 0.001F
                            && target.getAttributeValue(Attributes.ATTACK_DAMAGE) == 0.0D,
                    "1.16: a target frozen by " + mode + " cannot mine or deal melee damage; dig speed "
                            + frozenDigSpeed + " of " + digSpeed + ", attack damage "
                            + target.getAttributeValue(Attributes.ATTACK_DAMAGE));
            float exhaustion = target.getFoodData().getExhaustionLevel();
            target.getEffect(MobEffects.HUNGER).tick(target, () -> {});
            float drained = target.getFoodData().getExhaustionLevel() - exhaustion;
            helper.assertTrue(drained > 1.0F,
                    "1.16 Hunger 255 drains the frozen target's food bar by 1.28 a tick; the port drained " + drained);

            EchoesGravityService.perform(user, mode);
            resistance = target.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(!frozen.active() && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                            && !target.hasEffect(MobEffects.DIG_SLOWDOWN) && !target.hasEffect(MobEffects.WEAKNESS)
                            && !target.hasEffect(MobEffects.HUNGER) && jumpRise(target) > 0.0F
                            && resistance != null && resistance.getAmplifier() == 0,
                    "1.16 DisablerTagHandler.enable removes exactly the freeze's effects when " + mode
                            + " ends; the target kept" + activeEffects(target) + ", jump " + jumpRise(target));
        } finally {
            if (target != null) {
                target.discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    // 092 GravityFreezeSelfAction and the other gravity actions have no stamina condition: StandEntityTask pays
    // staminaCost(50) when the action performs and ignores the result.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60)
    public static void freezeSelfStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesShortBar");
        StandPower power = PowerClass.STAND.attachGet(user);
        EchoesUserState state = RotpEchoes.userState(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() > 50.0F,
                    "Setup: the user needs a finite stamina bar");
            power.setStamina(10.0F);
            Ability freezeSelf = power.getAbility("freeze_self");
            helper.assertTrue(freezeSelf != null, "Setup: ACT3 has no freeze_self");
            var selected = power.updateAvailableMoves().getContextVariationContainer(freezeSelf);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "1.16 starts Freeze Self on any stamina; the port refused it with " + power.getStamina()
                            + " of 50 on the bar");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(SHORT_BAR_KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    freezeSelf.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess, "1.16 starts Freeze Self on any stamina; the port's key press"
                    + " with " + power.getStamina() + " on the bar started nothing");
        } catch (RuntimeException | Error failure) {
            cleanupShortBar(helper, user, power);
            throw failure;
        }
        int[] waited = {0};
        helper.onEachTick(() -> {
            if (state.activeSkill() != EchoesUserState.ActiveSkill.FREEZE_SELF) {
                if (++waited[0] > 40) {
                    cleanupShortBar(helper, user, power);
                    helper.fail("1.16 performs Freeze Self on a short bar; the port's Freeze Self never started");
                }
                return;
            }
            float stamina = power.getStamina();
            cleanupShortBar(helper, user, power);
            helper.assertTrue(stamina == 0.0F,
                    "1.16 pays Freeze Self's 50 from a bar of 10 by draining it to 0; the bar holds " + stamina);
            helper.succeed();
        });
    }

    // 092 GravityProjectileAction.standPerform pays staminaCost(100) itself once it finds a gravity item, and
    // StandEntityTask pays it again after the perform, both results ignored: a throw costs 200.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void gravityProjectileThrowCostsTheDonorTwoHundred(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesThrower");
        StandPower power = PowerClass.STAND.attachGet(user);
        StandEntity stand = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 250.0F,
                    "Setup: the user needs a finite stamina bar of at least 250");
            Ability projectile = power.getAbility("gravity_projectile");
            helper.assertTrue(projectile instanceof EchoesGravityAbility,
                    "Setup: ACT3 has no gravity projectile: " + projectile);
            // the bar before the throw and after it
            float[][] bars = {{250.0F, 50.0F}, {150.0F, 0.0F}};
            for (float[] bar : bars) {
                inventory(user, 1);
                power.setStamina(bar[0]);
                EntityActionInstance action = ((EchoesGravityAbility) projectile)
                        .initActionOnAbilityUse(helper.getLevel(), user, stand, null);
                LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
                action.actionPerformStart();
                LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);
                discardThrownItems(helper, user);
                helper.assertTrue(user.getInventory().getItem(0).isEmpty(),
                        "Setup: the throw did not take the gravity item");
                helper.assertTrue(power.getStamina() == bar[1],
                        "1.16 pays the gravity throw's 100 twice, 200 in all; from " + bar[0] + " the bar ends at "
                                + bar[1] + ", the port's at " + power.getStamina());
            }
        } finally {
            discardThrownItems(helper, user);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    // 092 GravityProjectileAction has no gravity-item condition: pressed with no gravity item it throws nothing, and
    // StandEntityTask still pays its staminaCost(100) after the perform.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesEmptyThrow")
    public static void gravityThrowWithNoItemStillRunsAndPaysItsHundred(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesEmptyThrow");
        StandPower power = PowerClass.STAND.attachGet(user);
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.summonLockTicks = 0;
            stand.setNoGravity(true);
            helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 250.0F,
                    "Setup: the user needs a finite stamina bar of at least 250");
            // 092 unlocks the gravity throw at Resolve level 4.
            power.setResolveLevel(4);
            user.getInventory().clearContent();
            power.setStamina(250.0F);
            Ability projectile = power.getAbility("gravity_projectile");
            helper.assertTrue(projectile instanceof EchoesGravityAbility,
                    "Setup: ACT3 has no gravity projectile: " + projectile);
            var selected = power.updateAvailableMoves().getContextVariationContainer(projectile);
            helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
                    "1.16 runs the gravity throw with no gravity item; the port refused the press ("
                            + (selected != null ? selected.conditionCheck.getWarning() : "not available") + ")");
            BufferingState buffer = BufferingState.clickOnly();
            AbilityInput.keyPress(SHORT_BAR_KEY, selected.ability, user, null, InputMethod.CLICK, 0.0F, buffer,
                    projectile.getAbilityId());
            helper.assertTrue(buffer.isActionSuccess,
                    "1.16 runs the gravity throw with no gravity item; the port's key press started nothing");
        } catch (RuntimeException | Error failure) {
            cleanupShortBar(helper, user, power);
            throw failure;
        }
        int[] waited = {0};
        boolean[] closed = {false};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            if (power.getStamina() == 250.0F) {
                if (++waited[0] > 40) {
                    closed[0] = true;
                    cleanupShortBar(helper, user, power);
                    helper.fail("1.16 pays an empty gravity throw's 100 when it performs; the port's bar never moved");
                }
                return;
            }
            closed[0] = true;
            float stamina = power.getStamina();
            int thrown = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class, user.getBoundingBox().inflate(16.0D)).size();
            cleanupShortBar(helper, user, power);
            helper.assertTrue(stamina == 150.0F && thrown == 0,
                    "1.16 pays an empty gravity throw's 100 and throws nothing; the bar went from 250 to " + stamina
                            + ", " + thrown + " item(s) thrown");
            helper.succeed();
        });
    }

    // 092 GravityItemEvents gives a carrier Jump -min(count, 5), which 1.16 adds to the jump as 0.1 * (1 - level):
    // no change with one item, 0.1 less for each item from two up to five, and nothing once none is carried. It does
    // not lift a user whom a freeze keeps on the ground.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void carriedGravityItemsLowerTheJumpFromTwoUp(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesHeavyCarrier");
        try {
            float plain = jumpRise(player);
            helper.assertTrue(plain > 0.4F, "Setup: the carrier's own jump is " + plain);
            for (int count : new int[] {1, 2, 3, 5, 6, 0}) {
                inventory(player, count);
                EchoesGravityService.tickInventoryGravity(player);
                float expected = plain - 0.1F * Math.max(Math.min(count, 5) - 1, 0);
                float rise = jumpRise(player);
                helper.assertTrue(Math.abs(rise - expected) < 1.0E-4F && !player.hasEffect(MobEffects.JUMP),
                        "1.16 Jump -" + Math.min(count, 5) + " lifts a carrier of " + count + " gravity items by "
                                + expected + "; the port lifts it by " + rise + " with jump "
                                + player.getEffect(MobEffects.JUMP));
            }
            inventory(player, 1);
            EchoesGravityService.perform(player, EchoesGravityAbility.Mode.FREEZE_SELF);
            EchoesGravityService.tickInventoryGravity(player);
            float rise = jumpRise(player);
            helper.assertTrue(rise == 0.0F,
                    "1.16: Freeze Self keeps a carrier of one gravity item on the ground; the port's jumps " + rise
                            + " with jump " + player.getEffect(MobEffects.JUMP));
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    // 092 GravityItemEvents' Jump -min(count, 5) also moved the safe fall distance of 1.16 calculateFallDamage by
    // 1 - level: no change with one item, a block less for each item from two up to five, and none once no item is
    // carried. A 10-block fall hurts a carrier of no item or one item 7.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierAttributes")
    public static void carriedGravityItemsMakeFallsHurtMoreFromTwoUp(GameTestHelper helper) {
        FallingCarrier player = carrier(helper, "EchoesFallingCarrier");
        try {
            int plain = player.fallDamage(10.0F);
            helper.assertTrue(plain == 7, "Setup: a 10-block fall hurts the carrier " + plain);
            for (int count : new int[] {1, 2, 3, 5, 6, 0}) {
                inventory(player, count);
                EchoesGravityService.tickInventoryGravity(player);
                int expected = plain + Math.max(Math.min(count, 5) - 1, 0);
                int damage = player.fallDamage(10.0F);
                helper.assertTrue(damage == expected,
                        "1.16 Jump -" + Math.min(count, 5) + " makes a 10-block fall of a carrier of " + count
                                + " gravity items hurt " + expected + "; the port's hurts " + damage
                                + " (safe fall distance "
                                + player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) + ")");
            }
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    // The carrier's jump and fall modifiers live on synced attributes: they go to the client when the count of
    // gravity items changes them, not on every tick that carries the same items.
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40,
            batch = "rotpEchoesCarrierAttributes")
    public static void carrierJumpAndFallAreSentOnlyWhenTheyChange(GameTestHelper helper) {
        ServerPlayer player = freezer(helper, "EchoesSteadyCarrier");
        try {
            AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
            AttributeInstance fall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
            Set<AttributeInstance> toSync = player.getAttributes().getAttributesToSync();
            inventory(player, 3);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall),
                    "Setup: three gravity items did not change the carrier's jump and fall");
            toSync.clear();
            for (int tick = 0; tick < 3; tick++) {
                EchoesGravityService.tickInventoryGravity(player);
            }
            helper.assertTrue(!toSync.contains(jump) && !toSync.contains(fall),
                    "The carrier of the same three gravity items has its jump " + toSync.contains(jump)
                            + " and fall " + toSync.contains(fall) + " sent again on a tick that changes neither");
            inventory(player, 4);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall)
                            && Math.abs(jump.getValue() - (jump.getBaseValue() - 0.3D)) < 1.0E-6D
                            && Math.abs(fall.getValue() - (fall.getBaseValue() - 3.0D)) < 1.0E-6D,
                    "A fourth gravity item must change and send the carrier's jump and fall; jump "
                            + jump.getValue() + " (sent " + toSync.contains(jump) + "), fall " + fall.getValue()
                            + " (sent " + toSync.contains(fall) + ")");
            toSync.clear();
            inventory(player, 0);
            EchoesGravityService.tickInventoryGravity(player);
            helper.assertTrue(toSync.contains(jump) && toSync.contains(fall)
                            && !jump.hasModifier(EchoesGravityService.CARRIER_JUMP_MODIFIER)
                            && !fall.hasModifier(EchoesGravityService.CARRIER_FALL_MODIFIER),
                    "Dropping the last gravity item must remove and send the carrier's jump and fall modifiers");
        } finally {
            EchoesGravityService.cleanupUser(player, true);
            remove(helper, player);
        }
        helper.succeed();
    }

    /**
     * 092 DisablerTagHandler (both freezes) and HandFreezeHandler (the hand freeze): a frozen player stops using its
     * item, has no movement input and cannot fly, and a hand-frozen mob loses its target. On release 092 gave every
     * player mayfly, so survival players could fly; the port gives back the flight the player had before.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 40)
    public static void frozenPlayerStopsUsingItemsAndCannotFly(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesRestrainer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player target = null;
        Zombie zombie = null;
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesRestrained")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            for (EchoesGravityAbility.Mode mode
                    : List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE)) {
                // a survival player first, then one that could fly and was flying
                boolean flier = mode == EchoesGravityAbility.Mode.HAND_FREEZE;
                target.getAbilities().mayfly = flier;
                target.getAbilities().flying = flier;
                target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                target.startUsingItem(InteractionHand.OFF_HAND);
                target.xxa = 1.0F;
                target.zza = 1.0F;
                helper.assertTrue(target.isUsingItem(), "Setup: the target is not raising its shield");

                EchoesGravityService.perform(user, mode);
                EchoesFreezeState frozen = RotpEchoes.freezeState(target);
                helper.assertTrue(frozen.active(), "Setup: " + mode + " did not freeze the target");
                helper.assertTrue(!target.isUsingItem() && target.xxa == 0.0F && target.zza == 0.0F
                                && !target.getAbilities().flying && !target.getAbilities().mayfly,
                        "1.16: a player frozen by " + mode + " stops using its item, has no movement input and"
                                + " cannot fly; the port's target is using an item " + target.isUsingItem()
                                + ", input " + target.xxa + "/" + target.zza + ", flying "
                                + target.getAbilities().flying + ", mayfly " + target.getAbilities().mayfly);

                EchoesGravityService.perform(user, mode);
                helper.assertTrue(!frozen.active() && target.getAbilities().mayfly == flier
                                && !target.getAbilities().flying,
                        "When " + mode + " ends, the player keeps the flight it had before the freeze (mayfly "
                                + flier + ") and is not flying; the port's has mayfly "
                                + target.getAbilities().mayfly + ", flying " + target.getAbilities().flying);
            }
            Vec3 spot = target.position();
            target.discard();
            target = null;
            zombie = EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(zombie != null, "Setup: no zombie");
            zombie.setNoAi(true);
            zombie.setNoGravity(true);
            zombie.moveTo(spot.x, spot.y, spot.z, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: the zombie was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());
            for (EchoesGravityAbility.Mode mode
                    : List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE)) {
                zombie.setTarget(user);
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(RotpEchoes.freezeState(zombie).active(),
                        "Setup: " + mode + " did not freeze the zombie");
                boolean handFreeze = mode == EchoesGravityAbility.Mode.HAND_FREEZE;
                helper.assertTrue((zombie.getTarget() == null) == handFreeze,
                        "1.16 HandFreezeHandler takes a hand-frozen mob's target, and only the hand freeze does;"
                                + " after " + mode + " the zombie's target is " + zombie.getTarget());
                EchoesGravityService.perform(user, mode);
            }
        } finally {
            if (target != null) {
                target.discard();
            }
            if (zombie != null) {
                zombie.discard();
            }
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        }
        helper.succeed();
    }

    /**
     * 092 DisablerTagHandler.applyFreezeEffects and updateFreeze call stopUsingItem (func_184602_cy) on a frozen
     * player: a bow drawn for a second is lowered, and no arrow flies.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 100,
            batch = "rotpEchoesFrozenArcher")
    public static void frozenArcherLowersItsDrawnBowWithoutShooting(GameTestHelper helper) {
        ServerPlayer user = freezer(helper, "EchoesArcherFreezer");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player[] archer = {null};
        Runnable cleanup = () -> {
            discardArrows(helper, user);
            if (archer[0] != null) {
                archer[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            remove(helper, user);
        };
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Player target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesArcher")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            archer[0] = target;
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the archer was not added");
            target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            target.getInventory().setItem(9, new ItemStack(Items.ARROW, 64));
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            target.startUsingItem(InteractionHand.MAIN_HAND);
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        List<EchoesGravityAbility.Mode> modes =
                List.of(EchoesGravityAbility.Mode.FREEZE_TARGET, EchoesGravityAbility.Mode.HAND_FREEZE);
        int[] step = {0};
        int[] drawn = {0};
        helper.onEachTick(() -> {
            if (step[0] >= modes.size()) {
                return;
            }
            try {
                Player target = archer[0];
                if (++drawn[0] < 25) {
                    return;
                }
                EchoesGravityAbility.Mode mode = modes.get(step[0]);
                helper.assertTrue(target.isUsingItem() && target.getTicksUsingItem() >= 20,
                        "Setup: the archer has not drawn its bow for 20 ticks: using " + target.isUsingItem() + ", "
                                + target.getTicksUsingItem() + " ticks");
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(RotpEchoes.freezeState(target).active(),
                        "Setup: " + mode + " did not freeze the archer");
                List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(
                        AbstractArrow.class, user.getBoundingBox().inflate(32.0D));
                int left = target.getInventory().getItem(9).getCount();
                helper.assertTrue(!target.isUsingItem() && arrows.isEmpty() && left == 64,
                        "1.16: a player frozen by " + mode + " lowers a bow it has drawn for "
                                + "20 ticks and shoots nothing; the port's archer is using it " + target.isUsingItem()
                                + ", " + arrows.size() + " arrow(s) flew, " + left + " of 64 arrows left");
                EchoesGravityService.perform(user, mode);
                helper.assertTrue(!RotpEchoes.freezeState(target).active(),
                        "Setup: " + mode + " did not release the archer");
                step[0]++;
                drawn[0] = 0;
                if (step[0] < modes.size()) {
                    target.startUsingItem(InteractionHand.MAIN_HAND);
                    return;
                }
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                step[0] = modes.size();
                cleanup.run();
                throw failure;
            }
        });
    }

    /**
     * 092 HandFreezeHandler.tickFreeze, every tick of a hand freeze: the frozen player stops using its item and swings
     * both hands, and in the last 20 of the 600 ticks Slowness, Mining Fatigue and Weakness come off (Hunger and the
     * held jump stay). The target freeze does neither.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 80,
            batch = "rotpEchoesHandFreezeTicks")
    public static void handFrozenPlayerSwingsBothHandsAndEasesOffAtTheEnd(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesHandHolder");
        StandPower power = PowerClass.STAND.attachGet(user);
        Player[] held = {null};
        boolean[] closed = {false};
        Runnable cleanup = () -> {
            if (closed[0]) {
                return;
            }
            closed[0] = true;
            if (held[0] != null) {
                EchoesGravityService.releaseFrozenTarget(held[0]);
                held[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        };
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Player target = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                    new GameProfile(UUID.randomUUID(), "EchoesHandHeld")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            held[0] = target;
            target.setNoGravity(true);
            target.moveTo(user.getX(), user.getY(), user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(target), "Setup: the target was not added");
            target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            user.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE);
            helper.assertTrue(RotpEchoes.freezeState(target).active(), "Setup: HAND_FREEZE did not freeze the target");
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            try {
                Player target = held[0];
                EchoesFreezeState frozen = RotpEchoes.freezeState(target);
                int tick = ++ticks[0];
                if (tick <= 12) {
                    helper.assertTrue(frozen.active(), "Setup: the freeze was not held on tick " + tick);
                }
                if (tick == 3) {
                    target.startUsingItem(InteractionHand.OFF_HAND);
                } else if (tick == 4) {
                    helper.assertTrue(!target.isUsingItem(),
                            "1.16: a hand-frozen player who raises its shield during the freeze lowers it on the"
                                    + " next tick; the port's still holds it up");
                } else if (tick == 9) {
                    // A player on the server never counts its swing down, so the swing is cleared by hand.
                    target.swinging = false;
                    target.swingingArm = InteractionHand.MAIN_HAND;
                } else if (tick == 10) {
                    helper.assertTrue(target.swinging && target.swingingArm == InteractionHand.OFF_HAND,
                            "1.16 HandFreezeHandler swings both hands of a hand-frozen player every tick, the off"
                                    + " hand last; on its 10th tick the port's target is swinging " + target.swinging
                                    + " with " + target.swingingArm);
                    // As if 590 of the freeze's 600 ticks had passed.
                    frozen.activate(target, user.getUUID(), frozen.slowAmplifier(), EchoesFreezeState.Kind.HAND,
                            helper.getLevel().getGameTime() + 10L);
                    // A reload in that window: the transient jump hold is not saved.
                    target.getAttribute(Attributes.JUMP_STRENGTH)
                            .removeModifier(EchoesGravityService.FREEZE_JUMP_MODIFIER);
                } else if (tick == 11) {
                    helper.assertTrue(!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                    && !target.hasEffect(MobEffects.DIG_SLOWDOWN)
                                    && !target.hasEffect(MobEffects.WEAKNESS)
                                    && target.hasEffect(MobEffects.HUNGER) && jumpRise(target) == 0.0F,
                            "1.16 HandFreezeHandler takes Slowness, Mining Fatigue and Weakness off a hand freeze in"
                                    + " its last 20 ticks, and Hunger and the held jump stay; with 9 ticks left the"
                                    + " port's target has" + activeEffects(target) + ", jump " + jumpRise(target));
                    EchoesGravityService.perform(user, EchoesGravityAbility.Mode.HAND_FREEZE);
                    EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
                    helper.assertTrue(frozen.active() && frozen.kind() == EchoesFreezeState.Kind.TARGET,
                            "Setup: FREEZE_TARGET did not freeze the target");
                    frozen.activate(target, user.getUUID(), frozen.slowAmplifier(), EchoesFreezeState.Kind.TARGET,
                            helper.getLevel().getGameTime() + 10L);
                    target.swinging = false;
                } else if (tick == 12) {
                    helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                                    && target.hasEffect(MobEffects.DIG_SLOWDOWN)
                                    && target.hasEffect(MobEffects.WEAKNESS),
                            "1.16 keeps a target freeze's effects to its end; with 9 ticks left the port's target has"
                                    + activeEffects(target));
                } else if (tick == 20) {
                    helper.assertTrue(!target.swinging,
                            "1.16 swings only a hand-frozen player's hands; the target freeze's is swinging "
                                    + target.swingingArm);
                    cleanup.run();
                    helper.succeed();
                }
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
        });
    }

    /**
     * 092 DisablerTagHandler.updateFreeze gives a frozen player or mob in water a motion of 0, -0.5, 0 just before it
     * moves, so it sinks to the bottom instead of hanging where it was frozen.
     */
    @GameTest(template = "empty", templateNamespace = "rotp_echoes_pilot", timeoutTicks = 60,
            batch = "rotpEchoesFrozenInWater")
    public static void frozenMobInWaterSinksToTheBottom(GameTestHelper helper) {
        ServerPlayer user = connectedFreezer(helper, "EchoesWaterHolder");
        StandPower power = PowerClass.STAND.attachGet(user);
        Pig[] held = {null};
        List<BlockPos> water = new ArrayList<>();
        List<BlockPos> glass = new ArrayList<>();
        boolean[] closed = {false};
        Runnable cleanup = () -> {
            if (closed[0]) {
                return;
            }
            closed[0] = true;
            for (BlockPos pos : water) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            for (BlockPos pos : glass) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
            if (held[0] != null) {
                EchoesGravityService.releaseFrozenTarget(held[0]);
                held[0].discard();
            }
            EchoesGravityService.cleanupUser(user, true);
            if (power.isSummoned()) {
                EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
            }
            disconnect(user);
        };
        double[] frozenAt = {0.0D};
        try {
            power.setStand(EchoesStands.ECHOES_ACT3.get());
            helper.assertTrue(EchoesStands.ECHOES_ACT3.get().summon(user, power), "Setup: could not summon ACT3");
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Setup: no summoned ACT3");
            stand.setNoGravity(true);
            stand.moveTo(user.getX(), user.getY(), user.getZ());
            Pig pig = EntityType.PIG.create(helper.getLevel());
            helper.assertTrue(pig != null, "Setup: no pig");
            held[0] = pig;
            pig.setNoGravity(true);
            pig.moveTo(user.getX(), user.getY() + 1.0D, user.getZ() + 2.0D, 180.0F, 0.0F);
            helper.assertTrue(helper.getLevel().addFreshEntity(pig), "Setup: the pig was not added");
            user.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
            EchoesGravityService.perform(user, EchoesGravityAbility.Mode.FREEZE_TARGET);
            helper.assertTrue(RotpEchoes.freezeState(pig).active(), "Setup: FREEZE_TARGET did not freeze the pig");
            frozenAt[0] = pig.getY();
            // A column of three water blocks around the pig, walled in glass, with a glass floor two blocks under
            // its feet.
            BlockPos column = pig.blockPosition();
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos pos = column.above(dy);
                water.add(pos);
                glass.add(pos.north());
                glass.add(pos.south());
                glass.add(pos.east());
                glass.add(pos.west());
            }
            glass.add(column.below(2));
            for (BlockPos pos : glass) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
            }
            for (BlockPos pos : water) {
                helper.getLevel().setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (closed[0]) {
                return;
            }
            try {
                Pig pig = held[0];
                EchoesFreezeState frozen = RotpEchoes.freezeState(pig);
                helper.assertTrue(frozen.active(), "Setup: the freeze was not held on tick " + (ticks[0] + 1));
                double floor = frozenAt[0] - 1.0D;
                if (++ticks[0] < 20) {
                    return;
                }
                helper.assertTrue(pig.isInWater() && Math.abs(pig.getY() - floor) < 0.01D
                                && Math.abs(frozen.anchor().y - pig.getY()) < 0.01D,
                        "1.16: a mob frozen in water sinks to the bottom, from " + frozenAt[0] + " to " + floor
                                + ", and is held there; after 20 ticks the port's pig is at " + pig.getY()
                                + " (in water " + pig.isInWater() + "), held at " + frozen.anchor().y);
                cleanup.run();
                helper.succeed();
            } catch (RuntimeException | Error failure) {
                cleanup.run();
                throw failure;
            }
        });
    }

    // A server player in the server's player list, which the freeze's per-tick hold looks its owner up in.
    private static ServerPlayer connectedFreezer(GameTestHelper helper, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile,
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setNoGravity(true);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        return player;
    }

    private static void disconnect(ServerPlayer player) {
        player.connection.disconnect(Component.literal("Echoes freeze GameTest complete"));
    }

    private static void discardArrows(GameTestHelper helper, ServerPlayer user) {
        for (AbstractArrow arrow : helper.getLevel().getEntitiesOfClass(
                AbstractArrow.class, user.getBoundingBox().inflate(32.0D))) {
            arrow.discard();
        }
    }

    private static void discardThrownItems(GameTestHelper helper, ServerPlayer user) {
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, user.getBoundingBox().inflate(16.0D))) {
            item.discard();
        }
    }

    private static void cleanupShortBar(GameTestHelper helper, ServerPlayer user, StandPower power) {
        if (user.isRemoved()) {
            return;
        }
        AbilityInput.keyRelease(SHORT_BAR_KEY, user);
        EchoesGravityService.cleanupUser(user, true);
        if (power.isSummoned()) {
            EchoesStands.ECHOES_ACT3.get().forceUnsummon(user, power);
        }
        remove(helper, user);
    }

    // How high a jump from standing would lift the entity this tick.
    private static float jumpRise(LivingEntity entity) {
        Vec3 before = entity.getDeltaMovement();
        entity.setDeltaMovement(Vec3.ZERO);
        entity.jumpFromGround();
        float rise = (float) entity.getDeltaMovement().y;
        entity.setDeltaMovement(before);
        return rise;
    }

    private static boolean heldJump(LivingEntity entity, ResourceLocation modifier) {
        return entity.getAttribute(Attributes.JUMP_STRENGTH).hasModifier(modifier);
    }

    private static String activeEffects(LivingEntity entity) {
        StringBuilder effects = new StringBuilder();
        for (MobEffectInstance effect : entity.getActiveEffects()) {
            effects.append(' ').append(effect);
        }
        return effects.toString();
    }

    // Clear of the barrier ring the runner puts one and two blocks above each structure block, which would stop
    // the freeze's aim.
    private static ServerPlayer freezer(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static FallingCarrier carrier(GameTestHelper helper, String name) {
        FallingCarrier player = new FallingCarrier(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.5D,
                origin.getY() + 5.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.5D);
        player.setNoGravity(true);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    // A fake player is never hurt, so the fall damage it would take is read from LivingEntity.calculateFallDamage.
    private static final class FallingCarrier extends FakePlayer {
        private FallingCarrier(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        private int fallDamage(float distance) {
            return calculateFallDamage(distance, 1.0F);
        }
    }

    private static void inventory(ServerPlayer player, int count) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < count; slot++) {
            ItemStack stack = new ItemStack(Items.COBBLESTONE);
            EchoesGravityService.infuse(stack, player.level().getGameTime());
            player.getInventory().setItem(slot, stack);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX())) + 8.0D,
                origin.getY() + 2.0D,
                SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 8.0D);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static Pig pig(GameTestHelper helper, Vec3 position) {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        pig.setPos(position);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    private static void motion(GameTestHelper helper, Vec3 actual, Vec3 expected, String label) {
        helper.assertTrue(actual.distanceToSqr(expected) < 1.0E-12D,
                label + " expected " + expected + ", got " + actual);
    }

    private static void remove(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.UNLOADED_WITH_PLAYER);
    }
}
