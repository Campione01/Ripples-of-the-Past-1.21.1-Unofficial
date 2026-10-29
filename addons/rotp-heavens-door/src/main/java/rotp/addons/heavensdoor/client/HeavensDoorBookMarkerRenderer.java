package rotp.addons.heavensdoor.client;

import java.util.List;

import rotp.core.client.ClientGlobals;
import rotp.core.client.ui.marker.MarkerRenderer;

import rotp.addons.heavensdoor.HeavensDoorAddon;
import rotp.addons.heavensdoor.state.BookState;
import rotp.addons.heavensdoor.state.BookStateRules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class HeavensDoorBookMarkerRenderer
        extends MarkerRenderer {
    public HeavensDoorBookMarkerRenderer(Minecraft minecraft) {
        super(
                HeavensDoorAddon.id(
                        "textures/power/heavens_door.png"),
                minecraft);
    }

    @Override
    protected boolean shouldRender() {
        return mc.player != null
                && mc.level != null
                && ClientGlobals.canSeeStands
                && HeavensDoorAddon.controller(mc.player) != null;
    }

    @Override
    protected void updatePositions(
            List<MarkerInstance> list, float partialTick) {
        double rangeSquared =
                BookStateRules.OWNER_RANGE
                        * BookStateRules.OWNER_RANGE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                    || living.distanceToSqr(mc.player)
                            > rangeSquared
                    || !living.hasData(
                            HeavensDoorAddon.BOOK_STATE)) {
                continue;
            }
            BookState state = HeavensDoorAddon.bookState(living);
            if (state.active()
                    && state.isOwnedBy(mc.player.getUUID())
                    && state.matches(
                            living.getUUID(),
                            mc.level.dimension().location())) {
                list.add(new MarkerInstance(
                        entityMarkerPos(living, partialTick)));
            }
        }
    }
}
