package com.github.standobyte.jojoimpl.stands.goldexperience.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.v1_21_4_stuff.missingmethods.Model_1_21_2plus;
import com.github.standobyte.jojo.client.rendertype.CustomRenderType;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.CrazyDBlockBulletRenderer;
import com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;

public class GETransformationRenderer<T extends GETransformationEntity> extends EntityRenderer<T> {

    public GETransformationRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(T entity, float yRotation, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (entity.getVehicle() != null && entity.getVehicle() == player) {
            return;
        }
        if (entity.isInvisible() && (player == null || entity.isInvisibleTo(player))) {
            return;
        }

        float age = entity.getTfProgressTime(partialTick);
        float duration = Math.max(entity.getDuration(), 1);
        float itemSourceAge = Math.max(entity.getRenderAsItemTime(), 1.0F);
        if (age < itemSourceAge) {
            float scale = Mth.clamp(1.0F - age / itemSourceAge, 0.0F, 1.0F);
            renderSource(entity, scale, yRotation, partialTick, poseStack, buffer, packedLight);
        }
        else {
            float targetPhase = Math.max(duration - itemSourceAge, 1.0F);
            float progress = Mth.clamp((age - itemSourceAge) / targetPhase, 0.0F, 1.0F);
            renderTarget(entity, progress, yRotation, partialTick, poseStack, buffer, packedLight);
        }

        super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
    }

    private void renderSource(T entity, float scale, float yRotation, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (scale <= 0.0F) {
            return;
        }

        Entity sourceEntity = entity.getSourceEntityForRender();
        if (sourceEntity != null) {
            renderScaledEntity(sourceEntity, scale, yRotation, partialTick, poseStack, buffer, packedLight);
            return;
        }

        BlockState sourceBlock = entity.getSourceBlockState();
        if (sourceBlock != null && sourceBlock.getRenderShape() == RenderShape.MODEL) {
            renderScaledBlock(sourceBlock, scale, poseStack, buffer, packedLight);
            return;
        }

        ItemStack sourceItem = entity.getSourceItemView();
        if (!sourceItem.isEmpty()) {
            renderScaledItem(entity, sourceItem, scale, poseStack, buffer, packedLight);
        }
    }

