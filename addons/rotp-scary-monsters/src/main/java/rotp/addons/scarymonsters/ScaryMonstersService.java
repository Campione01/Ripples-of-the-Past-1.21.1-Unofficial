package rotp.addons.scarymonsters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.network.ScaryMonstersStatePayload;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import rotp.addons.scarymonsters.state.ScaryPossessionData;
import rotp.addons.scarymonsters.state.ScaryPossessionData.Possession;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ScaryMonstersService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FACET_VISION = "dynamic_vision";
    private static final String FACET_DRAGON = "dragon_form";
    private static final String FACET_DINOSAUR = "dinosaur_form";
    private static final String FACET_POSSESSION = "possession";
    private static final ResourceLocation DRAGON_HEALTH =
            ScaryMonstersAddon.id("dragon_health");
    private static final ResourceLocation DINOSAUR_HEALTH =
            ScaryMonstersAddon.id("dinosaur_health");
    private static final float DYNAMIC_VISION_STAMINA_PER_TICK = 5.0F;
    private static final float DYNAMIC_VISION_COUNTER_STAMINA = 50.0F;
    private static final Set<UUID> COUNTERED_THIS_TICK = new HashSet<>();
    private static long counterTick = Long.MIN_VALUE;
    // 1.16 TailSweepAction.KNOCKED_DOWN_TARGETS / WAS_SWIMMING (server thread only)
    private static final int KNOCKDOWN_TICKS = 100;
    private static final Map<UUID, Integer> KNOCKED_DOWN = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_SWIMMING = new HashMap<>();
    // Vanilla fall damage starts beyond three blocks.
    private static final double SAFE_FALL_BLOCKS = 3.0D;

    private ScaryMonstersService() {}

    public static ScaryMonstersStandEntity activeStand(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null && power.hasPower() && power.isSummoned()
                && power.getPowerType() == ScaryMonstersAddon.SCARY_MONSTERS.get()
                && power.getSummonedStandEntity() instanceof ScaryMonstersStandEntity stand
                && stand.isAlive() ? stand : null;
    }

    // 1.16 TailSweepAction.standPerform after its heavy punch: 3 damage to
    // every living thing in sight within 3 of the Stand, flung away and up and
    // knocked down, and the heavy impact sound at the Stand.
    public static void tailSweep(ServerPlayer player, StandEntity stand) {
        for (LivingEntity target : targets(player.serverLevel(), stand, player, 3.0D)) {
            target.hurt(DamageUtil.make(
                    stand.level(), ModDamageTypes.STAND_ATTACK, stand, player), 3.0F);
            Vec3 away = target.position().subtract(stand.position()).normalize();
            target.setDeltaMovement(away.x * 2.0D, 1.2D, away.z * 2.0D);
            target.hurtMarked = true;
            knockDown(target);
        }
        player.serverLevel().sendParticles(
                ParticleTypes.SWEEP_ATTACK,
                stand.getX(), stand.getY() + 0.5D, stand.getZ(),
                16, 1.5D, 0.5D, 1.5D, 0.1D);
        player.serverLevel().playSound(
                null, stand.getX(), stand.getY(), stand.getZ(),
                ModSoundEvents.STAND_PUNCH_HEAVY.get(), stand.getSoundSource(), 1.0F, 1.0F);
    }

    // 1.16 SlashAction / FinisherPunchAction.standPerform after their heavy
    // punch: 20 (15 for the finisher, reach 4) to every living thing in
    // sight within 3 of the Stand, with bleeding; the finisher flings them
    // up and dashes its user forward.
    public static void slash(ServerPlayer player, StandEntity stand, boolean finisher) {
        double range = finisher ? 4.0D : 3.0D;
        float damage = finisher ? 15.0F : 20.0F;
        for (LivingEntity target : targets(player.serverLevel(), stand, player, range)) {
            target.hurt(DamageUtil.make(
                    stand.level(), ModDamageTypes.STAND_ATTACK, stand, player), damage);
            target.addEffect(new MobEffectInstance(
                    ScaryMonstersAddon.BLEEDING, 100, 0, false, false, true));
            if (finisher) {
                Vec3 away = target.position().subtract(player.position()).normalize();
                target.setDeltaMovement(away.x * 1.5D, 1.5D, away.z * 1.5D);
                target.hurtMarked = true;
            }
            player.serverLevel().sendParticles(
                    ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 40, 1.25D, 1.0D, 1.25D, 0.15D);
        }
        if (finisher) {
            // 1.16 set the dash on the client; sent straight there so the
            // server's own travel step does not decay the first tick
            Vec3 look = player.getLookAngle();
            player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), new Vec3(look.x * 2.5D, 0.3D, look.z * 2.5D)));
        }
        else {
            player.serverLevel().playSound(
                    null, stand.blockPosition(), ScaryMonstersAddon.SLASH_SOUND.get(),
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * 1.16 TailSweepAction.applyKnockdown: the target lies down (swimming pose)
     * for 100 ticks. A player is also stopped where it is (so it is not flung),
     * dismounted, and slowed, fatigued, blinded and made nauseous; a mob is
     * slowed and weakened and keeps its fling.
     */
    public static void knockDown(LivingEntity target) {
        // One the sweep killed is not laid down (its entry would outlive it)
        if (!target.isAlive()) return;
        UUID uuid = target.getUUID();
        if (target instanceof Player player) {
            WAS_SWIMMING.put(uuid, player.isSwimming());
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 10, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 10, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
            player.setPose(Pose.SWIMMING);
            player.setSwimming(true);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            KNOCKED_DOWN.put(uuid, KNOCKDOWN_TICKS);
            if (player.isPassenger()) {
                player.stopRiding();
            }
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 2, false, false, true));
            target.setPose(Pose.SWIMMING);
            KNOCKED_DOWN.put(uuid, KNOCKDOWN_TICKS);
        }
    }

    /**
     * 1.16 TailSweepAction.tickKnockdowns. 1.16 KnockdownEvents ran it from
     * every living entity's tick and again from a player's, so a player counts
     * down twice a tick and gets up after 50 ticks, a mob after 100. While
     * down, a player keeps the pose and has its motion and inputs zeroed and
     * its flight cut; when it gets up its swimming state comes back and the
     * four effects go (where they are still the tail sweep's strength).
     */
    public static void tickKnockdowns(LivingEntity entity) {
        if (entity.level().isClientSide() || KNOCKED_DOWN.isEmpty()) return;
        UUID uuid = entity.getUUID();
        Integer remaining = KNOCKED_DOWN.get(uuid);
        if (remaining == null) return;
        // A dead or removed target's knockdown ends with it (a respawn or
        // relog keeps the UUID and must not come back down)
        if (!entity.isAlive()) {
            forgetKnockdown(uuid);
            return;
        }
        if (remaining > 0) {
            if (entity.getPose() != Pose.SWIMMING) {
                entity.setPose(Pose.SWIMMING);
            }
            if (entity instanceof Player player) {
                player.setSwimming(true);
                player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                player.xxa = 0.0F;
                player.zza = 0.0F;
                player.yya = 0.0F;
                if (player.getAbilities().flying) {
                    // 1.16 cut flight on the server only; tell the client too
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
            }
            KNOCKED_DOWN.put(uuid, remaining - 1);
        } else {
            entity.setPose(Pose.STANDING);
            if (entity instanceof Player player) {
                player.setSwimming(WAS_SWIMMING.getOrDefault(uuid, false));
                endKnockdownEffect(player, MobEffects.MOVEMENT_SLOWDOWN, 10);
                endKnockdownEffect(player, MobEffects.DIG_SLOWDOWN, 10);
                endKnockdownEffect(player, MobEffects.BLINDNESS, 0);
                endKnockdownEffect(player, MobEffects.CONFUSION, 0);
            }
            forgetKnockdown(uuid);
        }
    }

    // Drops a knockdown without getting up: death, destroying removal, logout.
    public static void forgetKnockdown(UUID uuid) {
        KNOCKED_DOWN.remove(uuid);
        WAS_SWIMMING.remove(uuid);
    }

    // A new world starts with nobody knocked down.
    public static void clearKnockdowns() {
        KNOCKED_DOWN.clear();
        WAS_SWIMMING.clear();
    }

    private static void endKnockdownEffect(
            Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getAmplifier() == amplifier) {
            player.removeEffect(effect);
        }
    }

    public static void enableDynamicVision(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (state.dynamicVision()) return;
        state.setDynamicVision(true);
        state.applyOwnedEffect(
                FACET_VISION,
                player,
                new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        Integer.MAX_VALUE,
                        5,
                        false,
                        false,
                        true));
        player.serverLevel().playSound(
                null, player.blockPosition(),
                ScaryMonstersAddon.DYNAMIC_VISION_SOUND.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        sync(player);
    }

    public static void disableDynamicVision(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (!state.dynamicVision() && !state.ownsFacet(FACET_VISION)) return;
        state.setDynamicVision(false);
        state.restoreOwnedEffect(
                FACET_VISION, player, MobEffects.MOVEMENT_SPEED);
        sync(player);
    }

    // 1.16 DynamicVisionEvents.onPlayerTick: the vision costs 5 stamina every tick and ends once less than that is left
    public static void tickDynamicVision(ServerPlayer player) {
        if (!ScaryMonstersAddon.state(player).dynamicVision()) return;
        StandPower power = StandPower.get(player);
        if (power == null) return;
        if (power.getStamina() < DYNAMIC_VISION_STAMINA_PER_TICK) {
            disableDynamicVision(player);
        } else {
            power.consumeStamina(DYNAMIC_VISION_STAMINA_PER_TICK);
        }
    }

    /**
     * 1.16 DynamicVisionEvents.onLivingHurt: while the vision is on, a melee hit on the user is cancelled and the Stand
     * strikes the attacker twice instead, if it is out, within 8 blocks of the attacker and 50 stamina is left. Each
     * attacker is countered at most once per tick. Returns whether the hit was countered (and must be cancelled).
     */
    public static boolean counterMeleeAttack(ServerPlayer player, DamageSource source) {
        if (!ScaryMonstersAddon.state(player).dynamicVision()) return false;
        // DamageUtil.getMeleeAttacker: an attacker that hit directly, not through a projectile
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
            return false;
        }
        long gameTime = player.level().getGameTime();
        if (counterTick != gameTime) {
            counterTick = gameTime;
            COUNTERED_THIS_TICK.clear();
        }
        if (COUNTERED_THIS_TICK.contains(attacker.getUUID())) return false;
        StandPower power = StandPower.get(player);
        if (power == null || power.getStamina() < DYNAMIC_VISION_COUNTER_STAMINA) return false;
        ScaryMonstersStandEntity stand = activeStand(player);
        if (stand == null || stand.distanceToSqr(attacker) > 64.0D) return false;
        power.consumeStamina(DYNAMIC_VISION_COUNTER_STAMINA);
        COUNTERED_THIS_TICK.add(attacker.getUUID());
        for (int hit = 0; hit < 2; hit++) {
            EntityActionInstance.standEntityAttack(
                    stand, attacker, stand.damageSources().mobAttack(stand), (float) stand.getAttackDamage());
        }
        Vec3 pos = attacker.position().add(0.0D, attacker.getBbHeight() * 0.5D, 0.0D);
        player.serverLevel().sendParticles(
                ParticleTypes.SWEEP_ATTACK, pos.x, pos.y, pos.z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
        player.serverLevel().sendParticles(
                ParticleTypes.CRIT, pos.x, pos.y, pos.z, 10, 0.3D, 0.3D, 0.3D, 0.3D);
        return true;
    }

    public static void enableDragonForm(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (state.dragonForm()) return;
        if (state.dinosaurForm()) disableDinosaurForm(player, true);
        state.setDragonForm(true);
        // 1.16 DragonAction: no particles on any of them; Dragon Form and Jump Boost also hide their icons
        player.addEffect(new MobEffectInstance(
                ScaryMonstersAddon.DRAGON_FORM, Integer.MAX_VALUE, 0, false, false, false));
        state.applyOwnedEffect(FACET_DRAGON, player,
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2, false, false, true));
        state.applyOwnedEffect(FACET_DRAGON, player,
                new MobEffectInstance(MobEffects.JUMP, Integer.MAX_VALUE, 2, false, false, false));
        state.applyOwnedEffect(FACET_DRAGON, player,
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, false, false, true));
        applyHealthModifier(player, DRAGON_HEALTH, 10.0D);
        // 1.16 DragonAction: other players within 8 get slowness, weakness and
        // mining fatigue 255 (field_76421_d, field_76437_t, field_76419_f) for
        // 60 ticks, without particles.
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(
                ServerPlayer.class,
                player.getBoundingBox().inflate(8.0D),
                entity -> entity != player && entity.isAlive())) {
            other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 255, false, false, true));
            other.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 60, 255, false, false, true));
        }
        player.serverLevel().playSound(
                null, player.blockPosition(), ScaryMonstersAddon.DRAGON_TRANSFORM.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        sync(player);
    }

    public static void disableDragonForm(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (!state.dragonForm() && !state.ownsFacet(FACET_DRAGON)) return;
        state.setDragonForm(false);
        player.removeEffect(ScaryMonstersAddon.DRAGON_FORM);
        state.restoreOwnedEffect(
                FACET_DRAGON, player, MobEffects.MOVEMENT_SPEED);
        state.restoreOwnedEffect(FACET_DRAGON, player, MobEffects.JUMP);
        state.restoreOwnedEffect(
                FACET_DRAGON, player, MobEffects.DAMAGE_RESISTANCE);
        removeHealthModifier(player, DRAGON_HEALTH);
        sync(player);
    }

    public static void enableDinosaurForm(ServerPlayer player) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (state.dinosaurForm()) return;
        if (state.dragonForm()) disableDragonForm(player);
        ScaryDinosaurEntity dinosaur = ScaryMonstersAddon.DINOSAUR.get()
                .create(player.serverLevel());
        if (dinosaur == null) return;
        dinosaur.moveTo(player.position());
        dinosaur.setOwnerUuid(player.getUUID());
        dinosaur.setPlayerControlled(true);
        // 1.16 DinoFormMorphHandler: the user's own dinosaur is drawn 1.7x the player's box
        dinosaur.setSourceSize(player.getBbWidth() * 1.7F, player.getBbHeight() * 1.7F);
        player.serverLevel().addFreshEntity(dinosaur);
        player.startRiding(dinosaur, true);
        state.applyOwnedEffect(FACET_DINOSAUR, player,
                new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE));
        // 1.16 DinosaurFormAction: the form, speed III, jump boost III and
        // resistance I (field_76429_m), all without particles.
        player.addEffect(new MobEffectInstance(
                ScaryMonstersAddon.DINOSAUR_FORM, Integer.MAX_VALUE, 0, false, false, true));
        state.applyOwnedEffect(FACET_DINOSAUR, player,
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 2, false, false, true));
        state.applyOwnedEffect(FACET_DINOSAUR, player,
                new MobEffectInstance(MobEffects.JUMP, Integer.MAX_VALUE, 2, false, false, true));
        state.applyOwnedEffect(FACET_DINOSAUR, player,
                new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
        applyHealthModifier(player, DINOSAUR_HEALTH, 30.0D);
        player.setHealth(player.getMaxHealth());
        state.setDinosaurForm(true);
        state.setControlledDinosaur(dinosaur.getUUID());
        // 1.16 DINOSAUR_FORM standSound plus DinosaurFormAction's world sound at the player
        ScaryMonstersStandEntity stand = activeStand(player);
        if (stand != null) {
            StandUtil.playStandEntitySound(stand, ScaryMonstersAddon.DINOSAURIFY_VOICE, 1.0F, 1.0F);
        }
        player.serverLevel().playSound(
                null, player.blockPosition(), ScaryMonstersAddon.DINOSAURIFY_VOICE.get(),
                player.getSoundSource(), 1.0F, 1.0F);
        sync(player);
    }

    public static void disableDinosaurForm(ServerPlayer player, boolean discard) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (!state.dinosaurForm()
                && state.controlledDinosaur() == null
                && !state.ownsFacet(FACET_DINOSAUR)) return;
        if (state.controlledDinosaur() != null) {
            Entity entity = player.serverLevel().getEntity(state.controlledDinosaur());
            if (entity instanceof ScaryDinosaurEntity dinosaur) {
                player.stopRiding();
                if (discard) dinosaur.discard();
                else dinosaur.setPlayerControlled(false);
            }
        }
        state.setControlledDinosaur(null);
        state.setDinosaurForm(false);
        player.removeEffect(ScaryMonstersAddon.DINOSAUR_FORM);
        state.restoreOwnedEffect(
                FACET_DINOSAUR, player, MobEffects.INVISIBILITY);
        state.restoreOwnedEffect(
                FACET_DINOSAUR, player, MobEffects.MOVEMENT_SPEED);
        state.restoreOwnedEffect(FACET_DINOSAUR, player, MobEffects.JUMP);
        state.restoreOwnedEffect(
                FACET_DINOSAUR, player, MobEffects.DAMAGE_RESISTANCE);
        // a form entered before R147 owns night vision instead
        state.restoreOwnedEffect(
                FACET_DINOSAUR, player, MobEffects.NIGHT_VISION);
        removeHealthModifier(player, DINOSAUR_HEALTH);
        sync(player);
    }

    public static void dinosaurifyNearest(ServerPlayer owner) {
        ScaryMonstersStandEntity stand = activeStand(owner);
        if (stand == null) return;
        // 1.16 DINOSAURIFY PERFORM standSound, played with or without a target
        StandUtil.playStandEntitySound(stand, ScaryMonstersAddon.DINOSAURIFY_VOICE, 1.0F, 1.0F);
        ScaryPossessionData recoveries = ScaryPossessionData.get(owner.serverLevel());
        // A player who is already somebody's dinosaur is no target: the next nearest one is taken instead.
        LivingEntity target = owner.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                stand.getBoundingBox().inflate(5.0D),
                entity -> entity != owner && entity != stand
                        && entity.isAlive()
                        && !(entity instanceof ScaryDinosaurEntity)
                        && !(entity instanceof rotp.core.powersystem.standpower.entity.StandEntity)
                        && !(entity instanceof ServerPlayer candidate && isPossessed(candidate, recoveries)))
                .stream()
                .min(Comparator.comparingDouble(stand::distanceToSqr))
                .orElse(null);
        if (target == null) return;
        ScaryDinosaurEntity dinosaur = ScaryMonstersAddon.DINOSAUR.get()
                .create(owner.serverLevel());
        if (dinosaur == null) return;
        dinosaur.moveTo(target.position());
        dinosaur.setOwnerUuid(owner.getUUID());
        // 1.16 DinosaurifyAction: the dinosaur keeps the size of what it was made from
        dinosaur.setSourceSize(target.getBbWidth(), target.getBbHeight());
        if (target instanceof ServerPlayer player) {
            ScaryMonstersState state = ScaryMonstersAddon.state(player);
            dinosaur.setPossessedPlayerUuid(player.getUUID());
            dinosaur.getPersistentData().putInt(
                    "PlayerGameMode", player.gameMode.getGameModeForPlayer().getId());
            dinosaur.getPersistentData().putBoolean("PossessionTracked", true);
            recoveries.begin(player.getUUID(), dinosaur.getUUID(), player.gameMode.getGameModeForPlayer(), false);
            state.setPossessedDinosaur(dinosaur.getUUID());
            if (!owner.serverLevel().addFreshEntity(dinosaur)) {
                recoveries.remove(player.getUUID(), dinosaur.getUUID());
                state.setPossessedDinosaur(null);
                return;
            }
            player.setGameMode(GameType.SPECTATOR);
            state.applyOwnedEffect(
                    FACET_POSSESSION,
                    player,
                    new MobEffectInstance(MobEffects.INVISIBILITY, 6000));
            player.startRiding(dinosaur, true);
        }
        else {
            CompoundTag source = new CompoundTag();
            target.save(source);
            dinosaur.setSourceEntityNbt(source);
            dinosaur.setCustomName(target.getCustomName());
            target.discard();
            owner.serverLevel().addFreshEntity(dinosaur);
        }
        owner.serverLevel().sendParticles(
                ParticleTypes.POOF,
                dinosaur.getX(), dinosaur.getY(), dinosaur.getZ(),
                30, 1.0D, dinosaur.getBbHeight(), 1.0D, 0.0D);
    }

    private static boolean isPossessed(ServerPlayer player, ScaryPossessionData recoveries) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        return state.possessedDinosaur() != null || state.ownsFacet(FACET_POSSESSION)
                || recoveries.get(player.getUUID()) != null;
    }

    /**
     * 1.16 DinosaurifyRevertAction / TargetHighlightHandler: the dinosaur nearest along the look vector, within 80
     * blocks and 3 blocks of that line. 1.16 dinosaur form put no dinosaur in the world, so the dinosaurs players
     * ride in that form here are no candidates. Runs on the server for the revert and on the client for its marker.
     */
    @Nullable
    public static ScaryDinosaurEntity lookedAtDinosaur(LivingEntity user) {
        Vec3 eye = user.getEyePosition();
        Vec3 look = user.getLookAngle().normalize();
        ScaryDinosaurEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (ScaryDinosaurEntity dinosaur : user.level().getEntitiesOfClass(
                ScaryDinosaurEntity.class,
                user.getBoundingBox().inflate(80.0D),
                entity -> entity.isAlive() && !entity.isPlayerControlled())) {
            Vec3 to = dinosaur.position().subtract(eye);
            double along = to.dot(look);
            if (along <= 0.0D || along > 80.0D || along > nearest) continue;
            if (to.subtract(look.scale(along)).lengthSqr() <= 9.0D) {
                nearest = along;
                best = dinosaur;
            }
        }
        return best;
    }

    public static void revertLookedAtDinosaur(ServerPlayer owner) {
        ScaryDinosaurEntity best = lookedAtDinosaur(owner);
        if (best == null) return;
        if (best.getPossessedPlayerUuid() != null) {
            ServerPlayer player = owner.server.getPlayerList()
                    .getPlayer(best.getPossessedPlayerUuid());
            if (player != null) {
                releasePossessedPlayer(player, best, false);
                best.discard();
            }
            return;
        }
        restoreCreatureDinosaur(best);
    }

    public static void restoreOwnedCreaturesOnDeath(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        List<ScaryDinosaurEntity> owned = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ScaryDinosaurEntity dinosaur
                    && dinosaur.isAlive()
                    && owner.getUUID().equals(dinosaur.getOwnerUuid())
                    && !dinosaur.isPlayerControlled()) {
                owned.add(dinosaur);
            }
        }
        for (ScaryDinosaurEntity dinosaur : owned) {
            UUID possessedPlayer = dinosaur.getPossessedPlayerUuid();
            if (possessedPlayer != null) {
                ServerPlayer player = owner.server.getPlayerList().getPlayer(possessedPlayer);
                if (player != null) {
                    releasePossessedPlayer(player, dinosaur, false);
                    dinosaur.discard();
                }
            }
            else {
                restoreCreatureDinosaur(dinosaur);
            }
        }
        releaseCamouflagedCaptivesOnDeath(owner);
    }

    // A possessed dinosaur inside its camouflage stone is treated as the live one above: its online captive is
    // released at it and it goes; while the captive is offline it is kept.
    private static void releaseCamouflagedCaptivesOnDeath(ServerPlayer owner) {
        ServerLevel level = owner.serverLevel();
        List<ItemEntity> stones = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity stone && stone.isAlive()) {
                CompoundTag snapshot = stone.getPersistentData().getCompound(ScaryDinosaurEntity.CAMOUFLAGE_TAG);
                if (snapshot.hasUUID("OwnerUUID") && owner.getUUID().equals(snapshot.getUUID("OwnerUUID"))
                        && snapshot.hasUUID("PossessedPlayerUUID") && snapshot.hasUUID("UUID")
                        && !snapshot.getBoolean("PlayerControlled")) {
                    stones.add(stone);
                }
            }
        }
        ScaryPossessionData recoveries = ScaryPossessionData.get(level);
        for (ItemEntity stone : stones) {
            CompoundTag snapshot = stone.getPersistentData().getCompound(ScaryDinosaurEntity.CAMOUFLAGE_TAG);
            ServerPlayer player = owner.server.getPlayerList().getPlayer(snapshot.getUUID("PossessedPlayerUUID"));
            if (player == null) continue;
            Possession possession = recoveries.get(player.getUUID());
            if (possession != null && !possession.terminal()
                    && possession.dinosaur().equals(snapshot.getUUID("UUID"))
                    && endPossession(player, possession, null) == Ended.CAPTIVE) {
                player.teleportTo(level, stone.getX(), stone.getY() + 1.0D, stone.getZ(),
                        player.getYRot(), player.getXRot());
                stone.getPersistentData().remove(ScaryDinosaurEntity.CAMOUFLAGE_TAG);
            }
            // A stone that still carries its snapshot here ends through onCamouflageStoneDestroyed.
            stone.discard();
        }
    }

    private static boolean restoreCreatureDinosaur(ScaryDinosaurEntity dinosaur) {
        if (!(dinosaur.level() instanceof ServerLevel level)
                || dinosaur.isRemoved()) {
            return false;
        }
        CompoundTag source = dinosaur.getSourceEntityNbt();
        if (source.isEmpty()) {
            return false;
        }
        try {
            Entity original = EntityType.loadEntityRecursive(
                    source, level, entity -> {
                        entity.moveTo(dinosaur.position());
                        return entity;
                    });
            if (original != null && level.addFreshEntity(original)) {
                dinosaur.discard();
                return true;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not restore dinosaur {}; keeping its source snapshot",
                    dinosaur.getUUID(), exception);
        }
        return false;
    }

    public static void releasePossessedPlayer(
            ServerPlayer player,
            ScaryDinosaurEntity dinosaur,
            boolean killPlayer) {
        if (!player.getUUID().equals(dinosaur.getPossessedPlayerUuid())) return;
        Possession possession = trackLoadedPossession(dinosaur);
        if (possession != null) finishPossession(player, possession, dinosaur, killPlayer);
    }

    public static Possession trackLoadedPossession(ScaryDinosaurEntity dinosaur) {
        UUID playerUuid = dinosaur.getPossessedPlayerUuid();
        if (playerUuid == null || !(dinosaur.level() instanceof ServerLevel level)) return null;
        ScaryPossessionData recoveries = ScaryPossessionData.get(level);
        Possession current = recoveries.get(playerUuid);
        if (current != null) {
            if (!current.dinosaur().equals(dinosaur.getUUID()) || current.terminal()) {
                clearDinosaurPossession(dinosaur);
                return null;
            }
            dinosaur.getPersistentData().putBoolean("PossessionTracked", true);
            return current;
        }
        if (dinosaur.getPersistentData().getBoolean("PossessionTracked")) {
            // A previously consumed record must not be recreated by an old entity snapshot.
            clearDinosaurPossession(dinosaur);
            return null;
        }
        if (!dinosaur.getPersistentData().contains("PlayerGameMode", Tag.TAG_INT)) return null;
        int originalMode = dinosaur.getPersistentData().getInt("PlayerGameMode");
        if (originalMode < 0 || originalMode > 3) return null;
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(playerUuid);
        if (online != null) {
            ScaryMonstersState state = ScaryMonstersAddon.state(online);
            if (state.possessedDinosaur() != null && !state.possessedDinosaur().equals(dinosaur.getUUID())
                    || state.possessedDinosaur() == null && !state.ownsFacet(FACET_POSSESSION)) {
                clearDinosaurPossession(dinosaur);
                return null;
            }
            state.setPossessedDinosaur(dinosaur.getUUID());
        }
        // Older saves kept the original mode only on the dinosaur. Import it once, without guessing Survival.
        recoveries.begin(playerUuid, dinosaur.getUUID(), GameType.byId(originalMode), true);
        dinosaur.getPersistentData().putBoolean("PossessionTracked", true);
        return recoveries.get(playerUuid);
    }

    public static void onPossessedDinosaurTerminal(ScaryDinosaurEntity dinosaur, boolean killPlayer) {
        // 1.16 turnIntoBlock: the dinosaur lives on in its stone and its player stays captive.
        if (dinosaur.isCamouflaging()) return;
        UUID playerUuid = dinosaur.getPossessedPlayerUuid();
        if (trackLoadedPossession(dinosaur) == null || !(dinosaur.level() instanceof ServerLevel level)) return;
        ScaryPossessionData recoveries = ScaryPossessionData.get(level);
        recoveries.markTerminal(playerUuid, dinosaur.getUUID(),
                level.dimension(), dinosaur.position().add(0.0D, 1.0D, 0.0D));
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) finishPossession(player, recoveries.get(playerUuid), dinosaur, killPlayer);
        clearDinosaurPossession(dinosaur);
    }

    /**
     * The safety net of the camouflage: a stone that is destroyed (anything but Remove Camouflage) takes its
     * dinosaur with it, so its captive is released alive, at once or on the next login.
     */
    public static void onCamouflageStoneDestroyed(ItemEntity stone) {
        if (!(stone.level() instanceof ServerLevel level)) return;
        CompoundTag snapshot = stone.getPersistentData().getCompound(ScaryDinosaurEntity.CAMOUFLAGE_TAG);
        if (!snapshot.hasUUID("PossessedPlayerUUID") || !snapshot.hasUUID("UUID")) return;
        UUID playerUuid = snapshot.getUUID("PossessedPlayerUUID");
        UUID dinosaurUuid = snapshot.getUUID("UUID");
        ScaryPossessionData recoveries = ScaryPossessionData.get(level);
        Possession possession = recoveries.get(playerUuid);
        if (possession == null || possession.terminal() || !possession.dinosaur().equals(dinosaurUuid)) return;
        recoveries.markTerminal(playerUuid, dinosaurUuid, level.dimension(), stone.position());
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerUuid);
        if (player != null) finishWithoutDinosaur(player, recoveries.get(playerUuid));
    }

    public static void recoverPossessionOnLogin(ServerPlayer player) {
        ScaryPossessionData recoveries = ScaryPossessionData.get(player.serverLevel());
        Possession possession = recoveries.get(player.getUUID());
        if (possession == null) return;
        if (possession.terminal()) {
            finishWithoutDinosaur(player, possession);
            return;
        }
        if (matchesPossession(player, possession)) return;
        // The record and the player's attachment are saved apart and have come apart (reset player data, a
        // rollback of one of them, an old snapshot). Without this the record would never be consumed.
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (player.isSpectator() || state.ownsFacet(FACET_POSSESSION)) {
            LOGGER.warn("Possession record of {} did not match the player's own data ({}); the record's dinosaur {}"
                    + " is taken as the captor", player.getGameProfile().getName(), state.possessedDinosaur(),
                    possession.dinosaur());
            state.setPossessedDinosaur(possession.dinosaur());
            return;
        }
        LOGGER.warn("Dropping the possession record of {} for dinosaur {}: the player is free already",
                player.getGameProfile().getName(), possession.dinosaur());
        recoveries.remove(player.getUUID(), possession.dinosaur());
        state.setPossessedDinosaur(null);
        for (ServerLevel level : player.server.getAllLevels()) {
            if (level.getEntity(possession.dinosaur()) instanceof ScaryDinosaurEntity dinosaur) {
                clearDinosaurPossession(dinosaur);
            }
        }
    }

    private static boolean matchesPossession(ServerPlayer player, Possession possession) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (state.possessedDinosaur() == null && possession.legacy() && state.ownsFacet(FACET_POSSESSION)) {
            state.setPossessedDinosaur(possession.dinosaur());
        }
        return possession.dinosaur().equals(state.possessedDinosaur());
    }

    private enum Ended {
        /** Nothing ended: a live record the player does not match. */
        NOT,
        /** The player was this dinosaur's captive. */
        CAPTIVE,
        /** A terminal record the player's own data did not match was consumed all the same. */
        RECORD_ONLY
    }

    private static Ended endPossession(ServerPlayer player, Possession possession,
            @Nullable ScaryDinosaurEntity dinosaur) {
        boolean matched = matchesPossession(player, possession);
        // A terminal record is the server's own word that the dinosaur is gone and holds the only copy of the
        // saved mode, so it is consumed even when the player's attachment no longer agrees.
        if (!matched && !possession.terminal()
                || !ScaryPossessionData.get(player.serverLevel()).remove(player.getUUID(), possession.dinosaur())) {
            return Ended.NOT;
        }
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        state.setPossessedDinosaur(null);
        if (dinosaur != null) clearDinosaurPossession(dinosaur);
        if (matched || player.isSpectator()) player.setGameMode(possession.originalMode());
        state.restoreOwnedEffect(
                FACET_POSSESSION, player, MobEffects.INVISIBILITY);
        if (!matched) {
            LOGGER.warn("Consumed the terminal possession record of {} for dinosaur {} although the player's own"
                    + " data did not match it", player.getGameProfile().getName(), possession.dinosaur());
        }
        return matched ? Ended.CAPTIVE : Ended.RECORD_ONLY;
    }

    private static void finishPossession(ServerPlayer player, Possession possession,
            ScaryDinosaurEntity dinosaur, boolean killPlayer) {
        Ended ended = endPossession(player, possession, dinosaur);
        if (ended == Ended.NOT) return;
        player.stopRiding();
        if (ended == Ended.CAPTIVE) {
            player.teleportTo((ServerLevel) dinosaur.level(), dinosaur.getX(), dinosaur.getY() + 1.0D,
                    dinosaur.getZ(), player.getYRot(), player.getXRot());
            if (killPlayer) player.kill();
        }
    }

    // The dinosaur is gone and cannot say where to stand: an offline captive on login, or a destroyed stone.
    private static void finishWithoutDinosaur(ServerPlayer player, Possession possession) {
        boolean wasSpectator = player.isSpectator();
        Ended ended = endPossession(player, possession, null);
        if (ended == Ended.NOT || ended == Ended.RECORD_ONLY && !wasSpectator) return;
        player.stopRiding();
        ServerLevel level = possession.releaseDimension() != null
                ? player.server.getLevel(possession.releaseDimension()) : null;
        Vec3 spot = possession.releaseSpot();
        if (level != null && spot != null && isSafeSpot(level, player, spot)) {
            player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
        }
        // A Spectator can have been left inside blocks or high in the air.
        else if (!isSafeSpot(player.serverLevel(), player, player.position())) {
            DimensionTransition respawn =
                    player.findRespawnPositionAndUseSpawnBlock(true, DimensionTransition.DO_NOTHING);
            player.teleportTo(respawn.newLevel(), respawn.pos().x, respawn.pos().y, respawn.pos().z,
                    respawn.yRot(), respawn.xRot());
        }
    }

    private static boolean isSafeSpot(ServerLevel level, ServerPlayer player, Vec3 spot) {
        if (spot.y < level.getMinBuildHeight() || spot.y >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(spot.x, spot.z)) {
            return false;
        }
        // An unloaded chunk would read as empty space.
        level.getChunkAt(BlockPos.containing(spot));
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(spot);
        AABB fall = body.expandTowards(0.0D, -SAFE_FALL_BLOCKS, 0.0D);
        return level.noCollision(body) && !level.noCollision(fall)
                && level.getBlockStates(fall).noneMatch(state ->
                        state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE));
    }

    private static void clearDinosaurPossession(ScaryDinosaurEntity dinosaur) {
        dinosaur.setPossessedPlayerUuid(null);
        dinosaur.getPersistentData().remove("PlayerGameMode");
    }

    public static void dinosaursToBlocks(ServerPlayer owner) {
        for (ScaryDinosaurEntity dinosaur : owner.serverLevel().getEntitiesOfClass(
                ScaryDinosaurEntity.class,
                owner.getBoundingBox().inflate(1000.0D),
                entity -> entity.isAlive() && !entity.isPlayerControlled())) {
            // turnIntoBlock has discarded the dinosaur: a stone the level refuses (a cancelled join) is a destroyed one.
            ItemEntity stone = dinosaur.turnIntoBlock();
            if (stone != null && !owner.serverLevel().addFreshEntity(stone)) onCamouflageStoneDestroyed(stone);
        }
    }

    public static void blocksToDinosaurs(ServerPlayer owner) {
        for (ItemEntity item : owner.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                owner.getBoundingBox().inflate(1000.0D),
                entity -> entity.isAlive()
                        && entity.getPersistentData().contains(ScaryDinosaurEntity.CAMOUFLAGE_TAG))) {
            ScaryDinosaurEntity.revertFromBlock(item, owner.serverLevel());
        }
    }

    public static void cleanupAll(ServerPlayer player) {
        cleanupAll(player, false);
    }

    public static void cleanupOnLogout(ServerPlayer player) {
        // Logout is not reversion: the forced target stays bound to its dinosaur.
        cleanupAll(player, true);
    }

    private static void cleanupAll(ServerPlayer player, boolean preservePossession) {
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        if (state.dinosaurForm() || state.ownsFacet(FACET_DINOSAUR)) {
            disableDinosaurForm(player, true);
        }
        if (state.dragonForm() || state.ownsFacet(FACET_DRAGON)) {
            disableDragonForm(player);
        }
        if (state.dynamicVision() || state.ownsFacet(FACET_VISION)) {
            disableDynamicVision(player);
        }
        if (!preservePossession && state.ownsFacet(FACET_POSSESSION)) {
            state.restoreOwnedEffect(
                    FACET_POSSESSION, player, MobEffects.INVISIBILITY);
        }
        state.clearAllExceptFacet(preservePossession ? FACET_POSSESSION : null);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, ScaryMonstersStatePayload.from(player));
    }

    // 1.16: every living thing but the user within range of the Stand
    // that the Stand can see.
    private static List<LivingEntity> targets(
            ServerLevel level,
            StandEntity stand,
            ServerPlayer player,
            double range) {
        return level.getEntitiesOfClass(
                LivingEntity.class,
                stand.getBoundingBox().inflate(range),
                entity -> entity != player && entity != stand && entity.isAlive()
                        && stand.hasLineOfSight(entity));
    }

    private static void applyHealthModifier(
            LivingEntity entity, ResourceLocation id, double amount) {
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        health.removeModifier(id);
        health.addOrUpdateTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeHealthModifier(
            LivingEntity entity, ResourceLocation id) {
        AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(id);
            entity.setHealth(Math.min(entity.getHealth(), entity.getMaxHealth()));
        }
    }
}
