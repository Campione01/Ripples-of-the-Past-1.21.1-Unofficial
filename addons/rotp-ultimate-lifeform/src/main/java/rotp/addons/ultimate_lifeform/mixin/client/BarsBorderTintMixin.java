package rotp.addons.ultimate_lifeform.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformHud;
import rotp.core.client.ui.hud_power.Bars;
import rotp.core.client.ui.utils.BlitFloat;

// 1.16 BarsRendererMixin.changeColor ran before the border of every bar the renderer drew, and changeTexture bound
// ulf_overlay.png for the whole render while the charge was above 0: an Ultimate Lifeform user's Stand bars got the
// yellow border, from the gold sprite while charged. The border here is the empty bar sprite, the first blit of the
// horizontal bar.
@Mixin(value = Bars.class, remap = false)
public abstract class BarsBorderTintMixin {
    @Redirect(
            method = "_renderHorizontalBar",
            at = @At(
                    value = "INVOKE",
                    target = "Lrotp/core/client/ui/utils/BlitFloat;blit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/Minecraft;Lnet/minecraft/resources/ResourceLocation;FFFFFI)V",
                    ordinal = 0))
    private static void rotp_ul$drawBorder(PoseStack pose, Minecraft mc, ResourceLocation empty, float x, float y,
            float length, float width, float z, int color) {
        UltimateLifeformHud.blitBorder(pose, mc, empty, x, y, length, width, z, color);
    }
}
