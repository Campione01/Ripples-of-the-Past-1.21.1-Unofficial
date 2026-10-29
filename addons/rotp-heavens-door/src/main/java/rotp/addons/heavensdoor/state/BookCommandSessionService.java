package rotp.addons.heavensdoor.state;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.mixin.EntityPersistentDataAccessor;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.network.BookPlayerOption;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.addons.heavensdoor.network.OpenBookSnapshotPayload;

public final class BookCommandSessionService {
    public static final int SESSION_LIFETIME_TICKS = 200;
    public static final int NO_PLAYER_OPTION = -1;
    private static final int MAX_PLAYER_OPTIONS = 256;

    private static final String SESSION_TAG =
            "rotp_hd.BookCommandSession";
    private static final String SESSION_COUNTER_TAG =
            "rotp_hd.BookCommandGeneration";
    private static final String SELF_HARM_OWNER_TAG =
            "rotp_hd.SelfHarmOwner";
    private static final String NONCE_TAG = "Nonce";
    private static final String CASTER_TAG = "Caster";
    private static final String TARGET_TAG = "Target";
    private static final String STAND_TAG = "Stand";
    private static final String STAND_TYPE_TAG = "StandType";
    private static final String DIMENSION_TAG = "Dimension";
    private static final String CREATED_AT_TAG = "CreatedAt";
    private static final String EXPIRES_AT_TAG = "ExpiresAt";
    private static final String GENERATION_TAG = "Generation";
    private static final String BOOK_GENERATION_TAG = "BookGeneration";
    private static final String BOOK_EXPIRES_AT_TAG = "BookExpiresAt";
    private static final String COMMAND_MASK_TAG = "CommandMask";
    private static final String PLAYER_OPTIONS_TAG = "PlayerOptions";

    private BookCommandSessionService() {}

    @Nullable
    public static IssuedSession issue(
            ServerPlayer player,
            LivingEntity rawTarget) {
        revoke(player);
        LivingEntity target = BookStateService.authorizedTarget(
                player, rawTarget);
        HeavensDoorStandEntity stand =
                HeavensDoorAddon.controller(player);
        if (target == null
                || stand == null
                || target instanceof Player targetPlayer
                        && targetPlayer.isSpectator()) {
            return null;
        }

        BookState state = HeavensDoorAddon.bookState(target);
        long now = player.serverLevel().getGameTime();
        long expiresAt = Math.min(
                state.expiresAt(),
                now + SESSION_LIFETIME_TICKS);
        if (expiresAt <= now) {
            return null;
        }

        CompoundTag persistent = player.getPersistentData();
        long generation = nextGeneration(persistent);
        UUID nonce = UUID.randomUUID();
        CompoundTag playerOptionTargets = new CompoundTag();
        List<BookPlayerOption> playerOptions =
                createPlayerOptions(
                        player, target, playerOptionTargets);
        int commandMask = availableCommandMask(
                player, target, !playerOptions.isEmpty());
        CompoundTag session = new CompoundTag();
        session.putUUID(NONCE_TAG, nonce);
        session.putUUID(CASTER_TAG, player.getUUID());
        session.putUUID(TARGET_TAG, target.getUUID());
        session.putUUID(STAND_TAG, stand.getUUID());
        session.putString(
                STAND_TYPE_TAG,
                HeavensDoorAddon.HEAVENS_DOOR.get()
                        .getId().toString());
        session.putString(
                DIMENSION_TAG,
                player.level().dimension().location().toString());
        session.putLong(CREATED_AT_TAG, now);
        session.putLong(EXPIRES_AT_TAG, expiresAt);
        session.putLong(GENERATION_TAG, generation);
        session.putLong(
                BOOK_GENERATION_TAG, state.generation());
        session.putLong(
                BOOK_EXPIRES_AT_TAG, state.expiresAt());
        session.putInt(COMMAND_MASK_TAG, commandMask);
        session.put(PLAYER_OPTIONS_TAG, playerOptionTargets);
        persistent.put(SESSION_TAG, session);
        return new IssuedSession(
                nonce,
                generation,
                expiresAt,
                commandMask,
                playerOptions);
    }

