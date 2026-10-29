package rotp.addons.d4c.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * The sandstorm drawn over the screen while the player has the Sandstorm effect, as 1.16 drew it
 * ({@code ad.ac.ae}): 100 sand grains tinted #FFCC99 at 30-70% opacity, 8-24 px across, blowing in from the right
 * edge at a speed that grows with their size, drifting and bouncing vertically and spinning, stepped every 30 ms of
 * wall-clock time and re-randomised whenever one leaves on the left; then an 8% sand-coloured tint. The port had
 * redrawn it with half-size, untinted, fully opaque grains on a fixed seed, and kept drawing it with the HUD hidden.
 */
public final class D4CSandstormOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation[] TEXTURES = {
        D4CAddon.id("textures/particle/sandstorm.png"),
        D4CAddon.id("textures/particle/sandstorm2.png"),
        D4CAddon.id("textures/particle/sandstorm3.png"),
        D4CAddon.id("textures/particle/sandstorm4.png")
    };
    private static final int PARTICLE_COUNT = 100;
    private static final long STEP_MILLIS = 30L;
    private static final RandomSource RANDOM = RandomSource.create();
    private static final List<Grain> GRAINS = createGrains();
    private static long lastStep;

    @Override
    public void render(
            GuiGraphics graphics,
            DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || minecraft.player == null
                || !minecraft.player.hasEffect(D4CAddon.SANDSTORM)) {
            return;
        }
        step();

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Grain grain : GRAINS) {
            float halfSide = grain.scale * 8.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(
                    grain.x * width, grain.y * height, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotation(grain.rotation));
            graphics.pose().scale(halfSide, halfSide, 1.0F);
            graphics.setColor(1.0F, 0.8F, 0.6F, grain.alpha);
            // the whole texture over a 2x2 square around the grain's centre, scaled to its size
            graphics.blit(
                    TEXTURES[grain.texture],
                    -1,
                    -1,
                    0.0F,
                    0.0F,
                    2,
                    2,
                    2,
                    2);
            graphics.pose().popPose();
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        graphics.fill(0, 0, width, height, 0x14CC994C);
    }

    private static void step() {
        long now = System.currentTimeMillis();
        if (now - lastStep < STEP_MILLIS) {
            return;
        }
        lastStep = now;
        for (Grain grain : GRAINS) {
            grain.step();
        }
    }

    private static List<Grain> createGrains() {
        List<Grain> grains = new ArrayList<>(PARTICLE_COUNT);
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            Grain grain = new Grain();
            grain.respawn();
            grains.add(grain);
        }
        return List.copyOf(grains);
    }

    private static final class Grain {
        private float x;
        private float y;
        private float velocityX;
        private float velocityY;
        private float scale;
        private float alpha;
        private float rotation;
        private float spin;
        private int texture;

        private void respawn() {
            x = 1.0F + RANDOM.nextFloat() * 0.2F;
            y = RANDOM.nextFloat();
            scale = 0.5F + RANDOM.nextFloat();
            alpha = 0.3F + RANDOM.nextFloat() * 0.4F;
            rotation = RANDOM.nextFloat() * ((float) Math.PI * 2.0F);
            spin = (RANDOM.nextFloat() - 0.5F) * 0.1F;
            texture = RANDOM.nextInt(TEXTURES.length);
            float speed = 0.005F + RANDOM.nextFloat() * 0.01F;
            float lift = (RANDOM.nextFloat() - 0.5F) * 0.002F;
            float sizeFactor = scale - 0.5F;
            velocityX = -(speed + 0.016F * sizeFactor);
            velocityY = lift * (1.0F + sizeFactor * 1.5F);
            if (scale > 1.2F) {
                velocityX *= 1.5F;
                velocityY *= 1.5F;
            }
        }

        private void step() {
            x += velocityX;
            y += velocityY;
            rotation += spin;
            if (x < -0.1F) {
                respawn();
            }
            if (y < 0.0F || y > 1.0F) {
                velocityY = -velocityY;
                y = Math.max(0.0F, Math.min(1.0F, y));
            }
        }
    }
}
