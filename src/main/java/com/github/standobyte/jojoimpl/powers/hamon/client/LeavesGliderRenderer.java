package com.github.standobyte.jojoimpl.powers.hamon.client;

import com.github.standobyte.jojoimpl.powers.hamon.entity.LeavesGliderEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.client.CrazyDBlockBulletRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;

public class LeavesGliderRenderer extends EntityRenderer<LeavesGliderEntity> {
    private static final ResourceLocation DEFAULT_OAK_LEAVES = ResourceLocation.withDefaultNamespace("textures/block/oak_leaves.png");

    private final LeavesGliderModel model = LeavesGliderModel.create();

    public LeavesGliderRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LeavesGliderEntity entity) {
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                .getBlockModel(entity.getLeavesBlock()).getParticleIcon(ModelData.EMPTY);
        ResourceLocation texture = sprite != null ? CrazyDBlockBulletRenderer.getSpriteTexture(sprite) : null;
        return texture != null ? texture : DEFAULT_OAK_LEAVES;
    }

    @Override
    public void render(LeavesGliderEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
            ResourceLocation texture = getTextureLocation(entity);
            VertexConsumer vertexBuilder = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));

            poseStack.pushPose();
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.translate(0.0D, -entity.getBbHeight(), 0.0D);
            model.setupAnim(entity, 0.0F, 0.0F, entity.tickCount + partialTick, entityYaw, entity.getXRot());
            model.renderToBuffer(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, foliageColor(entity));
            poseStack.popPose();
        }

        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static int foliageColor(LeavesGliderEntity entity) {
        int color = Minecraft.getInstance().getBlockColors().getColor(entity.getLeavesBlock(), entity.level(), entity.blockPosition(), 0);
        if (color < 0) {
            color = 0xFFFFFF;
        }
        return 0xFF000000 | color;
    }
}
