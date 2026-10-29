package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.init.ModParticles;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;

/**
 * The client parts of the 1.16 Hamon charge aura (UltimateLifeformData.tick): the core blue aura in third person and
 * the aura on the first-person arms of the camera user (renderParticleFirstPerson / renderULFParticleFirstPerson, both
 * n / 5 particles a tick per arm).
 */
public final class UltimateHamonAuraClient {
    private UltimateHamonAuraClient() {}

    public static void thirdPersonCoreBlue(LivingEntity user, double x, double y, double z) {
        CustomParticlesHelper.createHamonAuraParticle(ModParticles.HAMON_AURA_BLUE.get(), user, x, y, z);
    }

    // 1.16 CustomParticles.createUltimateHamonAuraParticle: the add-on's green and rainbow aura, following the user
    public static void thirdPerson(ParticleOptions aura, LivingEntity user, double x, double y, double z) {
        UltimateHamonAuraParticle.spawn(aura, user, x, y, z);
    }

    public static void firstPerson(LivingEntity user, float ratio, boolean wet) {
        if (ratio <= 0.0F || user != Minecraft.getInstance().getCameraEntity()) {
            return;
        }
        if (wet) {
            CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(
                    UltimateLifeformAddon.HAMON_AURA_GREEN.get(), user, Math.round(6.0F * ratio) / 5.0F);
            CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(
                    ModParticles.HAMON_AURA_BLUE.get(), user, Math.round(6.0F * ratio) / 5.0F);
        }
        else {
            for (int i = 0; i < 2; i++) {
                CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(
                        UltimateLifeformAddon.HAMON_AURA_RAINBOW.get(), user, Math.round(4.0F * ratio) / 5.0F);
            }
        }
        CustomParticlesHelper.summonHamonAuraParticlesFirstPerson(
                UltimateLifeformAddon.HAMON_AURA_RAINBOW.get(), user, Math.round(8.0F * ratio) / 5.0F);
    }
}
