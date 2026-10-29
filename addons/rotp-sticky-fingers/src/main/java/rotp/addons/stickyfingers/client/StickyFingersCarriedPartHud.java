package rotp.addons.stickyfingers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import rotp.core.client.ui.hud_misc.VanillaHudSprites;
import rotp.core.mixin.entity_like_player.puppetcontrol.client.GuiAccessor;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.stickyfingers.entity.DetachedBodyPartEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;

/**
 * The 1.16 {@code ClientEvents.renderCarriedBodyPartSlot}: while the player carries a detached body part
 * (it rides the player), an off-hand style slot frame is drawn at the off-hand position and the part is rendered
 * inside it (yaw -120, scale 12). 1.16 drew the frame before the hotbar at blit offset -90, so an item really held in
 * the off hand stays on top of it; the frame is therefore its own layer below the hotbar, the model one above it.
 */
public final class StickyFingersCarriedPartHud implements LayeredDraw.Layer {
    private static final float ENTITY_SCALE = 12.0F;
    private static final float ENTITY_YAW = -120.0F;

    private final boolean frame;

    private StickyFingersCarriedPartHud(boolean frame) {
        this.frame = frame;
    }

    public static StickyFingersCarriedPartHud frame() {
        return new StickyFingersCarriedPartHud(true);
    }

    public static StickyFingersCarriedPartHud model() {
        return new StickyFingersCarriedPartHud(false);
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) {
            return;
        }
        DetachedBodyPartEntity carried = null;
        for (Entity passenger : mc.player.getPassengers()) {
            if (passenger instanceof DetachedBodyPartEntity part && part.isCarried()) {
                carried = part;
                break;
            }
        }
        if (carried == null) {
            return;
        }
        VanillaHudSprites.cacheSpritePaths((GuiAccessor) mc.gui);
        HumanoidArm offHand = mc.player.getMainArm().getOpposite();
        int screenHeight = graphics.guiHeight();
        int halfWidth = graphics.guiWidth() / 2;
        int frameY = screenHeight - 23;
        if (!frame) {
            int slotX = offHand == HumanoidArm.LEFT ? halfWidth - 91 - 14 : halfWidth + 91 + 15;
            int slotY = screenHeight - 9;
            // 1.16 drew the slot's model at partial tick 1.0
            renderEntity(graphics, slotX - 3, slotY - 1, carried, 1.0F);
            return;
        }
        // the vanilla slot frame is semi-transparent inside; the vanilla hotbar draws it with blending on
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (offHand == HumanoidArm.LEFT) {
            graphics.blitSprite(
                    VanillaHudSprites.HOTBAR_OFFHAND_LEFT_SPRITE, halfWidth - 91 - 29, frameY, 29, 24);
        }
        else {
            graphics.blitSprite(
                    VanillaHudSprites.HOTBAR_OFFHAND_RIGHT_SPRITE, halfWidth + 91, frameY, 29, 24);
        }
        RenderSystem.disableBlend();
    }

    private static void renderEntity(
            GuiGraphics graphics, int x, int y, Entity entity, float partialTick) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 50.0F);
        pose.scale(ENTITY_SCALE, ENTITY_SCALE, -ENTITY_SCALE);
        // 1.16 passed a 30 degree pitch that had no effect: its part renderer forced the part's own pitch, to -90
        // degrees for arms and legs (lying flat) and 0 for the head. DetachedBodyPartRenderer does the same, here and in
        // the world alike.
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.overrideCameraOrientation(new Quaternionf());
        dispatcher.setRenderShadow(false);
        dispatcher.render(
                entity, 0.0D, 0.0D, 0.0D, ENTITY_YAW, partialTick, pose,
                graphics.bufferSource(), LightTexture.FULL_BRIGHT);
        graphics.flush();
        dispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
        pose.popPose();
    }
}
