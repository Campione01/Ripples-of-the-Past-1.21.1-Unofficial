package rotp.addons.thehand.effect;

import rotp.core.mechanics.StunEffect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class SurpriseEffect extends StunEffect {
    public SurpriseEffect() {
        super(0xDD0F0F);
    }

    @Override
    public boolean isApplicable(LivingEntity entity) {
        return !(entity instanceof Player) && super.isApplicable(entity);
    }
}
