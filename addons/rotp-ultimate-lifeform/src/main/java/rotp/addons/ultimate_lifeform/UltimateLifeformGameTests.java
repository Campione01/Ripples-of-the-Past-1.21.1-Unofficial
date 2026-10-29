package rotp.addons.ultimate_lifeform;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.util.functions.MathUtil;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformCombat;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.connection.ConnectionType;

@GameTestHolder(UltimateLifeformAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UltimateLifeformGameTests {
    private static final double EPSILON = 1.0E-4D;

    private UltimateLifeformGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_temporary_entity_nbt_round_trips_preserve_li")
    public static void temporaryEntityNbtRoundTripsPreserveLifecycle(
            GameTestHelper helper) {
        Cow owner = living(helper, new Vec3(2.5D, 2.0D, 2.5D));

        UltimateScaleProjectileEntity scale = scaleProjectile(helper);
        scale.initialize(owner, 7.0F, false, 0.0F, 0.0F, 11);
        setSavedAge(scale, 10);
        UltimateScaleProjectileEntity restoredScale = scaleRoundTrip(scale, helper);
        helper.assertTrue(
                restoredScale.getElapsedLifeTicks() == 10
                        && restoredScale.getLifeTicks() == 11,
                "Scale projectile lost elapsed life during NBT reload.");
        UltimateScaleProjectileEntity trackedScale = scaleSpawnRoundTrip(restoredScale, helper);
        helper.assertTrue(
                trackedScale.getElapsedLifeTicks() == 10,
                "Late-tracked scale lost elapsed spawn age.");
        restoredScale.tick();
        helper.assertTrue(
                restoredScale.isRemoved(),
                "Reloaded scale did not expire on its original exact tick: "
                        + "age=" + restoredScale.getElapsedLifeTicks()
                        + ", removed=" + restoredScale.isRemoved());

        UltimateProjectileEntity tentacle = projectile(
                UltimateLifeformAddon.SQUID_TENTACLE.get(), helper);
        tentacle.initializeTentacle(
                owner, true, 0.0F, 0.0F, -0.18D, -0.4D);
        setSavedAge(tentacle, 31);
        UltimateProjectileEntity restoredTentacle = roundTrip(
                UltimateLifeformAddon.SQUID_TENTACLE.get(),
                tentacle,
                helper);
        helper.assertTrue(
                restoredTentacle.getElapsedLifeTicks() == 31
                        && restoredTentacle.getLifeTicks() == 42,
                "Tentacle lost its 42-tick lifecycle phase on reload.");
        UltimateProjectileEntity trackedTentacle = spawnRoundTrip(
                UltimateLifeformAddon.SQUID_TENTACLE.get(),
                restoredTentacle,
                helper);
        helper.assertTrue(
                trackedTentacle.getElapsedLifeTicks() == 31,
                "Late-tracked tentacle reset its lifecycle phase.");

        UltimateProjectileEntity guardian = projectile(
                UltimateLifeformAddon.LASER_END.get(), helper);
        guardian.initializeGuardian(owner);
        setSavedAge(guardian, 411);
        UltimateProjectileEntity restoredGuardian = roundTrip(
                UltimateLifeformAddon.LASER_END.get(),
                guardian,
                helper);
        helper.assertTrue(
                restoredGuardian.getElapsedLifeTicks() == 411
                        && restoredGuardian.getLifeTicks() == 600,
                "Guardian endpoint lost elapsed life on reload.");
        UltimateProjectileEntity trackedGuardian = spawnRoundTrip(
                UltimateLifeformAddon.LASER_END.get(),
                restoredGuardian,
                helper);
        helper.assertTrue(
                trackedGuardian.getElapsedLifeTicks() == 411,
                "Late-tracked guardian reset its endpoint age.");

        BlockPos targetBlock = helper.absolutePos(
                new BlockPos(8, 2, 8));
        UltimateAreaEffectEntity sendo = area(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                helper);
        sendo.initializeSendo(
                owner, targetBlock, Direction.UP, 1.125F, 9.0F, 8);
        setSavedAge(sendo, 11);
        UltimateAreaEffectEntity restoredSendo = roundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                sendo,
                helper);
        helper.assertTrue(
                restoredSendo.getElapsedLifeTicks() == 11
                        && restoredSendo.getWavesToAdd() == 8,
                "Sendo lost its wave-schedule phase on reload.");
        UltimateAreaEffectEntity trackedSendo = spawnRoundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                restoredSendo,
                helper);
        helper.assertTrue(
                trackedSendo.getElapsedLifeTicks() == 11
                        && trackedSendo.getWavesToAdd() == 8,
                "Late-tracked Sendo reset its four-tick schedule age.");

        UltimateAreaEffectEntity turquoise = area(
                UltimateLifeformAddon
                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get(),
                helper);
        turquoise.initializeTurquoise(
                owner, 10.0F, 6.0F, 190, 1.5D);
        setSavedAge(turquoise, 142);
        float wearOff = turquoise.getDamageWearOffMultiplier();
        UltimateAreaEffectEntity restoredTurquoise = roundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get(),
                turquoise,
                helper);
        helper.assertTrue(
                restoredTurquoise.getElapsedLifeTicks() == 142
                        && close(
                                restoredTurquoise
                                        .getDamageWearOffMultiplier(),
                                wearOff),
                "Turquoise damage falloff reset during NBT reload.");
        UltimateAreaEffectEntity trackedTurquoise = spawnRoundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get(),
                restoredTurquoise,
                helper);
        helper.assertTrue(
                trackedTurquoise.getElapsedLifeTicks() == 142
                        && close(
                                trackedTurquoise
                                        .getDamageWearOffMultiplier(),
                                wearOff),
                "Late-tracked Turquoise reset age-based falloff.");

        helper.getLevel().setBlockAndUpdate(
                targetBlock, Blocks.STONE.defaultBlockState());
        UltimateAreaEffectEntity blockCharge = area(
                UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get(),
                helper);
        blockCharge.initializeBlockCharge(
                owner, targetBlock, 50.0F, 120, 1.0F);
        for (int tick = 0; tick < 7; tick++) {
            blockCharge.tick();
        }
        UltimateAreaEffectEntity restoredBlockCharge = roundTrip(
                UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get(),
                blockCharge,
                helper);
        helper.assertTrue(
                restoredBlockCharge.getElapsedLifeTicks() == 7
                        && restoredBlockCharge.getBlockChargeAge() == 7
                        && restoredBlockCharge.getBlockChargeTicks()
                                == blockCharge.getBlockChargeTicks(),
                "Block charge lost elapsed or donor charge age on reload.");
        UltimateAreaEffectEntity trackedBlockCharge = spawnRoundTrip(
                UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get(),
                restoredBlockCharge,
                helper);
        helper.assertTrue(
                trackedBlockCharge.getElapsedLifeTicks() == 7
                        && trackedBlockCharge.getBlockChargeAge() == 7,
                "Late-tracked block charge reset its donor phase age.");

        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_sendo_reload_preserves_phase_damage_and_hit")
    public static void sendoReloadPreservesPhaseDamageAndHitIdentity(
            GameTestHelper helper) {
        Cow owner = living(helper, new Vec3(2.5D, 2.0D, 2.5D));
        BlockPos blockPos = helper.absolutePos(new BlockPos(8, 2, 8));
        UltimateAreaEffectEntity sendo = area(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                helper);
        sendo.initializeSendo(
                owner, blockPos, Direction.UP, 1.125F, 9.0F, 8);
        helper.getLevel().addFreshEntity(sendo);
        Cow target = spawn(
                helper,
                EntityType.COW,
                sendo.position().add(0.2D, -0.7D, 0.0D));
        target.setNoAi(true);
        target.invulnerableTime = 20;
        float initialHealth = target.getHealth();
        // 1.16 Wave.tick hurt with the raw wave damage
        float expectedDamage = sendo.getEffectDamage();

        for (int tick = 0; tick < 4; tick++) {
            sendo.tick();
        }
        helper.assertTrue(
                close(sendo.getEffectDamage(), 1.125D)
                        && close(
                                initialHealth - target.getHealth(),
                                expectedDamage)
                        && target.invulnerableTime == 20
                        && sendo.getActiveWaveAges().equals(List.of(1)),
                "First Sendo wave missed exact damage, age, or immunity bypass: "
                        + "damage=" + (initialHealth - target.getHealth())
                        + ", invul=" + target.invulnerableTime
                        + ", ages=" + sendo.getActiveWaveAges());

        UltimateAreaEffectEntity lateTrackedSendo = spawnRoundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                sendo,
                helper);
        helper.assertTrue(
                lateTrackedSendo.getElapsedLifeTicks() == 4
                        && lateTrackedSendo.getActiveWaveAges()
                                .equals(List.of(1)),
                "Late-tracked Sendo lost active wave ages or cadence phase.");

        UltimateAreaEffectEntity restored = roundTrip(
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get(),
                sendo,
                helper);
        sendo.discard();
        float afterFirstWave = target.getHealth();
        for (int tick = 0; tick < 3; tick++) {
            restored.tick();
        }
        helper.assertTrue(
                close(target.getHealth(), afterFirstWave)
                        && restored.getElapsedLifeTicks() == 7
                        && restored.getActiveWaveCount() == 1,
                "Reload reset Sendo deduplication or four-tick phase.");
        restored.tick();
        helper.assertTrue(
                restored.getElapsedLifeTicks() == 8
                        && restored.getActiveWaveCount() == 2
                        && restored.getActiveWaveAges()
                                .equals(List.of(5, 1))
                        && close(
                                afterFirstWave - target.getHealth(),
                                expectedDamage),
                "Second Sendo wave did not land at tick 8 with exact damage.");

        target.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void beatRefreshesClippedCrosshairOnExactTick(
            GameTestHelper helper) {
        Zombie owner = fighter(
                helper, new Vec3(2.5D, 2.0D, 2.5D));
        UltimateLifeformData data = new UltimateLifeformData();
        LivingEntity near = spawn(
                helper,
                EntityType.COW,
                new Vec3(owner.getX(), owner.getY() + 0.4D,
                        owner.getZ() + 3.0D));
        LivingEntity far = spawn(
                helper,
                EntityType.COW,
                new Vec3(owner.getX(), owner.getY() + 0.4D,
                        owner.getZ() + 5.0D));
        ((Cow) near).setNoAi(true);
        ((Cow) far).setNoAi(true);
        near.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(0.0D);

        helper.assertTrue(
                UltimateLifeformCombat.refreshedCrosshairLivingTarget(
                        owner, 8.0D) == near,
                "Dual-target crosshair did not select the nearest hit.");
        BlockPos occluder = BlockPos.containing(
                owner.getX(), owner.getEyeY(), owner.getZ() + 2.0D);
        helper.getLevel().setBlockAndUpdate(
                occluder, Blocks.STONE.defaultBlockState());
        helper.assertTrue(
                UltimateLifeformCombat.refreshedCrosshairLivingTarget(
                        owner, 8.0D) == null,
                "Beat crosshair ignored a collidable block occluder.");
        helper.getLevel().setBlockAndUpdate(
                occluder, Blocks.AIR.defaultBlockState());
        far.discard();

        float before = near.getHealth();
        near.setDeltaMovement(Vec3.ZERO);
        // 1.16 struck the mouse target of tick 5
        LivingComponentAction.getComponent(owner)
                .entityAim.setTarget(new ActionTarget(near));
        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 4);
        helper.assertTrue(
                close(near.getHealth(), before),
                "Beat damaged before donor tick 5.");
        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 5);
        float after = near.getHealth();
        helper.assertTrue(
                close(before - after, 3.6D)
                        && close(
                                near.getDeltaMovement()
                                        .multiply(1.0D, 0.0D, 1.0D)
                                        .length(),
                                1.125D),
                "Beat missed exact damage or requested-1.25 runtime knockback: "
                        + "damage=" + (before - after)
                        + ", knockback="
                        + near.getDeltaMovement()
                                .multiply(1.0D, 0.0D, 1.0D)
                                .length());
        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT, 6);
        helper.assertTrue(
                close(near.getHealth(), after),
                "Beat applied a duplicate hit after donor tick 5.");

        near.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void guardianHitsNonLivingAndBypassesImmunity(
            GameTestHelper helper) {
        Cow owner = living(helper, new Vec3(2.5D, 2.0D, 2.5D));
        Cow livingTarget = living(
                helper,
                new Vec3(owner.getX(), owner.getY() + 0.4D,
                        owner.getZ() + 3.0D),
                false);
        UltimateProjectileEntity endpoint = projectile(
                UltimateLifeformAddon.LASER_END.get(), helper);
        endpoint.initializeGuardian(owner);
        helper.getLevel().addFreshEntity(endpoint);

        livingTarget.invulnerableTime = 20;
        float initialHealth = livingTarget.getHealth();
        endpoint.refreshGuardian(owner);
        float firstHealth = livingTarget.getHealth();
        endpoint.refreshGuardian(owner);
        helper.assertTrue(
                close(initialHealth - firstHealth, 0.25D)
                        && close(
                                firstHealth - livingTarget.getHealth(),
                                0.25D)
                        && livingTarget.invulnerableTime == 20,
                "Guardian did not deal repeated 0.25 through immunity.");

        livingTarget.discard();
        Vec3 beamStart = owner.getEyePosition().add(0.0D, 0.2D, 0.0D);
        Minecart minecart = spawn(
                helper,
                EntityType.MINECART,
                new Vec3(
                        beamStart.x,
                        beamStart.y - 0.35D,
                        beamStart.z + 3.0D));
        float minecartDamage = minecart.getDamage();
        endpoint.refreshGuardian(owner);
        helper.assertTrue(
                endpoint.getBeamLength() < 6.0D
                        && (minecart.getDamage() > minecartDamage
                                || !minecart.isAlive()),
                "Guardian did not hurt and terminate on a non-living entity.");

        minecart.discard();
        Boat boat = spawn(
                helper,
                EntityType.BOAT,
                new Vec3(
                        beamStart.x,
                        beamStart.y - 0.35D,
                        beamStart.z + 3.0D));
        float boatDamage = boat.getDamage();
        endpoint.refreshGuardian(owner);
        helper.assertTrue(
                endpoint.getBeamLength() < 6.0D
                        && (boat.getDamage() > boatDamage
                                || !boat.isAlive()),
                "Guardian did not collide with and hurt a boat.");

        boat.discard();
        endpoint.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heavyPunchMatchesExactTickDamageAndKnockback(
            GameTestHelper helper) {
        Zombie owner = fighter(
                helper, new Vec3(2.5D, 2.0D, 2.5D));
        UltimateLifeformData data = new UltimateLifeformData();
        Cow target = living(
                helper,
                new Vec3(owner.getX(), owner.getY() + 0.4D,
                        owner.getZ() + 3.0D),
                false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40.0D);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE)
                .setBaseValue(0.0D);
        target.setHealth(40.0F);
        target.setDeltaMovement(Vec3.ZERO);
        float before = target.getHealth();
        // 1.16 struck the mouse target of tick 5
        LivingComponentAction.getComponent(owner)
                .entityAim.setTarget(new ActionTarget(target));

        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HEAVY_PUNCH, 4);
        helper.assertTrue(
                close(target.getHealth(), before),
                "Heavy Punch damaged before donor tick 5.");
        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HEAVY_PUNCH, 5);
        float after = target.getHealth();
        helper.assertTrue(
                close(before - after, 12.0D)
                        && close(
                                target.getDeltaMovement()
                                .multiply(1.0D, 0.0D, 1.0D)
                                .length(),
                                2.2D),
                "Heavy Punch missed exact 12 damage or requested-2 runtime knockback: "
                        + "damage=" + (before - after)
                        + ", knockback="
                        + target.getDeltaMovement()
                                .multiply(1.0D, 0.0D, 1.0D)
                                .length());
        UltimateLifeformCombat.tickHeld(
                owner, data, UltimateLifeformSpec.HEAVY_PUNCH, 6);
        helper.assertTrue(
                close(target.getHealth(), after),
                "Heavy Punch applied a duplicate post-tick-5 hit.");

        target.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ulf_own_tentacles_and_kick_release_match_donor_cardi")
    public static void tentaclesAndKickReleaseMatchDonorCardinality(
            GameTestHelper helper) {
        Cow owner = living(
                helper, new Vec3(2.5D, 2.0D, 2.5D));
        UltimateLifeformData data = new UltimateLifeformData();
        UltimateLifeformCombat.perform(
                owner, data, UltimateLifeformSpec.TENTACLES, 0);
        // only this test's own entities: a neighbouring test's stay its own
        List<UltimateProjectileEntity> tentacles = helper.getLevel()
                .getEntitiesOfClass(
                        UltimateProjectileEntity.class,
                        owner.getBoundingBox().inflate(8.0D),
                        tentacle -> tentacle.isTentacle()
                                && tentacle.getOwner() == owner);
        helper.assertTrue(
                tentacles.size() == 8
                        && tentacles.stream().allMatch(tentacle ->
                                tentacle.getLifeTicks() == 42
                                        && tentacle.getTentacleDistance() == 0.0D
                                        && !tentacle.isTentacleRetracting()),
                "Tentacles did not spawn exactly eight owner-bound ones with a 42 tick lifespan.");

        data.beginHeld(owner, UltimateLifeformSpec.ULTIMATE_HAMON_KICK);
        owner.setOnGround(true);
        UltimateLifeformCombat.tickHeld(
                owner,
                data,
                UltimateLifeformSpec.ULTIMATE_HAMON_KICK,
                2);
        List<UltimateAreaEffectEntity> releaseWaves = helper.getLevel()
                .getEntitiesOfClass(
                        UltimateAreaEffectEntity.class,
                        owner.getBoundingBox().inflate(8.0D),
                        wave -> wave.isSendo() && wave.getOwner() == owner);
        helper.assertTrue(
                data.getHeldAbility() == null
                        && releaseWaves.size() == 1
                        && releaseWaves.get(0).getWavesToAdd() == 4
                        && close(
                                releaseWaves.get(0).getEffectRadius(),
                                4.0D),
                "Landing did not release and clean up one four-wave kick Sendo.");

        releaseWaves.forEach(Entity::discard);
        Cow collisionOwner = living(
                helper, new Vec3(8.5D, 2.0D, 2.5D));
        UltimateLifeformData collisionData =
                new UltimateLifeformData();
        collisionData.beginHeld(
                collisionOwner,
                UltimateLifeformSpec.ULTIMATE_HAMON_KICK);
        collisionOwner.setOnGround(false);
        BlockPos collisionBlock = collisionOwner.blockPosition();
        // 1.16 ended the kick on the ground or in a fluid
        helper.getLevel().setBlockAndUpdate(
                collisionBlock, Blocks.WATER.defaultBlockState());
        UltimateLifeformCombat.tickHeld(
                collisionOwner,
                collisionData,
                UltimateLifeformSpec.ULTIMATE_HAMON_KICK,
                2);
        List<UltimateAreaEffectEntity> collisionWaves =
                helper.getLevel().getEntitiesOfClass(
                        UltimateAreaEffectEntity.class,
                        collisionOwner.getBoundingBox().inflate(4.0D),
                        wave -> wave.isSendo()
                                && wave.getOwner() == collisionOwner);
        helper.assertTrue(
                collisionData.getHeldAbility() == null
                        && collisionWaves.size() == 1
                        && collisionWaves.get(0).getWavesToAdd() == 4,
                "A fluid at the feet did not release and clean up kick Sendo.");

        for (UltimateProjectileEntity tentacle : tentacles) {
            tentacle.discard();
        }
        collisionWaves.forEach(Entity::discard);
        helper.getLevel().setBlockAndUpdate(
                collisionBlock, Blocks.AIR.defaultBlockState());
        collisionOwner.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void productionInputChargesExactInitialHamonCost(
            GameTestHelper helper) {
        ServerPlayer costed = ultimatePlayer(
                helper,
                "UltimateCosted",
                "a0c60e74-3566-4d13-a250-46ac3b6bcb31",
                new Vec3(2.5D, 2.0D, 2.5D));
        ServerPlayer free = ultimatePlayer(
                helper,
                "UltimateFree",
                "f4f4f95f-9e1b-40f7-96c4-b4d29768295f",
                new Vec3(6.5D, 2.0D, 2.5D));
        PlayerPower costedPower = PlayerPower.get(costed);
        PlayerPower freePower = PlayerPower.get(free);
        UltimateLifeformData costedData = costedPower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        UltimateLifeformData freeData = freePower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        costedData.addHamonCharge(
                UltimateLifeformData.MAX_HAMON_CHARGE
                        - costedData.getHamonCharge());
        freeData.addHamonCharge(
                UltimateLifeformData.MAX_HAMON_CHARGE
                        - freeData.getHamonCharge());
        // 1.16 Hamon Overdrive took an entity under the crosshair
        Cow costedTarget = durableTarget(
                helper, new Vec3(2.5D, 2.0D, 4.5D));
        bindTarget(costed, costedTarget);
        long costedAccepted = UltimateLifeformAcceptance.observation(
                costed, UltimateLifeformSpec.HAMON_OVERDRIVE)
                .acceptedCount();
        long freeAccepted = UltimateLifeformAcceptance.observation(
                free, UltimateLifeformSpec.ARMOR_PROTECTION)
                .acceptedCount();

        pressClick(
                helper,
                costed,
                costedPower,
                UltimateLifeformSpec.HAMON_OVERDRIVE,
                (short) 31_000);
        pressClick(
                helper,
                free,
                freePower,
                UltimateLifeformSpec.ARMOR_PROTECTION,
                (short) 31_001);

        helper.runAfterDelay(4, () -> {
            UltimateLifeformAcceptance.ActionObservation costedReceipt =
                    UltimateLifeformAcceptance.observation(
                            costed,
                            UltimateLifeformSpec.HAMON_OVERDRIVE);
            UltimateLifeformAcceptance.ActionObservation freeReceipt =
                    UltimateLifeformAcceptance.observation(
                            free,
                            UltimateLifeformSpec.ARMOR_PROTECTION);
            // 1.16 Hamon Overdrive needed 250 Hamon and spent none of it
            helper.assertTrue(
                    costedReceipt.acceptedCount() == costedAccepted + 1
                            && costedReceipt.lastHamonAfterAccept()
                                    == UltimateLifeformData.MAX_HAMON_CHARGE,
                    "Production input did not keep the Hamon of a Hamon Overdrive: "
                            + costedReceipt.lastHamonAfterAccept());
            helper.assertTrue(
                    freeReceipt.acceptedCount() == freeAccepted + 1
                            && freeReceipt.lastHamonAfterAccept()
                                    == UltimateLifeformData.MAX_HAMON_CHARGE,
                    "Zero-cost production input changed Hamon: "
                            + freeReceipt.lastHamonAfterAccept());
            costedPower.setPowerType(null);
            freePower.setPowerType(null);
            costedTarget.discard();
            costed.discard();
            free.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void productionInputExecutesImmediateScriptedAndHeldHits(
            GameTestHelper helper) {
        ServerPlayer immediate = ultimatePlayer(
                helper,
                "UltimateImmediate",
                "b784af1e-6196-458e-b09f-313c35b4eae0",
                new Vec3(1.5D, 2.0D, 1.5D));
        ServerPlayer scripted = ultimatePlayer(
                helper,
                "UltimateScripted",
                "77fc49d0-65a6-4b00-aa8d-f88060310a03",
                new Vec3(4.0D, 2.0D, 1.5D));
        ServerPlayer held = ultimatePlayer(
                helper,
                "UltimateHeld",
                "fe176c80-df50-4161-8442-2fb6a6967618",
                new Vec3(6.5D, 2.0D, 1.5D));
        Zombie immediateTarget = durableZombieTarget(
                helper, new Vec3(1.5D, 2.0D, 4.0D));
        Cow scriptedTarget = durableTarget(
                helper, new Vec3(4.0D, 2.0D, 4.0D));
        Cow heldTarget = durableTarget(
                helper, new Vec3(6.5D, 2.0D, 4.0D));
        PlayerPower immediatePower = PlayerPower.get(immediate);
        PlayerPower scriptedPower = PlayerPower.get(scripted);
        PlayerPower heldPower = PlayerPower.get(held);
        UltimateLifeformData immediateData = immediatePower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        UltimateLifeformData scriptedData = scriptedPower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        UltimateLifeformData heldData = heldPower.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        immediateData.addHamonCharge(
                UltimateLifeformData.MAX_HAMON_CHARGE
                        - immediateData.getHamonCharge());
        scriptedData.setPreviousMode(scripted, PillarmanMode.LIGHT);
        bindTarget(immediate, immediateTarget);
        bindTarget(scripted, scriptedTarget);
        bindTarget(held, heldTarget);
        helper.assertTrue(
                HamonAbilityHelpers.getAimTarget(
                                held, held.level())
                                .getMainEntity()
                        == heldTarget,
                "Held input fixture did not bind the production aim target.");
        float immediateHealth = immediateTarget.getHealth();
        float scriptedHealth = scriptedTarget.getHealth();
        float heldHealth = heldTarget.getHealth();

        pressClick(
                helper,
                immediate,
                immediatePower,
                UltimateLifeformSpec.HAMON_OVERDRIVE,
                (short) 31_010);
        pressClick(
                helper,
                scripted,
                scriptedPower,
                UltimateLifeformSpec.BLADE_SLASH,
                (short) 31_011);
        pressHold(
                helper,
                held,
                heldPower,
                UltimateLifeformSpec.ULTIMATE_BARRAGE,
                (short) 31_012);
        helper.assertTrue(
                HamonAbilityHelpers.getAimTarget(held, held.level())
                                .getMainEntity()
                        == heldTarget,
                "Held production input lost its live aim target on press.");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(
                    heldData.getHeldAbility()
                            == UltimateLifeformSpec.ULTIMATE_BARRAGE,
                    "Held production action left the server lifecycle before release.");
            helper.assertTrue(
                    heldData.getHeldTicks() > 0,
                    "Held production action never entered the server tick path.");
            helper.assertTrue(
                    heldTarget.level() == held.level()
                            && heldTarget.isAlive()
                            && !held.isAlliedTo(heldTarget),
                    "Held input target became invalid before the damage tick: alive="
                            + heldTarget.isAlive()
                            + ", health=" + heldTarget.getHealth()
                            + ", removed=" + heldTarget.isRemoved()
                            + ", allied=" + held.isAlliedTo(heldTarget)
                            + ", sameLevel="
                            + (heldTarget.level() == held.level()));
            helper.assertTrue(
                    heldData.getHeldTarget(held, 100.0D) == heldTarget,
                    "Held production action did not retain its input target; distanceSq="
                            + heldTarget.distanceToSqr(held));
            helper.assertTrue(
                    heldTarget.getHealth() < heldHealth,
                    "Held production tick did not damage its bound target.");
        });
        helper.runAfterDelay(6, () ->
                AbilityInput.keyRelease((short) 31_012, held));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(
                    immediateTarget.getHealth() < immediateHealth,
                    "Immediate production action did not damage its target.");
            helper.assertTrue(
                    scriptedTarget.getHealth() < scriptedHealth,
                    "Scripted production action did not damage its target.");
            helper.assertTrue(
                    heldTarget.getHealth() < heldHealth,
                    "Held production action did not damage its target.");
            helper.assertTrue(
                    heldData.getHeldAbility() == null
                            && heldData.getHeldTarget(held, 100.0D) == null,
                    "Held production action retained target state after release.");
            immediatePower.setPowerType(null);
            scriptedPower.setPowerType(null);
            heldPower.setPowerType(null);
            immediateTarget.discard();
            scriptedTarget.discard();
            heldTarget.discard();
            immediate.discard();
            scripted.discard();
            held.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "ulf_own_production_input_creates_seven_plant_infusio")
    public static void productionInputCreatesSevenPlantInfusions(
            GameTestHelper helper) {
        ServerPlayer player = ultimatePlayer(
                helper,
                "UltimatePlantInfusion",
                "11b0c21a-c533-4692-a089-0fae9047a53a",
                new Vec3(4.5D, 2.0D, 1.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.addHamonCharge(
                UltimateLifeformData.MAX_HAMON_CHARGE
                        - data.getHamonCharge());

        BlockPos center = helper.absolutePos(new BlockPos(4, 3, 4));
        Set<BlockPos> expected = new LinkedHashSet<>();
        expected.add(center);
        for (Direction direction : Direction.values()) {
            expected.add(center.relative(direction));
        }
        BlockPos potted = center.relative(Direction.NORTH);
        helper.getLevel().setBlockAndUpdate(
                potted.below(), Blocks.SMOOTH_STONE.defaultBlockState());
        for (BlockPos pos : expected) {
            helper.getLevel().setBlockAndUpdate(
                    pos,
                    pos.equals(potted)
                            ? Blocks.POTTED_DANDELION.defaultBlockState()
                            : Blocks.OAK_LEAVES.defaultBlockState());
            helper.assertTrue(
                    UltimateLifeformCombat.canChargeBlock(
                            helper.getLevel(), pos),
                    "Plant Infusion fixture is not a living block: " + pos
                            + " state=" + helper.getLevel().getBlockState(pos));
        }
        LivingComponentAction.getComponent(player).entityAim.setTarget(
                new ActionTarget(center, Direction.NORTH));
        player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                Vec3.atCenterOf(center));

        pressClick(
                helper,
                player,
                power,
                UltimateLifeformSpec.HAMON_PLANT_INFUSION,
                (short) 31_014);
        helper.runAfterDelay(4, () -> {
            List<UltimateAreaEffectEntity> charges = helper.getLevel()
                    .getEntitiesOfClass(
                            UltimateAreaEffectEntity.class,
                            new net.minecraft.world.phys.AABB(center)
                                    .inflate(2.0D),
                            effect -> effect.isBlockCharge()
                                    && effect.getOwner() == player);
            Set<BlockPos> actual = new LinkedHashSet<>();
            charges.forEach(effect -> actual.add(
                    effect.getChargedBlockPos()));
            helper.assertTrue(
                    charges.size() == 7 && actual.equals(expected),
                    "Plant Infusion did not create seven exact charges: count="
                            + charges.size() + " expected=" + expected
                            + " actual=" + actual);
            helper.assertTrue(charges.stream().allMatch(effect ->
                            Float.compare(
                                    effect.getBlockChargeDamage(), 50.0F) == 0
                                    && effect.getBlockChargeInitialTicks()
                                            == 802_183),
                    "Plant Infusion charge values drifted");
            charges.forEach(Entity::discard);
            power.setPowerType(null);
            player.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 140)
    public static void atmosphericRiftDamagesItsSurvivalUserAfterCharge(
            GameTestHelper helper) {
        ServerPlayer player = damageableUltimatePlayer(
                helper,
                "UltimateRift",
                "4767146b-031e-4c58-a9fb-1062a3df62b2",
                new Vec3(2.5D, 2.0D, 2.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.setPreviousMode(player, PillarmanMode.WIND);
        float[] healthBefore = { Float.NaN };

        helper.runAfterDelay(61, () -> pressHold(
                helper,
                player,
                power,
                UltimateLifeformSpec.ATMOSPHERIC_RIFT,
                (short) 31_013));

        helper.runAfterDelay(100, () -> {
            player.setHealth(player.getMaxHealth());
            float probeBefore = player.getHealth();
            boolean probeDamage = player.hurt(
                    player.damageSources().generic(), 1.0F);
            helper.assertTrue(
                    probeDamage && player.getHealth() < probeBefore,
                    "Atmospheric Rift fixture rejected its donor self-damage source: invulnerable="
                            + player.isInvulnerableTo(
                                    player.damageSources().generic())
                            + " health=" + player.getHealth()
                            + " before=" + probeBefore);
            player.setHealth(player.getMaxHealth());
            healthBefore[0] = player.getHealth();
            player.invulnerableTime = 0;
        });

        helper.runAfterDelay(105, () -> {
            helper.assertTrue(
                    data.getHeldAbility()
                            == UltimateLifeformSpec.ATMOSPHERIC_RIFT
                            && data.getHeldTicks() >= 2,
                    "Atmospheric Rift did not reach its first donor emission tick: held="
                            + data.getHeldAbility()
                            + " ticks=" + data.getHeldTicks());
            helper.assertTrue(
                    player.getHealth() < healthBefore[0],
                    "Atmospheric Rift first donor emission did not self-damage: before="
                            + healthBefore[0]
                            + " after=" + player.getHealth()
                            + " invulnerableTime="
                            + player.invulnerableTime);
            // 1.16 FINAL_MODE_SELF_DAMAGE = new DamageSource("generic").bypassArmor()
            helper.assertTrue(
                    player.getLastDamageSource() != null
                            && player.getLastDamageSource().is(
                                    net.minecraft.world.damagesource
                                            .DamageTypes.GENERIC),
                    "1.16 hurt the rift's user with the generic source; got "
                            + (player.getLastDamageSource() == null ? null
                                    : player.getLastDamageSource()
                                            .typeHolder()));
        });

        helper.runAfterDelay(110, () -> {
            helper.assertTrue(
                    data.getHeldAbility()
                            == UltimateLifeformSpec.ATMOSPHERIC_RIFT
                            && data.getHeldTicks() >= 2,
                    "Atmospheric Rift never entered its held server ticks: held="
                            + data.getHeldAbility()
                            + " ticks=" + data.getHeldTicks());
            AbilityInput.keyRelease((short) 31_013, player);
            power.setPowerType(null);
            player.discard();
            helper.succeed();
        });
    }

    // 1.16 stone_form and enhanced_senses ignored the performer stun. Stone
    // form stuns its own user every tick, so without the flag the press to
    // leave it was refused, and the held check dropped the 80-tick charge.
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void stoneFormAndSensesHoldThroughOwnStun(
            GameTestHelper helper) {
        ServerPlayer player = ultimatePlayer(
                helper,
                "UltimateStoneStun",
                "3f6b2c1d-7e4a-4b58-9a0c-51d2e8f7a613",
                new Vec3(2.5D, 2.0D, 2.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        data.toggleStoneForm(player);

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(
                    data.isStoneForm()
                            && ModStatusEffects.isStunned(player),
                    "Stone form did not stun its survival user.");
            Ability stoneForm = power.getAbility(
                    UltimateLifeformSpec.STONE_FORM.id);
            helper.assertTrue(
                    stoneForm != null
                            && stoneForm.checkConditions(power)
                                    .isPositive(),
                    "The press to leave stone form was refused while stunned.");
            Ability nightVision = power.getAbility(
                    UltimateLifeformSpec.NIGHT_VISION.id);
            helper.assertTrue(
                    nightVision != null
                            && !nightVision
                                    .checkMainModLogicConditions(power)
                                    .isPositive(),
                    "The stun did not refuse an action 1.16 did not exempt.");
            pressHold(
                    helper,
                    player,
                    power,
                    UltimateLifeformSpec.STONE_FORM,
                    (short) 31_020);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(
                    !data.isStoneForm(),
                    "The 80-tick stone form hold did not survive the user's own stun.");
            AbilityInput.keyRelease((short) 31_020, player);
            // A fake player's effects never tick down; keep the stun explicit.
            player.addEffect(new MobEffectInstance(
                    ModStatusEffects.STUN, 200));
            helper.assertTrue(
                    ModStatusEffects.isStunned(player),
                    "Enhanced Senses fixture is not stunned.");
            Ability senses = power.getAbility(
                    UltimateLifeformSpec.ENHANCED_SENSES.id);
            helper.assertTrue(
                    senses != null
                            && senses.checkConditions(power).isPositive(),
                    "The Enhanced Senses press was refused while stunned.");
            pressHold(
                    helper,
                    player,
                    power,
                    UltimateLifeformSpec.ENHANCED_SENSES,
                    (short) 31_021);
        });
        helper.runAfterDelay(106, () -> {
            helper.assertTrue(
                    data.getHeldAbility()
                                    == UltimateLifeformSpec.ENHANCED_SENSES
                            && data.getHeldTicks() > 0,
                    "A stun ended the held Enhanced Senses: held="
                            + data.getHeldAbility());
            AbilityInput.keyRelease((short) 31_021, player);
        });
        helper.runAfterDelay(108, () -> pressHold(
                helper,
                player,
                power,
                UltimateLifeformSpec.ULTIMATE_BARRAGE,
                (short) 31_022));
        helper.runAfterDelay(112, () -> {
            Ability barrage = power.getAbility(
                    UltimateLifeformSpec.ULTIMATE_BARRAGE.id);
            var current = LivingComponentAction.getCurEntityAction(player);
            helper.assertTrue(
                    data.getHeldAbility()
                                    != UltimateLifeformSpec.ULTIMATE_BARRAGE
                            && (current == null
                                    || current.isOver()
                                    || current.ability != barrage),
                    "A stun did not end a hold 1.16 did not exempt (Ultimate Barrage).");
            AbilityInput.keyRelease((short) 31_022, player);
            player.removeEffect(ModStatusEffects.STUN);
            power.setPowerType(null);
            player.discard();
            helper.succeed();
        });
    }

    // 1.16 Action.onPerform: a swingHand() action without withUserPunch
    // resets the player's attack strength.
    @GameTest(template = "empty", timeoutTicks = 40, batch = "ulf_own_swing_hand_actions_reset_attack_strength")
    public static void swingHandActionsResetAttackStrength(
            GameTestHelper helper) {
        ServerPlayer player = ultimatePlayer(
                helper,
                "UltimateSwingReset",
                "c2d94e71-0a3b-4f8e-b6d5-7e1a9c3f4b20",
                new Vec3(2.5D, 2.0D, 2.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        for (UltimateLifeformSpec spec : UltimateLifeformSpec.values()) {
            Ability ability = power.getAbility(spec.id);
            helper.assertTrue(
                    ability instanceof EntityActionAbility entity
                            && entity.resetsAttackStrengthOnPerform()
                                    == spec.resetsAttackStrengthOnPerform()
                            && entity.ignoresPerformerStun()
                                    == spec.ignoresPerformerStun(),
                    "Registered Ultimate ability flags drifted: " + spec.id);
        }
        data.setPreviousMode(player, PillarmanMode.WIND);
        setAttackStrengthTicker(player, 100);
        // the Small Sandstorm's wave would fly 60 blocks through the other
        // tests' boxes: it is kept out of the level
        java.util.function.Consumer<
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent>
                keepWaveOut = event -> {
                    if (event.getEntity() instanceof rotp.core.impl.powers
                                    .pillarman.PillarmanDivineSandstormEntity wave
                            && wave.getOwner() == player) {
                        event.setCanceled(true);
                    }
                };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.bus.api.EventPriority.HIGHEST, false,
                net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class,
                keepWaveOut);
        pressClick(
                helper,
                player,
                power,
                UltimateLifeformSpec.ARMOR_PROTECTION,
                (short) 31_023);
        helper.runAfterDelay(6, () -> {
            helper.assertTrue(
                    getAttackStrengthTicker(player) >= 100,
                    "Armor Protection (no 1.16 swingHand) reset the attack strength.");
            pressClick(
                    helper,
                    player,
                    power,
                    UltimateLifeformSpec.SMALL_SANDSTORM,
                    (short) 31_024);
        });
        helper.runAfterDelay(10, () -> {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(
                    keepWaveOut);
            helper.assertTrue(
                    getAttackStrengthTicker(player) <= 1,
                    "Small Sandstorm (1.16 swingHand) did not reset the attack strength: "
                            + getAttackStrengthTicker(player));
            power.setPowerType(null);
            player.discard();
            helper.succeed();
        });
    }

    // 1.16 onGameModeChange -> resetCooldowns: entering Creative clears Ultimate cooldowns
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creativeModeClearsAbilityCooldowns(
            GameTestHelper helper) {
        ServerPlayer player = ultimatePlayer(
                helper,
                "UltimateCreativeReset",
                "3f8a6c21-7d4e-4b19-a0c5-9e2b7d1f6a48",
                new Vec3(2.5D, 2.0D, 2.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        String id = UltimateLifeformSpec.HEAVY_PUNCH.id;
        data.setAbilityCooldown(id, 100);
        helper.assertTrue(
                data.isAbilityOnCooldown(id)
                        && data.getAbilityCooldownTotalTicks(id) == 100,
                "Ultimate cooldown setup failed: " + data.getAbilityCooldownsSnapshot());
        helper.assertTrue(
                player.setGameMode(GameType.CREATIVE),
                "Ultimate player could not enter Creative.");
        helper.assertTrue(
                data.getAbilityCooldownsSnapshot().isEmpty()
                        && data.getAbilityCooldownTotalTicks(id) == 0
                        && data.getAbilityCooldownRatio(id, 0.0F) == 0.0F,
                "Entering Creative kept Ultimate cooldowns: "
                        + data.getAbilityCooldownsSnapshot());
        power.setPowerType(null);
        player.discard();
        helper.succeed();
    }

    // 1.16 Ultimate Lifeform cooldowns were PowerBaseImpl cooldowns:
    // checkRequirements refused an action on cooldown with no message.
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void abilityOnCooldownIsRefusedWithoutAMessage(
            GameTestHelper helper) {
        ServerPlayer player = ultimatePlayer(
                helper,
                "UltimateCooldownRefusal",
                "5c2e9a71-3b8d-4f06-a1e4-7d9b2c6f0e35",
                new Vec3(2.5D, 2.0D, 2.5D));
        PlayerPower power = PlayerPower.get(player);
        UltimateLifeformData data = power.getCurTypeData(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM).orElseThrow();
        for (UltimateLifeformSpec spec : new UltimateLifeformSpec[] {
                UltimateLifeformSpec.HEAVY_PUNCH,
                UltimateLifeformSpec.NIGHT_VISION }) {
            Ability ability = power.getAbility(spec.id);
            helper.assertTrue(ability != null,
                    "The Ultimate Lifeform moveset has no " + spec.id);
            data.setAbilityCooldown(spec.id, 100);
            var check = ability.checkConditions(power);
            var warning = check.getWarning();
            helper.assertTrue(
                    !check.isPositive() && warning == null,
                    "1.16 refused " + spec.id
                            + " on cooldown without a message: got "
                            + (check.isPositive() ? "admitted"
                                    : warning != null
                                            ? warning.getString()
                                            : "no message"));
        }
        power.setPowerType(null);
        player.discard();
        helper.succeed();
    }

    private static Field attackStrengthTicker() {
        try {
            Field field = LivingEntity.class.getDeclaredField(
                    "attackStrengthTicker");
            field.setAccessible(true);
            return field;
        }
        catch (NoSuchFieldException e) {
            throw new IllegalStateException(
                    "LivingEntity.attackStrengthTicker is missing", e);
        }
    }

    private static void setAttackStrengthTicker(
            LivingEntity entity, int ticks) {
        try {
            attackStrengthTicker().setInt(entity, ticks);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int getAttackStrengthTicker(LivingEntity entity) {
        try {
            return attackStrengthTicker().getInt(entity);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ServerPlayer ultimatePlayer(
            GameTestHelper helper,
            String name,
            String uuid,
            Vec3 relativePosition) {
        ServerPlayer player = FakePlayerFactory.get(
                helper.getLevel(),
                new GameProfile(UUID.fromString(uuid), name));
        player.setPos(absolute(helper, relativePosition));
        player.setGameMode(GameType.SURVIVAL);
        if (!helper.getLevel().addFreshEntity(player)) {
            throw new IllegalStateException(
                    "Ultimate input player could not be added: " + name);
        }
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Ultimate input power grant failed: " + name);
        }
        // a new Ultimate Lifeform starts with no energy (1.16); these
        // fixtures start full
        power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElseThrow()
                .setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        power.updateAvailableMoves();
        return player;
    }

    private static ServerPlayer damageableUltimatePlayer(
            GameTestHelper helper,
            String name,
            String uuid,
            Vec3 relativePosition) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(
                UUID.fromString(uuid), name);
        ServerPlayer player = new ServerPlayer(
                server,
                level,
                profile,
                ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(profile, false);
        new SilentServerPacketListener(
                server, connection, player, cookie);
        player.setPos(absolute(helper, relativePosition));
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        PlayerPower power = PowerClass.PLAYER_POWER.attachGet(player);
        if (!power.trySetPowerType(
                UltimateLifeformAddon.ULTIMATE_LIFEFORM.get())) {
            throw new IllegalStateException(
                    "Ultimate input power grant failed: " + name);
        }
        // a new Ultimate Lifeform starts with no energy (1.16); these
        // fixtures start full
        power.getCurTypeData(UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElseThrow()
                .setCommandEnergy(player, UltimateLifeformData.MAX_ENERGY);
        power.updateAvailableMoves();
        return player;
    }

    private static void pressClick(
            GameTestHelper helper,
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformSpec spec,
            short key) {
        Ability ability = power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Ultimate input ability is unavailable: " + spec.id);
        BufferingState buffering = BufferingState.clickCanBuffer();
        Object held;
        FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
        try {
            held = AbilityInput.keyPress(
                    key,
                    ability,
                    player,
                    extra,
                    InputMethod.CLICK,
                    0.0F,
                    buffering,
                    ability.getAbilityId());
        }
        finally {
            extra.release();
        }
        helper.assertTrue(held != null || buffering.isActionSuccess,
                "Ultimate production input was rejected: " + spec.id);
        AbilityInput.keyRelease(key, player);
    }

    private static void pressHold(
            GameTestHelper helper,
            ServerPlayer player,
            PlayerPower power,
            UltimateLifeformSpec spec,
            short key) {
        Ability ability = power.getAbility(spec.id);
        helper.assertTrue(ability != null,
                "Ultimate input ability is unavailable: " + spec.id);
        BufferingState buffering = BufferingState.clickCanBuffer();
        Object held;
        FriendlyByteBuf extra = new FriendlyByteBuf(Unpooled.buffer());
        try {
            held = AbilityInput.keyPress(
                    key,
                    ability,
                    player,
                    extra,
                    InputMethod.HOLD,
                    0.0F,
                    buffering,
                    ability.getAbilityId());
        }
        finally {
            extra.release();
        }
        helper.assertTrue(held != null || buffering.isActionSuccess,
                "Ultimate production input was rejected: " + spec.id);
    }

    private static Cow durableTarget(
            GameTestHelper helper, Vec3 relativePosition) {
        Cow target = living(helper, relativePosition);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        target.setHealth(1000.0F);
        return target;
    }

    private static Zombie durableZombieTarget(
            GameTestHelper helper, Vec3 relativePosition) {
        Zombie target = spawn(
                helper,
                EntityType.ZOMBIE,
                absolute(helper, relativePosition));
        target.setNoAi(true);
        target.setNoGravity(true);
        target.setPersistenceRequired();
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        target.setHealth(1000.0F);
        return target;
    }

    private static void bindTarget(
            ServerPlayer player, LivingEntity target) {
        LivingComponentAction.getComponent(player)
                .entityAim.setTarget(new ActionTarget(target));
        player.lookAt(
                EntityAnchorArgument.Anchor.EYES,
                target.getBoundingBox().getCenter());
    }

    private static Cow living(GameTestHelper helper, Vec3 relative) {
        return living(helper, relative, true);
    }

    private static Cow living(
            GameTestHelper helper,
            Vec3 position,
            boolean relative) {
        Cow living = EntityType.COW.create(helper.getLevel());
        if (living == null) {
            throw new IllegalStateException("Cow fixture could not be created.");
        }
        Vec3 absolute = absolute(helper, position);
        living.setPos(relative ? absolute : position);
        living.setXRot(0.0F);
        living.setYRot(0.0F);
        living.setYHeadRot(0.0F);
        living.yBodyRot = 0.0F;
        living.setNoAi(true);
        helper.getLevel().addFreshEntity(living);
        return living;
    }

    private static Zombie fighter(
            GameTestHelper helper, Vec3 relative) {
        Zombie fighter = EntityType.ZOMBIE.create(helper.getLevel());
        if (fighter == null) {
            throw new IllegalStateException(
                    "Zombie fixture could not be created.");
        }
        fighter.setPos(absolute(helper, relative));
        fighter.setXRot(0.0F);
        fighter.setYRot(0.0F);
        fighter.setYHeadRot(0.0F);
        fighter.yBodyRot = 0.0F;
        fighter.setNoAi(true);
        helper.getLevel().addFreshEntity(fighter);
        return fighter;
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void legacyScaleSaveMigratesAnglesWithoutResettingState(GameTestHelper helper) {
        Cow owner = living(helper, new Vec3(2.5D, 2.0D, 2.5D));
        owner.setXRot(-35.0F);
        owner.setYRot(65.0F);
        UltimateProjectileEntity legacy = legacyScaleProjectile(helper);
        legacy.initialize(owner, 7.0F, false, 1.7F, 0.0F,
                30, 0.0F, 0.0F, 0.0D, 0.0D, 0.0D);
        setSavedAge(legacy, 12);
        CompoundTag oldSave = new CompoundTag();
        legacy.saveWithoutId(oldSave);
        Vec3 motion = legacy.getDeltaMovement();
        helper.assertTrue(oldSave.contains("Item", Tag.TAG_COMPOUND),
                "Actual legacy scale save omitted its ThrowableItemProjectile marker.");
        helper.assertTrue(Math.abs(legacy.getXRot() - MathUtil.xRotDegFromVec(motion)) > 1.0F,
                "Legacy angle fixture did not distinguish vanilla and donor pitch.");

        UltimateScaleProjectileEntity restored = scaleProjectile(helper);
        restored.load(oldSave);
        helper.assertTrue(restored.getDeltaMovement().distanceToSqr(motion) < 1.0E-12D
                        && restored.getElapsedLifeTicks() == 12 && restored.getLifeTicks() == 30
                        && restored.getProjectileDamage() == 7.0F
                        && restored.getOwner() == owner,
                "Legacy scale migration changed motion, lifecycle, damage or owner.");
        helper.assertTrue(Math.abs(restored.getYRot() - MathUtil.yRotDegFromVec(motion)) < EPSILON
                        && Math.abs(restored.getXRot() - MathUtil.xRotDegFromVec(motion)) < EPSILON
                        && restored.yRotO == restored.getYRot() && restored.xRotO == restored.getXRot(),
                "Legacy scale migration did not restore donor launch angles.");

        restored.setYRot(11.0F);
        restored.setXRot(22.0F);
        CompoundTag newSave = new CompoundTag();
        restored.saveWithoutId(newSave);
        helper.assertTrue(!newSave.contains("Item"), "New scale save retained the legacy marker.");
        UltimateScaleProjectileEntity current = scaleProjectile(helper);
        current.load(newSave);
        helper.assertTrue(current.getYRot() == 11.0F && current.getXRot() == 22.0F
                        && current.getDeltaMovement().distanceToSqr(motion) < 1.0E-12D
                        && current.getElapsedLifeTicks() == 12,
                "Current scale save was incorrectly treated as legacy launch state.");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void scaleSpawnAndLaterMotionPreservePacketAngles(GameTestHelper helper) {
        Cow owner = living(helper, new Vec3(2.5D, 2.0D, 2.5D));
        float[][] poses = { {-35.0F, 65.0F}, {0.0F, 0.0F}, {0.25F, 0.25F} };
        for (float[] pose : poses) {
            owner.setXRot(pose[0]);
            owner.setYRot(pose[1]);
            UltimateScaleProjectileEntity source = scaleProjectile(helper);
            source.initialize(owner, 7.0F, false, 1.7F, 0.0F, 30);
            setSavedAge(source, 12);
            ServerEntity tracker = new ServerEntity(helper.getLevel(), source, 1, true, packet -> {});
            ClientboundAddEntityPacket add = (ClientboundAddEntityPacket) source.getAddEntityPacket(tracker);
            ClientboundSetEntityMotionPacket initialMotion = new ClientboundSetEntityMotionPacket(source);
            UltimateScaleProjectileEntity tracked = scaleProjectile(helper);
            tracked.recreateFromPacket(add);
            RegistryFriendlyByteBuf spawn = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
            try {
                source.writeSpawnData(spawn);
                tracked.readSpawnData(spawn);
                helper.assertTrue(!spawn.isReadable(), "Scale spawn payload has unread bytes.");
            }
            finally {
                spawn.release();
            }
            tracked.getEntityData().assignValues(source.getEntityData().getNonDefaultValues());
            tracked.lerpMotion(initialMotion.getXa(), initialMotion.getYa(), initialMotion.getZa());
            helper.assertTrue(Math.abs(tracked.getXRot() - add.getXRot()) < EPSILON
                            && Math.abs(tracked.getYRot() - add.getYRot()) < EPSILON
                            && tracked.xRotO == tracked.getXRot() && tracked.yRotO == tracked.getYRot()
                            && tracked.getElapsedLifeTicks() == 12 && tracked.tickCount == 12
                            && tracked.getLifeTicks() == 30 && tracked.getOwner() == owner,
                    "Initial motion overwrote packet angles, owner or spawn-age metadata.");
            helper.assertTrue(tracked.getDeltaMovement().distanceToSqr(new Vec3(
                            initialMotion.getXa(), initialMotion.getYa(), initialMotion.getZa())) < EPSILON,
                    "Scale ignored actual initial motion.");
            if (pose[0] == 0.25F) {
                helper.assertTrue(add.getXRot() == 0.0F && add.getYRot() == 0.0F
                                && Math.abs(initialMotion.getYa()) > 0.0D,
                        "Quantized-zero fixture did not distinguish packet angles from velocity.");
            }
            float oldX = tracked.getXRot();
            float oldY = tracked.getYRot();
            float oldXO = tracked.xRotO;
            float oldYO = tracked.yRotO;
            ClientboundSetEntityMotionPacket later = new ClientboundSetEntityMotionPacket(
                    tracked.getId(), new Vec3(-0.5D, 0.8D, 0.2D));
            tracked.lerpMotion(later.getXa(), later.getYa(), later.getZa());
            helper.assertTrue(tracked.getXRot() == oldX && tracked.getYRot() == oldY
                            && tracked.xRotO == oldXO && tracked.yRotO == oldYO
                            && tracked.getDeltaMovement().distanceToSqr(new Vec3(
                                    later.getXa(), later.getYa(), later.getZa())) < EPSILON,
                    "Noninitial motion normalized packet-owned angles or was ignored.");
        }
        helper.succeed();
    }

    @SuppressWarnings("unchecked")
    private static UltimateProjectileEntity legacyScaleProjectile(GameTestHelper helper) {
        // Exercise the unchanged old serializer under the preserved scale registry ID.
        EntityType<UltimateProjectileEntity> type = (EntityType<UltimateProjectileEntity>)
                (EntityType<?>) UltimateLifeformAddon.SCALE_PROJECTILE.get();
        return new UltimateProjectileEntity(type, helper.getLevel());
    }

    private static UltimateScaleProjectileEntity scaleProjectile(GameTestHelper helper) {
        UltimateScaleProjectileEntity entity = UltimateLifeformAddon.SCALE_PROJECTILE.get()
                .create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException("Ultimate scale projectile could not be created.");
        }
        return entity;
    }

    private static UltimateScaleProjectileEntity scaleRoundTrip(
            UltimateScaleProjectileEntity source, GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        helper.assertTrue(tag.contains("Age"), "Scale save omitted elapsed Age.");
        UltimateScaleProjectileEntity restored = scaleProjectile(helper);
        restored.load(tag);
        return restored;
    }

    private static UltimateScaleProjectileEntity scaleSpawnRoundTrip(
            UltimateScaleProjectileEntity source, GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            source.writeSpawnData(buffer);
            UltimateScaleProjectileEntity restored = scaleProjectile(helper);
            restored.readSpawnData(buffer);
            helper.assertTrue(!buffer.isReadable(), "Scale spawn data left unread bytes.");
            return restored;
        }
        finally {
            buffer.release();
        }
    }

    private static UltimateProjectileEntity projectile(
            EntityType<UltimateProjectileEntity> type,
            GameTestHelper helper) {
        UltimateProjectileEntity entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException(
                    "Ultimate projectile could not be created.");
        }
        return entity;
    }

    private static UltimateAreaEffectEntity area(
            EntityType<UltimateAreaEffectEntity> type,
            GameTestHelper helper) {
        UltimateAreaEffectEntity entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException(
                    "Ultimate area effect could not be created.");
        }
        return entity;
    }

    private static UltimateProjectileEntity roundTrip(
            EntityType<UltimateProjectileEntity> type,
            UltimateProjectileEntity source,
            GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        helper.assertTrue(
                tag.contains("Age"),
                "Projectile save omitted elapsed Age.");
        UltimateProjectileEntity restored = projectile(type, helper);
        restored.load(tag);
        return restored;
    }

    private static UltimateAreaEffectEntity roundTrip(
            EntityType<UltimateAreaEffectEntity> type,
            UltimateAreaEffectEntity source,
            GameTestHelper helper) {
        CompoundTag tag = new CompoundTag();
        source.saveWithoutId(tag);
        helper.assertTrue(
                tag.contains("Age"),
                "Area-effect save omitted elapsed Age.");
        UltimateAreaEffectEntity restored = area(type, helper);
        restored.load(tag);
        return restored;
    }

    private static UltimateProjectileEntity spawnRoundTrip(
            EntityType<UltimateProjectileEntity> type,
            UltimateProjectileEntity source,
            GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.NEOFORGE);
        try {
            source.writeSpawnData(buffer);
            UltimateProjectileEntity restored = projectile(type, helper);
            restored.readSpawnData(buffer);
            helper.assertTrue(
                    !buffer.isReadable(),
                    "Projectile spawn data left unread bytes.");
            return restored;
        }
        finally {
            buffer.release();
        }
    }

    private static UltimateAreaEffectEntity spawnRoundTrip(
            EntityType<UltimateAreaEffectEntity> type,
            UltimateAreaEffectEntity source,
            GameTestHelper helper) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                helper.getLevel().registryAccess(),
                ConnectionType.NEOFORGE);
        try {
            source.writeSpawnData(buffer);
            UltimateAreaEffectEntity restored = area(type, helper);
            restored.readSpawnData(buffer);
            helper.assertTrue(
                    !buffer.isReadable(),
                    "Area-effect spawn data left unread bytes.");
            return restored;
        }
        finally {
            buffer.release();
        }
    }

    private static void setSavedAge(Entity entity, int age) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        tag.putInt("Age", age);
        entity.load(tag);
    }

    private static <T extends Entity> T spawn(
            GameTestHelper helper,
            EntityType<T> type,
            Vec3 absolutePosition) {
        T entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException(
                    "GameTest entity could not be created: " + type);
        }
        entity.setPos(absolutePosition);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static Vec3 absolute(
            GameTestHelper helper, Vec3 relative) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return relative.add(
                origin.getX(), origin.getY(), origin.getZ());
    }

    private static final class SilentServerPacketListener
            extends ServerGamePacketListenerImpl {
        private SilentServerPacketListener(
                MinecraftServer server,
                Connection connection,
                ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(
                Packet<?> packet,
                PacketSendListener sendListener) {
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }
}
