package rotp.addons.kingcrimson.client;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.effect.KingCrimsonHalfBlindnessEffect;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class KingCrimsonHalfBlindnessLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final ResourceLocation LEFT_TEXTURE =
            KingCrimsonAddon.id("textures/entity/half_left.png");
    private static final ResourceLocation RIGHT_TEXTURE =
            KingCrimsonAddon.id("textures/entity/half_right.png");

    private final DeferredHolder<
            MobEffect,
            KingCrimsonHalfBlindnessEffect> effect;
    private final ResourceLocation texture;

    private KingCrimsonHalfBlindnessLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            DeferredHolder<
                    MobEffect,
                    KingCrimsonHalfBlindnessEffect> effect,
            ResourceLocation texture) {
        super(parent);
        this.effect = effect;
        this.texture = texture;
    }

    public static KingCrimsonHalfBlindnessLayer left(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent) {
        return new KingCrimsonHalfBlindnessLayer(
                parent,
                KingCrimsonAddon.HALF_BLINDNESS_LEFT,
                LEFT_TEXTURE);
    }

    public static KingCrimsonHalfBlindnessLayer right(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent) {
        return new KingCrimsonHalfBlindnessLayer(
                parent,
                KingCrimsonAddon.HALF_BLINDNESS_RIGHT,
                RIGHT_TEXTURE);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (player.isInvisible()
                || player.isSpectator()
                || !player.hasEffect(effect)) {
            return;
        }
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(texture));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F),
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
        if (!(entity instanceof AbstractClientPlayer player)
                || player.isInvisible()
                || player.isSpectator()
                || !player.hasEffect(effect)
                || !(entityRenderer.getModel()
                        instanceof HumanoidModel<?> model)) {
            return;
        }
        FirstPersonModelLayer.defaultRender(
                side,
                poseStack,
                buffer,
                light,
                player,
                entityRenderer,
                model,
                texture,
                partialTick);
    }
}
