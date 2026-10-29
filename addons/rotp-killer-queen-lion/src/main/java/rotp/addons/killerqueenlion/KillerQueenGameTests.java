package rotp.addons.killerqueenlion;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;
import rotp.addons.killerqueenlion.ability.BitesTheDustAbility;
import rotp.addons.killerqueenlion.btd.BitesTheDustActivationReceipt;
import rotp.addons.killerqueenlion.btd.BitesTheDustAwakening;
import rotp.addons.killerqueenlion.btd.BitesTheDustService;
import rotp.addons.killerqueenlion.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueenlion.state.KillerQueenState;
import rotp.addons.killerqueenlion.state.KillerQueenStateService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Creeper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KillerQueenGameTests {
    private KillerQueenGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 25)
    public static void sheerHeartPersistsAcrossEveryRuntimePhase(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-sha-owner", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }

        power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenLionAddon.KILLER_QUEEN.get()
                        .summon(owner, power),
                "Killer Queen could not be summoned for the SHA test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "Killer Queen was not added to the server level.");
        if (stand == null) {
            return;
        }
        stand.summonLockTicks = 0;
        cleanup.entities.add(stand);

        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        cleanup.entities.add(target);
        // 1.16 removes a bomb whose blast leaves its target dead
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
        target.setHealth(2000.0F);
        SheerHeartAttackEntity sheerHeart =
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                sheerHeart != null,
                "Sheer Heart Attack could not be created.");
        if (sheerHeart == null) {
            return;
        }
        sheerHeart.bind(owner, stand, target);
        cleanup.entities.add(sheerHeart);
        // Fixed pad spot, clear of the stand
        sheerHeart.moveTo(
                pad.getX() + 3.5D,
                pad.getY(),
                pad.getZ() + 0.5D,
                -90.0F,
                0.0F);
        target.setPos(
                sheerHeart.getX() + 8.0D,
                sheerHeart.getY(),
                sheerHeart.getZ());
        helper.assertTrue(
                !sheerHeart.isRemoved()
                        && helper.getLevel()
                                .addFreshEntity(sheerHeart)
                        && KillerQueenStateService.linkSheerHeart(
                                owner, sheerHeart),
                "Sheer Heart Attack did not enter its linked "
                        + "server lifetime.");

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(
                    sheerHeart.isAlive()
                            && sheerHeart.swellTicks() == 0
                            && sheerHeart
                                    .postExplosionIdleTicks()
                                    == 0,
                    "SHA did not remain present during idle/chase.");
            target.setPos(
                    sheerHeart.getX() + 1.0D,
                    sheerHeart.getY(),
                    sheerHeart.getZ());
            sheerHeart.setTarget(target);
        });
        helper.runAtTickTime(5, () ->
                helper.assertTrue(
                        sheerHeart.isAlive()
                                && sheerHeart.swellTicks() > 0
                                && sheerHeart.swellTicks()
                                        < SheerHeartAttackEntity
                                                .SWELL_TICKS,
                        "SHA did not remain present while swelling."));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(
                    sheerHeart.isAlive()
                            && !sheerHeart.isRemoved()
                            && sheerHeart.swellTicks() == SheerHeartAttackEntity.SWELL_TICKS
                            && sheerHeart
                                    .postExplosionIdleTicks()
                                    > 0,
                    "SHA did not remain present after detonation.");
            sheerHeart.discard();
            target.discard();
            stand.discard();
            owner.connection.disconnect(
                    Component.literal(
                            "Killer Queen SHA GameTest complete"));
            sheerHeartPad(helper, false);
            helper.succeed();
        });
    }

    // 1.16 SheerHeartSwelGoal: swell starts within 3 blocks, holds
    // still, and breaks blocks only when the rules allow it.
    @GameTest(
            template = "empty",
            batch = "kqSheerHeartSwell",
            timeoutTicks = 40)
    public static void sheerHeartSwellsWithinThreeBlocksAndHoldsStill(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-sha-swell", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }
        power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                "Killer Queen could not be summoned for the swell test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "Killer Queen was not added to the server level.");
        if (stand == null) {
            return;
        }
        stand.summonLockTicks = 0;
        cleanup.entities.add(stand);

        Cow target = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        cleanup.entities.add(target);
        // 1.16 removes a bomb whose blast leaves its target dead
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
        target.setHealth(2000.0F);
        SheerHeartAttackEntity sheerHeart =
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                sheerHeart != null,
                "Sheer Heart Attack could not be created.");
        if (sheerHeart == null) {
            return;
        }
        sheerHeart.bind(owner, stand, target);
        cleanup.entities.add(sheerHeart);
        // Fixed pad spot, clear of the stand
        sheerHeart.moveTo(
                pad.getX() + 3.5D,
                pad.getY(),
                pad.getZ() + 0.5D,
                -90.0F,
                0.0F);
        target.setPos(
                sheerHeart.getX() + 5.0D,
                sheerHeart.getY(),
                sheerHeart.getZ());
        helper.assertTrue(
                !sheerHeart.isRemoved()
                        && helper.getLevel()
                                .addFreshEntity(sheerHeart)
                        && KillerQueenStateService.linkSheerHeart(
                                owner, sheerHeart),
                "Sheer Heart Attack did not enter its linked "
                        + "server lifetime.");

        double[] anchor = new double[2];
        BlockPos[] floor = new BlockPos[1];
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(
                    sheerHeart.swellTicks() == 0,
                    "SHA started swelling more than 3 blocks away.");
            target.setPos(
                    sheerHeart.getX() + 2.5D,
                    sheerHeart.getY(),
                    sheerHeart.getZ());
            sheerHeart.setDeltaMovement(
                    0.0D, sheerHeart.getDeltaMovement().y, 0.0D);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(
                    sheerHeart.swellTicks() > 0,
                    "SHA did not swell within 3 blocks.");
            anchor[0] = sheerHeart.getX();
            anchor[1] = sheerHeart.getZ();
            floor[0] = sheerHeart.blockPosition().below();
            helper.getLevel().setBlockAndUpdate(
                    floor[0], Blocks.DIRT.defaultBlockState());
        });
        helper.runAtTickTime(13, () -> {
            double dx = sheerHeart.getX() - anchor[0];
            double dz = sheerHeart.getZ() - anchor[1];
            helper.assertTrue(
                    sheerHeart.swellTicks() > 0
                            && sheerHeart.swellTicks()
                                    < SheerHeartAttackEntity.SWELL_TICKS
                            && dx * dx + dz * dz < 0.04D,
                    "SHA kept chasing while swelling.");
        });
        helper.runAtTickTime(30, () -> {
            boolean breaks = JojoModUtil.breakingBlocksEnabled(
                            helper.getLevel())
                    && EventHooks.canEntityGrief(
                            helper.getLevel(), sheerHeart);
            helper.assertTrue(
                    sheerHeart.postExplosionIdleTicks() > 0,
                    "SHA did not detonate.");
            helper.assertTrue(
                    helper.getLevel().getBlockState(floor[0]).isAir()
                            == breaks,
                    "SHA block damage ignored the break-blocks "
                            + "and mobGriefing rules.");
            sheerHeart.discard();
            target.discard();
            stand.discard();
            owner.connection.disconnect(
                    Component.literal(
                            "Killer Queen SHA swell GameTest complete"));
            sheerHeartPad(helper, false);
            helper.succeed();
        });
    }

    // First target dies mid-swell: SHA swells again on a second target
    // within 3 blocks and detonates instead of freezing. The second
    // target is a monster, found by the 1.16 target selector at its
    // random interval; an unprovoked animal is no automatic target.
    @GameTest(
            template = "empty",
            batch = "kqSheerHeartRetarget",
            timeoutTicks = 210)
    public static void sheerHeartReswellsOnNextTargetAfterFirstDies(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-sha-retarget", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }
        power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                "Killer Queen could not be summoned for the retarget "
                        + "test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "Killer Queen was not added to the server level.");
        if (stand == null) {
            return;
        }
        stand.summonLockTicks = 0;
        cleanup.entities.add(stand);

        Cow first = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        Creeper second = helper.spawnWithNoFreeWill(
                EntityType.CREEPER, 1, 2, 1);
        cleanup.entities.add(first);
        cleanup.entities.add(second);
        // Not attackable until the first target is dead
        second.setInvulnerable(true);
        // and it survives the blast, which keeps the bomb (1.16)
        second.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000.0D);
        second.setHealth(2000.0F);
        SheerHeartAttackEntity sheerHeart =
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get()
                        .create(helper.getLevel());
        helper.assertTrue(
                sheerHeart != null,
                "Sheer Heart Attack could not be created.");
        if (sheerHeart == null) {
            return;
        }
        sheerHeart.bind(owner, stand, first);
        cleanup.entities.add(sheerHeart);
        // Fixed pad spot, clear of the stand
        sheerHeart.moveTo(
                pad.getX() + 3.5D,
                pad.getY(),
                pad.getZ() + 0.5D,
                -90.0F,
                0.0F);
        first.setPos(
                sheerHeart.getX() + 5.0D,
                sheerHeart.getY(),
                sheerHeart.getZ());
        second.setPos(
                sheerHeart.getX() + 6.0D,
                sheerHeart.getY(),
                sheerHeart.getZ());
        helper.assertTrue(
                !sheerHeart.isRemoved()
                        && helper.getLevel()
                                .addFreshEntity(sheerHeart)
                        && KillerQueenStateService.linkSheerHeart(
                                owner, sheerHeart),
                "Sheer Heart Attack did not enter its linked "
                        + "server lifetime.");

        helper.runAtTickTime(2, () -> {
            first.setPos(
                    sheerHeart.getX() + 2.0D,
                    sheerHeart.getY(),
                    sheerHeart.getZ());
            second.setPos(
                    sheerHeart.getX() + 2.0D,
                    sheerHeart.getY(),
                    sheerHeart.getZ() + 1.2D);
            sheerHeart.setDeltaMovement(
                    0.0D, sheerHeart.getDeltaMovement().y, 0.0D);
            sheerHeart.setTarget(first);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(
                    sheerHeart.swellTicks() > 0
                            && sheerHeart.swellTicks()
                                    < SheerHeartAttackEntity.SWELL_TICKS,
                    "SHA did not swell on the first target.");
            helper.assertTrue(
                    sheerHeart.getTarget() == first,
                    "SHA left its first target for one it cannot attack.");
            first.kill();
            helper.assertTrue(
                    !first.isAlive(),
                    "The first target did not die.");
            second.setInvulnerable(false);
        });
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(
                    sheerHeart.postExplosionIdleTicks() > 0,
                    "SHA froze on its dead first target instead of "
                            + "swelling on the second one.");
            sheerHeart.discard();
            first.discard();
            second.discard();
            stand.discard();
            owner.connection.disconnect(
                    Component.literal(
                            "Killer Queen SHA retarget GameTest complete"));
            sheerHeartPad(helper, false);
            helper.succeed();
        });
    }

    // Own batch and forced-chunk pad: the world spawn is cratered, far
    // from the test chunk and does not tick entities.
    @GameTest(
            template = "empty",
            batch = "kqBitesTheDust",
            timeoutTicks = 125)
    public static void bitesTheDustActivatesThroughProductionInput(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-btd-owner", pad);
        ServerPlayer attacker = makeCompatibleMockPlayer(
                helper, "kq-btd-attacker", pad.east(4));
        // Left logged in by the shared pad after a failure, the two would
        // keep the Sheer Heart despawn tests' bombs from despawning.
        MockPlayerCleanup cleanup =
                new MockPlayerCleanup(helper, owner, attacker);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }
        // Mock players join in the test server's Creative mode, and a
        // Creative grant skips progression (1.16); grant in Survival.
        owner.setGameMode(GameType.SURVIVAL);
        power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());

        Ability root = power.getAbility(
                KillerQueenLionAddon.TIME_MARK_NAME);
        helper.assertTrue(
                root instanceof BitesTheDustAbility,
                "The production moveset did not resolve time_mark.");
        helper.assertTrue(
                !power.isAbilityUnlocked(
                        KillerQueenLionAddon.TIME_MARK_NAME),
                "A new Survival Killer Queen incorrectly started with BTD.");
        // The rest of this flow runs in the mock player's Creative mode.
        owner.setGameMode(GameType.CREATIVE);

        double[] markedOwnerPosition = new double[3];
        Cow victim = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        cleanup.entities.add(victim);
        helper.runAtTickTime(61, () -> {
            StandArrowEntity arrow = new StandArrowEntity(
                    victim,
                    helper.getLevel(),
                    ModItems.STAND_ARROW.toStack(),
                    null);
            owner.invulnerableTime = 0;
            DamageSource standArrowHit = new DamageSource(
                    helper.getLevel()
                            .damageSources()
                            .genericKill()
                            .typeHolder(),
                    arrow,
                    victim);
            boolean arrowDamageAccepted = owner.hurt(
                    standArrowHit,
                    1.0F);
            helper.assertTrue(
                    arrowDamageAccepted,
                    "The mock server rejected the Stand Arrow damage "
                            + "before its incoming-damage event.");
            helper.assertTrue(
                    BitesTheDustAwakening.isUnlocked(owner)
                            && power.isAbilityUnlocked(
                                    KillerQueenLionAddon.TIME_MARK_NAME),
                    "A production Stand Arrow hit did not awaken BTD.");

            markedOwnerPosition[0] = owner.getX();
            markedOwnerPosition[1] = owner.getY();
            markedOwnerPosition[2] = owner.getZ();
            AbilityConditionCheck markInput =
                    power.updateAvailableMoves()
                            .getContextVariationContainer(root);
            helper.assertTrue(
                    markInput != null
                            && markInput.ability == root
                            && AbilityInput.withConditionCheck(
                                    markInput,
                                    owner,
                                    InputMethod.HOLD),
                    "The production HOLD preflight rejected time_mark.");

            var held = AbilityInput.keyPress(
                    (short) 71,
                    root,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    root.abilityId);
            // time_mark is a Stand move its user performs: it runs in the user's Stand-power action slot
            EntityActionInstance markAction =
                    LivingComponentAction.getCurEntityAction(
                            owner, PowerClass.STAND);
            helper.assertTrue(
                    held != null
                            && markAction != null
                            && markAction.ability == root,
                    "The production HOLD input did not create the "
                            + "time_mark server action.");
        });

        helper.runAtTickTime(63, () -> {
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            helper.assertTrue(
                    state.hasBitesTheDustMarker()
                            && state.bitesTheDustPlayerSnapshot(
                                    owner.getUUID()) != null,
                    "time_mark completed without entering persistent "
                            + "BTD server state.");

            victim.setPos(
                    owner.getX(),
                    owner.getY(),
                    owner.getZ() + 3.0D);
            attacker.setPos(
                    owner.getX() + 2.0D,
                    owner.getY(),
                    owner.getZ() + 3.0D);
            owner.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    victim.getEyePosition());
            owner.setShiftKeyDown(true);
            LivingComponentAction.getComponent(owner)
                    .entityAim
                    .setTarget(new ActionTarget(victim));
            owner.lookAt(
                    EntityAnchorArgument.Anchor.EYES,
                    owner.getEyePosition().add(0.0D, 0.0D, -8.0D));
            AbilityConditionCheck victimInput =
                    power.updateAvailableMoves()
                            .getContextVariationContainer(root);
            helper.assertTrue(
                    BitesTheDustService.targetedVictim(owner)
                                    == victim
                            && victimInput != null
                            && victimInput.ability.name().equals(
                                    KillerQueenLionAddon.PUT_DUST_NAME)
                            && AbilityInput.withConditionCheck(
                                    victimInput,
                                    owner,
                                    InputMethod.HOLD),
                    "The current control template did not resolve "
                            + "put_dust from a marked living target.");
            AbilityInput.keyPress(
                    (short) 72,
                    victimInput.ability,
                    owner,
                    new FriendlyByteBuf(Unpooled.buffer()),
                    InputMethod.HOLD,
                    0.0F,
                    BufferingState.clickCanBuffer(),
                    root.abilityId);
            LivingComponentAction.getComponent(owner)
                    .entityAim
                    .setTarget(ActionTarget.EMPTY);
        });

        helper.runAtTickTime(65, () -> {
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            helper.assertTrue(
                    state.hasBitesTheDustVictim()
                            && victim.getUUID().equals(
                                    state.bitesTheDustVictimUuid()),
                    "put_dust completed without arming the victim.");
            owner.setHealth(owner.getMaxHealth() - 6.0F);
            owner.setPos(
                    markedOwnerPosition[0] + 8.0D,
                    markedOwnerPosition[1],
                    markedOwnerPosition[2]);
            attacker.setPos(
                    victim.getX() + 2.0D,
                    victim.getY(),
                    victim.getZ());
            helper.getLevel().setDayTime(
                    state.bitesTheDustMarkerTime() + 600L);
            float victimHealth = victim.getHealth();
            boolean damaged = victim.hurt(
                    helper.getLevel()
                            .damageSources()
                            .playerAttack(attacker),
                    5.0F);
            helper.assertTrue(
                    !damaged
                            && victim.getHealth() == victimHealth
                            && attacker.isAlive()
                            && KillerQueenLionAddon.state(attacker)
                                    .hasBitesTheDustTrigger(),
                    "Armed BTD did not cancel victim damage and "
                            + "schedule the player-attacker trigger.");
        });

        helper.runAtTickTime(112, () -> {
            KillerQueenState state = KillerQueenLionAddon.state(owner);
            BitesTheDustActivationReceipt receipt =
                    state.latestBitesTheDustActivationReceipt();
            helper.assertTrue(
                    receipt != null
                            && receipt.verified()
                            && receipt.ownerUuid().equals(
                                    owner.getUUID())
                            && receipt.victimUuid().equals(
                                    victim.getUUID())
                            && receipt.attackerUuid().equals(
                                    attacker.getUUID())
                            && receipt.commitTick()
                                    - receipt.triggerStartTick()
                                    >= BitesTheDustService
                                            .TRIGGER_DELAY_TICKS
                            && receipt.sourceDayTimeAfter()
                                    == receipt.markerDayTime()
                            && receipt.journalStatus().equals(
                                    "APPLIED")
                            && receipt.journalEntryCount()
                                    == receipt
                                            .journalAttemptedEntries()
                            && receipt.ownerHealthAfter()
                                    == receipt.ownerMaxHealth()
                            && !receipt.attackerAliveAfter()
                            && !attacker.isAlive()
                            && owner.distanceToSqr(
                                    markedOwnerPosition[0],
                                    markedOwnerPosition[1],
                                    markedOwnerPosition[2]) < 1.0E-6D,
                    "The automatic BTD path did not emit a verified "
                            + "server commit receipt.");

            CompoundTag authentic = receipt.toTag();
            helper.assertTrue(
                    BitesTheDustService
                            .authenticatedReceiptTag(authentic),
                    "The untouched receipt failed integrity inspection.");
            assertReceiptTamperingRejected(
                    helper, authentic);
            helper.assertTrue(
                    !KillerQueenLionAddon.state(attacker)
                            .installBitesTheDustActivationReceipt(
                                    null,
                                    owner.getUUID(),
                                    receipt)
                            && !state
                                    .installBitesTheDustActivationReceipt(
                                            null,
                                            owner.getUUID(),
                                            receipt)
                            && state
                                    .consumeBitesTheDustActivationReceipt(
                                            null,
                                            owner.getUUID(),
                                            victim.getUUID(),
                                            attacker.getUUID())
                                    == null,
                    "Unprivileged code mutated or extracted receipt "
                            + "state.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    owner,
                                    victim.getUUID(),
                                    UUID.randomUUID())
                            .isEmpty(),
                    "A wrong attacker identity consumed the receipt.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    owner,
                                    victim.getUUID(),
                                    attacker.getUUID())
                            .filter(consumed -> consumed.receiptId()
                                    .equals(receipt.receiptId()))
                            .isPresent(),
                    "The state-bound receipt was not consumed once.");
            helper.assertTrue(
                    BitesTheDustService
                            .consumeLatestBitesTheDustActivationReceipt(
                                    owner,
                                    victim.getUUID(),
                                    attacker.getUUID())
                            .isEmpty(),
                    "A consumed receipt was accepted a second time.");
            cleanup.run();
            helper.succeed();
        });
    }

    private static void assertReceiptTamperingRejected(
            GameTestHelper helper, CompoundTag authentic) {
        Map<String, Consumer<CompoundTag>> mutations =
                new LinkedHashMap<>();
        mutations.put("Format", tag -> tag.putInt(
                "Format", tag.getInt("Format") + 1));
        for (String field : new String[] {
                "Receipt",
                "AuthorityEpoch",
                "MarkerEpoch",
                "Owner",
                "Victim",
                "Attacker"
        }) {
            mutations.put(field, tag ->
                    tag.putUUID(field, UUID.randomUUID()));
        }
        for (String field : new String[] {
                "MarkerDimension", "SourceDimension"
        }) {
            mutations.put(field, tag -> tag.putString(
                    field, "minecraft:the_nether"));
        }
        for (String field : new String[] {
                "MarkerDayTime",
                "MarkerGameTime",
                "SourceDayTimeBefore",
                "SourceDayTimeAfter"
        }) {
            mutations.put(field, tag -> tag.putLong(
                    field, tag.getLong(field) + 1L));
        }
        for (String field : new String[] {
                "TriggerStartTick",
                "CommitTick",
                "SnapshotCount",
                "TeleportedPlayers",
                "JournalEntryCount",
                "JournalAttemptedEntries"
        }) {
            mutations.put(field, tag -> tag.putInt(
                    field, tag.getInt(field) + 1));
        }
        for (String field : new String[] {
                "OwnerHealthBefore",
                "OwnerHealthAfter",
                "OwnerMaxHealth",
                "AttackerHealthBefore",
                "AttackerHealthAfter"
        }) {
            mutations.put(field, tag -> tag.putFloat(
                    field, tag.getFloat(field) + 1.0F));
        }
        for (String field : new String[] {
                "AttackerAliveAfter",
                "MarkerStillPresent",
                "VictimStillBound"
        }) {
            mutations.put(field, tag -> tag.putBoolean(
                    field, !tag.getBoolean(field)));
        }
        mutations.put("JournalStatus", tag -> tag.putString(
                "JournalStatus", "ROLLED_BACK"));
        mutations.put("Seal", tag -> {
            byte[] seal = tag.getByteArray("Seal");
            seal[0] ^= 0x01;
            tag.putByteArray("Seal", seal);
        });

        for (Map.Entry<String, Consumer<CompoundTag>> mutation
                : mutations.entrySet()) {
            CompoundTag forged = authentic.copy();
            mutation.getValue().accept(forged);
            helper.assertTrue(
                    !BitesTheDustService
                            .authenticatedReceiptTag(forged),
                    "A receipt with a forged " + mutation.getKey()
                            + " field passed integrity inspection.");
        }
    }

    // SHA and BTD tests run on a flat pad in the test's own forced
    // (entity-ticking) chunk. Mock players otherwise join at a random
    // spot near the world spawn, which earlier SHA blasts crater and
    // whose neighbouring chunks do not tick entities.
    // Each test has a pad of its own: the pads in use, wherever they are, lie
    // PAD_SPACING apart in height, out of each other's target search (4
    // blocks up and down) and blast reach even across a chunk border, and a
    // cleanup clears only its own. A test that fails before its cleanup keeps
    // its pad, so no later test is built among what it left.
    public static BlockPos sheerHeartPad(
            GameTestHelper helper, boolean build) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        ChunkPos chunk = new ChunkPos(origin);
        PadSlot slot = build
                ? claimPad(helper, origin)
                : releasePad(origin);
        if (slot == null) {
            // Already cleared
            return null;
        }
        int floorY = origin.getY() + 9 + slot.index() * PAD_SPACING;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ();
                    z <= chunk.getMaxBlockZ(); z++) {
                for (int y = floorY; y <= floorY + 4; y++) {
                    helper.getLevel().setBlockAndUpdate(
                            new BlockPos(x, y, z),
                            build && y == floorY
                                    ? Blocks.STONE.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (!build) {
            // Blast drops, including those that fell through the pad; a pad
            // above another stops short of the lower one's space
            helper.getLevel().getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(
                                    chunk.getMinBlockX(),
                                    slot.index() == 0
                                            ? origin.getY() - 1
                                            : floorY - PAD_SPACING + 6,
                                    chunk.getMinBlockZ(),
                                    chunk.getMaxBlockX() + 1,
                                    floorY + 5,
                                    chunk.getMaxBlockZ() + 1))
                    .forEach(ItemEntity::discard);
        }
        BlockPos spot = new BlockPos(
                chunk.getMinBlockX() + 2,
                floorY + 1,
                chunk.getMinBlockZ() + 8);
        helper.assertTrue(
                helper.getLevel().isPositionEntityTicking(spot),
                "The SHA test pad is outside the entity-ticking chunk.");
        return spot;
    }

    private static final int PAD_SPACING = 16;
    // The pads in use, by test origin; game tests run on the server thread alone
    private static final List<PadSlot> PADS = new ArrayList<>();

    private record PadSlot(long origin, int index) {}

    private static PadSlot claimPad(GameTestHelper helper, BlockPos origin) {
        Set<Integer> taken = new HashSet<>();
        for (PadSlot slot : PADS) {
            if (slot.origin() == origin.asLong()) {
                return slot;
            }
            taken.add(slot.index());
        }
        int index = 0;
        while (taken.contains(index)) {
            index++;
        }
        helper.assertTrue(
                origin.getY() + 14 + index * PAD_SPACING
                        < helper.getLevel().getMaxBuildHeight(),
                "fixture: no room for another SHA test pad; "
                        + PADS.size() + " are in use or kept by failed tests");
        PadSlot slot = new PadSlot(origin.asLong(), index);
        PADS.add(slot);
        return slot;
    }

    @Nullable
    private static PadSlot releasePad(BlockPos origin) {
        for (PadSlot slot : PADS) {
            if (slot.origin() == origin.asLong()) {
                PADS.remove(slot);
                return slot;
            }
        }
        return null;
    }

    static List<SheerHeartAttackEntity> ownedSheerHearts(ServerPlayer owner) {
        return List.copyOf(((ServerLevel) owner.level()).getEntities(
                KillerQueenLionAddon.SHEER_HEART_ENTITY.get(),
                sheer -> !sheer.isRemoved() && sheer.isOwnedBy(owner.getUUID())));
    }

    /**
     * Discards a test's entities, disconnects its mock players and releases its pad once, whether the test passes,
     * fails or times out.
     */
    static final class MockPlayerCleanup implements GameTestListener {
        private final GameTestHelper helper;
        private final List<ServerPlayer> players;
        final List<Entity> entities = new ArrayList<>();
        private boolean done;

        MockPlayerCleanup(GameTestHelper helper, ServerPlayer... players) {
            this.helper = helper;
            this.players = new ArrayList<>(List.of(players));
        }

        // A player who logs in again, or is made again by a respawn, during the test
        ServerPlayer join(ServerPlayer player) {
            players.add(player);
            return player;
        }

        void run() {
            if (done) {
                return;
            }
            done = true;
            entities.forEach(Entity::discard);
            // A bomb a failed fixture made but did not hand over
            players.forEach(player -> ownedSheerHearts(player).forEach(Entity::discard));
            for (ServerPlayer player : players) {
                if (!player.hasDisconnected()) {
                    player.connection.disconnect(Component.literal("Killer Queen GameTest complete"));
                }
            }
            sheerHeartPad(helper, false);
        }

        // From the test runner: the test has already passed or failed, and its own result is the one reported
        private void runQuietly() {
            try {
                run();
            }
            catch (RuntimeException ignored) {
            }
        }

        @Override
        public void testStructureLoaded(GameTestInfo info) {}

        @Override
        public void testPassed(GameTestInfo info, GameTestRunner runner) {
            runQuietly();
        }

        @Override
        public void testFailed(GameTestInfo info, GameTestRunner runner) {
            runQuietly();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) {
            runQuietly();
        }
    }

    /*
     * bitesTheDustActivatesThroughProductionInput's cleanup when it fails: its mock players leave and its pad is
     * released, so one failure does not leave players by the shared pad for the Sheer Heart despawn tests.
     */
    @GameTest(template = "empty", batch = "kqlBitesTheDustCleanup", timeoutTicks = 40)
    public static void bitesTheDustTestLetsItsPlayersGoWhenItFails(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-btd-cleanup-owner", pad);
        ServerPlayer attacker = makeCompatibleMockPlayer(helper, "kq-btd-cleanup-attacker", pad.east(4));
        Cow victim = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner, attacker);
        cleanup.entities.add(victim);
        cleanup.testFailed(helper.testInfo, null);
        boolean padReleased = sheerHeartPad(helper, false) == null;
        helper.succeedWhen(() -> {
            helper.assertTrue(owner.hasDisconnected() && attacker.hasDisconnected()
                            && helper.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID()) == null
                            && helper.getLevel().getServer().getPlayerList().getPlayer(attacker.getUUID()) == null,
                    "A failed production Bites the Dust test left its mock players logged in by the shared pad");
            helper.assertTrue(victim.isRemoved() && padReleased,
                    "A failed production Bites the Dust test kept its cow (" + victim.isRemoved()
                            + " removed) or its pad (" + padReleased + " released)");
        });
    }

    private static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String name) {
        return makeCompatibleMockPlayer(helper, name, null);
    }

    public static ServerPlayer makeCompatibleMockPlayer(
            GameTestHelper helper, String name, BlockPos spot) {
        GameProfile profile = new GameProfile(
                UUID.randomUUID(), name);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(),
                helper.getLevel(),
                profile,
                cookie.clientInformation());
        if (spot != null) {
            // Join at the spot, not at a random world-spawn position
            player.moveTo(
                    spot.getX() + 0.5D,
                    spot.getY(),
                    spot.getZ() + 0.5D,
                    0.0F,
                    0.0F);
        }
        Connection connection =
                new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel()
                .getServer()
                .getPlayerList()
                .placeNewPlayer(connection, player, cookie);
        return player;
    }

    /*
     * 1.16 KillerQueenModel.initActionPoses puts RANGED_ATTACK, DETONATE and BOMB_PUNCH only, and kq_heavy_punch and
     * its finisher kq_combo_punch set no pose, so both drew HumanoidStandModel's default heavy. The port keeps the core
     * default's clip: the official hooking punch with the core's one-tick perform segment (RECOVERY 0.4833, core
     * K5-31), for heavy_punch (the heavy and the combo finisher); the unused grab_heavy_punch copy is kept in step.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyClipsKeepTheCoreDefaultHookingPunch(
            GameTestHelper helper) {
        String path = "/assets/rotp_zkq8/stand_skins/killer_queen/assets/rotp_zkq8/animations/"
                + "killer_queen.animation.json";
        com.google.gson.JsonObject animations;
        try (java.io.InputStream in = KillerQueenGameTests.class.getResourceAsStream(path)) {
            helper.assertTrue(in != null, "Missing asset " + path);
            animations = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                            in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonObject("animations");
        }
        catch (java.io.IOException e) {
            throw new IllegalStateException("Could not read " + path, e);
        }
        com.google.gson.JsonObject heavy = animations.getAsJsonObject("heavy_punch");
        helper.assertTrue(heavy != null, "Killer Queen has no heavy_punch clip");
        com.google.gson.JsonObject expectedTimeline = com.google.gson.JsonParser.parseString(
                "{\"0.0\":\"phase = WINDUP;\",\"0.4333\":\"phase = PERFORM;\",\"0.4833\":\"phase = RECOVERY;\"}")
                .getAsJsonObject();
        helper.assertTrue(
                expectedTimeline.equals(heavy.getAsJsonObject("timeline")),
                "Killer Queen Lion's heavy_punch must keep the core default's PERFORM 0.4333 / RECOVERY 0.4833 (the"
                        + " thrust ends 0.4667, the hold plays over the recovery): "
                        + heavy.getAsJsonObject("timeline"));
        com.google.gson.JsonObject bones = heavy.getAsJsonObject("bones");
        com.google.gson.JsonObject rightArm = com.google.gson.JsonParser.parseString(
                "{\"0.0\":{\"vector\":[29.5,0,20]},\"0.3333\":{\"vector\":[9.25322,32.14662,33.30985]},"
                        + "\"0.4667\":{\"vector\":[-17.44969,18.61317,85.35624],\"easing\":\"easeInCubic\"},"
                        + "\"0.5333\":{\"vector\":[-17.44969,18.61317,85.35624]},"
                        + "\"1.0333\":{\"vector\":[-17.44969,18.61317,85.35624]},"
                        + "\"1.2\":{\"vector\":[-14.78217,-2.57594,35.3342]}}").getAsJsonObject();
        com.google.gson.JsonObject rightArmBend = com.google.gson.JsonParser.parseString(
                "{\"0.0\":{\"vector\":[-90,-10,-90]},\"0.3333\":{\"vector\":[-90,-31,-90]},"
                        + "\"0.4\":{\"vector\":[-114.00365,-19.49519,6.86178]},\"0.4333\":{\"vector\":[-103.01,0,0]},"
                        + "\"0.4667\":{\"vector\":[0,0,0]},\"0.5333\":{\"vector\":[0,0,0]},"
                        + "\"1.0333\":{\"vector\":[0,0,0]},"
                        + "\"1.2\":{\"vector\":[-96.90944,29.46837,-58.8207]}}").getAsJsonObject();
        helper.assertTrue(
                rightArm.equals(bones.getAsJsonObject("right_arm").getAsJsonObject("rotation"))
                        && rightArmBend.equals(bones.getAsJsonObject("right_arm_bend").getAsJsonObject("rotation")),
                "Killer Queen Lion's heavy_punch must stay the core default's hooking punch: right_arm "
                        + bones.get("right_arm") + ", right_arm_bend " + bones.get("right_arm_bend"));
        helper.assertTrue(
                heavy.equals(animations.get("grab_heavy_punch")),
                "Killer Queen Lion's grab_heavy_punch must be the same clip as heavy_punch");
        helper.succeed();
    }
}
