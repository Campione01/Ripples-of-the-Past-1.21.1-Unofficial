package rotp.addons.kingcrimson;

import java.util.LinkedHashSet;
import java.util.Set;

import rotp.core.init.ModParticles;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.StandUtil;

import rotp.addons.kingcrimson.effect.KingCrimsonGapingWoundEffect;
import rotp.addons.kingcrimson.state.EpitaphForesightService;
import rotp.addons.kingcrimson.state.EpitaphStateService;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = KingCrimsonAddon.MOD_ID)
public final class KingCrimsonGameEvents {
    private KingCrimsonGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                KingCrimsonAddon.KING_CRIMSON_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                KingCrimsonAddon.TIME_ERASE_DECOY_ENTITY.get(),
                net.minecraft.world.entity.Mob.createMobAttributes()
                        .add(Attributes.MAX_HEALTH, 20.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.0D)
                        .add(Attributes.FOLLOW_RANGE, 16.0D)
                        .build());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        // Stand melee bypasses the vanilla player's AttackEntityEvent.
        // Already-flying projectiles retain their own timeline and do not end the erase.
        if (event.getSource().getDirectEntity() instanceof LivingEntity direct
                && StandUtil.getStandUser(direct) instanceof ServerPlayer attacker) {
            KingCrimsonTemporalStateService.terminateOnInteraction(attacker);
        }

        if (event.getEntity() instanceof ServerPlayer player
                && KingCrimsonTemporalStateService.blocksDamage(
                        player, event.getSource())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof ServerPlayer player
                && EpitaphStateService.tryDodge(
                        player, event.getSource())) {
            event.setCanceled(true);
            return;
        }

        if (event.getSource().getEntity() instanceof Player attacker
                && attacker.hasEffect(
                        KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanceled(true);
            return;
        }

        LivingEntity target = event.getEntity();
        MobEffectInstance gapingWound =
                target.getEffect(KingCrimsonAddon.GAPING_WOUND);
        if (gapingWound == null) {
            return;
        }

        int amplifier = Math.min(
                gapingWound.getAmplifier(),
                KingCrimsonGapingWoundEffect.MAX_AMPLIFIER);
        event.setAmount(
                event.getAmount()
                        * (1.0F + 0.2F * (amplifier + 1)));

        if (target.level() instanceof ServerLevel serverLevel) {
            int count = Math.max(
                    Mth.ceil(
                            target.getBbHeight()
                                    * target.getBbWidth()
                                    * 1.5F),
                    1);
            serverLevel.sendParticles(
                    ModParticles.BLOOD.get(),
                    target.getRandomX(1.0D),
                    target.getRandomY(),
                    target.getRandomZ(1.0D),
                    count,
                    target.getBbWidth() * 0.25D,
                    target.getBbHeight() * 0.25D,
                    target.getBbWidth() * 0.25D,
                    0.0D);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (!event.getEntity().level().isClientSide()
                && event.getEntity().hasEffect(
                        KingCrimsonAddon.BLEEDING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMangledBodyAdded(
            MobEffectEvent.Added event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (!event.getEntity().level().isClientSide()
                && event.getEntity() instanceof Player player
                && effect.getEffect().is(
                        KingCrimsonAddon.MANGLED_BODY)) {
            applyInventoryCooldowns(
                    player, Math.max(effect.getDuration(), 1));
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
        if (event.getEntity().hasEffect(
                KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(
            ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && KingCrimsonAddon.temporalState(player)
                        .isErasing(
                                player.level().getGameTime())) {
            event.setCanPickup(TriState.FALSE);
            return;
        }
        if (event.getPlayer().hasEffect(
                KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
        if (event.getEntity() instanceof Player player
                && player.hasEffect(
                        KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
        if (event.getEntity().hasEffect(
                KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
        if (event.getEntity().hasEffect(
                KingCrimsonAddon.MANGLED_BODY)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        EpitaphForesightService.enforce(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
                EpitaphStateService.tick(player);
            }
            if (player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
                KingCrimsonTemporalStateService.tick(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            KingCrimsonTemporalStateService
                    .terminateOnInteraction(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTimeEraseRemoved(
            MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getEffect().is(
                        KingCrimsonAddon.TIME_ERASE)) {
            KingCrimsonTemporalStateService.terminate(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTimeEraseExpired(
            MobEffectEvent.Expired event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (event.getEntity() instanceof ServerPlayer player
                && effect != null
                && effect.getEffect().is(
                        KingCrimsonAddon.TIME_ERASE)) {
            KingCrimsonTemporalStateService.terminate(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerDeath(
            net.neoforged.neoforge.event.entity.living.LivingDeathEvent
                    event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(player);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        if (original instanceof ServerPlayer serverOriginal
                && original.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(serverOriginal);
        }
        if (player instanceof ServerPlayer serverPlayer
                && player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(serverPlayer);
        }
        if (original instanceof ServerPlayer serverOriginal
                && original.hasData(
                        KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(
                    serverOriginal);
        }
        if (player instanceof ServerPlayer serverPlayer
                && player.hasData(
                        KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(player);
            KingCrimsonAddon.syncEpitaphState(player);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(player);
            KingCrimsonAddon.syncTemporalState(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(player);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            EpitaphStateService.terminate(player);
        }
        if (event.getEntity() instanceof ServerPlayer player
                && player.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonTemporalStateService.terminate(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof Player target
                && target.hasData(KingCrimsonAddon.EPITAPH_STATE)) {
            KingCrimsonAddon.syncEpitaphState(
                    observer, target);
        }
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof Player target
                && target.hasData(KingCrimsonAddon.TEMPORAL_STATE)) {
            KingCrimsonAddon.syncTemporalState(
                    observer, target);
        }
    }

    private static void applyInventoryCooldowns(
            Player player,
            int ticks) {
        Set<Item> items = new LinkedHashSet<>();
        for (int slot = 0; slot < 9; slot++) {
            addItem(items, player.getInventory().getItem(slot));
        }
        addItem(items, player.getMainHandItem());
        addItem(items, player.getOffhandItem());
        for (Item item : items) {
            player.getCooldowns().addCooldown(item, ticks);
        }
    }

    private static void addItem(
            Set<Item> items,
            ItemStack stack) {
        if (!stack.isEmpty()) {
            items.add(stack.getItem());
        }
    }
}
