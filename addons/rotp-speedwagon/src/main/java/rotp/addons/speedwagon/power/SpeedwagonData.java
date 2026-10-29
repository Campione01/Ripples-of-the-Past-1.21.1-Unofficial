package rotp.addons.speedwagon.power;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerData;
import rotp.core.powersystem.PowerType;
import rotp.core.powersystem.playerpower.PlayerPowerData;

import rotp.addons.speedwagon.SpeedwagonAddon;
import rotp.addons.speedwagon.entity.AbstractHenchmanEntity;
import rotp.addons.speedwagon.entity.Henchman2Entity;
import rotp.addons.speedwagon.entity.HenchmanEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

public final class SpeedwagonData extends PlayerPowerData {
    public static final float MAX_ENERGY = 1000.0F;
    public static final float ENERGY_REGEN_PER_TICK = 10.0F;
    public static final double LEARNER_RANGE_SQR = 64.0D;

    private float energy;
    private final Map<String, Integer> abilityCooldowns =
            new HashMap<>();
    private final Map<String, Integer> abilityCooldownTotals =
            new HashMap<>();
    private final Set<UUID> pendingLearners = new HashSet<>();
    private final Set<UUID> summonedHenchmen = new HashSet<>();

    public SpeedwagonData() {
        super(SpeedwagonAddon.SPEEDWAGON.get());
    }

    @Override
    public void onPowerGiven(
            Power<?> userPower,
            @Nullable PowerType oldType,
            @Nullable PowerData oldData) {
        LivingEntity user = userPower.getUser();
        if (!user.level().isClientSide()) {
            setEnergy(MAX_ENERGY);
        }
    }

    @Override
    public void onPowerCleared(
            Power<?> userPower, @Nullable PowerType newType) {
        LivingEntity user = userPower.getUser();
        if (!user.level().isClientSide()) {
            cleanupSummons(user);
            pendingLearners.clear();
        }
    }

    @Override
    public void tick(Power<?> userPower) {
        super.tick(userPower);
        LivingEntity user = userPower.getUser();
        tickCooldowns();

        float previousEnergy = energy;
        if (user.getAirSupply() >= user.getMaxAirSupply()) {
            setEnergy(energy + ENERGY_REGEN_PER_TICK);
        }

        if (!user.level().isClientSide()) {
            tickPendingLearners(user);
            if (previousEnergy != energy
                    && user.tickCount % 10 == 0) {
                syncOnUpdate(user);
            }
        }
    }

    public float getEnergy() {
        return energy;
    }

    public float getEnergyRatio() {
        return MAX_ENERGY > 0.0F
                ? energy / MAX_ENERGY
                : 0.0F;
    }

    public boolean hasEnergy(float amount) {
        return amount <= 0.0F || energy >= amount;
    }

    // /jojoenergy hooks (1.16 getMaxEnergy); no @Override so older cores still load this.
    public float getCommandMaxEnergy(LivingEntity user) {
        return MAX_ENERGY;
    }

    public float getCommandEnergy() {
        return energy;
    }

    public void setCommandEnergy(
            LivingEntity user, float amount) {
        setEnergy(amount);
        syncOnUpdate(user);
    }

    public boolean consumeEnergy(
            LivingEntity user, float amount) {
        if (amount <= 0.0F
                || user instanceof Player player
                        && player.getAbilities().instabuild) {
            return true;
        }
        if (!hasEnergy(amount)) {
            return false;
        }
        setEnergy(energy - amount);
        return true;
    }

    public int getAbilityCooldown(String abilityName) {
        return abilityCooldowns.getOrDefault(
                abilityName, 0);
    }

    public boolean isAbilityOnCooldown(String abilityName) {
        return getAbilityCooldown(abilityName) > 0;
    }

    public void setAbilityCooldown(
            String abilityName, int cooldown) {
        int ticks = Math.max(cooldown, 0);
        if (ticks > 0) {
            abilityCooldowns.put(abilityName, ticks);
            abilityCooldownTotals.put(abilityName, ticks);
        }
        else {
            abilityCooldowns.remove(abilityName);
            abilityCooldownTotals.remove(abilityName);
        }
    }