    public static boolean consume(
            ServerPlayer player,
            UUID nonce,
            BookCommand command,
            int playerOptionId) {
        Session session = read(player);
        if (session == null
                || nonce == null
                || !session.nonce().equals(nonce)) {
            return false;
        }

        // A matching token is one-shot even when later validation fails.
        revoke(player);
        if (command == null || command == BookCommand.INVALID) {
            return false;
        }
        if (command != BookCommand.CANNOT_ATTACK_SELECTED_PLAYER
                && playerOptionId != NO_PLAYER_OPTION) {
            return false;
        }

        LivingEntity target = validateBinding(
                player,
                session,
                command != BookCommand.CLOSE);
        if (target == null) {
            return false;
        }
        if (command == BookCommand.CLOSE) {
            return BookStateService.clear(target);
        }
        if (!command.permittedBy(session.commandMask())
                || command.healthLimited() && RestrictedCommandOwners.released(player)
                || command.requiredResolve() > resolveLevel(player)
                || !execute(
                        player,
                        target,
                        session,
                        command,
                        playerOptionId)) {
            return false;
        }

        player.serverLevel().playSound(
                null,
                target,
                HeavensDoorAddon.WRITE_COMMAND.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        BookStateService.clear(target);
        return true;
    }

    public static void tick(ServerPlayer player) {
        Session session = read(player);
        if (session == null) {
            return;
        }
        LivingEntity target = validateBinding(
                player, session, false);
        if (target == null) {
            revoke(player);
            return;
        }
        if (player.distanceToSqr(target)
                > BookStateRules.ACTION_RANGE
                        * BookStateRules.ACTION_RANGE) {
            revoke(player);
            BookStateService.clear(target);
            return;
        }
        int mask = availableCommandMask(player, target, !session.playerOptions().isEmpty());
        if (mask != session.commandMask()) {
            player.getPersistentData().getCompound(SESSION_TAG).putInt(COMMAND_MASK_TAG, mask);
            sendRefresh(player, target);
        }
    }

    public static void revoke(ServerPlayer player) {
        if (player != null) {
            player.getPersistentData().remove(SESSION_TAG);
        }
    }

    public static void revokeMatchingBook(
            MinecraftServer server,
            UUID casterUuid,
            UUID targetUuid,
            long bookGeneration) {
        if (server == null
                || casterUuid == null
                || targetUuid == null) {
            return;
        }
        ServerPlayer player =
                server.getPlayerList().getPlayer(casterUuid);
        Session session = read(player);
        if (session != null
                && session.targetUuid().equals(targetUuid)
                && session.bookGeneration() == bookGeneration) {
            revoke(player);
        }
    }

    public static void refreshMatchingBook(LivingEntity target, BookState state) {
        ServerPlayer player = target.getServer().getPlayerList().getPlayer(state.ownerUuid());
        Session session = read(player);
        if (session != null && session.targetUuid().equals(target.getUUID())
                && session.bookGeneration() == state.generation()) {
            if (session.expiresAt() < target.level().getGameTime()) {
                revoke(player);
                return;
            }
            CompoundTag tag = player.getPersistentData().getCompound(SESSION_TAG);
            tag.putLong(BOOK_EXPIRES_AT_TAG, state.expiresAt());
            tag.putLong(EXPIRES_AT_TAG, Math.min(session.expiresAt(), state.expiresAt()));
            sendRefresh(player, target);
        }
    }

    private static void sendRefresh(ServerPlayer player, LivingEntity target) {
        Session session = read(player);
        if (session == null) {
            return;
        }
        List<BookPlayerOption> options = new ArrayList<>();
        for (String key : session.playerOptions().getAllKeys()) {
            ServerPlayer candidate = player.getServer().getPlayerList().getPlayer(session.playerOptions().getUUID(key));
            if (candidate != null) {
                options.add(new BookPlayerOption(Integer.parseInt(key), candidate.getGameProfile().getName()));
            }
        }
        options.sort(java.util.Comparator.comparingInt(BookPlayerOption::optionId));
        PacketDistributor.sendToPlayer(player, OpenBookSnapshotPayload.from(target, HeavensDoorAddon.bookState(target),
                new IssuedSession(session.nonce(), session.generation(), session.expiresAt(), session.commandMask(), options)));
    }

    public static int availableCommandMask(
            ServerPlayer player,
            LivingEntity target,
            boolean hasPlayerOptions) {
        int resolve = resolveLevel(player);
        int mask = 0;
        for (BookCommand command : BookCommand.values()) {
            boolean targetEligible = switch (command) {
                case CANNOT_ATTACK_SELECTED_PLAYER ->
                        hasPlayerOptions;
                case MOB_BEHAVIOR_TOGGLE ->
                        BookCommandLeaseService
                                .behaviorEligible(target);
                case INVALID, CLOSE -> false;
                default -> true;
            };
            if (BookCommandLeaseService.initialized()
                    && command.permissionBit() != 0
                    && resolve >= command.requiredResolve()
                    && targetEligible) {
                if (command.healthLimited() && RestrictedCommandOwners.released(player)) {
                    continue;
                }
                mask |= command.permissionBit();
            }
        }
        return mask;
    }

    @Nullable
    private static LivingEntity validateBinding(
            ServerPlayer player,
            Session session,
            boolean requireActionRange) {
        if (player == null
                || !player.isAlive()
                || player.isSpectator()
                || !player.getUUID().equals(session.casterUuid())
                || player.serverLevel().getGameTime()
                        > session.expiresAt()
                || player.serverLevel().getGameTime()
                        < session.createdAt()
                || player.getPersistentData().getLong(
                        SESSION_COUNTER_TAG)
                        != session.generation()
                || !player.level().dimension().location()
                        .equals(session.dimension())) {
            return null;
        }

        HeavensDoorStandEntity stand =
                HeavensDoorAddon.controller(player);
        StandPower power = StandPower.get(player);
        if (stand == null
                || !stand.getUUID().equals(session.standUuid())
                || power == null
                || !power.hasPower()
                || power.getPowerType()
                        != HeavensDoorAddon.HEAVENS_DOOR.get()
                || !power.getPowerType().getId().toString()
                        .equals(session.standType())) {
            return null;
        }

        Entity entity = player.serverLevel()
                .getEntity(session.targetUuid());
        if (!(entity instanceof LivingEntity target)
                || !target.isAlive()
                || target instanceof Player targetPlayer
                        && targetPlayer.isSpectator()
                || !target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            return null;
        }
        BookState state = HeavensDoorAddon.bookState(target);
        if (!state.isOwnedBy(player.getUUID())
                || !PageOwnerSavedData.get(player.getServer()).matches(state.ownerUuid(), state.bookOwnerEpoch())
                || !state.matches(
                        session.targetUuid(),
                        session.dimension())
                || state.generation()
                        != session.bookGeneration()
                || state.expiresAt()
                        != session.bookExpiresAt()
                || player.serverLevel().getGameTime()
                        > state.expiresAt()) {
            return null;
        }
        if (requireActionRange
                && BookStateService.authorizedTarget(
                        player, target) != target) {
            return null;
        }
        return target;
    }

    private static boolean execute(
            ServerPlayer player,
            LivingEntity target,
            Session session,
            BookCommand command,
            int playerOptionId) {
        return switch (command) {
            case FLY_BACK -> flyBack(player, target);
            case BLINDNESS_TOGGLE ->
                    toggleBlindness(player, target);
            case FREEZE -> freeze(player, target);
            case SELF_HARM_TOGGLE ->
                    toggleSelfHarm(player, target);
            case CANNOT_ATTACK_SELECTED_PLAYER ->
                    toggleCannotAttack(
                            player,
                            target,
                            session,
                            playerOptionId);
            case MOB_BEHAVIOR_TOGGLE ->
                    target instanceof Mob mob
                            && BookCommandLeaseService
                                    .toggleBehavior(mob, player);
            default -> false;
        };
    }

    private static boolean toggleCannotAttack(
            ServerPlayer player,
            LivingEntity target,
            Session session,
            int playerOptionId) {
        if (playerOptionId < 0) {
            return false;
        }
        String optionKey = Integer.toString(playerOptionId);
        if (!session.playerOptions().hasUUID(optionKey)) {
            return false;
        }
        UUID forbiddenUuid =
                session.playerOptions().getUUID(optionKey);
        ServerPlayer forbidden = player.getServer()
                .getPlayerList().getPlayer(forbiddenUuid);
        if (forbidden == null
                || !forbidden.isAlive()
                || forbidden.isSpectator()
                || forbidden.serverLevel() != player.serverLevel()
                || forbidden.getUUID().equals(target.getUUID())) {
            return false;
        }
        return BookCommandLeaseService.toggleCombat(
                target, forbidden, player);
    }

    private static boolean flyBack(
            ServerPlayer player, LivingEntity target) {
        return applyFlyBack(player, target);
    }

    /**
     * 1.16 TriggerFlyBackwardsPacket, shared by the book command and the
     * finisher punch: drop STUN, then push away from the caster.
     */
    public static boolean applyFlyBack(
            LivingEntity caster, LivingEntity target) {
        target.removeEffect(ModStatusEffects.STUN);
        Vec3 look = caster.getLookAngle();
        double[] velocity = FlyBackRules.velocity(
                target.getX() - caster.getX(),
                target.getZ() - caster.getZ(),
                look.x,
                look.z);
        if (velocity == null) {
            return false;
        }
        target.setDeltaMovement(
                velocity[0], velocity[1], velocity[2]);
        target.hurtMarked = true;
        return true;
    }

    private static boolean toggleBlindness(
            ServerPlayer player, LivingEntity target) {
        return WrittenCommandService.toggle(player, target, BookCommand.BLINDNESS_TOGGLE);
    }

    private static boolean freeze(
            ServerPlayer player, LivingEntity target) {
        return WrittenCommandService.apply(player, target, BookCommand.FREEZE);
    }

    private static boolean toggleSelfHarm(
            ServerPlayer player, LivingEntity target) {
        if (ownsLegacySelfHarm(target, player.getUUID())) {
            target.removeEffect(HeavensDoorAddon.KYS_EFFECT);
            ((EntityPersistentDataAccessor) target).heavensDoor$existingPersistentData().remove(SELF_HARM_OWNER_TAG);
            return true;
        }
        return WrittenCommandService.toggle(player, target, BookCommand.SELF_HARM_TOGGLE);
    }

    public static boolean ownsLegacySelfHarm(LivingEntity target, UUID issuer) {
        CompoundTag legacy = ((EntityPersistentDataAccessor) target).heavensDoor$existingPersistentData();
        return issuer != null && target.hasEffect(HeavensDoorAddon.KYS_EFFECT)
                && legacy != null && legacy.hasUUID(SELF_HARM_OWNER_TAG)
                && issuer.equals(legacy.getUUID(SELF_HARM_OWNER_TAG))
                && (!target.hasData(HeavensDoorAddon.WRITTEN_COMMAND_STATE)
                        || !target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).has(BookCommand.SELF_HARM_TOGGLE)
                                && !target.getData(HeavensDoorAddon.WRITTEN_COMMAND_STATE).hasExternal(BookCommand.SELF_HARM_TOGGLE));
    }

