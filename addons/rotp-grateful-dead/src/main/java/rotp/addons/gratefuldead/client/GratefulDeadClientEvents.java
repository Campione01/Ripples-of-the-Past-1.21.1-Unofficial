package rotp.addons.gratefuldead.client;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import rotp.core.api.client.render.AbilitySelectionVisualPolicies;
import rotp.core.api.client.render.ItemMaterialTintPolicies;
import rotp.core.api.client.render.LivingEntityBaseModelTints;
import rotp.core.api.client.render.PlayerArmModelQuery;
import rotp.core.api.client.render.PlayerArmPoseProvider;
import rotp.core.api.client.render.PlayerArmPoseProviders;
import rotp.core.api.client.render.StandMaterialTintPolicies;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.logging.LogUtils;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import rotp.addons.gratefuldead.ability.AgingAbility;
import rotp.addons.gratefuldead.network.BlockAgingPayload;
import rotp.addons.gratefuldead.service.AgingService;
import rotp.addons.gratefuldead.service.AgingText;
import rotp.addons.gratefuldead.state.ItemAgingState;
import rotp.addons.gratefuldead.state.LivingAgingState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

@EventBusSubscriber(
        modid = GratefulDeadAddon.MOD_ID,
        value = Dist.CLIENT)
public final class GratefulDeadClientEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation MATERIAL_OWNER =
            ResourceLocation.fromNamespaceAndPath(
                    GratefulDeadAddon.MOD_ID, "aging_material");
    private static final ResourceLocation SELECTION_OWNER =
            ResourceLocation.fromNamespaceAndPath(
                    GratefulDeadAddon.MOD_ID, "aging_aura_selection");
    private static final ResourceLocation ARM_POSE_OWNER =
            ResourceLocation.fromNamespaceAndPath(
                    GratefulDeadAddon.MOD_ID, "aging_touch_reach");
    private static final ResourceLocation STAND_TINT_OWNER =
            ResourceLocation.fromNamespaceAndPath(
                    GratefulDeadAddon.MOD_ID, "aging_stand_material");

    static {
        BlockAgingPayload.installClientHandler(
                GratefulDeadClientEvents::applyBlockAging);
        LivingEntityBaseModelTints.register(
                MATERIAL_OWNER,
                query -> {
                    LivingEntity entity = query.entity();
                    if (entity instanceof StandEntity
                            || !entity.hasData(
                                    GratefulDeadAddon
                                            .LIVING_AGING)) {
                        return OptionalInt.empty();
                    }
                    float progress =
                            LivingAgingState.get(entity)
                                    .progress();
                    return progress > 0.0F
                            ? OptionalInt.of(
                                    AgingVisuals.tintEntityArgb(
                                            query.originalColor(),
                                            progress))
                            : OptionalInt.empty();
                });
        ItemMaterialTintPolicies.register(
                MATERIAL_OWNER,
                query -> {
                    ItemAgingState aging = query.stack().get(
                            GratefulDeadAddon.ITEM_AGING);
                    if (aging == null
                            || aging.progress() <= 0.0F) {
                        return null;
                    }
                    float progress = aging.progress();
                    return originalArgb ->
                            AgingVisuals.tintEntityArgb(
                                    originalArgb, progress);
                });
        AbilitySelectionVisualPolicies.register(
                SELECTION_OWNER,
                query -> {
                    if (query.ability().abilityType
                                    != GratefulDeadAddon
                                            .AGING_AURA_TYPE.get()
                            || !(query.power()
                                    instanceof StandPower power)) {
                        return OptionalInt.empty();
                    }
                    boolean active = power.userStandEffects
                            .getEffectOfType(
                                    GratefulDeadAddon
                                            .AGING_AURA_EFFECT.get())
                            .isPresent();
                    return active
                            ? OptionalInt.of(0xFF00FF00)
                             : OptionalInt.empty();
                });
        PlayerArmPoseProviders.register(
                ARM_POSE_OWNER,
                new PlayerArmPoseProvider() {
                    @Override
                    public void applyPostSetup(
                            PlayerArmModelQuery query) {
                        applyAgingTouchReach(query);
                    }
                });
        StandMaterialTintPolicies.register(
                STAND_TINT_OWNER,
                query -> {
                    LivingEntity user = query.stand().getUser();
                    if (user == null
                            || !user.hasData(
                                    GratefulDeadAddon
                                            .LIVING_AGING)) {
                        return null;
                    }
                    float progress =
                            LivingAgingState.get(user).progress();
                    return progress > 0.0F
                            ? originalArgb ->
                                    AgingVisuals.tintEntityArgb(
                                            originalArgb,
                                            progress)
                            : null;
                });
    }

    private GratefulDeadClientEvents() {}

    private static void applyAgingTouchReach(
            PlayerArmModelQuery query) {
        if (!query.player().getMainHandItem().isEmpty()
                || !query.player().getOffhandItem().isEmpty()) {
            return;
        }
        // 1.16 AgingTouchClientHelper.isReaching: the touch its Stand
        // power holds, with or without the Stand out.
        EntityActionInstance held =
                LivingComponentAction.getCurEntityAction(
                        query.player(), PowerClass.STAND);
        if (held == null
                || held.isOver()
                || !(held.ability instanceof AgingAbility aging)
                || !aging.isTouchMode()
                || !aging.isHeldSincePress(held)) {
            return;
        }

        float reachX = -(float) Math.PI / 2.0F
                + (float) Math.toRadians(
                        query.player().getXRot());
        if (query.player().getMainArm() == HumanoidArm.RIGHT) {
            query.model().rightArm.xRot = reachX;
            query.model().rightArm.yRot = 0.0F;
            query.model().rightArm.zRot = 0.0F;
            query.model().rightSleeve.copyFrom(
                    query.model().rightArm);
        }
        else {
            query.model().leftArm.xRot = reachX;
            query.model().leftArm.yRot = 0.0F;
            query.model().leftArm.zRot = 0.0F;
            query.model().leftSleeve.copyFrom(
                    query.model().leftArm);
        }
    }

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                GratefulDeadAddon
                        .THE_GRATEFUL_DEAD_ENTITY.get(),
                GratefulDeadRenderer::new);
    }

    @SubscribeEvent
    public static void registerBlockColors(
            RegisterColorHandlersEvent.Block event) {
        event.register(
                GratefulDeadClientEvents::grassColor,
                Blocks.GRASS_BLOCK,
                Blocks.SHORT_GRASS,
                Blocks.FERN,
                Blocks.POTTED_FERN,
                Blocks.TALL_GRASS,
                Blocks.LARGE_FERN);
        event.register(
                GratefulDeadClientEvents::foliageColor,
                Blocks.OAK_LEAVES,
                Blocks.JUNGLE_LEAVES,
                Blocks.ACACIA_LEAVES,
                Blocks.DARK_OAK_LEAVES,
                Blocks.VINE);
        event.register(
                GratefulDeadClientEvents::birchColor,
                Blocks.BIRCH_LEAVES);
        event.register(
                GratefulDeadClientEvents::spruceColor,
                Blocks.SPRUCE_LEAVES);
        event.register(
                GratefulDeadClientEvents::lilyPadColor,
                Blocks.LILY_PAD);
    }

    @SubscribeEvent
    public static void modifyBakingResult(
            ModelEvent.ModifyBakingResult event) {
        Set<ModelResourceLocation> ageableModels =
                new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state :
                    block.getStateDefinition()
                            .getPossibleStates()) {
                try {
                    if (AgingService.isAgeableBlock(state)) {
                        ageableModels.add(
                                BlockModelShaper
                                        .stateToModelLocation(state));
                    }
                }
                catch (RuntimeException error) {
                    LOGGER.error(
                            "Failed to classify aging model for {}.",
                            BuiltInRegistries.BLOCK
                                    .getKey(block),
                            error);
                }
            }
        }
        for (ModelResourceLocation location : ageableModels) {
            BakedModel model =
                    event.getModels().get(location);
            if (model != null
                    && !(model instanceof AgingBakedModel)) {
                event.getModels().put(
                        location, new AgingBakedModel(model));
            }
        }
    }

    @SubscribeEvent
    public static void obfuscateAgedName(
            RenderNameTagEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && living.hasData(
                        GratefulDeadAddon.LIVING_AGING)
                && LivingAgingState.get(
                                living)
                        .progress()
                        >= 0.62F) {
            event.setContent(
                    AgingText.obfuscatedName(
                            event.getContent()));
        }
    }

    private static void applyBlockAging(
            BlockAgingPayload payload) {
        AgingBlockClientCache.handleUpdate(
                payload.position(), payload.progress());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.levelRenderer == null) {
            return;
        }
        int breakerId =
                0x47440000
                        ^ payload.position().hashCode();
        minecraft.levelRenderer.destroyBlockProgress(
                breakerId,
                payload.position(),
                payload.crackStage());
    }

    private static int grassColor(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        int base = level != null && position != null
                ? BiomeColors.getAverageGrassColor(
                        level, position)
                : GrassColor.getDefaultColor();
        return tintBlockColor(base, position);
    }

    private static int foliageColor(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        if (tintIndex != 0) {
            return -1;
        }
        int base = level != null && position != null
                ? BiomeColors.getAverageFoliageColor(
                        level, position)
                : FoliageColor.getDefaultColor();
        return tintBlockColor(base, position);
    }

    private static int birchColor(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        return tintIndex == 0
                ? tintBlockColor(
                        FoliageColor.getBirchColor(),
                        position)
                : -1;
    }

    private static int spruceColor(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        return tintIndex == 0
                ? tintBlockColor(
                        FoliageColor.getEvergreenColor(),
                        position)
                : -1;
    }

    private static int lilyPadColor(
            BlockState state,
            @Nullable BlockAndTintGetter level,
            @Nullable BlockPos position,
            int tintIndex) {
        return tintIndex == 0
                ? tintBlockColor(2129968, position)
                : -1;
    }

    private static int tintBlockColor(
            int originalColor,
            @Nullable BlockPos position) {
        float aging = position != null
                ? AgingVisuals.blockCurve(
                        AgingBlockClientCache
                                .getProgress(position))
                : 0.0F;
        return aging > 0.0F
                ? AgingVisuals.tintBlockRgb(
                        originalColor, aging)
                : originalColor;
    }
}
