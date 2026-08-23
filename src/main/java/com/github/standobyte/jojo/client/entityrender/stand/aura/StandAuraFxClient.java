package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityPostRenderExtensions;
import com.github.standobyte.jojo.api.client.render.LivingEntityRenderLayerExtensions;
import com.github.standobyte.jojo.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;

/** Core-owned registration for the integrated StandAuraFx effect. */
public final class StandAuraFxClient {
    private static boolean registered;

    private StandAuraFxClient() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        StandAuraMaskCompositor.register();
        EntityPostRenderExtensions.register(
                id("entity_aura_shell"),
                0,
                new EntityAuraShellRenderer());
        LivingEntityRenderLayerExtensions.register(
                id("living_aura"),
                100,
                new LivingAuraLayer());
        NeoForge.EVENT_BUS.addListener(
                StandAuraRenderRequests::onLogout);
    }

    public static void queue(Entity entity, Integer color) {
        StandAuraRenderRequests.queue(entity, color);
    }

    private static ResourceLocation id(String path) {
        return JojoMod.resLoc("stand_aura_fx/" + path);
    }
}
