package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.api.client.render.EntityMaskPostEffect;
import com.github.standobyte.jojo.api.client.render.EntityPostRenderContext;
import com.github.standobyte.jojo.api.client.render.EntityPostRenderExtension;
import com.github.standobyte.jojo.config.client.ClientModSettings;
import com.github.standobyte.jojo.config.client.StandAuraSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class EntityAuraShellRenderer
        implements EntityPostRenderExtension {
    @Override
    public void afterEntityRender(EntityPostRenderContext context) {
        StandAuraSettings settings =
                ClientModSettings.getSettingsReadOnly().standAura;
        Entity entity = context.entity();
        if (!settings.enabled
                || EntityMaskPostEffect.isCapturePass()
                || entity instanceof LivingEntity
                || isInvisibleToLocalPlayer(entity)) {
            return;
        }
        StandAuraRenderRequests.Decision decision =
                StandAuraRenderRequests.decisionFor(entity);
        if (!decision.render()) {
            return;
        }
        int color = decision.color() != null
                ? decision.color() & 0xFFFFFF
                : StandAuraSettings.FALLBACK_COLOR;
        StandAuraMaskCompositor.queue(entity, color, 1.0F);
    }

    @Override
    public void endFrame(long frameId) {
        StandAuraRenderRequests.endFrame(frameId);
    }

    private static boolean isInvisibleToLocalPlayer(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                ? entity.isInvisibleTo(minecraft.player)
                : entity.isInvisible();
    }
}
