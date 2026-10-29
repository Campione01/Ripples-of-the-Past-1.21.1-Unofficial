package rotp.addons.greenday.client;

import java.util.List;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.greenday.entity.ShootingArmEntity;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class ShootingArmRenderer
        extends EntityRenderer<ShootingArmEntity> {
    private final ShootingArmModel model;

    public ShootingArmRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new ShootingArmModel(
                createLayer().bakeRoot());
        shadowRadius = 0.15F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            ShootingArmEntity entity) {
        LivingEntity owner = entity.getOwner();
        if (owner instanceof AbstractClientPlayer player) {
            return player.getSkin().texture();
        }
        if (owner != null) {
            return rendererTexture(owner);
        }
        return DefaultPlayerSkin.get(entity.getUUID())
                .texture();
    }

    @Override
    public void render(
            ShootingArmEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        LivingEntity owner = entity.getOwner();
        StandVisualContext visualContext =
                GreenDayVisuals.fromArmOwner(
                        owner, partialTick);
        if (!GreenDayVisuals.canRender(visualContext)) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        boolean slim = owner instanceof AbstractClientPlayer player
                && player.getSkin().model()
                        == PlayerSkin.Model.SLIM;
        model.select(entity.isRight(), slim);

        poseStack.pushPose();
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                180.0F - yRotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(
                entity.getXRot() - 90.0F));
        VertexConsumer vertices = alphaBuffer.getBuffer(
                ModRenderTypes.standTranslucent(
                        getTextureLocation(entity)));
        model.render(
                poseStack,
                vertices,
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResourceLocation rendererTexture(Entity entity) {
        EntityRenderer renderer =
                entityRenderDispatcher.getRenderer(entity);
        return renderer.getTextureLocation(entity);
    }

    private static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        addArm(root, "right_arm", 40, 16, 4.0F, false);
        addArm(root, "left_arm", 32, 48, 4.0F, true);
        addArm(
                root,
                "right_arm_slim",
                40,
                16,
                3.0F,
                false);
        addArm(
                root,
                "left_arm_slim",
                32,
                48,
                3.0F,
                true);
        addOuterArm(
                root,
                "right_sleeve",
                40,
                32,
                4.0F,
                false);
        addOuterArm(
                root,
                "left_sleeve",
                48,
                48,
                4.0F,
                true);
        addOuterArm(
                root,
                "right_sleeve_slim",
                40,
                32,
                3.0F,
                false);
        addOuterArm(
                root,
                "left_sleeve_slim",
                48,
                48,
                3.0F,
                true);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addArm(
            PartDefinition root,
            String name,
            int textureU,
            int textureV,
            float width,
            boolean mirror) {
        root.addOrReplaceChild(
                name,
                box(
                        textureU,
                        textureV,
                        width,
                        mirror,
                        0.0F),
                PartPose.ZERO);
    }

    private static void addOuterArm(
            PartDefinition root,
            String name,
            int textureU,
            int textureV,
            float width,
            boolean mirror) {
        root.addOrReplaceChild(
                name,
                box(
                        textureU,
                        textureV,
                        width,
                        mirror,
                        0.25F),
                PartPose.ZERO);
    }

    private static CubeListBuilder box(
            int textureU,
            int textureV,
            float width,
            boolean mirror,
            float deformation) {
        CubeListBuilder cubes = CubeListBuilder.create()
                .texOffs(textureU, textureV);
        if (mirror) {
            cubes = cubes.mirror();
        }
        return cubes.addBox(
                -width * 0.5F,
                -10.0F,
                -2.0F,
                width,
                12.0F,
                4.0F,
                new CubeDeformation(deformation));
    }

    private static final class ShootingArmModel {
        private final ModelPart root;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightArmSlim;
        private final ModelPart leftArmSlim;
        private final ModelPart rightSleeve;
        private final ModelPart leftSleeve;
        private final ModelPart rightSleeveSlim;
        private final ModelPart leftSleeveSlim;
        private final List<ModelPart> allParts;

        private ShootingArmModel(ModelPart root) {
            this.root = root;
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
            rightArmSlim =
                    root.getChild("right_arm_slim");
            leftArmSlim = root.getChild("left_arm_slim");
            rightSleeve = root.getChild("right_sleeve");
            leftSleeve = root.getChild("left_sleeve");
            rightSleeveSlim =
                    root.getChild("right_sleeve_slim");
            leftSleeveSlim =
                    root.getChild("left_sleeve_slim");
            allParts = List.of(
                    rightArm,
                    leftArm,
                    rightArmSlim,
                    leftArmSlim,
                    rightSleeve,
                    leftSleeve,
                    rightSleeveSlim,
                    leftSleeveSlim);
        }

        private void select(boolean right, boolean slim) {
            allParts.forEach(part -> part.visible = false);
            if (right && slim) {
                rightArmSlim.visible = true;
                rightSleeveSlim.visible = true;
            }
            else if (right) {
                rightArm.visible = true;
                rightSleeve.visible = true;
            }
            else if (slim) {
                leftArmSlim.visible = true;
                leftSleeveSlim.visible = true;
            }
            else {
                leftArm.visible = true;
                leftSleeve.visible = true;
            }
        }

        private void render(
                PoseStack poseStack,
                VertexConsumer vertices,
                int packedLight,
                int packedOverlay) {
            root.render(
                    poseStack,
                    vertices,
                    packedLight,
                    packedOverlay);
        }
    }
}
