package rotp.addons.greenday.state;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.greenday.GreenDayAddon;
import rotp.addons.greenday.GreenDayDamageTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.tags.EntityTypeTags;
import net.neoforged.neoforge.common.util.INBTSerializable;

public final class GreenDayMoldState
        implements INBTSerializable<CompoundTag> {
    public static final int SOURCE_TIMEOUT_TICKS = 40;
    public static final int MOLD_DURATION_TICKS = 20;
    public static final int RECOVERY_RESET_TICKS = 100;
    public static final int MAX_TRACKED_SOURCES = 64;

    public static final StreamCodec<
            RegistryFriendlyByteBuf, GreenDayMoldState> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public GreenDayMoldState decode(
                        RegistryFriendlyByteBuf buffer) {
                    GreenDayMoldState state =
                            new GreenDayMoldState();
                    state.moldActive = buffer.readBoolean();
                    state.maxY = buffer.readVarInt();
                    state.ticksInMold = buffer.readVarInt();
                    state.ticksWithoutMold = buffer.readVarInt();
                    state.deathMoldTailTicks = Mth.clamp(
                            buffer.readVarInt(),
                            0,
                            MOLD_DURATION_TICKS);
                    int count = Mth.clamp(
                            buffer.readVarInt(),
                            0,
                            MAX_TRACKED_SOURCES);
                    for (int index = 0; index < count; index++) {
                        state.sources.put(
                                buffer.readUUID(),
                                buffer.readVarLong());
                    }
                    if (!state.sources.isEmpty()) {
                        state.deathMoldTailTicks = 0;
                    }
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        GreenDayMoldState state) {
                    buffer.writeBoolean(state.moldActive);
                    buffer.writeVarInt(state.maxY);
                    buffer.writeVarInt(state.ticksInMold);
                    buffer.writeVarInt(state.ticksWithoutMold);
                    buffer.writeVarInt(state.deathMoldTailTicks);
                    buffer.writeVarInt(state.sources.size());
                    state.sources.forEach((source, lastSeen) -> {
                        buffer.writeUUID(source);
                        buffer.writeVarLong(lastSeen);
                    });
                }
            };

    private boolean moldActive;
    private int maxY;
    private int ticksInMold;
    private int ticksWithoutMold;
    private int deathMoldTailTicks;
    private final Map<UUID, Long> sources =
            new LinkedHashMap<>();

    public static GreenDayMoldState get(LivingEntity entity) {
        return entity.getData(GreenDayAddon.MOLD_STATE);
    }

    public boolean moldActive() {
        return moldActive;
    }

    public int maxY() {
        return maxY;
    }

    public int ticksInMold() {
        return ticksInMold;
    }

    public int ticksWithoutMold() {
        return ticksWithoutMold;
    }

    public int sourceCount() {
        return sources.size();
    }

    public Set<UUID> sourceIds() {
        return Set.copyOf(sources.keySet());
    }

    public void setMoldActive(
            LivingEntity owner, boolean active) {
        if (owner.level().isClientSide()
                || moldActive == active) {
            return;
        }
        moldActive = active;
        sync(owner);
    }

    public void markExposed(
            LivingEntity target,
            UUID source,
            long gameTime) {
        if (target.level().isClientSide()
                || isImmune(target)) {
            return;
        }
        boolean newSource = !sources.containsKey(source);
        if (newSource
                && sources.size() >= MAX_TRACKED_SOURCES) {
            return;
        }
        if (sources.isEmpty()) {
            maxY = target.blockPosition().getY();
            ticksWithoutMold = 0;
        }
        deathMoldTailTicks = 0;
        sources.put(source, gameTime);
        target.addEffect(new MobEffectInstance(
                GreenDayAddon.MOLD_UTIL,
                SOURCE_TIMEOUT_TICKS,
                0,
                false,
                false,
                true));
        if (newSource) {
            sync(target);
        }
    }

    public void tickServer(
            ServerLevel level, LivingEntity target) {
        if (isImmune(target)) {
            if (!sources.isEmpty()
                    || deathMoldTailTicks > 0
                    || target.hasEffect(
                            GreenDayAddon.MOLD_UTIL)
                    || target.hasEffect(GreenDayAddon.MOLD)) {
                clearExposure(target);
            }
            return;
        }

        boolean changed = pruneSources(level.getGameTime());
        if (!sources.isEmpty()) {
            int currentY = target.blockPosition().getY();
            if (currentY > maxY) {
                maxY = currentY;
                changed = true;
            }
            int descent = maxY - currentY;
            if (descent > 0) {
                target.addEffect(new MobEffectInstance(
                        GreenDayAddon.MOLD,
                        MOLD_DURATION_TICKS,
                        Mth.clamp(descent, 0, 255),
                        false,
                        false,
                        true));
                ticksWithoutMold = 0;
            }
            else if (!target.hasEffect(GreenDayAddon.MOLD)) {
                ticksWithoutMold++;
                if (ticksWithoutMold > RECOVERY_RESET_TICKS
                        && ticksInMold != 0) {
                    ticksInMold = 0;
                    changed = true;
                }
            }
        }
        else {
            if (target.hasEffect(GreenDayAddon.MOLD_UTIL)) {
                target.removeEffect(GreenDayAddon.MOLD_UTIL);
                changed = true;
            }
            if (hasDeathMoldTail(target)) {
                ticksWithoutMold = 0;
            }
            else {
                if (deathMoldTailTicks != 0) {
                    deathMoldTailTicks = 0;
                    changed = true;
                }
                if (target.hasEffect(GreenDayAddon.MOLD)) {
                    target.removeEffect(GreenDayAddon.MOLD);
                    changed = true;
                }
                maxY = target.blockPosition().getY();
                ticksWithoutMold++;
                if (ticksWithoutMold > RECOVERY_RESET_TICKS
                        && ticksInMold != 0) {
                    ticksInMold = 0;
                    changed = true;
                }
            }
        }

        if (changed) {
            sync(target);
        }
    }

    public void recordMoldTick(
            ServerLevel level,
            LivingEntity target,
            int amplifier) {
        boolean deathTail = sources.isEmpty();
        if (deathTail) {
            if (!hasDeathMoldTail(target)) {
                return;
            }
            // An external refresh cannot renew this effect-tick budget.
            deathMoldTailTicks--;
        }
        if (isImmune(target)
                || isTimeStopped(level, target)) {
            return;
        }

        ticksInMold++;
        ticksWithoutMold = 0;
        // 1.16 MoldEffect: Weakness from level 2, Nausea from level 5.
        if (amplifier > 1) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS,
                    MOLD_DURATION_TICKS,
                    amplifier - 1,
                    false,
                    false,
                    false));
        }
        if (amplifier > 4) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.CONFUSION,
                    MOLD_DURATION_TICKS,
                    amplifier - 4,
                    false,
                    false,
                    false));
        }

        int ticksToDie = amplifier == 0
                ? Integer.MAX_VALUE
                : Math.max(1, 200 * (10 - amplifier) / 3);
        if (ticksInMold >= ticksToDie) {
            target.hurt(
                    DamageUtil.make(
                            level,
                            GreenDayDamageTypes.MOLD),
                    target.getMaxHealth());
        }
        if (ticksInMold % 20 == 0
                || ticksInMold == ticksToDie
                || (deathTail && deathMoldTailTicks == 0)) {
            sync(target);
        }
    }

    public boolean removeSource(
            LivingEntity target, UUID source) {
        return removeSource(target, source, false);
    }

    private boolean removeSource(
            LivingEntity target,
            UUID source,
            boolean preserveDeathTail) {
        if (sources.remove(source) == null) {
            return false;
        }
        if (sources.isEmpty()) {
            target.removeEffect(GreenDayAddon.MOLD_UTIL);
            deathMoldTailTicks = preserveDeathTail
                    && target.isAlive()
                    && !isImmune(target)
                    ? finiteMoldDuration(target)
                    : 0;
            if (deathMoldTailTicks == 0) {
                target.removeEffect(GreenDayAddon.MOLD);
            }
            maxY = target.blockPosition().getY();
            ticksWithoutMold = 0;
        }
        sync(target);
        return true;
    }

    public void clearExposure(LivingEntity target) {
        sources.clear();
        deathMoldTailTicks = 0;
        maxY = target.blockPosition().getY();
        ticksInMold = 0;
        ticksWithoutMold = 0;
        target.removeEffect(GreenDayAddon.MOLD_UTIL);
        target.removeEffect(GreenDayAddon.MOLD);
        sync(target);
    }

    public void clearAll(LivingEntity entity) {
        moldActive = false;
        clearExposure(entity);
    }

    public static void removeSourceFromLevel(
            ServerLevel level, LivingEntity source) {
        removeSourceFromLevel(level, source, false);
    }

    public static void removeDeadSourceFromLevel(
            ServerLevel level, LivingEntity source) {
        removeSourceFromLevel(level, source, true);
    }

    private static void removeSourceFromLevel(
            ServerLevel level,
            LivingEntity source,
            boolean preserveDeathTail) {
        UUID sourceId = source.getUUID();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living
                    && living.hasData(GreenDayAddon.MOLD_STATE)) {
                get(living).removeSource(
                        living, sourceId, preserveDeathTail);
            }
        }
    }

    private boolean hasDeathMoldTail(LivingEntity target) {
        return deathMoldTailTicks > 0
                && finiteMoldDuration(target) > 0;
    }

    private static int finiteMoldDuration(LivingEntity target) {
        MobEffectInstance mold = target.getEffect(GreenDayAddon.MOLD);
        if (mold == null
                || mold.isInfiniteDuration()
                || mold.getDuration() <= 0
                || mold.getDuration() > MOLD_DURATION_TICKS) {
            return 0;
        }
        return mold.getDuration();
    }

    public static boolean isImmune(LivingEntity entity) {
        return entity.getType().is(EntityTypeTags.UNDEAD)
                || PlayerPower.getPowerData(
                                entity,
                                ModPlayerPowers.ZOMBIE)
                        .isPresent();
    }

    private boolean pruneSources(long gameTime) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Long>> iterator =
                sources.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> source = iterator.next();
            if (gameTime - source.getValue()
                    > SOURCE_TIMEOUT_TICKS) {
                iterator.remove();
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isTimeStopped(
            ServerLevel level, LivingEntity entity) {
        TimeStopState state = level.getData(
                ModDataAttachmentTypes.TIME_STOP.get());
        return state.isTimeStopped(entity);
    }

    private static void sync(LivingEntity entity) {
        if (!entity.level().isClientSide()) {
            entity.syncData(GreenDayAddon.MOLD_STATE);
        }
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("MoldActive", moldActive);
        tag.putInt("MaxY", maxY);
        tag.putInt("TicksInMold", ticksInMold);
        tag.putInt("TicksWithoutMold", ticksWithoutMold);
        tag.putInt("DeathMoldTailTicks", deathMoldTailTicks);
        ListTag sourceList = new ListTag();
        sources.forEach((source, lastSeen) -> {
            CompoundTag sourceTag = new CompoundTag();
            sourceTag.putUUID("Id", source);
            sourceTag.putLong("LastSeen", lastSeen);
            sourceList.add(sourceTag);
        });
        tag.put("Sources", sourceList);
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        moldActive = tag.getBoolean("MoldActive");
        maxY = tag.getInt("MaxY");
        ticksInMold = Math.max(
                0, tag.getInt("TicksInMold"));
        ticksWithoutMold = Math.max(
                0, tag.getInt("TicksWithoutMold"));
        deathMoldTailTicks = Mth.clamp(
                tag.getInt("DeathMoldTailTicks"),
                0,
                MOLD_DURATION_TICKS);
        sources.clear();
        ListTag sourceList =
                tag.getList("Sources", Tag.TAG_COMPOUND);
        int count = Math.min(
                sourceList.size(), MAX_TRACKED_SOURCES);
        for (int index = 0; index < count; index++) {
            CompoundTag sourceTag =
                    sourceList.getCompound(index);
            if (sourceTag.hasUUID("Id")) {
                sources.put(
                        sourceTag.getUUID("Id"),
                        sourceTag.getLong("LastSeen"));
            }
        }
        if (!sources.isEmpty()) {
            deathMoldTailTicks = 0;
        }
    }
}
