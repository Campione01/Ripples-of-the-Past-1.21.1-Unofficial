package rotp.addons.khnum.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.khnum.disguise.DisguiseMode;
import rotp.addons.khnum.entity.KhnumControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

public final class KhnumDisguiseLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>>
        implements FirstPersonModelLayer {
    private static final Map<ResourceLocation, EntityVisual>
            ENTITY_VISUALS = new HashMap<>();
    private static final Set<ResourceLocation> REJECTED_TYPES =
            new HashSet<>();
    private static ClientLevel cachedLevel;
    private static PlayerModel<AbstractClientPlayer>
            firstPersonNormalModel;
    private static PlayerModel<AbstractClientPlayer>
            firstPersonSlimModel;

    private final PlayerModel<AbstractClientPlayer> normalModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public KhnumDisguiseLayer(
            RenderLayerParent<
                    AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent,
            ModelPart normalRoot,
            ModelPart slimRoot) {
        super(parent);
        normalModel = new PlayerModel<>(normalRoot, false);
        slimModel = new PlayerModel<>(slimRoot, true);
        firstPersonNormalModel = normalModel;
        firstPersonSlimModel = slimModel;
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
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || !state.controller().isDisguised()) {
            return;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.context().effectiveAlpha());
        if (state.controller().getDisguiseMode()
                == DisguiseMode.PLAYER) {
            renderPlayer(
                    state.controller(),
                    player,
                    poseStack,
                    alphaBuffer,
                    packedLight,
                    limbSwing,
                    limbSwingAmount,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
            return;
        }
        EntityVisual visual = entityVisual(
                state.controller().getDisguiseId().orElse(null));
        if (visual != null) {
            renderEntity(
                    visual,
                    player,
                    poseStack,
                    alphaBuffer,
                    packedLight,
                    limbSwing,
                    limbSwingAmount,
                    partialTick,
                    ageInTicks,
                    netHeadYaw,
                    headPitch);
        }
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (entity instanceof AbstractClientPlayer player) {
            renderFirstPersonReplacement(
                    player,
                    side,
                    poseStack,
                    buffer,
                    light,
                    partialTick);
        }
    }

    public static boolean canRender(
            AbstractClientPlayer player, float partialTick) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || !state.controller().isDisguised()) {
            return false;
        }
        return state.controller().getDisguiseMode()
                        == DisguiseMode.PLAYER
                ? state.controller().getDisguisePlayer().isPresent()
                : entityVisual(
                        state.controller().getDisguiseId()
                                .orElse(null)) != null;
    }

    public static boolean renderFirstPersonReplacement(
            AbstractClientPlayer player,
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            float partialTick) {
        KhnumVisuals.RenderState state =
                KhnumVisuals.resolve(player, partialTick);
        if (state == null
                || !state.controller().isDisguised()) {
            return false;
        }
        MultiBufferSource alphaBuffer =
                AlphaMultiBufferSource.wrap(
                        buffer,
                        state.context().effectiveAlpha());
        if (state.controller().getDisguiseMode()
                == DisguiseMode.PLAYER) {
            PlayerSkin skin = playerSkin(state.controller());
            PlayerModel<AbstractClientPlayer> model =
                    skin != null
                                    && skin.model()
                                            == PlayerSkin.Model.SLIM
                            ? firstPersonSlimModel
                            : firstPersonNormalModel;
            if (skin == null || model == null) {
                return false;
            }
            renderFirstPersonArm(
                    model,
                    player,
                    side,
                    poseStack,
                    alphaBuffer,
                    light,
                    skin.texture(),
                    partialTick);
            return true;
        }

        EntityVisual visual = entityVisual(
                state.controller().getDisguiseId().orElse(null));
        if (visual == null) {
            return false;
        }
        syncDummy(visual.dummy(), player);
        if (visual.model() instanceof HumanoidModel<?> humanoid) {
            renderFirstPersonArm(
                    humanoid,
                    visual.dummy(),
                    side,
                    poseStack,
                    alphaBuffer,
                    light,
                    visual.texture(),
                    partialTick);
        }
        return true;
    }

    public static void clearCache() {
        for (EntityVisual visual : ENTITY_VISUALS.values()) {
            visual.dummy().remove(Entity.RemovalReason.DISCARDED);
        }
        ENTITY_VISUALS.clear();
        REJECTED_TYPES.clear();
        cachedLevel = null;
    }

    private void renderPlayer(
            KhnumControllerEntity controller,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        PlayerSkin skin = playerSkin(controller);
        if (skin == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model =
                skin.model() == PlayerSkin.Model.SLIM
                        ? slimModel
                        : normalModel;
        getParentModel().copyPropertiesTo(model);
        model.setAllVisible(true);
        model.setupAnim(
                wearer,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(
                        skin.texture()));
        model.renderToBuffer(
                poseStack,
                vertices,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(
                        wearer, 0.0F));
    }

    private static PlayerSkin playerSkin(
            KhnumControllerEntity controller) {
        UUID uuid = controller.getDisguisePlayer().orElse(null);
        if (uuid == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var info = connection != null
                ? connection.getPlayerInfo(uuid)
                : null;
        return info != null
                ? info.getSkin()
                : minecraft.getSkinManager().getInsecureSkin(
                        new GameProfile(
                                uuid,
                                controller.getDisguisePlayerName()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderEntity(
            EntityVisual visual,
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
        LivingEntity dummy = visual.dummy();
        syncDummy(dummy, wearer);
        renderNative(
                visual.renderer(),
                dummy,
                wearer,
                poseStack,
                buffer,
                packedLight,
                partialTick);
    }

    private static void syncDummy(
            LivingEntity dummy,
            AbstractClientPlayer wearer) {
        boolean newTick = dummy.tickCount != wearer.tickCount;
        dummy.setPose(wearer.getPose());
        dummy.setYRot(wearer.getYRot());
        dummy.setXRot(wearer.getXRot());
        dummy.yBodyRot = wearer.yBodyRot;
        dummy.yBodyRotO = wearer.yBodyRotO;
        dummy.yHeadRot = wearer.yHeadRot;
        dummy.yHeadRotO = wearer.yHeadRotO;
        dummy.swinging = wearer.swinging;
        dummy.swingingArm = wearer.swingingArm;
        dummy.swingTime = wearer.swingTime;
        dummy.hurtTime = wearer.hurtTime;
        dummy.oAttackAnim = wearer.oAttackAnim;
        dummy.attackAnim = wearer.attackAnim;
        if (newTick) {
            dummy.walkAnimation.update(
                    wearer.walkAnimation.speed(), 1.0F);
        }
        dummy.tickCount = wearer.tickCount;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            try {
                dummy.setItemSlot(
                        slot, wearer.getItemBySlot(slot).copy());
            }
            catch (RuntimeException ignored) {
                // Some modded living entities reject unsupported slots.
            }
        }
        if (wearer.isUsingItem()) {
            if (!dummy.isUsingItem()
                    || dummy.getUsedItemHand()
                            != wearer.getUsedItemHand()) {
                dummy.startUsingItem(wearer.getUsedItemHand());
            }
        }
        else if (dummy.isUsingItem()) {
            dummy.stopUsingItem();
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
                    .getOptional(typeId).orElse(null);
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
                            livingRenderer)) {
                living.remove(Entity.RemovalReason.DISCARDED);
                REJECTED_TYPES.add(typeId);
                return null;
            }
            ResourceLocation texture = texture(
                    renderer, living);
            EntityVisual visual = new EntityVisual(
                    living,
                    renderer,
                    livingRenderer.getModel(),
                    texture);
            ENTITY_VISUALS.put(typeId, visual);
            return visual;
        }
        catch (RuntimeException | LinkageError ignored) {
            REJECTED_TYPES.add(typeId);
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResourceLocation texture(
            EntityRenderer renderer, LivingEntity entity) {
        return renderer.getTextureLocation(entity);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderNative(
            EntityRenderer renderer,
            LivingEntity dummy,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        poseStack.pushPose();
        try {
            renderer.render(
                    dummy,
                    wearer.getYRot(),
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
        }
        finally {
            poseStack.popPose();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderFirstPersonArm(
            HumanoidModel<?> model,
            LivingEntity entity,
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            ResourceLocation texture,
            float partialTick) {
        FirstPersonModelLayer.setupForFirstPersonRender(
                (HumanoidModel) model, entity, partialTick);
        VertexConsumer vertices = buffer.getBuffer(
                ModRenderTypes.standTranslucentCull(texture));
        FirstPersonModelLayer.renderArmAndOuter(
                model,
                side,
                poseStack,
                vertices,
                light,
                OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF,
                FirstPersonModelLayer.isRipplesAnimPlaying(model));
    }

    private record EntityVisual(
            LivingEntity dummy,
            EntityRenderer<?> renderer,
            EntityModel<?> model,
            ResourceLocation texture) {}
}
