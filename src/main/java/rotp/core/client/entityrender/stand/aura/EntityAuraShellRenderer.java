package rotp.core.client.entityrender.stand.aura;

import rotp.core.api.client.render.EntityMaskPostEffect;
import rotp.core.api.client.render.EntityPostRenderContext;
import rotp.core.api.client.render.EntityPostRenderExtension;
import rotp.core.config.client.ClientModSettings;
import rotp.core.config.client.StandAuraSettings;

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
