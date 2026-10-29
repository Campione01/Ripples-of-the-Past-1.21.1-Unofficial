package rotp.addons.wonderofu.client;

import rotp.core.client.entityrender.entities.SimpleEntityRenderer;
import rotp.core.client.ui.utils.BlitFloat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.wonderofu.WonderOfUAddon;
import rotp.addons.wonderofu.entity.CarCalamityEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;

// 1.16 drew the car with a plain MobRenderer: the model's cutout material
// at full alpha for every viewer. The car is not part of the Stand, so the
// caster's Stand and the viewer's Stand transparency settings do not reach it.
// As a mob it also flashed red when struck and tipped over while dying.
public final class CarCalamityRenderer
        extends SimpleEntityRenderer<
                CarCalamityEntity, CarCalamityModel> {
    private static final float DEATH_FLIP_DEGREES = 90.0F;

    public CarCalamityRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        initTexture(
                WonderOfUAddon.id(
                        "textures/entity/car_projectile.png"),
                false);
        initModel(new CarCalamityModel(
                context.bakeLayer(
                        CarCalamityModel.LAYER_LOCATION)));
        offsetModelByEntityHeight(false);
        shadowRadius = 0.0F;
    }

    @Override
    protected void rotateModel(
            CarCalamityModel model,
            CarCalamityEntity entity,
            float partialTick,
            float yRotation,
            float xRotation,
            PoseStack poseStack) {
        super.rotateModel(
                model, entity, partialTick, yRotation, xRotation, poseStack);
        liftToFeet(poseStack);
        if (entity.deathTime > 0) {
            tipOver(entity, partialTick, yRotation, poseStack);
        }
    }

    // LivingEntityRenderer's translate(0, -1.501, 0): the body's base pivot
    // sits on the entity's feet, so the car is drawn from the feet up inside
    // its hitbox. This pose is flipped (1, -1, -1), hence the negative lift.
    private static void liftToFeet(PoseStack poseStack) {
        poseStack.translate(
                0.0F, -CarCalamityModel.BODY_PIVOT_Y / 16.0F, 0.0F);
    }

    // LivingEntityRenderer.setupRotations' death roll. This pose is flipped
    // (1, -1, -1) and the model turns itself by the yaw, so the roll is taken
    // about the car's base pivot, now at the feet, and its own yawed Z axis.
    private static void tipOver(
            CarCalamityEntity entity,
            float partialTick,
            float yRotation,
            PoseStack poseStack) {
        float progress = Math.min(1.0F, Mth.sqrt(
                (entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F));
        float pivotY = CarCalamityModel.BODY_PIVOT_Y / 16.0F;
        float yaw = yRotation * Mth.DEG_TO_RAD;
        poseStack.translate(0.0F, pivotY, 0.0F);
        poseStack.mulPose(Axis.YP.rotation(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                progress * DEATH_FLIP_DEGREES));
        poseStack.mulPose(Axis.YP.rotation(-yaw));
        poseStack.translate(0.0F, -pivotY, 0.0F);
    }

    @Override
    protected void renderModel(
            CarCalamityEntity entity,
            CarCalamityModel model,
            float partialTick,
            PoseStack poseStack,
            VertexConsumer vertexBuilder,
            int packedLight) {
        model.renderToBuffer(
                poseStack,
                vertexBuilder,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F),
                BlitFloat.NO_TINT);
    }
}