    @Override
    public float getAbilityCooldownRatio(
            String abilityName, float partialTick) {
        int cooldown = getAbilityCooldown(abilityName);
        int total = abilityCooldownTotals.getOrDefault(
                abilityName, cooldown);
        if (cooldown <= 0 || total <= 0) {
            return 0.0F;
        }
        return Mth.clamp(
                (cooldown - partialTick) / total,
                0.0F,
                1.0F);
    }

    public boolean addPendingLearner(
            LivingEntity teacher, ServerPlayer learner) {
        boolean added = pendingLearners.add(
                learner.getUUID());
        if (added && teacher instanceof Player teacherPlayer) {
            teacherPlayer.displayClientMessage(
                    Component.translatable(
                            "rotp_sp.chat.message.new_sp_learner",
                            learner.getDisplayName()),
                    true);
        }
        if (added) {
            syncOnUpdate(teacher);
        }
        return added;
    }

    public boolean hasPendingLearner(UUID learner) {
        return pendingLearners.contains(learner);
    }

    public void removePendingLearner(
            LivingEntity teacher, UUID learner) {
        if (pendingLearners.remove(learner)) {
            syncOnUpdate(teacher);
        }
    }

    public void trackSummon(
            LivingEntity owner, AbstractHenchmanEntity henchman) {
        if (summonedHenchmen.add(henchman.getUUID())) {
            syncOnUpdate(owner);
        }
    }

    public void untrackSummon(
            LivingEntity owner, UUID entityId) {
        if (summonedHenchmen.remove(entityId)) {
            syncOnUpdate(owner);
        }
    }

    public boolean hasNearbyHenchmen(LivingEntity owner) {
        AABB area = owner.getBoundingBox().inflate(16.0D);
        boolean henchmanPresent = !owner.level()
                .getEntitiesOfClass(
                        HenchmanEntity.class,
                        area,
                        entity -> owns(owner, entity))
                .isEmpty();
        boolean henchman2Present = !owner.level()
                .getEntitiesOfClass(
                        Henchman2Entity.class,
                        area,
                        entity -> owns(owner, entity))
                .isEmpty();
        return henchmanPresent || henchman2Present;
    }

    public void cleanupForDeath(LivingEntity owner) {
        cleanupSummons(owner);
        pendingLearners.clear();
    }

    public void cleanupSummons(LivingEntity owner) {
        MinecraftServer server = owner.level().getServer();
        if (server != null) {
            for (UUID entityId : Set.copyOf(summonedHenchmen)) {
                Entity entity = findEntity(server, entityId);
                if (entity instanceof AbstractHenchmanEntity henchman
                        && henchman.isSummonedFromAbility()) {
                    henchman.discard();
                }
            }
        }
        summonedHenchmen.clear();
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = super.serializeNBT(provider);
        tag.putFloat("SpeedwagonEnergy", energy);
        writeCooldowns(tag);
        tag.put("PendingLearners", writeUuids(pendingLearners));
        tag.put("SummonedHenchmen", writeUuids(summonedHenchmen));
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider, CompoundTag tag) {
        super.deserializeNBT(provider, tag);
        setEnergy(tag.getFloat("SpeedwagonEnergy"));
        readCooldowns(tag);
        readUuids(tag.get("PendingLearners"), pendingLearners);
        readUuids(tag.get("SummonedHenchmen"), summonedHenchmen);
    }

    @Override
    public void toBuf(
            FriendlyByteBuf buf, boolean isSentToTracking) {
        super.toBuf(buf, isSentToTracking);
        buf.writeFloat(energy);
        buf.writeVarInt(abilityCooldowns.size());
        for (Map.Entry<String, Integer> entry
                : abilityCooldowns.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
            buf.writeVarInt(
                    abilityCooldownTotals.getOrDefault(
                            entry.getKey(), entry.getValue()));
        }
        writeUuids(buf, pendingLearners);
        writeUuids(buf, summonedHenchmen);
    }

