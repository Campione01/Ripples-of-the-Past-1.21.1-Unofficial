package rotp.addons.ultimate_lifeform.client;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.entity.UltimateRabbitEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class UltimateRabbitRenderer
        extends MobRenderer<
                UltimateRabbitEntity,
                RabbitModel<UltimateRabbitEntity>> {
    private static final ResourceLocation TEXTURE =
            UltimateLifeformAddon.id(
                    "textures/entity/"
                            + "ultimate_lifeform_killer_rabbit.png");

    public UltimateRabbitRenderer(
            EntityRendererProvider.Context context) {
        super(
                context,
                new RabbitModel<>(
                        context.bakeLayer(ModelLayers.RABBIT)),
                0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(
            UltimateRabbitEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            UltimateRabbitEntity entity,
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
                ClientReceipt.RABBIT_ENTITY,
                "bunny_summon");
    }
}
