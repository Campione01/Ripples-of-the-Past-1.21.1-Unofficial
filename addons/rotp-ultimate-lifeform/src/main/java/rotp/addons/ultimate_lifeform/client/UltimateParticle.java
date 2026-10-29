package rotp.addons.ultimate_lifeform.client;

import javax.annotation.Nullable;

import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.client.UltimateLifeformDownstreamSubmissions.RegisteredSubmission;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.DownstreamPointTicket;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.EmissionBatch;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.PendingAttempt;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class UltimateParticle
        extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final ClientReceipt receipt;

    private UltimateParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xd,
            double yd,
            double zd,
            SpriteSet sprites,
            ClientReceipt receipt) {
        super(level, x, y, z, xd, yd, zd);
        this.sprites = sprites;
        this.receipt = receipt;
        this.friction = 0.92F;
        this.gravity = 0.0F;
        this.lifetime = 14 + random.nextInt(10);
        this.quadSize = 0.08F + random.nextFloat() * 0.08F;
        this.hasPhysics = false;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setSpriteFromAge(sprites);
            alpha = Math.max(
                    0.0F,
                    1.0F - (float) age / lifetime);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void render(
            VertexConsumer vertices,
            Camera camera,
            float partialTick) {
        super.render(vertices, camera, partialTick);
        UltimateLifeformClientAcceptance.recordGlobalVisual(
                receipt,
                receipt.name().toLowerCase(java.util.Locale.ROOT),
                x,
                y,
                z);
    }

    @Nullable
    static synchronized ParticleBatchProof claimDownstreamBatch(
            UltimateAreaEffectEntity entity,
            ClientReceipt receipt,
            EmissionBatch batch) {
        if (entity == null
                || receipt != areaEmissionReceipt(entity)
                || batch == null
                || batch.count() <= 0
                || batch.points().size() != batch.count()
                || !expectedResource(receipt).equals(batch.resource())) {
            UltimateLifeformDownstreamSubmissions.discardParticleBatch(
                    batch);
            return null;
        }
        RegisteredSubmission registration =
                UltimateLifeformDownstreamSubmissions
                        .combineParticleBatch(entity, receipt, batch);
        if (registration == null) {
            return null;
        }
        return new ParticleBatchProof(
                entity,
                receipt,
                batch,
                registration);
    }

    private static ClientReceipt areaEmissionReceipt(
            UltimateAreaEffectEntity entity) {
        return entity.isSendo()
                ? ClientReceipt.SENDO_WAVE_PARTICLE_EMISSION
                : entity.isTurquoise()
                        ? ClientReceipt
                                .TURQUOISE_SPHERE_PARTICLE_EMISSION
                        : ClientReceipt
                                .HAMON_BLOCK_CHARGE_PARTICLE_EMISSION;
    }

    private static String expectedResource(ClientReceipt receipt) {
        return receipt == ClientReceipt.SENDO_WAVE_PARTICLE_EMISSION
                ? UltimateLifeformAddon.id("rainbow_sendo").toString()
                : UltimateLifeformAddon.id("rainbow_spark").toString();
    }

    private static void recordDownstreamSubmission(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ) {
        ResourceLocation resource = BuiltInRegistries.PARTICLE_TYPE
                .getKey(type);
        if (resource == null) {
            return;
        }
        PendingAttempt pending = UltimateParticleEmissionEvidence.pending(
                level,
                resource.toString(),
                level.getGameTime(),
                x,
                y,
                z,
                velocityX,
                velocityY,
                velocityZ);
        if (pending == null) {
            return;
        }
        DownstreamPointTicket ticket =
                UltimateLifeformDownstreamSubmissions
                        .registerParticlePoint(
                                pending.entity(),
                                level,
                                pending.sequence(),
                                pending.resource(),
                                pending.clientGameTime(),
                                pending.x(),
                                pending.y(),
                                pending.z(),
                                pending.velocityX(),
                                pending.velocityY(),
                                pending.velocityZ());
        boolean acknowledged = pending.acknowledge(
                ticket,
                () -> UltimateLifeformDownstreamSubmissions
                        .discardParticlePoint(ticket));
        if (!acknowledged) {
            UltimateLifeformDownstreamSubmissions
                    .discardParticlePoint(ticket);
        }
    }

    public static final class Provider
            implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xd,
                double yd,
                double zd) {
            ClientReceipt receipt = type
                            == UltimateLifeformAddon
                                    .HAMON_BREATH_AURA_RAINBOW.get()
                    ? ClientReceipt.HAMON_BREATH_PARTICLE
                    : type == UltimateLifeformAddon.RAINBOW_SENDO.get()
                            ? ClientReceipt.RAINBOW_SENDO_PARTICLE
                            : type == UltimateLifeformAddon
                                    .RAINBOW_SPARK.get()
                                    ? ClientReceipt
                                            .RAINBOW_SPARK_PARTICLE
                                    : ClientReceipt
                                            .HAMON_AURA_PARTICLE;
            UltimateParticle particle = new UltimateParticle(
                    level,
                    x,
                    y,
                    z,
                    xd,
                    yd,
                    zd,
                    sprites,
                    receipt);
            recordDownstreamSubmission(
                    type,
                    level,
                    x,
                    y,
                    z,
                    xd,
                    yd,
                    zd);
            return particle;
        }
    }

    static final class ParticleBatchProof {
        private final UltimateAreaEffectEntity entity;
        private final ClientReceipt receipt;
        private final EmissionBatch batch;
        private final RegisteredSubmission registration;
        private boolean claimed;

        private ParticleBatchProof(
                UltimateAreaEffectEntity entity,
                ClientReceipt receipt,
                EmissionBatch batch,
                RegisteredSubmission registration) {
            this.entity = entity;
            this.receipt = receipt;
            this.batch = batch;
            this.registration = registration;
        }

        synchronized boolean claim() {
            if (claimed) {
                return false;
            }
            claimed = true;
            return true;
        }

        UltimateAreaEffectEntity entity() {
            return entity;
        }

        ClientReceipt receipt() {
            return receipt;
        }

        EmissionBatch batch() {
            return batch;
        }

        RegisteredSubmission registration() {
            return registration;
        }
    }
}