    private void renderTarget(T entity, float progress, float yRotation, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Entity target = entity.getTransformationTarget();
        if (target != null && progress > 0.0F) {
            EntityRenderer<?> renderer = entityRenderDispatcher.getRenderer(target);
            if (target instanceof LivingEntity living && renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
                renderTransformationLiving(living, entity, livingRenderer, yRotation, partialTick,
                        poseStack, buffer, packedLight, progress);
            }
            else {
                renderScaledEntity(target, progress, yRotation, partialTick, poseStack, buffer, packedLight);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <E extends LivingEntity, M extends EntityModel<E>> void renderTransformationLiving(E living,
            T transformationEntity, LivingEntityRenderer renderer, float yRotation, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight, float progress) {
        M targetModel = (M) renderer.getModel();
        if (!(targetModel instanceof Model_1_21_2plus modelPlus)) {
            renderScaledEntity(living, progress, yRotation, partialTick, poseStack, buffer, packedLight);
            return;
        }

        RenderLivingEvent.Pre<E, M> preEvent = NeoForge.EVENT_BUS.post(new RenderLivingEvent.Pre<>(
                living, renderer, partialTick, poseStack, buffer, packedLight));
        if (preEvent.isCanceled()) {
            return;
        }

        poseStack.pushPose();
        try {
            targetModel.attackTime = 0;
            targetModel.riding = false;
            targetModel.young = living.isBaby();

            float yHeadRotation = Mth.rotLerp(partialTick, living.yHeadRotO, living.yHeadRot);
            float yBodyRotation = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
            float headYaw = yHeadRotation - yBodyRotation;
            float xRotation = Mth.lerp(partialTick, transformationEntity.xRotO, transformationEntity.getXRot());

            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - yBodyRotation));
            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.scale(progress, progress, progress);
            poseStack.translate(0.0D, -1.501D, 0.0D);

            float ticks = living.tickCount + partialTick;
            targetModel.prepareMobModel(living, 0, 0, partialTick);
            targetModel.setupAnim(living, 0, 0, ticks, headYaw, xRotation);
            ResourceLocation texture = renderer.getTextureLocation(living);
            RenderType renderType = texture != null ? targetModel.renderType(texture) : null;
            if (renderType != null) {
                this.shadowRadius = 0.15F * progress;
                ModelStateEntry modelState = getModelState(targetModel, modelPlus);
                if (modelState == null) {
                    renderScaledEntity(living, progress, yRotation, partialTick, poseStack, buffer, packedLight);
                    return;
                }
                modelState.saveState();
                try {
                    modelState.lerp(progress);
                    VertexConsumer vertexBuilder = buffer.getBuffer(renderType);
                    float color = 0.25F + progress * 0.75F;
                    int argb = FastColor.ARGB32.colorFromFloat(1.0F, color, color, color);
                    targetModel.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, argb);

                    float blockOverlayAlpha = 1.0F - progress;
                    if (blockOverlayAlpha > 0.0F) {
                        ResourceLocation blockOverlay = getBlockOverlaySprite(transformationEntity);
                        if (blockOverlay != null) {
                            TextureScale textureScale = getTextureScale(texture);
                            VertexConsumer overlayBuilder = buffer.getBuffer(CustomRenderType.goldExperienceLifeformOverlay(
                                    blockOverlay, textureScale.x(), textureScale.y()));
                            int overlayColor = FastColor.ARGB32.colorFromFloat(blockOverlayAlpha, 1.0F, 1.0F, 1.0F);
                            targetModel.renderToBuffer(poseStack, overlayBuilder, packedLight, OverlayTexture.NO_OVERLAY, overlayColor);
                        }
                    }
                }
                finally {
                    modelState.restoreState();
                }
            }
        }
        finally {
            poseStack.popPose();
            NeoForge.EVENT_BUS.post(new RenderLivingEvent.Post<>(living, renderer, partialTick, poseStack, buffer, packedLight));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void renderScaledEntity(Entity entity, float scale, float yRotation, float partialTick,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        this.shadowRadius = 0.15F * scale;
        EntityRenderer renderer = entityRenderDispatcher.getRenderer(entity);
        renderer.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    private void renderScaledBlock(BlockState state, float scale, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-0.5D, 0.0D, -0.5D);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffer,
                packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderScaledItem(T entity, ItemStack stack, float scale, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                packedLight, OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), entity.getId());
        poseStack.popPose();
    }

    @Nullable
    private ResourceLocation getBlockOverlaySprite(T entity) {
        BlockState sourceBlock = entity.getSourceBlockState();
        if (sourceBlock != null) {
            return CrazyDBlockBulletRenderer.getBlockTexture(sourceBlock);
        }

        ItemStack sourceItem = entity.getSourceItemView();
        ResourceLocation sourceItemBlockTexture = getBlockItemTexture(sourceItem);
        if (sourceItemBlockTexture != null) {
            return sourceItemBlockTexture;
        }

        Entity sourceEntity = entity.getSourceEntityForRender();
        if (sourceEntity instanceof ItemEntity itemEntity) {
            return getBlockItemTexture(itemEntity.getItem());
        }
        return null;
    }

    @Nullable
    private static ResourceLocation getBlockItemTexture(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
            return CrazyDBlockBulletRenderer.getBlockTexture(blockItem.getBlock().defaultBlockState());
        }
        return null;
    }

    private static TextureScale getTextureScale(ResourceLocation texture) {
        return TEXTURE_SCALE_CACHE.computeIfAbsent(texture, GETransformationRenderer::readTextureScale);
    }

    private static TextureScale readTextureScale(ResourceLocation texture) {
        return Minecraft.getInstance().getResourceManager().getResource(texture)
                .map(GETransformationRenderer::readTextureScale)
                .orElse(TextureScale.DEFAULT);
    }

    private static TextureScale readTextureScale(Resource resource) {
        try (InputStream stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
            return new TextureScale(image.getWidth() / 16.0F, image.getHeight() / 16.0F);
        }
        catch (IOException | RuntimeException e) {
            return TextureScale.DEFAULT;
        }
    }

    private record TextureScale(float x, float y) {
        private static final TextureScale DEFAULT = new TextureScale(4.0F, 4.0F);
    }

    private static final Map<ResourceLocation, TextureScale> TEXTURE_SCALE_CACHE = new HashMap<>();

    @Nullable
    private ModelStateEntry getModelState(EntityModel<?> model, Model_1_21_2plus modelPlus) {
        Collection<ModelPart> modelParts = modelPlus.jojo_ripples$allParts();
        if (modelParts == null || modelParts.isEmpty()) {
            Class<?> modelClass = model.getClass();
            if (!MISSING_MODEL_PARTS_LOG.containsKey(modelClass)) {
                MISSING_MODEL_PARTS_LOG.put(modelClass, Boolean.TRUE);
                JojoMod.getLogger().warn(
                        "Gold Experience lifeform transformation renderer could not access model parts for {}; falling back to scaled entity rendering.",
                        modelClass.getName());
            }
            return null;
        }
        return MODEL_PARTS_CACHE.computeIfAbsent(model, ignored -> new ModelStateEntry(modelParts));
    }

