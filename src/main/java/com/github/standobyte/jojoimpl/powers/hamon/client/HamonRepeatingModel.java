package com.github.standobyte.jojoimpl.powers.hamon.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;

public abstract class HamonRepeatingModel {
    private float length;
    private float yRotation;
    private float xRotation;

    public void setup(float length, float yRotation, float xRotation) {
        this.length = length;
        this.yRotation = yRotation;
        this.xRotation = xRotation;
    }

    @Nullable
    protected abstract ModelPart getMainPart();

    protected abstract float getMainPartLength();

    protected abstract ModelPart getRepeatingPart();

    protected abstract float getRepeatingPartLength();

    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexBuilder, int packedLight, int color) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yRotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(xRotation));

        float modelLength = length;
        ModelPart mainPart = getMainPart();
        float mainPartLength = getMainPartLength() / 16.0F;
        if (mainPart != null && modelLength >= mainPartLength) {
            mainPart.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, color);
            modelLength -= mainPartLength;
        }

        ModelPart repeatingPart = getRepeatingPart();
        float repeatingLength = getRepeatingPartLength() / 16.0F;
        while (modelLength >= repeatingLength) {
            repeatingPart.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, color);
            modelLength -= repeatingLength;
            poseStack.translate(0.0F, 0.0F, repeatingLength);
        }
        repeatingPart.render(poseStack, vertexBuilder, packedLight, OverlayTexture.NO_OVERLAY, color);

        poseStack.popPose();
    }
}
