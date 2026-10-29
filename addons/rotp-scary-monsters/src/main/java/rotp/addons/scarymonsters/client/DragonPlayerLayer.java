package rotp.addons.scarymonsters.client;

import rotp.core.client.ClientGlobals;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class DragonPlayerLayer extends RenderLayer<
        AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation NORMAL = ScaryMonstersAddon.id(
            "textures/entity/stand/dragon.png");
    private static final ResourceLocation SLIM = ScaryMonstersAddon.id(
            "textures/entity/stand/dragon_slim.png");
    private final boolean slim;

    public DragonPlayerLayer(
            RenderLayerParent<AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            boolean slim) {
        super(parent);
        this.slim = slim;
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
        if (!ClientGlobals.canSeeStands
                || player.isInvisible()
                || !ScaryMonstersAddon.state(player).dragonForm()) {
            return;
        }
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityTranslucent(slim ? SLIM : NORMAL));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
    }
}
