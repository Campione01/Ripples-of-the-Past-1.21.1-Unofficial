package rotp.addons.d4c.client;

import java.util.OptionalInt;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;

import rotp.addons.d4c.D4CAddon;
import rotp.addons.d4c.D4CParallelItemService;
import rotp.addons.d4c.D4CPhase2BSpec;
import rotp.addons.d4c.D4CSelectionPredicates;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.addons.d4c.item.D4CColtItem;
import rotp.addons.d4c.network.D4CPayloads;
import rotp.addons.d4c.travel.D4CDimensionTargets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = D4CAddon.MOD_ID, value = Dist.CLIENT)
public final class D4CClientEvents {
    // 1.16 onFogColors: pink fog in the parallel world
    static final float PARALLEL_FOG_RED = 1.0F;
    static final float PARALLEL_FOG_GREEN = 0.7F;
    static final float PARALLEL_FOG_BLUE = 0.9F;

    private D4CClientEvents() {}

    @SubscribeEvent
    public static void tintParallelWorldFog(
            ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && D4CDimensionTargets.PARALLEL_WORLD.equals(
                level.dimension().location())) {
            event.setRed(PARALLEL_FOG_RED);
            event.setGreen(PARALLEL_FOG_GREEN);
            event.setBlue(PARALLEL_FOG_BLUE);
        }
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    D4CAddon.COLT_CUSTOM.get(),
                    D4CAddon.id("reloading"),
                    (stack, level, entity, seed) ->
                            D4CColtItem.reloadProgress(stack));
            ItemProperties.register(
                    D4CAddon.COLT_CUSTOM.get(),
                    D4CAddon.id("shooting"),
                    (stack, level, entity, seed) ->
                            D4CColtItem.shootingProgress(stack));
            for (var portal : new net.minecraft.world.level.block.Block[] {
                    D4CAddon.PARALLEL_WORLD_PORTAL.get(),
                    D4CAddon.NETHER_PORTAL.get(),
                    D4CAddon.END_PORTAL.get(),
                    D4CAddon.OVERWORLD_PORTAL.get(),
                    D4CAddon.ATUM_PORTAL.get(),
                    D4CAddon.DEEP_DARK_PORTAL.get(),
                    D4CAddon.LOST_CITY_PORTAL.get(),
                    D4CAddon.SKYLIGHT_FOREST_PORTAL.get(),
                    D4CAddon.TWILIGHT_FOREST_PORTAL.get()}) {
                ItemBlockRenderTypes.setRenderLayer(
                        portal, RenderType.translucent());
            }
        });
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                D4CAddon.D4C_ENTITY.get(),
                D4CStandRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.SUMMONED_PLAYER.get(),
                D4CMinionRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.ANNIHILATION_CLONE.get(),
                D4CCloneRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.ANNIHILATION_PLAYER_CLONE.get(),
                D4CCloneRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.COLT_BULLET.get(),
                D4CColtBulletRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.FLAG.get(),
                D4CFlagRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.SUMMON_KICK_ENTITY.get(),
                D4CSummonKickRenderer::new);
        event.registerEntityRenderer(
                D4CAddon.SUMMON_KICK_FLAG.get(),
                D4CFlagRenderer::new);
        D4CPayloads.installClientOpenHandler(
                D4CClientPayloads::openDimensionSelector);
        D4CPayloads.installClientMinionAiSelectorHandler(
                D4CClientPayloads::openMinionAiSelector);
        D4CPayloads.installClientLoveTrainHandler(
                D4CLoveTrainClientPresentation::accept);
        D4CPayloads.installClientXrayGlowHandler(
                D4CSaintCorpseClient::acceptXrayGlow);
        D4CPayloads.installClientDevilPalmMarkerHandler(
                D4CSaintCorpseClient::acceptDevilPalmMarker);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(
            EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(
                D4CFlagModel.LAYER,
                D4CFlagModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerGuiLayers(
            RegisterGuiLayersEvent event) {
        AbilitySelectionVisualPolicies.register(
                D4CAddon.id("phase2b_selection"),
                query -> {
                    LivingEntity user = query.power() != null
                            ? query.power().getUser()
                            : null;
                    int ownedMinions = user != null
                            ? countOwnedMinions(user)
                            : 0;
                    boolean aimedOwnedMinion = user != null
                            && hasAimedOwnedMinion(user);
                    return D4CSelectionPredicates.donorGreenSelection(
                                    query.ability()
                                            .getAbilityId()
                                            .nameInMoveset(),
                                    ownedMinions,
                                    aimedOwnedMinion,
                                    query.conditionCheck()
                                            .isPositive())
                            ? OptionalInt.of(0xFF00FF00)
                            : OptionalInt.empty();
                });
        event.registerAboveAll(
                D4CAddon.id("sandstorm"),
                new D4CSandstormOverlay());
    }

    @SubscribeEvent
    public static void addParallelOriginTooltip(
            ItemTooltipEvent event) {
        if (D4CParallelItemService.hasParallelOriginTag(
                event.getItemStack())) {
            event.getToolTip().add(
                    Component.translatable(
                                    "rotp_d4c.tooltip.parallel_world_origin")
                            .withStyle(
                                    ChatFormatting.DARK_PURPLE,
                                    ChatFormatting.ITALIC));
        }
    }

    private static int countOwnedMinions(
            LivingEntity user) {
        return user.level().getEntitiesOfClass(
                        D4CMinionEntity.class,
                        user.getBoundingBox().inflate(
                                D4CPhase2BSpec.MINION_RADIUS),
                        minion -> minion.isAlive()
                                && minion.isOwnedBy(
                                        user.getUUID()))
                .size();
    }

    private static boolean hasAimedOwnedMinion(
            LivingEntity user) {
        double range = 20.0D;
        double halfRange = range / 2.0D;
        Vec3 origin = user.position();
        Vec3 look = user.getLookAngle();
        Vec3 center = origin.add(look.scale(halfRange));
        AABB bounds = new AABB(
                center.x - halfRange,
                center.y - halfRange,
                center.z - halfRange,
                center.x + halfRange,
                center.y + halfRange,
                center.z + halfRange);
        return user.level().getEntitiesOfClass(
                        D4CMinionEntity.class,
                        bounds,
                        minion -> {
                            Vec3 difference =
                                    minion.position()
                                            .subtract(origin);
                            return minion.isAlive()
                                    && minion.isOwnedBy(
                                            user.getUUID())
                                    && difference.lengthSqr()
                                            > 0.0D
                                    && look.dot(
                                            difference.normalize())
                                            > 0.8D;
                        })
                .size() > 0;
    }

    @SubscribeEvent
    public static void registerParticles(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                D4CAddon.GOLDEN_LIGHT_PILLAR.get(),
                D4CParticle.GoldenLightPillarProvider::new);
        event.registerSpriteSet(
                D4CAddon.SHORT_GOLDEN_LIGHT_PILLAR.get(),
                D4CParticle.ShortGoldenLightPillarProvider::new);
        event.registerSpriteSet(
                D4CAddon.ANNIHILATE.get(),
                D4CParticle.AnnihilateProvider::new);
        event.registerSpriteSet(
                D4CAddon.LIGHT_BALL.get(),
                D4CParticle.LightBallProvider::new);
        event.registerSpriteSet(
                D4CAddon.WIDE_GOLDEN_LIGHT_PILLAR.get(),
                D4CParticle.WideGoldenLightPillarProvider::new);
        event.registerSpriteSet(
                D4CAddon.GOLD_LIGHT.get(),
                D4CParticle.GoldLightProvider::new);
        event.registerSpriteSet(
                D4CAddon.DEVIL_SKY.get(),
                D4CParticle.DevilSkyProvider::new);
        event.registerSpriteSet(
                D4CAddon.SKULLHEAD.get(),
                D4CParticle.SkullHeadProvider::new);
    }
}
