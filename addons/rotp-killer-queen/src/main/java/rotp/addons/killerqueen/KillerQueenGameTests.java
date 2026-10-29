package rotp.addons.killerqueen;

import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import net.minecraft.server.MinecraftServer;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import rotp.addons.killerqueen.ability.BitesTheDustAbility;
import rotp.addons.killerqueen.ability.KillerQueenComboPunchAbility;
import rotp.addons.killerqueen.btd.BitesTheDustActivationReceipt;
import rotp.addons.killerqueen.btd.BitesTheDustAwakening;
import rotp.addons.killerqueen.btd.BitesTheDustService;
import rotp.addons.killerqueen.entity.KillerQueenBombProjectile;
import rotp.addons.killerqueen.entity.SheerHeartAttackEntity;
import rotp.addons.killerqueen.state.ItemBombService;
import rotp.addons.killerqueen.state.KillerQueenState;
import rotp.addons.killerqueen.state.KillerQueenStateService;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import rotp.core.util.functions.JojoModUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import rotp.addons.killerqueen.network.KillerQueenStateSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder(KillerQueenAddon.MOD_ID)
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

        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenAddon.KILLER_QUEEN.get()
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
        // 1.16 explodeCreeper removes a bomb whose blast kills its
        // target; this one survives so the post-blast phase is seen.
        target.getAttribute(
                        net.minecraft.world.entity.ai.attributes.Attributes
                                .MAX_HEALTH)
                .setBaseValue(200.0D);
        target.setHealth(200.0F);
        SheerHeartAttackEntity sheerHeart =
                KillerQueenAddon.SHEER_HEART_ENTITY.get()
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
                            && sheerHeart.swellTicks() == 0
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

    // 1.16 KQBarrage registered its cry with the one-argument standSound
    // (playInArmsOnly true): an arms-only Killer Queen still cried.
    @GameTest(
            template = "empty",
            batch = "kqBarrageCry",
            timeoutTicks = 20)
    public static void barrageCriesInArmsOnlyMode(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-barrage-cry", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        helper.assertTrue(
                KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                "Killer Queen could not be summoned for the cry test.");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(
                stand != null && stand.isAddedToLevel(),
                "Killer Queen was not added to the server level.");
        cleanup.entities.add(stand);
        boolean full;
        boolean armsOnly;
        try {
            Ability barrage = power.getAbility("barrage");
            helper.assertTrue(
                    barrage instanceof EntityActionType,
                    "Killer Queen has no registered barrage.");
            EntityActionInstance action =
                    ((EntityActionType) barrage).createActionObj();
            Method playsCry = StandEntityBarrage.class.getDeclaredMethod(
                    "playsBarrageCry", Level.class, StandEntity.class);
            playsCry.setAccessible(true);
            full = (Boolean) playsCry.invoke(action, stand.level(), stand);
            stand.setArmsOnlyMode(true, true);
            armsOnly = (Boolean) playsCry.invoke(
                    action, stand.level(), stand);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "The core barrage cry hook is missing.", e);
        }
        finally {
            stand.discard();
            owner.connection.disconnect(
                    Component.literal("Killer Queen cry GameTest done"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(full,
                "The fully summoned Killer Queen does not cry.");
        helper.assertTrue(armsOnly,
                "The arms-only Killer Queen barrage stayed silent; "
                        + "1.16 KQBarrage cried there.");
        helper.succeed();
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
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
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
        SheerHeartAttackEntity sheerHeart =
                KillerQueenAddon.SHEER_HEART_ENTITY.get()
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
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(
                KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
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
        SheerHeartAttackEntity sheerHeart =
                KillerQueenAddon.SHEER_HEART_ENTITY.get()
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
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());

        Ability root = power.getAbility(
                KillerQueenAddon.TIME_MARK_NAME);
        helper.assertTrue(
                root instanceof BitesTheDustAbility,
                "The production moveset did not resolve time_mark.");
        helper.assertTrue(
                !power.isAbilityUnlocked(
                        KillerQueenAddon.TIME_MARK_NAME),
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
                                    KillerQueenAddon.TIME_MARK_NAME),
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
            KillerQueenState state = KillerQueenAddon.state(owner);
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
                                    KillerQueenAddon.PUT_DUST_NAME)
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
            KillerQueenState state = KillerQueenAddon.state(owner);
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
                            && KillerQueenAddon.state(attacker)
                                    .hasBitesTheDustTrigger(),
                    "Armed BTD did not cancel victim damage and "
                            + "schedule the player-attacker trigger.");
        });

        helper.runAtTickTime(112, () -> {
            KillerQueenState state = KillerQueenAddon.state(owner);
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
                    !KillerQueenAddon.state(attacker)
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

    // 1.16 KQFinisher: a full finisher bar turns heavy_punch into
    // kq_combo_punch, whose hit plants the primary bomb.
    @GameTest(
            template = "empty",
            batch = "kqComboFinisher",
            timeoutTicks = 20)
    public static void comboFinisherPlantsPrimaryBomb(
            GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-combo-owner", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(
                power != null,
                "The mock player has no Stand power attachment.");
        if (power == null) {
            return;
        }
        // Survival, so the unlock comes from the skill tree
        owner.setGameMode(GameType.SURVIVAL);
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        Ability heavy = power.getAbility("heavy_punch");
        Ability combo = power.getAbility(
                KillerQueenAddon.COMBO_PUNCH_NAME);
        helper.assertTrue(
                combo instanceof KillerQueenComboPunchAbility
                        && combo.isStandFinisherOf != null
                        && power.isAbilityUnlocked(
                                KillerQueenAddon.COMBO_PUNCH_NAME),
                "kq_combo_punch is not an unlocked heavy finisher.");
        helper.assertTrue(
                StandEntity.isFinisherMechanicUnlocked(power),
                "The combo finisher did not open the finisher bar "
                        + "at Resolve 0 (1.16 default unlock).");
        helper.assertTrue(
                KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                "Killer Queen could not be summoned for the finisher "
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
        LivingComponentAction.getComponent(stand)
                .setAction(null, SyncType.NO_SYNC);

        stand.setFinisherMeter(1.0F);
        power.tick();
        Ability resolved = power.updateAvailableMoves()
                .getContextVariation("heavy_punch");
        helper.assertTrue(
                resolved == combo && heavy != combo,
                "A full finisher bar resolved heavy_punch to "
                        + (resolved != null ? resolved.name() : "nothing")
                        + " instead of kq_combo_punch.");
        helper.assertTrue(
                KillerQueenComboPunchAbility.comboKnockback(0.25F)
                        == 0.75F,
                "The combo knockback is not 0.5 + finisher value.");

        Cow victim = helper.spawnWithNoFreeWill(
                EntityType.COW, 1, 2, 1);
        cleanup.entities.add(victim);
        // On the pad: the structure can lie beyond the 16-block bomb range
        victim.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
        helper.assertTrue(
                !KillerQueenComboPunchAbility.plantPrimaryBomb(
                                owner, stand)
                        && KillerQueenStateService.primaryBombTarget(
                                owner) == null,
                "The combo punch bombed a Stand.");
        helper.assertTrue(
                KillerQueenComboPunchAbility.plantPrimaryBomb(
                                owner, victim)
                        && KillerQueenStateService.primaryBombTarget(
                                owner) == victim,
                "The combo punch hit did not plant the primary bomb.");
        KillerQueenStateService.clearPrimaryBomb(owner);
        // A Survival player left on the shared pad would be hunted by
        // the Sheer Heart Attack of a later test
        victim.discard();
        stand.discard();
        owner.connection.disconnect(
                Component.literal(
                        "Killer Queen combo GameTest complete"));
        sheerHeartPad(helper, false);
        helper.succeed();
    }

    // 1.16 CapabilityHandler.onPlayerChangedDimension sends the player's data again. The client builds a new
    // player for every respawn packet and loses its copy of the state with the old one, so what the server
    // keeps through a dimension change has to be sent again after that packet.
    @GameTest(template = "empty", batch = "kqTimeMarkResync", timeoutTicks = 20)
    public static void keptTimeMarkIsSentToTheOwnerAgainAfterADimensionChange(GameTestHelper helper) {
        ServerPlayer owner = makeCompatibleMockPlayer(
                helper, "kq-mark-resync", helper.absolutePos(new BlockPos(1, 2, 1)));
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        List<String> stale = new ArrayList<>();
        try {
            StandPower power = StandPower.get(owner);
            helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            KillerQueenState state = KillerQueenAddon.state(owner);
            helper.assertTrue(
                    BitesTheDustService.markTime(owner)
                            && state.hasBitesTheDustMarker()
                            && state.bitesTheDustMarkerEpoch() != null
                            && !state.hasPrimaryBomb()
                            && !state.hasBlockBomb()
                            && !state.hasSheerHeart(),
                    "fixture: the owner does not hold a time mark and nothing else");
            UUID epoch = state.bitesTheDustMarkerEpoch();
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            helper.assertTrue(
                    nether != null && owner.connection.getConnection().channel() instanceof EmbeddedChannel,
                    "fixture: no Nether or no recording connection");
            EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
            Vec3 back = owner.position();

            sent.outboundMessages().clear();
            helper.assertTrue(owner.teleportTo(nether, 1.5D, 80.0D, 1.5D, Set.of(), 0.0F, 0.0F)
                            && owner.level() == nether,
                    "fixture: the owner could not change dimension");
            if (!timeMarkSentAfterRespawn(helper, sent, owner, epoch)) {
                stale.add("entering the Nether");
            }
            sent.outboundMessages().clear();
            helper.assertTrue(owner.teleportTo(level, back.x, back.y, back.z, Set.of(), 0.0F, 0.0F)
                            && owner.level() == level,
                    "fixture: the owner could not return");
            if (!timeMarkSentAfterRespawn(helper, sent, owner, epoch)) {
                stale.add("returning to the mark's dimension");
            }
            helper.assertTrue(
                    state.hasBitesTheDustMarker() && epoch.equals(state.bitesTheDustMarkerEpoch()),
                    "fixture: the server did not keep the time mark");
        } finally {
            if (owner.hasData(KillerQueenAddon.KILLER_QUEEN_STATE)) {
                BitesTheDustService.removeTimeMarker(owner);
            }
            owner.connection.disconnect(Component.literal("Killer Queen resync GameTest complete"));
        }
        helper.assertTrue(stale.isEmpty(),
                "The server keeps the time mark through a dimension change, but after the respawn packet the "
                        + "owner got no state sync carrying it when " + stale + ". The owner's new client "
                        + "player then has no time mark");
        helper.succeed();
    }

    // Whether a state sync with this time mark went to the owner after the last respawn packet on the connection.
    private static boolean timeMarkSentAfterRespawn(
            GameTestHelper helper, EmbeddedChannel sent, ServerPlayer owner, UUID epoch) {
        // The server batches a player's packets within a tick
        sent.flush();
        boolean respawned = false;
        boolean resent = false;
        for (Object message : sent.outboundMessages()) {
            if (message instanceof ClientboundRespawnPacket) {
                respawned = true;
                resent = false;
            } else if (respawned && carriesTimeMark(message, owner, epoch)) {
                resent = true;
            }
        }
        helper.assertTrue(respawned, "fixture: the dimension change left no respawn packet on the connection");
        return resent;
    }

    private static boolean carriesTimeMark(Object message, ServerPlayer owner, UUID epoch) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (carriesTimeMark(packet, owner, epoch)) {
                    return true;
                }
            }
            return false;
        }
        return message instanceof ClientboundCustomPayloadPacket packet
                && packet.payload() instanceof KillerQueenStateSyncPayload sync
                && sync.ownerUuid().equals(owner.getUUID())
                && sync.bitesTheDustMarked()
                && epoch.equals(sync.bitesTheDustMarkerEpoch());
    }

    // 1.16 LivingData saves the bomb with its owner as a bare UUID ("Bomb"), CapabilityHandler only sends it again
    // on login and on a dimension change, and PunchBomb.entityInRange looks that UUID up around the owner wherever
    // the owner is. Nothing in the donor drops the bomb when its owner or its target leaves and comes back.
    @GameTest(template = "empty", batch = "kqPrimaryBombLifetime", timeoutTicks = 40)
    public static void primaryBombOutlivesLogoutsAndDimensionChanges(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID ownerUuid = UUID.randomUUID();
        UUID markedUuid = UUID.randomUUID();
        BlockPos seat = pad.east(3);
        ServerPlayer[] owner = {mockPlayer(helper, ownerUuid, "kq-bomb-life", pad)};
        ServerPlayer[] marked = {mockPlayer(helper, markedUuid, "kq-bomb-marked", seat)};
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner[0], marked[0]);
        helper.testInfo.addListener(cleanup);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        cow.setPos(owner[0].getX(), owner[0].getY(), owner[0].getZ() + 3.0D);
        StandEntity[] stand = {null};
        // Each stage restores the bomb the port lost before it, so one run reports every loss.
        List<String> lost = new ArrayList<>();
        try {
            ServerLevel nether = server.getLevel(Level.NETHER);
            StandPower firstPower = StandPower.get(owner[0]);
            helper.assertTrue(
                    nether != null
                            && firstPower != null
                            && owner[0].connection.getConnection().channel() instanceof EmbeddedChannel,
                    "fixture: no Nether, no Stand power attachment or no recording connection");
            firstPower.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            KillerQueenState state = KillerQueenAddon.state(owner[0]);
            Vec3 home = owner[0].position();
            Vec3 away = new Vec3(1.5D, 80.0D, 1.5D);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && KillerQueenStateService.primaryBombTarget(owner[0]) == marked[0],
                    "fixture: the primary bomb on the player was rejected");

            marked[0].connection.disconnect(Component.literal("Killer Queen relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(markedUuid) == null,
                    "fixture: the marked player did not log out");
            boolean keptWhileOffline = markedUuid.equals(state.primaryTargetUuid());
            marked[0] = cleanup.join(mockPlayer(helper, markedUuid, "kq-bomb-marked", seat));
            if (!keptWhileOffline || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player logged out and came back");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            move(helper, marked[0], nether, away);
            boolean keptWhileAway = markedUuid.equals(state.primaryTargetUuid());
            move(helper, marked[0], level, Vec3.atBottomCenterOf(seat));
            if (!keptWhileAway || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player changed dimension and came back");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            EmbeddedChannel sent = (EmbeddedChannel) owner[0].connection.getConnection().channel();
            sent.outboundMessages().clear();
            move(helper, owner[0], nether, away);
            boolean keptInNether = markedUuid.equals(state.primaryTargetUuid());
            boolean resent = primaryBombSentAfterRespawn(helper, sent, owner[0], markedUuid);
            move(helper, owner[0], level, home);
            if (!keptInNether || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the owner changed dimension and came back");
            } else if (!resent) {
                lost.add("the owner changed dimension and the new client player was not sent the kept bomb");
            }

            move(helper, owner[0], nether, away);
            move(helper, marked[0], nether, away);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && markedUuid.equals(state.primaryTargetUuid()),
                    "fixture: the primary bomb placed in the Nether was rejected");
            move(helper, marked[0], level, Vec3.atBottomCenterOf(seat));
            move(helper, owner[0], level, home);
            if (KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("a bomb placed in the Nether, after its owner and its target both came to the Overworld "
                        + "(the donor's bomb is a UUID with no dimension)");
            }

            // No donor handler for a death or a Clone touches the bomb: a marked player who dies and respawns, or
            // who is cloned on the way back from the End, is still the bomb; a dead mob's body is the bomb until it
            // leaves the level, then its UUID matches nothing.
            Vec3 seatSpot = Vec3.atBottomCenterOf(seat);
            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], marked[0])
                            && markedUuid.equals(state.primaryTargetUuid()),
                    "fixture: the primary bomb on the player was rejected before the death");
            // A player who just changed dimension is invulnerable until the next tick clears the flag
            marked[0].hasChangedDimension();
            marked[0].kill();
            helper.assertTrue(marked[0].isDeadOrDying(), "fixture: the marked player did not die");
            boolean keptOnDeath = markedUuid.equals(state.primaryTargetUuid());
            marked[0] = cleanup.join(respawn(marked[0], false, Entity.RemovalReason.KILLED));
            marked[0].moveTo(seatSpot.x, seatSpot.y, seatSpot.z, 0.0F, 0.0F);
            if (!keptOnDeath || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player died and respawned");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            marked[0] = cleanup.join(respawn(marked[0], true, Entity.RemovalReason.CHANGED_DIMENSION));
            marked[0].moveTo(seatSpot.x, seatSpot.y, seatSpot.z, 0.0F, 0.0F);
            if (!markedUuid.equals(state.primaryTargetUuid())
                    || KillerQueenStateService.primaryBombTarget(owner[0]) != marked[0]) {
                lost.add("the marked player was cloned without dying (the return from the End)");
                KillerQueenStateService.markPrimaryBomb(owner[0], marked[0]);
            }

            Cow doomed = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
            doomed.setPos(owner[0].getX(), owner[0].getY(), owner[0].getZ() - 3.0D);
            try {
                helper.assertTrue(
                        KillerQueenStateService.markPrimaryBomb(owner[0], doomed)
                                && KillerQueenStateService.primaryBombTarget(owner[0]) == doomed,
                        "fixture: the primary bomb on the doomed cow was rejected");
                doomed.kill();
                helper.assertTrue(!doomed.isAlive(), "fixture: the marked cow did not die");
                if (KillerQueenStateService.primaryBombTarget(owner[0]) != doomed) {
                    lost.add("the marked mob died and its body, still in the level, was no longer the bomb");
                }
            } finally {
                doomed.discard();
            }
            if (state.hasPrimaryBomb()) {
                lost.add("the marked mob's body left the level and the bomb stayed on its owner");
                state.clearPrimaryBomb();
            }
            helper.assertTrue(lost.isEmpty(), "The primary bomb was lost where the donor keeps it: " + lost);

            helper.assertTrue(
                    KillerQueenStateService.markPrimaryBomb(owner[0], cow)
                            && KillerQueenStateService.primaryBombTarget(owner[0]) == cow,
                    "fixture: the primary bomb on the cow was rejected");
            owner[0].connection.disconnect(Component.literal("Killer Queen relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(ownerUuid) == null,
                    "fixture: the owner did not log out");
            owner[0] = cleanup.join(mockPlayer(helper, ownerUuid, "kq-bomb-life", null));
            StandPower power = StandPower.get(owner[0]);
            helper.assertTrue(
                    power != null && KillerQueenAddon.isKillerQueenUser(owner[0]),
                    "fixture: the Stand did not survive the relog");
            KillerQueenState reloaded = KillerQueenAddon.state(owner[0]);
            if (!cow.getUUID().equals(reloaded.primaryTargetUuid())
                    || KillerQueenStateService.primaryBombTarget(owner[0]) != cow) {
                lost.add("the owner logged out and came back (the donor saves the bomb with the player)");
            }
            helper.assertTrue(lost.isEmpty(), "The primary bomb was lost where the donor keeps it: " + lost);

            power.setStamina(power.getMaxStamina());
            helper.assertTrue(
                    KillerQueenAddon.KILLER_QUEEN.get().summon(owner[0], power),
                    "fixture: Killer Queen could not be summoned after the relog");
            stand[0] = power.getSummonedStandEntity();
            helper.assertTrue(stand[0] != null && stand[0].isAddedToLevel(),
                    "fixture: Killer Queen was not added to the level");
            cleanup.entities.add(stand[0]);
            Ability detonate = power.getAbility(KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME);
            helper.assertTrue(
                    detonate instanceof EntityActionAbility && detonate.isAbilityAvailable(power),
                    "The kept bomb cannot be detonated after the relog");
            float health = cow.getHealth();
            EntityActionInstance action = ((EntityActionAbility) detonate)
                    .initActionOnAbilityUse(level, owner[0], stand[0], null);
            action.setPhaseStart(ActionPhase.PERFORM);
            LivingComponentAction component = LivingComponentAction.getComponent(stand[0]);
            component.setAction(action, owner[0], SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "fixture: kq_entity_explo was not started");
            action._tickAction();
            component.setAction(null, owner[0], SyncType.NO_SYNC);
            helper.assertTrue(
                    !reloaded.hasPrimaryBomb() && (!cow.isAlive() || cow.getHealth() < health),
                    "The kept bomb did not explode on the cow and clear after the relog; cow health "
                            + cow.getHealth() + " of " + health);
        } finally {
            if (server.getPlayerList().getPlayer(markedUuid) == marked[0]) {
                marked[0].connection.disconnect(Component.literal("Killer Queen bomb GameTest complete"));
            }
            if (stand[0] != null) {
                stand[0].discard();
            }
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                KillerQueenStateService.clearAll(owner[0]);
                owner[0].connection.disconnect(Component.literal("Killer Queen bomb GameTest complete"));
            }
            cow.discard();
            sheerHeartPad(helper, false);
        }
        helper.succeed();
    }

    // 1.16 PunchBomb.replaceAction: while the bombed entity is within 16 blocks of its owner the kq_entitybomb slot
    // shows kq_entity_explo instead (Action.getVisibleAction), never both; farther away the plant comes back.
    @GameTest(template = "empty", batch = "kqPrimaryBombSlot", timeoutTicks = 20)
    public static void primaryBombSlotOffersTheDetonationInPlaceOfThePlant(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-bomb-slot", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        List<String> wrong = new ArrayList<>();
        try {
            owner.setGameMode(GameType.CREATIVE);
            StandPower power = StandPower.get(owner);
            helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            String plant = KillerQueenAddon.PRIMARY_BOMB_NAME;
            String detonate = KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME;
            int sharedSlots = 0;
            int detonateSlots = 0;
            rotp.core.powersystem.ability.controls.ControlSchemeTemplate template =
                    power.getPowerType().makeDefaultControlSchemeTemplate();
            for (var group : template.groups.values()) {
                for (var hotbar : group.hotbars) {
                    for (var slot : hotbar.slots) {
                        if (slot.values().stream().anyMatch(binds -> binds.containsValue(detonate))) {
                            detonateSlots++;
                        }
                        Map<InputMethod, String> base = slot.get(null);
                        if (base != null && plant.equals(base.get(InputMethod.CLICK))
                                && detonate.equals(base.get(InputMethod.HOLD))) {
                            sharedSlots++;
                        }
                    }
                }
            }
            if (sharedSlots != 1 || detonateSlots != 1) {
                wrong.add("kq_entity_explo is not the hold of the kq_entitybomb slot alone: " + sharedSlots
                        + " shared slot(s), " + detonateSlots + " slot(s) holding it");
            }
            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
            primaryBombOffer(power, true, false, "before a bomb", wrong);
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                    "fixture: the primary bomb was rejected");
            primaryBombOffer(power, false, true, "with the bombed cow 3 blocks away", wrong);
            // 1.16 MCUtil.entitiesAround: the cube of 16 around the centre of the owner's box, not a sphere
            cow.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ() + 12.0D);
            primaryBombOffer(power, false, true,
                    "with the bombed cow 12 blocks east and 12 south (17 away, inside the 1.16 cube)", wrong);
            rotp.core.powersystem.ability.condition.ConditionCheck press =
                    power.getAbility(detonate).checkSpecificConditions(power);
            if (!press.isPositive() && rotp.core.powersystem.ability.condition.ConditionCheck
                    .message("killer_queen_no_primary_bomb").equals(press.getWarning())) {
                wrong.add("with the bombed cow 12 blocks east and 12 south: kq_entity_explo is refused for want of "
                        + "a bomb in range");
            }
            cow.setPos(owner.getX() + 16.5D, owner.getY(), owner.getZ());
            primaryBombOffer(power, true, false, "with the bombed cow 16.5 blocks east, its box outside the cube",
                    wrong);
            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 20.0D);
            primaryBombOffer(power, true, false, "with the bombed cow 20 blocks away", wrong);
        } finally {
            cow.discard();
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen bomb slot GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "The primary bomb slot does not offer what 1.16 shows: " + wrong);
        helper.succeed();
    }

    // 1.16 PunchBomb.entityInRange (MCUtil.entitiesAround, no alive filter) still found a killed bombed mob while its
    // body lay in the level, up to 20 ticks: the slot kept kq_entity_explo and EntityExplode.standPerform exploded
    // at the body. Once the body is gone the plant comes back.
    @GameTest(template = "empty", batch = "kqPrimaryBombDeadBody", timeoutTicks = 60)
    public static void aKilledBombedMobStaysTheBombWhileItsBodyLies(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-bomb-dead-body", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        power.skipProgression();
        KillerQueenState state = KillerQueenAddon.state(owner);
        String detonate = KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME;
        List<String> wrong = new ArrayList<>();
        Cow detonated = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        detonated.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
        Cow left = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(detonated);
        cleanup.entities.add(left);
        // out of the first blast's reach
        left.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ());
        Runnable cleanUp = () -> {
            detonated.discard();
            left.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen dead body GameTest complete"));
            sheerHeartPad(helper, false);
        };
        try {
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, detonated),
                    "fixture: the primary bomb was rejected");
            detonated.kill();
            helper.assertTrue(detonated.isDeadOrDying() && !detonated.isRemoved(),
                    "fixture: the bombed cow did not die or its body is already gone");
            if (!state.hasPrimaryBomb() || KillerQueenStateService.primaryBombTarget(owner) != detonated) {
                wrong.add("the bombed cow was killed and its body, still in the level, is no longer the bomb");
            }
            primaryBombOffer(power, false, true, "with the bombed cow's body lying 3 blocks away", wrong);
            runAtFloor(helper, owner, power, stand, detonate, 250.0F, 0.0F, () -> !state.hasPrimaryBomb(),
                    "did not explode at the bombed cow's body", wrong);

            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, left),
                    "fixture: the second primary bomb was rejected");
            left.kill();
            helper.assertTrue(left.isDeadOrDying(), "fixture: the second bombed cow did not die");
        } catch (RuntimeException failure) {
            cleanUp.run();
            throw failure;
        }
        // LivingEntity.tickDeath removes the body after 20 ticks
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(left.isRemoved(), "fixture: the second cow's body was not removed");
                if (state.hasPrimaryBomb()) {
                    wrong.add("the bombed cow's body left the level and the bomb stayed on its owner");
                }
                primaryBombOffer(power, true, false, "after the bombed cow's body left the level", wrong);
            } finally {
                cleanUp.run();
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 kept a killed bombed mob as the bomb while its body lay: "
                    + wrong);
            helper.succeed();
        });
    }

    // 1.16 BlockBombAction.replaceAction: with a block bomb set, the kq_block_bomb slot is kq_block_explode, fired by
    // the slot's own click, never both; once the bomb is gone the plant comes back.
    @GameTest(template = "empty", batch = "kqBlockBombSlot", timeoutTicks = 20)
    public static void blockBombSlotOffersTheDetonationInPlaceOfThePlant(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-block-slot", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        List<String> wrong = new ArrayList<>();
        try {
            StandPower power = slotFixture(helper, owner);
            String plant = KillerQueenAddon.BLOCK_BOMB_NAME;
            String detonate = KillerQueenAddon.BLOCK_BOMB_DETONATE_NAME;
            sharedClickSlot(power, plant, detonate, wrong);
            slotOffer(owner, power, plant, plant, "before a bomb", wrong);
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, pad.below()),
                    "fixture: the block bomb was rejected");
            slotOffer(owner, power, plant, detonate, "with the block bomb set", wrong);
            // 1.16 showed it only while a Killer Queen stood within 9 blocks of the user's box (inflate(9.0))
            StandEntity stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "fixture: no summoned Stand");
            Vec3 home = stand.position();
            stand.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ());
            slotOffer(owner, power, plant, plant, "with the Stand 12 blocks from the user", wrong);
            stand.setPos(owner.getX() + 9.0D + owner.getBbWidth() / 2.0D + stand.getBbWidth() / 2.0D - 0.2D,
                    owner.getY(), owner.getZ());
            slotOffer(owner, power, plant, detonate, "with the Stand's box inside 9 blocks of the user's box", wrong);
            stand.setPos(home);
            KillerQueenStateService.clearBlockBomb(owner);
            slotOffer(owner, power, plant, plant, "after the bomb was cleared", wrong);
        } finally {
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen block bomb slot GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "The block bomb slot does not offer what 1.16 shows: " + wrong);
        helper.succeed();
    }

    // 1.16 SnowBomb.replaceAction: while the user holds soap (gloves with ammo) the kq_snow_bomb slot is
    // kq_bubble_bomb, which has no slot of its own.
    @GameTest(template = "empty", batch = "kqSnowBombSlot", timeoutTicks = 20)
    public static void snowBombSlotOffersTheBubbleBombWithSoap(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-snow-slot", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        List<String> wrong = new ArrayList<>();
        try {
            StandPower power = slotFixture(helper, owner);
            String snow = KillerQueenAddon.SNOW_BOMB_NAME;
            String bubble = KillerQueenAddon.BUBBLE_BOMB_NAME;
            sharedClickSlot(power, snow, bubble, wrong);
            slotOffer(owner, power, snow, snow, "without soap", wrong);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SOAP.get()));
            slotOffer(owner, power, snow, bubble, "with soap in hand", wrong);
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            slotOffer(owner, power, snow, snow, "once the soap is put away", wrong);
        } finally {
            owner.getInventory().clearContent();
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen snow bomb slot GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "The snow bomb slot does not offer what 1.16 shows: " + wrong);
        helper.succeed();
    }

    private static StandPower slotFixture(GameTestHelper helper, ServerPlayer owner) {
        owner.setGameMode(GameType.CREATIVE);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                "fixture: Killer Queen could not be summoned");
        return power;
    }

    // The base ability has one click slot of its own, and the replacement no slot at all
    private static void sharedClickSlot(StandPower power, String base, String replacement, List<String> wrong) {
        int baseSlots = 0;
        int replacementSlots = 0;
        rotp.core.powersystem.ability.controls.ControlSchemeTemplate template =
                power.getPowerType().makeDefaultControlSchemeTemplate();
        for (var group : template.groups.values()) {
            for (var hotbar : group.hotbars) {
                for (var slot : hotbar.slots) {
                    if (slot.values().stream().anyMatch(binds -> binds.containsValue(replacement))) {
                        replacementSlots++;
                    }
                    Map<InputMethod, String> binds = slot.get(null);
                    if (binds != null && base.equals(binds.get(InputMethod.CLICK))) {
                        baseSlots++;
                    }
                }
            }
        }
        if (baseSlots != 1 || replacementSlots != 0) {
            wrong.add(base + " has " + baseSlots + " click slot(s) and " + replacement + " " + replacementSlots
                    + " slot(s) of its own");
        }
    }

    private static void slotOffer(ServerPlayer owner, StandPower power, String slot, String expected, String when,
            List<String> wrong) {
        // a fresh resolution: the power caches its moves within a tick
        rotp.core.powersystem.ability.condition.AvailableAbilities available =
                new rotp.core.powersystem.ability.condition.AvailableAbilities();
        available.update(power, power.getMoveset());
        var container = available.getContextVariationContainer(slot);
        String shown = container != null ? container.ability.name() : "nothing";
        if (!expected.equals(shown)) {
            wrong.add(when + ": the " + slot + " slot shows " + shown + ", not " + expected);
        } else if (!AbilityInput.withConditionCheck(container, owner, InputMethod.CLICK)) {
            wrong.add(when + ": a click on the " + slot + " slot does not fire " + expected);
        }
    }

    private static void primaryBombOffer(StandPower power, boolean plantShown, boolean detonateShown, String when,
            List<String> wrong) {
        // a fresh resolution: the power caches its moves within a tick
        rotp.core.powersystem.ability.condition.AvailableAbilities available =
                new rotp.core.powersystem.ability.condition.AvailableAbilities();
        available.update(power, power.getMoveset());
        boolean plant = available.getContextVariation(KillerQueenAddon.PRIMARY_BOMB_NAME) != null;
        boolean detonate = available.getContextVariation(KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME) != null;
        if (plant != plantShown || detonate != detonateShown) {
            wrong.add(when + ": kq_entitybomb " + (plant ? "shown" : "hidden") + ", kq_entity_explo "
                    + (detonate ? "shown" : "hidden"));
        }
    }

    // 1.16 EntityQuitBomb.checkConditions asked only that Bites the Dust was not armed, and standPerform set the bomb
    // to the user's own UUID: with no bomb the kq_entity_quit slot was there, the press was accepted, the Stand
    // played the unbomb sound and the 50 stamina were paid.
    @GameTest(template = "empty", batch = "kqQuitNoBomb", timeoutTicks = 40)
    public static void entityQuitIsPressableWithNoBomb(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-quit-no-bomb", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        power.skipProgression();
        power.setStamina(400.0F);
        KillerQueenState state = KillerQueenAddon.state(owner);
        Runnable cleanUp = () -> {
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen quit with no bomb GameTest complete"));
            sheerHeartPad(helper, false);
        };
        List<String> wrong = new ArrayList<>();
        try {
            helper.assertTrue(!state.hasPrimaryBomb(), "fixture: the owner has a bomb");
            if (quitSlot(power) == null) {
                wrong.add("kq_entity_quit is hidden with no bomb");
            }
            if (!pressQuit(owner, power, (short) 94)) {
                wrong.add("the press of kq_entity_quit with no bomb was refused");
            } else {
                EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
                if (action == null || action.ability != power.getAbility(KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME)) {
                    wrong.add("the Stand did not start kq_entity_quit after the press");
                }
            }
        } catch (RuntimeException failure) {
            cleanUp.run();
            throw failure;
        }
        // the bar regenerates between ticks, so the payment is the largest single-tick drop
        float[] last = {power.getStamina()};
        float[] biggestDrop = {0.0F};
        helper.onEachTick(() -> {
            biggestDrop[0] = Math.max(biggestDrop[0], last[0] - power.getStamina());
            last[0] = power.getStamina();
        });
        helper.runAtTickTime(12, () -> {
            try {
                if (biggestDrop[0] < 40.0F) {
                    wrong.add("the 50 stamina were not paid, the bar dropped by " + biggestDrop[0] + " at most");
                }
                if (state.hasPrimaryBomb()) {
                    wrong.add("a bomb appeared");
                }
                helper.assertTrue(wrong.isEmpty(),
                        "1.16 let kq_entity_quit be pressed with no bomb and ran it all the same: " + wrong);
            } finally {
                cleanUp.run();
            }
            helper.succeed();
        });
    }

    // 1.16 kq_entity_quit: standWindupDuration(5), the default perform 1 and recovery 0
    @GameTest(template = "empty", batch = "kqQuitTiming", timeoutTicks = 40)
    public static void entityQuitRunsForTheDonorSixTicks(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-quit-time", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        power.skipProgression();
        power.setStamina(400.0F);
        KillerQueenState state = KillerQueenAddon.state(owner);
        ServerLevel level = helper.getLevel();
        Cow bombed = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(bombed);
        bombed.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
        Runnable cleanUp = () -> {
            bombed.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen quit timing GameTest complete"));
            sheerHeartPad(helper, false);
        };
        long[] pressedAt = {-1L};
        long[] clearedAfter = {-1L};
        long[] endedAfter = {-1L};
        try {
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, bombed),
                    "fixture: the primary bomb was rejected");
            helper.assertTrue(pressQuit(owner, power, (short) 92), "fixture: the kq_entity_quit press was refused");
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            helper.assertTrue(action != null && action.ability == power.getAbility(KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME),
                    "fixture: the Stand did not start kq_entity_quit");
            pressedAt[0] = level.getGameTime();
        } catch (RuntimeException failure) {
            cleanUp.run();
            throw failure;
        }
        helper.onEachTick(() -> {
            if (pressedAt[0] < 0L || endedAfter[0] >= 0L) {
                return;
            }
            long elapsed = level.getGameTime() - pressedAt[0];
            if (clearedAfter[0] < 0L && !state.hasPrimaryBomb()) {
                clearedAfter[0] = elapsed;
            }
            EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
            if (elapsed > 0L && (action == null || action.isOver())) {
                endedAfter[0] = elapsed;
            }
        });
        helper.runAtTickTime(22, () -> {
            try {
                helper.assertTrue(
                        clearedAfter[0] >= 6L && clearedAfter[0] <= 7L && endedAfter[0] >= 6L && endedAfter[0] <= 7L,
                        "The donor kq_entity_quit is windup 5 + perform 1 with no recovery: the bomb is cleared and "
                                + "the Stand is free 6 ticks after the press. Bomb cleared after " + clearedAfter[0]
                                + " ticks, action over after " + endedAfter[0] + " ticks");
            } finally {
                cleanUp.run();
            }
            helper.succeed();
        });
    }

    // 1.16 EntityQuitBomb replaced checkConditions without calling StandAction's, and its builder names no part:
    // a Stand without arms could press it.
    @GameTest(template = "empty", batch = "kqQuitNoArms", timeoutTicks = 40)
    public static void entityQuitWorksWithoutStandArms(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-quit-arms", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        power.skipProgression();
        power.setStamina(400.0F);
        KillerQueenState state = KillerQueenAddon.state(owner);
        Cow bombed = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(bombed);
        bombed.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
        StandInstance instance = power.getStandInstance().orElse(null);
        Runnable cleanUp = () -> {
            if (instance != null) {
                instance.addPart(StandPart.ARMS);
            }
            bombed.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen quit arms GameTest complete"));
            sheerHeartPad(helper, false);
        };
        boolean pressed;
        try {
            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, bombed),
                    "fixture: the primary bomb was rejected");
            helper.assertTrue(instance != null && instance.removePart(StandPart.ARMS),
                    "fixture: the Stand's arms could not be removed");
            pressed = pressQuit(owner, power, (short) 93);
        } catch (RuntimeException failure) {
            cleanUp.run();
            throw failure;
        }
        if (!pressed) {
            cleanUp.run();
        }
        helper.assertTrue(pressed, "The donor kq_entity_quit requires no Stand part, but the port refused it without arms");
        helper.runAtTickTime(12, () -> {
            try {
                helper.assertTrue(!state.hasPrimaryBomb(),
                        "kq_entity_quit did not clear the bomb for a Stand without arms");
            } finally {
                cleanUp.run();
            }
            helper.succeed();
        });
    }

    // A fresh resolution: the power caches its moves within a tick
    @Nullable
    private static AbilityConditionCheck quitSlot(StandPower power) {
        rotp.core.powersystem.ability.condition.AvailableAbilities available =
                new rotp.core.powersystem.ability.condition.AvailableAbilities();
        available.update(power, power.getMoveset());
        return available.getContextVariationContainer(KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME);
    }

    // The server side of a click, as the press handler resolves and checks it
    private static boolean pressQuit(ServerPlayer owner, StandPower power, short key) {
        AbilityConditionCheck slot = quitSlot(power);
        Ability quit = power.getAbility(KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME);
        if (slot == null || quit == null || !AbilityInput.withConditionCheck(slot, owner, InputMethod.CLICK)) {
            return false;
        }
        AbilityInput.keyPress(key, slot.ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                0.0F, BufferingState.clickCanBuffer(), quit.abilityId);
        AbilityInput.keyRelease(key, owner);
        return true;
    }

    // 1.16 SheerHeart is a mob saved with its own Owner UUID, and no handler removes it when its owner logs out or
    // changes dimension; isSHpresent and SHABack find it again around the owner when he is back.
    @GameTest(template = "empty", batch = "kqSheerHeartAbsentOwner", timeoutTicks = 40)
    public static void sheerHeartOutlivesItsOwnersLogoutAndDimensionChange(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        UUID ownerUuid = UUID.randomUUID();
        ServerPlayer[] owner = {mockPlayer(helper, ownerUuid, "kq-sha-absent", pad)};
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner[0]);
        helper.testInfo.addListener(cleanup);
        SheerHeartAttackEntity[] sheer = {null};
        List<String> lost = new ArrayList<>();
        try {
            ServerLevel nether = server.getLevel(Level.NETHER);
            owner[0].setGameMode(GameType.CREATIVE);
            StandPower power = StandPower.get(owner[0]);
            helper.assertTrue(nether != null && power != null, "fixture: no Nether or no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner[0], power),
                    "fixture: Killer Queen could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            sheer[0] = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(level);
            helper.assertTrue(stand != null && sheer[0] != null, "fixture: no Stand or no Sheer Heart Attack");
            cleanup.entities.add(sheer[0]);
            sheer[0].bind(owner[0], stand, null);
            sheer[0].setPos(owner[0].getX() + 2.0D, owner[0].getY(), owner[0].getZ());
            helper.assertTrue(!sheer[0].isRemoved() && level.addFreshEntity(sheer[0])
                            && KillerQueenStateService.linkSheerHeart(owner[0], sheer[0]),
                    "fixture: the Sheer Heart Attack was rejected");
            UUID sheerUuid = sheer[0].getUUID();

            owner[0].connection.disconnect(Component.literal("Killer Queen relog"));
            helper.assertTrue(server.getPlayerList().getPlayer(ownerUuid) == null,
                    "fixture: the owner did not log out");
            sheer[0].tick();
            if (sheer[0].isRemoved()) {
                lost.add("its owner logged out");
            }
            owner[0] = cleanup.join(mockPlayer(helper, ownerUuid, "kq-sha-absent", pad));
            owner[0].setGameMode(GameType.CREATIVE);
            KillerQueenState state = KillerQueenAddon.state(owner[0]);
            sheer[0].tick();
            if (sheer[0].isRemoved() || !sheerUuid.equals(state.sheerHeartUuid())) {
                lost.add("its owner logged back in");
            }

            Vec3 home = owner[0].position();
            move(helper, owner[0], nether, new Vec3(1.5D, 80.0D, 1.5D));
            owner[0].doTick();
            sheer[0].tick();
            if (sheer[0].isRemoved()) {
                lost.add("its owner went to the Nether");
            }
            StandPower netherPower = StandPower.get(owner[0]);
            Ability summon = netherPower.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
            if (summon == null || !summon.isAbilityAvailable(netherPower)) {
                lost.add("1.16 isSHpresent finds no bomb around its owner in the Nether, so he may summon one there");
            }
            move(helper, owner[0], level, home);
            owner[0].doTick();
            sheer[0].tick();
            if (sheer[0].isRemoved() || !sheerUuid.equals(state.sheerHeartUuid())) {
                lost.add("its owner came back from the Nether");
            }
            StandPower homePower = StandPower.get(owner[0]);
            Ability recall = homePower.getAbility(KillerQueenAddon.SHEER_HEART_RECALL_NAME);
            if (recall == null || !recall.isAbilityAvailable(homePower)) {
                lost.add("its owner cannot recall it after his return");
            }
        } finally {
            if (sheer[0] != null) {
                sheer[0].discard();
            }
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                KillerQueenStateService.clearAll(owner[0]);
                owner[0].connection.disconnect(Component.literal("Killer Queen SHA GameTest complete"));
            }
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(lost.isEmpty(), "The Sheer Heart Attack was dropped where 1.16 keeps it: " + lost);
        helper.succeed();
    }

    // 1.16 SheerHeart has no owner check in its AI: with its owner away it takes players other than him and monsters
    // as targets on its own, and explodeCreeper sources the blast to itself when getOwner() finds no owner.
    @GameTest(template = "empty", batch = "kqSheerHeartHunt", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartHuntsOnItsOwnWhileItsOwnerIsAway(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-hunt", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerPlayer[] stranger = {null};
        SheerHeartAttackEntity[] sheer = {null};
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 1, 2, 1);
        cleanup.entities.add(husk);
        List<String> wrong = new ArrayList<>();
        // A dying mob forgets an attacker that is gone, so the hit is taken as it lands
        List<Entity> hitBy = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> hits = event -> {
            if (event.getEntity() == husk && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                hitBy.add(event.getSource().getEntity());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, hits);
        try {
            owner.setGameMode(GameType.CREATIVE);
            StandPower power = StandPower.get(owner);
            helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                    "fixture: Killer Queen could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            sheer[0] = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(level);
            helper.assertTrue(stand != null && sheer[0] != null, "fixture: no Stand or no Sheer Heart Attack");
            cleanup.entities.add(sheer[0]);
            sheer[0].bind(owner, stand, null);
            sheer[0].moveTo(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D, -90.0F, 0.0F);
            helper.assertTrue(!sheer[0].isRemoved() && level.addFreshEntity(sheer[0])
                            && KillerQueenStateService.linkSheerHeart(owner, sheer[0]),
                    "fixture: the Sheer Heart Attack was rejected");
            husk.setPos(sheer[0].getX() + 2.5D, sheer[0].getY(), sheer[0].getZ());
            owner.connection.disconnect(Component.literal("Killer Queen owner away"));
            stranger[0] = cleanup.join(mockPlayer(helper, UUID.randomUUID(), "kq-sha-stranger", pad));
            stranger[0].setGameMode(GameType.SURVIVAL);
            // Out of the bomb's 16-block follow range: only the monster is in reach
            stranger[0].setPos(pad.getX() + 40.5D, pad.getY(), pad.getZ() + 0.5D);
            if (!sheer[0].canAttack(husk)) {
                wrong.add("it does not take a monster as its target");
            }
            if (!sheer[0].canAttack(stranger[0])) {
                wrong.add("it does not take another player as its target");
            }
        } catch (RuntimeException | Error failure) {
            NeoForge.EVENT_BUS.unregister(hits);
            huntCleanup(helper, sheer[0], husk, stranger[0]);
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 100, () -> sheer[0].isRemoved() || hitBy.contains(sheer[0]), () -> {
            NeoForge.EVENT_BUS.unregister(hits);
            huntCleanup(helper, sheer[0], husk, stranger[0]);
            if (!hitBy.contains(sheer[0])) {
                wrong.add("no blast of its own hit the monster beside it (hits from " + hitBy + ")");
            }
            helper.assertTrue(wrong.isEmpty(),
                    "With its owner away the Sheer Heart Attack does not hunt as in 1.16: " + wrong);
            helper.succeed();
        });
    }

    private static void huntCleanup(GameTestHelper helper, Entity sheer, Entity husk, ServerPlayer stranger) {
        if (sheer != null) {
            sheer.discard();
        }
        husk.discard();
        if (stranger != null) {
            stranger.connection.disconnect(Component.literal("Killer Queen SHA hunt GameTest complete"));
        }
        sheerHeartPad(helper, false);
    }

    // 1.16 SheerHeart.explodeCreeper removes the bomb when its blast leaves its target dead, and isSHpresent then
    // finds no bomb, so Sheer Heart Attack can be sent again.
    @GameTest(template = "empty", batch = "kqSheerHeartKill", timeoutTicks = 40)
    public static void aSheerHeartWhoseBlastKillsItsTargetIsGone(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-kill", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        SheerHeartAttackEntity sheer = linkedSheerHeart(helper, owner, pad);
        cleanup.entities.add(sheer);
        StandPower power = StandPower.get(owner);
        KillerQueenState state = KillerQueenAddon.state(owner);
        cow.setPos(sheer.getX() + 1.0D, sheer.getY(), sheer.getZ());
        sheer.setTarget(cow);
        helper.runAtTickTime(30, () -> {
            boolean dead = !cow.isAlive();
            boolean removed = sheer.isRemoved();
            boolean held = state.hasSheerHeart();
            Ability summon = power.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
            boolean offered = summon != null && summon.isAbilityAvailable(power);
            sheer.discard();
            cow.discard();
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen SHA kill GameTest complete"));
            sheerHeartPad(helper, false);
            helper.assertTrue(dead, "fixture: the cow survived the blast");
            List<String> wrong = new ArrayList<>();
            if (!removed) {
                wrong.add("the bomb whose blast killed its target stayed");
            }
            if (held || !offered) {
                wrong.add("Sheer Heart Attack stayed refused after its bomb was gone");
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 removes a Sheer Heart Attack that kills its target: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeartAttack.isSHpresent looked only for a bomb within 140 blocks of the user, and nothing removes a
    // bomb for its owner's distance: one left farther away stays and hunts but no longer blocks a new summon, and it
    // lives on beside the second one when its owner comes back.
    @GameTest(template = "empty", batch = "kqSheerHeartFarOwner", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartLeftFarFromItsOwnerFreesTheSummon(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-far", pad);
        MockPlayerCleanup playerCleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(playerCleanup);
        SheerHeartAttackEntity[] sheer = {linkedSheerHeart(helper, owner, pad), null};
        playerCleanup.entities.add(sheer[0]);
        StandPower power = StandPower.get(owner);
        StandEntity stand = power.getSummonedStandEntity();
        KillerQueenState state = KillerQueenAddon.state(owner);
        Ability summon = power.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
        Husk husk = toughHuskBeside(helper, sheer[0]);
        playerCleanup.entities.add(husk);
        List<DamageSource> blasts = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> hits = recordBlastsOn(husk, blasts);
        List<String> wrong = new ArrayList<>();
        Vec3 home = owner.position();
        Vec3 far = home.add(SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 20.0D, 0.0D, 0.0D);
        // The bomb is an ordinary despawning mob: a player near it, not its owner, is what keeps it
        ServerPlayer bystander = playerCleanup.join(
                mockPlayer(helper, UUID.randomUUID(), "kq-sha-far-bystander", pad.south(6)));
        bystander.setGameMode(GameType.CREATIVE);
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(hits);
            ownedSheerHearts(owner).forEach(Entity::discard);
            husk.discard();
            KillerQueenStateService.clearAll(owner);
            bystander.connection.disconnect(Component.literal("Killer Queen SHA far GameTest complete"));
            owner.connection.disconnect(Component.literal("Killer Queen SHA far GameTest complete"));
            sheerHeartPad(helper, false);
        };
        try {
            helper.assertTrue(summon != null && !summon.isAbilityAvailable(power),
                    "fixture: Sheer Heart Attack was offered with its bomb beside the owner");
            owner.moveTo(far.x, far.y, far.z, owner.getYRot(), owner.getXRot());
            // The bomb's own tick, before any tick of its owner
            sheer[0].tick();
            if (sheer[0].isRemoved()) {
                wrong.add("the bomb was removed for its owner 160 blocks away");
            }
            if (state.hasSheerHeart()) {
                wrong.add("the bomb's own tick did not let the summon go with its owner 160 blocks away");
            }
            // A bomb that has not ticked since its owner left, as in a chunk that stopped ticking
            sheer[1] = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
            helper.assertTrue(sheer[1] != null, "fixture: no Sheer Heart Attack");
            sheer[1].bind(owner, stand, null);
            sheer[1].moveTo(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 4.5D, -90.0F, 0.0F);
            helper.assertTrue(!sheer[1].isRemoved() && helper.getLevel().addFreshEntity(sheer[1])
                            && KillerQueenStateService.linkSheerHeart(owner, sheer[1]),
                    "fixture: the unticked Sheer Heart Attack was rejected");
            if (!summon.isAbilityAvailable(power) || state.hasSheerHeart()) {
                wrong.add("Sheer Heart Attack stayed refused with its only bomb 160 blocks away");
            }
            sheer[1].discard();
            aimStand(stand, ActionTarget.EMPTY);
            if (startAndPerform(helper, owner, power, stand, KillerQueenAddon.SHEER_HEART_NAME,
                    power.getMaxStamina(), wrong)) {
                UUID second = state.sheerHeartUuid();
                if (second == null || second.equals(sheer[0].getUUID())) {
                    wrong.add("no second Sheer Heart Attack was sent with the first one 160 blocks away");
                } else {
                    owner.moveTo(home.x, home.y, home.z, owner.getYRot(), owner.getXRot());
                    sheer[0].tick();
                    if (sheer[0].isRemoved()) {
                        wrong.add("the first bomb was removed when its owner came back with a second one out");
                    } else if (!second.equals(state.sheerHeartUuid())) {
                        wrong.add("the first bomb took the summon from the second one");
                    }
                    // The second bomb has been sent; the first now hunts alone with its owner far again. A recall
                    // here would take both, as 1.16 SHABack does.
                    ownedSheerHearts(owner).stream()
                            .filter(bomb -> second.equals(bomb.getUUID()))
                            .forEach(Entity::discard);
                    KillerQueenStateService.releaseSheerHeart(owner, second);
                    owner.moveTo(far.x, far.y, far.z, owner.getYRot(), owner.getXRot());
                }
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> sheer[0].isRemoved() || !blasts.isEmpty(), () -> {
            try {
                if (sheer[0].isRemoved()) {
                    wrong.add("the first bomb did not stay while its owner was 160 blocks away");
                } else if (blasts.isEmpty()) {
                    wrong.add("the first bomb did not blast the monster beside it with its owner 160 blocks away");
                }
            } finally {
                cleanup.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps a Sheer Heart Attack far from its owner and frees the summon: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no death handler and no Killer Queen handler touches it on a death: the bomb of an owner who
    // died stays and hunts, and isSHpresent finds it around him again when he respawns beside it.
    @GameTest(template = "empty", batch = "kqSheerHeartOwnerDeath", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartOutlivesItsOwnersDeath(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        MinecraftServer server = helper.getLevel().getServer();
        UUID ownerUuid = UUID.randomUUID();
        ServerPlayer[] owner = {mockPlayer(helper, ownerUuid, "kq-sha-death", pad)};
        MockPlayerCleanup playerCleanup = new MockPlayerCleanup(helper, owner[0]);
        helper.testInfo.addListener(playerCleanup);
        SheerHeartAttackEntity sheer = linkedSheerHeart(helper, owner[0], pad);
        playerCleanup.entities.add(sheer);
        Husk husk = toughHuskBeside(helper, sheer);
        playerCleanup.entities.add(husk);
        List<DamageSource> blasts = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> hits = recordBlastsOn(husk, blasts);
        List<String> wrong = new ArrayList<>();
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(hits);
            sheer.discard();
            husk.discard();
            if (server.getPlayerList().getPlayer(ownerUuid) == owner[0]) {
                KillerQueenStateService.clearAll(owner[0]);
                owner[0].connection.disconnect(Component.literal("Killer Queen SHA death GameTest complete"));
            }
            sheerHeartPad(helper, false);
        };
        try {
            KillerQueenState state = KillerQueenAddon.state(owner[0]);
            owner[0].setGameMode(GameType.SURVIVAL);
            owner[0].kill();
            helper.assertTrue(owner[0].isDeadOrDying(), "fixture: the owner did not die");
            if (sheer.isRemoved()) {
                wrong.add("its owner's death removed it");
            } else {
                sheer.tick();
                if (sheer.isRemoved()) {
                    wrong.add("it dropped itself on its first tick after its owner's death");
                }
            }
            if (state.hasSheerHeart()) {
                wrong.add("its dead owner still holds the summon");
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> sheer.isRemoved() || !blasts.isEmpty(), () -> {
            try {
                if (!sheer.isRemoved() && blasts.isEmpty()) {
                    wrong.add("it did not blast the monster beside it while its owner was dead");
                }
                owner[0] = playerCleanup.join(respawn(owner[0], false, Entity.RemovalReason.KILLED));
                owner[0].moveTo(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0.0F, 0.0F);
                if (!sheer.isRemoved()) {
                    sheer.tick();
                }
                if (sheer.isRemoved()) {
                    wrong.add("it was gone after its owner died and respawned");
                } else if (!sheer.getUUID().equals(KillerQueenAddon.state(owner[0]).sheerHeartUuid())) {
                    wrong.add("it was not his again when he respawned beside it");
                }
            } finally {
                cleanup.run();
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 keeps a Sheer Heart Attack whose owner died: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no Stand check of its own: when its owner, alive and beside it, no longer has Killer Queen it
    // stays and hunts, and isSHpresent counts it around him again once he has the Stand back.
    @GameTest(template = "empty", batch = "kqSheerHeartStandLost", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartOutlivesItsOwnersLossOfKillerQueen(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-standless", pad);
        MockPlayerCleanup playerCleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(playerCleanup);
        SheerHeartAttackEntity sheer = linkedSheerHeart(helper, owner, pad);
        playerCleanup.entities.add(sheer);
        StandPower power = StandPower.get(owner);
        KillerQueenState state = KillerQueenAddon.state(owner);
        Husk husk = toughHuskBeside(helper, sheer);
        playerCleanup.entities.add(husk);
        List<DamageSource> blasts = new ArrayList<>();
        Consumer<LivingIncomingDamageEvent> hits = recordBlastsOn(husk, blasts);
        List<String> wrong = new ArrayList<>();
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(hits);
            ownedSheerHearts(owner).forEach(Entity::discard);
            husk.discard();
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen SHA Stand loss GameTest complete"));
            sheerHeartPad(helper, false);
        };
        try {
            power.setStand(null);
            helper.assertTrue(!KillerQueenAddon.isKillerQueenUser(owner), "fixture: the owner kept Killer Queen");
            // A mock player runs no player tick by itself; his own tick is what lets go of the summon
            owner.doTick();
            helper.assertTrue(!state.hasSheerHeart(), "fixture: his state still held the bomb after his tick");
            sheer.tick();
            if (sheer.isRemoved()) {
                wrong.add("it was removed when its owner beside it lost Killer Queen");
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> sheer.isRemoved() || !blasts.isEmpty(), () -> {
            try {
                if (sheer.isRemoved()) {
                    wrong.add("it did not stay while its owner had no Killer Queen");
                } else {
                    if (blasts.isEmpty()) {
                        wrong.add("it did not blast the monster beside it while its owner had no Killer Queen");
                    }
                    power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
                    helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                            "fixture: Killer Queen could not be summoned again");
                    sheer.tick();
                    Ability summon = power.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
                    if (sheer.isRemoved() || !sheer.getUUID().equals(state.sheerHeartUuid())) {
                        wrong.add("it was not his again when he had Killer Queen back beside it");
                    } else if (summon == null || summon.isAbilityAvailable(power)) {
                        wrong.add("Sheer Heart Attack was offered again with his bomb beside him");
                    }
                }
            } finally {
                cleanup.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps a Sheer Heart Attack whose owner lost Killer Queen: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart has no dimension check: a bomb that goes to another dimension by itself, as through a portal,
    // lives on and hunts there, and isSHpresent counts it around its owner there. The bomb is sent from the Nether to
    // this test's pad by the same changeDimension a portal runs, so no Nether chunk has to be generated.
    @GameTest(template = "empty", batch = "kqSheerHeartOtherDimension", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartLivesOnInTheDimensionItReaches(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-portal", pad);
        MockPlayerCleanup playerCleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(playerCleanup);
        Entity[] arrived = {null};
        Husk[] husk = {null};
        List<DamageSource> blasts = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Runnable cleanup = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            for (Entity entity : new Entity[] {arrived[0], husk[0]}) {
                if (entity != null) {
                    entity.discard();
                }
            }
            ownedSheerHearts(owner).forEach(Entity::discard);
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen SHA dimension GameTest complete"));
            sheerHeartPad(helper, false);
        };
        try {
            helper.assertTrue(nether != null, "fixture: no Nether");
            owner.setGameMode(GameType.CREATIVE);
            StandPower power = StandPower.get(owner);
            helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                    "fixture: Killer Queen could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            SheerHeartAttackEntity sent = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(nether);
            helper.assertTrue(stand != null && sent != null, "fixture: no Stand or no Sheer Heart Attack");
            // Summoned in the Nether
            sent.bind(owner, stand, null);
            helper.assertTrue(!sent.isRemoved(), "fixture: the Sheer Heart Attack was rejected");
            arrived[0] = sent.changeDimension(new DimensionTransition(level,
                    new Vec3(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D), Vec3.ZERO, -90.0F, 0.0F,
                    DimensionTransition.DO_NOTHING));
            if (arrived[0] != null) {
                playerCleanup.entities.add(arrived[0]);
            }
            helper.assertTrue(arrived[0] instanceof SheerHeartAttackEntity bomb && bomb.level() == level
                            && sent.getUUID().equals(bomb.getUUID()) && bomb.isOwnedBy(owner.getUUID()),
                    "fixture: the Sheer Heart Attack did not reach this dimension");
            SheerHeartAttackEntity bomb = (SheerHeartAttackEntity) arrived[0];
            bomb.tick();
            if (bomb.isRemoved()) {
                wrong.add("it was removed in the dimension it reached");
            } else {
                KillerQueenState state = KillerQueenAddon.state(owner);
                Ability summon = power.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
                if (!bomb.getUUID().equals(state.sheerHeartUuid())) {
                    wrong.add("it did not hold its owner's summon beside him");
                } else if (summon == null || summon.isAbilityAvailable(power)) {
                    wrong.add("Sheer Heart Attack was offered again with his bomb beside him");
                }
                husk[0] = toughHuskBeside(helper, bomb);
                playerCleanup.entities.add(husk[0]);
                hits.add(recordBlastsOn(husk[0], blasts));
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> arrived[0].isRemoved() || !blasts.isEmpty(), () -> {
            try {
                if (arrived[0].isRemoved()) {
                    wrong.add("it did not stay in the dimension it reached");
                } else if (blasts.isEmpty()) {
                    wrong.add("it did not blast the monster beside it in the dimension it reached");
                }
            } finally {
                cleanup.run();
            }
            helper.assertTrue(wrong.isEmpty(),
                    "1.16 keeps a Sheer Heart Attack that went to another dimension: " + wrong);
            helper.succeed();
        });
    }

    // 1.16 SheerHeart without an owner, as /summon makes it, is a mob like any other: it stays, hunts, and its blast is
    // its own (explodeCreeper 298). ModEntityTypes 35 registers it summonable and 0.85 x 0.7.
    @GameTest(template = "empty", batch = "kqSheerHeartOwnerless", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void anOwnerlessSheerHeartLivesAndHunts(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        List<SheerHeartAttackEntity> summoned = new ArrayList<>();
        Vec3 at = new Vec3(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D);
        String refused = summonByCommand(level, KillerQueenAddon.SHEER_HEART_ENTITY.get(), at);
        summoned.addAll(level.getEntities(KillerQueenAddon.SHEER_HEART_ENTITY.get(),
                new AABB(at, at).inflate(0.5D), sheer -> !sheer.isRemoved()));
        Husk[] husk = {null};
        List<DamageSource> blasts = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        Runnable cleanup = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            summoned.forEach(Entity::discard);
            if (husk[0] != null) {
                husk[0].discard();
            }
            sheerHeartPad(helper, false);
        };
        try {
            helper.assertTrue(refused == null && summoned.size() == 1 && summoned.getFirst().getOwnerUuid() == null,
                    "/summon made no Sheer Heart Attack without an owner (the command said: " + refused + ")");
            SheerHeartAttackEntity sheer = summoned.getFirst();
            if (sheer.getBbWidth() != 0.85F || sheer.getBbHeight() != 0.7F) {
                wrong.add("its hitbox is " + sheer.getBbWidth() + " x " + sheer.getBbHeight() + ", not 1.16's 0.85 x 0.7");
            }
            husk[0] = toughHuskBeside(helper, sheer);
            hits.add(recordBlastsOn(husk[0], blasts));
            sheer.tick();
            if (sheer.isRemoved()) {
                wrong.add("it was removed for having no owner");
            }
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
        checkOnceTheSheerHeartBlasted(helper, 80, () -> summoned.getFirst().isRemoved()
                || blasts.stream().anyMatch(source -> source.getEntity() == summoned.getFirst()), () -> {
            SheerHeartAttackEntity sheer = summoned.getFirst();
            try {
                if (sheer.isRemoved()) {
                    wrong.add("it did not stay without an owner");
                } else if (blasts.stream().noneMatch(source -> source.getEntity() == sheer)) {
                    wrong.add("no blast of its own hit the monster beside it (hits from "
                            + blasts.stream().map(DamageSource::getEntity).toList() + ")");
                }
            } finally {
                cleanup.run();
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 keeps a Sheer Heart Attack without an owner: " + wrong);
            helper.succeed();
        });
    }

    // The blast checks above wait for a first target that comes late, by 1.16's random roll or, here, because the
    // monster only arrives at tick 90, past the checks' first tick (80).
    @GameTest(template = "empty", batch = "kqSheerHeartLateTarget", timeoutTicks = SHEER_HEART_BLAST_TIMEOUT)
    public static void sheerHeartBlastChecksAwaitALateFirstTarget(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-late", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        SheerHeartAttackEntity sheer = linkedSheerHeart(helper, owner, pad);
        cleanup.entities.add(sheer);
        double walk = sheer.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        Husk[] husk = {null};
        List<DamageSource> blasts = new ArrayList<>();
        List<Consumer<LivingIncomingDamageEvent>> hits = new ArrayList<>();
        Runnable done = () -> {
            hits.forEach(NeoForge.EVENT_BUS::unregister);
            sheer.discard();
            if (husk[0] != null) {
                husk[0].discard();
            }
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen SHA late target GameTest complete"));
            sheerHeartPad(helper, false);
        };
        // It stays where the monster will come
        sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        helper.runAtTickTime(90, () -> {
            try {
                sheer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(walk);
                husk[0] = toughHuskBeside(helper, sheer);
                hits.add(recordBlastsOn(husk[0], blasts));
            } catch (RuntimeException | Error failure) {
                done.run();
                throw failure;
            }
        });
        checkOnceTheSheerHeartBlasted(helper, 80, () -> sheer.isRemoved() || !blasts.isEmpty(), () -> {
            long checkedAt = helper.getTick();
            boolean removed = sheer.isRemoved();
            done.run();
            helper.assertTrue(!removed && !blasts.isEmpty() && checkedAt > 90,
                    "A Sheer Heart blast check did not wait for a first target that came at tick 90: checked at tick "
                            + checkedAt + ", the bomb removed " + removed + ", " + blasts.size() + " blasts");
            helper.succeed();
        });
    }

    // 1.16 SHABack removes every SheerHeart whose owner is the user inside the 140-block box of isSHpresent, linked
    // or not, and leaves the others; isSHpresent then finds none and Sheer Heart Attack can be sent again.
    @GameTest(template = "empty", batch = "kqSheerHeartRecallBox", timeoutTicks = 20)
    public static void recallTakesEveryBombOfItsOwnerWithin140Blocks(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = mockPlayer(helper, UUID.randomUUID(), "kq-sha-recall", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        SheerHeartAttackEntity linked = linkedSheerHeart(helper, owner, pad);
        cleanup.entities.add(linked);
        StandPower power = StandPower.get(owner);
        StandEntity stand = power.getSummonedStandEntity();
        KillerQueenState state = KillerQueenAddon.state(owner);
        List<SheerHeartAttackEntity> made = new ArrayList<>(List.of(linked));
        List<String> wrong = new ArrayList<>();
        try {
            SheerHeartAttackEntity earlier = ownersBomb(helper, owner, stand, owner.position().add(3.0D, 0.0D, 4.0D));
            // Outside the 140-block box, in this test's own loaded chunk
            SheerHeartAttackEntity far = ownersBomb(helper, owner, stand,
                    owner.position().add(0.0D, SheerHeartAttackEntity.MAX_OWNER_DISTANCE + 10.0D, 0.0D));
            SheerHeartAttackEntity ownerless = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(level);
            helper.assertTrue(ownerless != null, "fixture: no Sheer Heart Attack");
            ownerless.moveTo(owner.getX() - 3.0D, owner.getY(), owner.getZ(), 0.0F, 0.0F);
            helper.assertTrue(level.addFreshEntity(ownerless), "fixture: the ownerless bomb was rejected");
            made.addAll(List.of(earlier, far, ownerless));
            cleanup.entities.addAll(made);
            helper.assertTrue(level.getEntities(KillerQueenAddon.SHEER_HEART_ENTITY.get(), far.getBoundingBox(),
                            sheer -> sheer == far).size() == 1,
                    "fixture: the far bomb cannot be found in the level");
            helper.assertTrue(linked.getUUID().equals(state.sheerHeartUuid()),
                    "fixture: the first bomb does not hold the summon");
            if (startAndPerform(helper, owner, power, stand, KillerQueenAddon.SHEER_HEART_RECALL_NAME,
                    power.getMaxStamina(), wrong)) {
                if (!linked.isRemoved()) {
                    wrong.add("the recall left the bomb that holds the summon");
                }
                if (!earlier.isRemoved()) {
                    wrong.add("the recall left another bomb of his 5 blocks away");
                }
                if (far.isRemoved()) {
                    wrong.add("the recall took a bomb of his 150 blocks above him");
                }
                if (ownerless.isRemoved()) {
                    wrong.add("the recall took a bomb that is not his");
                }
                Ability summon = power.getAbility(KillerQueenAddon.SHEER_HEART_NAME);
                if (state.hasSheerHeart() || summon == null || !summon.isAbilityAvailable(power)) {
                    wrong.add("Sheer Heart Attack was not offered again after the recall");
                }
            }
        } finally {
            made.forEach(Entity::discard);
            KillerQueenStateService.clearAll(owner);
            owner.connection.disconnect(Component.literal("Killer Queen SHA recall GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 SHABack removes every bomb of its user within 140 blocks: " + wrong);
        helper.succeed();
    }

    // The real /summon with the server console's permission; the command's failure text, or null when it ran
    static String summonByCommand(ServerLevel level, EntityType<?> type, Vec3 at) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(at);
        String command = String.format(Locale.ROOT, "summon %s %.2f %.2f %.2f",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), at.x, at.y, at.z);
        try {
            level.getServer().getCommands().getDispatcher().execute(command, source);
            return null;
        } catch (CommandSyntaxException failure) {
            return failure.getMessage();
        }
    }

    // A bomb of the owner's that does not hold his summon, as one left from an earlier summon
    private static SheerHeartAttackEntity ownersBomb(
            GameTestHelper helper, ServerPlayer owner, StandEntity stand, Vec3 at) {
        SheerHeartAttackEntity sheer = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(sheer != null, "fixture: no Sheer Heart Attack");
        sheer.bind(owner, stand, null);
        sheer.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        helper.assertTrue(!sheer.isRemoved() && helper.getLevel().addFreshEntity(sheer),
                "fixture: a Sheer Heart Attack was rejected");
        return sheer;
    }

    // 1.16 SheerHeart takes its first target through NearestAttackableTargetGoal, a 1-in-10 roll each tick (1-in-5 on
    // each second tick here) with no upper bound on the wait, and swells 15 ticks more before it blasts. A check on its
    // blast runs on the first tick from fromTick on at which settled holds, else at the deadline, so a late first
    // target is waited for instead of failing the test by chance.
    static final int SHEER_HEART_BLAST_DEADLINE = 600;
    static final int SHEER_HEART_BLAST_TIMEOUT = SHEER_HEART_BLAST_DEADLINE + 20;

    static void checkOnceTheSheerHeartBlasted(
            GameTestHelper helper, long fromTick, BooleanSupplier settled, Runnable check) {
        boolean[] checked = {false};
        for (long tick = fromTick; tick <= SHEER_HEART_BLAST_DEADLINE; tick++) {
            boolean last = tick == SHEER_HEART_BLAST_DEADLINE;
            helper.runAtTickTime(tick, () -> {
                if (!checked[0] && (last || settled.getAsBoolean())) {
                    checked[0] = true;
                    check.run();
                }
            });
        }
    }

    // A monster 2.5 blocks from the bomb that its blasts do not kill, so the bomb stays (1.16 explodeCreeper 304-306)
    private static Husk toughHuskBeside(GameTestHelper helper, SheerHeartAttackEntity sheer) {
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, 1, 2, 1);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        husk.setHealth(1000.0F);
        husk.setPos(sheer.getX() + 2.5D, sheer.getY(), sheer.getZ());
        return husk;
    }

    private static Consumer<LivingIncomingDamageEvent> recordBlastsOn(LivingEntity victim, List<DamageSource> hits) {
        Consumer<LivingIncomingDamageEvent> listener = event -> {
            if (event.getEntity() == victim && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
                hits.add(event.getSource());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, listener);
        return listener;
    }

    private static List<SheerHeartAttackEntity> ownedSheerHearts(ServerPlayer owner) {
        return List.copyOf(((ServerLevel) owner.level()).getEntities(
                KillerQueenAddon.SHEER_HEART_ENTITY.get(),
                sheer -> !sheer.isRemoved() && sheer.isOwnedBy(owner.getUUID())));
    }

    // Creative Killer Queen user with the Stand out and a linked Sheer Heart Attack 3 blocks away on the pad.
    public static SheerHeartAttackEntity linkedSheerHeart(GameTestHelper helper, ServerPlayer owner, BlockPos pad) {
        owner.setGameMode(GameType.CREATIVE);
        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                "fixture: Killer Queen could not be summoned");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null && stand.isAddedToLevel(), "fixture: Killer Queen was not added to the level");
        stand.summonLockTicks = 0;
        SheerHeartAttackEntity sheer = KillerQueenAddon.SHEER_HEART_ENTITY.get().create(helper.getLevel());
        helper.assertTrue(sheer != null, "fixture: no Sheer Heart Attack");
        sheer.bind(owner, stand, null);
        sheer.moveTo(pad.getX() + 3.5D, pad.getY(), pad.getZ() + 0.5D, -90.0F, 0.0F);
        helper.assertTrue(!sheer.isRemoved() && helper.getLevel().addFreshEntity(sheer)
                        && KillerQueenStateService.linkSheerHeart(owner, sheer),
                "fixture: the Sheer Heart Attack was rejected");
        return sheer;
    }

    // 1.16 StandEntityTask pays a Stand action's cost after standPerform and StandAction.onPerform before perform,
    // both with the result ignored, and the bomb kit has no stamina condition of its own: on a short bar every one
    // of these starts and acts, the bar drops to 0 and no stamina message is sent.
    @GameTest(template = "empty", batch = "kqStaminaShortBar", timeoutTicks = 20)
    public static void bombKitStartsOnAShortBarAndDrainsIt(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-short-bar", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        KillerQueenState state = KillerQueenAddon.state(owner);
        EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
        List<String> wrong = new ArrayList<>();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        // In front of the user, who looks along +Z
        cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 1.5D);
        try {
            sent.outboundMessages().clear();
            aimStand(stand, new ActionTarget(cow));
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_NAME, 100.0F,
                    () -> KillerQueenStateService.primaryBombTarget(owner) == cow, wrong);
            aimStand(stand, ActionTarget.EMPTY);
            if (!state.hasPrimaryBomb()) {
                helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                        "fixture: the primary bomb was rejected");
            }
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_CLEAR_NAME, 30.0F,
                    () -> !state.hasPrimaryBomb(), wrong);

            BlockPos block = pad.offset(4, 0, -2);
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, block),
                    "fixture: the block bomb was rejected");
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.BLOCK_BOMB_CLEAR_NAME, 2.0F,
                    () -> !state.hasBlockBomb(), wrong);
            if (!state.hasBlockBomb()) {
                helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, block),
                        "fixture: the second block bomb was rejected");
            }
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.BLOCK_BOMB_DETONATE_NAME, 100.0F,
                    () -> !state.hasBlockBomb(), wrong);

            owner.getInventory().add(new ItemStack(Items.SNOWBALL, 2));
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.SNOW_BOMB_NAME, 50.0F,
                    () -> owner.getInventory().countItem(Items.SNOWBALL) == 1 && ownsBombProjectile(owner), wrong);
            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.PROJECTILE_DETONATE_NAME, 60.0F,
                    () -> !ownsBombProjectile(owner), wrong);

            runOnShortBar(helper, owner, power, stand, KillerQueenAddon.SHEER_HEART_NAME, 100.0F,
                    state::hasSheerHeart, wrong);
            KillerQueenStateService.recallSheerHeart(owner);

            runOnShortBar(helper, owner, power, owner, KillerQueenAddon.TIME_MARK_NAME, 20.0F,
                    state::hasBitesTheDustMarker, wrong);
            power.setStamina(100.0F);
            Ability reset = power.getAbility(KillerQueenAddon.RESET_DAY_NAME);
            if (reset == null || !reset.checkSpecificConditions(power).isPositive()) {
                wrong.add(KillerQueenAddon.RESET_DAY_NAME + " refused at 100 stamina");
            }
            if (staminaMessageSent(sent)) {
                wrong.add("a not-enough-stamina message was sent");
            }
        } finally {
            cow.discard();
            KillerQueenStateService.clearAll(owner);
            owner.getInventory().clearContent();
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen short bar GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 has no stamina condition on these: " + wrong);
        helper.succeed();
    }

    // 1.16 BlockBombAction, ItemFBombExplode and EntityExplode act in standPerform only on more than 124, 20 and
    // 249 stamina, read before the Stand task pays 75, 20 and 250 whatever came of it. The action still starts
    // below that and says nothing.
    @GameTest(template = "empty", batch = "kqStaminaFloors", timeoutTicks = 20)
    public static void bombKitKeepsTheDonorStaminaFloorsSilently(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-stamina-floor", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        KillerQueenState state = KillerQueenAddon.state(owner);
        EmbeddedChannel sent = (EmbeddedChannel) owner.connection.getConnection().channel();
        List<String> wrong = new ArrayList<>();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 6.0D);
        ItemEntity[] item = {null};
        try {
            sent.outboundMessages().clear();
            BlockPos block = pad.offset(2, 0, 0);
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            // 1.16 BlockBombAction.standPerform bombed the block its user looked at
            owner.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
            aimStand(stand, new ActionTarget(block, Direction.UP));
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.BLOCK_BOMB_NAME, 124.0F, 49.0F,
                    () -> !state.hasBlockBomb(), "marked the block", wrong);
            aimStand(stand, new ActionTarget(block, Direction.UP));
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.BLOCK_BOMB_NAME, 125.0F, 50.0F,
                    () -> block.equals(KillerQueenStateService.blockBombPos(owner)),
                    "did not mark the block above the floor", wrong);
            aimStand(stand, ActionTarget.EMPTY);
            KillerQueenStateService.clearBlockBomb(owner);

            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND, 2));
            helper.assertTrue(ItemBombService.markOffhandItem(owner), "fixture: the item bomb was rejected");
            item[0] = new ItemEntity(level, owner.getX() + 4.0D, owner.getY(), owner.getZ(),
                    owner.getMainHandItem().copy());
            item[0].setPickUpDelay(100);
            owner.getInventory().clearContent();
            helper.assertTrue(level.addFreshEntity(item[0]), "fixture: the item bomb could not be dropped");
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.ITEM_BOMB_DETONATE_NAME, 20.0F, 0.0F,
                    () -> item[0].isAlive(), "detonated the item", wrong);
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.ITEM_BOMB_DETONATE_NAME, 21.0F, 1.0F,
                    () -> !item[0].isAlive(), "did not detonate the item above the floor", wrong);

            helper.assertTrue(KillerQueenStateService.markPrimaryBomb(owner, cow),
                    "fixture: the primary bomb was rejected");
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME, 249.0F, 0.0F,
                    () -> KillerQueenStateService.primaryBombTarget(owner) == cow, "exploded the bomb", wrong);
            runAtFloor(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_DETONATE_NAME, 250.0F, 0.0F,
                    () -> !state.hasPrimaryBomb(), "did not explode the bomb above the floor", wrong);
            if (staminaMessageSent(sent)) {
                wrong.add("a not-enough-stamina message was sent");
            }
        } finally {
            cow.discard();
            if (item[0] != null) {
                item[0].discard();
            }
            KillerQueenStateService.clearAll(owner);
            owner.getInventory().clearContent();
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen stamina floor GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "The 1.16 stamina floors were not kept: " + wrong);
        helper.succeed();
    }

    // 1.16 BlockBombAction kept the block it was pressed at (standKeepsTarget) and, with the default
    // lockOnTargetPosition, StandEntity.taskOffset turned the default offset withYOffset(-0.75, 0.2, -0.75) by the yaw
    // from the user's eyes to the block's centre while it ran, wherever the user looked.
    @GameTest(template = "empty", batch = "kqBlockBombOffset", timeoutTicks = 20)
    public static void blockBombTurnsTheStandToItsBlockAsTheDonorDid(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-block-bomb-offset", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        BlockPos block = pad.offset(3, 1, 0);
        try {
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            level.setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            // the user looks east at the block, 3 blocks away
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(block, Direction.WEST));
            Ability ability = power.getAbility(KillerQueenAddon.BLOCK_BOMB_NAME);
            helper.assertTrue(ability instanceof EntityActionAbility, "fixture: the block bomb is not an action");
            EntityActionInstance action = ((EntityActionAbility) ability)
                    .initActionOnAbilityUse(level, owner, stand, null);
            LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(stand.getCurStandAction() == action && action.getPhase() == ActionPhase.WINDUP
                            && stand.isFollowingUser(),
                    "fixture: the block bomb is not the following Stand's action in its windup");
            ActionTarget held = action.getTaskTarget(level);
            helper.assertTrue(held.getType() == ActionTarget.TargetType.BLOCK && block.equals(held.getBlockPos()),
                    "1.16 BlockBombAction.standKeepsTarget kept the block it was pressed at; the task holds " + held);

            // the user looks south with its body turned away
            turnOwner(owner, 0.0F, 50.0F);
            Vec3 toBlock = Vec3.atCenterOf(block).subtract(owner.getEyePosition());
            float yaw = (float) -Mth.atan2(toBlock.x, toBlock.z) * Mth.RAD_TO_DEG;
            Vec3 expected = new Vec3(-0.75D, 0.2D, -0.75D).yRot(-yaw * Mth.DEG_TO_RAD);
            stand.updatePosition(owner);
            Vec3 actual = stand.position().subtract(owner.position());
            helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                    "1.16 put Killer Queen planting a block bomb 3 blocks east of its user, who looks south, at its"
                            + " user's feet + " + expected + " (the default offset turned to the block), got " + actual
                            + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                            + stand.offsetFromUser.getRotations() + ")");
        } finally {
            level.setBlockAndUpdate(block, Blocks.AIR.defaultBlockState());
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen block bomb offset GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.succeed();
    }

    // 1.16 ItemFBomb and PunchBomb (kq_itembomb, kq_entitybomb) are StandEntityLightAttacks: pressed with no Stand
    // out they brought out only its main arm (the builder's MAIN_ARM). With the whole Stand out it stood at
    // front3dOffset, withXRot(0, front) along the user's view (lockOnTargetPosition false), front the horizontal
    // distance from the user's feet to the task's target less 1 and half its width, within 0.5 to 2. The task kept
    // any target (standKeepsTarget), and one pressed at nothing measured its front to the target it took over.
    @GameTest(template = "empty", batch = "kqLightBombOffset", timeoutTicks = 20)
    public static void itemAndEntityBombsComeOutAndStandAsTheDonorDid(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-light-bomb-offset", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandPower power = StandPower.get(owner);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(cow);
        try {
            owner.setGameMode(GameType.SURVIVAL);
            owner.setInvulnerable(true);
            power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            owner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIAMOND));
            // the entity bomb is refused unless pressed at an entity, so the user aims at the cow
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            turnOwner(owner, -90.0F, -90.0F);
            LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(cow));
            short key = 93;
            for (String name : List.of(KillerQueenAddon.ITEM_BOMB_NAME, KillerQueenAddon.PRIMARY_BOMB_NAME)) {
                Ability ability = power.getAbility(name);
                // a fresh resolution: the power caches its moves within a tick
                rotp.core.powersystem.ability.condition.AvailableAbilities available =
                        new rotp.core.powersystem.ability.condition.AvailableAbilities();
                available.update(power, power.getMoveset());
                AbilityConditionCheck input = available.getContextVariationContainer(ability);
                helper.assertTrue(power.getSummonedStandEntity() == null && input != null && input.ability == ability
                                && AbilityInput.withConditionCheck(input, owner, InputMethod.CLICK),
                        "fixture: " + name + " cannot be pressed with no Stand out");
                AbilityInput.keyPress(key, ability, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK,
                        0.0F, BufferingState.clickCanBuffer(), ability.abilityId);
                AbilityInput.keyRelease(key++, owner);
                StandEntity arm = power.getSummonedStandEntity();
                helper.assertTrue(arm != null && arm.isArmsOnlyMode() && arm.showArm(InteractionHand.MAIN_HAND)
                                && !arm.showArm(InteractionHand.OFF_HAND),
                        "1.16 brought out only Killer Queen's main arm for " + name + " pressed with no Stand out; got "
                                + (arm == null ? "no Stand" : "arms only " + arm.isArmsOnlyMode() + ", main arm "
                                        + arm.showArm(InteractionHand.MAIN_HAND) + ", off arm "
                                        + arm.showArm(InteractionHand.OFF_HAND)));
                power.getPowerType().forceUnsummon(owner, power);
            }

            helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                    "fixture: Killer Queen could not be summoned");
            StandEntity stand = power.getSummonedStandEntity();
            stand.summonLockTicks = 0;
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            double front = Mth.clamp(cow.position().subtract(owner.position()).horizontalDistance()
                            - (1.0D + cow.getBbWidth() / 2.0D),
                    Math.min(0.5D, stand.getEffectiveRange()), Math.min(2.0D, stand.getMaxRange()));
            helper.assertTrue(front > 0.5D && front < 2.0D, "fixture: the cow does not set the front offset: " + front);

            // the user looks east at the cow and plants an item bomb, then looks south with its body turned away
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(cow));
            EntityActionInstance item = startBomb(helper, owner, power, stand, KillerQueenAddon.ITEM_BOMB_NAME);
            helper.assertTrue(item.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntityLightAttack.standKeepsTarget kept the cow the item bomb was pressed at; the task "
                            + "holds " + item.getTaskTarget(level));
            turnOwner(owner, 0.0F, 50.0F);
            assertFrontAlongView(helper, owner, stand, front, "an item bomb pressed at a cow 2.5 blocks east");

            // the item bomb pressed at nothing, the user still looking south, takes the cow over and measures its
            // front to it (the entity bomb cannot be pressed at nothing: its target requirement is ENTITY)
            aimStand(stand, ActionTarget.EMPTY);
            EntityActionInstance next = startBomb(helper, owner, power, stand, KillerQueenAddon.ITEM_BOMB_NAME);
            helper.assertTrue(next.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntity.setTask gave the item bomb pressed at nothing the cow of the action it "
                            + "replaced; it holds " + next.getTaskTarget(level));
            assertFrontAlongView(helper, owner, stand, front, "an item bomb pressed at nothing after an action at the cow");

            // the entity bomb is pressed at an entity only; with the user's body turned away it stands the same
            // front along the user's view, measured to the cow it was pressed at
            LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(cow));
            EntityActionInstance entity = startBomb(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(entity.getTaskTarget(level).getEntity() == cow,
                    "1.16 StandEntityLightAttack.standKeepsTarget kept the cow the entity bomb was pressed at; the "
                            + "task holds " + entity.getTaskTarget(level));
            turnOwner(owner, 0.0F, 50.0F);
            assertFrontAlongView(helper, owner, stand, front, "an entity bomb pressed at a cow 2.5 blocks east");
        } finally {
            cow.discard();
            KillerQueenStateService.clearAll(owner);
            owner.getInventory().clearContent();
            if (power.getSummonedStandEntity() != null) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            owner.connection.disconnect(Component.literal("Killer Queen light bomb offset GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.succeed();
    }

    // 1.16 PunchBomb.standPerform (StandEntityLightAttack): StandEntity.punch at the task's target while the Stand
    // reached it (aimWithThisOrUser), whatever its user looked at; otherwise it aimed from the user's view again.
    // What it struck became the task's target (setTaskTarget).
    @GameTest(template = "empty", batch = "kqEntityBombStrike", timeoutTicks = 20)
    public static void entityBombStrikesItsTaskTargetAsTheDonorDid(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-entity-bomb-strike", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        Cow kept = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Cow looked = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(kept);
        cleanup.entities.add(looked);
        try {
            owner.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standSide = HumanoidArm.RIGHT;
            kept.setPos(owner.getX() + 2.0D, owner.getY(), owner.getZ());
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() - 6.0D);
            // an entity bomb at the cow east of the user; the user then looks south and a second cow comes into the
            // user's view, both in the Stand's reach
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(kept));
            EntityActionInstance bomb = startBomb(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == kept,
                    "fixture: the entity bomb pressed at the cow did not keep it: " + bomb.getTaskTarget(level));
            turnOwner(owner, 0.0F, 0.0F);
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() + 2.0D);
            aimStand(stand, new ActionTarget(looked));
            LivingComponentAction.getComponent(owner).entityAim.setTarget(new ActionTarget(looked));
            stand.updatePosition(owner);
            stand.setYRot(0.0F);
            Vec3 eyesBefore = stand.getEyePosition();
            performNow(bomb);
            // 1.16 StandEntity.punch -> aimWithThisOrUser turned the Stand toward the cow it struck in the hit's own
            // tick, by at most attackSpeed / 16 x 18 degrees (MCUtil.rotateTowards), not a tick later
            float turnLimit = (float) stand.getAttackSpeed() / 16.0F * 18.0F;
            float wantedTurn = Mth.clamp(Mth.degreesDifference(0.0F,
                    rotp.core.util.functions.MathUtil.yRotDegFromVec(kept.getEyePosition().subtract(eyesBefore))),
                    -turnLimit, turnLimit);
            float turned = Mth.degreesDifference(0.0F, stand.getYRot());
            helper.assertTrue(Math.abs(wantedTurn) > 1.0F && Math.abs(turned - wantedTurn) < 0.5F,
                    "1.16 turned the Stand " + wantedTurn + " degrees toward the cow the entity bomb struck, in the "
                            + "hit's own tick; it turned " + turned);
            helper.assertTrue(KillerQueenStateService.primaryBombTarget(owner) == kept,
                    "1.16 StandEntity.punch struck the cow the entity bomb was pressed at, not the one the user "
                            + "looked at; the bomb is on " + KillerQueenStateService.primaryBombTarget(owner));

            // an entity bomb at that cow, now out of the Stand's reach but kept, the user turned to another cow
            KillerQueenStateService.clearPrimaryBomb(owner);
            kept.setPos(owner.getX() + 5.0D, owner.getY(), owner.getZ());
            looked.setPos(owner.getX(), owner.getY(), owner.getZ() + 1.5D);
            turnOwner(owner, -90.0F, -90.0F);
            aimStand(stand, new ActionTarget(kept));
            bomb = startBomb(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_NAME);
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == kept,
                    "fixture: the entity bomb did not keep the cow 5 blocks away: " + bomb.getTaskTarget(level));
            turnOwner(owner, 0.0F, 0.0F);
            aimStand(stand, new ActionTarget(looked));
            stand.updatePosition(owner);
            performNow(bomb);
            helper.assertTrue(KillerQueenStateService.primaryBombTarget(owner) == looked,
                    "1.16 aimed the entity bomb from the user's view again once its cow was out of reach, at the cow "
                            + "the user looked at; the bomb is on " + KillerQueenStateService.primaryBombTarget(owner));
            helper.assertTrue(bomb.getTaskTarget(level).getEntity() == looked,
                    "1.16 StandEntity.punch made the cow the entity bomb struck its task's target; the task holds "
                            + bomb.getTaskTarget(level));

            // 1.16 StandEntity.setTaskTarget let server code drop the target of the current task, the struck one too
            helper.assertTrue(keptTarget(bomb) != null, "fixture: the entity bomb keeps no target beside its task's");
            stand.setTaskTarget(null);
            helper.assertTrue(keptTarget(bomb) == null,
                    "1.16 StandEntity.setTaskTarget(null) dropped the one target the entity bomb's task held; the "
                            + "kept target field still holds " + keptTarget(bomb));
            helper.assertTrue(bomb.getTaskTarget(level).isEmpty(level),
                    "1.16 StandEntity.setTaskTarget(null) dropped the target of the entity bomb, the cow it struck; the "
                            + "task still holds " + bomb.getTaskTarget(level));
        } finally {
            kept.discard();
            looked.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen entity bomb strike GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.succeed();
    }

    // 1.16 PunchBomb.punchEntity (020 :81-88) stored the bomb on the living entity struck before
    // StandEntity.attackTarget ran the hit (punchEntity, then doHit), so a target that took no damage was bombed too:
    // one whose hurt was refused, one blocking with a shield toward the Stand.
    @GameTest(template = "empty", batch = "kqEntityBombRefusedHit", timeoutTicks = 20)
    public static void entityBombPlantsOnATargetThatTakesNoDamageAsTheDonorDid(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-entity-bomb-refused", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        ServerLevel level = helper.getLevel();
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        Cow refusing = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Cow plain = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        cleanup.entities.add(refusing);
        cleanup.entities.add(plain);
        ShieldBlocker blocker = new ShieldBlocker(level);
        Consumer<LivingIncomingDamageEvent> refuse = event -> {
            if (event.getEntity() == refusing) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, LivingIncomingDamageEvent.class, refuse);
        List<String> wrong = new ArrayList<>();
        try {
            Vec3 away = owner.position().add(10.0D, 0.0D, 0.0D);
            refusing.setPos(away);
            plain.setPos(away.add(0.0D, 0.0D, 3.0D));
            // facing north, toward the user and the Stand in front of it
            blocker.moveTo(away.x, away.y, away.z - 3.0D, 180.0F, 0.0F);
            blocker.setYHeadRot(180.0F);
            blocker.getAbilities().invulnerable = false;
            blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            helper.assertTrue(level.addFreshEntity(blocker), "fixture: the shield blocker could not join the level");
            turnOwner(owner, 0.0F, 0.0F);
            // the target, what it is, whether the punch hurts it
            Object[][] rows = {
                    { refusing, "a cow whose hurt is cancelled", false },
                    { blocker, "a player blocking with a shield toward the Stand", false },
                    { plain, "a cow the punch hurts", true } };
            for (Object[] row : rows) {
                LivingEntity target = (LivingEntity) row[0];
                String what = (String) row[1];
                boolean hurts = (Boolean) row[2];
                Vec3 home = target.position();
                // 2 blocks south of the user, who looks south
                target.setPos(owner.getX(), owner.getY(), owner.getZ() + 2.0D);
                target.invulnerableTime = 0;
                if (target == blocker) {
                    blocker.raiseShield(helper);
                }
                float health = target.getHealth();
                KillerQueenStateService.clearPrimaryBomb(owner);
                aimStand(stand, new ActionTarget(target));
                EntityActionInstance bomb = startBomb(helper, owner, power, stand, KillerQueenAddon.PRIMARY_BOMB_NAME);
                aimStand(stand, ActionTarget.EMPTY);
                stand.updatePosition(owner);
                performNow(bomb);
                if (target.getHealth() < health != hurts) {
                    wrong.add("fixture: " + what + (hurts ? " took no damage" : " was hurt") + ", health " + health
                            + " -> " + target.getHealth());
                }
                if (KillerQueenStateService.primaryBombTarget(owner) != target) {
                    wrong.add(what + ": 1.16 PunchBomb.punchEntity bombed it before the hit ran; the bomb is on "
                            + KillerQueenStateService.primaryBombTarget(owner));
                }
                LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                target.setPos(home);
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(refuse);
            refusing.discard();
            plain.discard();
            blocker.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen refused entity bomb GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 kq_entitybomb on a target that takes no damage: " + wrong);
        helper.succeed();
    }

    // GameTestPlayers' survival mock (rotp core) that raises its shield at once: vanilla isBlocking needs 5 use ticks
    private static final class ShieldBlocker extends Player {
        ShieldBlocker(Level level) {
            super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "kq-shield-blocker"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        void raiseShield(GameTestHelper helper) {
            startUsingItem(InteractionHand.OFF_HAND);
            for (int i = 0; i < 8 && isUsingItem(); i++) {
                updateUsingItem(getUseItem());
            }
            helper.assertTrue(isBlocking(), "fixture: the blocker's shield is not raised");
        }
    }

    // 1.16 PunchBomb.getTargetRequirement is ENTITY: PowerBaseImpl.checkTarget refused the press, with no message,
    // unless the client's target was an entity (a block, nothing, or the user's own precision ray finding none);
    // Action.checkRangeAndTarget answered "target_too_far" beyond 8 blocks, another entity than the user, and with the
    // Stand out StandEntityAction.checkStandTarget wanted a living entity it may attack.
    @GameTest(template = "empty", batch = "kqEntityBombTarget", timeoutTicks = 20)
    public static void entityBombIsRefusedUnlessPressedAtAnEntityAsTheDonorDid(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        ServerPlayer owner = makeCompatibleMockPlayer(helper, "kq-entity-bomb-target", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, owner);
        helper.testInfo.addListener(cleanup);
        StandEntity stand = staminaFixture(helper, owner);
        cleanup.entities.add(stand);
        StandPower power = StandPower.get(owner);
        power.skipProgression();
        Ability bomb = power.getAbility(KillerQueenAddon.PRIMARY_BOMB_NAME);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 1);
        Minecart cart = helper.spawn(EntityType.MINECART, 1, 2, 1);
        cleanup.entities.add(cow);
        cleanup.entities.add(cart);
        List<String> wrong = new ArrayList<>();
        try {
            helper.assertTrue(bomb instanceof EntityActionAbility, "fixture: the entity bomb is not an action");
            // the user looks east; the cow and the cart are south of it, out of the view
            turnOwner(owner, -90.0F, -90.0F);
            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
            cart.setPos(owner.getX(), owner.getY(), owner.getZ() + 4.0D);

            aimUser(owner, ActionTarget.EMPTY);
            expectBombCheck(wrong, bomb, power, "pressed at nothing", false, null);
            expectBombPress(wrong, owner, power, stand, bomb, (short) 91, "pressed at nothing", false);
            aimUser(owner, new ActionTarget(pad.below(), Direction.UP));
            expectBombCheck(wrong, bomb, power, "pressed at a block", false, null);
            expectBombPress(wrong, owner, power, stand, bomb, (short) 92, "pressed at a block", false);
            aimUser(owner, new ActionTarget(owner));
            expectBombCheck(wrong, bomb, power, "pressed at its user", false, null);
            aimUser(owner, new ActionTarget(cart));
            expectBombCheck(wrong, bomb, power, "pressed at a minecart", false, null);

            cow.setPos(owner.getX() + 12.0D, owner.getY(), owner.getZ());
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow 12 blocks away", false, "target_too_far");

            cow.setPos(owner.getX(), owner.getY(), owner.getZ() + 3.0D);
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow", true, null);

            // nothing under the crosshair, the cow on the Stand's precision ray from the user's eyes
            cow.setPos(owner.getX() + 2.5D, owner.getY(), owner.getZ());
            aimUser(owner, ActionTarget.EMPTY);
            expectBombCheck(wrong, bomb, power, "pressed at nothing with a cow on the precision ray", true, null);

            aimUser(owner, new ActionTarget(cow));
            expectBombPress(wrong, owner, power, stand, bomb, (short) 93, "pressed at a cow", true);

            // with no Stand out the user is the performer: it is not a target, and the living entity the Stand may
            // attack is asked for only once the Stand is out (StandEntityAction.checkRangeAndTarget)
            power.getPowerType().forceUnsummon(owner, power);
            aimUser(owner, new ActionTarget(owner));
            expectBombCheck(wrong, bomb, power, "pressed at its user with no Stand out", false, null);
            aimUser(owner, new ActionTarget(cart));
            expectBombCheck(wrong, bomb, power, "pressed at a minecart with no Stand out", true, null);
            aimUser(owner, new ActionTarget(cow));
            expectBombCheck(wrong, bomb, power, "pressed at a cow with no Stand out", true, null);
            helper.assertTrue(wrong.isEmpty(), "The 1.16 entity bomb target requirement was not kept: " + wrong);
        } finally {
            cow.discard();
            cart.discard();
            KillerQueenStateService.clearAll(owner);
            stand.discard();
            owner.connection.disconnect(Component.literal("Killer Queen entity bomb target GameTest complete"));
            sheerHeartPad(helper, false);
        }
        helper.succeed();
    }

    private static void aimUser(ServerPlayer owner, ActionTarget target) {
        LivingComponentAction.getComponent(owner).entityAim.setTarget(target);
    }

    private static void expectBombCheck(List<String> wrong, Ability bomb, StandPower power, String when,
            boolean allowed, String message) {
        ConditionCheck check = bomb.checkSpecificConditions(power);
        Component warning = check.getWarning();
        String key = warning != null && warning.getContents() instanceof TranslatableContents translatable
                ? translatable.getKey() : null;
        String expectedKey = message != null ? "jojo.message.action_condition." + message : null;
        if (check.isPositive() != allowed || !java.util.Objects.equals(key, expectedKey)) {
            wrong.add("the entity bomb " + when + " " + (check.isPositive() ? "was allowed" : "was refused")
                    + " with message " + key + " (1.16: " + (allowed ? "allowed" : "refused") + " with message "
                    + expectedKey + ")");
        }
    }

    private static void expectBombPress(List<String> wrong, ServerPlayer owner, StandPower power, StandEntity stand,
            Ability bomb, short key, String when, boolean starts) {
        // the server's press handler: the condition check first, the press only when it passes
        rotp.core.powersystem.ability.condition.AvailableAbilities available =
                new rotp.core.powersystem.ability.condition.AvailableAbilities();
        available.update(power, power.getMoveset());
        if (available.getContextVariationContainer(bomb) == null) {
            wrong.add("fixture: the entity bomb is not among the available abilities " + when);
            return;
        }
        if (AbilityInput.withConditionCheck(available.getContextVariationContainer(bomb), owner, InputMethod.CLICK)) {
            AbilityInput.keyPress(key, bomb, owner, new FriendlyByteBuf(Unpooled.buffer()), InputMethod.CLICK, 0.0F,
                    BufferingState.clickCanBuffer(), bomb.abilityId);
            AbilityInput.keyRelease(key, owner);
        }
        boolean started = stand.getCurStandAction() != null
                && stand.getCurStandAction().ability == bomb;
        if (started != starts) {
            wrong.add("the entity bomb key " + when + (started ? " started the action" : " did not start it")
                    + " (1.16: " + (starts ? "started" : "refused") + ")");
        }
    }

    private static EntityActionInstance startBomb(GameTestHelper helper, ServerPlayer owner, StandPower power,
            StandEntity stand, String name) {
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability instanceof EntityActionAbility, "fixture: " + name + " is not an action");
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, stand, null);
        LivingComponentAction.getComponent(stand).setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(stand.getCurStandAction() == action && !stand.isArmsOnlyMode(),
                "fixture: " + name + " is not the whole Stand's action");
        return action;
    }

    // the windup skipped: the strike runs on this tick
    private static void performNow(EntityActionInstance action) {
        action.setPhaseStart(ActionPhase.PERFORM);
        action._tickAction();
    }

    private static void assertFrontAlongView(GameTestHelper helper, ServerPlayer owner, StandEntity stand,
            double front, String when) {
        Vec3 expected = new Vec3(0.0D, 0.0D, front)
                .xRot(-owner.getXRot() * Mth.DEG_TO_RAD)
                .yRot(-owner.getYRot() * Mth.DEG_TO_RAD);
        stand.updatePosition(owner);
        Vec3 actual = stand.position().subtract(owner.position());
        helper.assertTrue(actual.distanceTo(expected) < 1.0E-4D,
                "1.16 put Killer Queen at its user's feet + " + expected + " during " + when + ", the user looking "
                        + "south, got " + actual + " (offset " + stand.offsetFromUser.getRelativeOffset() + " "
                        + stand.offsetFromUser.getRotations() + ")");
    }

    private static ActionTarget keptTarget(EntityActionInstance action) {
        try {
            java.lang.reflect.Field field = EntityActionInstance.class.getDeclaredField("actionTargetSnapshot");
            field.setAccessible(true);
            ActionTarget target = (ActionTarget) field.get(action);
            return target == null || target.getType() == ActionTarget.TargetType.EMPTY ? null : target;
        }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void turnOwner(ServerPlayer owner, float viewYaw, float bodyYaw) {
        owner.setYRot(viewYaw);
        owner.yRotO = viewYaw;
        owner.setYHeadRot(viewYaw);
        owner.yHeadRotO = viewYaw;
        owner.setXRot(0.0F);
        owner.xRotO = 0.0F;
        owner.yBodyRot = bodyYaw;
        owner.yBodyRotO = bodyYaw;
    }

    // A Survival Killer Queen user (a finite bar) with the Stand out; the user takes no blast damage.
    private static StandEntity staminaFixture(GameTestHelper helper, ServerPlayer owner) {
        StandPower power = StandPower.get(owner);
        helper.assertTrue(power != null, "fixture: the mock player has no Stand power attachment");
        owner.setGameMode(GameType.SURVIVAL);
        owner.setInvulnerable(true);
        power.setStand(KillerQueenAddon.KILLER_QUEEN.get());
        power.setStamina(power.getMaxStamina());
        helper.assertTrue(!power.isStaminaInfinite() && power.getMaxStamina() >= 250.0F,
                "fixture: the bar is infinite or shorter than 250: " + power.getMaxStamina());
        helper.assertTrue(KillerQueenAddon.KILLER_QUEEN.get().summon(owner, power),
                "fixture: Killer Queen could not be summoned");
        StandEntity stand = power.getSummonedStandEntity();
        helper.assertTrue(stand != null && stand.isAddedToLevel(), "fixture: Killer Queen was not added to the level");
        stand.summonLockTicks = 0;
        return stand;
    }

    private static void runOnShortBar(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, BooleanSupplier acted, List<String> wrong) {
        if (startAndPerform(helper, owner, power, performer, name, stamina, wrong)) {
            if (!acted.getAsBoolean()) {
                wrong.add(name + " did nothing at " + stamina + " stamina");
            }
            if (power.getStamina() != 0.0F) {
                wrong.add(name + " left " + power.getStamina() + " of a " + stamina + " bar");
            }
        }
    }

    private static void runAtFloor(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, float left, BooleanSupplier expected,
            String failure, List<String> wrong) {
        if (startAndPerform(helper, owner, power, performer, name, stamina, wrong)) {
            if (!expected.getAsBoolean()) {
                wrong.add(name + " " + failure + " at " + stamina + " stamina");
            }
            if (power.getStamina() != left) {
                wrong.add(name + " left " + power.getStamina() + " of a " + stamina + " bar, not " + left);
            }
        }
    }

    // The ability's own conditions at this stamina, then its action run to the perform tick at once.
    private static boolean startAndPerform(GameTestHelper helper, ServerPlayer owner, StandPower power,
            LivingEntity performer, String name, float stamina, List<String> wrong) {
        Ability ability = power.getAbility(name);
        helper.assertTrue(ability instanceof EntityActionAbility, "fixture: " + name + " is not an action");
        power.setStamina(stamina);
        if (!ability.checkSpecificConditions(power).isPositive()) {
            wrong.add(name + " refused at " + stamina + " stamina");
            return false;
        }
        EntityActionInstance action = ((EntityActionAbility) ability)
                .initActionOnAbilityUse(helper.getLevel(), owner, performer, null);
        action.setPhaseStart(ActionPhase.PERFORM);
        LivingComponentAction component = LivingComponentAction.getComponent(performer);
        component.setAction(action, owner, SyncType.NO_SYNC);
        helper.assertTrue(component.getAction() == action, "fixture: " + name + " was not started");
        action._tickAction();
        component.setAction(null, owner, SyncType.NO_SYNC);
        return true;
    }

    private static void aimStand(StandEntity stand, ActionTarget target) {
        LivingComponentAction.getComponent(stand).entityAim.setTarget(target);
    }

    private static boolean ownsBombProjectile(ServerPlayer owner) {
        return !owner.level().getEntitiesOfClass(KillerQueenBombProjectile.class,
                owner.getBoundingBox().inflate(20.0D), projectile -> projectile.isOwnedBy(owner.getUUID())).isEmpty();
    }

    private static boolean staminaMessageSent(EmbeddedChannel sent) {
        // The server batches a player's packets within a tick
        sent.flush();
        for (Object message : sent.outboundMessages()) {
            if (isStaminaMessage(message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStaminaMessage(Object message) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (isStaminaMessage(packet)) {
                    return true;
                }
            }
            return false;
        }
        return message instanceof ClientboundSetActionBarTextPacket packet
                && packet.text().getContents() instanceof TranslatableContents text
                && text.getKey().endsWith("not_enough_stamina");
    }

    private static void move(GameTestHelper helper, ServerPlayer player, ServerLevel to, Vec3 spot) {
        helper.assertTrue(
                player.teleportTo(to, spot.x, spot.y, spot.z, Set.of(), 0.0F, 0.0F) && player.level() == to,
                "fixture: " + player.getGameProfile().getName() + " could not move to "
                        + to.dimension().location());
    }

    // Whether a state sync with this primary bomb went to the owner after the last respawn packet on the connection.
    private static boolean primaryBombSentAfterRespawn(
            GameTestHelper helper, EmbeddedChannel sent, ServerPlayer owner, UUID target) {
        // The server batches a player's packets within a tick
        sent.flush();
        boolean respawned = false;
        boolean resent = false;
        for (Object message : sent.outboundMessages()) {
            if (message instanceof ClientboundRespawnPacket) {
                respawned = true;
                resent = false;
            } else if (respawned && carriesPrimaryBomb(message, owner, target)) {
                resent = true;
            }
        }
        helper.assertTrue(respawned, "fixture: the dimension change left no respawn packet on the connection");
        return resent;
    }

    private static boolean carriesPrimaryBomb(Object message, ServerPlayer owner, UUID target) {
        if (message instanceof ClientboundBundlePacket bundle) {
            for (Packet<?> packet : bundle.subPackets()) {
                if (carriesPrimaryBomb(packet, owner, target)) {
                    return true;
                }
            }
            return false;
        }
        return message instanceof ClientboundCustomPayloadPacket packet
                && packet.payload() instanceof KillerQueenStateSyncPayload sync
                && sync.ownerUuid().equals(owner.getUUID())
                && target.equals(sync.primaryTargetUuid());
    }

    // PlayerList.respawn leaves the connection on the dead player; ServerGamePacketListenerImpl.handleClientCommand
    // moves it to the new one, and without that a later disconnect leaves the new player in the level.
    private static ServerPlayer respawn(ServerPlayer player, boolean keepInventory, Entity.RemovalReason reason) {
        ServerPlayer respawned = player.getServer().getPlayerList().respawn(player, keepInventory, reason);
        respawned.connection.player = respawned;
        return respawned;
    }

    // A mock player with a chosen UUID, so that a second join loads what the first one saved.
    private static ServerPlayer mockPlayer(GameTestHelper helper, UUID uuid, String name, BlockPos spot) {
        GameProfile profile = new GameProfile(uuid, name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(
                helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        if (spot != null) {
            player.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, 0.0F, 0.0F);
        }
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
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
    @GameTest(template = "empty", batch = "kqBitesTheDustCleanup", timeoutTicks = 40)
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

    /*
     * The same cleanup for the tests that log a player out and in again, or make a bomb before they hand it over:
     * a player who joined later and a bomb of a player of the test go with the players it began with.
     */
    @GameTest(template = "empty", batch = "kqMockPlayerCleanup", timeoutTicks = 40)
    public static void aFailedTestLetsGoOfItsRelogsAndTheBombsItNeverHandedOver(GameTestHelper helper) {
        BlockPos pad = sheerHeartPad(helper, true);
        UUID ownerUuid = UUID.randomUUID();
        ServerPlayer first = mockPlayer(helper, ownerUuid, "kq-cleanup-relog", pad);
        MockPlayerCleanup cleanup = new MockPlayerCleanup(helper, first);
        helper.testInfo.addListener(cleanup);
        first.connection.disconnect(Component.literal("Killer Queen relog"));
        ServerPlayer again = cleanup.join(mockPlayer(helper, ownerUuid, "kq-cleanup-relog", pad));
        ServerPlayer bystander = cleanup.join(
                mockPlayer(helper, UUID.randomUUID(), "kq-cleanup-bystander", pad.south(6)));
        SheerHeartAttackEntity bomb = linkedSheerHeart(helper, again, pad);
        cleanup.testFailed(helper.testInfo, null);
        boolean padReleased = sheerHeartPad(helper, false) == null;
        helper.succeedWhen(() -> {
            PlayerList players = helper.getLevel().getServer().getPlayerList();
            helper.assertTrue(again.hasDisconnected() && bystander.hasDisconnected()
                            && players.getPlayer(ownerUuid) == null
                            && players.getPlayer(bystander.getUUID()) == null,
                    "A failed test left a player who joined after it began (" + again.hasDisconnected()
                            + " relogged owner, " + bystander.hasDisconnected() + " bystander gone) by the shared pad");
            helper.assertTrue(bomb.isRemoved() && padReleased,
                    "A failed test kept a bomb it never handed over (" + bomb.isRemoved() + " removed) or its pad ("
                            + padReleased + " released)");
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
     * K5-31), for heavy_punch (the heavy and the combo finisher) and grab_heavy_punch.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyClipsKeepTheCoreDefaultHookingPunch(
            GameTestHelper helper) {
        String path = "/assets/rotp_zkq/stand_skins/killer_queen/assets/rotp_zkq/animations/"
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
                "Killer Queen's heavy_punch must keep the core default's PERFORM 0.4333 / RECOVERY 0.4833 (the"
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
                "Killer Queen's heavy_punch must stay the core default's hooking punch: right_arm "
                        + bones.get("right_arm") + ", right_arm_bend " + bones.get("right_arm_bend"));
        helper.assertTrue(
                heavy.equals(animations.get("grab_heavy_punch")),
                "Killer Queen's grab_heavy_punch must be the same clip as heavy_punch");
        helper.succeed();
    }
}
