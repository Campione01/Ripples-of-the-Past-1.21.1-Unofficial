package rotp.addons.tusk.client;

import rotp.core.client.ClientGlobals;
import rotp.core.client.entityrender.stand.StandVisualContext;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.tusk.particle.NailSwipeParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;

public final class NailSwipeParticle extends TextureSheetParticle {
    public static final int FRAME_COUNT = 8;
    private final SpriteSet sprites;
    private final NailSwipeParticleOptions options;

    private NailSwipeParticle(
            NailSwipeParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.options = options;
        this.sprites = sprites;
        lifetime = FRAME_COUNT;
        quadSize = 0.3F;
        hasPhysics = false;
        float gray = 0.1F + random.nextFloat() * 0.3F;
        setColor(gray, gray, gray);
        setSpriteFromAge(sprites);
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        StandVisualContext context = ClientGlobals.canSeeStands
                ? TuskVisuals.forOwnerUuid(
                        level, options.ownerUuid(), partialTick)
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
        if (!removed) {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory
            implements ParticleProvider<NailSwipeParticleOptions> {
        private final SpriteSet sprites;

        public Factory(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                NailSwipeParticleOptions options,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed) {
            return new NailSwipeParticle(
                    options, level, x, y, z, sprites);
        }
    }
}
