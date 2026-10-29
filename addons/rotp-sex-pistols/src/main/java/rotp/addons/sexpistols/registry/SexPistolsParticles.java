package rotp.addons.sexpistols.registry;

import rotp.addons.sexpistols.SexPistolsIds;
import rotp.addons.sexpistols.particle.BulletHoleParticleType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SexPistolsParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(
                    Registries.PARTICLE_TYPE,
                    SexPistolsIds.MOD_ID);

    public static final DeferredHolder<
            ParticleType<?>, BulletHoleParticleType> BULLET_HOLE =
            PARTICLES.register(
                    SexPistolsIds.BULLET_HOLE_PATH,
                    BulletHoleParticleType::new);
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> REVOLVER_CASING =
            PARTICLES.register(
                    SexPistolsIds.REVOLVER_CASING_PATH,
                    () -> new SimpleParticleType(true));
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            REVOLVER_MUZZLE_FLASH = PARTICLES.register(
                    SexPistolsIds.REVOLVER_MUZZLE_FLASH_PATH,
                    () -> new SimpleParticleType(true));
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType> KICK_MUZZLE_FLASH =
            PARTICLES.register(
                    SexPistolsIds.KICK_MUZZLE_FLASH_PATH,
                    () -> new SimpleParticleType(true));
    public static final DeferredHolder<
            ParticleType<?>, SimpleParticleType>
            KICK_MUZZLE_FLASH_CRITICAL = PARTICLES.register(
                    SexPistolsIds.KICK_MUZZLE_FLASH_CRITICAL_PATH,
                    () -> new SimpleParticleType(true));

    private SexPistolsParticles() {}

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
