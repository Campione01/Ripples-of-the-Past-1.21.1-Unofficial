package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.impl.powers.hamon.client.particle.custom.HamonSparkEntityOffsetParticle;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** The client halves of the Ultimate Lifeform's own clientbound payloads. */
@OnlyIn(Dist.CLIENT)
public final class UltimateLifeformClientEffects {
    private UltimateLifeformClientEffects() {}

    // 1.16 Player.playSound / playLocalSound on the user's client: at the client player's own position
    public static void playAtOwnPosition(
            ResourceLocation sound, int source, float volume, float pitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(sound);
        SoundSource[] sources = SoundSource.values();
        if (player == null || event == null || source < 0
                || source >= sources.length) {
            return;
        }
        player.level().playLocalSound(
                player.getX(),
                player.getY(),
                player.getZ(),
                event,
                sources[source],
                volume,
                pitch,
                false);
    }

    // 1.16 CustomParticles.createUltimateHamonSparkParticles with an entity to follow: every particle at the one
    // point given, moving with the entity from there
    public static void sparksFollowing(
            int entityId, double x, double y, double z, int count) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity entity = level != null ? level.getEntity(entityId) : null;
        if (entity == null) {
            return;
        }
        RandomSource random = level.random;
        Vec3 at = new Vec3(x, y, z);
        for (int index = 0; index < count; index++) {
            CustomParticlesHelper.addParticle(
                    new HamonSparkEntityOffsetParticle(
                            level,
                            entity,
                            x,
                            y,
                            z,
                            random.nextGaussian() * 0.25D,
                            random.nextGaussian() * 0.25D,
                            random.nextGaussian() * 0.25D,
                            UltimateLifeformAddon.RAINBOW_SPARK.get()),
                    at,
                    false,
                    false);
        }
    }
}
