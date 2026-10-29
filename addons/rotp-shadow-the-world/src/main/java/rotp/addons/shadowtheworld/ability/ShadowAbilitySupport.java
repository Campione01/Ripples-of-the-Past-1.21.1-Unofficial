package rotp.addons.shadowtheworld.ability;

import rotp.addons.shadowtheworld.ShadowTheWorldAddon;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

final class ShadowAbilitySupport {
    private ShadowAbilitySupport() {}

    static boolean isStable(LivingEntity user) {
        return user instanceof Player player
                && ShadowTheWorldAddon.state(player)
                        .usedNetherStar();
    }

    static void unsummonIfUnstable(LivingEntity user) {
        if (user == null
                || user.level().isClientSide()
                || isStable(user)) {
            return;
        }
        var power = rotp.core
                .powersystem.standpower.StandPower.get(user);
        if (power != null
                && power.isSummoned()
                && power.getPowerType()
                        == ShadowTheWorldAddon
                                .SHADOW_THE_WORLD
                                .get()) {
            // 1.16 broadcast (null except): the user hears it too
            user.level().playSound(
                    null,
                    user.getX(),
                    user.getY(),
                    user.getZ(),
                    ShadowTheWorldAddon
                            .SHADOW_WORLD_TIME_STOP_BLINK
                            .get(),
                    user.getSoundSource(),
                    1.0F,
                    0.9F);
            power.getPowerType().forceUnsummon(user, power);
        }
    }

    static void burst(ServerLevel level, LivingEntity user) {
        level.sendParticles(
                ParticleTypes.PORTAL,
                user.getX(),
                user.getY() + user.getBbHeight() * 0.5D,
                user.getZ(),
                50,
                2.0D,
                2.0D,
                2.0D,
                0.2D);
        level.sendParticles(
                ParticleTypes.END_ROD,
                user.getX(),
                user.getY() + user.getBbHeight() * 0.5D,
                user.getZ(),
                50,
                2.0D,
                2.0D,
                2.0D,
                0.1D);
    }

    static Vec3 targetCenter(LivingEntity target) {
        return target.position().add(
                0.0D, target.getBbHeight() * 0.5D, 0.0D);
    }
}
