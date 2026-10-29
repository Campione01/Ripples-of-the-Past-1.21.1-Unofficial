package rotp.addons.greenday;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.mechanics.standdisc.StandDiscItem;

import rotp.addons.greenday.entity.GreenDayStandEntity;
import rotp.addons.greenday.state.GreenDayMoldState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = GreenDayAddon.MOD_ID)
public final class GreenDayGameEvents {
    private GreenDayGameEvents() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity()
                instanceof LivingEntity living)
                || !(living.level()
                        instanceof ServerLevel level)) {
            return;
        }

        StandPower power = StandPower.get(living);
        boolean hasGreenDay = power != null
                && power.getPowerType()
                        == GreenDayAddon.GREEN_DAY.get();
        if (!hasGreenDay) {
            clearDetachedArmState(living);
        }
        if (!living.hasData(GreenDayAddon.MOLD_STATE)) {
            return;
        }
        GreenDayMoldState state =
                GreenDayMoldState.get(living);
        state.tickServer(level, living);
        if (state.moldActive()) {
            if (!hasGreenDay) {
                state.setMoldActive(living, false);
                GreenDayMoldState.removeSourceFromLevel(
                        level, living);
                return;
            }
            GreenDayStandEntity.tickMoldField(
                    level, living, power);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (!(living.level() instanceof ServerLevel level)) {
            return;
        }
        GreenDayMoldState.removeDeadSourceFromLevel(
                level, living);
        if (living.hasData(GreenDayAddon.MOLD_STATE)) {
            GreenDayMoldState.get(living)
                    .clearAll(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanupPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity()
                instanceof ServerPlayer player)
                || player.getServer() == null) {
            return;
        }
        ServerLevel previousLevel =
                player.getServer().getLevel(event.getFrom());
        if (previousLevel != null) {
            GreenDayMoldState.removeSourceFromLevel(
                    previousLevel, player);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(
            PlayerEvent.BreakSpeed event) {
        if (dominantArmMissing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(
            ItemEntityPickupEvent.Pre event) {
        if (bothArmsMissing(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
                && armMissing(
                        player,
                        armForHand(
                                player,
                                player.getUsedItemHand()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(
            AttackEntityEvent event) {
        if (dominantArmMissing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // 1.16 GameplayHandler.onEntityAttack (LivingAttackEvent): every hit
    // whose attacker has the dominant arm out is cancelled, the hits of the
    // flying arms themselves included.
    @SubscribeEvent
    public static void onLivingIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity()
                instanceof LivingEntity attacker
                && dominantArmMissing(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem()
                instanceof StandDiscItem) {
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND
                && dominantArmMissing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem()
                instanceof StandDiscItem) {
            return;
        }
        if (bothArmsMissing(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static boolean dominantArmMissing(
            LivingEntity entity) {
        return armMissing(entity, entity.getMainArm());
    }

    public static boolean bothArmsMissing(
            LivingEntity entity) {
        return entity.hasEffect(GreenDayAddon.LEFT_ARMLESS)
                && entity.hasEffect(
                        GreenDayAddon.RIGHT_ARMLESS);
    }

    public static boolean armMissing(
            LivingEntity entity, HumanoidArm arm) {
        return entity.hasEffect(
                arm == HumanoidArm.RIGHT
                        ? GreenDayAddon.RIGHT_ARMLESS
                        : GreenDayAddon.LEFT_ARMLESS);
    }

    private static HumanoidArm armForHand(
            LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND
                ? entity.getMainArm()
                : entity.getMainArm().getOpposite();
    }

    private static void clearDetachedArmState(
            LivingEntity living) {
        living.removeEffect(GreenDayAddon.LEFT_ARMLESS);
        living.removeEffect(GreenDayAddon.RIGHT_ARMLESS);
    }

    private static void cleanupPlayer(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        GreenDayMoldState.removeSourceFromLevel(
                level, player);
        if (player.hasData(GreenDayAddon.MOLD_STATE)) {
            GreenDayMoldState.get(player)
                    .clearAll(player);
        }
    }
}
