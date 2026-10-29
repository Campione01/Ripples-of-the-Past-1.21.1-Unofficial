package rotp.addons.scr;

import java.util.EnumSet;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.standarrow.StandArrowItem;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.scr.entity.SilverChariotRequiemEntity;
import rotp.addons.scr.service.RequiemSoulService;
import rotp.addons.scr.state.RequiemEvolutionState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = SilverChariotRequiemAddon.MOD_ID)
public final class SilverChariotRequiemEvents {
    private SilverChariotRequiemEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                SilverChariotRequiemAddon
                        .SILVER_CHARIOT_REQUIEM_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        boolean beetleArrow =
                event.getItemStack().getItem()
                        == ModItems.STAND_ARROW_BEETLE.get();
        boolean standArrow =
                event.getItemStack().getItem()
                        instanceof StandArrowItem;
        if (!standArrow) {
            return;
        }

        Player player = event.getEntity();
        StandPower power = StandPower.get(player);
        if (power == null
                || !power.hasPower()
                || power.getPowerType()
                        != ModStands.SILVER_CHARIOT.get()) {
            return;
        }

        boolean fullResolve =
                power.getResolveLevel()
                        >= power.getMaxResolveLevel();
        boolean row32Eligible = beetleArrow && fullResolve;
        boolean row80Eligible =
                power.isSummoned()
                        && power.getResolveLevel() > 3;
        boolean eligible = row32Eligible || row80Eligible;

        if (!beetleArrow && !row80Eligible) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(
                eligible
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
        if (player.level().isClientSide()) {
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !eligible) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_silver_chariot_requiem"
                                    + ".evolution.max_resolve"),
                    true);
            return;
        }

        ResourceLocation triggerItem =
                BuiltInRegistries.ITEM.getKey(
                        event.getItemStack().getItem());
        evolve(
                serverPlayer,
                power,
                event,
                beetleArrow,
                triggerItem);
    }

    private static void evolve(
            ServerPlayer player,
            StandPower power,
            PlayerInteractEvent.RightClickItem event,
            boolean beetleArrow,
            ResourceLocation triggerItem) {
        StandInstance current =
                power.getStandInstance().orElse(null);
        if (current == null
                || current.getStandType()
                        != ModStands.SILVER_CHARIOT.get()) {
            return;
        }

        StandInstance replacement = new StandInstance(
                SilverChariotRequiemAddon
                        .SILVER_CHARIOT_REQUIEM.get());
        EnumSet<StandPart> parts = current.getAllParts();
        for (StandPart part : StandPart.values()) {
            if (!parts.contains(part)) {
                replacement.removePart(part);
            }
        }

        boolean wasSummoned = power.isSummoned();
        StandPowerTransitions.Result result =
                StandPowerTransitions.replace(
                        power,
                        current.getStandId(),
                        replacement);
        if (!result.applied()) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_silver_chariot_requiem"
                                    + ".evolution.failed"),
                    true);
            return;
        }

        event.getItemStack().shrink(1);
        if (wasSummoned) {
            SilverChariotRequiemAddon
                    .SILVER_CHARIOT_REQUIEM.get()
                    .summon(player, power);
        }
        RequiemEvolutionState state =
                SilverChariotRequiemAddon
                        .evolutionState(player);
        state.recordTransition(
                current.getStandId(),
                triggerItem,
                beetleArrow,
                player.level().getGameTime());
        player.displayClientMessage(
                Component.translatable(
                        "rotp_silver_chariot_requiem"
                                + ".evolution.complete"),
                true);
    }

    // A hit while the dark aura is held is the core held-action rule
    // (ChariotDarkAuraAbility opts in).

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectRemoved(
            MobEffectEvent.Remove event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null
                && instance.getEffect().is(
                SilverChariotRequiemAddon
                        .CHARIOT_VIRUS_EFFECT)) {
            grantStandAfterVirus(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null
                && instance.getEffect().is(
                        SilverChariotRequiemAddon
                                .CHARIOT_VIRUS_EFFECT)) {
            grantStandAfterVirus(event.getEntity());
        }
    }

    private static void grantStandAfterVirus(
            LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)
                || !player.isAlive()) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (power != null && !power.hasPower()) {
            StandArrowItem.giveStand(player.level(), player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(
            EntityTickEvent.Post event) {
        if (event.getEntity()
                        instanceof LivingEntity living
                && !(living instanceof StandEntity)
                && !living.level().isClientSide()) {
            RequiemSoulService.tickTarget(living);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(
            LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!living.level().isClientSide()
                && !(living instanceof StandEntity)) {
            RequiemSoulService.clearOnDeath(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RequiemSoulService.clearOnDeparture(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RequiemSoulService.clearOnDeparture(player);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity()
                instanceof SilverChariotRequiemEntity stand) {
            RequiemSoulService.clearBySource(stand, level);
        }
        else if (event.getEntity()
                        instanceof LivingEntity living
                && !(living instanceof StandEntity)) {
            RequiemSoulService.clearOnDeparture(living, level);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity()
                        instanceof ServerPlayer replacement)
                || !(event.getOriginal()
                        instanceof ServerPlayer original)) {
            return;
        }
        SilverChariotRequiemAddon
                .evolutionState(replacement)
                .copyFrom(SilverChariotRequiemAddon
                        .evolutionState(original));
        if (!event.isWasDeath()) {
            SilverChariotRequiemAddon
                    .soulState(replacement)
                    .copyFrom(SilverChariotRequiemAddon
                            .soulState(original));
        }
    }
}
