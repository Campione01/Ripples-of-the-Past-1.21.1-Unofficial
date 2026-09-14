package rotp.core.client.entityrender.stand.aura;

import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.config.client.StandAuraSettings;
import rotp.core.powersystem.standpower.StandPower;

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
