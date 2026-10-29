package rotp.addons.metallica.client;

import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.client.util.functions.ClientUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.metallica.MetallicaAddon;
import rotp.addons.metallica.entity.MetallicaStandEntity;
import rotp.addons.metallica.state.MetallicaIronState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class LackOfIronLayer<
        T extends LivingEntity,
        M extends EntityModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    public LackOfIronLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (!ClientGlobals.canSeeStands
                || entity.isInvisible()
                || !entity.hasEffect(
                        MetallicaAddon.LACK_OF_IRON)
                || !entity.hasData(MetallicaAddon.IRON_STATE)) {
            return;
        }

        float alpha = visualAlpha(entity, partialTick);
        if (alpha <= 0.0F) {
            return;
        }

        ResourceLocation texture =
                texture(getParentModel(), entity);
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        alpha);
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(texture));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (!ClientGlobals.canSeeStands
                || !entity.hasEffect(
                        MetallicaAddon.LACK_OF_IRON)
                || !entity.hasData(MetallicaAddon.IRON_STATE)
                || !(entityRenderer.getModel()
                        instanceof HumanoidModel<?> model)) {
            return;
        }
        float alpha = visualAlpha(entity, partialTick);
        if (alpha <= 0.0F) {
            return;
        }
        FirstPersonModelLayer.defaultRender(
                side,
                poseStack,
                AlphaMultiBufferSource.wrap(buffer, alpha),
                light,
                entity,
                entityRenderer,
                model,
                texture(model, entity),
                partialTick);
    }

    private static float visualAlpha(
            LivingEntity entity, float partialTick) {
        MetallicaIronState state =
                MetallicaAddon.ironState(entity);
        UUID sourceId =
                state.sourceOwner().orElse(null);
        Minecraft minecraft = Minecraft.getInstance();
        Player source = minecraft.level != null
                && sourceId != null
                ? minecraft.level.getPlayerByUUID(sourceId)
                : null;
        MetallicaStandEntity controller =
                source != null
                ? MetallicaAddon.controller(source)
                : null;
        if (controller == null
                || controller.clientStuff == null) {
            return 0.0F;
        }
        float lifecycleAlpha = controller.clientStuff.getAlpha(
                controller, partialTick);
        StandVisualContext visualContext =
                StandVisualContext.world(
                        controller, lifecycleAlpha);
        return visualContext.effectiveAlpha();
    }

    private static ResourceLocation texture(
            EntityModel<?> model, LivingEntity entity) {
        String canvas = model instanceof HumanoidModel<?>
                ? "t64x64"
                : "t64x32";
        boolean lowLight =
                entity.level().getMaxLocalRawBrightness(
                        entity.blockPosition()) < 8;
        return MetallicaAddon.id(
                "textures/entity/biped/layer/lack_of_iron/"
                        + canvas + "/"
                        + (lowLight ? "2.png" : "1.png"));
    }
}
