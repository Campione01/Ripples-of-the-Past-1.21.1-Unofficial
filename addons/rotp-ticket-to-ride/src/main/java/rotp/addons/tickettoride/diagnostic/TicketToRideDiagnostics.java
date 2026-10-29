package rotp.addons.tickettoride.diagnostic;

import java.util.UUID;

import javax.annotation.Nullable;

import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.ability.RefillAmmoAbility;
import rotp.addons.tickettoride.entity.TicketToRideStandEntity;
import rotp.addons.tickettoride.item.TearsBladeData;
import rotp.addons.tickettoride.item.TearsBladeItem;
import rotp.addons.tickettoride.state.LoveTrainService;
import rotp.addons.tickettoride.state.LoveTrainState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Read-only production state for automated client acceptance.
 */
public final class TicketToRideDiagnostics {
    public static final String STAND_TYPE_ID =
            "rotp_ttr:ticket_to_ride";
    public static final String CONTROLLER_ENTITY_ID =
            "rotp_ttr:ticket_to_ride";
    public static final String TEARS_BLADE_ITEM_ID =
            "rotp_ttr:tearsblade";
    public static final String ITEM_FILL_ACTION_ID = "item_fill";
    public static final String LOVE_TRAIN_ACTION_ID = "lovetrain";
    public static final String REMOVE_LOVE_TRAIN_ACTION_ID =
            "removel";

    private TicketToRideDiagnostics() {}

    public static Snapshot snapshot(
            @Nullable LivingEntity user) {
        if (user == null) {
            return Snapshot.empty();
        }

        long gameTime = user.level().getGameTime();
        StandPower power = StandPower.get(user);
        TicketToRideStandEntity controller =
                TicketToRideAddon.controller(user);
        EntityActionInstance action = controller != null
                ? controller.getCurStandAction()
                : null;
        String activeActionId = actionId(action);

        BladeSnapshot mainHand = blade(
                user.getMainHandItem(), user, controller);
        BladeSnapshot offHand = blade(
                user.getOffhandItem(), user, controller);
        boolean anyBlade = mainHand.present()
                || offHand.present();
        boolean allBladesFull = anyBlade
                && (!mainHand.present() || mainHand.ammoFull())
                && (!offHand.present() || offHand.ammoFull());

        LoveTrainSnapshot loveTrain =
                loveTrain(user, gameTime);
        return new Snapshot(
                true,
                user.getUUID(),
                user.level().isClientSide()
                        ? "client" : "server",
                gameTime,
                STAND_TYPE_ID,
                CONTROLLER_ENTITY_ID,
                controller != null,
                controller != null ? controller.getId() : -1,
                controller != null
                        ? controller.getUUID() : null,
                activeActionId,
                action != null && action.getPhase() != null
                        ? action.getPhase().name() : "IDLE",
                action != null ? action.getPhaseTick() : -1.0F,
                action != null
                        ? action.getPhaseTicksLeft() : -1.0F,
                action(
                        ITEM_FILL_ACTION_ID,
                        "HOLD",
                        RefillAmmoAbility.HOLD_TO_FIRE_TICKS,
                        0,
                        power,
                        action,
                        activeActionId),
                action(
                        LOVE_TRAIN_ACTION_ID,
                        "HOLD",
                        50,
                        1200,
                        power,
                        action,
                        activeActionId),
                action(
                        REMOVE_LOVE_TRAIN_ACTION_ID,
                        "CLICK",
                        0,
                        0,
                        power,
                        action,
                        activeActionId),
                mainHand,
                offHand,
                anyBlade,
                mainHand.refillable() || offHand.refillable(),
                allBladesFull,
                loveTrain);
    }

    private static String actionId(
            @Nullable EntityActionInstance action) {
        return action != null
                && action.ability != null
                && action.ability.getAbilityId() != null
                ? action.ability.getAbilityId().nameInMoveset()
                : "";
    }

