package rotp.addons.mandom.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.mandom.MandomAddon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class RingoClockRenderer
        extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation TEXTURE =
            MandomAddon.id("textures/item/ringo_layer.png");

    private final EntityModelSet entityModels;
    private RingoClockModel model;

    public RingoClockRenderer() {
        super(
                Minecraft.getInstance()
                        .getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
        entityModels = Minecraft.getInstance().getEntityModels();
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        applyTransform(poseStack, displayContext);
        RingoClockModel clockModel = model();
        clockModel.setupAnim(
                Minecraft.getInstance().level != null
                        ? Minecraft.getInstance().level.getGameTime()
                        : 0.0F);
        clockModel.render(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight,
                packedOverlay);
        poseStack.popPose();
    }

    private RingoClockModel model() {
        if (model == null) {
            model = new RingoClockModel(entityModels.bakeLayer(
                    RingoClockModel.LAYER_LOCATION));
        }
        return model;
    }

    private static void applyTransform(
            PoseStack poseStack,
            ItemDisplayContext context) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
        switch (context) {
            case GUI -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(28.0F));
                poseStack.mulPose(Axis.YP.rotationDegrees(215.0F));
                poseStack.scale(2.35F, 2.35F, 2.35F);
            }
            case GROUND -> {
                poseStack.translate(0.0F, -0.28F, 0.0F);
                poseStack.scale(1.35F, 1.35F, 1.35F);
            }
            case FIXED -> {
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.scale(2.0F, 2.0F, 2.0F);
            }
            case FIRST_PERSON_LEFT_HAND,
                    THIRD_PERSON_LEFT_HAND ->
                handTransform(poseStack, true);
            case FIRST_PERSON_RIGHT_HAND,
                    THIRD_PERSON_RIGHT_HAND ->
                handTransform(poseStack, false);
            default -> poseStack.scale(1.6F, 1.6F, 1.6F);
        }
    }

    private static void handTransform(
            PoseStack poseStack, boolean leftHand) {
        poseStack.translate(
                leftHand ? -0.28F : 0.28F,
                -0.2F,
                0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                leftHand ? -90.0F : 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(1.55F, 1.55F, 1.55F);
    }
}
