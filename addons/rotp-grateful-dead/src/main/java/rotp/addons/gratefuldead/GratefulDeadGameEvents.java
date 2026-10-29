package rotp.addons.gratefuldead;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.subsystems.timestop.TimeStopState;

import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.service.AgingText;
import rotp.addons.gratefuldead.state.BlockAgingData;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = GratefulDeadAddon.MOD_ID)
public final class GratefulDeadGameEvents {
    private static final Map<UUID, FoodSnapshot>
            AGED_FOOD_STARTS = new HashMap<>();
    private static final Set<UUID>
            ACTIVE_GRATEFUL_DEAD_OWNERS =
                    new HashSet<>();

    private GratefulDeadGameEvents() {}

    @SubscribeEvent
    public static void onEntityTick(
            EntityTickEvent.Post event) {
        if (!(event.getEntity().level()
                instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity()
                instanceof LivingEntity living) {
            if (living.hasData(
                    GratefulDeadAddon.LIVING_AGING)) {
                LivingAgingState.get(living)
                        .tickServer(level, living);
                float progress =
                        LivingAgingState.get(living)
                                .progress();
                if (progress >= 0.35F
                        && living instanceof Player player
                        && living.tickCount % 20 == 0) {
                    float normalized =
                            Math.clamp(
                                    (progress - 0.35F)
                                            / 0.65F,
                                    0.0F,
                                    1.0F);
                    float targetMaximum =
                            4.0F
                                    + (1.0F - normalized)
                                            * 8.0F;
                    float saturation = player.getFoodData()
                            .getSaturationLevel();
                    if (saturation > targetMaximum) {
                        float drain =
                                0.2F + normalized * 0.2F;
                        player.getFoodData().setSaturation(
                                Math.max(
                                        targetMaximum,
                                        saturation - drain));
                    }
                }
                if (progress > 0.0F
                        && living.tickCount % 20 == 0) {
                    level.sendParticles(
                            net.minecraft.core.particles
                                    .ParticleTypes.ASH,
                            living.getX(),
                            living.getY(0.5D),
                            living.getZ(),
                            Math.max(
                                    1,
                                    (int) (progress * 4.0F)),
                            living.getBbWidth() * 0.35D,
                            living.getBbHeight() * 0.35D,
                            living.getBbWidth() * 0.35D,
                            0.005D);
                }
            }
            if (living instanceof Player player) {
                trackOwnerPower(level, player);
                AgingService.tickPlayerInventory(player);
            }
        }
        else if (event.getEntity()
                instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            if (stack.has(
                    GratefulDeadAddon.ITEM_AGING)) {
                ItemStack next =
                        AgingService.tickItemState(
                                level.getServer(), stack);
                if (next.isEmpty()) {
                    item.discard();
                }
                else {
                    item.setItem(next);
                }
            }
        }

        BlockPos contact =
                event.getEntity().blockPosition().below();
        BlockAgingData.get(level)
                .crumbleOnContact(level, contact);
    }

    @SubscribeEvent
    public static void onServerTick(
            ServerTickEvent.Post event) {
        for (ServerLevel level :
                event.getServer().getAllLevels()) {
            BlockAgingData.get(level).tick(level);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!target.hasData(
                GratefulDeadAddon.LIVING_AGING)) {
            return;
        }
        float progress =
                LivingAgingState.get(target).progress();
        if (progress < 0.45F) {
            return;
        }
        float multiplier =
                (progress - 0.45F) / 0.55F;
        float damageScale =
                1.0F + 0.6F * multiplier;
        if (event.getSource().is(DamageTypes.FALL)) {
            damageScale += 0.4F * multiplier;
        }
        float originalAmount = event.getAmount();
        float scaledAmount = originalAmount * damageScale;
        if (Float.isFinite(originalAmount) && Float.isInfinite(scaledAmount)) {
            scaledAmount = Math.copySign(Float.MAX_VALUE, scaledAmount);
        }
        event.setAmount(scaledAmount);
    }

    @SubscribeEvent
    public static void onTimeStopPreStart(
            TimeStopLifecycleEvent.PreStart event) {
        if (!(event.getLevel().getEntity(
                        event.getInstance().userId())
                instanceof LivingEntity user)
                || !user.hasData(
                        GratefulDeadAddon.LIVING_AGING)) {
            return;
        }
        float progress =
                LivingAgingState.get(user).progress();
        if (progress <= 0.0F) {
            return;
        }
        TimeStopState.Instance original =
                event.getInstance();
        int agedTicks =
                AgingService.agedActiveTimeStopTicks(
                        original.ticksLeft(), progress);
        if (agedTicks <= 0) {
            event.setCanceled(true);
            return;
        }
        event.setInstance(
                original.withTiming(
                        agedTicks,
                        Math.min(
                                original.totalTicks(),
                                agedTicks)));
    }

    @SubscribeEvent
    public static void onFoodStarted(
            LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        ItemAgingState state = event.getItem().get(
                GratefulDeadAddon.ITEM_AGING);
        if (state != null
                && state.progress() > 0.0F
                && event.getItem().getFoodProperties(
                                player)
                        != null) {
            AGED_FOOD_STARTS.put(
                    player.getUUID(),
                    new FoodSnapshot(
                            player.getFoodData()
                                    .getFoodLevel(),
                            player.getFoodData()
                                    .getSaturationLevel(),
                            state.progress()));
        }
        else {
            AGED_FOOD_STARTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onFoodStopped(
            LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            AGED_FOOD_STARTS.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onFoodFinished(
            LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ItemAgingState state =
                event.getItem().get(
                        GratefulDeadAddon.ITEM_AGING);
        FoodSnapshot snapshot =
                event.getEntity() instanceof Player player
                        ? AGED_FOOD_STARTS.remove(
                                player.getUUID())
                        : null;
        float progress = snapshot != null
                ? snapshot.progress()
                : state != null ? state.progress() : 0.0F;
        if (progress <= 0.0F
                || event.getItem().getFoodProperties(
                                event.getEntity())
                        == null) {
            return;
        }
        if (snapshot != null
                && event.getEntity()
                        instanceof Player player) {
            player.getFoodData().setFoodLevel(
                    snapshot.foodLevel());
            player.getFoodData().setSaturation(
                    snapshot.saturation());
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetHealthPacket(
                        player.getHealth(),
                        player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel()));
            }
        }
        int duration = Math.clamp(
                20 + Math.round(80.0F * progress),
                20,
                100);
        event.getEntity().addEffect(
                new MobEffectInstance(
                        MobEffects.HUNGER,
                        duration,
                        0,
                        false,
                        true,
                        true));
        event.getEntity().addEffect(
                new MobEffectInstance(
                        MobEffects.CONFUSION,
                        duration,
                        0,
                        false,
                        true,
                        true));
    }

    @SubscribeEvent
    public static void onBlockDrops(
            BlockDropsEvent event) {
        BlockAgingData data =
                BlockAgingData.get(event.getLevel());
        BlockAgingData.Entry entry =
                data.entry(event.getPos());
        if (entry == null) {
            return;
        }
        if (event.getState().getBlock()
                        instanceof CropBlock crop
                && crop.isMaxAge(event.getState())) {
            var youngDrops = Block.getDrops(
                    crop.getStateForAge(0),
                    event.getLevel(),
                    event.getPos(),
                    event.getBlockEntity(),
                    event.getBreaker(),
                    event.getTool());
            event.getDrops().clear();
            for (ItemStack stack : youngDrops) {
                ItemEntity drop = new ItemEntity(
                        event.getLevel(),
                        event.getPos().getX() + 0.5D,
                        event.getPos().getY() + 0.5D,
                        event.getPos().getZ() + 0.5D,
                        stack);
                drop.setDefaultPickUpDelay();
                event.getDrops().add(drop);
            }
        }
        for (ItemEntity drop : event.getDrops()) {
            if (!(drop.getItem().getItem()
                            instanceof BlockItem blockItem)
                    || blockItem.getBlock()
                            != event.getState().getBlock()) {
                continue;
            }
            drop.getItem().set(
                    GratefulDeadAddon.ITEM_AGING,
                    new ItemAgingState(
                            entry.progress(),
                            java.util.Optional.of(
                                    entry.owner()),
                            entry.ownerGeneration(),
                            entry.fading()));
        }
        data.remove(event.getLevel(), event.getPos());
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)
                || !(event.getEntity()
                        instanceof LivingEntity living)) {
            return;
        }
        for (net.minecraft.world.InteractionHand hand :
                net.minecraft.world.InteractionHand.values()) {
            ItemAgingState state =
                    living.getItemInHand(hand)
                            .get(
                                    GratefulDeadAddon
                                            .ITEM_AGING);
            if (state != null) {
                ItemStack stack =
                        living.getItemInHand(hand);
                if (!(stack.getItem()
                                instanceof BlockItem blockItem)
                        || blockItem.getBlock()
                                != event.getPlacedBlock()
                                        .getBlock()) {
                    continue;
                }
                if (AgingService.isSeedItem(stack)) {
                    event.setCanceled(true);
                    return;
                }
                BlockAgingData.get(level).inherit(
                        level, event.getPos(), state);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (!(event.getLevel()
                instanceof ServerLevel level)) {
            return;
        }
        BlockAgingData.Entry entry =
                BlockAgingData.get(level)
                        .entry(event.getPos());
        if (entry == null
                || entry.progress() <= 0.0F) {
            return;
        }
        event.setCanceled(true);
        event.setSuccessful(false);
    }

    @SubscribeEvent
    public static void onCropGrow(
            CropGrowEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level
                && BlockAgingData.get(level)
                                .progress(event.getPos())
                        > 0.0F) {
            event.setResult(
                    CropGrowEvent.Pre.Result.DO_NOT_GROW);
        }
    }

    @SubscribeEvent
    public static void onNameFormat(
            PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(player).progress()
                        >= 0.62F) {
            event.setDisplayname(
                    AgingText.obfuscatedName(
                            event.getDisplayname()));
        }
    }

