package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.spectator.SpectatorGui;

/**
 * Ghost mode does not come with the spectator menu.
 *
 * <p>Ghost mode is {@code GameType.SPECTATOR} under the hood ({@code AchtungBabyService.beginGhost}), so vanilla hands
 * the player the whole spectator kit - including the menu that teleports you into other players and mobs. 1.16's
 * add-on took that away in SpectatorGuiMixin, and the port dropped it.
 *
 * <p><b>Every way in is closed, not just the one 1.16 knew about.</b> An earlier version of this mixin cancelled only
 * {@code onHotbarSelected} and {@code renderHotbar}, on the reasoning that {@code onHotbarSelected} is the only thing
 * that creates the menu. In-game testing disproved that: 1.21.1's {@code SpectatorGui} builds a menu from
 * {@code onMouseMiddleClick} as well - {@code MouseHandler} routes the middle button straight there for any spectator -
 * and a middle click in ghost mode opened a working, navigable menu in 8 of 14 attempts while the hotbar keys opened
 * none. The hidden hotbar made it invisible, not absent, and it was still the path to the teleport packet.
 *
 * <p>So each of vanilla's own entry points is closed at its caller:
 * <ul>
 * <li>{@code onHotbarSelected} - {@code Minecraft.handleKeybinds}, hotbar keys 1-9.</li>
 * <li>{@code onMouseMiddleClick} - {@code MouseHandler}, middle button.</li>
 * <li>{@code isMenuActive} - {@code MouseHandler} gates scroll navigation on it, so returning false closes the wheel
 *     route without needing to guess at {@code onMouseScrolled}.</li>
 * <li>{@code renderHotbar} and {@code renderTooltip} - {@code Gui}'s hotbar and spectator-tooltip layers.</li>
 * </ul>
 */
@Mixin(SpectatorGui.class)
public abstract class SpectatorGuiMixin {
    @Inject(method = "onHotbarSelected(I)V", at = @At("HEAD"), cancellable = true)
    private void rotp_ab$blockGhostHotbarSelection(int slot, CallbackInfo ci) {
        if (rotp_ab$isLocalPlayerGhost()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseMiddleClick()V", at = @At("HEAD"), cancellable = true)
    private void rotp_ab$blockGhostMiddleClick(CallbackInfo ci) {
        if (rotp_ab$isLocalPlayerGhost()) {
            ci.cancel();
        }
    }

    @Inject(method = "isMenuActive()Z", at = @At("HEAD"), cancellable = true)
    private void rotp_ab$hideGhostMenuFromVanilla(CallbackInfoReturnable<Boolean> cir) {
        if (rotp_ab$isLocalPlayerGhost()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "renderHotbar(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void rotp_ab$hideGhostHotbar(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (rotp_ab$isLocalPlayerGhost()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void rotp_ab$hideGhostTooltip(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (rotp_ab$isLocalPlayerGhost()) {
            ci.cancel();
        }
    }

    private static boolean rotp_ab$isLocalPlayerGhost() {
        return AchtungBabyService.isGhost(Minecraft.getInstance().player);
    }
}
