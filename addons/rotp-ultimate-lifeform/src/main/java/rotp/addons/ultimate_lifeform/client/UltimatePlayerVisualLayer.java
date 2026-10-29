package rotp.addons.ultimate_lifeform.client;

import java.util.Collections;
import java.util.List;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.util.functions.ClientUtil;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.pillarman.client.PillarmanBladesModel;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;

public final class UltimatePlayerVisualLayer
        extends RenderLayer<
                AbstractClientPlayer,
                net.minecraft.client.model.PlayerModel<
                        AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    public static final ModelLayerLocation HEAD_FEATHERS =
            new ModelLayerLocation(
                    UltimateLifeformAddon.id("head_feathers"),
                    "main");
    private static final ResourceLocation ARMOR =
            UltimateLifeformAddon.id(
                    "textures/entity/layers/"
                            + "armor_kars_protection.png");
    private static final ResourceLocation FIRE_ARMOR =
            UltimateLifeformAddon.id(
                    "textures/entity/layers/"
                            + "armor_kars_fire_protection.png");
    private static final ResourceLocation GUARDIAN_EYE =
            UltimateLifeformAddon.id(
                    "textures/entity/layers/"
                            + "guardian_eye_layer.png");
    private static final ResourceLocation STONE_FORM =
            JojoMod.resLoc(
                    "textures/entity/biped/pillarman_layer.png");
    private static final ResourceLocation BLADES =
            JojoMod.resLoc(
                    "textures/entity/layer/pillarman_blades.png");
    private static final ResourceLocation WINGS_BIG =
            UltimateLifeformAddon.id(
                    "textures/entity/kars_wings_big.png");
    private static final ResourceLocation WINGS_SMALL =
            UltimateLifeformAddon.id(
                    "textures/entity/kars_wings_small.png");
    private static final ResourceLocation FEATHERS =
            UltimateLifeformAddon.id(
                    "textures/entity/layers/headfeathers.png");

    private final PillarmanBladesModel<AbstractClientPlayer>
            bladesModel;
    private final ElytraModel<AbstractClientPlayer> wingsModel;
    private final HeadFeathersModel feathersModel;

    public UltimatePlayerVisualLayer(
            PlayerRenderer parent,
            PillarmanBladesModel<AbstractClientPlayer>
                    bladesModel,
            ElytraModel<AbstractClientPlayer> wingsModel,
            ModelPart feathersRoot) {
        super(parent);
        this.bladesModel = bladesModel;
        this.wingsModel = wingsModel;
        this.feathersModel =
                new HeadFeathersModel(feathersRoot);
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
        UltimateLifeformData data = exactOwnerData(player);
        if (data == null || player.isInvisible()) {
            return;
        }

        ResourceLocation armorTexture = switch (
                data.getArmorType()) {
            case NORMAL -> ARMOR;
            case FIRE -> FIRE_ARMOR;
            case NONE -> null;
        };
        if (armorTexture != null) {
            renderParentOverlay(
                    poseStack,
                    buffer,
                    packedLight,
                    player,
                    armorTexture);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player,
                    data.getArmorType()
                                    == UltimateLifeformData
                                            .ArmorType.NORMAL
                            ? ClientReceipt.NORMAL_ARMOR_LAYER
                            : ClientReceipt.FIRE_ARMOR_LAYER);
        }
        if (data.isStoneForm()) {
            renderParentOverlay(
                    poseStack,
                    buffer,
                    packedLight,
                    player,
                    STONE_FORM);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player, ClientReceipt.STONE_LAYER);
        }
        if (data.getHeldAbility()
                == UltimateLifeformSpec.GUARDIAN_LASER) {
            renderParentOverlay(
                    poseStack,
                    buffer,
                    ClientUtil.MAX_LIGHT,
                    player,
                    GUARDIAN_EYE);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player, ClientReceipt.GUARDIAN_EYE_LAYER);
        }
        if (data.isBladesVisible()) {
            renderBlades(
                    poseStack, buffer, player);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player, ClientReceipt.BLADES_MODEL);
        }
        if (data.isFlying() || player.isFallFlying()) {
            renderWings(
                    poseStack,
                    buffer,
                    packedLight,
                    player,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player, ClientReceipt.WINGS_MODEL);
            renderFeathers(
                    poseStack,
                    buffer,
                    packedLight,
                    player,
                    limbSwing,
                    limbSwingAmount,
                    partialTick,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            UltimateLifeformClientAcceptance.recordPlayerVisual(
                    player, ClientReceipt.FEATHERS_MODEL);
        }
    }

    // 1.16 KarsArmorLayer.renderHandFirstPerson: the armor of the stored
    // armor type on the first-person arm
    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        UltimateLifeformData data = exactOwnerData(player);
        if (data == null) {
            return;
        }
        ResourceLocation armorTexture = switch (data.getArmorType()) {
            case NORMAL -> ARMOR;
            case FIRE -> FIRE_ARMOR;
            case NONE -> null;
        };
        FirstPersonModelLayer.defaultRender(
                side,
                poseStack,
                buffer,
                light,
                player,
                entityRenderer,
                getParentModel(),
                armorTexture,
                partialTick);
        // 1.16 UltimateStoneFormLayer.renderHandFirstPerson: the stone skin
        // on the first-person arm in stone form
        if (data.isStoneForm()) {
            FirstPersonModelLayer.defaultRender(
                    side,
                    poseStack,
                    buffer,
                    light,
                    player,
                    entityRenderer,
                    getParentModel(),
                    STONE_FORM,
                    partialTick);
        }
    }

    private void renderParentOverlay(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            ResourceLocation texture) {
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityTranslucent(texture));
        getParentModel().renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F),
                0xFFFFFFFF);
    }

    private void renderBlades(
            PoseStack poseStack,
            MultiBufferSource buffer,
            AbstractClientPlayer player) {
        poseStack.pushPose();
        if (getParentModel().young) {
            poseStack.translate(0.0D, 0.75D, 0.0D);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
        getParentModel().copyPropertiesTo(bladesModel);
        bladesModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(BLADES)),
                ClientUtil.MAX_LIGHT,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderWings(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        ResourceLocation texture =
                player.getItemBySlot(
                                net.minecraft.world.entity
                                        .EquipmentSlot.CHEST)
                                .is(Items.ELYTRA)
                        ? WINGS_SMALL
                        : WINGS_BIG;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.0D, 0.125D);
        getParentModel().copyPropertiesTo(wingsModel);
        wingsModel.setupAnim(
                player,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        wingsModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(texture)),
                packedLight,
                OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private void renderFeathers(
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
        feathersModel.prepareMobModel(
                player,
                limbSwing,
                limbSwingAmount,
                partialTick);
        getParentModel().copyPropertiesTo(feathersModel);
        feathersModel.setupAnim(
                player,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        feathersModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(
                        RenderType.entityCutoutNoCull(FEATHERS)),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        player, 0.0F));
    }

    private static UltimateLifeformData exactOwnerData(
            AbstractClientPlayer player) {
        PlayerPower power = PlayerPower.get(player);
        if (power == null
                || power.getPowerType()
                        != UltimateLifeformAddon
                                .ULTIMATE_LIFEFORM.get()) {
            return null;
        }
        return PlayerPower.getPowerData(
                        player,
                        UltimateLifeformAddon
                                .ULTIMATE_LIFEFORM)
                .orElse(null);
    }

    public static LayerDefinition createHeadFeathersLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(
                CubeDeformation.NONE, 0.0F);
        PartDefinition head = mesh.getRoot().getChild("head");
        PartDefinition feathers = head.addOrReplaceChild(
                "feathers",
                CubeListBuilder.create(),
                PartPose.offset(0.0F, 0.5F, 0.0F));
        addFeather(
                feathers,
                "center",
                0.0F,
                0.0F,
                0,
                0);
        addFeather(
                feathers,
                "right",
                1.0F,
                0.3927F,
                0,
                3);
        addFeather(
                feathers,
                "left",
                -1.0F,
                -0.3927F,
                4,
                0);
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static void addFeather(
            PartDefinition feathers,
            String name,
            float x,
            float zRotation,
            int firstU,
            int firstV) {
        PartDefinition base = feathers.addOrReplaceChild(
                name,
                CubeListBuilder.create(),
                PartPose.offsetAndRotation(
                        x,
                        -7.5F,
                        -4.6F,
                        0.0F,
                        0.0F,
                        zRotation));
        PartDefinition lower = base.addOrReplaceChild(
                "lower",
                CubeListBuilder.create()
                        .texOffs(firstU, firstV)
                        .addBox(
                                -0.5F,
                                0.0F,
                                0.0F,
                                1.0F,
                                3.0F,
                                0.0F),
                PartPose.offsetAndRotation(
                        0.0F,
                        -2.5F,
                        0.0F,
                        0.2182F,
                        0.0F,
                        0.0F));
        lower.addOrReplaceChild(
                "upper",
                CubeListBuilder.create()
                        .texOffs(firstU + 2, firstV)
                        .addBox(
                                -0.5F,
                                -3.0F,
                                0.0F,
                                1.0F,
                                3.0F,
                                0.0F),
                PartPose.rotation(
                        -0.3927F, 0.0F, 0.0F));
    }

    private static final class HeadFeathersModel
            extends HumanoidModel<AbstractClientPlayer> {
        private HeadFeathersModel(ModelPart root) {
            super(root);
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return List.of(head);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return Collections.emptyList();
        }
    }
}