    public static void tickSelfHarmOwner(LivingEntity subject) {
        if (subject.level().isClientSide()) {
            return;
        }
        CompoundTag persistent = ((EntityPersistentDataAccessor) subject).heavensDoor$existingPersistentData();
        if (persistent == null || !persistent.contains(SELF_HARM_OWNER_TAG)) {
            return;
        }
        if (!subject.hasEffect(HeavensDoorAddon.KYS_EFFECT)) {
            persistent.remove(SELF_HARM_OWNER_TAG);
            return;
        }
        if (!persistent.hasUUID(SELF_HARM_OWNER_TAG)) {
            return;
        }
        Player owner = subject.level().getPlayerByUUID(
                persistent.getUUID(SELF_HARM_OWNER_TAG));
        if (RestrictedCommandOwners.released(owner)) {
            subject.removeEffect(HeavensDoorAddon.KYS_EFFECT);
            persistent.remove(SELF_HARM_OWNER_TAG);
        }
    }

    private static int resolveLevel(ServerPlayer player) {
        StandPower power =
                player != null ? StandPower.get(player) : null;
        return power != null
                && power.hasPower()
                && power.getPowerType()
                        == HeavensDoorAddon.HEAVENS_DOOR.get()
                ? power.getResolveLevel()
                : -1;
    }

