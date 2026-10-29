package rotp.addons.tusk.client;

import java.util.List;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.tusk.TuskAddon;
import rotp.addons.tusk.client.TuskClientRenderDiagnostics.GeometryCapture;
import rotp.addons.tusk.entity.WormholeArmEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class WormholeArmRenderer
        extends EntityRenderer<WormholeArmEntity> {
    private static final ResourceLocation WORMHOLE_TEXTURE =
            TuskAddon.id("textures/entity/wormhole.png");
    private final ArmModel model;
    private final ModelPart portalPlane;

    public WormholeArmRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new ArmModel(createLayer().bakeRoot());
        portalPlane = TuskWormholeRenderUtil.createPortalPlane();
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            WormholeArmEntity entity) {
        Player owner = entity.level()
                .getPlayerByUUID(entity.getOwnerUuid());
        if (owner instanceof AbstractClientPlayer clientPlayer) {
            return clientPlayer.getSkin().texture();
        }
        return DefaultPlayerSkin.get(entity.getOwnerUuid() != null
                ? entity.getOwnerUuid()
                : entity.getUUID()).texture();
    }

    @Override
    public void render(
            WormholeArmEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        StandVisualContext visualContext =
                TuskVisuals.forOwnerUuid(
                        entity.level(),
                        entity.getOwnerUuid(),
                        partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource standBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        Player owner = entity.level()
                .getPlayerByUUID(entity.getOwnerUuid());
        boolean slim = owner instanceof AbstractClientPlayer player
                && player.getSkin().model() == PlayerSkin.Model.SLIM;
        model.setPose(
                entity.isLeftArm(),
                slim,
                (Mth.lerp(
                                partialTick,
                                entity.xRotO,
                                entity.getXRot())
                                - 90.0F)
                        * Mth.DEG_TO_RAD,
                yRotation * Mth.DEG_TO_RAD);
        portalPlane.yRot = (entity.tickCount + partialTick)
                * TuskWormholeRenderUtil.ROTATION_RADIANS_PER_TICK;
        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        ResourceLocation armTexture = getTextureLocation(entity);
        VertexConsumer vertices = standBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        armTexture));
        try (GeometryCapture capture =
                TuskClientRenderDiagnostics.beginGeometry(
                        TuskClientRenderDiagnostics.VisualRoute
                                .REMOTE_ARM_MODEL,
                        entity,
                        entity.getOwnerUuid(),
                        armTexture,
                        vertices)) {
            model.root.render(
                    poseStack,
                    capture.consumer(),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    0xFFFFFFFF);
        }
        TuskClientRenderDiagnostics.recordEntityRender(
                TuskClientRenderDiagnostics.VisualRoute
                        .REMOTE_ARM_MODEL,
                entity,
                entity.getOwnerUuid());
        TuskWormholeRenderUtil.renderModelPart(
                portalPlane,
                poseStack,
                standBuffer,
                WORMHOLE_TEXTURE,
                packedLight,
                TuskClientRenderDiagnostics.VisualRoute.REMOTE_ARM,
                entity,
                entity.getOwnerUuid());
        poseStack.popPose();
        TuskClientRenderDiagnostics.recordEntityRender(
                TuskClientRenderDiagnostics.VisualRoute.REMOTE_ARM,
                entity,
                entity.getOwnerUuid());
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                standBuffer,
                packedLight);
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartPose pose = PartPose.offset(0.0F, -1.0F, 0.0F);
        root.addOrReplaceChild(
                "right",
                armBox(40, 16, -2.0F, 4.0F, 0.0F),
                pose);
        root.addOrReplaceChild(
                "left",
                armBox(32, 48, -2.0F, 4.0F, 0.0F),
                pose);
        root.addOrReplaceChild(
                "rightSlim",
                armBox(40, 16, -2.0F, 3.0F, 0.0F),
                pose);
        root.addOrReplaceChild(
                "leftSlim",
                armBox(32, 48, -2.0F, 3.0F, 0.0F),
                pose);
        root.addOrReplaceChild(
                "rightSleeve",
                armBox(40, 32, -2.0F, 4.0F, 0.25F),
                pose);
        root.addOrReplaceChild(
                "leftSleeve",
                armBox(48, 48, -2.0F, 4.0F, 0.25F),
                pose);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static CubeListBuilder armBox(
            int textureU,
            int textureV,
            float x,
            float width,
            float inflate) {
        return CubeListBuilder.create()
                .texOffs(textureU, textureV)
                .addBox(
                        x,
                        0.0F,
                        -2.0F,
                        width,
                        12.0F,
                        4.0F,
                        new CubeDeformation(inflate));
    }

    private static final class ArmModel {
        private final ModelPart root;
        private final ModelPart right;
        private final ModelPart left;
        private final ModelPart rightSlim;
        private final ModelPart leftSlim;
        private final ModelPart rightSleeve;
        private final ModelPart leftSleeve;
        private final List<ModelPart> parts;

        private ArmModel(ModelPart root) {
            this.root = root;
            right = root.getChild("right");
            left = root.getChild("left");
            rightSlim = root.getChild("rightSlim");
            leftSlim = root.getChild("leftSlim");
            rightSleeve = root.getChild("rightSleeve");
            leftSleeve = root.getChild("leftSleeve");
            parts = List.of(
                    right,
                    left,
                    rightSlim,
                    leftSlim,
                    rightSleeve,
                    leftSleeve);
        }

        private void setPose(
                boolean leftSide,
                boolean slim,
                float xRot,
                float yRot) {
            parts.forEach(part -> part.visible = false);
            if (leftSide) {
                (slim ? leftSlim : left).visible = true;
                leftSleeve.visible = true;
            } else {
                (slim ? rightSlim : right).visible = true;
                rightSleeve.visible = true;
            }
            for (ModelPart part : parts) {
                part.xRot = xRot;
                part.yRot = yRot;
                part.zRot = 0.0F;
            }
        }
    }
}
