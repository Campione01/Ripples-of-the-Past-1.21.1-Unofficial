package rotp.addons.rhcp.client;

import rotp.core.client.ClientGlobals;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class RhcpParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean flash;

    private RhcpParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz,
            SpriteSet sprites,
            boolean flash) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        this.flash = flash;
        hasPhysics = false;
        friction = flash ? 1.0F : 0.96F;
        lifetime = flash ? 5 : Math.max(
                1, (int) (4.0D / (random.nextDouble() * 0.8D + 0.2D)));
        quadSize = flash
                ? 1.8F
                : quadSize * 0.375F;
        if (flash) {
            xd = yd = zd = 0.0D;
        }
        if (!flash) {
            xd *= 0.1D;
            yd *= 0.1D;
            zd *= 0.1D;
        }
        rCol = 1.0F;
        gCol = flash ? 0.9F : 0.0F;
        bCol = flash ? 0.05F : 0.0F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        return flash ? 7.1F * Mth.sin(
                Math.max(0.0F, age + partialTick - 1.0F)
                        * 0.25F * Mth.PI) : super.getQuadSize(partialTick);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (flash) {
            alpha = Mth.clamp(
                    0.6F - (age + partialTick - 1.0F) * 0.125F,
                    0.0F, 1.0F);
        }
        super.render(buffer, camera, partialTick);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        // Full brightness comes from getLightColor; the flash still needs alpha blending.
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class ElectricProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ElectricProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dx,
                double dy,
                double dz) {
            return ClientGlobals.canSeeStands
                    ? new RhcpParticle(
                            level, x, y, z, dx, dy, dz, sprites, false)
                    : null;
        }
    }

    public static final class FlashProvider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public FlashProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double dx,
                double dy,
                double dz) {
            return ClientGlobals.canSeeStands
                    ? new RhcpParticle(
                            level, x, y, z, 0, 0, 0, sprites, true)
                    : null;
        }
    }
}
