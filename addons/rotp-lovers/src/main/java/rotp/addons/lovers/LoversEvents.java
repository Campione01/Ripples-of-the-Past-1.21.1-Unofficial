package rotp.addons.lovers;

import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.lovers.entity.LoversStandEntity;
import rotp.addons.lovers.state.LoversLinkService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = LoversAddon.MOD_ID)
public final class LoversEvents {
    private LoversEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                LoversAddon.LOVERS_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer owner
                && owner.hasData(LoversAddon.LINK_STATE)) {
            LoversLinkService.validate(owner);
        }
    }

    public static float shareAcceptedDamage(
            Player player, DamageContainer container, float amount) {
        DamageSource source = container.getSource();
        if (!(player instanceof ServerPlayer owner)
                || amount <= 0.0F
                || source.is(
                        LoversDamageTypes.LOVERS_FEEDBACK)) {
            return amount;
        }

        LivingEntity target = LoversLinkService.validate(owner);
        if (target == null
                || LoversAddon.activeStand(owner) == null) {
            return amount;
        }

        DamageSource feedbackSource = DamageUtil.make(
                owner.level(),
                LoversDamageTypes.LOVERS_FEEDBACK,
                owner);
        float sharedAmount = amount * 0.5F;
        container.setNewDamage(sharedAmount);
        target.hurt(feedbackSource, LoversConfig.scaleFeedback(amount));
        return sharedAmount;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity entity = event.getEntity();
        MinecraftServer server = entity.getServer();
        LoversLinkService.clearByTarget(
                server, entity.getUUID());
        if (entity instanceof ServerPlayer owner) {
            LoversLinkService.clearOwner(owner);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            LoversLinkService.clearOwner(original);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            LoversLinkService.clearOwner(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoversLinkService.clearOwner(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoversLinkService.clearOwner(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LoversLinkService.clearOwner(player);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity()
                        instanceof LivingEntity living)) {
            return;
        }
        LoversLinkService.clearByTarget(
                event.getLevel().getServer(),
                living.getUUID());
        if (living instanceof LoversStandEntity stand) {
            LoversLinkService.clearByStand(stand);
        }
    }
}