    @SubscribeEvent
    public static void onServerChat(
            ServerChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(player).progress()
                        >= 0.62F) {
            event.setMessage(
                    AgingText.obfuscatedMessage(
                            event.getRawText()));
        }
    }

    @SubscribeEvent
    public static void onChunkSent(
            ChunkWatchEvent.Sent event) {
        BlockAgingData.get(event.getLevel())
                .syncChunk(
                        event.getPlayer(),
                        event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(
            LivingDeathEvent event) {
        if (event.isCanceled()
                || event.getEntity().level().isClientSide()) {
            return;
        }
        UUID playerId =
                event.getEntity().getUUID();
        AGED_FOOD_STARTS.remove(playerId);
        if (event.getEntity().hasData(GratefulDeadAddon.LIVING_AGING)) {
            LivingAgingState.get(event.getEntity())
                    .clearImmediately(event.getEntity());
        }
        requestOwnerFade(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId =
                event.getEntity().getUUID();
        AGED_FOOD_STARTS.remove(playerId);
        requestOwnerFade(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        AGED_FOOD_STARTS.remove(
                event.getEntity().getUUID());
    }

    private static void requestOwnerFade(
            LivingEntity entity) {
        boolean tracked =
                ACTIVE_GRATEFUL_DEAD_OWNERS.remove(
                        entity.getUUID());
        if (entity.getServer() != null
                && (tracked
                        || AgingService.hasGratefulDead(
                                entity))) {
            AgingService.requestFade(
                    entity.getServer(),
                    entity.getUUID());
        }
    }

    private static void trackOwnerPower(
            ServerLevel level, Player player) {
        UUID playerId = player.getUUID();
        if (AgingService.hasGratefulDead(player)) {
            ACTIVE_GRATEFUL_DEAD_OWNERS.add(playerId);
        }
        else if (ACTIVE_GRATEFUL_DEAD_OWNERS.remove(
                playerId)) {
            AgingService.requestFade(
                    level.getServer(), playerId);
        }
    }

    private record FoodSnapshot(
            int foodLevel,
            float saturation,
            float progress) {}
}
