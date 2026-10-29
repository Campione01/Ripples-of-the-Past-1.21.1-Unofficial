package rotp.addons.neo_stand.client;

import rotp.core.api.client.render.EntityPostRenderContext;
import rotp.core.api.client.render.EntityPostRenderExtension;
import rotp.core.client.entityrender.entities.SimpleEntityModel;
import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.entityrender.parsemodel.loader.ResourceModelEntry;
import rotp.core.client.entityrender.parsemodel.loader.RotpGeckoModelLoader;
import rotp.core.client.standskin.StandSkin;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class NeoEmeraldRenderer
        implements EntityPostRenderExtension {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples",
                    "textures/entity/projectiles/hg_emerald.png");
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(
                    "jojo_ripples", "hg_emerald");

    private ResourceModelEntry resourceModel;

    @Override
    public void afterEntityRender(
            EntityPostRenderContext context) {
        if (!(context.entity()
                instanceof HGEmeraldEntity entity)
                || entity.isInvisible()
                        && entity.isInvisibleTo(
                                Minecraft.getInstance().player)) {
            return;
        }
        SimpleEntityModel<HGEmeraldEntity> model =
                model(entity);
        if (model == null) {
            return;
        }
        StandSkin skin =
                SimpleEntityRenderer.getStandSkin(entity);
        ResourceLocation texture = skin != null
                ? skin.getTexture(TEXTURE)
                : TEXTURE;
        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        float xRotation = Mth.lerp(
                context.partialTick(),
                entity.xRotO,
                entity.getXRot());
        model.setupAnim(
                entity,
                0.0F,
                0.0F,
                entity.tickCount + context.partialTick(),
                context.entityYaw(),
                xRotation);
        model.renderToBuffer(
                poseStack,
                context.buffer().getBuffer(
                        model.renderType(texture)),
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                BlitFloat.NO_TINT);
        poseStack.popPose();
    }

    @SuppressWarnings("unchecked")
    private SimpleEntityModel<HGEmeraldEntity> model(
            HGEmeraldEntity entity) {
        RotpGeckoModelLoader loader =
                RotpGeckoModelLoader.getInstance();
        if (loader == null) {
            return null;
        }
        if (resourceModel == null) {
            resourceModel =
                    loader.getModelContainer(MODEL);
            resourceModel.rendererInit(
                    SimpleEntityModel::new);
        }
        return resourceModel.getModel(
                SimpleEntityRenderer.getStandSkin(entity));
    }
}
