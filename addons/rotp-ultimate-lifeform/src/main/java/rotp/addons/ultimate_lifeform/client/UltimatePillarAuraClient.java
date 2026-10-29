package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.init.ModParticles;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * The 1.16 onHoldTickClientEffect of the core Pillar Man actions the Ultimate Lifeform's mode moves extend, on every
 * client that sees the user: the light sparks of the Light Flash charge, the auras of the decoy, sandstorm, rift,
 * prison and detonation charges and the rift's blood while it fires.
 */
public final class UltimatePillarAuraClient {
    private UltimatePillarAuraClient() {}

    public static void tick(EntityActionInstance action, LivingEntity user, UltimateLifeformSpec spec) {
        boolean charging = action.getPhase() == ActionPhase.BUTTON_CHARGE;
        switch (spec) {
            // PillarmanLightFlash :28-36 after 10 held ticks
            case LIGHT_FLASH -> {
                if (charging && action.getPhaseTick() > 10) {
                    RandomSource random = user.getRandom();
                    for (int i = 0; i <= 24; i++) {
                        user.level().addParticle(ModParticles.LIGHT_SPARK.get(), true,
                                user.getX(), user.getY() + 0.8D, user.getZ(),
                                (random.nextDouble() - 0.5D) / 4.0D,
                                (random.nextDouble() - 0.5D) / 4.0D,
                                (random.nextDouble() - 0.5D) / 4.0D);
                    }
                }
            }
            // PillarmanLightFlashDecoy :106-111
            case LIGHT_FLASH_DECOY -> {
                if (charging) {
                    auraEffect(user, ModParticles.HAMON_AURA_RAINBOW.get(), 10);
                }
            }
            // PillarmanDivineSandstorm :48-52, before tick 40
            case DIVINE_SANDSTORM -> {
                if (charging) {
                    auraEffect(user, ModParticles.HAMON_AURA_GREEN.get(), 6);
                }
            }
            // PillarmanAtmosphericRift :128-143: the aura before tick 40, blood while it fires
            case ATMOSPHERIC_RIFT -> {
                if (charging) {
                    auraEffect(user, ModParticles.HAMON_AURA_GREEN.get(), 3);
                }
                else if (action.getPhase() == ActionPhase.PERFORM) {
                    RandomSource random = user.getRandom();
                    for (int i = 0; i < 3; i++) {
                        user.level().addParticle(ModParticles.BLOOD.get(),
                                user.getX() + (random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D),
                                user.getY() + random.nextDouble() * user.getBbHeight(),
                                user.getZ() + (random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D),
                                (random.nextDouble() - 0.5D) / 2.0D,
                                (random.nextDouble() - 0.5D) / 2.0D,
                                (random.nextDouble() - 0.5D) / 2.0D);
                    }
                }
            }
            // PillarmanGiantCarthwheelPrison :212-216
            case GIANT_CARTHWHEEL_PRISON -> {
                if (charging) {
                    auraEffect(user, ModParticles.HAMON_AURA_RED.get(), 6);
                }
            }
            // PillarmanSelfDetonation :286-291
            case SELF_DETONATION -> {
                if (charging) {
                    auraEffect(user, ModParticles.HAMON_AURA_RED.get(), 12);
                    auraEffect(user, ModParticles.BOILING_BLOOD_POP.get(), 1);
                }
            }
            default -> {
            }
        }
    }

    // 1.16 PillarmanDivineSandstorm.auraEffect (the core port's PillarmanActionAbility.auraEffect is protected)
    private static void auraEffect(LivingEntity user, ParticleOptions particles, int intensity) {
        RandomSource random = user.getRandom();
        for (int i = 0; i < intensity; i++) {
            CustomParticlesHelper.createHamonAuraParticle(particles, user,
                    user.getX() + (random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D),
                    user.getY() + random.nextDouble() * (user.getBbHeight() * 0.5D),
                    user.getZ() + (random.nextDouble() - 0.5D) * (user.getBbWidth() + 0.5D));
        }
        if (user == Minecraft.getInstance().getCameraEntity()) {
            // integer division as 1.16: no arm particles below 5
            CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(particles, user, intensity / 5);
        }
    }
}
