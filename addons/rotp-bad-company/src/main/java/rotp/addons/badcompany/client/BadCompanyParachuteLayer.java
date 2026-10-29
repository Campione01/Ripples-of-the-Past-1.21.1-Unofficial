package rotp.addons.badcompany.client;

import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.badcompany.BadCompanyAddon;
import rotp.addons.badcompany.entity.BadCompanySoldierEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public final class BadCompanyParachuteLayer
        extends RenderLayer<
                BadCompanySoldierEntity,
                BadCompanySoldierModel> {
    private static final ResourceLocation TEXTURE =
            BadCompanyAddon.id(
                    "textures/entity/stand/parachute.png");

    private final BadCompanyParachuteModel model;

    public BadCompanyParachuteLayer(
            RenderLayerParent<
                    BadCompanySoldierEntity,
                    BadCompanySoldierModel> parent,
            BadCompanyParachuteModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            BadCompanySoldierEntity entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!entity.isParachuting()) {
            return;
        }
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        BadCompanyVisuals.texture(entity, TEXTURE)));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        entity, 0.0F),
                0xFFFFFFFF);
    }
}
