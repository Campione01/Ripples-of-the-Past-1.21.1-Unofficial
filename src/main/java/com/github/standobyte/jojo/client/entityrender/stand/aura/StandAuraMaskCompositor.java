package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityMaskCompositeContext;
import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

final class StandAuraMaskCompositor {
    private static EntityMaskPostEffect effect;

    private StandAuraMaskCompositor() {}

    static void register() {
        if (effect == null) {
            effect = EntityMaskPostEffect.register(
                    JojoMod.resLoc("stand_aura_fx/sdf"),
                    0,
                    StandAuraMaskCompositor::composite);
        }
    }

    static boolean queue(Entity entity, int color, float alpha) {
        StandAuraSettings settings = settings();
        ShaderInstance shader = StandAuraShaders.composite();
        EntityMaskPostEffect current = effect;
        if (!settings.enabled
                || shader == null
                || current == null
                || alpha <= 0.0F) {
            return false;
        }
        current.setOutputScale(settings.framebufferScale);
        return current.queue(
                entity,
                new AuraGroup(
                        color & 0xFFFFFF,
                        Float.floatToIntBits(
                                Mth.clamp(alpha, 0.0F, 1.0F))),
                shapePadding(settings));
    }

    private static void composite(
            EntityMaskCompositeContext context) {
        ShaderInstance shader = StandAuraShaders.composite();
        if (shader == null
                || !(context.groupKey() instanceof AuraGroup group)) {
            return;
        }
        context.draw(
                shader,
                (activeShader, activeContext) ->
                        StandAuraShaders.applyCompositeUniforms(
                                activeShader,
                                activeContext,
                                group.color(),
                                group.alpha()));
    }

    private static float shapePadding(
            StandAuraSettings settings) {
        float width = (settings.baseAuraWidth
                + settings.auraWidthChaos * 1.9F
                + settings.edgeWarpStrength * 2.0F
                + 0.07F) * settings.auraThickness;
        return Mth.clamp(
                Math.max(width * 2.6F, 0.18F),
                0.08F,
                0.75F);
    }

    private static StandAuraSettings settings() {
        return ClientModSettings.getSettingsReadOnly().standAura;
    }

    private record AuraGroup(int color, int alphaBits) {
        private float alpha() {
            return Float.intBitsToFloat(alphaBits);
        }
    }
}
