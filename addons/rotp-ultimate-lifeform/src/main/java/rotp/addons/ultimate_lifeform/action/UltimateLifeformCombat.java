package rotp.addons.ultimate_lifeform.action;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.HamonSparksLoopSound;
import rotp.core.client.sound.sounds.EntityStoppableSoundInstance;
import rotp.core.customobjects.explosion.CustomExplosion;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModParticles;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.ModEntityTypes;
import rotp.core.mechanics.JojoDefinitions;
import rotp.core.mechanics.KnockbackCollisionImpact;
import rotp.core.mechanics.HypnosisEffect;
import rotp.core.network.s2c.TrHamonParticlesPacket;
import rotp.core.network.s2c.TrPillarmanParticlesPacket;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.HamonWallClimbingHelper;
import rotp.core.impl.powers.hamon.HamonHypnosisState;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.impl.powers.hamon.entity.LeavesGliderEntity;
import rotp.core.impl.powers.pillarman.PillarmanDivineSandstormEntity;
import rotp.core.impl.powers.pillarman.PillarmanHornEntity;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanErraticBlazeKingAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanSelfDetonationAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.addons.ultimate_lifeform.UltimateHamonCharge;
import rotp.addons.ultimate_lifeform.UltimateHamonEntityCharge;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.client.UltimatePillarAuraClient;
import rotp.addons.ultimate_lifeform.effect.UltimateHamonSpreadEffect;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import rotp.addons.ultimate_lifeform.network.UltimateSparkBurstPayload;
import rotp.addons.ultimate_lifeform.network.UltimateUserSoundPayload;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.ChorusPlantBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CoralFanBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.CoralWallFanBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.FrogspawnBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnifferEggBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class UltimateLifeformCombat {
    // 1.16 PillarmanAbsorption.getMaxRangeSqEntityTarget
    public static final double ABSORPTION_RANGE_SQ = 4.0D;
    // 1.16 UltimateLifeformSendoOverdrive kept Action.getMaxRangeSqBlockTarget
    public static final double SENDO_RANGE_SQ = 100.0D;

    private UltimateLifeformCombat() {}

    public static void perform(
            LivingEntity user,
            UltimateLifeformData data,
            UltimateLifeformSpec spec,
            int chargeTicks) {
        if (user.level().isClientSide()) {
            return;
        }
        UltimateLifeformAcceptance.recordExecution(user, spec);
        swingIfSwingHand(user, spec);
        switch (spec) {
            // 1.16 UltimateLifeformHamonOverdrive.perform: the entity target
            // its press took, if living
            case HAMON_OVERDRIVE -> {
                if (crosshairTarget(user).getMainEntity()
                                instanceof LivingEntity target
                        && target != user
                        && hamon(user, target, 16.0F)) {
                    UltimateHamonSpreadEffect.stackUltimateHamonBurn(
                            target, 0);
                }
            }
            case STONE_FORM -> data.toggleStoneForm(user);
            case TENTACLES -> spawnTentacles(user);
            case HORN_ATTACK -> spawnHorn(user);
            case LIGHT_FLASH -> {
                applyFlash(user);
                lightFlashBurst(user, ModParticles.LIGHT_MODE_FLASH.get());
            }
            case LIGHT_FLASH_DECOY -> {
                user.addEffect(
                        new MobEffectInstance(
                                ModStatusEffects.FULL_INVISIBILITY,
                                100,
                                0,
                                false,
                                false,
                                false));
                lightFlashBurst(user, ParticleTypes.FLASH);
            }
            case SMALL_SANDSTORM ->
                    spawnSandstorm(
                            user, false, 1.5F, 6.0F, 40, 1.5F, 1.0F);
            case GIANT_CARTHWHEEL_PRISON ->
                    spawnGiantCarthwheelPrison(user);
            case SELF_DETONATION -> selfDetonate(user);
            case DIVINE_SANDSTORM,
                    ATMOSPHERIC_RIFT,
                    ULTIMATE_HAMON_OVERDRIVE,
                    HEAVY_PUNCH,
                    HAMON_OVERDRIVE_BEAT,
                    BLADE_SLASH,
                    ERRATIC_BLAZE_KING,
                    ULTIMATE_HAMON_KICK,
                    HAMON_SHOCK -> {
                // Their timed server actions execute from tickHeld.
            }
            case ULTIMATE_HAMON_SENDO_OVERDRIVE ->
                    spawnTargetedSendo(user, 1.125F, 9.0F, 8);
            case ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE ->
                    spawnTurquoise(user);
            case HAMON_PLANT_INFUSION,
                    HAMON_ORGANISM_INFUSION ->
                    performInfusion(user, spec);
            /*
             * 1.16 UltimateLifeformFlightDash.perform ran on the user's
             * client: (the client's motion + look) * 0.9 and the wing flap
             * for the user. A player's motion is the client's, so the server
             * starts from the movement the client last reported and sends
             * the result to it.
             */
            case ULTIMATE_FLIGHT_DASH -> {
                Vec3 motion = user instanceof ServerPlayer player
                        ? player.getKnownMovement()
                        : user.getDeltaMovement();
                Vec3 dash = motion
                        .add(user.getLookAngle())
                        .scale(0.9D);
                user.setDeltaMovement(dash);
                user.hurtMarked = true;
                user.fallDistance = 0.0F;
                if (user instanceof Player player) {
                    playOwnSound(
                            player,
                            UltimateLifeformAddon.SOUNDS
                                    .get("ultimate_lifeform_wing_flap").get(),
                            player.getSoundSource(),
                            1.0F,
                            1.0F);
                }
            }
            case SCALE_SPLIT_SHOT -> {
                for (int index = 0; index < 5; index++) {
                    spawnScaleProjectile(
                            user,
                            UltimateScaleProjectileEntity.DONOR_DAMAGE,
                            false,
                            (float) (2.0D
                                    + user.getDeltaMovement().length()),
                            8.0F);
                }
            }
            case BUNNY_SUMMON -> summonRabbits(user, data);
            case PHANTOM_SUMMON -> summonPhantoms(user, data);
            // 1.16 ProtectionArmor / FireProtectionArmor.perform toggled by
            // the effect: after milk or fire took it off, a press put it on
            // again
            case ARMOR_PROTECTION ->
                    data.setArmorType(
                            user,
                            user.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)
                                    ? UltimateLifeformData
                                            .ArmorType.NONE
                                    : UltimateLifeformData
                                            .ArmorType.NORMAL);
            case ARMOR_FIRE_PROTECTION ->
                    data.setArmorType(
                            user,
                            user.hasEffect(
                                            UltimateLifeformAddon
                                                    .ULTIMATE_FIRE_ARMOR)
                                    ? UltimateLifeformData
                                            .ArmorType.NONE
                                    : UltimateLifeformData
                                            .ArmorType.FIRE);
            case OVERWHELMING_DESPAIR ->
                    applyOverwhelmingDespair(user);
            case NIGHT_VISION -> data.toggleNightVision(user);
            case ENDERMAN_TELEPORT -> teleport(user);
            case HYPNOTIZE -> {
                // 1.16 UltimateLifeformTameAnimal.perform: the crosshair
                // entity its checks passed
                ActionTarget aim = crosshairTarget(user);
                if (checkHypnosisTarget(user, aim).isPositive()
                        && aim.getMainEntity() instanceof LivingEntity target) {
                    HypnosisEffect.hypnotizeEntity(
                            target, user, 64000);
                }
            }
            case HAMON_LIFE_MAGNETISM -> {
                summonLeavesGlider(user, data);
            }
            case WALL_CLIMBING -> {
                // 1.16 perform: every press ends a glide; a press while
                // climbing moves to another wall and does nothing on the
                // same one
                if (user instanceof Player player) {
                    player.stopFallFlying();
                }
                // 1.16 UltimateLifeformWallClimb: the crosshair block
                ActionTarget target = crosshairTarget(user);
                if (target.getType() == TargetType.BLOCK) {
                    data.startWallClimbing(
                            user,
                            target.getBlockPos(),
                            target.getFace());
                }
            }
            default -> {
                // Continuous held abilities execute from tickHeld.
            }
        }
        playAbilitySound(user, spec);
        UltimateLifeformAcceptance.recordImmediateResult(user, spec);
        data.syncOnUpdate(user);
    }

    public static void tickHeld(
            LivingEntity user,
            UltimateLifeformData data,
            UltimateLifeformSpec spec,
            int ticksHeld) {
        if (user.level().isClientSide()) {
            return;
        }
        switch (spec) {
            case ENHANCED_SENSES -> {
                // Donor sensing is a client-only tinted glow channel.
            }
            case BLADE_DASH_ATTACK -> {
                if (ticksHeld == 1) {
                    Vec3 leap = Vec3.directionFromRotation(
                                    Mth.clamp(
                                            user.getXRot(),
                                            -45.0F,
                                            -18.0F),
                                    user.getYRot())
                            .scale(1.0D
                                    + user.getAttributeValue(
                                            Attributes.MOVEMENT_SPEED)
                                            * 20.0D);
                    user.setDeltaMovement(
                            leap.x, leap.y * 0.05D, leap.z);
                    user.hurtMarked = true;
                    // 1.16 UltimateLifeformBladeDash.Instance tick 1
                    user.swing(InteractionHand.MAIN_HAND, true);
                    user.level().playSound(
                            null,
                            user,
                            ModSoundEvents.HAMON_SYO_SWING.get(),
                            user.getSoundSource(),
                            1.0F,
                            1.0F);
                }
                sweepBladeDash(user, data);
            }
            case DIVINE_SANDSTORM -> {
                if (ticksHeld % 2 == 0) {
                    spawnSandstorm(
                            user,
                            false,
                            1.5F,
                            2.0F,
                            10,
                            0.9F,
                            2.0F);
                }
            }
            case ATMOSPHERIC_RIFT -> {
                if (ticksHeld % 2 == 0) {
                    spawnSandstorm(
                            user,
                            true,
                            0.5F,
                            2.0F,
                            60,
                            1.75F,
                            1.0F);
                    // 1.16 core PillarmanAtmosphericRift.FINAL_MODE_SELF_DAMAGE:
                    // "generic" past armor ("... died")
                    if (!(user instanceof Player player)
                            || !player.getAbilities()
                                    .instabuild) {
                        user.hurt(
                                user.damageSources().generic(),
                                1.0F);
                    }
                }
            }
            /*
             * 1.16 UltimateLifeformHamonUltimateOverdrive.Instance: ticks 1
             * and 3 (case 1 fell through into case 3) the swing sound and a
             * main hand swing; tick 5 whatever its 5-block look ray met first
             * (JojoModUtil.rayTrace: entities anywhere on the ray, else the
             * block), a living entity struck through punchPerform, a block
             * broken with the cube around it. Its client-side HAMON_SYO_PUNCH
             * at the struck entity passed a null player to ClientWorld
             * .playSound, which plays only for the client's own player, so
             * nobody heard it.
             */
            case ULTIMATE_HAMON_OVERDRIVE -> {
                if (ticksHeld == 1 || ticksHeld == 3) {
                    user.level().playSound(
                            null,
                            user,
                            ModSoundEvents.PILLAR_MAN_SWING.get(),
                            user.getSoundSource(),
                            1.0F,
                            1.25F);
                    user.swing(InteractionHand.MAIN_HAND, true);
                }
                if (ticksHeld == 5) {
                    ActionTarget aim = ultimateOverdriveTarget(user);
                    if (aim.getType() == TargetType.ENTITY
                            && aim.getMainEntity()
                                    instanceof LivingEntity target) {
                        UltimateHamonSpreadEffect.stackUltimateHamonBurn(
                                target, 4);
                        int modifier = JojoDefinitions
                                        .isUndeadOrVampiric(
                                                target)
                                ? Math.round(
                                        (float) data
                                                        .getHamonCharge()
                                                / spec.hamonCost)
                                : 1;
                        if (physical(
                                user,
                                target,
                                100.0F * modifier)) {
                            user.level().playSound(
                                    null,
                                    target,
                                    ModSoundEvents.PILLAR_MAN_PUNCH.get(),
                                    target.getSoundSource(),
                                    1.2F,
                                    0.8F);
                            target.knockback(
                                    2.0D,
                                    user.getX() - target.getX(),
                                    user.getZ() - target.getZ());
                            KnockbackCollisionImpact.getHandler(target)
                                    .onPunchSetKnockbackImpact(
                                            target.getDeltaMovement(), user);
                        }
                        emitParticles(
                                target,
                                UltimateLifeformAddon
                                        .RAINBOW_SPARK.get(),
                                30,
                                0.15D,
                                0.25D);
                        // 1.16 drew these 32 on the client, following the
                        // struck entity (createUltimateHamonSparkParticles
                        // with the entity), on the user's client and on
                        // every client tracking the user
                        UltimateSparkBurstPayload.sendToUserAndTrackers(
                                user, target, 32);
                        // 1.16 emptied the charge only when a living
                        // entity was struck
                        data.consumeHamonCharge(
                                data.getHamonCharge());
                    }
                    else if (aim.getType() == TargetType.BLOCK) {
                        ultimateOverdriveBlocks(user, aim.getBlockPos());
                    }
                }
            }
            case HAMON_SHOCK -> {
                // 1.16 UltimateLifeformHamonShock.Instance: the crosshair
                // target of tick 9 under its checkTarget, then core sparks at
                // the shocked target on every tick after tick 10
                if (ticksHeld == 9) {
                    ActionTarget aim = crosshairTarget(user);
                    Entity entity = aim.getMainEntity();
                    if (entity instanceof LivingEntity target
                            && (target.getType()
                                            == ModEntityTypes
                                                    .HAMON_MASTER.get()
                                    || ModStatusEffects
                                            .isStunned(target))) {
                        entity = null;
                    }
                    else if (entity != null
                            && !(entity instanceof LivingEntity living
                                    && HamonUtil.isLiving(living))) {
                        ConditionCheck.sendActionFailedMessage(null,
                                ConditionCheck.createNegative(
                                        "living_mob_shock"),
                                user);
                        entity = null;
                    }
                    if (entity instanceof LivingEntity target) {
                        data.consumeHamonCharge(
                                data.getHamonCharge());
                        data.setShockedTarget(target);
                        target.addEffect(
                                new MobEffectInstance(
                                        ModStatusEffects
                                                .HAMON_SHOCK,
                                        28000,
                                        4,
                                        false,
                                        false,
                                        true));
                        HamonUtil.emitHamonSparkParticles(user.level(), null,
                                target.getBoundingBox().getCenter(), 1.0F);
                    }
                }
                LivingEntity shocked = data.getShockedTarget();
                if (ticksHeld > 10 && shocked != null) {
                    HamonUtil.emitHamonSparkParticles(user.level(), null,
                            shocked.getBoundingBox().getCenter(), 1.0F);
                }
            }
            case HEAVY_PUNCH -> {
                if (ticksHeld == 3) {
                    user.swing(InteractionHand.MAIN_HAND, true);
                    user.level().playSound(
                            null,
                            user,
                            ModSoundEvents.PILLAR_MAN_SWING.get(),
                            user.getSoundSource(),
                            1.0F,
                            1.25F);
                }
                if (ticksHeld == 5) {
                    heavyPunch(user, data);
                }
            }
            case HAMON_OVERDRIVE_BEAT -> {
                if (ticksHeld == 2) {
                    user.swing(InteractionHand.OFF_HAND, true);
                    user.level().playSound(
                            null,
                            user,
                            ModSoundEvents.HAMON_SYO_SWING.get(),
                            user.getSoundSource(),
                            1.0F,
                            1.5F);
                }
                // 1.16 UltimateLifeformHamonBeat.Instance tick 5: the mouse
                // target of that tick; punch re-checked the hands before the
                // Hamon hit, the melee attack came anyway
                if (ticksHeld == 5
                        && crosshairTarget(user).getMainEntity()
                                instanceof LivingEntity target
                        && target != user) {
                    if (UltimateLifeformAbility.checkHeldItems(user, spec)
                                    .isPositive()
                            && hamon(user, target, 3.0F)) {
                        user.level().playSound(
                                null,
                                target,
                                ModSoundEvents
                                        .HAMON_SYO_PUNCH.get(),
                                target.getSoundSource(),
                                1.0F,
                                1.5F);
                        target.knockback(
                                1.25D,
                                user.getX() - target.getX(),
                                user.getZ() - target.getZ());
                        UltimateHamonSpreadEffect.stackUltimateHamonBurn(
                                target, 0);
                    }
                    HamonAbilityHelpers.doMeleeAttack(user, target);
                }
            }
            // 1.16 UltimateLifeformBladeSlash.Instance ticks 6 and 8
            case BLADE_SLASH -> {
                if (ticksHeld == 6) {
                    user.swing(InteractionHand.MAIN_HAND, true);
                    user.level().playSound(
                            null,
                            user,
                            ModSoundEvents.HAMON_SYO_SWING.get(),
                            user.getSoundSource(),
                            1.0F,
                            1.25F);
                }
                if (ticksHeld == 8) {
                    bladeSlashHit(user, 6.0F, 0.75D);
                }
            }
            case ERRATIC_BLAZE_KING -> {
                if (ticksHeld == 10) {
                    spawnErraticBlazeKing(user);
                }
            }
            case ULTIMATE_HAMON_KICK -> {
                if (ticksHeld == 1) {
                    user.setOnGround(false);
                    Vec3 leap = Vec3.directionFromRotation(
                                    Mth.clamp(
                                            user.getXRot(),
                                            -45.0F,
                                            -18.0F),
                                    user.getYRot())
                            .scale(1.0D
                                    + user.getAttributeValue(
                                            Attributes.MOVEMENT_SPEED)
                                            * 10.0D);
                    user.setDeltaMovement(
                            leap.x,
                            leap.y * 0.75D,
                            leap.z);
                    user.hurtMarked = true;
                }
                // 1.16 UltimateLifeformHamonKick.Instance: on the ground or in
                // a fluid the kick ends and its wave goes off
                if (ticksHeld > 1
                        && (user.onGround()
                                || !user.level()
                                        .getFluidState(user.blockPosition())
                                        .isEmpty())) {
                    data.releaseHeld(user);
                    return;
                }
                sweepHamonKick(user, data);
                user.fallDistance = 0.0F;
            }
            // 1.16 UltimateLifeformBladeBarrage.holdTick: the crosshair target
            // of the tick, a block by the dig rule or a living entity, 12
            // light sparks on a landed hit
            case BLADE_BARRAGE -> {
                ActionTarget target = crosshairTarget(user);
                if (target.getType() == TargetType.BLOCK) {
                    barrageBlock(user, target.getBlockPos(), true);
                }
                else if (target.getType() == TargetType.ENTITY
                        && target.getMainEntity()
                                instanceof LivingEntity living
                        && physicalThroughInvulnerability(
                                user, living, 6.0F)) {
                    sparkEffect(living, 12);
                }
                user.level().playSound(
                        null,
                        user.getX(),
                        user.getY(),
                        user.getZ(),
                        ModSoundEvents.SILVER_CHARIOT_BARRAGE_SWIPE.get(),
                        user.getSoundSource(),
                        0.5F,
                        1.0F);
            }
            /*
             * 1.16 UltimateLifeformBarrage.holdTick: the swing sound on the
             * user's client; at the crosshair a block broken by the dig rule
             * (its hit sound only then), or the player's own attack with the
             * target's hurt cooldown put back, then a 3 damage hit under
             * that cooldown. ultimate_hamon_punch was a server-side
             * playLocalSound (a no-op).
             */
            case ULTIMATE_BARRAGE -> {
                if (user instanceof Player player) {
                    playOwnSound(
                            player,
                            ModSoundEvents.STAND_PUNCH_BARRAGE_SWING.get(),
                            SoundSource.MASTER,
                            1.0F,
                            1.0F);
                }
                ActionTarget target = crosshairTarget(user);
                if (target.getType() == TargetType.BLOCK) {
                    barrageBlock(user, target.getBlockPos(), false);
                }
                else if (target.getType() == TargetType.ENTITY
                        && target.getMainEntity() != null) {
                    Entity entity = target.getMainEntity();
                    if (user instanceof Player player) {
                        int invulnerableTime = entity.invulnerableTime;
                        player.attack(entity);
                        entity.invulnerableTime = invulnerableTime;
                    }
                    if (entity instanceof LivingEntity living) {
                        living.hurt(physicalDamageSource(user), 3.0F);
                    }
                }
            }
            case ULTIMATE_HAMON_BARRAGE -> {
                // 1.16 holdTick took 20 Hamon a tick without a check, also
                // from a creative user, so the barrage and its Hamon hits
                // went on at 0 Hamon (1.16 reset a charge below 0 to 0 on
                // the same tick)
                data.consumeHamonCharge(
                        Math.min(20, data.getHamonCharge()));
                hamonBarrageTick(user, data);
                // 1.16 sent the charge to the clients on every change
                data.syncOnUpdate(user);
            }
            // 1.16 UltimateLifeformScaleShot.holdTick: every fifth tick a
            // main hand swing, the shot sound for the user and a scale
            case SCALE_SHOT -> {
                if (ticksHeld % 5 == 0) {
                    user.swing(InteractionHand.MAIN_HAND, true);
                    playUserOnlySound(user, UltimateLifeformAddon.SOUNDS
                            .get("ultimate_lifeform_scale_shot").get());
                    spawnScaleProjectile(
                            user,
                            UltimateScaleProjectileEntity.DONOR_DAMAGE,
                            false,
                            (float) (2.0D
                                    + user.getDeltaMovement().length()),
                            4.0F);
                }
            }
            case ABSORPTION -> {
                // a held tick without a target in reach did not go off
                // (continueHold, requirementsFulfilled false)
                Entity crosshair = absorptionTarget(user, data);
                List<LivingEntity> targets = crosshair == null
                        ? List.of()
                        : data.isStoneForm()
                                ? enemies(user, 1.5D)
                                : List.of((LivingEntity) crosshair);
                // 1.16 UltimateLifeformAbsorbtion.absorbEntity: a target not
                // dead or dying; one farther than 1.5 got its motion replaced
                // by a quarter of the way to the user
                for (LivingEntity target : targets) {
                    if (target == null || target.isDeadOrDying()) {
                        continue;
                    }
                    Vec3 pull = user.position()
                            .subtract(target.position())
                            .scale(0.25D);
                    if (PillarmanAbsorptionAbility.absorb(
                            user, target, 2.0F)) {
                        if (target.distanceToSqr(user) >= 2.25D) {
                            target.setDeltaMovement(pull);
                            target.hurtMarked = true;
                        }
                        // 1.16 UltimateLifeformAbsorbtion.absorbEntity:
                        // bloodDrainMultiplier (per difficulty) * 70
                        data.addEnergy(user, VampirismBloodDrainAbility
                                .bloodDrainMultiplier(user.level()) * 70.0F);
                    }
                }
            }
            // 1.16 UltimateLifeformHamonBreath.onHoldTick: +100 on both sides,
            // the change sent at once; the breath cloud on the user's client
            // (tickClient)
            case HAMON_BREATH -> {
                data.addHamonCharge(100);
                data.syncOnUpdate(user);
            }
            case GUARDIAN_LASER -> {
                if (user.level() instanceof ServerLevel level) {
                    UltimateProjectileEntity endpoint =
                            data.getGuardianEndpoint(level);
                    if (endpoint == null || endpoint.isRemoved()) {
                        endpoint = spawnGuardianEndpoint(user, data);
                    }
                    if (endpoint != null) {
                        endpoint.refreshGuardian(user);
                    }
                }
            }
            case UNLIMITED_POWER ->
                    data.addEnergy(user, 120.0F);
            // 1.16 UltimateLifeformAbsoluteImmortality.onHoldTick: its own
            // step counter, not the Absorption the user has, restarting at
            // the first step after a healing tick
            case ABSOLUTE_IMMORTALITY -> {
                if (ticksHeld % 5 == 0) {
                    if (user.getHealth()
                            < user.getMaxHealth()) {
                        user.heal(4.0F);
                        data.resetImmortalityLevel();
                    }
                    else if (data.getImmortalityLevel() <= 4) {
                        user.removeEffect(MobEffects.ABSORPTION);
                        user.addEffect(new MobEffectInstance(
                                MobEffects.ABSORPTION,
                                2400,
                                data.raiseImmortalityLevel()));
                    }
                }
            }
            default -> {
                // Evasion and agility are enforced by incoming damage.
            }
        }
        if (ticksHeld % 5 == 0) {
            data.syncOnUpdate(user);
        }
        UltimateLifeformAcceptance.recordHeldResultIfDue(
                user, spec, ticksHeld);
    }

    public static void release(
            LivingEntity user,
            UltimateLifeformData data,
            UltimateLifeformSpec spec,
            int ticksHeld) {
        UltimateLifeformAcceptance.recordRelease(
                user, spec, ticksHeld);
        if (spec == UltimateLifeformSpec.ULTIMATE_HAMON_KICK) {
            spawnSendo(
                    user,
                    user.blockPosition(),
                    Direction.UP,
                    1.0F,
                    4.0F,
                    4);
        }
        // Timed actions apply their effects while held, as in the legacy mod.
    }

    public static void started(
            LivingEntity user,
            UltimateLifeformData data,
            UltimateLifeformSpec spec) {
        UltimateLifeformAcceptance.recordExecution(user, spec);
        swingIfSwingHand(user, spec);
        if (spec == UltimateLifeformSpec.GUARDIAN_LASER) {
            spawnGuardianEndpoint(user, data);
        }
        // 1.16 UltimateLifeformHamonKick.perform on the kicker's client:
        // ULTIMATE_LIFEFORM_KICK, the ultimate_hamon_barrage sound event
        if (spec == UltimateLifeformSpec.ULTIMATE_HAMON_KICK
                && user instanceof Player player) {
            playOwnSound(
                    player,
                    UltimateLifeformAddon.SOUNDS
                            .get("ultimate_hamon_barrage").get(),
                    player.getSoundSource(),
                    1.0F,
                    1.25F);
        }
        playAbilitySound(user, spec);
        playOverdriveStrikeSound(user, spec);
        data.syncOnUpdate(user);
    }

    // 1.16 swingHand() actions swung the main arm when they went off (the
    // heavy punch's tick-3 swing starts it over, as 1.16's did)
    private static void swingIfSwingHand(
            LivingEntity user, UltimateLifeformSpec spec) {
        switch (spec) {
            case HEAVY_PUNCH,
                    HAMON_OVERDRIVE,
                    SMALL_SANDSTORM,
                    ULTIMATE_HAMON_OVERDRIVE,
                    ULTIMATE_HAMON_SENDO_OVERDRIVE,
                    ULTIMATE_HAMON_KICK,
                    ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE,
                    HAMON_PLANT_INFUSION,
                    HAMON_ORGANISM_INFUSION ->
                    user.swing(InteractionHand.MAIN_HAND, true);
            default -> {
            }
        }
    }

    // 1.16 UltimateLifeformHamonUltimateOverdrive.startedHolding: the
    // charge sound runs on the client only while the button charges.
    public static void playChargePhaseSound(
            EntityActionInstance action,
            LivingEntity user,
            UltimateLifeformSpec spec,
            ActionPhase phase) {
        if (spec != UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE
                || phase != ActionPhase.BUTTON_CHARGE
                || !user.level().isClientSide()) {
            return;
        }
        ClientsideSoundsHelper.playLoopingActionSound(
                UltimateLifeformAddon.SOUNDS
                        .get("kars_hamon_overdrive_charge").get(),
                user,
                action,
                ActionPhase.BUTTON_CHARGE,
                1.0F,
                1.0F);
    }

    // 1.16 KillerRabbitsSummon / PhantomSummon.startedHolding: on the
    // presser's own client user.playSound(SUMMON, 2.0, 1.0)
    public static void playSummonChargeSound(
            @Nullable LivingEntity user,
            UltimateLifeformSpec spec,
            ActionPhase phase) {
        if (!(user instanceof Player player)
                || user.level().isClientSide()
                || phase != ActionPhase.BUTTON_CHARGE
                || spec != UltimateLifeformSpec.BUNNY_SUMMON
                        && spec != UltimateLifeformSpec.PHANTOM_SUMMON) {
            return;
        }
        playOwnSound(
                player,
                UltimateLifeformAddon.SOUNDS
                        .get("ultimate_lifeform_summon").get(),
                player.getSoundSource(),
                2.0F,
                1.0F);
    }

    // 1.16 Unlimited Power / Absolute Immortality startedHolding: a client
    // loop that stops once the held action ends.
    public static void playHeldLoopSound(
            EntityActionInstance action,
            @Nullable LivingEntity user,
            UltimateLifeformSpec spec,
            ActionPhase phase) {
        // 1.16 UltimateLifeformAbsorbtion.onHoldTickClientEffect: the held
        // absorption loop (1.25, 0.8) while the hold has a target to drain
        if (spec == UltimateLifeformSpec.ABSORPTION
                && user != null
                && phase == ActionPhase.PERFORM
                && user.level().isClientSide()) {
            ClientsideSoundsHelper.playLoopingActionSound(
                    ModSoundEvents.PILLAR_MAN_ABSORPTION.get(),
                    user,
                    action,
                    ActionPhase.PERFORM,
                    1.25F,
                    0.8F,
                    0,
                    () -> absorptionSoundFactor(user));
            return;
        }
        // 1.16 UltimateLifeformHamonBreath.startedHolding: on the user's own
        // client a stoppable (not looping) breath sound that ends with the hold
        if (spec == UltimateLifeformSpec.HAMON_BREATH
                && user instanceof Player player
                && player.isLocalPlayer()
                && phase == ActionPhase.PERFORM) {
            ClientsideSoundsHelper.playNonVanillaClassSound(
                    new EntityStoppableSoundInstance(
                            UltimateLifeformAddon.SOUNDS
                                    .get("ultimate_hamon_breath").get(),
                            user.getSoundSource(),
                            1.0F,
                            1.0F,
                            user,
                            user.getRandom().nextLong(),
                            () -> action.isOver()
                                    || action.getPhase()
                                            != ActionPhase.PERFORM));
            return;
        }
        // 1.16 UltimateLifeformGuardianLaser.startedHolding: on the user's own
        // client the vanilla SoundEvents.field_204323_b (the underwater
        // loop), 4.0 / 1.0, looping while held
        if (spec == UltimateLifeformSpec.GUARDIAN_LASER
                && user instanceof Player player
                && player.isLocalPlayer()
                && phase == ActionPhase.PERFORM) {
            ClientsideSoundsHelper.playLoopingActionSound(
                    SoundEvents.AMBIENT_UNDERWATER_LOOP,
                    user,
                    action,
                    ActionPhase.PERFORM,
                    4.0F,
                    1.0F);
            return;
        }
        // startedHolding ran on the holder's own client alone
        String sound = heldLoopSound(spec);
        if (sound == null
                || user == null
                || phase != ActionPhase.PERFORM
                || !user.level().isClientSide()
                || !(user instanceof Player holder && holder.isLocalPlayer())) {
            return;
        }
        ClientsideSoundsHelper.playLoopingActionSound(
                UltimateLifeformAddon.SOUNDS.get(sound).get(),
                user,
                action,
                ActionPhase.PERFORM,
                1.0F,
                1.0F);
    }

    static double absorptionSoundFactor(LivingEntity user) {
        UltimateLifeformData data = rotp.core.powersystem.playerpower
                .PlayerPower.getPowerData(user,
                        UltimateLifeformAddon.ULTIMATE_LIFEFORM)
                .orElse(null);
        return data != null && absorptionTarget(user, data) != null
                ? 1.0D
                : 0.0D;
    }

    @Nullable
    static String heldLoopSound(UltimateLifeformSpec spec) {
        return switch (spec) {
            case UNLIMITED_POWER -> "stamina_regeneration";
            case ABSOLUTE_IMMORTALITY -> "regeneration";
            default -> null;
        };
    }

    // 1.16 overdrive Instance tick 1 (:151-153): every client that ran the
    // strike instance, the user's and those tracking the user
    // (TrPlayerContinuousActionPacket), played the strike sound on its own
    // player (ClientTickingSoundsHelper.playEntitySound(getClientPlayer())),
    // so each of them heard it at full volume wherever they stood
    private static void playOverdriveStrikeSound(
            LivingEntity user,
            UltimateLifeformSpec spec) {
        if (spec != UltimateLifeformSpec.ULTIMATE_HAMON_OVERDRIVE
                || !(user.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> listeners = new ArrayList<>();
        if (user instanceof ServerPlayer self) {
            listeners.add(self);
        }
        ChunkMap.TrackedEntity tracked =
                level.getChunkSource().chunkMap.entityMap.get(user.getId());
        if (tracked != null) {
            tracked.seenBy.forEach(
                    connection -> listeners.add(connection.getPlayer()));
        }
        for (ServerPlayer listener : listeners) {
            listener.connection.send(new ClientboundSoundEntityPacket(
                    UltimateLifeformAddon.SOUNDS.get("kars_hamon_overdrive"),
                    listener.getSoundSource(),
                    listener,
                    1.0F,
                    1.0F,
                    listener.getRandom().nextLong()));
        }
    }

    private static void spawnSandstorm(
            LivingEntity user,
            boolean atmospheric,
            float radius,
            float damage,
            int duration,
            float speed,
            float inaccuracy) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        PillarmanDivineSandstormEntity wave =
                new PillarmanDivineSandstormEntity(
                                level, user, 0.0F)
                        .setAtmospheric(atmospheric)
                        .setRadius(radius)
                        .setDamage(damage)
                        .setDuration(duration);
        wave.shootFromRotation(user, speed, inaccuracy);
        level.addFreshEntity(wave);
        // 1.16 Small / Divine Sandstorm 0.2, Atmospheric Rift 0.1 with each wave
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                ModSoundEvents.MAGICIANS_RED_FIRE_BLAST.get(),
                SoundSource.AMBIENT,
                atmospheric ? 0.1F : 0.2F,
                1.0F);
    }

    private static void spawnTentacles(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        float[] rotations = xRotYRotOffsets(Math.PI * 2.0D, 10.0D);
        for (int index = 0; index < 8; index++) {
            boolean right = index < 4;
            int row = index % 4;
            double offsetX = (right ? -1.0D : 1.0D)
                    * (row == 0 || row == 3 ? 0.18D : 0.22D);
            double offsetY = -0.4D - row * 0.2D;
            UltimateProjectileEntity tentacle =
                    UltimateLifeformAddon.SQUID_TENTACLE.get()
                            .create(level);
            if (tentacle != null) {
                tentacle.initializeTentacle(
                        user,
                        right,
                        rotations[0],
                        rotations[1],
                        offsetX,
                        offsetY);
                level.addFreshEntity(tentacle);
            }
        }
    }

    private static void spawnHorn(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        PillarmanHornEntity horn = new PillarmanHornEntity(user, level);
        horn.setLifeSpan(40);
        level.addFreshEntity(horn);
    }

    private static void spawnErraticBlazeKing(LivingEntity user) {
        Level level = user.level();
        for (int index = 0; index < 5; index++) {
            float[] rotations = xRotYRotOffsets(
                    (double) index / 5.0D * Math.PI * 2.0D,
                    10.0D);
            PillarmanErraticBlazeKingAbility.addVeinProjectile(
                    level,
                    user,
                    rotations[0],
                    rotations[1],
                    -0.4D,
                    -0.45D,
                    1.0D);
            PillarmanErraticBlazeKingAbility.addVeinProjectile(
                    level,
                    user,
                    rotations[0],
                    rotations[1],
                    0.425D,
                    -0.575D,
                    1.0D);
        }
    }

    private static void spawnGiantCarthwheelPrison(
            LivingEntity user) {
        Level level = user.level();
        for (int index = 0; index < 8; index++) {
            float[] rotations = xRotYRotOffsets(
                    (double) index / 8.0D * Math.PI * 2.0D,
                    2.0D);
            for (float yRot : new float[] {
                    rotations[1],
                    rotations[1] + 180.0F,
                    rotations[1] + 90.0F,
                    rotations[1] - 90.0F
            }) {
                PillarmanErraticBlazeKingAbility.addVeinProjectile(
                        level,
                        user,
                        rotations[0],
                        yRot,
                        0.0D,
                        -0.5D,
                        0.0D);
            }
        }
        user.addEffect(new MobEffectInstance(
                MobEffects.FIRE_RESISTANCE,
                600,
                0,
                false,
                false,
                false));
    }

    private static float[] xRotYRotOffsets(
            double angleXYRad, double z) {
        double xSq = -Math.cos(angleXYRad);
        double ySq = Math.sin(angleXYRad);
        xSq *= xSq;
        ySq *= ySq;
        double zSq = z * z;
        double xRot = Math.acos(Math.sqrt(
                (ySq + zSq) / (xSq + ySq + zSq)));
        double yRot = Math.acos(Math.sqrt(
                (xSq + zSq) / (xSq + ySq + zSq)));
        if (angleXYRad > Math.PI) {
            yRot *= -1.0D;
        }
        if (angleXYRad > Math.PI / 2.0D
                && angleXYRad < Math.PI * 3.0D / 2.0D) {
            xRot *= -1.0D;
        }
        return new float[] {
                (float) Math.toDegrees(xRot),
                (float) Math.toDegrees(yRot)
        };
    }

    private static void selfDetonate(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        DamageSource source = DamageUtil.make(
                level,
                ModDamageTypes.PILLAR_MAN_SELF_DETONATION,
                user,
                user);
        PillarmanSelfDetonationAbility.PillarmanExplosion explosion =
                new PillarmanSelfDetonationAbility.PillarmanExplosion(
                        level,
                        user,
                        source,
                        user.getX(),
                        user.getY(),
                        user.getZ(),
                        3.0F,
                        true,
                        Explosion.BlockInteraction.DESTROY);
        CustomExplosion.explode(explosion);
        if (!(user instanceof Player player)
                || !player.getAbilities().instabuild) {
            user.hurt(
                    level.damageSources().explosion(user, user),
                    40.0F);
            user.addEffect(new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE, 200, 0));
        }
    }

    private static void summonRabbits(
            LivingEntity user, UltimateLifeformData data) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        int spawned = 0;
        for (int index = 0; index < 5; index++) {
            UltimateRabbitEntity rabbit =
                    UltimateLifeformAddon.ULTIMATE_RABBIT.get()
                            .create(level);
            if (rabbit == null) {
                continue;
            }
            // 1.16 KillerRabbitsSummon.perform: a random spot in the
            // user's box
            rabbit.moveTo(
                    user.getRandomX(1.0D),
                    user.getRandomY(),
                    user.getRandomZ(1.0D),
                    user.getYRot(),
                    0.0F);
            rabbit.setSummonedBy(user, 600);
            if (level.addFreshEntity(rabbit)) {
                spawned++;
            }
        }
        // 1.16 added each new summon to the count
        data.setSummonedRabbits(
                data.getSummonedRabbitsCount() + spawned, 600);
    }

    private static void summonPhantoms(
            LivingEntity user, UltimateLifeformData data) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        int spawned = 0;
        for (int index = 0; index < 3; index++) {
            UltimatePhantomEntity phantom =
                    UltimateLifeformAddon.ULTIMATE_PHANTOM.get()
                            .create(level);
            if (phantom == null) {
                continue;
            }
            // 1.16 PhantomSummon.perform: a random spot in the user's box,
            // one block up
            phantom.moveTo(
                    user.getRandomX(1.0D),
                    user.getRandomY() + 1.0D,
                    user.getRandomZ(1.0D),
                    user.getYRot(),
                    0.0F);
            phantom.setSummonedBy(user, 600);
            if (level.addFreshEntity(phantom)) {
                spawned++;
            }
        }
        data.setSummonedPhantoms(
                data.getSummonedPhantomsCount() + spawned, 600);
    }

    private static void spawnScaleProjectile(
            LivingEntity user,
            float damage,
            boolean hamon,
            float speed,
            float inaccuracy) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        UltimateScaleProjectileEntity projectile =
                UltimateLifeformAddon.SCALE_PROJECTILE.get().create(level);
        if (projectile == null) {
            return;
        }
        projectile.initialize(
                user,
                damage,
                hamon,
                speed,
                inaccuracy);
        level.addFreshEntity(projectile);
    }

    @Nullable
    private static UltimateProjectileEntity spawnGuardianEndpoint(
            LivingEntity user, UltimateLifeformData data) {
        if (!(user.level() instanceof ServerLevel level)) {
            return null;
        }
        data.clearGuardianEndpoint(user);
        UltimateProjectileEntity endpoint =
                UltimateLifeformAddon.LASER_END.get().create(level);
        if (endpoint == null) {
            return null;
        }
        endpoint.initializeGuardian(user);
        if (!level.addFreshEntity(endpoint)) {
            return null;
        }
        data.setGuardianEndpoint(endpoint);
        return endpoint;
    }

    /*
     * 1.16 UltimateLifeformSendoOverdrive took the vanilla crosshair target
     * (InputHandler.pickMouseTarget); overrideVanillaMouseTarget (:75-82)
     * traced a block under it again along the look for
     * sqrt(getMaxRangeSqBlockTarget()) = 10 blocks with the COLLIDER shape, so
     * through grass and flowers to the block behind; other targets stayed, and
     * nothing under the crosshair stayed no target.
     */
    public static ActionTarget sendoTarget(LivingEntity user) {
        double range = Math.sqrt(SENDO_RANGE_SQ);
        ActionTarget aimed = crosshairTarget(user);
        if (aimed.getType() != TargetType.BLOCK) {
            return aimed;
        }
        Vec3 start = user.getEyePosition(1.0F);
        Vec3 end = start.add(user.getViewVector(1.0F).scale(range));
        return ActionTarget.fromVanilla(user.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                user)));
    }

    // 1.16 PowerBaseImpl.checkTarget: TargetRequirement BLOCK refused other
    // targets silently, then Action.checkRangeAndTarget said
    // "target_too_far" beyond 10 blocks
    public static ConditionCheck checkSendoTarget(
            LivingEntity user, ActionTarget target) {
        // 1.16 refused any other target without a message; the port's
        // "block_target" is kept (owner 2026-10-19)
        if (target.getType() != TargetType.BLOCK
                || target.getFace() == null) {
            return ConditionCheck.createNegative("block_target");
        }
        return ActionTargetRange.isBlockWithinRange(
                        user, user.level(), target.getBlockPos(),
                        SENDO_RANGE_SQ)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("target_too_far");
    }

    private static void spawnTargetedSendo(
            LivingEntity user,
            float damage,
            float radius,
            int waves) {
        ActionTarget target = sendoTarget(user);
        if (checkSendoTarget(user, target).isPositive()) {
            spawnSendo(
                    user,
                    target.getBlockPos(),
                    target.getFace(),
                    damage,
                    radius,
                    waves);
        }
    }

    private static void spawnSendo(
            LivingEntity user,
            BlockPos blockPos,
            Direction face,
            float damage,
            float radius,
            int waves) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        UltimateAreaEffectEntity area =
                UltimateLifeformAddon
                        .ULTIMATE_SENDO_HAMON_OVERDRIVE.get()
                        .create(level);
        if (area != null) {
            area.initializeSendo(
                    user,
                    blockPos,
                    face,
                    damage,
                    radius,
                    waves);
            level.addFreshEntity(area);
        }
    }

    /*
     * 1.16 UltimateLifeformSendoOverdrive.checkSpecificConditions looked for
     * the core's SENDO_HAMON_OVERDRIVE entities in the block's box; the
     * add-on's own waves never blocked a press.
     */
    public static boolean isHamonSendoOverdriveOn(
            Level level, @Nullable BlockPos blockPos,
            @Nullable Direction face) {
        if (blockPos == null) {
            return false;
        }
        return !level.getEntities(
                        ModEntityTypes.SENDO_HAMON_OVERDRIVE.get(),
                        new AABB(blockPos),
                        sendo -> blockPos.equals(sendo.getTargetedBlockPos())
                                && face == sendo.getTargetedFace())
                .isEmpty();
    }

    private static void spawnTurquoise(LivingEntity user) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        UltimateAreaEffectEntity area =
                UltimateLifeformAddon
                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE.get()
                        .create(level);
        if (area != null) {
            area.initializeTurquoise(
                    user, 10.0F, 6.0F, 190, 1.5D);
            level.addFreshEntity(area);
        }
    }

    private static void performInfusion(
            LivingEntity user, UltimateLifeformSpec spec) {
        ActionTarget target = infusionTarget(user, spec);
        if (target.getType() == TargetType.ENTITY
                && spec == UltimateLifeformSpec.HAMON_ORGANISM_INFUSION
                && target.getMainEntity() instanceof LivingEntity living
                && HamonUtil.isLiving(living)) {
            // 1.16 EntityUltimateHamonChargeCap, apart from the core's charge
            UltimateHamonEntityCharge state =
                    UltimateHamonEntityCharge.get(living);
            if (!state.hasHamonCharge()) {
                state.setHamonCharge(
                        50.0F,
                        802_183,
                        user,
                        spec.hamonCost);
            }
            return;
        }
        if (target.getType() != TargetType.BLOCK) {
            return;
        }
        // 1.16 Plant Infusion ran the Organism Infusion perform: every living
        // neighbour was charged, eggs too
        BlockPos center = target.getBlockPos();
        chargeBlock(user, center, spec.hamonCost);
        for (Direction direction : Direction.values()) {
            chargeBlock(
                    user,
                    center.relative(direction),
                    spec.hamonCost);
        }
    }

    private static void chargeBlock(
            LivingEntity user,
            BlockPos blockPos,
            float energySpent) {
        if (!(user.level() instanceof ServerLevel level)
                || !canChargeBlock(level, blockPos)) {
            return;
        }
        level.getEntitiesOfClass(
                        UltimateAreaEffectEntity.class,
                        new AABB(blockPos),
                        area -> area.isBlockCharge()
                                && blockPos.equals(
                                        area.getChargedBlockPos()))
                .forEach(Entity::discard);
        UltimateAreaEffectEntity charge =
                UltimateLifeformAddon.HAMON_BLOCK_CHARGE.get()
                        .create(level);
        if (charge != null) {
            charge.initializeBlockCharge(
                    user,
                    blockPos,
                    50.0F,
                    802_183,
                    energySpent);
            level.addFreshEntity(charge);
        }
    }

    // 1.16 UltimateLifeformOrganismInfusion.canChargeBlock (:130-146): a living
    // block, or a flower pot whose contents the core's isItemLivingMatter
    // counts as living (not a dead bush)
    public static boolean canChargeBlock(Level level, BlockPos blockPos) {
        BlockState state = level.getBlockState(blockPos);
        if (isBlockLiving(state)) {
            return true;
        }
        return state.getBlock() instanceof FlowerPotBlock pot
                && !state.is(Blocks.FLOWER_POT)
                && HamonUtil.isItemLivingMatter(new ItemStack(pot.getPotted()));
    }

    /*
     * 1.16 UltimateLifeformOrganismInfusion.isBlockLiving (:215-226), the
     * add-on's own rule (no logs, no coral blocks): Material REPLACEABLE_PLANT
     * unless "dead" is in the name; PLANT, WATER_PLANT, REPLACEABLE_FIREPROOF_
     * PLANT, REPLACEABLE_WATER_PLANT, GRASS, BAMBOO_SAPLING, BAMBOO, LEAVES,
     * CACTUS, CORAL, VEGETABLE and EGG; a SnowyDirtBlock; "mossy" in the name.
     * GRASS was the grass block, mycelium, hay, dried kelp, the two wart
     * blocks, shroomlight and the target block; VEGETABLE the pumpkins and the
     * melon; EGG the turtle and dragon eggs.
     * Blocks added after 1.16 count by their kind, as the sniffer egg: the
     * plants (big dripleaf and its stem, spore blossom, hanging roots, moss
     * carpet: PLANT / REPLACEABLE_PLANT in 1.17-1.19; glow lichen and the moss
     * block: plants on their own 1.17 materials); the newer bushes, saplings,
     * crops, vines and leaves already fall under the plant classes and the
     * leaves tag. Roots of wood, mud, rooted dirt, sculk, froglights,
     * amethyst and the bamboo block are not living.
     */
    public static boolean isBlockLiving(BlockState state) {
        Block block = state.getBlock();
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (block instanceof BushBlock
                || block instanceof VineBlock
                || block instanceof GrowingPlantBlock
                || block instanceof SugarCaneBlock
                || block instanceof CocoaBlock
                || block instanceof ChorusPlantBlock
                || block instanceof ChorusFlowerBlock
                || block instanceof CoralPlantBlock
                || block instanceof CoralFanBlock
                || block instanceof CoralWallFanBlock) {
            return !name.contains("dead");
        }
        return state.is(BlockTags.LEAVES)
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.CARVED_PUMPKIN)
                || state.is(Blocks.JACK_O_LANTERN)
                || state.is(Blocks.MELON)
                || isEggBlock(state)
                || state.is(Blocks.HAY_BLOCK)
                || state.is(Blocks.DRIED_KELP_BLOCK)
                || state.is(Blocks.NETHER_WART_BLOCK)
                || state.is(Blocks.WARPED_WART_BLOCK)
                || state.is(Blocks.SHROOMLIGHT)
                || state.is(Blocks.TARGET)
                || block instanceof SnowyDirtBlock
                || name.contains("mossy")
                || block instanceof BigDripleafBlock
                || block instanceof BigDripleafStemBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || block instanceof GlowLichenBlock
                || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.MOSS_BLOCK);
    }

    // 1.16 Material.EGG: the turtle egg and the dragon egg; the sniffer egg
    // (1.20, after materials were gone) and frogspawn (1.19, its own
    // material) are egg blocks of the same kind
    public static boolean isEggBlock(BlockState state) {
        return state.getBlock() instanceof TurtleEggBlock
                || state.getBlock() instanceof DragonEggBlock
                || state.getBlock() instanceof SnifferEggBlock
                || state.getBlock() instanceof FrogspawnBlock;
    }

    public static boolean isLeavesItem(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof LeavesBlock;
    }

    private static ItemStack findLeavesItem(LivingEntity user) {
        if (isLeavesItem(user.getMainHandItem())) {
            return user.getMainHandItem();
        }
        if (isLeavesItem(user.getOffhandItem())) {
            return user.getOffhandItem();
        }
        if (user instanceof Player player) {
            for (ItemStack stack : player.getInventory().items) {
                if (isLeavesItem(stack)) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // 1.16 UltimateLifeformLifeMagnetism.checkSpecificConditions: aimed
    // leaves, leaves in a hand or leaves in the inventory
    public static boolean hasLeavesTargetOrItem(
            LivingEntity user, ActionTarget target) {
        return target.getType() == TargetType.BLOCK
                        && user.level().getBlockState(target.getBlockPos())
                                .getBlock() instanceof LeavesBlock
                || !findLeavesItem(user).isEmpty();
    }

    // 1.16 UltimateLifeformLifeMagnetism.perform: aimed leaves become a
    // glider where they were; otherwise leaves from the hands or the
    // inventory become one the user rides
    private static void summonLeavesGlider(
            LivingEntity user, UltimateLifeformData data) {
        Level level = user.level();
        ActionTarget target = crosshairTarget(user);
        if (target.getType() == TargetType.BLOCK) {
            BlockPos blockPos = target.getBlockPos();
            BlockState state = level.getBlockState(blockPos);
            if (state.getBlock() instanceof LeavesBlock) {
                level.destroyBlock(blockPos, false, user);
                spawnLeavesGlider(user, data,
                        Vec3.atBottomCenterOf(blockPos), state);
                return;
            }
        }
        ItemStack leaves = findLeavesItem(user);
        if (leaves.isEmpty()
                || !(leaves.getItem()
                        instanceof BlockItem blockItem)) {
            return;
        }
        LeavesGliderEntity glider = spawnLeavesGlider(user, data,
                user.position(), blockItem.getBlock().defaultBlockState());
        if (glider != null) {
            user.startRiding(glider);
            if (!(user instanceof Player player)
                    || !player.getAbilities().instabuild) {
                leaves.shrink(1);
            }
        }
    }

    @Nullable
    private static LeavesGliderEntity spawnLeavesGlider(
            LivingEntity user,
            UltimateLifeformData data,
            Vec3 pos,
            BlockState leavesBlock) {
        LeavesGliderEntity glider =
                new LeavesGliderEntity(user.level());
        glider.moveTo(
                pos.x,
                pos.y,
                pos.z,
                user.getYRot(),
                user.getXRot());
        glider.setLeavesBlock(leavesBlock);
        glider.setEnergy(Math.min(
                (float) data.getHamonCharge(),
                LeavesGliderEntity.MAX_ENERGY));
        if (!user.level().addFreshEntity(glider)) {
            return null;
        }
        // 1.16 UltimateLifeformLifeMagnetism.summonGlider: core Hamon sparks
        // with their sound at the top of the glider
        HamonUtil.emitHamonSparkParticles(user.level(), null,
                new Vec3(pos.x, glider.getY(1.0D), pos.z), 0.1F);
        return glider;
    }

    private static void applyFlash(LivingEntity user) {
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                user.getBoundingBox().inflate(16.0D),
                target -> target != user
                        && target.isAlive()
                        && target.hasLineOfSight(user)
                        && !(target instanceof StandEntity stand
                                && stand.getUser() == user))) {
            int blindnessTicks = target.distanceToSqr(user) < 25.0D
                    ? 200
                    : 80;
            target.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    blindnessTicks,
                    0,
                    true,
                    true,
                    false));
            if (!(target instanceof Player)
                    && !(target instanceof StandEntity)) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN,
                        60,
                        0,
                        true,
                        true,
                        false));
            }
        }
    }

    /*
     * 1.16 PillarmanLightFlash / PillarmanLightFlashDecoy.perform:
     * AJA_STONE_BEAM (range 16: (16 + 16) / 16 = 2.0, 1.0) for everyone and
     * HamonUtil.createHamonSparkParticlesEmitter(user, 2, 0, particle), a
     * silent emitter on every client tracking the user.
     */
    private static void lightFlashBurst(
            LivingEntity user, ParticleOptions particle) {
        user.level().playSound(
                null,
                user,
                ModSoundEvents.AJA_STONE_BEAM.get(),
                user.getSoundSource(),
                2.0F,
                1.0F);
        TrHamonParticlesPacket emitter =
                TrHamonParticlesPacket.emitter(user, 2.0F, 0.0F, particle);
        if (emitter != null) {
            TrHamonParticlesPacket.send(user, emitter);
        }
    }

    /*
     * 1.16 UltimateLifeformOverwhelmingDespair.perform: in the 32-block cube
     * everyone but the user's own rabbits, phantoms, tamed animals and Stand
     * is debuffed (teammates too; passive mobs longer and stunned), the
     * user's own summons get Speed II, Strength and Resistance, and everyone
     * hears the awakening.
     */
    private static void applyOverwhelmingDespair(
            LivingEntity user) {
        Vec3 center = user.getBoundingBox().getCenter();
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                new AABB(center, center).inflate(32.0D),
                target -> target != user && target.isAlive())) {
            boolean ownedSummon = target instanceof UltimateRabbitEntity rabbit
                    && rabbit.getOwner() == user
                    || target instanceof UltimatePhantomEntity phantom
                            && phantom.getOwner() == user;
            if (ownedSummon) {
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 300, 1));
                target.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_BOOST, 300, 0));
                target.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, 300, 0));
                continue;
            }
            if (target instanceof TamableAnimal tamable
                            && tamable.getOwner() == user
                    || target instanceof StandEntity stand
                            && stand.getUser() == user) {
                continue;
            }
            boolean passive = target instanceof AgeableMob
                    && !(target instanceof UltimateRabbitEntity);
            int duration = passive ? 600 : 200;
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, duration, 2));
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, duration, 2));
            target.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, duration, 2));
            if (passive) {
                target.addEffect(new MobEffectInstance(
                        ModStatusEffects.STUN, duration, 0));
            }
        }
        user.level().playSound(
                null,
                user,
                ModSoundEvents.PILLAR_MAN_AWAKENING.get(),
                user.getSoundSource(),
                3.0F,
                1.0F);
    }

    private static void teleport(LivingEntity user) {
        ActionTarget target = aimTarget(user);
        if (target.getType() == TargetType.EMPTY) {
            // 1.16 calcBlinkPos: JojoModUtil.rayTrace(user, 24, a living
            // entity but the user's own Stand): the first entity anywhere on
            // the ray, boxes not grown, else the block it met (OUTLINE)
            List<ActionTarget> hits = HitResultUtil.clipMultipleTargets(
                    user, 24.0D,
                    entity -> entity instanceof LivingEntity
                            && !(entity instanceof StandEntity stand
                                    && stand.getUser() == user),
                    0.0D, 0.0D);
            target = hits.isEmpty() ? ActionTarget.EMPTY : hits.get(0);
        }
        Vec3 destination;
        if (target.getType() == TargetType.ENTITY
                && target.getMainEntity() != null) {
            destination = target.getMainEntity().position();
        }
        else if (target.getType() == TargetType.BLOCK) {
            BlockPos blockPos = target.getBlockPos();
            BlockPos destinationBlock = user.level().isEmptyBlock(
                    blockPos.above())
                    ? blockPos.above()
                    : blockPos.relative(target.getFace());
            destination = Vec3.atBottomCenterOf(destinationBlock);
        }
        else {
            Vec3 rayEnd = user.getEyePosition()
                    .add(user.getLookAngle().scale(24.0D));
            BlockPos floorSearch = BlockPos.containing(rayEnd);
            int minimumY = user.level().getMinBuildHeight();
            while (floorSearch.getY() > minimumY
                    && user.level().isEmptyBlock(floorSearch.below())) {
                floorSearch = floorSearch.below();
            }
            destination = new Vec3(
                    rayEnd.x,
                    floorSearch.getY() > minimumY
                            ? floorSearch.getY()
                            : user.getY(),
                    rayEnd.z);
        }
        // 1.16 perform on the user's client: 32 portal particles where the
        // user stood, seen by the user only
        List<Vec3> portal = new java.util.ArrayList<>();
        for (int index = 0; index < 32; index++) {
            portal.add(new Vec3(
                    user.getRandomX(1.0D),
                    user.getRandomY(),
                    user.getRandomZ(1.0D)));
        }
        // and Player.playSound there, before the server moved the user: the
        // user hears the teleport from where it stood
        if (user instanceof Player player) {
            playOwnSound(player, SoundEvents.ENDERMAN_TELEPORT,
                    player.getSoundSource(), 2.0F, 1.0F);
        }
        user.teleportTo(
                destination.x,
                destination.y,
                destination.z);
        if (user instanceof ServerPlayer player) {
            for (Vec3 at : portal) {
                player.serverLevel().sendParticles(
                        player,
                        ParticleTypes.PORTAL,
                        true,
                        at.x,
                        at.y,
                        at.z,
                        0,
                        (user.getRandom().nextDouble() - 0.5D) * 2.0D,
                        -user.getRandom().nextDouble(),
                        (user.getRandom().nextDouble() - 0.5D) * 2.0D,
                        1.0D);
            }
        }
    }

    // 1.16 played these on the user's client at the client's own position; the server only tells that client to
    private static void playOwnSound(
            Player player,
            net.minecraft.sounds.SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch) {
        if (player instanceof ServerPlayer serverPlayer) {
            UltimateUserSoundPayload.send(
                    serverPlayer, sound, source, volume, pitch);
        }
    }

    // 1.16 sounds played on the user's client alone
    private static void playUserOnlySound(
            LivingEntity user, net.minecraft.sounds.SoundEvent sound) {
        if (user instanceof Player player) {
            playOwnSound(player, sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    // 1.16 UltimateLifeformHeavyPunch.Instance tick 5: the mouse target of
    // that tick, a block or a living entity, nothing else
    private static void heavyPunch(
            LivingEntity user, UltimateLifeformData data) {
        ActionTarget target = crosshairTarget(user);
        if (target.getType() == TargetType.BLOCK) {
            BlockPos center = target.getBlockPos();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        heavyPunchBlock(
                                user, center.offset(x, y, z));
                    }
                }
            }
            user.level().playSound(
                    null,
                    user,
                    ModSoundEvents.HEAVY_PUNCH.get(),
                    user.getSoundSource(),
                    1.5F,
                    1.2F);
        }
        else if (target.getType() == TargetType.ENTITY
                && target.getMainEntity() instanceof LivingEntity living
                && physical(user, living, 12.0F)) {
            user.level().playSound(
                    null,
                    living,
                    ModSoundEvents.PILLAR_MAN_PUNCH.get(),
                    living.getSoundSource(),
                    1.2F,
                    0.8F);
            living.knockback(
                    2.0D,
                    user.getX() - living.getX(),
                    user.getZ() - living.getZ());
            KnockbackCollisionImpact.getHandler(living)
                    .onPunchSetKnockbackImpact(
                            living.getDeltaMovement(), user);
        }
    }

    private static void heavyPunchBlock(
            LivingEntity user, BlockPos blockPos) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir()
                || !JojoModUtil.canEntityDestroy(
                        level, blockPos, state, user)) {
            return;
        }
        float digDuration = state.getDestroySpeed(level, blockPos);
        boolean dropBlock = JojoModUtil.dropBrokenBlock(user);
        if (user instanceof Player player) {
            float digSpeed = player.getDestroySpeed(state);
            if (digSpeed > 0.0F) {
                digDuration /= digSpeed / 2.0F;
            }
            if (player.getAbilities().instabuild) {
                digDuration = 0.0F;
                dropBlock = false;
            }
            else if (!player.hasCorrectToolForDrops(state)) {
                digDuration *= 1.0F / 3.0F;
            }
        }
        double breakThreshold = 2.5D * Math.sqrt(
                user.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (digDuration >= 0.0F && digDuration <= breakThreshold) {
            JojoModUtil.destroyBlock(
                    level, blockPos, dropBlock, user);
        }
        else {
            SoundType sound = state.getSoundType(
                    level, blockPos, user);
            level.playSound(
                    null,
                    blockPos,
                    sound.getHitSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F,
                    sound.getPitch() * 0.5F);
        }
    }

    /*
     * 1.16 UltimateLifeformBladeBarrage / UltimateLifeformBarrage.holdTick on a
     * block: the dig time (hardness over the full dig speed, x3.33 without the
     * right tool, 0 in creative) within 2.5 * sqrt(attack damage) breaks it.
     * The Blade Barrage rang the block's hit sound when it held; the Ultimate
     * Barrage rang it only after a break.
     */
    private static void barrageBlock(
            LivingEntity user, BlockPos blockPos, boolean blade) {
        if (!(user.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir()
                || !JojoModUtil.canEntityDestroy(
                        level, blockPos, state, user)) {
            return;
        }
        float digDuration = state.getDestroySpeed(level, blockPos);
        boolean dropBlock = JojoModUtil.dropBrokenBlock(user);
        if (user instanceof Player player) {
            float digSpeed = player.getDestroySpeed(state);
            if (digSpeed > 0.0F) {
                digDuration /= digSpeed;
            }
            if (player.getAbilities().instabuild) {
                digDuration = 0.0F;
                dropBlock = false;
            }
            else if (!player.hasCorrectToolForDrops(state)) {
                digDuration *= 10.0F / 3.0F;
            }
        }
        boolean breaks = digDuration >= 0.0F
                && digDuration <= 2.5D * Math.sqrt(
                        user.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (breaks) {
            if (blade) {
                JojoModUtil.destroyBlock(level, blockPos, dropBlock, user);
            }
            else {
                level.destroyBlock(blockPos, dropBlock);
            }
        }
        if (breaks != blade) {
            SoundType sound = state.getSoundType(level, blockPos, user);
            level.playSound(
                    null,
                    blockPos,
                    sound.getHitSound(),
                    SoundSource.BLOCKS,
                    (sound.getVolume() + 1.0F) / 8.0F,
                    sound.getPitch() * 0.5F);
        }
    }

    private static void sweepBladeDash(
            LivingEntity user, UltimateLifeformData data) {
        AABB hitBox = sweptHitBox(user, 1.5F);
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                hitBox,
                target -> target != user
                        && target.isAlive()
                        && JojoModUtil.canHarm(user, target))) {
            if (!data.markActionHit(target)) {
                continue;
            }
            DamageSource source = physicalDamageSource(user);
            boolean hurt = target.hurt(
                    source,
                    DamageUtil.addArmorPiercing(
                            10.0F, 15.0F, target, source));
            // 1.16 UltimateLifeformBladeDash.Instance.playerTick: 60 light
            // sparks at each newly hit target
            if (hurt) {
                knockbackSideways(user, target);
                sparkEffect(target, 60);
            }
        }
    }

    private static void sweepHamonKick(
            LivingEntity user, UltimateLifeformData data) {
        AABB hitBox = sweptHitBox(user, 1.25F);
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                hitBox,
                target -> target != user
                        && target.isAlive()
                        && JojoModUtil.canHarm(user, target))) {
            if (!data.markActionHit(target)) {
                continue;
            }
            if (physical(user, target, 1.0F)) {
                knockbackSideways(user, target);
                UltimateHamonSpreadEffect.stackUltimateHamonBurn(target, 1);
            }
        }
    }

    private static AABB sweptHitBox(
            LivingEntity user, float widthMultiplier) {
        float angle = -user.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(
                Math.sin(angle), 0.0D, Math.cos(angle));
        Vec3 center = user.position().add(
                forward.scale(user.getBbWidth() * 0.75F));
        return new AABB(center, center)
                .inflate(
                        user.getBbWidth() * widthMultiplier,
                        0.125D,
                        user.getBbWidth() * widthMultiplier)
                .expandTowards(
                        0.0D, user.getBbHeight() / 2.0D, 0.0D);
    }

    private static void knockbackSideways(
            LivingEntity user, LivingEntity target) {
        Vec3 toTarget = target.position().subtract(user.position());
        boolean left = Mth.wrapDegrees(
                user.yBodyRot - MathUtil.yRotDegFromVec(toTarget)) < 0.0F;
        float angle = (60.0F + user.getRandom().nextFloat() * 30.0F)
                * (left ? 1.0F : -1.0F);
        angle += (float) -Mth.atan2(toTarget.x, toTarget.z)
                * MathUtil.RAD_TO_DEG;
        target.knockback(
                0.75F,
                Mth.sin(angle * Mth.DEG_TO_RAD),
                -Mth.cos(angle * Mth.DEG_TO_RAD));
    }

    private static boolean hitAimed(
            LivingEntity user,
            double range,
            float damage,
            boolean hamon,
            double knockback) {
        LivingEntity target = aimed(user, range);
        if (target == null) {
            return false;
        }
        boolean hurt = hamon
                ? hamon(user, target, damage)
                : physical(user, target, damage);
        if (hurt && knockback > 0.0D) {
            Vec3 look = user.getLookAngle();
            target.knockback(
                    knockback,
                    -look.x,
                    -look.z);
        }
        return hurt;
    }

    // 1.16 UltimateLifeformBladeSlash.Instance.punchPerform: the living entity
    // under the crosshair on that tick, 9 light sparks, its heavy hit sound
    // and the impact
    private static boolean bladeSlashHit(
            LivingEntity user,
            float damage,
            double knockback) {
        if (!(crosshairTarget(user).getMainEntity()
                instanceof LivingEntity target)) {
            return false;
        }
        DamageSource source = physicalDamageSource(user);
        boolean hurt = target.hurt(
                source,
                DamageUtil.addArmorPiercing(
                        damage, 15.0F, target, source));
        if (hurt) {
            sparkEffect(target, 9);
            user.level().playSound(
                    null,
                    target,
                    ModSoundEvents.THE_WORLD_PUNCH_HEAVY_ENTITY.get(),
                    target.getSoundSource(),
                    1.2F,
                    0.8F);
            target.knockback(
                    knockback,
                    user.getX() - target.getX(),
                    user.getZ() - target.getZ());
            KnockbackCollisionImpact.getHandler(target)
                    .onPunchSetKnockbackImpact(
                            target.getDeltaMovement(), user);
        }
        return hurt;
    }

    private static void sweep(
            LivingEntity user,
            double radius,
            float damage,
            boolean hamon,
            double knockback) {
        Vec3 look = user.getLookAngle();
        for (LivingEntity target : enemies(user, radius)) {
            boolean hurt = hamon
                    ? hamon(user, target, damage)
                    : physical(user, target, damage);
            if (hurt && knockback > 0.0D) {
                target.knockback(
                        knockback,
                        -look.x,
                        -look.z);
            }
        }
    }

    private static boolean physical(
            LivingEntity user,
            LivingEntity target,
            float damage) {
        return target.hurt(physicalDamageSource(user), damage);
    }

    private static boolean physicalThroughInvulnerability(
            LivingEntity user,
            LivingEntity target,
            float damage) {
        return DamageUtil.hurtThroughInvulTicks(
                target, physicalDamageSource(user), damage);
    }

    // 1.16 PillarmanUtil.sparkEffect: the light-spark emitter of a hit, sent
    // from the server to everyone tracking the entity and to the entity
    private static void sparkEffect(Entity target, int quantity) {
        if (target.level() instanceof ServerLevel) {
            TrPillarmanParticlesPacket.send(target,
                    new TrPillarmanParticlesPacket(target.getId(), quantity));
        }
    }

    private static DamageSource physicalDamageSource(
            LivingEntity user) {
        return user instanceof Player player
                ? user.damageSources().playerAttack(player)
                : user.damageSources().mobAttack(user);
    }

    // 1.16 KarsUtil.dealUltimateHamonDamage(target, damage, user, null): the
    // rainbow spark emitter, and Hamon spread on undead without any skill
    private static boolean hamon(
            LivingEntity user,
            LivingEntity target,
            float damage) {
        return UltimateHamonCharge.dealUltimateHamonDamage(
                target, damage, user, null);
    }

    /*
     * 1.16 UltimateLifeformHamonBarrage.holdTick on the server: at the
     * crosshair block the dig rule (its hit sound after a break); at an
     * entity the player's own attack with the target's hurt cooldown put
     * back and, for a living one, a plain 3 Hamon hit under the cooldown;
     * every fifth entity hit (hurtCount, one field of the action in 1.16,
     * one per user here) stacked the burn. On the user's client the swing
     * sound every 2nd tick, the spark sound every tick and 8 rainbow sparks
     * at a hit entity.
     */
    private static void hamonBarrageTick(
            LivingEntity user, UltimateLifeformData data) {
        if (user instanceof ServerPlayer player) {
            if (user.tickCount % 2 == 0) {
                playOwnSound(
                        player,
                        ModSoundEvents.STAND_PUNCH_BARRAGE_SWING.get(),
                        SoundSource.MASTER,
                        1.0F,
                        1.0F);
            }
            playOwnSound(
                    player,
                    ModSoundEvents.HAMON_SPARK.get(),
                    SoundSource.MASTER,
                    1.0F,
                    1.0F);
        }
        ActionTarget target = crosshairTarget(user);
        if (target.getType() == TargetType.BLOCK) {
            barrageBlock(user, target.getBlockPos(), false);
            return;
        }
        Entity entity = target.getType() == TargetType.ENTITY
                ? target.getMainEntity()
                : null;
        if (entity == null) {
            return;
        }
        int hits = data.countHamonBarrageHit();
        if (user instanceof Player player) {
            int invulnerableTime = entity.invulnerableTime;
            player.attack(entity);
            entity.invulnerableTime = invulnerableTime;
        }
        if (user instanceof ServerPlayer player) {
            // 1.16: 8 sparks on the user's client following the hit entity
            UltimateSparkBurstPayload.sendToUser(player, entity, 8);
        }
        if (entity instanceof LivingEntity living) {
            DamageUtil.hurtRespectingCooldown(living,
                    UltimateHamonCharge.plainHamonSource(user.level()), 3.0F);
            if (hits >= 5) {
                data.resetHamonBarrageHits();
                UltimateHamonSpreadEffect.stackUltimateHamonBurn(living, 0);
            }
        }
    }

    /*
     * 1.16 UltimateLifeformHamonUltimateOverdrive.Instance tick 5:
     * JojoModUtil.rayTrace(user, 5.0, entity -> true), the first entity
     * anywhere on the 5-block look ray, else the block it met (OUTLINE).
     */
    public static ActionTarget ultimateOverdriveTarget(LivingEntity user) {
        List<ActionTarget> hits = HitResultUtil.clipMultipleTargets(
                user, 5.0D, entity -> true, 0.0D, 0.0D);
        return hits.isEmpty() ? ActionTarget.EMPTY : hits.get(0);
    }

    /*
     * 1.16 Instance.blockDestroy, called for the aimed block and then for the
     * 3x3x3 cube around it (the aimed block twice): 7 light sparks at every
     * position, the heavy punch dig rule for a block in it.
     */
    private static void ultimateOverdriveBlocks(
            LivingEntity user, BlockPos center) {
        if (!(user.level() instanceof ServerLevel level)
                || !JojoModUtil.breakingBlocksEnabled(level)) {
            return;
        }
        ultimateOverdriveBlock(level, user, center);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    ultimateOverdriveBlock(level, user, center.offset(x, y, z));
                }
            }
        }
    }

    private static void ultimateOverdriveBlock(
            ServerLevel level, LivingEntity user, BlockPos pos) {
        Vec3 at = Vec3.atCenterOf(pos);
        level.sendParticles(ModParticles.LIGHT_SPARK.get(),
                at.x, at.y, at.z, 7, 0.0D, 0.0D, 0.0D, 0.0722D);
        heavyPunchBlock(user, pos);
    }

    // 1.16 Hamon Overdrive's ENTITY target requirement (PowerBaseImpl
    // .checkTarget, silent) and Action.checkRangeAndTarget (target_too_far)
    public static ConditionCheck checkOverdriveTarget(
            LivingEntity user, ActionTarget target) {
        if (target.getType() != TargetType.ENTITY
                || target.getMainEntity() == null
                || target.getMainEntity() == user) {
            return ConditionCheck.NEGATIVE;
        }
        return ActionTargetRange.isEntityWithinRange(user,
                        target.getMainEntity(),
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("target_too_far");
    }

    /*
     * The 1.16 client side of the Hamon moves, run on the client copy of the
     * action: the Hamon spark loop at the kicker and at the Hamon Shock user,
     * at the entity the Ultimate Hamon Overdrive's tick-5 ray meets, and the
     * Hamon Breath cloud on the breathing player's own client.
     */
    public static void tickClient(
            EntityActionInstance action,
            LivingEntity user,
            UltimateLifeformSpec spec) {
        if (!user.level().isClientSide()) {
            return;
        }
        UltimatePillarAuraClient.tick(action, user, spec);
        if (action.getPhase() != ActionPhase.PERFORM) {
            return;
        }
        switch (spec) {
            case ULTIMATE_BARRAGE, ULTIMATE_HAMON_BARRAGE ->
                    swingBarrageHands(user, (int) action.getPhaseTick() + 1);
            case ULTIMATE_HAMON_KICK -> HamonSparksLoopSound.playSparkSound(
                    user, user.position().add(0.0D,
                            user.getBbHeight() * 0.25D, 0.0D),
                    1.0F, true);
            case HAMON_SHOCK -> HamonSparksLoopSound.playSparkSound(
                    user, user.position(), 1.0F, true);
            case ULTIMATE_HAMON_OVERDRIVE -> {
                if ((int) action.getPhaseTick() == 5
                        && ultimateOverdriveTarget(user).getMainEntity()
                                instanceof LivingEntity target) {
                    HamonSparksLoopSound.playSparkSound(target,
                            target.getBoundingBox().getCenter(), 1.0F, true);
                }
            }
            case HAMON_BREATH -> {
                if (user instanceof Player player && player.isLocalPlayer()) {
                    for (int index = 0; index < 24; index++) {
                        user.level().addParticle(
                                UltimateLifeformAddon
                                        .HAMON_BREATH_AURA_RAINBOW.get(),
                                true,
                                user.getX(),
                                user.getY() + 0.8D,
                                user.getZ(),
                                (Math.random() - 0.5D) * 0.675D,
                                (Math.random() - 0.5D) * 0.675D,
                                (Math.random() - 0.5D) * 0.675D);
                    }
                }
            }
            default -> {
            }
        }
    }

    /*
     * 1.16 UltimateLifeformBarrage.onHoldTickClientEffect (the Hamon Barrage
     * inherits it), on every client holding the barrage: every 2nd held tick
     * a fresh swing, the off hand at 2, the main hand at 4 and so on.
     */
    public static void swingBarrageHands(LivingEntity user, int ticksHeld) {
        if (ticksHeld % 2 == 0) {
            user.swinging = false;
            user.swing(ticksHeld % 4 == 0
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND);
        }
    }

    private static void emitParticles(
            LivingEntity anchor,
            ParticleOptions particle,
            int count,
            double spread,
            double speed) {
        if (anchor.level() instanceof ServerLevel level) {
            level.sendParticles(
                    particle,
                    anchor.getX(),
                    anchor.getY(0.5D),
                    anchor.getZ(),
                    count,
                    spread,
                    spread,
                    spread,
                    speed);
        }
    }

    private static List<LivingEntity> enemies(
            LivingEntity user, double radius) {
        return user.level().getEntitiesOfClass(
                LivingEntity.class,
                user.getBoundingBox().inflate(radius),
                target -> target != user
                        && target.isAlive()
                        && !user.isAlliedTo(target));
    }

    public static boolean canStartWallClimbing(
            LivingEntity user, ActionTarget target) {
        if (target == null
                || target.getType() != TargetType.BLOCK
                || target.getFace() == null
                || target.getFace().getAxis() == Direction.Axis.Y) {
            return false;
        }
        Vec3 grip = Vec3.atLowerCornerOf(
                target.getFace().getOpposite().getNormal()).scale(0.5D);
        Vec3 collided = HamonWallClimbingHelper.collide(
                user, user.getBoundingBox(), grip, true);
        return collided.subtract(grip).lengthSqr() >= 1.0E-7D;
    }

    // 1.16 Plant and Organism Infusion took the vanilla crosshair target
    // (InputHandler.pickMouseTarget): nothing under it was no target, with no
    // longer ray behind it. UltimateLifeformOrganismInfusion
    // .overrideVanillaMouseTarget (:159-172) then turned an aimed block into
    // an animal or ambient mob without a Hamon charge inside the block's
    // outline, and a block without an outline into no target.
    public static ActionTarget infusionTarget(
            LivingEntity user, UltimateLifeformSpec spec) {
        ActionTarget target = aimTarget(user);
        if (spec != UltimateLifeformSpec.HAMON_ORGANISM_INFUSION
                || target.getType() != TargetType.BLOCK) {
            return target;
        }
        BlockPos blockPos = target.getBlockPos();
        VoxelShape shape = user.level().getBlockState(blockPos)
                .getShape(user.level(), blockPos);
        if (shape.isEmpty()) {
            return ActionTarget.EMPTY;
        }
        return user.level().getEntities(user,
                        shape.bounds().move(blockPos),
                        entity -> (entity instanceof Animal
                                        || entity instanceof AmbientCreature)
                                && !EntityHamonChargeState.get(entity)
                                        .hasHamonCharge())
                .stream()
                .findFirst()
                .map(ActionTarget::new)
                .orElse(target);
    }

    // 1.16 Life Magnetism and Hypnotize took the vanilla crosshair target
    // (InputHandler.pickMouseTarget) with no longer ray behind it
    public static ActionTarget crosshairTarget(LivingEntity user) {
        return aimTarget(user);
    }

    // 1.16 read the client's mouse target on either side; the core's helper
    // gives the user's own client (the HUD, its check of a press) the aim it
    // sends, which its copy of the user never gets back, and the server the
    // aim that client sent
    private static ActionTarget aimTarget(LivingEntity user) {
        return LivingComponentAction.getAimTarget(user);
    }

    /*
     * 1.16 UltimateLifeformTameAnimal: TargetRequirement ENTITY (no entity
     * under the crosshair refused without a message), Action
     * .checkRangeAndTarget (8 blocks, a quarter of the reach without a line
     * of sight), then checkTarget (:69-84).
     */
    public static ConditionCheck checkHypnosisTarget(
            LivingEntity user, ActionTarget target) {
        if (target.getType() != TargetType.ENTITY
                || target.getMainEntity() == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!ActionTargetRange.isEntityWithinRange(user,
                target.getMainEntity(),
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!(target.getMainEntity() instanceof LivingEntity living)) {
            return ConditionCheck.createNegative("hypnosis");
        }
        return switch (HamonHypnosisState.canBeHypnotized(living, user)) {
            case CORRECT -> ConditionCheck.POSITIVE;
            case ALREADY_TAMED_BY_USER ->
                    ConditionCheck.createNegative("already_tamed");
            case INVALID -> ConditionCheck.createNegative("hypnosis");
        };
    }

    public static ActionTarget currentTarget(
            LivingEntity user, double range) {
        ActionTarget target = aimTarget(user);
        return target.getType() != TargetType.EMPTY
                ? target
                : ActionTarget.fromVanilla(rayTraceTarget(user, range));
    }

    /*
     * 1.16 PowerBaseImpl.checkTarget for UltimateLifeformAbsorbtion
     * (TargetRequirement.ENTITY): the entity under the crosshair, within 2
     * blocks (1 without a line of sight), and living unless the user is in
     * stone form.
     */
    @Nullable
    public static Entity absorptionTarget(
            LivingEntity user, UltimateLifeformData data) {
        Entity entity = absorptionAimEntity(user);
        if (entity == null
                || !ActionTargetRange.isEntityWithinRange(
                        user, entity, ABSORPTION_RANGE_SQ)
                || !(entity instanceof LivingEntity living
                                && living.isAlive()
                        || data.isStoneForm())) {
            return null;
        }
        return entity;
    }

    // the mouse target only (1.16 vanilla pick, no pick margin): a mob beside
    // an empty crosshair is no target
    @Nullable
    public static Entity absorptionAimEntity(LivingEntity user) {
        return entityTarget(user, crosshairTarget(user));
    }

    // only for the port's kept target_too_far refusal (owner Wave 25 Q2): an
    // entity farther along the look than the pick reaches
    @Nullable
    public static Entity absorptionLookEntity(LivingEntity user) {
        return entityTarget(user, currentTarget(user, 8.0D));
    }

    @Nullable
    private static Entity entityTarget(
            LivingEntity user, ActionTarget target) {
        Entity entity = target.getType() == TargetType.ENTITY
                ? target.getMainEntity()
                : null;
        return entity != user ? entity : null;
    }

    private static HitResult rayTraceTarget(
            LivingEntity user, double range) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getLookAngle().normalize().scale(range));
        BlockHitResult blockHit = user.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                user));
        Vec3 clipEnd = blockHit.getType() == HitResult.Type.MISS
                ? end
                : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                user.level(),
                user,
                start,
                clipEnd,
                user.getBoundingBox()
                        .expandTowards(clipEnd.subtract(start))
                        .inflate(1.0D),
                entity -> entity != user
                        && entity instanceof LivingEntity living
                        && living.isAlive());
        return entityHit != null ? entityHit : blockHit;
    }

    @Nullable
    public static LivingEntity refreshedCrosshairLivingTarget(
            LivingEntity user, double range) {
        HitResult target = rayTraceTarget(user, range);
        return target instanceof EntityHitResult entityHit
                        && entityHit.getEntity()
                                instanceof LivingEntity living
                ? living
                : null;
    }

    @Nullable
    private static LivingEntity aimed(
            LivingEntity user, double range) {
        return aimed(user, null, range);
    }

    @Nullable
    private static LivingEntity aimed(
            LivingEntity user,
            @Nullable UltimateLifeformData data,
            double range) {
        ActionTarget inputTarget = aimTarget(user);
        if (inputTarget.getType() == TargetType.ENTITY
                && inputTarget.getMainEntity()
                        instanceof LivingEntity living
                && living != user
                && living.isAlive()
                && !user.isAlliedTo(living)
                && living.distanceToSqr(user)
                        <= range * range) {
            return living;
        }
        if (data != null) {
            LivingEntity heldTarget = data.getHeldTarget(user, range);
            if (heldTarget != null) {
                return heldTarget;
            }
        }
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle().normalize();
        AABB search = user.getBoundingBox()
                .expandTowards(look.scale(range))
                .inflate(2.0D);
        return user.level()
                .getEntitiesOfClass(
                        LivingEntity.class,
                        search,
                        target -> target != user
                                && target.isAlive()
                                && !user.isAlliedTo(target))
                .stream()
                .filter(target -> {
                    Vec3 relative = target.getBoundingBox()
                            .getCenter()
                            .subtract(eye);
                    double forward = relative.dot(look);
                    if (forward < 0.0D || forward > range) {
                        return false;
                    }
                    double perpendicular =
                            relative.subtract(
                                            look.scale(forward))
                                    .length();
                    return perpendicular
                            <= Math.max(
                                    target.getBbWidth(),
                                    target.getBbHeight())
                                    * 0.75D
                                    + 0.8D;
                })
                .min(Comparator.comparingDouble(
                        target -> target.distanceToSqr(user)))
                .orElse(null);
    }

    private static void playAbilitySound(
            LivingEntity user,
            UltimateLifeformSpec spec) {
        if (spec == UltimateLifeformSpec.HEAVY_PUNCH
                || spec == UltimateLifeformSpec.HAMON_OVERDRIVE_BEAT) {
            return;
        }
        // 1.16 UltimateLifeformEnderTeleport.perform: Player.playSound on the
        // user's client, heard by the user alone (played by teleport)
        if (spec == UltimateLifeformSpec.ENDERMAN_TELEPORT) {
            return;
        }
        // 1.16 played these with playLocalSound on the user's client: only the
        // user heard them (MASTER, volume 1, pitch 1)
        String userSound = switch (spec) {
            case SCALE_SPLIT_SHOT -> "ultimate_lifeform_scale_shot";
            case ARMOR_PROTECTION -> "ultimate_lifeform_armor";
            case ARMOR_FIRE_PROTECTION -> "ultimate_lifeform_fire_armor";
            default -> null;
        };
        if (userSound != null) {
            playUserOnlySound(user,
                    UltimateLifeformAddon.SOUNDS.get(userSound).get());
            return;
        }
        // 1.16 had no start sound for the barrages (ultimate_hamon_barrage was
        // the kick's ULTIMATE_LIFEFORM_KICK), the Hamon Breath played its own
        // on the user's client and the Scale Shot one with each scale
        String sound = switch (spec) {
            case BUNNY_SUMMON,
                    PHANTOM_SUMMON ->
                    "ultimate_lifeform_summon";
            // Kars' lines are 1.16 shouts, said by UltimateLifeformAbility
            default -> null;
        };
        if (sound == null) {
            return;
        }
        // 1.16 summon perform: playLocalSound(SUMMON, MASTER, 1.0, 1.0) at the
        // user on every client that ran the fired hold, the user's included
        user.level().playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                UltimateLifeformAddon.SOUNDS.get(sound).get(),
                SoundSource.MASTER,
                1.0F,
                1.0F);
    }
}
