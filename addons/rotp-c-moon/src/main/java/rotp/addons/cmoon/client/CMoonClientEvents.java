package rotp.addons.cmoon.client;

import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.client.particle.CustomParticlesHelper;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.impl.powers.hamon.client.particle.HamonAuraParticle;
import rotp.core.impl.powers.hamon.client.particle.HamonSparkParticle;

import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(
        modid = CMoonAddon.MOD_ID,
        value = Dist.CLIENT)
public final class CMoonClientEvents {
    private CMoonClientEvents() {}

    @SubscribeEvent
    public static void registerRenderers(
            EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                CMoonAddon.C_MOON_ENTITY.get(),
                StandEntityRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticleProviders(
            RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                CMoonAddon.HAMON_SPARK_PURPLE.get(),
                HamonSparkParticle.HamonParticleFactory::new);
        event.registerSpriteSet(
                CMoonAddon.HAMON_AURA_PURPLE.get(),
                sprites -> {
                    CustomParticlesHelper.saveSpriteSet(
                            CMoonAddon.HAMON_AURA_PURPLE.get(),
                            sprites);
                    return new HamonAuraParticle.Factory(sprites);
                });
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof CMoonStandEntity stand)
                    || !stand.isShiftActive()) {
                continue;
            }
            LivingEntity user = stand.getUser();
            StandPower power =
                    user != null ? StandPower.get(user) : null;
            if (user == null || power == null
                    || power.getMaxStamina() <= 0.0F) {
                continue;
            }
            float energyRatio = Math.min(
                    power.getStamina() / power.getMaxStamina(),
                    1.0F);
            if (stand.getRandom().nextFloat() >= energyRatio) {
                continue;
            }
            CustomParticlesHelper.createHamonAuraParticle(
                    CMoonAddon.HAMON_AURA_PURPLE.get(),
                    user,
                    user.getX()
                            + (stand.getRandom().nextDouble() - 0.5D)
                                    * (user.getBbWidth() + 0.5F),
                    user.getY()
                            + stand.getRandom().nextDouble()
                                    * user.getBbHeight() * 0.5F,
                    user.getZ()
                            + (stand.getRandom().nextDouble() - 0.5D)
                                    * (user.getBbWidth() + 0.5F));
        }
    }
}