    private static ActionSnapshot action(
            String id,
            String inputMethod,
            int requiredHoldTicks,
            int configuredSuccessCooldown,
            @Nullable StandPower power,
            @Nullable EntityActionInstance action,
            String activeActionId) {
        boolean actionMatches = id.equals(activeActionId)
                && action != null;
        @Nullable ActionPhase activePhase = actionMatches
                ? action.getPhase() : null;
        boolean active = activePhase != null;
        String phase = active
                ? activePhase.name() : "IDLE";
        float phaseTick = active
                ? action.getPhaseTick() : -1.0F;
        float phaseTicksLeft = active
                ? action.getPhaseTicksLeft() : -1.0F;
        return new ActionSnapshot(
                id,
                inputMethod,
                requiredHoldTicks,
                configuredSuccessCooldown,
                active,
                phase,
                phaseTick,
                phaseTicksLeft,
                active
                        && (activePhase != ActionPhase.WINDUP
                                || phaseTick >= requiredHoldTicks),
                power != null
                        ? power.getAbilityCooldown(id) : 0);
    }

    private static BladeSnapshot blade(
            ItemStack stack,
            LivingEntity user,
            @Nullable TicketToRideStandEntity controller) {
        String itemId = BuiltInRegistries.ITEM.getKey(
                stack.getItem()).toString();
        if (!stack.is(TicketToRideAddon.TEARS_BLADE.get())) {
            return new BladeSnapshot(
                    false,
                    itemId,
                    false,
                    null,
                    false,
                    false,
                    -1,
                    false,
                    false);
        }
        TearsBladeData data = stack.get(
                TicketToRideAddon.TEARS_BLADE_DATA.get());
        boolean ownerMatches = data != null
                && data.ownerUuid().equals(user.getUUID());
        int ammo = data != null ? data.ammo() : -1;
        boolean activeOwned = ownerMatches && controller != null;
        return new BladeSnapshot(
                true,
                itemId,
                data != null,
                data != null ? data.ownerUuid() : null,
                ownerMatches,
                activeOwned,
                ammo,
                ammo == TearsBladeItem.MAX_AMMO,
                activeOwned
                        && ammo >= 0
                        && ammo < TearsBladeItem.MAX_AMMO);
    }

    private static LoveTrainSnapshot loveTrain(
            LivingEntity user, long gameTime) {
        MobEffectInstance effect = user.getEffect(
                TicketToRideAddon.LOVE_TRAIN);
        LoveTrainState state = user.getExistingDataOrNull(
                TicketToRideAddon.LOVE_TRAIN_STATE);
        long startedAt = state != null ? state.startedAt() : 0L;
        long expiresAt = state != null ? state.expiresAt() : 0L;
        boolean stateActive = state != null
                && state.active(gameTime);
        boolean serviceActive = effect != null
                && (user.level().isClientSide() || stateActive);
        long elapsed = startedAt > 0L
                ? Math.max(0L, gameTime - startedAt)
                : effect != null
                        ? Math.max(
                                0,
                                LoveTrainService.DURATION_TICKS
                                        - effect.getDuration())
                        : 0L;
        float radius = Mth.clamp(
                LoveTrainService.INITIAL_RADIUS
                        + elapsed
                                * LoveTrainService.RADIUS_PER_TICK,
                LoveTrainService.INITIAL_RADIUS,
                LoveTrainService.MAX_RADIUS);
        boolean stateCleared = state == null
                || startedAt == 0L && expiresAt == 0L;
        return new LoveTrainSnapshot(
                effect != null,
                effect != null ? effect.getDuration() : 0,
                effect != null ? effect.getAmplifier() : -1,
                state != null,
                startedAt,
                expiresAt,
                stateActive,
                stateCleared,
                serviceActive,
                radius,
                !user.hasEffect(
                        TicketToRideAddon.SHARED_LOVE_TRAIN),
                effect == null && stateCleared);
    }

