package rotp.addons.whitesnake.memory;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.disc.MemoryDiscData;
import rotp.addons.whitesnake.disc.MemoryDiscItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class MemoryDiscService {
    private static final ResourceKey<MobEffect> LOVE_TRAIN = ResourceKey.create(
            Registries.MOB_EFFECT,
            ResourceLocation.fromNamespaceAndPath("rotp_d4c", "love_train"));

    private MemoryDiscService() {}

    public static Result extractTo(
            LivingEntity target, LivingEntity recipient) {
        LivingEntity targetUser = normalizeTarget(target);
        if (targetUser == null || recipient == null) {
            return Result.of(Status.INVALID_TARGET);
        }
        if (targetUser.level().isClientSide()
                || recipient.level().isClientSide()) {
            return Result.of(Status.NOT_SERVER_SIDE);
        }
        if (rejectLoveTrainProtectedTarget(targetUser, recipient)) {
            return Result.of(Status.PROTECTED_BY_LOVE_TRAIN);
        }

        MemoryLossState state = WhitesnakeAddon.memoryState(targetUser);
        if (state.isExtracted()) {
            return Result.of(Status.ALREADY_EXTRACTED);
        }
        if (MemoryDiscItem.hasDiscForTarget(
                recipient, targetUser.getUUID())) {
            return Result.of(Status.DUPLICATE_DISC);
        }

        UUID extractionId = UUID.randomUUID();
        ItemStack disc = MemoryDiscItem.withMemoryOf(
                targetUser, extractionId);
        if (disc.isEmpty()
                || !state.beginExtraction(extractionId)) {
            return Result.of(Status.ALREADY_EXTRACTED);
        }
        ItemUtil.giveItemTo(recipient, disc, true);
        return Result.of(Status.APPLIED);
    }

    public static boolean rejectLoveTrainProtectedTarget(
            LivingEntity target, LivingEntity recipient) {
        LivingEntity targetUser = normalizeTarget(target);
        if (targetUser == null || targetUser.level().isClientSide()
                || !targetUser.registryAccess().lookupOrThrow(Registries.MOB_EFFECT)
                        .get(LOVE_TRAIN).map(targetUser::hasEffect).orElse(false)) {
            return false;
        }
        if (recipient instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.rotp_d4c.memory_extraction_blocked_by_love_train"), true);
        }
        if (targetUser instanceof Player player) {
            player.displayClientMessage(Component.translatable(
                    "message.rotp_d4c.memory_protected_by_love_train"), true);
        }
        return true;
    }

    public static Result restore(
            ItemStack disc, LivingEntity target) {
        return restore(disc, target, null);
    }

    public static Result restore(
            ItemStack disc,
            LivingEntity target,
            @Nullable CompoundTag payload) {
        LivingEntity targetUser = normalizeTarget(target);
        if (targetUser == null) {
            return Result.of(Status.INVALID_TARGET);
        }
        if (targetUser.level().isClientSide()) {
            return Result.of(Status.NOT_SERVER_SIDE);
        }

        MemoryDiscData data =
                MemoryDiscItem.getData(disc).orElse(null);
        if (data == null) {
            return Result.of(Status.INVALID_DISC);
        }
        if (payload != null && !data.matchesPayload(payload)) {
            return Result.of(Status.INVALID_PAYLOAD);
        }
        if (!data.targetId().equals(targetUser.getUUID())) {
            return Result.of(Status.TARGET_MISMATCH);
        }

        MemoryLossState state = WhitesnakeAddon.memoryState(targetUser);
        if (!state.isExtracted()) {
            return Result.of(Status.NO_MEMORY_LOSS);
        }
        if (!state.restore(data.extractionId())) {
            return Result.of(Status.TOKEN_MISMATCH);
        }
        return Result.of(Status.APPLIED);
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

    public enum Status {
        APPLIED,
        NOT_SERVER_SIDE,
        INVALID_TARGET,
        PROTECTED_BY_LOVE_TRAIN,
        ALREADY_EXTRACTED,
        DUPLICATE_DISC,
        INVALID_DISC,
        INVALID_PAYLOAD,
        TARGET_MISMATCH,
        NO_MEMORY_LOSS,
        TOKEN_MISMATCH
    }

    public record Result(Status status) {
        public Result {
            if (status == null) {
                throw new IllegalArgumentException(
                        "Memory-disc result status must not be null");
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
