package rotp.addons.yellowtemperance.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import rotp.core.client.rendertype.AlphaMultiBufferSource;
import rotp.core.client.rendertype.ModRenderTypes;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public final class YellowTemperanceDisguiseLayer extends RenderLayer<
        AbstractClientPlayer,
        PlayerModel<AbstractClientPlayer>> {
    private static final Map<VisualKey, LivingEntity> DUMMIES =
            new HashMap<>();
    private static ClientLevel cachedLevel;

    private final PlayerModel<AbstractClientPlayer> normalModel;
    private final PlayerModel<AbstractClientPlayer> slimModel;

    public YellowTemperanceDisguiseLayer(
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
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        player, partialTick);
        if (state == null
                || state.controller().getDisguiseMode()
                        != DisguiseMode.PLAYER) {
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
    }

    // Full entity renderers need the entity frame, not a player layer's model frame.
    static void renderEntityDisguise(
            AbstractClientPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        if (player.isSpectator()) {
            return;
        }
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(player, partialTick);
        if (state == null
                || !state.controller().isDisguised()
                || state.controller().getDisguiseMode() == DisguiseMode.PLAYER) {
            return;
        }
        LivingEntity dummy = dummy(state.controller());
        if (dummy != null) {
            renderEntity(dummy, player, poseStack,
                    AlphaMultiBufferSource.wrap(buffer, state.context().effectiveAlpha()),
                    packedLight, partialTick);
        }
    }

    public static boolean canRender(
            AbstractClientPlayer player, float partialTick) {
        YellowTemperanceVisuals.RenderState state =
                YellowTemperanceVisuals.resolve(
                        player, partialTick);
        if (state == null
                || !state.controller().isDisguised()) {
            return false;
        }
        return state.controller().getDisguiseMode()
                        == DisguiseMode.PLAYER
                ? state.controller().getDisguisePlayer().isPresent()
                : dummy(state.controller()) != null;
    }

    public static void clearCache() {
        for (LivingEntity entity : DUMMIES.values()) {
            entity.remove(Entity.RemovalReason.DISCARDED);
        }
        DUMMIES.clear();
        cachedLevel = null;
    }

    private void renderPlayer(
            YellowTemperanceControllerEntity controller,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        UUID uuid = controller.getDisguisePlayer().orElse(null);
        if (uuid == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var info = connection != null
                ? connection.getPlayerInfo(uuid)
                : null;
        PlayerSkin skin = info != null
                ? info.getSkin()
                : minecraft.getSkinManager().getInsecureSkin(
                        new GameProfile(
                                uuid,
                                controller.getDisguisePlayerName()));
        PlayerModel<AbstractClientPlayer> model =
                skin.model() == PlayerSkin.Model.SLIM
                        ? slimModel
                        : normalModel;
        poseStack.pushPose();
        try {
            float scale = transitionScale(controller, ageInTicks);
            poseStack.scale(scale, scale, scale);
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
        finally {
            poseStack.popPose();
        }
    }

    private static void renderEntity(
            LivingEntity dummy,
            AbstractClientPlayer wearer,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            float partialTick) {
        dummy.setPose(wearer.getPose());
        dummy.setYRot(wearer.getYRot());
        dummy.setXRot(wearer.getXRot());
        dummy.yBodyRot = wearer.yBodyRot;
        dummy.yBodyRotO = wearer.yBodyRotO;
        dummy.yHeadRot = wearer.yHeadRot;
        dummy.yHeadRotO = wearer.yHeadRotO;
        dummy.tickCount = wearer.tickCount;
        EntityRenderDispatcher dispatcher =
                Minecraft.getInstance()
                        .getEntityRenderDispatcher();
        poseStack.pushPose();
        try {
            float scale = transitionScale(
                    YellowTemperanceAddon.controller(wearer),
                    wearer.tickCount + partialTick);
            poseStack.scale(scale, scale, scale);
            EntityRenderer<? super LivingEntity> renderer =
                    dispatcher.getRenderer(dummy);
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

    private static float transitionScale(
            YellowTemperanceControllerEntity controller,
            float ageInTicks) {
        if (controller == null) {
            return 1.0F;
        }
        double transitionTicks = YellowTemperanceConfig
                .DISGUISE_STAND_TRANSITION_TICKS.get();
        return Mth.clamp(
                (float) ((ageInTicks
                        - controller.getStateChangedTick())
                        / transitionTicks),
                0.0F,
                1.0F);
    }

    private static LivingEntity dummy(
            YellowTemperanceControllerEntity controller) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        ResourceLocation id =
                controller.getDisguiseId().orElse(null);
        DisguiseMode mode = controller.getDisguiseMode();
        if (level == null || id == null
                || mode == DisguiseMode.NONE
                || mode == DisguiseMode.PLAYER) {
            return null;
        }
        if (cachedLevel != level) {
            clearCache();
            cachedLevel = level;
        }
        VisualKey key = new VisualKey(mode, id);
        LivingEntity cached = DUMMIES.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            LivingEntity created = switch (mode) {
                case ENTITY -> createEntity(level, id);
                case STAND -> createStand(level, id);
                default -> null;
            };
            if (created != null) {
                DUMMIES.put(key, created);
            }
            return created;
        }
        catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static LivingEntity createEntity(
            ClientLevel level, ResourceLocation id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(id).orElse(null);
        Entity entity = type != null ? type.create(level) : null;
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private static LivingEntity createStand(
            ClientLevel level, ResourceLocation id) {
        Object standType = JojoRegistries.DEFAULT_STANDS_REG
                .getOptional(id).orElse(null);
        if (!(standType
                instanceof EntityStandType entityStandType)) {
            return null;
        }
        Entity entity = entityStandType.getEntityType().create(level);
        if (!(entity instanceof StandEntity stand)) {
            return null;
        }
        stand.withStandType(entityStandType);
        for (int tick = 0; tick < 80; tick++) {
            stand.tick();
        }
        return stand;
    }

    private record VisualKey(
            DisguiseMode mode, ResourceLocation id) {}
}
