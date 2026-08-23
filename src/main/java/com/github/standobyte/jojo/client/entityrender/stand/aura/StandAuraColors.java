package com.github.standobyte.jojo.client.entityrender.stand.aura;

import com.github.standobyte.jojo.client.standskin.StandSkin;
import com.github.standobyte.jojo.client.standskin.StandSkinsLoader;
import com.github.standobyte.jojo.config.client.StandAuraSettings;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.world.entity.LivingEntity;

final class StandAuraColors {
    private StandAuraColors() {}

    static int forLiving(
            LivingEntity entity, Integer requestedColor) {
        if (requestedColor != null) {
            return requestedColor & 0xFFFFFF;
        }
        StandPower power = StandPower.get(entity);
        StandSkinsLoader loader = StandSkinsLoader.getInstance();
        StandSkin skin = power != null && loader != null
                ? loader.getSkin(power)
                : null;
        return skin != null
                ? skin.getColor() & 0xFFFFFF
                : StandAuraSettings.FALLBACK_COLOR;
    }
}
