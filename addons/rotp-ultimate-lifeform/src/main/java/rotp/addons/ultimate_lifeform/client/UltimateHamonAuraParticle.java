package rotp.addons.ultimate_lifeform.client;

import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.config.client.ClientModSettings;
import rotp.core.impl.powers.hamon.client.particle.custom.HamonAura3rdPersonParticle;
import rotp.addons.ultimate_lifeform.action.UltimateLifeformAcceptance.ClientReceipt;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 CustomParticles.createUltimateHamonAuraParticle: the add-on's green and rainbow charge aura drawn as the core's
 * HamonAura3PersonParticle (it follows its user, hidden in its user's own first-person view and behind the
 * "third person Hamon aura" setting). It records the aura's client receipt when it is drawn, as UltimateParticle did.
 */
final class UltimateHamonAuraParticle extends HamonAura3rdPersonParticle {
    private final LivingEntity user;

    private UltimateHamonAuraParticle(ClientLevel level, LivingEntity user, double x, double y, double z,
            SpriteSet sprites) {
        super(level, user, x, y, z, 0.0D, 0.0D, 0.0D, sprites);
        this.user = user;
    }

    static void spawn(ParticleOptions type, LivingEntity user, double x, double y, double z) {
        SpriteSet sprites = CustomParticlesHelper.getSavedSpriteSet(type.getType());
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (sprites == null) {
            level.addParticle(type, x, y, z, 0.0D, 0.0D, 0.0D);
            return;
        }
        CustomParticlesHelper.addParticle(new UltimateHamonAuraParticle(level, user, x, y, z, sprites),
                new Vec3(x, y, z), false, false);
    }

    @Override
    public void render(VertexConsumer vertices, Camera camera, float partialTick) {
        super.render(vertices, camera, partialTick);
        Minecraft mc = Minecraft.getInstance();
        if (ClientModSettings.getSettingsReadOnly().thirdPersonHamonAura
                && !(mc.cameraEntity == user && mc.options.getCameraType() == CameraType.FIRST_PERSON)) {
            UltimateLifeformClientAcceptance.recordGlobalVisual(ClientReceipt.HAMON_AURA_PARTICLE,
                    ClientReceipt.HAMON_AURA_PARTICLE.name().toLowerCase(java.util.Locale.ROOT), x, y, z);
        }
    }
}
