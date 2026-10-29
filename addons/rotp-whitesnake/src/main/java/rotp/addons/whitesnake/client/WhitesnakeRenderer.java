package rotp.addons.whitesnake.client;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import rotp.core.client.entityrender.stand.StandEntityModel;
import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.entityrender.stand.StandEntityRenderState.ObstructionRenderMode;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.compat.v1_21_4.renderstate.RenderStateCrutches;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;

import rotp.addons.whitesnake.disguise.DisguiseTypePolicy;
import rotp.addons.whitesnake.entity.WhitesnakeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class WhitesnakeRenderer extends StandEntityRenderer<
        WhitesnakeEntity,
        WhitesnakeRenderState,
        StandEntityModel<WhitesnakeEntity, WhitesnakeRenderState>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float TRANSFORM_TICKS = 20.0F;
    private static final int MAX_PREVIEW_CACHE = 32;

    private final Map<ResourceLocation, LivingEntity> previewEntities =
            new LinkedHashMap<>(16, 0.75F, true);
    private final Set<ResourceLocation> rejectedRenderers =
            new HashSet<>();
    private ClientLevel previewLevel;
    private boolean renderingDisguise;

    public WhitesnakeRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new WhitesnakeGlintLayer(this));
    }

    @Override
    public WhitesnakeRenderState createRenderState() {
        return new WhitesnakeRenderState();
    }

    @Override
    public void extractRenderState(
            WhitesnakeEntity entity,
            WhitesnakeRenderState renderState,
            float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.disguiseType = entity.getDisguiseType();
        renderState.disguiseChangedTick =
                entity.getDisguiseChangedTick();
    }

    @Override
    public void render(
            WhitesnakeEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        WhitesnakeRenderState renderState =
                createRenderState(entity, partialTick);
        StandVisualContext visualContext =
                renderState.visualContext;
        if (renderState.disguiseType.isPresent()
                && visualContext != null
                && !entity.isArmsOnlyMode()
                && renderState.obstructionRenderMode == ObstructionRenderMode.NONE
                && renderDisguise(
                        entity,
                        renderState,
                        entityYaw,
                        partialTick,
                        poseStack,
                        bufferSource,
                        packedLight)) {
            return;
        }
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight);
    }

    private boolean renderDisguise(
            WhitesnakeEntity whitesnake,
            WhitesnakeRenderState renderState,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        StandVisualContext visualContext =
                renderState.visualContext;
        if (visualContext.effectiveAlpha() <= 0.0F
                || renderState.isInvisibleToPlayer
                        && !renderState.visibleForSpectator) {
            return true;
        }
        if (renderingDisguise) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        ResourceLocation typeId =
                renderState.disguiseType.orElse(null);
        if (level == null
                || typeId == null
                || rejectedRenderers.contains(typeId)) {
            return false;
        }
        if (previewLevel != level) {
            clearPreviewCache();
            previewLevel = level;
        }

        LivingEntity preview = previewEntities.get(typeId);
        if (preview == null) {
            EntityType<?> entityType =
                    BuiltInRegistries.ENTITY_TYPE
                            .getOptional(typeId)
                            .orElse(null);
            preview = DisguiseTypePolicy.createAllowedInstance(
                    entityType, level);
            if (preview == null) {
                rejectedRenderers.add(typeId);
                return false;
            }
            cachePreview(typeId, preview);
        }
        copyRenderPose(whitesnake, preview);

        float transformProgress = Mth.clamp(
                (whitesnake.tickCount + partialTick
                        - renderState.disguiseChangedTick)
                        / TRANSFORM_TICKS,
                0.05F,
                1.0F);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        bufferSource,
                        visualContext.effectiveAlpha());
        RenderStateCrutches.Snapshot snapshot =
                preRender(renderState);
        poseStack.pushPose();
        renderingDisguise = true;
        try {
            poseStack.scale(
                    transformProgress,
                    transformProgress,
                    transformProgress);
            renderEntity(
                    preview,
                    entityYaw,
                    partialTick,
                    poseStack,
                    alphaBuffer,
                    packedLight);
            return true;
        }
        catch (RuntimeException | LinkageError exception) {
            rejectedRenderers.add(typeId);
            previewEntities.remove(typeId);
            LOGGER.warn(
                    "Whitesnake disguise renderer rejected entity type {}; "
                            + "falling back to the Stand renderer.",
                    typeId,
                    exception);
            return false;
        }
        finally {
            renderingDisguise = false;
            poseStack.popPose();
            postRender(snapshot);
        }
    }

    private void clearPreviewCache() {
        for (LivingEntity preview : previewEntities.values()) {
            preview.remove(
                    net.minecraft.world.entity.Entity
                            .RemovalReason.DISCARDED);
        }
        previewEntities.clear();
        rejectedRenderers.clear();
    }

    private void cachePreview(
            ResourceLocation typeId, LivingEntity preview) {
        previewEntities.put(typeId, preview);
        if (previewEntities.size() <= MAX_PREVIEW_CACHE) {
            return;
        }
        var iterator = previewEntities.entrySet().iterator();
        if (iterator.hasNext()) {
            var eldest = iterator.next();
            eldest.getValue().remove(
                    net.minecraft.world.entity.Entity
                            .RemovalReason.DISCARDED);
            iterator.remove();
        }
    }

    private static void copyRenderPose(
            WhitesnakeEntity whitesnake, LivingEntity preview) {
        preview.setPos(
                whitesnake.getX(),
                whitesnake.getY(),
                whitesnake.getZ());
        preview.setYRot(whitesnake.getYRot());
        preview.setXRot(whitesnake.getXRot());
        preview.xRotO = whitesnake.xRotO;
        preview.yBodyRot = whitesnake.yBodyRot;
        preview.yBodyRotO = whitesnake.yBodyRotO;
        preview.yHeadRot = whitesnake.yHeadRot;
        preview.yHeadRotO = whitesnake.yHeadRotO;
        preview.tickCount = whitesnake.tickCount;
        preview.hurtTime = whitesnake.hurtTime;
        preview.deathTime = whitesnake.deathTime;
        preview.setPose(whitesnake.getPose());
        preview.setDeltaMovement(whitesnake.getDeltaMovement());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderEntity(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        EntityRenderer renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(entity);
        renderer.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                bufferSource,
                packedLight);
    }
}
