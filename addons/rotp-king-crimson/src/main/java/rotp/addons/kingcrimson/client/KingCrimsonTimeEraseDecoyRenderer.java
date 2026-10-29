package rotp.addons.kingcrimson.client;

import rotp.core.adventure.npc.client.CharacterMobRenderer;

import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.player.PlayerModelPart;

public final class KingCrimsonTimeEraseDecoyRenderer
        extends CharacterMobRenderer<
                KingCrimsonTimeEraseDecoyEntity> {
    public KingCrimsonTimeEraseDecoyRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            KingCrimsonTimeEraseDecoyEntity entity,
            float entityYaw,
            float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource buffer,
            int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == entity.getOwner()
                && entity.getBoundingBox().inflate(0.1D)
                        .contains(minecraft.gameRenderer.getMainCamera().getPosition())) {
            return;
        }
        applySkinParts(regularPlayerModel, entity);
        applySkinParts(slimPlayerModel, entity);
        super.render(
                entity,
                entityYaw,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private static void applySkinParts(
            EntityModel<KingCrimsonTimeEraseDecoyEntity> model,
            KingCrimsonTimeEraseDecoyEntity entity) {
        if (!(model instanceof PlayerModel<?> playerModel)) {
            return;
        }
        playerModel.hat.visible =
                entity.isSkinPartShown(PlayerModelPart.HAT);
        playerModel.jacket.visible =
                entity.isSkinPartShown(PlayerModelPart.JACKET);
        playerModel.leftSleeve.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.LEFT_SLEEVE);
        playerModel.rightSleeve.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.RIGHT_SLEEVE);
        playerModel.leftPants.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.LEFT_PANTS_LEG);
        playerModel.rightPants.visible =
                entity.isSkinPartShown(
                        PlayerModelPart.RIGHT_PANTS_LEG);
    }
}
