package rotp.core.api.client.render;

import rotp.core.client.entityrender.stand.aura.StandAuraFxClient;

import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Public exact-target request surface retained from StandAuraFx. */
@OnlyIn(Dist.CLIENT)
public final class StandAuraFx {
    private StandAuraFx() {}

    public static void renderAura(Entity entity) {
        StandAuraFxClient.queue(entity, null);
    }

    public static void renderAura(Entity entity, int auraColor) {
        StandAuraFxClient.queue(entity, auraColor & 0xFFFFFF);
    }
}