    public record Snapshot(
            boolean userPresent,
            @Nullable UUID userUuid,
            String logicalSide,
            long gameTime,
            String standTypeId,
            String controllerEntityId,
            boolean controllerPresent,
            int controllerNetworkId,
            @Nullable UUID controllerUuid,
            String activeActionId,
            String activeActionPhase,
            float activeActionPhaseTick,
            float activeActionPhaseTicksLeft,
            ActionSnapshot itemFill,
            ActionSnapshot loveTrainAction,
            ActionSnapshot removeLoveTrainAction,
            BladeSnapshot mainHandBlade,
            BladeSnapshot offHandBlade,
            boolean anyBladePresent,
            boolean anyBladeRefillable,
            boolean allHeldBladesFull,
            LoveTrainSnapshot loveTrain) {
        private static Snapshot empty() {
            ActionSnapshot itemFill = ActionSnapshot.empty(
                    ITEM_FILL_ACTION_ID,
                    "HOLD",
                    RefillAmmoAbility.HOLD_TO_FIRE_TICKS,
                    0);
            ActionSnapshot loveTrain = ActionSnapshot.empty(
                    LOVE_TRAIN_ACTION_ID,
                    "HOLD",
                    50,
                    1200);
            ActionSnapshot removeLoveTrain = ActionSnapshot.empty(
                    REMOVE_LOVE_TRAIN_ACTION_ID,
                    "CLICK",
                    0,
                    0);
            return new Snapshot(
                    false,
                    null,
                    "unavailable",
                    -1L,
                    STAND_TYPE_ID,
                    CONTROLLER_ENTITY_ID,
                    false,
                    -1,
                    null,
                    "",
                    "IDLE",
                    -1.0F,
                    -1.0F,
                    itemFill,
                    loveTrain,
                    removeLoveTrain,
                    BladeSnapshot.empty(),
                    BladeSnapshot.empty(),
                    false,
                    false,
                    false,
                    LoveTrainSnapshot.empty());
        }

        public boolean itemFillSuccessPostcondition() {
            return anyBladePresent && allHeldBladesFull;
        }

        public boolean itemFillIdleWithoutCooldown() {
            return !itemFill.active()
                    && itemFill.cooldownRemaining() == 0;
        }

        public boolean loveTrainSuccessPostcondition() {
            return loveTrain.serviceActive()
                    && loveTrainAction.cooldownRemaining() > 0;
        }
    }

    public record ActionSnapshot(
            String nameInMoveset,
            String inputMethod,
            int requiredHoldTicks,
            int configuredSuccessCooldown,
            boolean active,
            String phase,
            float phaseTick,
            float phaseTicksLeft,
            boolean holdThresholdReached,
            int cooldownRemaining) {
        private static ActionSnapshot empty(
                String id,
                String input,
                int holdTicks,
                int cooldown) {
            return new ActionSnapshot(
                    id,
                    input,
                    holdTicks,
                    cooldown,
                    false,
                    "IDLE",
                    -1.0F,
                    -1.0F,
                    false,
                    0);
        }
    }

    public record BladeSnapshot(
            boolean present,
            String itemId,
            boolean componentPresent,
            @Nullable UUID ownerUuid,
            boolean ownerMatchesUser,
            boolean activeOwned,
            int ammo,
            boolean ammoFull,
            boolean refillable) {
        private static BladeSnapshot empty() {
            return new BladeSnapshot(
                    false,
                    "minecraft:air",
                    false,
                    null,
                    false,
                    false,
                    -1,
                    false,
                    false);
        }
    }

    public record LoveTrainSnapshot(
            boolean effectPresent,
            int effectDuration,
            int effectAmplifier,
            boolean attachmentAllocated,
            long stateStartedAt,
            long stateExpiresAt,
            boolean stateActive,
            boolean stateCleared,
            boolean serviceActive,
            float radius,
            boolean sharedEffectAbsent,
            boolean cleanupComplete) {
        private static LoveTrainSnapshot empty() {
            return new LoveTrainSnapshot(
                    false,
                    0,
                    -1,
                    false,
                    0L,
                    0L,
                    false,
                    true,
                    false,
                    LoveTrainService.INITIAL_RADIUS,
                    true,
                    true);
        }
    }
}
