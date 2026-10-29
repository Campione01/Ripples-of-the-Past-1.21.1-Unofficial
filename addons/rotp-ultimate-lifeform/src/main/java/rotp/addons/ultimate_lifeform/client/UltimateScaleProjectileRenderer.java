package rotp.addons.ultimate_lifeform.client;

import java.util.Set;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.entity.UltimateScaleProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class UltimateScaleProjectileRenderer
        extends EntityRenderer<UltimateScaleProjectileEntity> {
    private static final ResourceLocation TEXTURE = UltimateLifeformAddon.id(
            "textures/entity/projectile/scale_projectile.png");
    private static final Direction[] DONOR_FACE_ORDER = {
            Direction.EAST, Direction.WEST, Direction.DOWN,
            Direction.UP, Direction.NORTH, Direction.SOUTH
    };
    private static final ModelPart.Cube[] SCALE_VERTICAL = scalePlane(
            0.0F, -2.0F, -5.0F, 0.0F, 4.0F, 10.0F);
    private static final ModelPart.Cube[] SCALE_HORIZONTAL = scalePlane(
            -2.0F, 0.0F, -5.0F, 4.0F, 0.0F, 10.0F);

    private static ModelPart.Cube[] scalePlane(
            float x, float y, float z, float width, float height, float depth) {
        ModelPart.Cube[] faces = new ModelPart.Cube[DONOR_FACE_ORDER.length];
        // Opposing zero-thickness faces have different UVs; preserve donor submission order.
        for (int index = 0; index < faces.length; index++) {
            faces[index] = new ModelPart.Cube(0, 0, x, y, z, width, height, depth,
                    0.0F, 0.0F, 0.0F, false, 32.0F, 32.0F, Set.of(DONOR_FACE_ORDER[index]));
        }
        return faces;
    }

    public UltimateScaleProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(UltimateScaleProjectileEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            UltimateScaleProjectileEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        poseStack.pushPose();
        try {
            poseStack.scale(1.0F, -1.0F, -1.0F);
            poseStack.translate(0.0D, -3.0D / 16.0D, 0.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
            PoseStack.Pose pose = poseStack.last();
            for (ModelPart.Cube face : SCALE_VERTICAL) {
                face.compile(pose, vertices, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }
            for (ModelPart.Cube face : SCALE_HORIZONTAL) {
                face.compile(pose, vertices, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }
        }
        finally {
            poseStack.popPose();
        }
        UltimateLifeformClientAcceptance.recordGlobalVisual(
                entity, ClientReceipt.SCALE_PROJECTILE, "scale_projectile");
        super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
    }
}
