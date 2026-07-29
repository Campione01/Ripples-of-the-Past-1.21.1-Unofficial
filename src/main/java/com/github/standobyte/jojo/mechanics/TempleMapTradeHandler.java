package com.github.standobyte.jojo.mechanics;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.api.trade.ContextualVillagerTradeContext;
import com.github.standobyte.jojo.api.trade.ContextualVillagerTradeProvider;
import com.github.standobyte.jojo.api.trade.ContextualVillagerTrades;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModMapDecorationTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
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
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class TempleMapTradeHandler {
    private static final String JOJO_STRUCTURE_TAG = "JojoStructure";
    private static final String HAMON_TEMPLE = "hamon_temple";
    private static final String PILLARMAN_TEMPLE = "pillarman_temple";
    private static final ResourceLocation PILLARMAN_MAP_PROVIDER =
            JojoMod.resLoc("pillarman_temple_map");

    public static final TagKey<Structure> HAMON_TEMPLE_MAPS = TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, JojoMod.resLoc("on_hamon_temple_maps"));
    public static final TagKey<Structure> PILLARMAN_TEMPLE_MAPS = TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE, JojoMod.resLoc("on_pillarman_temple_maps"));
    private static final TempleMapTrade PILLARMAN_MAP_TRADE = new TempleMapTrade(32, PILLARMAN_TEMPLE_MAPS,
            "filled_map.jojo_ripples:pillarman_temple", ModMapDecorationTypes.PILLARMAN_TEMPLE, PILLARMAN_TEMPLE, 1, 30);
    private static final ContextualVillagerTradeProvider
            PILLARMAN_MAP_PROVIDER_IMPL =
            new ContextualVillagerTradeProvider() {
                @Override
                public boolean isEligible(
                        ContextualVillagerTradeContext context) {
                    VillagerData villagerData =
                            context.villager().getVillagerData();
                    return villagerData.getProfession()
                            == VillagerProfession.CARTOGRAPHER
                            && villagerData.getLevel() >= 4;
                }

                @Override
                public MerchantOffer createOffer(
                        ContextualVillagerTradeContext context) {
                    if (context.player().getRandom().nextDouble()
                            >= getPillarmanMapChance(
                                    context.player(),
                                    context.villager()
                                            .getVillagerData()
                                            .getType())) {
                        return null;
                    }
                    return PILLARMAN_MAP_TRADE.getOffer(
                            context.villager(),
                            context.villager().getRandom());
                }

                @Override
                public void onFirstPurchase(
                        ContextualVillagerTradeContext context,
                        MerchantOffer offer) {
                    onPillarmanMapTaken(
                            context.player());
                }
            };

    private TempleMapTradeHandler() {}

    public static void registerContextualTrades() {
        ContextualVillagerTrades.register(
                PILLARMAN_MAP_PROVIDER,
                ContextualVillagerTrades
                        .EXPERT_STRUCTURE_MAP_GROUP,
                PILLARMAN_MAP_PROVIDER_IMPL);
    }

    @SubscribeEvent
    public static void addTempleMapTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CARTOGRAPHER) {
            return;
        }
        List<VillagerTrades.ItemListing> level4 = event.getTrades().get(4);
        level4.add(new TempleMapTrade(24, HAMON_TEMPLE_MAPS, "filled_map.jojo_ripples:hamon_temple", net.minecraft.world.level.saveddata.maps.MapDecorationTypes.TAIGA_VILLAGE, HAMON_TEMPLE, 12, 23));
    }

    private static double getPillarmanMapChance(ServerPlayer player, VillagerType villagerType) {
        PlayerPower playerPower = PlayerPower.get(player);
        if (playerPower != null
                && playerPower.getCurTypeData(
                        ModPlayerPowers.VAMPIRISM)
                        .isPresent()) {
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

        StandPower standPower = StandPower.get(player);
        if (standPower != null && standPower.hasPower()) {
            mapChance *= 0.05;
        }
        else if (playerPower != null
                && playerPower.getCurTypeData(
                        ModPlayerPowers.HAMON)
                        .isPresent()) {
            mapChance *= 0.4;
        }
        return mapChance;
    }

    private static boolean canGivePillarmanMap(VillagerType villagerType) {
        return villagerType == VillagerType.DESERT
                || villagerType == VillagerType.SAVANNA
                || villagerType == VillagerType.SWAMP;
    }

    public static boolean isTempleMap(ItemStack stack, String templeId) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.contains(JOJO_STRUCTURE_TAG) && templeId.equals(data.getString(JOJO_STRUCTURE_TAG));
    }

    public static void onTradeTaken(ServerPlayer player, ItemStack result) {
        if (ContextualVillagerTrades
                .isContextualResult(result)) {
            return;
        }
        if (isTempleMap(result, HAMON_TEMPLE)) {
            ModCriteriaTriggers.triggerTempleMap(player, HAMON_TEMPLE);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSoundEvents.MAP_BOUGHT_HAMON_TEMPLE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        else if (isTempleMap(result, PILLARMAN_TEMPLE)) {
            onPillarmanMapTaken(player);
        }
    }

    private static void onPillarmanMapTaken(
            ServerPlayer player) {
        ModCriteriaTriggers.triggerTempleMap(
                player, PILLARMAN_TEMPLE);
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSoundEvents
                        .MAP_BOUGHT_PILLAR_MAN_TEMPLE
                        .get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
    }

    private record TempleMapTrade(int emeraldCost, TagKey<Structure> destination, String displayName, Holder<MapDecorationType> destinationType, String structureName, int maxUses, int villagerXp)
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
