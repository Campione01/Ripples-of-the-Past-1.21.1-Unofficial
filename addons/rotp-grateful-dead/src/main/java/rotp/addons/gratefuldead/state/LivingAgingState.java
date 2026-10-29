package rotp.addons.gratefuldead.state;

import java.util.Optional;
import java.util.UUID;

import rotp.core.init.ModEntityAttributes;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.service.AgingService;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class LivingAgingState
        implements INBTSerializable<CompoundTag> {
    public static final int DEFAULT_FADE_TICKS = 40;
    public static final int COLD_FADE_TICKS = 200;
    private static final ResourceLocation MOVEMENT_MODIFIER =
            GratefulDeadAddon.id("aging_movement");
    private static final ResourceLocation FLYING_MODIFIER =
            GratefulDeadAddon.id("aging_flying");
    private static final ResourceLocation ATTACK_DAMAGE_MODIFIER =
            GratefulDeadAddon.id("aging_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            GratefulDeadAddon.id("aging_attack_speed");
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER =
            GratefulDeadAddon.id("aging_follow_range");
    private static final ResourceLocation JUMP_MODIFIER =
            GratefulDeadAddon.id("aging_jump");
    private static final ResourceLocation MINING_MODIFIER =
            GratefulDeadAddon.id("aging_mining");
    private static final ResourceLocation STAND_STRENGTH_MODIFIER =
            GratefulDeadAddon.id("aging_stand_strength");
    private static final ResourceLocation STAND_SPEED_MODIFIER =
            GratefulDeadAddon.id("aging_stand_speed");
    private static final ResourceLocation STAND_DURABILITY_MODIFIER =
            GratefulDeadAddon.id("aging_stand_durability");
    private static final ResourceLocation STAND_PRECISION_MODIFIER =
            GratefulDeadAddon.id("aging_stand_precision");

    public static final StreamCodec<
            RegistryFriendlyByteBuf, LivingAgingState>
            STREAM_CODEC = new StreamCodec<>() {
                @Override
                public LivingAgingState decode(
                        RegistryFriendlyByteBuf buffer) {
                    LivingAgingState state =
                            new LivingAgingState();
                    state.progress = buffer.readFloat();
                    state.owner = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    state.ownerGeneration =
                            buffer.readVarLong();
                    state.fading = buffer.readBoolean();
                    state.fadeTicks = buffer.readVarInt();
                    state.fadePerTick = buffer.readFloat();
                    state.woolDropped = buffer.readBoolean();
                    state.progress = Mth.clamp(
                            state.progress, 0.0F, 1.0F);
                    state.ownerGeneration = Math.max(
                            0L, state.ownerGeneration);
                    state.fadeTicks = Mth.clamp(
                            state.fadeTicks,
                            1,
                            COLD_FADE_TICKS);
                    state.fadePerTick = Mth.clamp(
                            state.fadePerTick, 0.0F, 1.0F);
                    if (state.fading
                            && state.fadePerTick <= 0.0F) {
                        state.fadePerTick =
                                state.progress
                                        / state.fadeTicks;
                    }
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        LivingAgingState state) {
                    buffer.writeFloat(state.progress);
                    buffer.writeBoolean(state.owner != null);
                    if (state.owner != null) {
                        buffer.writeUUID(state.owner);
                    }
                    buffer.writeVarLong(
                            state.ownerGeneration);
                    buffer.writeBoolean(state.fading);
                    buffer.writeVarInt(state.fadeTicks);
                    buffer.writeFloat(state.fadePerTick);
                    buffer.writeBoolean(state.woolDropped);
                }
            };

    private float progress;
    private UUID owner;
    private long ownerGeneration;
    private boolean fading;
    private int fadeTicks = DEFAULT_FADE_TICKS;
    private float fadePerTick;
    private boolean woolDropped;
    private float lastSyncedProgress = -1.0F;

    public static LivingAgingState get(
            LivingEntity entity) {
        return entity.getData(
                GratefulDeadAddon.LIVING_AGING);
    }

    public float progress() {
        return progress;
    }

    public Optional<UUID> owner() {
        return Optional.ofNullable(owner);
    }

    public boolean fading() {
        return fading;
    }

    public boolean active() {
        return progress > 0.0F;
    }

    public void age(
            LivingEntity target,
            UUID newOwner,
            long generation,
            float amount) {
        if (target.level().isClientSide()
                || amount <= 0.0F) {
            return;
        }
        progress = Mth.clamp(
                progress + amount, 0.0F, 1.0F);
        owner = newOwner;
        ownerGeneration = Math.max(0L, generation);
        fading = false;
        fadeTicks = DEFAULT_FADE_TICKS;
        fadePerTick = 0.0F;
        applyModifiers(target);
        applyThresholdSemantics(target);
        sync(target, true);
    }

    public void startFading(
            LivingEntity target, int duration) {
        if (progress <= 0.0F) {
            clear(target);
            return;
        }
        beginFade(duration);
        sync(target, true);
    }

    public void clearImmediately(LivingEntity target) {
        if (target != null && target.level() instanceof ServerLevel) {
            clear(target);
        }
    }

    public void tickServer(
            ServerLevel level, LivingEntity target) {
        if (progress <= 0.0F) {
            removeModifiers(target);
            return;
        }
        if (owner != null
                && AgingRemovalData.get(
                                level.getServer())
                        .generation(owner)
                        > ownerGeneration) {
            beginFade(DEFAULT_FADE_TICKS);
        }
        if (AgingService.isCold(level, target.blockPosition())) {
            beginFade(COLD_FADE_TICKS);
        }
        if (owner != null) {
            net.minecraft.server.level.ServerPlayer ownerPlayer =
                    level.getServer()
                            .getPlayerList()
                            .getPlayer(owner);
            if (ownerPlayer == null
                    || !AgingService.hasGratefulDead(
                            ownerPlayer)
                    || ownerPlayer.level() != level
                    || ownerPlayer.distanceToSqr(
                                    target)
                            > AgingService.AURA_RADIUS
                                    * AgingService
                                            .AURA_RADIUS) {
                beginFade(DEFAULT_FADE_TICKS);
            }
        }
        if (fading) {
            progress = Math.max(
                    0.0F, progress - fadePerTick);
            fadeTicks--;
            if (progress <= 0.0F || fadeTicks <= 0) {
                clear(target);
                return;
            }
        }
        applyModifiers(target);
        applyThresholdSemantics(target);
        if (target.tickCount % 10 == 0) {
            sync(target, false);
        }
    }

    private void beginFade(int duration) {
        if (fading || progress <= 0.0F) {
            return;
        }
        fading = true;
        fadeTicks = Math.max(1, duration);
        fadePerTick = progress / fadeTicks;
    }

    private void clear(LivingEntity target) {
        progress = 0.0F;
        owner = null;
        ownerGeneration = 0L;
        fading = false;
        fadeTicks = DEFAULT_FADE_TICKS;
        fadePerTick = 0.0F;
        woolDropped = false;
        removeModifiers(target);
        sync(target, true);
    }

    private void applyModifiers(LivingEntity target) {
        updateModifier(
                target,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER,
                -0.55D * progress);
        updateModifier(
                target,
                Attributes.FLYING_SPEED,
                FLYING_MODIFIER,
                -0.60D * progress);
        updateModifier(
                target,
                Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_MODIFIER,
                -0.60D * progress);
        updateModifier(
                target,
                Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER,
                -0.60D * progress);
        updateModifier(
                target,
                Attributes.FOLLOW_RANGE,
                FOLLOW_RANGE_MODIFIER,
                -0.70D * progress);
        updateModifier(
                target,
                Attributes.JUMP_STRENGTH,
                JUMP_MODIFIER,
                -0.45D * progress);
        updateModifier(
                target,
                Attributes.BLOCK_BREAK_SPEED,
                MINING_MODIFIER,
                -0.60D * progress);
        updateModifier(
                target,
                ModEntityAttributes.STAND_STRENGTH,
                STAND_STRENGTH_MODIFIER,
                -0.85D * progress);
        updateModifier(
                target,
                ModEntityAttributes.STAND_SPEED,
                STAND_SPEED_MODIFIER,
                -0.90D * progress);
        updateModifier(
                target,
                ModEntityAttributes.STAND_DURABILITY,
                STAND_DURABILITY_MODIFIER,
                -0.88D * progress);
        updateModifier(
                target,
                ModEntityAttributes.STAND_PRECISION,
                STAND_PRECISION_MODIFIER,
                -0.88D * progress);
    }

    private void applyThresholdSemantics(
            LivingEntity target) {
        if (progress >= 0.60F
                && !woolDropped
                && target instanceof Sheep sheep
                && sheep.readyForShearing()) {
            sheep.shear(SoundSource.NEUTRAL);
            woolDropped = true;
        }
    }

    private static void updateModifier(
            LivingEntity target,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double amount) {
        AttributeInstance instance =
                target.getAttribute(attribute);
        if (instance != null) {
            instance.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id,
                            amount,
                            AttributeModifier.Operation
                                    .ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeModifiers(
            LivingEntity target) {
        remove(
                target,
                Attributes.MOVEMENT_SPEED,
                MOVEMENT_MODIFIER);
        remove(
                target,
                Attributes.FLYING_SPEED,
                FLYING_MODIFIER);
        remove(
                target,
                Attributes.ATTACK_DAMAGE,
                ATTACK_DAMAGE_MODIFIER);
        remove(
                target,
                Attributes.ATTACK_SPEED,
                ATTACK_SPEED_MODIFIER);
        remove(
                target,
                Attributes.FOLLOW_RANGE,
                FOLLOW_RANGE_MODIFIER);
        remove(
                target,
                Attributes.JUMP_STRENGTH,
                JUMP_MODIFIER);
        remove(
                target,
                Attributes.BLOCK_BREAK_SPEED,
                MINING_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_STRENGTH,
                STAND_STRENGTH_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_SPEED,
                STAND_SPEED_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_DURABILITY,
                STAND_DURABILITY_MODIFIER);
        remove(
                target,
                ModEntityAttributes.STAND_PRECISION,
                STAND_PRECISION_MODIFIER);
    }

    private static void remove(
            LivingEntity target,
            Holder<Attribute> attribute,
            ResourceLocation id) {
        AttributeInstance instance =
                target.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    private void sync(
            LivingEntity target, boolean force) {
        if (!target.level().isClientSide()
                && (force
                        || Math.abs(
                                        progress
                                                - lastSyncedProgress)
                                >= 0.01F)) {
            lastSyncedProgress = progress;
            target.syncData(
                    GratefulDeadAddon.LIVING_AGING);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Progress", progress);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putLong(
                "OwnerGeneration", ownerGeneration);
        tag.putBoolean("Fading", fading);
        tag.putInt("FadeTicks", fadeTicks);
        tag.putFloat("FadePerTick", fadePerTick);
        tag.putBoolean("WoolDropped", woolDropped);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        progress = Mth.clamp(
                tag.getFloat("Progress"), 0.0F, 1.0F);
        owner = tag.hasUUID("Owner")
                ? tag.getUUID("Owner")
                : null;
        ownerGeneration = Math.max(
                0L, tag.getLong("OwnerGeneration"));
        fading = tag.getBoolean("Fading");
        fadeTicks = Mth.clamp(
                tag.contains("FadeTicks")
                        ? tag.getInt("FadeTicks")
                        : DEFAULT_FADE_TICKS,
                1,
                COLD_FADE_TICKS);
        fadePerTick = Mth.clamp(
                tag.getFloat("FadePerTick"),
                0.0F,
                1.0F);
        if (fading && fadePerTick <= 0.0F) {
            fadePerTick = progress / fadeTicks;
        }
        woolDropped = tag.getBoolean("WoolDropped");
    }
}
