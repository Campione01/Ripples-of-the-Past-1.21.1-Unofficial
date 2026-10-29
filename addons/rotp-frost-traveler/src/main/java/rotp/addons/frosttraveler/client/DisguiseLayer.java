package rotp.addons.frosttraveler.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.frosttraveler.item.DisguiseData;
import rotp.addons.frosttraveler.state.CreamStarterService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class DisguiseLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> {
    private static final Map<ResourceLocation, EntityVisual>
            ENTITY_VISUALS = new HashMap<>();
    private static final java.util.Set<ResourceLocation>
            REJECTED_TYPES = new java.util.HashSet<>();
    private static ClientLevel cachedLevel;

    private final PlayerModel<AbstractClientPlayer> normalModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public DisguiseLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            ModelPart normalRoot,
            ModelPart slimRoot) {
        super(parent);
        normalModel = new PlayerModel<>(normalRoot, false);
        slimModel = new PlayerModel<>(slimRoot, true);
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
        DisguiseData data =
                CreamStarterService.equippedDisguise(player);
        FrostTravelerMaskRenderDiagnostics.recordLayerVisit(player);
        if (player.isInvisible() || data == null) {
            return;
        }
        if (data.targetUuid().isPresent()) {
            renderPlayerDisguise(
                    data,
                    player,
                    poseStack,
                    buffer,
                    packedLight,
                    limbSwing,
                    limbSwingAmount,
                    partialTick,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            return;
        }
        EntityVisual visual =
                entityVisual(data.targetType().orElse(null));
        if (visual != null) {
            renderEntityDisguise(
                    visual,
                    player,
                    data.targetType().orElse(null),
                    poseStack,
                    buffer,
                    packedLight,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
        }
    }

    public static boolean canRender(
            AbstractClientPlayer player, float partialTick) {
        if (player.isInvisible()) {
            return false;
        }
        DisguiseData data =
                CreamStarterService.equippedDisguise(player);
        return data != null
                && (data.targetUuid().isPresent()
                        || entityVisual(
                                data.targetType()
                                        .orElse(null)) != null);
    }

    private void renderPlayerDisguise(
            DisguiseData data,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        UUID uuid = data.targetUuid().orElseThrow();
        GameProfile profile = data.capturedProfile()
                .map(captured -> captured.gameProfile())
                .orElseGet(() -> new GameProfile(uuid, data.targetName()));
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var playerInfo = connection != null
                ? connection.getPlayerInfo(uuid)
                : null;
        boolean hasCapturedTextures =
                !profile.getProperties().get("textures").isEmpty();
        PlayerSkin skin = hasCapturedTextures || playerInfo == null
                ? minecraft.getSkinManager().getInsecureSkin(profile)
                : playerInfo.getSkin();
        PlayerModel<AbstractClientPlayer> model =
                skin.model() == PlayerSkin.Model.SLIM
                        ? slimModel
                        : normalModel;
        getParentModel().copyPropertiesTo(model);
        setAllVisible(model);
        model.prepareMobModel(wearer, limbSwing, limbSwingAmount, partialTick);
        model.setupAnim(
                wearer,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityTranslucent(skin.texture()));
        FrostTravelerMaskRenderDiagnostics.CountingVertexConsumer
                countingVertices = FrostTravelerMaskRenderDiagnostics
                        .counting(vertices);
        model.renderToBuffer(
                poseStack,
                countingVertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        wearer, 0.0F));
        FrostTravelerMaskRenderDiagnostics.recordPlayerSubmission(
                wearer,
                uuid,
                model,
                skin.texture(),
                countingVertices.geometrySnapshot());
    }

    private static void setAllVisible(
            PlayerModel<AbstractClientPlayer> model) {
        model.head.visible = true;
        model.hat.visible = true;
        model.body.visible = true;
        model.jacket.visible = true;
        model.leftArm.visible = true;
        model.leftSleeve.visible = true;
        model.rightArm.visible = true;
        model.rightSleeve.visible = true;
        model.leftLeg.visible = true;
        model.leftPants.visible = true;
        model.rightLeg.visible = true;
        model.rightPants.visible = true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void renderEntityDisguise(
            EntityVisual visual,
            AbstractClientPlayer wearer,
            ResourceLocation targetType,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        LivingEntity dummy = visual.dummy();
        dummy.setPose(wearer.getPose());
        dummy.setYRot(wearer.getYRot());
        dummy.setXRot(wearer.getXRot());
        dummy.yBodyRot = wearer.yBodyRot;
        dummy.yBodyRotO = wearer.yBodyRotO;
        dummy.yHeadRot = wearer.yHeadRot;
        dummy.yHeadRotO = wearer.yHeadRotO;
        dummy.tickCount = wearer.tickCount;
        EntityModel model = visual.model();
        boolean wasYoung = model.young;
        model.young = false;
        try {
            model.setupAnim(
                    dummy,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            HumanoidModel<?> humanoid = (HumanoidModel<?>) model;
            boolean rightMain = wearer.getMainArm() == HumanoidArm.RIGHT;
            ArmTransform rightPose = (rightMain
                    ? wearer.getMainHandItem() : wearer.getOffhandItem()).isEmpty()
                            ? null : ArmTransform.capture(humanoid.rightArm);
            ArmTransform leftPose = (rightMain
                    ? wearer.getOffhandItem() : wearer.getMainHandItem()).isEmpty()
                            ? null : ArmTransform.capture(humanoid.leftArm);
            boolean slim = wearer.getSkin().model() == PlayerSkin.Model.SLIM;
            try {
                // Held items use the real player's evaluated arm, including the slim offset.
                if (rightPose != null) {
                    humanoid.rightArm.copyFrom(getParentModel().rightArm);
                    if (slim) {
                        humanoid.rightArm.x += 0.5F;
                    }
                }
                if (leftPose != null) {
                    humanoid.leftArm.copyFrom(getParentModel().leftArm);
                    if (slim) {
                        humanoid.leftArm.x -= 0.5F;
                    }
                }
                ResourceLocation texture =
                        ((EntityRenderer) visual.renderer())
                                .getTextureLocation(dummy);
                VertexConsumer vertices = buffer.getBuffer(
                        RenderType.entityTranslucent(texture));
                FrostTravelerMaskRenderDiagnostics.CountingVertexConsumer
                        countingVertices = FrostTravelerMaskRenderDiagnostics
                                .counting(vertices);
                model.renderToBuffer(
                        poseStack,
                        countingVertices,
                        packedLight,
                        LivingEntityRenderer.getOverlayCoords(
                                wearer, 0.0F));
                FrostTravelerMaskRenderDiagnostics.recordEntitySubmission(
                        wearer,
                        targetType,
                        model,
                        visual.renderer(),
                        texture,
                        countingVertices.geometrySnapshot());
            } finally {
                // The entity renderer owns this shared model; do not leak player transforms.
                if (rightPose != null) {
                    rightPose.restore(humanoid.rightArm);
                }
                if (leftPose != null) {
                    leftPose.restore(humanoid.leftArm);
                }
            }
        } finally {
            model.young = wasYoung;
        }
    }

    private static EntityVisual entityVisual(
            ResourceLocation typeId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null
                || typeId == null
                || REJECTED_TYPES.contains(typeId)) {
            return null;
        }
        if (cachedLevel != level) {
            clearCache();
            cachedLevel = level;
        }
        EntityVisual cached = ENTITY_VISUALS.get(typeId);
        if (cached != null) {
            return cached;
        }
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(typeId)
                    .orElse(null);
            Entity created = type != null ? type.create(level) : null;
            if (!(created instanceof LivingEntity living)) {
                if (created != null) {
                    created.remove(Entity.RemovalReason.DISCARDED);
                }
                REJECTED_TYPES.add(typeId);
                return null;
            }
            EntityRenderer<?> renderer = minecraft
                    .getEntityRenderDispatcher()
                    .getRenderer(living);
            if (!(renderer
                            instanceof LivingEntityRenderer<?, ?>
                                    livingRenderer)
                    || !(livingRenderer.getModel()
                            instanceof HumanoidModel<?>)) {
                living.remove(Entity.RemovalReason.DISCARDED);
                REJECTED_TYPES.add(typeId);
                return null;
            }
            EntityVisual visual = new EntityVisual(
                    living,
                    livingRenderer,
                    livingRenderer.getModel());
            ENTITY_VISUALS.put(typeId, visual);
            return visual;
        } catch (RuntimeException | LinkageError exception) {
            REJECTED_TYPES.add(typeId);
            return null;
        }
    }

    static void clearCache() {
        for (EntityVisual visual : ENTITY_VISUALS.values()) {
            visual.dummy().remove(Entity.RemovalReason.DISCARDED);
        }
        ENTITY_VISUALS.clear();
        REJECTED_TYPES.clear();
    }

    private record ArmTransform(
            PartPose pose, float xScale, float yScale, float zScale) {
        private static ArmTransform capture(ModelPart arm) {
            return new ArmTransform(
                    arm.storePose(), arm.xScale, arm.yScale, arm.zScale);
        }

        private void restore(ModelPart arm) {
            arm.loadPose(pose);
            arm.xScale = xScale;
            arm.yScale = yScale;
            arm.zScale = zScale;
        }
    }

    private record EntityVisual(
            LivingEntity dummy,
            LivingEntityRenderer<?, ?> renderer,
            EntityModel<?> model) {}
}
