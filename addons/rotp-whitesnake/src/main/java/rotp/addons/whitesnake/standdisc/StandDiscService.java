package rotp.addons.whitesnake.standdisc;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.StandDiscAuthorityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class StandDiscService {
    private StandDiscService() {}

    public static Result extractTo(
            LivingEntity target, LivingEntity recipient) {
        LivingEntity owner = normalizeTarget(target);
        if (owner == null || recipient == null) {
            return Result.of(Status.INVALID_TARGET);
        }
        if (owner.level().isClientSide()
                || recipient.level().isClientSide()) {
            return Result.of(Status.NOT_SERVER_SIDE);
        }

        StandPower ownerPower = StandPower.get(owner);
        StandInstance current = ownerPower != null
                ? ownerPower.getStandInstance().orElse(null)
                : null;
        if (current == null || !current.standExists()) {
            return Result.of(Status.NO_STAND);
        }

        ResourceLocation standId = current.getStandId();
        StandTypePersistentData typeData =
                ownerPower.getCurTypeData();
        if (standId == null || typeData == null) {
            return Result.of(Status.INVALID_STAND_STATE);
        }

        StandDiscLossState state =
                WhitesnakeAddon.standDiscState(owner);
        if (state.isExtracted()) {
            return Result.of(Status.ALREADY_EXTRACTED);
        }

        UUID extractionId = UUID.randomUUID();
        CompoundTag progression =
                typeData.serializeNBT(owner.registryAccess());
        CompoundTag awakening =
                ownerPower.userStandAwakeningState.serializeNBT();
        ItemStack disc = StandDiscItem.withStand(current);
        disc.set(
                WhitesnakeAddon.STAND_DISC_AUTHORITY.get(),
                new StandDiscAuthorityData(
                        owner.getUUID(),
                        extractionId,
                        standId,
                        owner.getName().getString()));

        if (!state.beginExtraction(
                extractionId,
                standId,
                progression,
                awakening)) {
            return Result.of(Status.ALREADY_EXTRACTED);
        }

        StandPowerTransitions.Result transition =
                StandPowerTransitions.extract(ownerPower, standId);
        if (!transition.applied()) {
            state.restore(extractionId, standId);
            return Result.of(
                    fromTransition(transition.status()));
        }

        // 1.16 putOutStand: the Stand on the disc stays taken
        StandDiscItem.markPutOut(owner, disc);
        ItemUtil.giveItemTo(recipient, disc, true);
        return Result.of(Status.APPLIED);
    }

    public static Result restore(
            ItemStack disc, LivingEntity target) {
        LivingEntity owner = normalizeTarget(target);
        if (owner == null) {
            return Result.of(Status.INVALID_TARGET);
        }
        if (owner.level().isClientSide()) {
            return Result.of(Status.NOT_SERVER_SIDE);
        }

        StandDiscAuthorityData authority =
                StandDiscAuthorityData.get(disc).orElse(null);
        if (authority == null) {
            return Result.of(Status.INVALID_DISC);
        }
        if (!authority.ownerId().equals(owner.getUUID())) {
            return Result.of(Status.OWNER_MISMATCH);
        }

        StandInstance replacement =
                StandDiscItem.getStandInstance(disc);
        if (replacement == null
                || !replacement.standExists()
                || !authority.standId().equals(
                        replacement.getStandId())) {
            return Result.of(Status.STAND_MISMATCH);
        }

        StandDiscLossState state =
                WhitesnakeAddon.standDiscState(owner);
        CompoundTag progression = state.progression(
                authority.extractionId(),
                authority.standId()).orElse(null);
        CompoundTag awakening = state.awakening(
                authority.extractionId(),
                authority.standId()).orElse(null);
        if (progression == null || awakening == null) {
            return Result.of(state.isExtracted()
                    ? Status.TOKEN_MISMATCH
                    : Status.NO_EXTRACTION);
        }

        StandPower ownerPower =
                PowerClass.STAND.attachGet(owner);
        if (ownerPower.hasPower()) {
            return Result.of(Status.STAND_ALREADY_PRESENT);
        }

        StandPowerTransitions.Result transition =
                StandPowerTransitions.insert(
                        ownerPower, replacement);
        if (!transition.applied()) {
            return Result.of(
                    fromTransition(transition.status()));
        }

        StandTypePersistentData restoredData =
                ownerPower.getCurTypeData();
        if (restoredData == null
                || ownerPower.getPowerType() == null
                || !authority.standId().equals(
                        ownerPower.getPowerType().getId())) {
            rollbackInsert(ownerPower, authority.standId());
            return Result.of(Status.PROGRESSION_RESTORE_FAILED);
        }

        try {
            restoredData.deserializeNBT(
                    owner.registryAccess(), progression);
            ownerPower.userStandAwakeningState.deserializeNBT(
                    awakening);
            ownerPower.afterNbtRead();
            restoredData.syncOnUpdate(owner);
            ownerPower.userStandAwakeningState.sync(owner);
        } catch (RuntimeException exception) {
            rollbackInsert(ownerPower, authority.standId());
            return Result.of(Status.PROGRESSION_RESTORE_FAILED);
        }

        if (!state.restore(
                authority.extractionId(),
                authority.standId())) {
            rollbackInsert(ownerPower, authority.standId());
            return Result.of(Status.TOKEN_MISMATCH);
        }
        // 1.16 giveStandFromInstance(standExistedInWorld): not counted twice
        StandDiscItem.takePutOut(owner, disc);
        return Result.of(Status.APPLIED);
    }

    public static boolean isAuthoritative(ItemStack disc) {
        return StandDiscAuthorityData.get(disc).isPresent();
    }

    @Nullable
    public static LivingEntity normalizeTarget(LivingEntity target) {
        if (target == null) {
            return null;
        }
        LivingEntity normalized = StandUtil.getStandUser(target);
        return normalized != null && normalized.isAlive()
                ? normalized
                : null;
    }

    private static void rollbackInsert(
            StandPower power, ResourceLocation standId) {
        StandPowerTransitions.extract(power, standId);
    }

    private static Status fromTransition(
            StandPowerTransitions.Status status) {
        if (status == StandPowerTransitions.Status.NO_STAND) {
            return Status.NO_STAND;
        }
        if (status
                == StandPowerTransitions.Status.STAND_ALREADY_PRESENT) {
            return Status.STAND_ALREADY_PRESENT;
        }
        if (status
                == StandPowerTransitions.Status.SOURCE_MISMATCH) {
            return Status.STAND_MISMATCH;
        }
        return Status.CORE_TRANSITION_FAILED;
    }

    public enum Status {
        APPLIED,
        NOT_SERVER_SIDE,
        INVALID_TARGET,
        NO_STAND,
        INVALID_STAND_STATE,
        ALREADY_EXTRACTED,
        INVALID_DISC,
        OWNER_MISMATCH,
        TOKEN_MISMATCH,
        NO_EXTRACTION,
        STAND_MISMATCH,
        STAND_ALREADY_PRESENT,
        PROGRESSION_RESTORE_FAILED,
        CORE_TRANSITION_FAILED
    }

    public record Result(Status status) {
        public Result {
            if (status == null) {
                throw new IllegalArgumentException(
                        "Stand-disc result status must not be null");
            }
        }

        public boolean applied() {
            return status == Status.APPLIED;
        }

        private static Result of(Status status) {
            return new Result(status);
        }
    }
}
