package rotp.addons.frosttraveler.client;

import java.util.UUID;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.frosttraveler.particle.MeatParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.entity.player.Player;

public final class MeatParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final UUID ownerUuid;

    private MeatParticle(
            MeatParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        ownerUuid = options.ownerUuid();
        lifetime = 8;
        gravity = 0.05F;
        quadSize = 0.08F;
        pickSprite(sprites);
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        Player owner = level.getPlayerByUUID(ownerUuid);
        StandVisualContext context =
                ClientGlobals.canSeeStands && owner != null
                        ? CreamVisuals.context(owner, partialTick)
                        : null;
        float effectiveAlpha =
                context != null ? context.effectiveAlpha() : 0.0F;
        setAlpha(effectiveAlpha);
        if (effectiveAlpha > 0.0F) {
            super.render(vertices, camera, partialTick);
        }
    }

    @Override
    public void tick() {
        super.tick();
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider
            implements ParticleProvider<MeatParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                MeatParticleOptions options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xd,
                double yd,
                double zd) {
            return new MeatParticle(
                    options,
                    level,
                    x,
                    y,
                    z,
                    xd,
                    yd,
                    zd,
                    sprites);
        }
    }
}
