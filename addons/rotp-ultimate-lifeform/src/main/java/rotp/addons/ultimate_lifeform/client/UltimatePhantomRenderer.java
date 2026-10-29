package rotp.addons.ultimate_lifeform.client;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.entity.UltimatePhantomEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.PhantomEyesLayer;
import net.minecraft.resources.ResourceLocation;

public final class UltimatePhantomRenderer
        extends MobRenderer<
                UltimatePhantomEntity,
                PhantomModel<UltimatePhantomEntity>> {
    private static final ResourceLocation TEXTURE =
            UltimateLifeformAddon.id(
                    "textures/entity/"
                            + "phantom_ultimate_lifeform.png");

    // 1.16 UltimateLifeformPhantomRenderer: the vanilla phantom's glowing
    // eyes, pitch and placement, always at the size-1 scale
    public UltimatePhantomRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new PhantomModel<>(
                        context.bakeLayer(ModelLayers.PHANTOM)),
                0.75F);
        addLayer(new PhantomEyesLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(
            UltimatePhantomEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(
            UltimatePhantomEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale) {
        super.setupRotations(
                entity, poseStack, bob, yBodyRot, partialTick, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(entity.getXRot()));
    }

    @Override
    protected void scale(
            UltimatePhantomEntity entity,
            PoseStack poseStack,
            float partialTick) {
        poseStack.scale(1.15F, 1.15F, 1.15F);
        poseStack.translate(0.0F, 1.3125F, 0.1875F);
    }

    @Override
    public void render(
            UltimatePhantomEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
        UltimateLifeformClientAcceptance.recordGlobalVisual(
                entity,
                ClientReceipt.PHANTOM_ENTITY,
                "phantom_summon");
    }
}
