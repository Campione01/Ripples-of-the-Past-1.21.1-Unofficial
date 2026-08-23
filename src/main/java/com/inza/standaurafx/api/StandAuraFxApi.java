package com.inza.standaurafx.api;

import com.github.standobyte.jojo.api.client.render.StandAuraFx;

import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Binary-compatible bridge for addons compiled against StandAuraFx.
 */
@Deprecated(forRemoval = false)
@OnlyIn(Dist.CLIENT)
public final class StandAuraFxApi {
    private StandAuraFxApi() {}

    public static void renderAura(Entity entity) {
        StandAuraFx.renderAura(entity);
    }

    public static void renderAura(Entity entity, int auraColor) {
        StandAuraFx.renderAura(entity, auraColor);
    }
}
