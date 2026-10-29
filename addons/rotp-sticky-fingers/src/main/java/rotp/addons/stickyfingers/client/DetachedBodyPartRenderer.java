package rotp.addons.stickyfingers.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Matrix3f;

import rotp.core.client.entityrender.stand.StandVisualContext;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import rotp.addons.stickyfingers.mixin.client.AgeableListModelAccessor;
import rotp.addons.stickyfingers.mixin.client.LivingEntityRendererScaleInvoker;
import rotp.addons.stickyfingers.state.DetachedPartType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
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
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

public final class DetachedBodyPartRenderer
        extends EntityRenderer<DetachedBodyPartEntity> {
    private final DetachedPartModel model;
    private final Map<UUID, Float> shadowScales = new HashMap<>(1);

    public DetachedBodyPartRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new DetachedPartModel(createLayer().bakeRoot());
        shadowRadius = 0.3F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            DetachedBodyPartEntity entity) {
        LivingEntity target = entity.resolveTarget();
        if (target instanceof AbstractClientPlayer player) {
            return player.getSkin().texture();
        }
        if (target != null) {
            return rendererTexture(target);
        }
        UUID targetId = entity.getTargetUuid();
        return DefaultPlayerSkin.get(
                        targetId != null
                                ? targetId
                                : entity.getUUID())
                .texture();
    }

    @Override
    protected float getShadowRadius(DetachedBodyPartEntity entity) {
        return shadowRadius * shadowScales.getOrDefault(entity.getUUID(), 0.0F);
    }

    @Override
    public void render(
            DetachedBodyPartEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        beginShadow(shadowScales, entity.getUUID());
        StandVisualContext visualContext =
                visualContext(entity, partialTick);
        if (visualContext == null
                || visualContext.effectiveAlpha() <= 0.0F) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        visualContext.effectiveAlpha());
        LivingEntity target = entity.resolveTarget();
        boolean player = target instanceof AbstractClientPlayer;
        boolean slim = target instanceof AbstractClientPlayer client
                && client.getSkin().model() == PlayerSkin.Model.SLIM;
        boolean skeleton = target instanceof AbstractSkeleton;
        model.setVisiblePart(
                entity.getPartType(), player, slim, skeleton);

        LivingEntityRenderer<?, ?> ownerRenderer = target != null
                && entityRenderDispatcher.getRenderer(target)
                        instanceof LivingEntityRenderer<?, ?> renderer
                ? renderer : null;
        EntityModel<?> ownerModel = ownerRenderer != null
                ? ownerRenderer.getModel() : null;
        List<ModelPart> ownerParts = ownerParts(entity, ownerModel);
        PoseStack.Pose renderPose = poseStack.last();
        poseStack.pushPose();
        try {
            poseStack.scale(1.0F, -1.0F, -1.0F);
            if (ownerParts.isEmpty()) {
                poseStack.mulPose(Axis.YP.rotationDegrees(
                        180.0F - yRotation));
            }
            float spin =
                    (entity.tickCount + partialTick) * 8.0F;
            if (!entity.isCarried()
                    && (!entity.onGround()
                            || entity.isReturning())) {
                poseStack.mulPose(
                        Axis.XP.rotationDegrees(spin));
                poseStack.mulPose(
                        Axis.ZP.rotationDegrees(spin * 0.5F));
            }
            VertexConsumer vertices = alphaBuffer.getBuffer(
                    ModRenderTypes.standTranslucent(
                            getTextureLocation(entity)));
            if (ownerParts.isEmpty()) {
                model.render(
                        poseStack,
                        vertices,
                        packedLight,
                        OverlayTexture.NO_OVERLAY);
                recordShadowScale(shadowScales, entity.getUUID(), 1.0F);
            } else {
                renderOwnerParts(
                        ownerParts,
                        entity,
                        target,
                        ownerRenderer,
                        ownerModel,
                        yRotation,
                        partialTick,
                        poseStack,
                        vertices,
                        packedLight);
            }
        } catch (RuntimeException | Error failure) {
            beginShadow(shadowScales, entity.getUUID());
            throw failure;
        } finally {
            while (poseStack.last() != renderPose) {
                poseStack.popPose();
            }
        }
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                alphaBuffer,
                packedLight);
    }

    @Nullable
    private static StandVisualContext visualContext(
            DetachedBodyPartEntity entity,
            float partialTick) {
        UUID detacherUuid = entity.getDetacherUuid();
        Player detacher = detacherUuid != null
                ? entity.level().getPlayerByUUID(detacherUuid)
                : null;
        StandPower power = detacher != null
                ? StandPower.get(detacher)
                : null;
        StandEntity controller = power != null
                && power.getPowerType()
                        == StickyFingersAddon.STICKY_FINGERS.get()
                && power.getSummonedStandEntity()
                        instanceof StandEntity stand
                ? stand
                : null;
        if (controller == null || !controller.isAlive()) {
            return null;
        }
        float lifecycleAlpha = controller.clientStuff != null
                ? controller.clientStuff.getAlpha(
                        controller, partialTick)
                : 1.0F;
        return StandVisualContext.world(
                controller, lifecycleAlpha);
    }

    /**
     * 1.16 HumanoidUtil.renderPart: the detached part is drawn with the owner's own model part and texture, so each
     * supported humanoid keeps its geometry and texture layout (the port's fixed player-shaped boxes drew a zombie's left
     * limbs from an empty corner of its texture, and a skeleton's from a 64x32 texture as if it were 64x64). Arms and
     * legs get -90 degrees of pitch and lie flat at (-2, -2, 2); the head stays upright at the origin; both take the
     * part entity's yaw and the owner renderer's scale, and flash red with the owner when it is hurt. The fixed model
     * remains for an owner this client cannot resolve.
     */
    private void renderOwnerParts(
            List<ModelPart> parts,
            DetachedBodyPartEntity entity,
            LivingEntity target,
            LivingEntityRenderer<?, ?> renderer,
            EntityModel<?> ownerModel,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            VertexConsumer vertices,
            int packedLight) {
        Matrix3f parentTransform = new Matrix3f(poseStack.last().pose());
        withOwnerAge(ownerModel, target.isBaby(), () -> {
            float scale = target.getScale();
            poseStack.scale(scale, scale, scale);
            ((LivingEntityRendererScaleInvoker) renderer)
                    .stickyFingers$invokeScale(target, poseStack, partialTick);
            boolean head = entity.getPartType() == DetachedPartType.HEAD;
            int overlay = LivingEntityRenderer.getOverlayCoords(target, 0.0F);
            for (int i = 0; i < parts.size(); i++) {
                ModelPart part = parts.get(i);
                // The owner's render hides detached parts; its skin-layer switches still apply here.
                boolean showPart = i == 0
                        || overlayShown(target, entity.getPartType(), part.visible);
                float modelScale = youngModelPartScale(ownerModel, part);
                renderOwnerPart(
                        part, head, showPart, yRotation,
                        modelScale,
                        poseStack, vertices, packedLight, overlay);
                if (showPart) {
                    recordShadowScale(shadowScales, entity.getUUID(), shadowScale(
                            parentTransform, new Matrix3f(poseStack.last().pose()), modelScale, part));
                }
            }
        });
    }

    static float shadowScale(
            Matrix3f parent, Matrix3f rendered, float modelScale, ModelPart part) {
        float determinant = parent.determinant();
        if (!Float.isFinite(determinant) || determinant == 0.0F
                || !Float.isFinite(modelScale) || modelScale <= 0.0F) {
            return 0.0F;
        }
        Matrix3f local = new Matrix3f(parent).invert().mul(rendered)
                .scale(modelScale * part.xScale, modelScale * part.yScale, modelScale * part.zScale);
        // Vanilla draws a circle: use its largest transformed axis for non-uniform scale, not a mesh projection.
        float xSquared = local.m00() * local.m00() + local.m01() * local.m01() + local.m02() * local.m02();
        float ySquared = local.m10() * local.m10() + local.m11() * local.m11() + local.m12() * local.m12();
        float zSquared = local.m20() * local.m20() + local.m21() * local.m21() + local.m22() * local.m22();
        float scale = Mth.sqrt(Math.max(xSquared, Math.max(ySquared, zSquared)));
        return Float.isFinite(scale) && scale > 0.0F ? scale : 0.0F;
    }

    static void beginShadow(Map<UUID, Float> scales, UUID entityId) {
        scales.clear();
        scales.put(entityId, 0.0F);
    }

    static void recordShadowScale(Map<UUID, Float> scales, UUID entityId, float renderedScale) {
        if (Float.isFinite(renderedScale) && renderedScale > 0.0F && scales.containsKey(entityId)) {
            scales.put(entityId, Math.max(scales.get(entityId), renderedScale));
        }
    }

    static void withOwnerAge(EntityModel<?> ownerModel, boolean young, Runnable render) {
        boolean savedYoung = ownerModel.young;
        ownerModel.young = young;
        try {
            render.run();
        } finally {
            ownerModel.young = savedYoung;
        }
    }

    static float youngModelPartScale(EntityModel<?> ownerModel, ModelPart part) {
        if (!ownerModel.young || !(ownerModel instanceof AgeableListModel<?>)) {
            return 1.0F;
        }
        AgeableListModelAccessor model = (AgeableListModelAccessor) ownerModel;
        // Hats and other outer layers can belong to the body branch even for a detached head.
        for (ModelPart head : model.stickyFingers$headParts()) {
            if (part == head) {
                return model.stickyFingers$scaleHead()
                        ? 1.5F / model.stickyFingers$babyHeadScale() : 1.0F;
            }
        }
        for (ModelPart body : model.stickyFingers$bodyParts()) {
            if (part == body) {
                return 1.0F / model.stickyFingers$babyBodyScale();
            }
        }
        return 1.0F;
    }

    static void renderOwnerPart(
            ModelPart part, boolean head, boolean showPart,
            float yRotation, float modelScale,
            PoseStack poseStack, VertexConsumer vertices,
            int packedLight, int overlay) {
        PartPose saved = part.storePose();
        float savedXScale = part.xScale;
        float savedYScale = part.yScale;
        float savedZScale = part.zScale;
        boolean visible = part.visible;
        PoseStack.Pose parentPose = poseStack.last();
        poseStack.pushPose();
        try {
            poseStack.scale(modelScale, modelScale, modelScale);
            part.visible = showPart;
            part.setPos(head ? 0.0F : -2.0F, head ? 0.0F : -2.0F, head ? 0.0F : 2.0F);
            part.setRotation(head ? 0.0F : -Mth.HALF_PI, yRotation * Mth.DEG_TO_RAD, 0.0F);
            part.render(poseStack, vertices, packedLight, overlay);
        } finally {
            part.loadPose(saved);
            part.xScale = savedXScale;
            part.yScale = savedYScale;
            part.zScale = savedZScale;
            part.visible = visible;
            // ModelPart.render does not unwind its own stack frame when vertex emission throws.
            while (poseStack.last() != parentPose) {
                poseStack.popPose();
            }
        }
    }

    // the owner's model parts for this detached part, base part first, then its skin overlay (the port also draws
    // sleeves, trousers and the hat); empty when this client cannot resolve the owner's humanoid model
    private static List<ModelPart> ownerParts(
            DetachedBodyPartEntity entity, @Nullable EntityModel<?> model) {
        if (model == null) {
            return List.of();
        }
        DetachedPartType part = entity.getPartType();
        if (model instanceof PlayerModel<?> player) {
            return switch (part) {
                case HEAD -> List.of(player.head, player.hat);
                case LEFT_ARM -> List.of(player.leftArm, player.leftSleeve);
                case RIGHT_ARM -> List.of(player.rightArm, player.rightSleeve);
                case LEFT_LEG -> List.of(player.leftLeg, player.leftPants);
                case RIGHT_LEG -> List.of(player.rightLeg, player.rightPants);
            };
        }
        if (model instanceof HumanoidModel<?> humanoid) {
            return switch (part) {
                case HEAD -> List.of(humanoid.head, humanoid.hat);
                case LEFT_ARM -> List.of(humanoid.leftArm);
                case RIGHT_ARM -> List.of(humanoid.rightArm);
                case LEFT_LEG -> List.of(humanoid.leftLeg);
                case RIGHT_LEG -> List.of(humanoid.rightLeg);
            };
        }
        if (model instanceof HierarchicalModel<?> hierarchical) {
            String name = switch (part) {
                case HEAD -> "head";
                case LEFT_ARM -> "left_arm";
                case RIGHT_ARM -> "right_arm";
                case LEFT_LEG -> "left_leg";
                case RIGHT_LEG -> "right_leg";
            };
            ModelPart root = hierarchical.root();
            return root.hasChild(name) ? List.of(root.getChild(name)) : List.of();
        }
        return List.of();
    }

    private static boolean overlayShown(
            LivingEntity target, DetachedPartType part, boolean visible) {
        if (!(target instanceof Player player)) {
            return visible;
        }
        return player.isModelPartShown(switch (part) {
            case HEAD -> PlayerModelPart.HAT;
            case LEFT_ARM -> PlayerModelPart.LEFT_SLEEVE;
            case RIGHT_ARM -> PlayerModelPart.RIGHT_SLEEVE;
            case LEFT_LEG -> PlayerModelPart.LEFT_PANTS_LEG;
            case RIGHT_LEG -> PlayerModelPart.RIGHT_PANTS_LEG;
        });
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

        root.addOrReplaceChild(
                "mob_head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F,
                                -4.0F,
                                -4.0F,
                                8.0F,
                                8.0F,
                                8.0F),
                PartPose.ZERO);
        root.addOrReplaceChild(
                "player_head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F,
                                -4.0F,
                                -4.0F,
                                8.0F,
                                8.0F,
                                8.0F)
                        .texOffs(32, 0)
                        .addBox(
                                -4.0F,
                                -4.0F,
                                -4.0F,
                                8.0F,
                                8.0F,
                                8.0F,
                                new CubeDeformation(0.25F)),
                PartPose.ZERO);

        addArm(root, "right_arm", 40, 16, 4.0F, false);
        addArm(root, "left_arm", 32, 48, 4.0F, true);
        addArm(root, "right_arm_slim", 40, 16, 3.0F, false);
        addArm(root, "left_arm_slim", 32, 48, 3.0F, true);
        addArm(root, "right_arm_skeleton", 40, 16, 2.0F, false);
        addArm(root, "left_arm_skeleton", 32, 48, 2.0F, true);
        addOuterArm(
                root, "right_sleeve", 40, 32, 4.0F, false);
        addOuterArm(
                root, "left_sleeve", 48, 48, 4.0F, true);
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

        addLeg(root, "right_leg", 0, 16, 4.0F, false);
        addLeg(root, "left_leg", 16, 48, 4.0F, true);
        addLeg(
                root, "right_leg_skeleton", 0, 16, 2.0F, false);
        addLeg(
                root, "left_leg_skeleton", 16, 48, 2.0F, true);
        addOuterLeg(
                root, "right_pants", 0, 32, 4.0F, false);
        addOuterLeg(
                root, "left_pants", 0, 48, 4.0F, true);
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
                box(textureU, textureV, width, mirror, 0.0F),
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
                box(textureU, textureV, width, mirror, 0.25F),
                PartPose.ZERO);
    }

    private static void addLeg(
            PartDefinition root,
            String name,
            int textureU,
            int textureV,
            float width,
            boolean mirror) {
        root.addOrReplaceChild(
                name,
                box(textureU, textureV, width, mirror, 0.0F),
                PartPose.ZERO);
    }

    private static void addOuterLeg(
            PartDefinition root,
            String name,
            int textureU,
            int textureV,
            float width,
            boolean mirror) {
        root.addOrReplaceChild(
                name,
                box(textureU, textureV, width, mirror, 0.25F),
                PartPose.ZERO);
    }

    private static CubeListBuilder box(
            int textureU,
            int textureV,
            float width,
            boolean mirror,
            float deformation) {
        CubeListBuilder builder = CubeListBuilder.create()
                .texOffs(textureU, textureV);
        if (mirror) {
            builder = builder.mirror();
        }
        return builder.addBox(
                -width * 0.5F,
                -6.0F,
                -2.0F,
                width,
                12.0F,
                4.0F,
                new CubeDeformation(deformation));
    }

    private static final class DetachedPartModel {
        private final ModelPart root;
        private final ModelPart mobHead;
        private final ModelPart playerHead;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightArmSlim;
        private final ModelPart leftArmSlim;
        private final ModelPart rightArmSkeleton;
        private final ModelPart leftArmSkeleton;
        private final ModelPart rightSleeve;
        private final ModelPart leftSleeve;
        private final ModelPart rightSleeveSlim;
        private final ModelPart leftSleeveSlim;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;
        private final ModelPart rightLegSkeleton;
        private final ModelPart leftLegSkeleton;
        private final ModelPart rightPants;
        private final ModelPart leftPants;
        private final List<ModelPart> allParts;

        private DetachedPartModel(ModelPart root) {
            this.root = root;
            mobHead = root.getChild("mob_head");
            playerHead = root.getChild("player_head");
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
            rightArmSlim = root.getChild("right_arm_slim");
            leftArmSlim = root.getChild("left_arm_slim");
            rightArmSkeleton =
                    root.getChild("right_arm_skeleton");
            leftArmSkeleton =
                    root.getChild("left_arm_skeleton");
            rightSleeve = root.getChild("right_sleeve");
            leftSleeve = root.getChild("left_sleeve");
            rightSleeveSlim =
                    root.getChild("right_sleeve_slim");
            leftSleeveSlim =
                    root.getChild("left_sleeve_slim");
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
            rightLegSkeleton =
                    root.getChild("right_leg_skeleton");
            leftLegSkeleton =
                    root.getChild("left_leg_skeleton");
            rightPants = root.getChild("right_pants");
            leftPants = root.getChild("left_pants");
            allParts = List.of(
                    mobHead,
                    playerHead,
                    rightArm,
                    leftArm,
                    rightArmSlim,
                    leftArmSlim,
                    rightArmSkeleton,
                    leftArmSkeleton,
                    rightSleeve,
                    leftSleeve,
                    rightSleeveSlim,
                    leftSleeveSlim,
                    rightLeg,
                    leftLeg,
                    rightLegSkeleton,
                    leftLegSkeleton,
                    rightPants,
                    leftPants);
        }

        private void setVisiblePart(
                DetachedPartType part,
                boolean player,
                boolean slim,
                boolean skeleton) {
            allParts.forEach(modelPart ->
                    modelPart.visible = false);
            switch (part) {
                case HEAD -> (player ? playerHead : mobHead)
                        .visible = true;
                case LEFT_ARM -> showArm(
                        true, player, slim, skeleton);
                case RIGHT_ARM -> showArm(
                        false, player, slim, skeleton);
                case LEFT_LEG -> showLeg(
                        true, player, skeleton);
                case RIGHT_LEG -> showLeg(
                        false, player, skeleton);
            }
        }

        private void showArm(
                boolean left,
                boolean player,
                boolean slim,
                boolean skeleton) {
            if (skeleton) {
                (left ? leftArmSkeleton : rightArmSkeleton)
                        .visible = true;
                return;
            }
            if (player && slim) {
                (left ? leftArmSlim : rightArmSlim).visible = true;
                (left ? leftSleeveSlim : rightSleeveSlim)
                        .visible = true;
                return;
            }
            (left ? leftArm : rightArm).visible = true;
            if (player) {
                (left ? leftSleeve : rightSleeve).visible = true;
            }
        }

        private void showLeg(
                boolean left,
                boolean player,
                boolean skeleton) {
            if (skeleton) {
                (left ? leftLegSkeleton : rightLegSkeleton)
                        .visible = true;
                return;
            }
            (left ? leftLeg : rightLeg).visible = true;
            if (player) {
                (left ? leftPants : rightPants).visible = true;
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
                    packedOverlay,
                    0xFFFFFFFF);
        }
    }
}
