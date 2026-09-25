package rotp.core.mechanics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleBiFunction;

import javax.annotation.Nullable;

import rotp.core.api.trade.ContextualVillagerTradeContext;
import rotp.core.api.trade.ContextualVillagerTradeProvider;
import rotp.core.api.trade.ContextualVillagerTrades;
import rotp.core.core.JojoMod;
import rotp.core.init.ModCriteriaTriggers;
import rotp.core.init.ModMapDecorationTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.network.s2c.TrEntitySpecialEffectPacket;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.playerpower.PlayerPowerType;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 1.16 CustomVillagerTrades.MapTrades: one unique expert map per cartographer
 * (meteorite, Hamon temple or Pillar Man temple), rolled per player with biome
 * and power weights; the first map of each kind a player buys plays its flavor
 * music and puts menacing particles on the merchant.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class TempleMapTradeHandler {
    private static final String JOJO_STRUCTURE_TAG = "JojoStructure";
    public static final String HAMON_TEMPLE = "hamon_temple";
    public static final String PILLARMAN_TEMPLE = "pillarman_temple";
    public static final String METEORITE = "meteorite";
    // 1.16 PlayerUtilCap.OneTimeNotification BOUGHT_*_MAP; PlayerPersisted survives death
    private static final String FLAVOR_NOTIFIED = JojoMod.MOD_ID + ":MapFlavorNotified";

    public static final ResourceLocation METEORITE_MAP_PROVIDER = JojoMod.resLoc("meteorite_map");
    public static final ResourceLocation HAMON_MAP_PROVIDER = JojoMod.resLoc("hamon_temple_map");
    public static final ResourceLocation PILLARMAN_MAP_PROVIDER = JojoMod.resLoc("pillarman_temple_map");

    public static final TagKey<Structure> HAMON_TEMPLE_MAPS = TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, JojoMod.resLoc("on_hamon_temple_maps"));
    public static final TagKey<Structure> PILLARMAN_TEMPLE_MAPS = TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, JojoMod.resLoc("on_pillarman_temple_maps"));
    public static final TagKey<Structure> METEORITE_MAPS = TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, JojoMod.resLoc("on_meteorite_maps"));

    // 1.16 EmeraldForMapTrade(cost, structure, icon, color, maxUses 1, xp)
    public static final TempleMapTrade METEORITE_MAP_TRADE = new TempleMapTrade(16, METEORITE_MAPS,
            "filled_map.jojo_ripples:meteorite", ModMapDecorationTypes.METEORITE, METEORITE, 1, 15);
    public static final TempleMapTrade HAMON_MAP_TRADE = new TempleMapTrade(24, HAMON_TEMPLE_MAPS,
            "filled_map.jojo_ripples:hamon_temple", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.TAIGA_VILLAGE, HAMON_TEMPLE, 1, 23);
    public static final TempleMapTrade PILLARMAN_MAP_TRADE = new TempleMapTrade(32, PILLARMAN_TEMPLE_MAPS,
            "filled_map.jojo_ripples:pillarman_temple", ModMapDecorationTypes.PILLARMAN_TEMPLE, PILLARMAN_TEMPLE, 1, 30);

    private static final ContextualVillagerTradeProvider METEORITE_MAP_PROVIDER_IMPL =
            new MapTradeProvider(METEORITE_MAP_TRADE, TempleMapTradeHandler::meteoriteMapChance);
    private static final ContextualVillagerTradeProvider HAMON_MAP_PROVIDER_IMPL =
            new MapTradeProvider(HAMON_MAP_TRADE, TempleMapTradeHandler::hamonMapChance);
    private static final ContextualVillagerTradeProvider PILLARMAN_MAP_PROVIDER_IMPL =
            new MapTradeProvider(PILLARMAN_MAP_TRADE, TempleMapTradeHandler::pillarmanMapChance);

    // flavor effects waiting for the buyer to close the trade screen (1.16 cap.doWhen)
    private static final Map<UUID, List<PendingFlavor>> PENDING_FLAVOR = new HashMap<>();

    private TempleMapTradeHandler() {}

    public static void registerContextualTrades() {
        ContextualVillagerTrades.register(METEORITE_MAP_PROVIDER,
                ContextualVillagerTrades.EXPERT_STRUCTURE_MAP_GROUP, METEORITE_MAP_PROVIDER_IMPL);
        ContextualVillagerTrades.register(HAMON_MAP_PROVIDER,
                ContextualVillagerTrades.EXPERT_STRUCTURE_MAP_GROUP, HAMON_MAP_PROVIDER_IMPL);
        ContextualVillagerTrades.register(PILLARMAN_MAP_PROVIDER,
                ContextualVillagerTrades.EXPERT_STRUCTURE_MAP_GROUP, PILLARMAN_MAP_PROVIDER_IMPL);
    }

    /** 1.16 METEORITE_MAP.getMapChance. */
    public static double meteoriteMapChance(LivingEntity player, VillagerType villagerType) {
        if (hasStand(player)) {
            return 0;
        }
        double mapChance;
        if (villagerType == VillagerType.SNOW) {
            mapChance = 1;
        }
        else if (villagerType == VillagerType.TAIGA || villagerType == VillagerType.JUNGLE) {
            mapChance = 0; // another map's biome
        }
        else if (villagerType == VillagerType.SWAMP) {
            mapChance = 0.8; // canGiveMap(swamp, snow)
        }
        else {
            mapChance = 0.2;
        }

        if (hasPlayerPower(player, ModPlayerPowers.VAMPIRISM.get())) {
            mapChance *= 0.05;
        }
        else if (hasPlayerPower(player, ModPlayerPowers.HAMON.get())) {
            mapChance *= 0.0625;
        }
        return mapChance;
    }

    /** 1.16 HAMON_MAP.getMapChance. */
    public static double hamonMapChance(LivingEntity player, VillagerType villagerType) {
        double mapChance;
        if (villagerType == VillagerType.TAIGA) {
            mapChance = 1;
        }
        else if (villagerType == VillagerType.SNOW || villagerType == VillagerType.JUNGLE) {
            mapChance = 0; // another map's biome
        }
        else if (villagerType == VillagerType.PLAINS || villagerType == VillagerType.SWAMP) {
            mapChance = 0.5; // canGiveMap(type, taiga)
        }
        else {
            mapChance = 0.125;
        }

        if (hasStand(player)) {
            mapChance *= 0.0625;
        }
        if (hasPlayerPower(player, ModPlayerPowers.VAMPIRISM.get())) {
            mapChance *= 0.125;
        }
        else if (hasPlayerPower(player, ModPlayerPowers.HAMON.get())) {
            mapChance *= 0.25;
        }
        return mapChance;
    }

    /** 1.16 PILLARMAN_MAP.getMapChance. */
    public static double pillarmanMapChance(LivingEntity player, VillagerType villagerType) {
        if (hasPlayerPower(player, ModPlayerPowers.VAMPIRISM.get())) {
            return 0;
        }

        double mapChance;
        if (villagerType == VillagerType.JUNGLE) {
            mapChance = 1;
        }
        else if (villagerType == VillagerType.SNOW || villagerType == VillagerType.TAIGA) {
            mapChance = 0;
        }
        else if (canGivePillarmanMap(villagerType)) {
            mapChance = 0.25;
        }
        else {
            mapChance = 0;
        }

        if (hasStand(player)) {
            mapChance *= 0.05;
        }
        else if (hasPlayerPower(player, ModPlayerPowers.HAMON.get())) {
            mapChance *= 0.4;
        }
        return mapChance;
    }

    private static boolean canGivePillarmanMap(VillagerType villagerType) {
        return villagerType == VillagerType.DESERT
                || villagerType == VillagerType.SAVANNA
                || villagerType == VillagerType.SWAMP;
    }

    private static boolean hasStand(LivingEntity entity) {
        StandPower standPower = StandPower.get(entity);
        return standPower != null && standPower.hasPower();
    }

    private static boolean hasPlayerPower(LivingEntity entity, PlayerPowerType<?> type) {
        PlayerPower playerPower = PlayerPower.get(entity);
        return playerPower != null && playerPower.getPowerType() == type;
    }

    public static boolean isTempleMap(ItemStack stack, String templeId) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(JOJO_STRUCTURE_TAG) && templeId.equals(data.getString(JOJO_STRUCTURE_TAG));
    }

    /** Legacy (non-contextual) map offers, e.g. the old 12-use Hamon map in existing villagers. */
    public static void onTradeTaken(ServerPlayer player, ItemStack result) {
        if (ContextualVillagerTrades.isContextualResult(result)) {
            return;
        }
        for (String structureName : new String[] { METEORITE, HAMON_TEMPLE, PILLARMAN_TEMPLE }) {
            if (isTempleMap(result, structureName)) {
                onMapTaken(player, findTradingMerchant(player), structureName);
                return;
            }
        }
    }

    @Nullable
    private static Entity findTradingMerchant(ServerPlayer player) {
        List<AbstractVillager> merchants = player.level().getEntitiesOfClass(AbstractVillager.class,
                player.getBoundingBox().inflate(16), merchant -> merchant.getTradingPlayer() == player);
        return merchants.isEmpty() ? null : merchants.get(0);
    }

    private static void onMapTaken(ServerPlayer player, @Nullable Entity merchant, String structureName) {
        ModCriteriaTriggers.triggerTempleMap(player, structureName);
        if (merchant != null && claimFirstBuyFlavor(player, structureName)) {
            PENDING_FLAVOR.computeIfAbsent(player.getUUID(), id -> new ArrayList<>())
                    .add(new PendingFlavor(merchant, flavorSound(structureName)));
        }
    }

    private static Holder<SoundEvent> flavorSound(String structureName) {
        return switch (structureName) {
        case METEORITE -> ModSoundEvents.MAP_BOUGHT_METEORITE;
        case PILLARMAN_TEMPLE -> ModSoundEvents.MAP_BOUGHT_PILLAR_MAN_TEMPLE;
        default -> ModSoundEvents.MAP_BOUGHT_HAMON_TEMPLE;
        };
    }

    /** True only the first time this player buys this kind of map. */
    public static boolean claimFirstBuyFlavor(Player player, String structureName) {
        CompoundTag persistent = player.getPersistentData();
        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag notified = persisted.getCompound(FLAVOR_NOTIFIED);
        if (notified.getBoolean(structureName)) {
            return false;
        }
        notified.putBoolean(structureName, true);
        persisted.put(FLAVOR_NOTIFIED, notified);
        persistent.put(Player.PERSISTED_NBT_TAG, persisted);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (PENDING_FLAVOR.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)
                || player.containerMenu != player.inventoryMenu) {
            return;
        }
        List<PendingFlavor> pending = PENDING_FLAVOR.remove(player.getUUID());
        if (pending != null) {
            for (PendingFlavor flavor : pending) {
                if (!flavor.merchant().isRemoved()) {
                    TrEntitySpecialEffectPacket.send(flavor.merchant(), flavor.sound(), player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_FLAVOR.remove(event.getEntity().getUUID());
    }

    private record PendingFlavor(Entity merchant, Holder<SoundEvent> sound) {}

    private static final class MapTradeProvider implements ContextualVillagerTradeProvider {
        private final TempleMapTrade trade;
        private final ToDoubleBiFunction<LivingEntity, VillagerType> chance;

        private MapTradeProvider(TempleMapTrade trade, ToDoubleBiFunction<LivingEntity, VillagerType> chance) {
            this.trade = trade;
            this.chance = chance;
        }

        @Override
        public boolean isEligible(ContextualVillagerTradeContext context) {
            VillagerData villagerData = context.villager().getVillagerData();
            return villagerData.getProfession() == VillagerProfession.CARTOGRAPHER
                    && villagerData.getLevel() >= 4;
        }

        @Override
        public MerchantOffer createOffer(ContextualVillagerTradeContext context) {
            if (context.player().getRandom().nextDouble()
                    >= chance.applyAsDouble(context.player(), context.villager().getVillagerData().getType())) {
                return null;
            }
            return trade.getOffer(context.villager(), context.villager().getRandom());
        }

        @Override
        public void onFirstPurchase(ContextualVillagerTradeContext context, MerchantOffer offer) {
            onMapTaken(context.player(), context.villager(), trade.structureName());
        }
    }

    public record TempleMapTrade(int emeraldCost, TagKey<Structure> destination, String displayName, Holder<MapDecorationType> destinationType, String structureName, int maxUses, int villagerXp)
            implements VillagerTrades.ItemListing {
        @Override
        public MerchantOffer getOffer(Entity trader, net.minecraft.util.RandomSource random) {
            if (!(trader.level() instanceof ServerLevel serverLevel)) {
                return null;
            }
            BlockPos target = serverLevel.findNearestMapStructure(destination, trader.blockPosition(), 100, true);
            if (target == null) {
                return null;
            }
            ItemStack map = MapItem.create(serverLevel, target.getX(), target.getZ(), (byte) 2, true, true);
            MapItem.renderBiomePreviewMap(serverLevel, map);
            MapItemSavedData.addTargetDecoration(map, target, "+", destinationType);
            map.set(DataComponents.ITEM_NAME, Component.translatable(displayName));
            CustomData.update(DataComponents.CUSTOM_DATA, map, tag -> tag.putString(JOJO_STRUCTURE_TAG, structureName));
            return new MerchantOffer(
                    new ItemCost(Items.EMERALD, emeraldCost),
                    Optional.of(new ItemCost(Items.COMPASS)),
                    map,
                    maxUses,
                    villagerXp,
                    0.2F);
        }
    }
}