    private static final Map<EntityModel<?>, ModelStateEntry> MODEL_PARTS_CACHE = new IdentityHashMap<>();
    private static final Map<Class<?>, Boolean> MISSING_MODEL_PARTS_LOG = new IdentityHashMap<>();

    private static class ModelStateEntry {
        private final Map<ModelPart, ModelPartState> state;

        private ModelStateEntry(Collection<ModelPart> modelParts) {
            Map<ModelPart, float[]> stateZero = createStateZero(modelParts);
            this.state = modelParts.stream().collect(Collectors.toMap(part -> part, part -> {
                float[] zero = stateZero.get(part);
                return new ModelPartState()
                        .withNormalState(part)
                        .withStateZero(zero[0], zero[1], zero[2], zero[3], zero[4], zero[5]);
            }, (first, second) -> first, IdentityHashMap::new));
        }

        private void saveState() {
            state.forEach((part, partState) -> partState.saveState(part));
        }

        private void lerp(float progress) {
            state.forEach((part, partState) -> partState.lerp(part, progress));
        }

        private void restoreState() {
            state.forEach((part, partState) -> partState.restoreState(part));
        }
    }

    private static Map<ModelPart, float[]> createStateZero(Collection<ModelPart> modelParts) {
        Map<ModelPart, float[]> map = new IdentityHashMap<>();
        modelParts.forEach(part -> {
            float minX = part.cubes.stream().map(cube -> cube.minX).min(Float::compare).orElse(0F);
            float maxX = part.cubes.stream().map(cube -> cube.maxX).max(Float::compare).orElse(0F);
            float minY = part.cubes.stream().map(cube -> cube.minY).min(Float::compare).orElse(0F);
            float maxY = part.cubes.stream().map(cube -> cube.maxY).max(Float::compare).orElse(0F);
            float minZ = part.cubes.stream().map(cube -> cube.minZ).min(Float::compare).orElse(0F);
            float maxZ = part.cubes.stream().map(cube -> cube.maxZ).max(Float::compare).orElse(0F);

            float x = -(minX + maxX / 2);
            float y = -(minY + maxY / 2);
            float z = -(minZ + maxZ / 2);
            y += part.y;
            map.put(part, new float[] { x, y, z, 0, 0, 0 });
        });
        return map;
    }

    private static class ModelPartState {
        private final float[] stateNormal = new float[6];
        private final float[] stateZero = new float[6];
        private final float[] stateSaved = new float[6];

        private ModelPartState withStateZero(float x, float y, float z,
                float xRot, float yRot, float zRot) {
            stateZero[0] = x;
            stateZero[1] = y;
            stateZero[2] = z;
            stateZero[3] = xRot;
            stateZero[4] = yRot;
            stateZero[5] = zRot;
            return this;
        }

        private ModelPartState withNormalState(ModelPart part) {
            stateNormal[0] = part.x;
            stateNormal[1] = part.y;
            stateNormal[2] = part.z;
            stateNormal[3] = part.xRot;
            stateNormal[4] = part.yRot;
            stateNormal[5] = part.zRot;
            return this;
        }

        private void saveState(ModelPart part) {
            stateSaved[0] = part.x;
            stateSaved[1] = part.y;
            stateSaved[2] = part.z;
            stateSaved[3] = part.xRot;
            stateSaved[4] = part.yRot;
            stateSaved[5] = part.zRot;
        }

        private void restoreState(ModelPart part) {
            part.x = stateSaved[0];
            part.y = stateSaved[1];
            part.z = stateSaved[2];
            part.xRot = stateSaved[3];
            part.yRot = stateSaved[4];
            part.zRot = stateSaved[5];
        }

        private void lerp(ModelPart part, float progress) {
            part.x = Mth.lerp(progress, stateZero[0], stateNormal[0]);
            part.y = Mth.lerp(progress, stateZero[1], stateNormal[1]);
            part.z = Mth.lerp(progress, stateZero[2], stateNormal[2]);
            part.xRot = Mth.lerp(progress, stateZero[3], stateNormal[3]);
            part.yRot = Mth.lerp(progress, stateZero[4], stateNormal[4]);
            part.zRot = Mth.lerp(progress, stateZero[5], stateNormal[5]);
        }
    }
}
