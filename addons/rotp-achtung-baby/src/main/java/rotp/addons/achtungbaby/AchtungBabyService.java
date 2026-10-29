package rotp.addons.achtungbaby;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.PacketDistributor;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.standpower.StandPower;
import rotp.addons.achtungbaby.network.AchtungBabyStatePayload;
import rotp.addons.achtungbaby.state.AchtungBabyState;

public final class AchtungBabyService {
    private static final String ITEM_INVISIBLE =
            "AchtungBabyInvisible";

    private AchtungBabyService() {}

    public static boolean isInvisible(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null
                && data.copyTag().getBoolean(ITEM_INVISIBLE);
    }

    /**
     * Whether this entity is currently in ghost mode.
     *
     * <p>Ghost mode puts the player in {@link GameType#SPECTATOR}, which hands them the whole vanilla spectator kit.
     * 1.16's add-on took three pieces of it back away (the spectator hotbar, container screens and spectator
     * teleport), and those restrictions all ask this one question - on either side, since the state rides to the
     * client on {@link AchtungBabyStatePayload}.
     */
    public static boolean isGhost(@Nullable LivingEntity entity) {
        return entity != null && AchtungBabyAddon.state(entity).ghostMode();
    }

    /**
     * Remembers that this position holds a block placed from an invisible item.
     *
     * <p>No dirty flag to set, unlike 1.16's {@code WorldSavedData.setDirty()}: NeoForge backs level attachments with
     * a {@code SavedData} whose {@code isDirty()} is hardcoded true, so they are re-saved with the level regardless.
     */
    public static void markBlockInvisible(ServerLevel level, BlockPos pos) {
        level.getData(AchtungBabyAddon.INVISIBLE_BLOCKS).add(pos);
    }

    /**
     * Forgets this position and says whether it was remembered, so the caller can hand the invisibility back to
     * whatever the block dropped.
     */
    public static boolean takeBlockInvisible(ServerLevel level, BlockPos pos) {
        return level.getData(AchtungBabyAddon.INVISIBLE_BLOCKS).remove(pos);
    }

    /** Marks a stack invisible without toggling, for handing the tag back to a broken block's drops. */
    public static void setInvisible(ItemStack stack) {
        if (!stack.isEmpty() && !isInvisible(stack)) {
            CustomData.update(
                    DataComponents.CUSTOM_DATA,
                    stack,
                    tag -> tag.putBoolean(ITEM_INVISIBLE, true));
        }
    }

