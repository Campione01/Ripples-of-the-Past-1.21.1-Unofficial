package rotp.addons.spawn_with_stand.init;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.addons.spawn_with_stand.RotpSpawnWithStandAddon;
import rotp.addons.spawn_with_stand.SpawnWithStandPolicy;
import rotp.addons.spawn_with_stand.SpawnWithStandPolicy.LifecycleDecision;
import rotp.addons.spawn_with_stand.SpawnWithStandPolicy.Selection;
import rotp.addons.spawn_with_stand.SpawnWithStandPolicy.SelectionKind;
import rotp.addons.spawn_with_stand.SpawnWithStandPolicy.Trigger;
import rotp.addons.spawn_with_stand.config.StandSpawnConfig;
import rotp.addons.spawn_with_stand.config.StandSpawnConfig.Snapshot;
import rotp.addons.spawn_with_stand.util.PlayerDataHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(
        modid = RotpSpawnWithStandAddon.MOD_ID)
public final class ModEvents {
    public static final String GRANTED_MESSAGE_KEY =
            "rotp_ss.stand_granted_pog";

    private ModEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        PlayerDataHelper.get(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerFirstJoin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handleLifecycle(player, Trigger.FIRST_LOGIN);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handleLifecycle(player, Trigger.RESPAWN);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            handleLifecycle(player, Trigger.DIMENSION_REENTRY);
        }
    }

    static AssignmentOutcome handleLifecycle(
            ServerPlayer player, Trigger trigger) {
        PlayerDataHelper data =
                PlayerDataHelper.get(player.getServer());
        StandPower standPower = StandPower.get(player);
        LifecycleDecision decision =
                SpawnWithStandPolicy.lifecycleDecision(
                        trigger,
                        data.hasRolledStand(player.getUUID()),
                        standPower != null
                                && standPower.hasPower());
        if (decision
                != LifecycleDecision.ATTEMPT_FIRST_ROLL) {
            return switch (decision) {
                case PRESERVE_EXISTING_STAND ->
                        AssignmentOutcome.EXISTING_STAND_PRESERVED;
                case ALREADY_ROLLED_NO_REASSIGN ->
                        AssignmentOutcome.ALREADY_ROLLED_NO_REASSIGN;
                case NON_LOGIN_NO_REASSIGN ->
                        AssignmentOutcome.NON_LOGIN_NO_REASSIGN;
                case ATTEMPT_FIRST_ROLL ->
                        throw new IllegalStateException();
            };
        }
        return attemptFirstRoll(player, data);
    }

    private static AssignmentOutcome attemptFirstRoll(
            ServerPlayer player, PlayerDataHelper data) {
        Snapshot config = StandSpawnConfig.snapshot();
        double rollPercent =
                player.getRandom().nextDouble() * 100.0D;
        AssignmentOutcome outcome;
        if (SpawnWithStandPolicy.passesChance(
                config.standChance(), rollPercent)) {
            outcome = giveAssignedStand(
                    player, data, config);
        }
        else {
            outcome = AssignmentOutcome.CHANCE_MISSED;
        }
        data.setHasRolledStand(player.getUUID());
        return outcome;
    }

    private static AssignmentOutcome giveAssignedStand(
            ServerPlayer player,
            PlayerDataHelper data,
            Snapshot config) {
        StandPower standPower = StandPower.get(player);
        if (standPower == null) {
            return AssignmentOutcome.MISSING_STAND_POWER;
        }
        if (standPower.hasPower()) {
            return AssignmentOutcome.EXISTING_STAND_PRESERVED;
        }

        int orderedIndex = config.useStandOrder()
                ? data.getAndIncrementJoinOrder()
                : -1;
        Selection selection =
                SpawnWithStandPolicy.selection(
                        config.useStandOrder(),
                        config.standOrder(),
                        orderedIndex);
        AssignmentOutcome outcome =
                selection.kind() == SelectionKind.CONFIGURED
                        ? giveConfiguredStand(
                                standPower,
                                selection.configuredId())
                        : giveRandomStand(
                                player, standPower);
        if (outcome.granted()) {
            player.sendSystemMessage(
                    Component.translatable(
                            GRANTED_MESSAGE_KEY));
        }
        return outcome;
    }

    private static AssignmentOutcome giveConfiguredStand(
            StandPower standPower,
            String configuredId) {
        ResourceLocation standId =
                ResourceLocation.tryParse(configuredId);
        if (standId == null) {
            return AssignmentOutcome
                    .CONFIGURED_STAND_UNAVAILABLE;
        }
        StandType standType = StandType.fromId(standId);
        if (standType == null) {
            return AssignmentOutcome
                    .CONFIGURED_STAND_UNAVAILABLE;
        }
        return insertStand(
                standPower,
                standType,
                AssignmentOutcome.GRANTED_CONFIGURED);
    }

    private static AssignmentOutcome giveRandomStand(
            ServerPlayer player,
            StandPower standPower) {
        StandType standType = StandUtil
                .randomStandOrError(
                        player,
                        player.getRandom())
                .left()
                .orElse(null);
        if (standType == null) {
            return AssignmentOutcome.RANDOM_POOL_EMPTY;
        }
        return insertStand(
                standPower,
                standType,
                AssignmentOutcome.GRANTED_RANDOM);
    }

    private static AssignmentOutcome insertStand(
            StandPower standPower,
            StandType standType,
            AssignmentOutcome grantedOutcome) {
        StandPowerTransitions.Result result =
                StandPowerTransitions.insert(
                        standPower,
                        new StandInstance(standType));
        if (result.applied()) {
            return grantedOutcome;
        }
        if (result.status()
                == StandPowerTransitions.Status
                        .STAND_ALREADY_PRESENT) {
            return AssignmentOutcome
                    .EXISTING_STAND_PRESERVED;
        }
        return AssignmentOutcome.TRANSITION_REJECTED;
    }

    enum AssignmentOutcome {
        GRANTED_CONFIGURED(true),
        GRANTED_RANDOM(true),
        CHANCE_MISSED(false),
        EXISTING_STAND_PRESERVED(false),
        MISSING_STAND_POWER(false),
        CONFIGURED_STAND_UNAVAILABLE(false),
        RANDOM_POOL_EMPTY(false),
        TRANSITION_REJECTED(false),
        ALREADY_ROLLED_NO_REASSIGN(false),
        NON_LOGIN_NO_REASSIGN(false);

        private final boolean granted;

        AssignmentOutcome(boolean granted) {
            this.granted = granted;
        }

        boolean granted() {
            return granted;
        }
    }
}
