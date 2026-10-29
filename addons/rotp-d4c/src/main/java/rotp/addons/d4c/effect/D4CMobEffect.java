package rotp.addons.d4c.effect;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class D4CMobEffect extends MobEffect {
    // 1.16 af.ab: faint d4c_light chime every 12 ticks during Love Train
    public static final int LOVE_TRAIN_CHIME_TICKS = 12;
    public static final float LOVE_TRAIN_CHIME_VOLUME = 0.15F;

    public D4CMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(
            int duration, int amplifier) {
        return isLoveTrain();
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        Level level = entity.level();
        // client only, like 1.16; playLocalSound is a no-op on the server
        if (isLoveTrain()
                && level.isClientSide()
                && entity instanceof Player
                && entity.tickCount % LOVE_TRAIN_CHIME_TICKS == 0) {
            float pitch = 0.9F + level.getRandom().nextFloat() * 1.1F;
            level.playLocalSound(
                    entity.getX(), entity.getY(), entity.getZ(),
                    D4CAddon.D4C_LIGHT.get(),
                    SoundSource.PLAYERS,
                    LOVE_TRAIN_CHIME_VOLUME,
                    pitch,
                    false);
        }
        return true;
    }

    private boolean isLoveTrain() {
        return D4CAddon.LOVE_TRAIN.isBound()
                && D4CAddon.LOVE_TRAIN.value() == this;
    }
}