    private static List<BookPlayerOption> createPlayerOptions(
            ServerPlayer player,
            LivingEntity target,
            CompoundTag optionTargets) {
        List<BookPlayerOption> options = new ArrayList<>();
        for (ServerPlayer candidate :
                player.getServer().getPlayerList().getPlayers()) {
            if (options.size() >= MAX_PLAYER_OPTIONS) {
                break;
            }
            if (!candidate.isAlive()
                    || candidate.isSpectator()
                    || candidate.serverLevel()
                            != player.serverLevel()
                    || candidate.getUUID().equals(
                            target.getUUID())) {
                continue;
            }
            int optionId = options.size();
            optionTargets.putUUID(
                    Integer.toString(optionId),
                    candidate.getUUID());
            options.add(new BookPlayerOption(
                    optionId,
                    candidate.getGameProfile().getName()));
        }
        return List.copyOf(options);
    }

    private static long nextGeneration(CompoundTag persistent) {
        long current =
                persistent.getLong(SESSION_COUNTER_TAG);
        long next = current == Long.MAX_VALUE
                ? 1L
                : Math.max(1L, current + 1L);
        persistent.putLong(SESSION_COUNTER_TAG, next);
        return next;
    }

    @Nullable
    private static Session read(@Nullable ServerPlayer player) {
        if (player == null) {
            return null;
        }
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(SESSION_TAG)) {
            return null;
        }
        CompoundTag tag = persistent.getCompound(SESSION_TAG);
        if (!tag.hasUUID(NONCE_TAG)
                || !tag.hasUUID(CASTER_TAG)
                || !tag.hasUUID(TARGET_TAG)
                || !tag.hasUUID(STAND_TAG)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(DIMENSION_TAG));
        if (dimension == null) {
            return null;
        }
        return new Session(
                tag.getUUID(NONCE_TAG),
                tag.getUUID(CASTER_TAG),
                tag.getUUID(TARGET_TAG),
                tag.getUUID(STAND_TAG),
                tag.getString(STAND_TYPE_TAG),
                dimension,
                tag.getLong(CREATED_AT_TAG),
                tag.getLong(EXPIRES_AT_TAG),
                tag.getLong(GENERATION_TAG),
                tag.getLong(BOOK_GENERATION_TAG),
                tag.getLong(BOOK_EXPIRES_AT_TAG),
                tag.getInt(COMMAND_MASK_TAG),
                tag.getCompound(PLAYER_OPTIONS_TAG));
    }

    public record IssuedSession(
            UUID nonce,
            long generation,
            long expiresAt,
            int commandMask,
            List<BookPlayerOption> playerOptions) {}

    private record Session(
            UUID nonce,
            UUID casterUuid,
            UUID targetUuid,
            UUID standUuid,
            String standType,
            ResourceLocation dimension,
            long createdAt,
            long expiresAt,
            long generation,
            long bookGeneration,
            long bookExpiresAt,
            int commandMask,
            CompoundTag playerOptions) {}
}
