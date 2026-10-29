package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.entityrender.entities.NoopEntityRenderer;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import rotp.addons.ultimate_lifeform.entity.UltimateAreaEffectEntity;
import rotp.addons.ultimate_lifeform.entity.UltimateParticleEmissionEvidence.EmissionBatch;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public final class UltimateAreaEffectRenderer
        extends NoopEntityRenderer<UltimateAreaEffectEntity> {
    public UltimateAreaEffectRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            UltimateAreaEffectEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        ClientReceipt receipt = entity.getType()
                        == UltimateLifeformAddon
                                .ULTIMATE_SENDO_HAMON_OVERDRIVE.get()
                ? ClientReceipt.SENDO_AREA_ENTITY
                : entity.getType()
                                == UltimateLifeformAddon
                                        .ULTIMATE_TURQUOISE_BLUE_OVERDRIVE
                                        .get()
                        ? ClientReceipt.TURQUOISE_AREA_ENTITY
                        : ClientReceipt.HAMON_BLOCK_AREA_ENTITY;
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
        UltimateLifeformClientAcceptance.recordGlobalVisual(
                entity,
                receipt,
                receipt == ClientReceipt.SENDO_AREA_ENTITY
                        ? "ultimate_hamon_sendo_overdrive"
                        : receipt == ClientReceipt.TURQUOISE_AREA_ENTITY
                                ? "ultimate_hamon_turquoise_blue_overdrive"
                                : "hamon_block_charge");
        EmissionBatch emission = entity.drainParticleEvidence();
        if (emission == null) {
            return;
        }
        ClientReceipt emissionReceipt = entity.isSendo()
                ? ClientReceipt.SENDO_WAVE_PARTICLE_EMISSION
                : entity.isTurquoise()
                        ? ClientReceipt
                                .TURQUOISE_SPHERE_PARTICLE_EMISSION
                        : ClientReceipt
                                .HAMON_BLOCK_CHARGE_PARTICLE_EMISSION;
        UltimateParticle.ParticleBatchProof proof =
                UltimateParticle.claimDownstreamBatch(
                        entity,
                        emissionReceipt,
                        emission);
        if (proof == null) {
            return;
        }
        UltimateLifeformClientAcceptance.recordGlobalParticleEmission(
                proof);
    }
}