    @Override
    public void fromBuf(
            FriendlyByteBuf buf, boolean isSentToTracking) {
        super.fromBuf(buf, isSentToTracking);
        setEnergy(buf.readFloat());
        abilityCooldowns.clear();
        abilityCooldownTotals.clear();
        int cooldownCount = buf.readVarInt();
        for (int i = 0; i < cooldownCount; i++) {
            String abilityName = buf.readUtf();
            int cooldown = buf.readVarInt();
            int total = buf.readVarInt();
            if (cooldown > 0) {
                abilityCooldowns.put(abilityName, cooldown);
                abilityCooldownTotals.put(
                        abilityName, Math.max(total, cooldown));
            }
        }
        readUuids(buf, pendingLearners);
        readUuids(buf, summonedHenchmen);
    }

    private void setEnergy(float amount) {
        energy = Float.isFinite(amount)
                ? Mth.clamp(amount, 0.0F, MAX_ENERGY)
                : 0.0F;
    }

    private void tickCooldowns() {
        Iterator<Map.Entry<String, Integer>> iterator =
                abilityCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int ticks = entry.getValue() - 1;
            if (ticks > 0) {
                entry.setValue(ticks);
            }
            else {
                abilityCooldownTotals.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private void tickPendingLearners(LivingEntity teacher) {
        MinecraftServer server = teacher.level().getServer();
        if (server == null || pendingLearners.isEmpty()) {
            return;
        }
        boolean changed = pendingLearners.removeIf(id -> {
            ServerPlayer learner =
                    server.getPlayerList().getPlayer(id);
            return learner == null
                    || !learner.isAlive()
                    || learner.level() != teacher.level()
                    || teacher.distanceToSqr(learner)
                            > LEARNER_RANGE_SQR;
        });
        if (changed) {
            syncOnUpdate(teacher);
        }
    }

    private void writeCooldowns(CompoundTag tag) {
        if (abilityCooldowns.isEmpty()) {
            return;
        }
        CompoundTag cooldowns = new CompoundTag();
        abilityCooldowns.forEach((name, cooldown) ->
                cooldowns.putIntArray(
                        name,
                        new int[] {
                                cooldown,
                                abilityCooldownTotals.getOrDefault(
                                        name, cooldown)
                        }));
        tag.put("SpeedwagonAbilityCooldowns", cooldowns);
    }

    private void readCooldowns(CompoundTag tag) {
        abilityCooldowns.clear();
        abilityCooldownTotals.clear();
        if (!(tag.get("SpeedwagonAbilityCooldowns")
                instanceof CompoundTag cooldowns)) {
            return;
        }
        for (String name : cooldowns.getAllKeys()) {
            int[] values = cooldowns.getIntArray(name);
            int cooldown = values.length > 0 ? values[0] : 0;
            int total = values.length > 1
                    ? values[1]
                    : cooldown;
            if (cooldown > 0) {
                abilityCooldowns.put(name, cooldown);
                abilityCooldownTotals.put(
                        name, Math.max(total, cooldown));
            }
        }
    }

    private static ListTag writeUuids(Set<UUID> values) {
        ListTag list = new ListTag();
        values.stream()
                .map(UUID::toString)
                .sorted()
                .map(StringTag::valueOf)
                .forEach(list::add);
        return list;
    }

    private static void readUuids(
            @Nullable Tag tag, Set<UUID> destination) {
        destination.clear();
        if (!(tag instanceof ListTag list)
                || list.getElementType() != Tag.TAG_STRING) {
            return;
        }
        for (Tag value : list) {
            try {
                destination.add(
                        UUID.fromString(value.getAsString()));
            }
            catch (IllegalArgumentException ignored) {
                // Invalid legacy or edited UUID entries are skipped.
            }
        }
    }

    private static void writeUuids(
            FriendlyByteBuf buf, Set<UUID> values) {
        buf.writeVarInt(values.size());
        values.stream()
                .sorted()
                .forEach(buf::writeUUID);
    }

    private static void readUuids(
            FriendlyByteBuf buf, Set<UUID> destination) {
        destination.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            destination.add(buf.readUUID());
        }
    }

    private static boolean owns(
            LivingEntity owner,
            AbstractHenchmanEntity henchman) {
        return owner.getUUID().equals(henchman.getOwnerUUID());
    }

    @Nullable
    private static Entity findEntity(
            MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
