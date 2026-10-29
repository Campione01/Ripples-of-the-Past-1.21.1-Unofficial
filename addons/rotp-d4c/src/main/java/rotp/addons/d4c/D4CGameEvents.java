package rotp.addons.d4c;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.subsystems.timestop.TimeStopState;
import rotp.addons.d4c.travel.D4CParallelTerrainMirror;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(
        modid = D4CAddon.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class D4CGameEvents {
    private D4CGameEvents() {}

    @SubscribeEvent
    public static void extendHeartBearerTimeStop(
            TimeStopLifecycleEvent.PreStart event) {
        TimeStopState.Instance instance = event.getInstance();
        Entity owner = event.getLevel().getEntity(instance.userId());
        if (!(owner instanceof ServerPlayer player)
                || !D4CCorpseService.hasHeartOrComplete(player)) {
            return;
        }

        int totalTicks = instance.totalTicks();
        int newTotalTicks = Math.min(
                TimeStopLifecycleEvent.MAX_TOTAL_TICKS,
                totalTicks + D4CPhase2ASpec.TIME_STOP_HEART_BONUS_TICKS);
        int addedTicks = newTotalTicks - totalTicks;
        if (addedTicks <= 0) {
            return;
        }

        int newTicksLeft = Math.min(
                newTotalTicks,
                instance.ticksLeft() + addedTicks);
        event.setInstance(instance.withTiming(
                newTicksLeft,
                newTotalTicks));
    }

    static boolean hasSaintCorpseHeart(ServerPlayer player) {
        ItemStack heart = new ItemStack(D4CAddon.SAINT_CORPSE_HEART.get());
        if (player.getInventory().contains(heart)) {
            return true;
        }
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(
                        D4CAddon.SAINT_CORPSE_HEART.get()))
                .isPresent();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            D4CCorpseService.tickPlayer(player);
            D4CParallelItemService.tickPlayer(player);
            D4CSaintCorpseXrayService.tickPlayer(player);
            D4CCorpseFinderService.tickPlayer(player);
            D4CPhase2BService.syncMarkToClient(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(
            PlayerEvent.PlayerLoggedOutEvent event) {
        D4CPhase2BService.forgetSentMark(event.getEntity().getUUID());
        D4CPhase2BService.forgetMark(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemPickupAttempt(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            D4CSaintCorpseAwakeningService.onPickupAttempt(
                    player, event.getItemEntity());
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide()) {
            D4CParallelItemService.markPlayerTossed(
                    event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onItemJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && event.getEntity()
                        instanceof net.minecraft.world.entity.item
                                .ItemEntity item) {
            D4CParallelItemService.onItemJoined(level, item);
        }
    }

    @SubscribeEvent
    public static void onParallelItemPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
                && (D4CParallelItemService.isParallel(
                                player.getMainHandItem())
                        || D4CParallelItemService.isParallel(
                                player.getOffhandItem()))) {
            event.setCanceled(true);
            notifyParallelItemRestricted(player);
        }
    }

    @SubscribeEvent
    public static void onParallelItemRightClick(
            PlayerInteractEvent.RightClickItem event) {
        if (D4CParallelItemService.isParallel(
                event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            notifyParallelItemRestricted(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onParallelItemRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (D4CParallelItemService.isParallel(
                event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            notifyParallelItemRestricted(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onParallelItemUseStart(
            LivingEntityUseItemEvent.Start event) {
        if (D4CParallelItemService.isParallel(event.getItem())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                notifyParallelItemRestricted(player);
            }
        }
    }

    @SubscribeEvent
    public static void onParallelItemCrafted(
            PlayerEvent.ItemCraftedEvent event) {
        for (int slot = 0;
                slot < event.getInventory().getContainerSize();
                slot++) {
            if (!D4CParallelItemService.isParallel(
                    event.getInventory().getItem(slot))) {
                continue;
            }
            event.getCrafting().shrink(
                    event.getCrafting().getCount());
            if (event.getEntity() instanceof Player player) {
                notifyParallelItemRestricted(player);
            }
            return;
        }
    }

    @SubscribeEvent
    public static void onParallelItemFuel(
            FurnaceFuelBurnTimeEvent event) {
        if (D4CParallelItemService.isParallel(
                event.getItemStack())) {
            event.setBurnTime(0);
        }
    }

    @SubscribeEvent
    public static void onParallelItemContainerOpen(
            PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            if (D4CParallelItemService.isParallel(
                    player.getInventory().getItem(slot))) {
                notifyParallelItemRestricted(player);
                return;
            }
        }
    }

    private static void notifyParallelItemRestricted(Player player) {
        if (!player.level().isClientSide()) {
            player.displayClientMessage(
                    Component.translatable(
                            "rotp_d4c.message.parallel_item_restricted"),
                    true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        D4CParallelItemService.tick(event.getServer());
        D4CCorpseFinderService.tickServer(event.getServer());
        D4CParallelTerrainMirror.tick(event.getServer());
        D4CPhase2BService.tickMarks(event.getServer());
    }

    // 1.16 am.onPlayerChangedDimension: overworld -> parallel copies terrain
    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            D4CParallelTerrainMirror.onPlayerChangedDimension(
                    player, event.getFrom(), event.getTo());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        D4CParallelItemService.clearRuntimeState();
        D4CCorpseFinderService.clearRuntimeState();
        D4CParallelTerrainMirror.clear();
        D4CPhase2BService.clearSentMarks();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        D4CCorpseService.modifyIncomingDamage(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobEffectApplicable(
            MobEffectEvent.Applicable event) {
        if (D4CRuntimeRules.blockLoveTrainEffect(
                event.getEntity().hasEffect(D4CAddon.LOVE_TRAIN),
                event.getEffectInstance()
                                .getEffect()
                                .value()
                                .getCategory()
                        == MobEffectCategory.HARMFUL)) {
            event.setResult(
                    MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKnockBack(
            LivingKnockBackEvent event) {
        if (event.getEntity().hasEffect(D4CAddon.LOVE_TRAIN)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionDetonate(
            ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(
                entity -> entity instanceof LivingEntity living
                        && living.hasEffect(D4CAddon.LOVE_TRAIN));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFall(LivingFallEvent event) {
        if (D4CCorpseService.hasLegs(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        D4CParallelItemService.recordRelevantDeath(
                event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player
                && D4CPhase2BService.ownsLoveTrainCorpse(player)) {
            D4CPhase2BService.returnLoveTrainCorpse(player);
        }
    }
}