    public static boolean toggleItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        boolean next = !isInvisible(stack);
        CustomData.update(
                DataComponents.CUSTOM_DATA,
                stack,
                tag -> {
                    if (next) {
                        tag.putBoolean(ITEM_INVISIBLE, true);
                    } else {
                        tag.remove(ITEM_INVISIBLE);
                    }
                });
        return next;
    }

    /**
     * Stamina, restored from 1.16's actions: Active Invisibility a fifth of maximum stamina, Grant Invisibility a
     * tenth, Item Invisibility a twentieth per item. Each checked its own fraction and refused silently below it,
     * with no Resolve-mode allowance.
     */
    public static final float ACTIVE_STAMINA_FRACTION = 0.2F;
    public static final float GRANT_STAMINA_FRACTION = 0.1F;
    private static final float ITEM_STAMINA_FRACTION = 0.05F;

    /** 1.16's NEGATIVE condition: a Stand power below this fraction of its maximum stamina. */
    public static boolean lacksStamina(@Nullable Power<?> context, float fraction) {
        return context instanceof StandPower power
                && power.getStamina() < power.getMaxStamina() * fraction;
    }

    /** Pays this fraction of maximum stamina; with less than that, pays nothing and returns false. */
    public static boolean payStamina(LivingEntity user, float fraction) {
        StandPower power = StandPower.get(user);
        if (power == null) {
            return false;
        }
        float cost = power.getMaxStamina() * fraction;
        if (power.getStamina() < cost) {
            return false;
        }
        power.consumeStamina(cost);
        return true;
    }

    /**
     * 1.16 ItemInvisibilityAction.convertToInvisibleItem: only as many items as the stamina covers are split off the
     * stack in this hand, paid for and made invisible. The whole stack stays in the hand; a part goes to the
     * inventory, and whatever does not fit is dropped (1.16 lost a remainder the inventory only partly took).
     *
     * @return whether anything was made invisible; with too little stamina for one item, nothing happens
     */
    public static boolean makeInvisible(LivingEntity user, InteractionHand hand) {
        StandPower power = StandPower.get(user);
        ItemStack stack = user.getItemInHand(hand);
        if (power == null || stack.isEmpty()) {
            return false;
        }
        float maxStamina = power.getMaxStamina();
        int affordable = (int) (power.getStamina()
                / (maxStamina * ITEM_STAMINA_FRACTION));
        int count = Math.min(stack.getCount(), affordable);
        if (count <= 0) {
            return false;
        }
        power.consumeStamina(count * maxStamina * ITEM_STAMINA_FRACTION);
        ItemStack invisible = stack.split(count);
        setInvisible(invisible);
        if (stack.isEmpty()) {
            user.setItemInHand(hand, invisible);
            return true;
        }
        if (user instanceof Player player) {
            player.getInventory().add(invisible);
        }
        if (!invisible.isEmpty()) {
            user.spawnAtLocation(invisible);
        }
        return true;
    }

    /**
     * Grant Invisibility's rate limit, restored from 1.16's GrantInvisibilityAction: six uses, each within 200 ticks
     * of the one before, lock it for 200 ticks. Kept in the player's persistent data under 1.16's tag names.
     */
    private static final String GRANT_USES_TAG =
            "achtung_baby_grant_invisibility_uses";
    private static final String GRANT_LAST_USE_TAG =
            "achtung_baby_grant_invisibility_last_use";
    private static final String GRANT_PENALTY_TAG =
            "achtung_baby_grant_invisibility_penalty";
    private static final long GRANT_USE_WINDOW_TICKS = 200L;
    private static final int GRANT_MAX_USES = 6;
    private static final long GRANT_PENALTY_TICKS = 200L;

    public static void countGrantUse(Player player) {
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();
        int uses = data.getInt(GRANT_USES_TAG);
        if (now - data.getLong(GRANT_LAST_USE_TAG) > GRANT_USE_WINDOW_TICKS) {
            uses = 0;
        }
        data.putInt(GRANT_USES_TAG, ++uses);
        data.putLong(GRANT_LAST_USE_TAG, now);
        if (uses >= GRANT_MAX_USES) {
            data.putLong(GRANT_PENALTY_TAG, now + GRANT_PENALTY_TICKS);
            data.putInt(GRANT_USES_TAG, 0);
        }
    }

    /** Ticks left in Grant Invisibility's lockout, 0 when none is running. */
    public static int grantPenaltyTicksLeft(Player player) {
        long left = player.getPersistentData().getLong(GRANT_PENALTY_TAG)
                - player.level().getGameTime();
        return left > 0L ? (int) left : 0;
    }

    /**
     * Perfect Invisibility, 1.16 PerfectInvisibilityAction + PerfectInvisibilityManager: a flag saved with the player
     * that only the toggle switches off. It survives logout, login and a server restart; death ends it, because 1.16
     * kept it in data that is not copied to the respawned player.
     *
     * <p>1.16 gave a 999999-tick Invisibility and removed whatever Invisibility the player had on toggling off. The
     * port keeps a short effect topped up from the saved flag instead, so it can tell its own effect from a potion's.
     */
    private static final int PERFECT_EFFECT_TICKS = 10;

    public static void togglePerfect(ServerPlayer player) {
        var state = AchtungBabyAddon.state(player);
        state.setPerfectInvisible(!state.perfectInvisible());
        if (!state.perfectInvisible()) {
            removeOwnInvisibility(player);
        }
        sync(player);
    }

    /** Leaves alone an Invisibility that outlasts the top-up: a potion, a rune, another mod. */
    private static void removeOwnInvisibility(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (current != null && current.endsWithin(PERFECT_EFFECT_TICKS)) {
            player.removeEffect(MobEffects.INVISIBILITY);
        }
    }

    /**
     * Ghost mode's cost and limits, restored from 1.16's GhostModeAction + GhostModeManager.
     *
     * <p>The port had turned ghost mode into a free, unbounded hold. 1.16 charged an eighth of maximum stamina to
     * enter and the same again every second, cut it off at a minute, and locked it out for twenty seconds if the
     * upkeep ever went unpaid.
     */
    private static final float GHOST_STAMINA_FRACTION = 0.125F;
    private static final int GHOST_UPKEEP_INTERVAL = 20;
    private static final int GHOST_MAX_TICKS = 1200;
    private static final int GHOST_PENALTY_TICKS = 400;

    /**
     * 1.16 GhostModeAction.checkSpecificConditions: the unpaid-upkeep lockout, or less than an eighth of max stamina.
     * The lockout is not synced, so on the client only the stamina half can refuse - as with 1.16's persistent data.
     */
    public static boolean ghostRefused(@Nullable Power<?> context) {
        LivingEntity user = context != null ? context.getUser() : null;
        return user != null
                && (user.level().getGameTime()
                                < AchtungBabyAddon.state(user).ghostPenaltyUntil()
                        || lacksStamina(context, GHOST_STAMINA_FRACTION));
    }

    /**
     * Starts ghost mode if it is affordable and not locked out; toggles it back off if it is already running.
     *
     * @return whether the call changed anything, so a refusal can stay silent the way 1.16's did
     */
    public static boolean toggleGhost(ServerPlayer player) {
        var state = AchtungBabyAddon.state(player);
        if (state.ghostMode()) {
            endGhost(player);
            return true;
        }
        long now = player.level().getGameTime();
        if (now < state.ghostPenaltyUntil()) {
            return false;
        }
        state.setGhostPenaltyUntil(0L);
        StandPower power = StandPower.get(player);
        if (power == null) {
            return false;
        }
        float cost = power.getMaxStamina() * GHOST_STAMINA_FRACTION;
        if (power.getStamina() < cost || !power.consumeStamina(cost)) {
            return false;
        }
        state.beginGhostMode(
                player.gameMode.getGameModeForPlayer().getId(), now);
        player.setGameMode(GameType.SPECTATOR);
        sync(player);
        return true;
    }

    public static void beginGhost(ServerPlayer player) {
        toggleGhost(player);
    }

    public static void endGhost(ServerPlayer player) {
        var state = AchtungBabyAddon.state(player);
        if (!state.ghostMode()) {
            return;
        }
        int original = state.originalGameMode();
        state.endGhostMode();
        player.setGameMode(GameType.byId(
                original >= 0 ? original : GameType.SURVIVAL.getId()));
        sync(player);
    }

    public static void tick(ServerPlayer player) {
        var state = AchtungBabyAddon.state(player);
        // Spectator mode unsummons the actor. Keep the ghost lifetime independent
        // of that actor; tickGhost checks the actual Stand type and exit conditions.
        if (state.ghostMode()) {
            tickGhost(player, state);
            return;
        }
        if (!AchtungBabyAddon.isUser(player)) {
            if (state.perfectInvisible()) {
                cleanup(player);
            }
            return;
        }
        if (state.perfectInvisible()) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.INVISIBILITY,
                    PERFECT_EFFECT_TICKS,
                    0,
                    false,
                    false,
                    false));
        }
        if (state.ghostMode()) {
            tickGhost(player, state);
        }
    }

    /**
     * Keeps ghost mode paid for, and ends it when it is not.
     *
     * <p>Restore the saved game mode when the Stand changes, spectator mode ends,
     * the one-minute limit is reached, or stamina upkeep cannot be paid.
     */
    private static void tickGhost(
            ServerPlayer player, AchtungBabyState state) {
        long elapsed = player.level().getGameTime() - state.ghostStartTick();
        if (!AchtungBabyAddon.isUser(player)
                || !player.isSpectator() || elapsed >= GHOST_MAX_TICKS) {
            endGhost(player);
            return;
        }
        if (elapsed <= 0 || elapsed % GHOST_UPKEEP_INTERVAL != 0) {
            return;
        }
        StandPower power = StandPower.get(player);
        float cost = power == null
                ? Float.MAX_VALUE
                : power.getMaxStamina() * GHOST_STAMINA_FRACTION;
        if (power == null || power.getStamina() < cost
                || !power.consumeStamina(cost)) {
            state.setGhostPenaltyUntil(
                    player.level().getGameTime() + GHOST_PENALTY_TICKS);
            endGhost(player);
        }
    }

    /**
     * Ends both modes: death, respawn, and no longer having Achtung Baby. Retracting the Stand ends neither; logout
     * and login only end Ghost mode.
     */
    public static void cleanup(ServerPlayer player) {
        var state = AchtungBabyAddon.state(player);
        int original = state.originalGameMode();
        boolean wasGhost = state.ghostMode();
        if (!state.clear()) {
            return;
        }
        if (wasGhost) {
            player.setGameMode(GameType.byId(
                    original >= 0
                            ? original
                            : GameType.SURVIVAL.getId()));
        }
        removeOwnInvisibility(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                AchtungBabyStatePayload.from(player));
    }
}
