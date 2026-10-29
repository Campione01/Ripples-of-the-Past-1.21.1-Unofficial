package rotp.addons.sexpistols.content.sexpistols;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nullable;

import net.minecraft.advancements.AdvancementHolder;
import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.revolver.state.RevolverMemberAttachments;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsEntities;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

import rotp.core.powersystem.standpower.StandPower;

public final class SexPistolsCoordinator
        implements INBTSerializable<CompoundTag> {
    public static final int MEMBER_COUNT =
            SexPistolsIds.SEX_PISTOLS_NUMBERS.length;
    public static final int RECALL_TICKS = 12;
    public static final int MAX_TRACKED_SPLITTING_KILLS = 4;

    public static final StreamCodec<
            RegistryFriendlyByteBuf, SexPistolsCoordinator> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public SexPistolsCoordinator decode(
                        RegistryFriendlyByteBuf buffer) {
                    SexPistolsCoordinator state =
                            new SexPistolsCoordinator();
                    state.selectedNumber = buffer.readVarInt();
                    state.targetMode = SexPistolsTargetMode.byId(
                            buffer.readVarInt());
                    state.transferOrder = SexPistolsTransferOrder.byId(
                            buffer.readVarInt());
                    state.summoned = buffer.readBoolean();
                    state.recallTicks = buffer.readVarInt();
                    state.loadedMemberMask =
                            buffer.readVarInt() & 0x3F;
                    state.inFlightMemberMask =
                            buffer.readVarInt()
                                    & state.loadedMemberMask;
                    state.encirclementReturnMask =
                            buffer.readVarInt() & 0x3F;
                    state.trajectoryVision =
                            buffer.readBoolean();
                    state.hungerTicks =
                            Math.max(0, buffer.readVarInt());
                    state.hungry = buffer.readBoolean();
                    state.hungerEnabled = buffer.readBoolean();
                    state.splittingVolleyId = buffer.readBoolean()
                            ? buffer.readUUID()
                            : null;
                    state.splittingActiveFragments =
                            buffer.readVarInt();
                    state.splittingVolleyAwarded =
                            buffer.readBoolean();
                    int splitKillCount = buffer.readVarInt();
                    if (splitKillCount < 0
                            || splitKillCount
                                    > MAX_TRACKED_SPLITTING_KILLS) {
                        throw new IllegalArgumentException(
                                "Invalid splitting kill tracker size: "
                                        + splitKillCount);
                    }
                    for (int i = 0; i < splitKillCount; i++) {
                        state.splittingKilledTargets.add(
                                buffer.readUUID());
                    }
                    state.skin = buffer.readBoolean()
                            ? Optional.of(
                                    ResourceLocation.STREAM_CODEC.decode(
                                            buffer))
                            : Optional.empty();
                    for (int i = 0; i < MEMBER_COUNT; i++) {
                        state.childUuids[i] = buffer.readBoolean()
                                ? buffer.readUUID()
                                : null;
                        state.childEntityIds[i] = buffer.readVarInt();
                        state.reviveTicks[i] = buffer.readVarInt();
                    }
                    return state;
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        SexPistolsCoordinator state) {
                    buffer.writeVarInt(state.selectedNumber);
                    buffer.writeVarInt(state.targetMode.ordinal());
                    buffer.writeVarInt(state.transferOrder.ordinal());
                    buffer.writeBoolean(state.summoned);
                    buffer.writeVarInt(state.recallTicks);
                    buffer.writeVarInt(state.loadedMemberMask);
                    buffer.writeVarInt(state.inFlightMemberMask);
                    buffer.writeVarInt(
                            state.encirclementReturnMask);
                    buffer.writeBoolean(state.trajectoryVision);
                    buffer.writeVarInt(state.hungerTicks);
                    buffer.writeBoolean(state.hungry);
                    buffer.writeBoolean(state.hungerEnabled);
                    buffer.writeBoolean(
                            state.splittingVolleyId != null);
                    if (state.splittingVolleyId != null) {
                        buffer.writeUUID(state.splittingVolleyId);
                    }
                    buffer.writeVarInt(
                            state.splittingActiveFragments);
                    buffer.writeBoolean(
                            state.splittingVolleyAwarded);
                    buffer.writeVarInt(
                            state.splittingKilledTargets.size());
                    for (UUID killed :
                            state.splittingKilledTargets) {
                        buffer.writeUUID(killed);
                    }
                    buffer.writeBoolean(state.skin.isPresent());
                    state.skin.ifPresent(value ->
                            ResourceLocation.STREAM_CODEC.encode(
                                    buffer, value));
                    for (int i = 0; i < MEMBER_COUNT; i++) {
                        UUID childUuid = state.childUuids[i];
                        buffer.writeBoolean(childUuid != null);
                        if (childUuid != null) {
                            buffer.writeUUID(childUuid);
                        }
                        buffer.writeVarInt(state.childEntityIds[i]);
                        buffer.writeVarInt(state.reviveTicks[i]);
                    }
                }
            };

    private int selectedNumber = SexPistolsIds.SEX_PISTOLS_NUMBERS[0];
    private SexPistolsTargetMode targetMode =
            SexPistolsTargetMode.PLAYERS;
    private SexPistolsTransferOrder transferOrder =
            SexPistolsTransferOrder.NONE;
    private final UUID[] childUuids = new UUID[MEMBER_COUNT];
    private final int[] childEntityIds = new int[MEMBER_COUNT];
    private final int[] reviveTicks = new int[MEMBER_COUNT];
    private boolean summoned;
    // 1.16 kept the swarm in a SexPistolsEntities effect that the first
    // summon created and the user's death or a Stand change removed; with
    // the Stand put away, members could only be loaded while it existed.
    private boolean swarmEstablished;
    // 1.16 releaseLoadedPistol marked a put-away swarm summoned with only
    // the released member recreated; the rest wait for the next summon.
    private boolean releasedMembersOnly;
    // A bullet discarded during a recall or cleanup hands its members back;
    // that must not bring the put-away swarm out again.
    private boolean tearingDown;
    private boolean foodBeggingSummon;
    private int recallTicks;
    private int loadedMemberMask;
    private int inFlightMemberMask;
    private int encirclementReturnMask;
    private boolean trajectoryVision;
    private int hungerTicks = randomHungerTicks();
    private boolean hungry;
    private boolean hungerEnabled =
            SexPistolsConfig.DEFAULT_SEX_PISTOLS_HUNGER_ENABLED;
    @Nullable
    private UUID splittingVolleyId;
    private int splittingActiveFragments;
    private boolean splittingVolleyAwarded;
    private final Set<UUID> splittingKilledTargets =
            new LinkedHashSet<>();
    private Optional<ResourceLocation> skin = Optional.empty();

    public SexPistolsCoordinator() {
        Arrays.fill(childEntityIds, -1);
    }

    public int selectedNumber() {
        return selectedNumber;
    }

    public SexPistolsTargetMode targetMode() {
        return targetMode;
    }

    public SexPistolsTransferOrder transferOrder() {
        return transferOrder;
    }

    public boolean isSummoned() {
        return summoned;
    }

    public boolean isSwarmEstablished() {
        return swarmEstablished || summoned;
    }

    public int loadedMemberMask() {
        return loadedMemberMask;
    }

    public int inFlightMemberMask() {
        return inFlightMemberMask;
    }

    public boolean isMemberLoaded(int slot) {
        return slot >= 0
                && slot < MEMBER_COUNT
                && (loadedMemberMask & 1 << slot) != 0;
    }

    public boolean trajectoryVision() {
        return trajectoryVision;
    }

    public int hungerTicks() {
        return hungerTicks;
    }

    public boolean isHungry() {
        return hungerEnabled && hungry;
    }

    public void debugSetHungry(
            LivingEntity owner, boolean hungry) {
        this.hungry = hungry;
        hungerTicks = hungry ? 0 : randomHungerTicks();
        sync(owner);
    }

    @Nullable
    public UUID beginSplittingVolley(
            LivingEntity owner, int fragmentCount) {
        if (!(owner instanceof ServerPlayer)
                || fragmentCount <= 0) {
            return null;
        }
        splittingVolleyId = UUID.randomUUID();
        splittingActiveFragments = fragmentCount;
        splittingVolleyAwarded = false;
        splittingKilledTargets.clear();
        sync(owner);
        return splittingVolleyId;
    }

    public void recordSplittingFragmentKill(
            LivingEntity owner,
            @Nullable UUID volleyId,
            LivingEntity target) {
        if (volleyId == null
                || target == null
                || !volleyId.equals(splittingVolleyId)
                || splittingVolleyAwarded
                || splittingKilledTargets.size()
                        >= MAX_TRACKED_SPLITTING_KILLS
                || !splittingKilledTargets.add(
                        target.getUUID())) {
            return;
        }
        if (splittingKilledTargets.size() >= 4
                && owner instanceof ServerPlayer player) {
            AdvancementHolder advancement =
                    player.server.getAdvancements().get(
                            SexPistolsIds.id("four_attack"));
            if (advancement != null) {
                player.getAdvancements().award(
                        advancement, "four_attack");
            }
            splittingVolleyAwarded = true;
        }
        sync(owner);
    }

    public void releaseSplittingFragment(
            LivingEntity owner, @Nullable UUID volleyId) {
        if (volleyId == null
                || !volleyId.equals(splittingVolleyId)) {
            return;
        }
        splittingActiveFragments = Math.max(
                0, splittingActiveFragments - 1);
        if (splittingActiveFragments == 0
                || splittingVolleyAwarded) {
            clearSplittingVolley();
        }
        sync(owner);
    }

    public Optional<ResourceLocation> skin() {
        return skin;
    }

    public UUID childUuid(int number) {
        int slot = SexPistolsEntities.slotForNumber(number);
        return slot >= 0 ? childUuids[slot] : null;
    }

    public int childEntityId(int number) {
        int slot = SexPistolsEntities.slotForNumber(number);
        return slot >= 0 ? childEntityIds[slot] : -1;
    }

    public int reviveTicks(int number) {
        int slot = SexPistolsEntities.slotForNumber(number);
        return slot >= 0 ? reviveTicks[slot] : 0;
    }

    public int trackedMemberCount() {
        int count = 0;
        for (UUID childUuid : childUuids) {
            if (childUuid != null) {
                count++;
            }
        }
        return count;
    }

    public boolean owns(int number, UUID childUuid) {
        int slot = SexPistolsEntities.slotForNumber(number);
        return slot >= 0
                && childUuid != null
                && childUuid.equals(childUuids[slot]);
    }

    public SexPistolsEntity selectedMember(ServerLevel level) {
        return member(level, selectedNumber);
    }

    public List<SexPistolsEntity> loadedMembers(
            ServerLevel level) {
        List<SexPistolsEntity> loaded =
                new ArrayList<>(MEMBER_COUNT);
        forEachLoadedMember(level, loaded::add);
        return List.copyOf(loaded);
    }

    public boolean cycleSelected(
            ServerLevel level,
            LivingEntity owner,
            boolean backwards) {
        int currentSlot =
                SexPistolsEntities.slotForNumber(selectedNumber);
        int step = backwards ? -1 : 1;
        for (int offset = 1;
                offset <= MEMBER_COUNT;
                offset++) {
            int slot = Math.floorMod(
                    currentSlot + step * offset,
                    MEMBER_COUNT);
            int number =
                    SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
            SexPistolsEntity member = member(level, number);
            if (reviveTicks[slot] <= 0
                    && member != null
                    && member.isAlive()) {
                selectedNumber = number;
                sync(owner);
                return true;
            }
        }
        return false;
    }

    public void cycleTargetMode(
            LivingEntity owner,
            boolean backwards) {
        targetMode = targetMode.cycle(backwards);
        sync(owner);
    }

    public void cycleTransferOrder(
            LivingEntity owner,
            boolean backwards) {
        transferOrder = transferOrder.cycle(backwards);
        sync(owner);
    }

    public void toggleTrajectoryVision(LivingEntity owner) {
        trajectoryVision = !trajectoryVision;
        sync(owner);
    }

    public void activate(
            ServerLevel level,
            LivingEntity owner,
            StandPower standPower) {
        summoned = true;
        swarmEstablished = true;
        releasedMembersOnly = false;
        recallTicks = 0;
        updateSkin(standPower.getSelectedSkin());
        ensureClosure(level, owner);
        // 1.16 SexPistolsStandType spread a summoned swarm at random; the
        // food-begging summon used the pistols' fixed places.
        double baseAngle = owner.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            SexPistolsEntity member = member(
                    level, SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
            if (member != null) {
                member.setIdleOffset(foodBeggingSummon
                        ? SexPistolsEntity.slotIdleOffset(slot)
                        : SexPistolsEntity.summonIdleOffset(
                                slot, baseAngle, owner.getRandom()));
            }
        }
        sync(owner);
    }

    public void beginRecall(ServerLevel level, LivingEntity owner) {
        if (!summoned && recallTicks <= 0) {
            return;
        }
        tearingDown = true;
        try {
            RevolverBulletEntity.discardOwnedBy(
                    level, owner.getUUID());
        }
        finally {
            tearingDown = false;
        }
        summoned = false;
        releasedMembersOnly = false;
        recallTicks = RECALL_TICKS;
        forEachLoadedMember(level, member ->
                member.setMemberState(
                        SexPistolsMemberState.RECALLING));
        sync(owner);
    }

    public void cleanup(ServerLevel level, LivingEntity owner) {
        tearingDown = true;
        try {
            RevolverBulletEntity.discardOwnedBy(
                    level, owner.getUUID());
        }
        finally {
            tearingDown = false;
        }
        discardChildren(level);
        loadedMemberMask &= ~inFlightMemberMask;
        inFlightMemberMask = 0;
        summoned = false;
        releasedMembersOnly = false;
        recallTicks = 0;
        sync(owner);
    }

    // Stand removed or replaced (stand remove, stand give ... true): 1.16
    // dropped the SexPistolsEntities effect, so the next swarm is fresh.
    public void resetForStandCleared(
            ServerLevel level, LivingEntity owner) {
        tearingDown = true;
        try {
            RevolverBulletEntity.discardOwnedBy(
                    level, owner.getUUID());
        }
        finally {
            tearingDown = false;
        }
        releasedMembersOnly = false;
        discardChildren(level);
        clearItemAttachments(owner);
        loadedMemberMask = 0;
        inFlightMemberMask = 0;
        encirclementReturnMask = 0;
        Arrays.fill(reviveTicks, 0);
        hungry = false;
        hungerTicks = randomHungerTicks();
        targetMode = SexPistolsTargetMode.PLAYERS;
        transferOrder = SexPistolsTransferOrder.NONE;
        selectedNumber = SexPistolsIds.SEX_PISTOLS_NUMBERS[0];
        trajectoryVision = false;
        clearSplittingVolley();
        summoned = false;
        swarmEstablished = false;
        recallTicks = 0;
        sync(owner);
    }

    public void forgetSwarmOnOwnerDeath() {
        swarmEstablished = false;
    }

    public void onMemberUnloaded(
            LivingEntity owner,
            int number,
            UUID childUuid) {
        int slot = SexPistolsEntities.slotForNumber(number);
        if (slot < 0
                || childUuid == null
                || !childUuid.equals(childUuids[slot])) {
            return;
        }
        childUuids[slot] = null;
        childEntityIds[slot] = -1;
        sync(owner);
    }

    public void syncForTracking(LivingEntity owner) {
        sync(owner);
    }

    public void tickServer(
            ServerLevel level,
            LivingEntity owner) {
        boolean syncNeeded = tickReviveTimers();
        boolean configuredHungerEnabled =
                SexPistolsConfig.hungerEnabled();
        if (hungerEnabled != configuredHungerEnabled) {
            hungerEnabled = configuredHungerEnabled;
            syncNeeded = true;
        }
        if ((owner.tickCount & 7) == 0) {
            syncNeeded |= reconcileItemCarriers(owner);
        }
        StandPower standPower = StandPower.get(owner);
        boolean ownsStand = standPower != null
                && standPower.hasPower()
                && standPower.getPowerType()
                        == SexPistolsStands.SEX_PISTOLS.get();

        if (!ownsStand || !owner.isAlive()) {
            if (trackedMemberCount() > 0 || summoned
                    || recallTicks > 0
                    || loadedMemberMask != 0) {
                discardChildren(level);
                clearItemAttachments(owner);
                loadedMemberMask = 0;
                inFlightMemberMask = 0;
                encirclementReturnMask = 0;
                summoned = false;
                releasedMembersOnly = false;
                recallTicks = 0;
                syncNeeded = true;
            }
        }
        else if (tickHunger()) {
            syncNeeded = true;
        }

        if (ownsStand
                && isHungry()
                && !summoned
                && !standPower.isSummoned()
                && isHoldingFood(owner)) {
            foodBeggingSummon = true;
            boolean summonedForFood;
            try {
                summonedForFood = SexPistolsStands.SEX_PISTOLS.get()
                        .summon(owner, standPower);
            }
            finally {
                foodBeggingSummon = false;
            }
            if (summonedForFood) {
                return;
            }
        }

        if (!ownsStand || !owner.isAlive()) {
            if (syncNeeded) {
                sync(owner);
            }
            return;
        }
        if (summoned) {
            if (!standPower.isSummoned()) {
                SexPistolsStands.SEX_PISTOLS.get()
                        .restoreAfterLoad(owner, standPower);
            }
            Optional<ResourceLocation> selectedSkin =
                    standPower.getSelectedSkin();
            if (!Objects.equals(skin, selectedSkin)) {
                updateSkin(selectedSkin);
                syncNeeded = true;
            }
            syncNeeded |= ensureClosure(level, owner);
            syncNeeded |= applyEncirclementReturns(
                    level, owner);
            if (releasedMembersOnly
                    && trackedMemberCount() == 0
                    && inFlightMemberMask == 0) {
                // 1.16 had no Stand manifestation left once the last
                // released member was gone or loaded again.
                summoned = false;
                releasedMembersOnly = false;
                SexPistolsStands.SEX_PISTOLS.get()
                        .putAwayQuietly(owner, standPower);
                syncNeeded = true;
            }
        }
        else if (recallTicks > 0) {
            recallTicks--;
            if (recallTicks <= 0
                    || allLoadedMembersCloseTo(level, owner, 0.65D)) {
                discardChildren(level);
            }
            syncNeeded = true;
        }
        else if (standPower.isSummoned()) {
            activate(level, owner, standPower);
            return;
        }

        if (syncNeeded) {
            sync(owner);
        }
    }

    public boolean feedSwarm(
            ServerLevel level,
            LivingEntity owner,
            Player feeder,
            ItemStack food) {
        if (!isHungry()
                || feeder == null
                || food == null
                || food.isEmpty()
                || food.getFoodProperties(feeder) == null) {
            return false;
        }
        hungerTicks = randomHungerTicks();
        hungry = false;
        ItemStack eating = food.copyWithCount(1);
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            SexPistolsEntity member = member(level, number);
            if (member != null && member.isAlive()) {
                member.startSharedEating(feeder, eating);
            }
        }
        sync(owner);
        return true;
    }

    public boolean toggleSelectedMove(
            ServerLevel level,
            LivingEntity owner,
            Vec3 target) {
        SexPistolsEntity member = selectedMember(level);
        if (member == null
                || !member.isAlive()
                || member.getMemberState()
                        == SexPistolsMemberState.ENCIRCLEMENT) {
            return false;
        }
        if (member.getMemberState()
                == SexPistolsMemberState.SCOUTING) {
            member.setMemberState(
                    SexPistolsMemberState.FORMATION);
        }
        else {
            member.setCommandTarget(target);
            member.setMemberState(
                    SexPistolsMemberState.SCOUTING);
        }
        sync(owner);
        return true;
    }

    public List<Integer> availableMemberSlots(
            ServerLevel level) {
        List<Integer> available =
                new ArrayList<>(MEMBER_COUNT);
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            if (isMemberLoaded(slot)
                    || reviveTicks[slot] > 0) {
                continue;
            }
            if (canLoadMember(level, slot)) {
                available.add(slot);
            }
        }
        return List.copyOf(available);
    }

    // 1.16 isPistolAvailable read only the revive timer and the loaded mask,
    // so a put-away swarm still lends its members to the revolver.
    private boolean canLoadMember(ServerLevel level, int slot) {
        if (!summoned) {
            return swarmEstablished;
        }
        SexPistolsEntity member = member(
                level,
                SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
        if (member == null && releasedMembersOnly) {
            return true;
        }
        return member != null && member.isAlive();
    }

    public boolean markMemberLoaded(
            ServerLevel level,
            LivingEntity owner,
            int slot) {
        if (slot < 0
                || slot >= MEMBER_COUNT
                || isMemberLoaded(slot)
                || reviveTicks[slot] > 0
                || !canLoadMember(level, slot)) {
            return false;
        }
        int number = SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
        SexPistolsEntity member = member(level, number);

        loadedMemberMask |= 1 << slot;
        childUuids[slot] = null;
        childEntityIds[slot] = -1;
        if (member != null) {
            member.discard();
        }
        if (selectedNumber == number) {
            selectNextTracked();
        }
        sync(owner);
        return true;
    }

    public boolean beginMemberFlight(
            LivingEntity owner, int memberMask) {
        int normalized = memberMask & 0x3F;
        if (normalized == 0
                || (loadedMemberMask & normalized) != normalized
                || (inFlightMemberMask & normalized) != 0) {
            return false;
        }
        inFlightMemberMask |= normalized;
        sync(owner);
        return true;
    }

    public void cancelMemberFlight(
            LivingEntity owner, int memberMask) {
        int before = inFlightMemberMask;
        inFlightMemberMask &= ~(memberMask & 0x3F);
        if (before != inFlightMemberMask) {
            sync(owner);
        }
    }

    public SexPistolsEntity releaseInFlightMember(
            ServerLevel level,
            LivingEntity owner,
            int slot,
            Vec3 position) {
        if (slot < 0
                || slot >= MEMBER_COUNT
                || (inFlightMemberMask & 1 << slot) == 0) {
            return null;
        }
        SexPistolsEntity member = releaseMember(
                level, owner, slot, position);
        sync(owner);
        return member;
    }

    public int releaseInFlightMembers(
            ServerLevel level,
            LivingEntity owner,
            int memberMask,
            Vec3 position) {
        int releasable =
                memberMask & inFlightMemberMask & 0x3F;
        int released = releaseMembers(
                level, owner, releasable, position);
        if (releasable != 0) {
            setMembersToFormation(level, releasable);
            sync(owner);
        }
        return released;
    }

    public void returnEncirclementMembers(
            ServerLevel level,
            LivingEntity owner,
            int memberMask) {
        int normalized = memberMask & 0x3F;
        if (normalized == 0) {
            return;
        }
        encirclementReturnMask |= normalized;
        applyEncirclementReturns(level, owner);
        sync(owner);
    }

    public int releaseAttachedMembers(
            ServerLevel level,
            LivingEntity owner,
            int memberMask,
            Vec3 position) {
        int releasable = memberMask
                & loadedMemberMask
                & ~inFlightMemberMask
                & 0x3F;
        int released = releaseMembers(
                level, owner, releasable, position);
        if (releasable != 0) {
            setMembersToFormation(level, releasable);
            sync(owner);
        }
        return released;
    }

    public int recallAttachmentsAndScouts(
            ServerLevel level, LivingEntity owner) {
        int itemMask = clearItemAttachments(owner);
        int releasable = itemMask
                & loadedMemberMask
                & ~inFlightMemberMask;
        int recalled = releaseMembers(
                level,
                owner,
                releasable,
                owner.position().add(
                        0.0D,
                        owner.getBbHeight() * 0.65D,
                        0.0D));
        for (SexPistolsEntity member : loadedMembers(level)) {
            if (member.getMemberState()
                    == SexPistolsMemberState.SCOUTING
                    || member.getMemberState()
                            == SexPistolsMemberState.ENCIRCLEMENT) {
                member.setMemberState(
                        SexPistolsMemberState.FORMATION);
                int slot = SexPistolsEntities.slotForNumber(
                        member.getMemberNumber());
                // 1.16 recallRemoteControlPistols: back to the fixed place
                member.setIdleOffset(
                        SexPistolsEntity.slotIdleOffset(slot));
                if (slot < 0
                        || (releasable & 1 << slot) == 0) {
                    recalled++;
                }
            }
        }
        if (itemMask != 0 || recalled != 0) {
            sync(owner);
        }
        return recalled;
    }

    public void onMemberDeath(
            LivingEntity owner,
            int number,
            UUID childUuid) {
        if (recordMemberDeath(number, childUuid)) {
            selectNextTracked();
            sync(owner);
        }
    }

    boolean recordMemberDeath(int number, UUID childUuid) {
        int slot = SexPistolsEntities.slotForNumber(number);
        if (slot < 0 || childUuid == null
                || !childUuid.equals(childUuids[slot])) {
            return false;
        }
        childUuids[slot] = null;
        childEntityIds[slot] = -1;
        reviveTicks[slot] = SexPistolsConfig.reviveTicks();
        return true;
    }

    boolean tickReviveTimers() {
        boolean syncNeeded = false;
        for (int i = 0; i < MEMBER_COUNT; i++) {
            if (reviveTicks[i] <= 0) {
                continue;
            }
            reviveTicks[i]--;
            if (reviveTicks[i] == 0
                    || reviveTicks[i] % 20 == 0) {
                syncNeeded = true;
            }
        }
        return syncNeeded;
    }

    void trackMemberForTest(int number, UUID uuid, int entityId) {
        int slot = SexPistolsEntities.slotForNumber(number);
        if (slot < 0) {
            throw new IllegalArgumentException(
                    "Unknown Sex Pistols number: " + number);
        }
        childUuids[slot] = uuid;
        childEntityIds[slot] = entityId;
        reviveTicks[slot] = 0;
    }

    void setSummonedForTest(boolean value) {
        summoned = value;
    }

    void setSelectedNumberForTest(int number) {
        if (SexPistolsEntities.slotForNumber(number) < 0) {
            throw new IllegalArgumentException(
                    "Unknown Sex Pistols number: " + number);
        }
        selectedNumber = number;
    }

    private boolean ensureClosure(
            ServerLevel level,
            LivingEntity owner) {
        boolean changed = false;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            int number = SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
            SexPistolsEntity member = member(level, number);
            if (isMemberLoaded(slot)) {
                if (member != null) {
                    member.discard();
                    changed = true;
                }
                if (childUuids[slot] != null
                        || childEntityIds[slot] != -1) {
                    childUuids[slot] = null;
                    childEntityIds[slot] = -1;
                    changed = true;
                }
                continue;
            }
            if (member != null && member.isAlive()) {
                member.bindOwner(owner);
                member.setSelectedSkin(skin);
                if (member.getMemberState()
                        == SexPistolsMemberState.RECALLING) {
                    member.setMemberState(
                            SexPistolsMemberState.FORMATION);
                    changed = true;
                }
                if (childEntityIds[slot] != member.getId()) {
                    childEntityIds[slot] = member.getId();
                    changed = true;
                }
                continue;
            }

            if (childUuids[slot] != null) {
                childUuids[slot] = null;
                childEntityIds[slot] = -1;
                changed = true;
            }
            if (reviveTicks[slot] > 0 || releasedMembersOnly) {
                continue;
            }

            SexPistolsEntity created =
                    SexPistolsEntities.typeForNumber(number)
                            .create(level);
            if (created == null) {
                continue;
            }
            created.bindOwner(owner);
            created.setMemberState(
                    SexPistolsMemberState.FORMATION);
            created.setSelectedSkin(skin);
            created.setPos(SexPistolsEntity.formationPosition(
                    owner, slot));
            childUuids[slot] = created.getUUID();
            if (level.addFreshEntity(created)) {
                childEntityIds[slot] = created.getId();
            }
            else {
                childUuids[slot] = null;
                childEntityIds[slot] = -1;
            }
            changed = true;
        }
        return changed;
    }

    public SexPistolsEntity member(
            ServerLevel level,
            int number) {
        int slot = SexPistolsEntities.slotForNumber(number);
        if (slot < 0 || childUuids[slot] == null) {
            return null;
        }
        Entity entity = level.getEntity(childUuids[slot]);
        if (entity instanceof SexPistolsEntity member
                && member.getMemberNumber() == number) {
            return member;
        }
        return null;
    }

    private void forEachLoadedMember(
            ServerLevel level,
            java.util.function.Consumer<SexPistolsEntity> action) {
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            SexPistolsEntity member = member(level, number);
            if (member != null) {
                action.accept(member);
            }
        }
    }

    private boolean allLoadedMembersCloseTo(
            ServerLevel level,
            LivingEntity owner,
            double distance) {
        double distanceSqr = distance * distance;
        boolean found = false;
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            SexPistolsEntity member = member(level, number);
            if (member == null) {
                continue;
            }
            found = true;
            if (member.distanceToSqr(owner) > distanceSqr) {
                return false;
            }
        }
        return found;
    }

    private void discardChildren(ServerLevel level) {
        forEachLoadedMember(level, Entity::discard);
        Arrays.fill(childUuids, null);
        Arrays.fill(childEntityIds, -1);
    }

    private SexPistolsEntity releaseMember(
            ServerLevel level,
            LivingEntity owner,
            int slot,
            Vec3 position) {
        int bit = 1 << slot;
        if ((loadedMemberMask & bit) == 0) {
            inFlightMemberMask &= ~bit;
            return null;
        }
        loadedMemberMask &= ~bit;
        inFlightMemberMask &= ~bit;
        reviveTicks[slot] = 0;

        StandPower standPower = StandPower.get(owner);
        if (standPower == null
                || standPower.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return null;
        }
        // 1.16 releaseLoadedPistol recreated the member of a put-away swarm
        // and marked the swarm summoned.
        boolean bringsSwarmOut = !summoned;
        if (bringsSwarmOut
                ? tearingDown || !swarmEstablished || !owner.isAlive()
                : !standPower.isSummoned()) {
            return null;
        }
        if (bringsSwarmOut) {
            if (recallTicks > 0) {
                discardChildren(level);
                recallTicks = 0;
            }
            updateSkin(standPower.getSelectedSkin());
        }

        int number = SexPistolsIds.SEX_PISTOLS_NUMBERS[slot];
        SexPistolsEntity created =
                SexPistolsEntities.typeForNumber(number)
                        .create(level);
        if (created == null) {
            return null;
        }
        created.bindOwner(owner);
        created.setMemberState(
                SexPistolsMemberState.SCOUTING);
        created.setCommandTarget(position);
        created.setSelectedSkin(skin);
        created.setPos(position);
        childUuids[slot] = created.getUUID();
        if (!level.addFreshEntity(created)) {
            childUuids[slot] = null;
            childEntityIds[slot] = -1;
            return null;
        }
        childEntityIds[slot] = created.getId();
        if (bringsSwarmOut) {
            summoned = true;
            releasedMembersOnly = true;
            SexPistolsStands.SEX_PISTOLS.get()
                    .restoreAfterLoad(owner, standPower);
        }
        return created;
    }

    private int releaseMembers(
            ServerLevel level,
            LivingEntity owner,
            int memberMask,
            Vec3 position) {
        int normalized = memberMask & 0x3F;
        int released = 0;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            if ((normalized & 1 << slot) == 0) {
                continue;
            }
            boolean wasLoaded = isMemberLoaded(slot);
            releaseMember(level, owner, slot, position);
            if (wasLoaded) {
                released++;
            }
        }
        return released;
    }

    private void setMembersToFormation(
            ServerLevel level, int memberMask) {
        int normalized = memberMask & 0x3F;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            if ((normalized & 1 << slot) == 0) {
                continue;
            }
            SexPistolsEntity member = member(
                    level,
                    SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
            if (member != null) {
                member.setMemberState(
                        SexPistolsMemberState.FORMATION);
            }
        }
    }

    private boolean reconcileItemCarriers(
            LivingEntity owner) {
        if (!(owner instanceof Player player)) {
            return false;
        }
        int allowed =
                loadedMemberMask & ~inFlightMemberMask;
        int seen = 0;
        boolean changed = false;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (!(stack.getItem() instanceof RevolverItem)) {
                continue;
            }
            int before =
                    RevolverMemberAttachments
                            .attachedMemberMask(stack);
            int retained = before & allowed & ~seen;
            if (before != retained) {
                RevolverMemberAttachments.retainMembers(
                        stack, retained);
                changed = true;
            }
            seen |= retained;
        }
        int orphaned = allowed & ~seen;
        if (orphaned != 0) {
            loadedMemberMask &= ~orphaned;
            changed = true;
        }
        return changed;
    }

    private boolean applyEncirclementReturns(
            ServerLevel level, LivingEntity owner) {
        int before = encirclementReturnMask;
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            int bit = 1 << slot;
            if ((encirclementReturnMask & bit) == 0) {
                continue;
            }
            if (isMemberLoaded(slot)) {
                encirclementReturnMask &= ~bit;
                continue;
            }
            SexPistolsEntity member = member(
                    level,
                    SexPistolsIds.SEX_PISTOLS_NUMBERS[slot]);
            if (member == null || !member.isOwnedBy(owner)) {
                continue;
            }
            member.setMemberState(
                    SexPistolsMemberState.FORMATION);
            // 1.16 recallEncirclementStand: back to the fixed place
            member.setIdleOffset(SexPistolsEntity.slotIdleOffset(slot));
            member.setCommandTarget(
                    owner.position().add(
                            0.0D,
                            owner.getBbHeight() * 0.65D,
                            0.0D));
            encirclementReturnMask &= ~bit;
        }
        return before != encirclementReturnMask;
    }

    private static int clearItemAttachments(
            LivingEntity owner) {
        if (!(owner instanceof Player player)) {
            return 0;
        }
        int cleared = 0;
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack =
                    player.getInventory().getItem(slot);
            if (stack.getItem() instanceof RevolverItem) {
                cleared |=
                        RevolverMemberAttachments.clear(stack);
            }
        }
        return cleared;
    }

    private void updateSkin(Optional<ResourceLocation> selectedSkin) {
        skin = selectedSkin != null
                ? selectedSkin
                : Optional.empty();
    }

    private void selectNextTracked() {
        if (childUuid(selectedNumber) != null) {
            return;
        }
        for (int number : SexPistolsIds.SEX_PISTOLS_NUMBERS) {
            if (childUuid(number) != null) {
                selectedNumber = number;
                return;
            }
        }
    }

    private void sync(LivingEntity owner) {
        if (!owner.level().isClientSide()) {
            owner.syncData(
                    SexPistolsAttachments.SEX_PISTOLS_COORDINATOR);
        }
    }

    public void copyPersistentFrom(
            SexPistolsCoordinator other) {
        selectedNumber = other.selectedNumber;
        targetMode = other.targetMode;
        transferOrder = other.transferOrder;
        System.arraycopy(
                other.reviveTicks,
                0,
                reviveTicks,
                0,
                MEMBER_COUNT);
        skin = other.skin;
        loadedMemberMask = other.loadedMemberMask
                & ~other.inFlightMemberMask;
        inFlightMemberMask = 0;
        encirclementReturnMask = 0;
        trajectoryVision = other.trajectoryVision;
        hungerTicks = other.hungerTicks;
        hungry = other.hungry;
        clearSplittingVolley();
        summoned = false;
        releasedMembersOnly = false;
        swarmEstablished = other.swarmEstablished;
        recallTicks = 0;
        Arrays.fill(childUuids, null);
        Arrays.fill(childEntityIds, -1);
    }

    @Override
    public CompoundTag serializeNBT(
            HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SelectedNumber", selectedNumber);
        tag.putInt("TargetMode", targetMode.ordinal());
        tag.putInt("TransferOrder", transferOrder.ordinal());
        tag.putBoolean("Summoned", summoned);
        tag.putBoolean("SwarmEstablished", swarmEstablished);
        tag.putBoolean("ReleasedMembersOnly", releasedMembersOnly);
        tag.putInt("LoadedMemberMask", loadedMemberMask);
        tag.putInt("InFlightMemberMask", inFlightMemberMask);
        tag.putInt(
                "EncirclementReturnMask",
                encirclementReturnMask);
        tag.putBoolean(
                "TrajectoryVision", trajectoryVision);
        tag.putInt("SharedHungerTicks", hungerTicks);
        tag.putBoolean("SharedHungry", hungry);
        if (splittingVolleyId != null) {
            tag.putUUID(
                    "SplittingVolleyId", splittingVolleyId);
        }
        tag.putInt(
                "SplittingActiveFragments",
                splittingActiveFragments);
        tag.putBoolean(
                "SplittingVolleyAwarded",
                splittingVolleyAwarded);
        tag.putInt(
                "SplittingKilledCount",
                splittingKilledTargets.size());
        int splitKillIndex = 0;
        for (UUID killed : splittingKilledTargets) {
            tag.putUUID(
                    "SplittingKilled" + splitKillIndex++,
                    killed);
        }
        tag.putIntArray("ReviveTicks", reviveTicks);
        skin.ifPresent(value ->
                tag.putString("Skin", value.toString()));
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            UUID uuid = childUuids[slot];
            if (uuid != null) {
                tag.putUUID("Child" + slot, uuid);
            }
        }
        return tag;
    }

    @Override
    public void deserializeNBT(
            HolderLookup.Provider provider,
            CompoundTag tag) {
        int selected = tag.contains("SelectedNumber")
                ? tag.getInt("SelectedNumber")
                : SexPistolsIds.SEX_PISTOLS_NUMBERS[0];
        selectedNumber = SexPistolsEntities.slotForNumber(selected) >= 0
                ? selected
                : SexPistolsIds.SEX_PISTOLS_NUMBERS[0];
        targetMode = SexPistolsTargetMode.byId(
                tag.getInt("TargetMode"));
        transferOrder = SexPistolsTransferOrder.byId(
                tag.getInt("TransferOrder"));
        summoned = tag.getBoolean("Summoned");
        loadedMemberMask =
                tag.getInt("LoadedMemberMask") & 0x3F;
        // Saves from before this flag: a summoned or loaded swarm existed.
        swarmEstablished = tag.contains("SwarmEstablished")
                ? tag.getBoolean("SwarmEstablished") || summoned
                : summoned || loadedMemberMask != 0;
        releasedMembersOnly = summoned
                && tag.getBoolean("ReleasedMembersOnly");
        inFlightMemberMask =
                tag.getInt("InFlightMemberMask")
                        & loadedMemberMask;
        encirclementReturnMask =
                tag.getInt("EncirclementReturnMask")
                        & 0x3F;
        trajectoryVision =
                tag.getBoolean("TrajectoryVision");
        hungerTicks = tag.contains("SharedHungerTicks")
                ? Math.max(0, tag.getInt("SharedHungerTicks"))
                : randomHungerTicks();
        hungry = tag.getBoolean("SharedHungry");
        splittingVolleyId =
                tag.hasUUID("SplittingVolleyId")
                        ? tag.getUUID("SplittingVolleyId")
                        : null;
        splittingActiveFragments = Math.max(
                0,
                tag.getInt("SplittingActiveFragments"));
        splittingVolleyAwarded =
                tag.getBoolean("SplittingVolleyAwarded");
        splittingKilledTargets.clear();
        int splitKillCount = net.minecraft.util.Mth.clamp(
                tag.getInt("SplittingKilledCount"),
                0,
                MAX_TRACKED_SPLITTING_KILLS);
        for (int i = 0; i < splitKillCount; i++) {
            String key = "SplittingKilled" + i;
            if (tag.hasUUID(key)) {
                splittingKilledTargets.add(tag.getUUID(key));
            }
        }
        if (splittingVolleyId == null
                || splittingActiveFragments == 0
                || splittingVolleyAwarded) {
            clearSplittingVolley();
        }
        recallTicks = 0;
        Arrays.fill(childUuids, null);
        Arrays.fill(childEntityIds, -1);
        Arrays.fill(reviveTicks, 0);
        int[] savedReviveTicks = tag.getIntArray("ReviveTicks");
        for (int i = 0;
                i < MEMBER_COUNT && i < savedReviveTicks.length;
                i++) {
            reviveTicks[i] = Math.max(0, savedReviveTicks[i]);
        }
        for (int slot = 0; slot < MEMBER_COUNT; slot++) {
            String key = "Child" + slot;
            if (!isMemberLoaded(slot)
                    && tag.hasUUID(key)) {
                childUuids[slot] = tag.getUUID(key);
            }
        }
        if (!summoned && trackedMemberCount() > 0) {
            recallTicks = RECALL_TICKS;
        }
        ResourceLocation savedSkin = tag.contains("Skin")
                ? ResourceLocation.tryParse(tag.getString("Skin"))
                : null;
        skin = Optional.ofNullable(savedSkin);
    }

    private void clearSplittingVolley() {
        splittingVolleyId = null;
        splittingActiveFragments = 0;
        splittingVolleyAwarded = false;
        splittingKilledTargets.clear();
    }

    private boolean tickHunger() {
        if (!hungerEnabled || hungry) {
            return false;
        }
        if (hungerTicks > 0) {
            hungerTicks--;
        }
        if (hungerTicks > 0) {
            return false;
        }
        hungerTicks = 0;
        hungry = true;
        return true;
    }

    private static boolean isHoldingFood(LivingEntity owner) {
        return owner instanceof Player player
                && (player.getMainHandItem()
                                .getFoodProperties(player) != null
                        || player.getOffhandItem()
                                .getFoodProperties(player) != null);
    }

    private static int randomHungerTicks() {
        int minimum = SexPistolsConfig.hungerMinTicks();
        int extra = SexPistolsConfig.hungerExtraTicks();
        long randomExtra = extra > 0
                ? ThreadLocalRandom.current().nextLong((long) extra + 1L)
                : 0L;
        return (int) Math.min(
                Integer.MAX_VALUE, (long) minimum + randomExtra);
    }
}
