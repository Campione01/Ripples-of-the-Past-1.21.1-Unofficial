package rotp.addons.scarymonsters.client;

import rotp.core.client.ClientGlobals;
import rotp.core.powersystem.standpower.StandPower;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** The 1.16 {@code TailLayer}: the swaying tail on the user's back while the Scary Monsters Stand is summoned. */
public final class ScaryMonstersTailLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TAIL =
            ScaryMonstersAddon.id("textures/entity/stand/tail.png");

    private final ScaryMonstersTailModel tailModel;

    public ScaryMonstersTailLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
            ScaryMonstersTailModel tailModel) {
        super(parent);
        this.tailModel = tailModel;
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
        if (!ClientGlobals.canSeeStands || player.isInvisible() || player.isSpectator()) {
            return;
        }
        StandPower power = StandPower.get(player);
        if (power == null
                || power.getPowerType() != ScaryMonstersAddon.SCARY_MONSTERS.get()
                || !power.isSummoned()) {
            return;
        }
        tailModel.setupAnim(ageInTicks);
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        tailModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.entityTranslucent(TAIL)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF);
        poseStack.popPose();
    }
}
