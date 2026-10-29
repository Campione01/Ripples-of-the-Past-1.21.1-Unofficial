package rotp.addons.scarymonsters.client;

import javax.annotation.Nullable;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import rotp.addons.scarymonsters.ScaryMonstersAddon;
import rotp.addons.scarymonsters.ScaryMonstersService;
import rotp.addons.scarymonsters.ability.ScaryMonstersAbility;
import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 1.16 TargetHighlightHandler: while the local player holds Dinosaurify (revert), the dinosaur the release would
 * revert carries the target tag above its head. 1.16 read the power's held action; the Stand charges it here.
 */
@EventBusSubscriber(modid = ScaryMonstersAddon.MOD_ID, value = Dist.CLIENT)
public final class RevertTargetMarker {
    private static final ResourceLocation TAG_TEXTURE =
            ScaryMonstersAddon.id("textures/entity/target_tag.png");
    private static final float SCALE = 0.04F;
    @Nullable
    private static ScaryDinosaurEntity currentTarget;

    private RevertTargetMarker() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        currentTarget = isChargingRevert(player) ? ScaryMonstersService.lookedAtDinosaur(player) : null;
    }

    private static boolean isChargingRevert(LocalPlayer player) {
        StandPower power = StandPower.get(player);
        if (power == null || !power.hasPower()
                || power.getPowerType() != ScaryMonstersAddon.SCARY_MONSTERS.get()) {
            return false;
        }
        StandEntity stand = power.getSummonedStandEntity();
        EntityActionInstance action = stand != null ? LivingComponentAction.getCurEntityAction(stand) : null;
        return action != null && !action.isOver()
                && action.getPhase() == ActionPhase.BUTTON_CHARGE
                && action.ability instanceof ScaryMonstersAbility ability
                && ability.mode() == ScaryMonstersAbility.Mode.DINOSAURIFY_REVERT;
    }

    // 1.16 drew it in RenderWorldLastEvent, after the rest of the level
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || currentTarget == null) {
            return;
        }
        if (!currentTarget.isAlive()) {
            currentTarget = null;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 pos = currentTarget.position().add(0.0D, currentTarget.getBbHeight() + 1.0D, 0.0D);
        PoseStack stack = event.getPoseStack();
        stack.pushPose();
        stack.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        stack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        stack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        stack.scale(SCALE, SCALE, SCALE);
        Matrix4f matrix = stack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, -16.0F, 32.0F, 0.0F).setUv(0.0F, 1.0F);
        buffer.addVertex(matrix, 16.0F, 32.0F, 0.0F).setUv(1.0F, 1.0F);
        buffer.addVertex(matrix, 16.0F, 0.0F, 0.0F).setUv(1.0F, 0.0F);
        buffer.addVertex(matrix, -16.0F, 0.0F, 0.0F).setUv(0.0F, 0.0F);
        stack.popPose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, TAG_TEXTURE);
        try {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        }
        finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        currentTarget = null;
    }
}
