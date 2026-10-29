package rotp.addons.yellowtemperance.client;

import java.util.HashMap;
import java.util.Map;

import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class ErosionVisualState {
    private static final Map<Integer, Integer> CONTROLLERS =
            new HashMap<>();

    private ErosionVisualState() {}

    static void apply(
            int targetId, int controllerId, boolean active) {
        if (active && controllerId > 0) {
            CONTROLLERS.put(targetId, controllerId);
        }
        else {
            CONTROLLERS.remove(targetId);
        }
    }

    static YellowTemperanceControllerEntity controller(
            LivingEntity target) {
        Integer controllerId = CONTROLLERS.get(target.getId());
        if (controllerId == null
                || Minecraft.getInstance().level == null) {
            return null;
        }
        Entity entity = Minecraft.getInstance()
                .level.getEntity(controllerId);
        if (entity instanceof YellowTemperanceControllerEntity
                controller) {
            return controller;
        }
        CONTROLLERS.remove(target.getId());
        return null;
    }

    static void clear() {
        CONTROLLERS.clear();
    }
}
