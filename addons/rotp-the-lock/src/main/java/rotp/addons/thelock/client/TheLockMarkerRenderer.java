package rotp.addons.thelock.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ui.hud_power.PowerHud;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.thelock.TheLockAddon;
import rotp.addons.thelock.state.LockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class TheLockMarkerRenderer extends MarkerRenderer {
    public TheLockMarkerRenderer(Minecraft minecraft) {
        super(
                TheLockAddon.id(
                        "textures/power/the_lock.png"),
                minecraft);
        renderThroughBlocks = true;
    }

    @Override
    protected boolean shouldRender() {
        return mc.player != null
                && mc.level != null
                && ClientGlobals.canSeeStands
                && TheLockAddon.isTheLockUser(mc.player)
                && PowerHud.abilityHUDInstance != null
                && PowerHud.abilityHUDInstance.isAbilitySelected(
                        TheLockAddon.REMOVE_LOCK_NAME);
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        double rangeSquared =
                LockRules.OWNER_RANGE * LockRules.OWNER_RANGE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living
                    && living.distanceToSqr(mc.player)
                            <= rangeSquared
                    && living.hasData(
                            TheLockAddon.LOCK_STATE)
                    && TheLockAddon.lockState(living)
                            .active()) {
                list.add(new MarkerInstance(
                        entityMarkerPos(living, partialTick)));
            }
        }
    }
}
