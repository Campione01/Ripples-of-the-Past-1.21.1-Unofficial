package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

final class StandAuraShellStyle {
    private StandAuraShellStyle() {}

    static int passCount() {
        float scale = settings().framebufferScale;
        return scale < 0.45F ? 2 : scale < 0.8F ? 3 : 4;
    }

    static float width(Entity entity, float partialTick) {
        StandAuraSettings settings = settings();
        float phase = (entity.tickCount + partialTick)
                * (0.035F + settings.noiseScale * 0.0015F);
        float disorder = Mth.sin(
                phase * 1.618F + entity.getId() * 0.37F)
                * settings.chaos;
        return Math.max(
                0.001F,
                (settings.baseAuraWidth
                        + settings.auraWidthChaos * disorder
                        + settings.edgeWarpStrength
                                * Mth.sin(phase * 2.31F))
                        * settings.auraThickness);
    }

    static float scale(int pass, int passes, float width) {
        float progress = (pass + 1.0F) / passes;
        return 1.0F + width * (0.45F + progress);
    }

    private static StandAuraSettings settings() {
        return ClientModSettings.getSettingsReadOnly().standAura;
    }
}
