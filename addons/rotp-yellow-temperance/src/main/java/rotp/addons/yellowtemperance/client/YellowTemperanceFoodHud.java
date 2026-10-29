package rotp.addons.yellowtemperance.client;

import com.mojang.blaze3d.systems.RenderSystem;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import rotp.addons.yellowtemperance.YellowTemperanceConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The food row redrawn from {@code yt_energy.png} while Yellow Temperance is out. 1.16 only bound this texture and let
 * Forge's own food bar draw from it, so everything except the texture is the host game's food bar: the Hunger variant
 * of every icon (including the dark-green empty one at u133), the one-pixel jitter at zero saturation, hiding the row
 * under a mount's hearts, and reserving the row in {@code Gui.rightHeight} so the air bubbles stack above it. The port
 * had drawn it with the normal empty icon under Hunger, without the jitter, over a mount's hearts, and without
 * reserving the row, so underwater the bubbles were drawn on top of it.
 */
public final class YellowTemperanceFoodHud
        implements LayeredDraw.Layer {
    private static final ResourceLocation TEXTURE =
            YellowTemperanceAddon.id("textures/gui/yt_energy.png");
    private static final int ICON_SIZE = 9;
    private static final int EMPTY_U = 16;
    private static final int HUNGRY_EMPTY_U = 16 + 13 * ICON_SIZE;
    private static final int NORMAL_FULL_U = 52;
    private static final int HUNGRY_FULL_U = 88;
    private static final int ICON_V = 27;
    private static final RandomSource JITTER = RandomSource.create();

    private final LayeredDraw.Layer vanilla;

    public YellowTemperanceFoodHud(LayeredDraw.Layer vanilla) {
        this.vanilla = vanilla;
    }

    @Override
    public void render(
            GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        // wrapLayer replaces the wrapper that carried vanilla's F1 check along with the layer, so the check is
        // repeated here: with the HUD hidden the vanilla layer is called and draws nothing either
        if (!shouldReplace(player) || minecraft.options.hideGui) {
            vanilla.render(graphics, deltaTracker);
            return;
        }
        if (vehicleMaxHearts(player) != 0) {
            return;
        }

        int food = player.getFoodData().getFoodLevel();
        int right = graphics.guiWidth() / 2 + 91;
        int top = graphics.guiHeight() - minecraft.gui.rightHeight;
        boolean hungry = player.hasEffect(MobEffects.HUNGER);
        int emptyU = hungry ? HUNGRY_EMPTY_U : EMPTY_U;
        int fullU = hungry ? HUNGRY_FULL_U : NORMAL_FULL_U;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // vanilla reseeds its HUD random every frame from the GUI tick, so the jitter changes once a tick, not every
        // frame
        JITTER.setSeed(minecraft.gui.getGuiTicks() * 312871L);
        for (int slot = 0; slot < 10; slot++) {
            int y = top;
            if (player.getFoodData().getSaturationLevel() <= 0.0F
                    && minecraft.gui.getGuiTicks() % (food * 3 + 1) == 0) {
                y = top + JITTER.nextInt(3) - 1;
            }
            int x = right - slot * 8 - ICON_SIZE;
            drawFood(graphics, x, y, emptyU);
            if (slot * 2 + 1 < food) {
                drawFood(graphics, x, y, fullU);
            }
            else if (slot * 2 + 1 == food) {
                drawFood(graphics, x, y, fullU + ICON_SIZE);
            }
        }
        RenderSystem.disableBlend();
        minecraft.gui.rightHeight += 10;
    }

    private static boolean shouldReplace(Player player) {
        return YellowTemperanceConfig
                        .ENABLE_FOOD_BAR_OVERLAY.get()
                && player != null
                && !player.isCreative()
                && !player.isSpectator()
                && YellowTemperanceAddon.controller(player) != null;
    }

    // the host game's Gui.getVehicleMaxHearts: a ridden entity that shows its own hearts takes the food row's place
    private static int vehicleMaxHearts(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof LivingEntity living
                && living.showVehicleHealth()) {
            return Math.min(30, (int) (living.getMaxHealth() + 0.5F) / 2);
        }
        return 0;
    }

    private static void drawFood(
            GuiGraphics graphics, int x, int y, int u) {
        graphics.blit(
                TEXTURE,
                x,
                y,
                u,
                ICON_V,
                ICON_SIZE,
                ICON_SIZE,
                256,
                256);
    }
}
