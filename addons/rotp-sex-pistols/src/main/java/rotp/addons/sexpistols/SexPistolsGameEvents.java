package rotp.addons.sexpistols;

import rotp.core.mechanics.resolve.ResolveCounter;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;

import rotp.addons.sexpistols.content.revolver.entity.RevolverBulletEntity;
import rotp.addons.sexpistols.content.revolver.item.RevolverItem;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsCoordinator;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsDebugCommand;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsEntity;
import rotp.addons.sexpistols.content.sexpistols.SexPistolsProjectileRedirector;
import rotp.addons.sexpistols.network.SexPistolsConfigSync;
import rotp.addons.sexpistols.registry.SexPistolsAttachments;
import rotp.addons.sexpistols.registry.SexPistolsStands;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(
        modid = SexPistolsIds.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME)
public final class SexPistolsGameEvents {
    private SexPistolsGameEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(
            RegisterCommandsEvent event) {
        SexPistolsDebugCommand.register(
                event.getDispatcher());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity owner
                && owner.level() instanceof ServerLevel level
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .tickServer(level, owner);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity owner = event.getEntity();
        clearTransientRevolverState(owner);
        if (owner.level() instanceof ServerLevel level) {
            cleanupOwnedBullets(level, owner);
            if (owner.hasData(
                    SexPistolsAttachments
                            .SEX_PISTOLS_COORDINATOR)) {
                SexPistolsAttachments.coordinator(owner)
                        .forgetSwarmOnOwnerDeath();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(
            LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        float damage = event.getNewDamage();
        if (damage <= 0.0F
                || target.level().isClientSide()) {
            return;
        }

        if (target.level() instanceof ServerLevel level
                && target.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)
                && !(event.getSource()
                        instanceof StandLinkDamageSource linkedDamage
                        && linkedDamage.standEntity
                                instanceof SexPistolsEntity)
                && !(event.getSource().getDirectEntity()
                        instanceof SexPistolsEntity)
                && !(event.getSource().getEntity()
                        instanceof SexPistolsEntity)) {
            var members = SexPistolsAttachments
                    .coordinator(target)
                    .loadedMembers(level);
            if (!members.isEmpty()) {
                float share = damage / members.size();
                for (SexPistolsEntity member : members) {
                    float remaining = member.getHealth() - share;
                    if (remaining <= 0.0F) {
                        member.setHealth(0.0F);
                        member.die(event.getSource());
                    }
                    else {
                        member.setHealth(remaining);
                    }
                }
            }
        }

        if (!(event.getSource().getDirectEntity()
                        instanceof Projectile projectile)
                || !(projectile.getOwner()
                        instanceof LivingEntity attacker)) {
            return;
        }
        if (event.getSource().getEntity() == target
                || attacker == target) {
            return;
        }
        StandPower power = StandPower.get(attacker);
        if (power == null
                || power.getPowerType()
                        != SexPistolsStands.SEX_PISTOLS.get()) {
            return;
        }
        float multiplier = projectile
                        instanceof RevolverBulletEntity bullet
                ? bullet.resolveGainMultiplier()
                : genericProjectileResolveMultiplier(projectile);
        if (multiplier > 1.0F) {
            ResolveCounter.addResolve(
                    power,
                    target,
                    damage * (multiplier - 1.0F));
        }
    }

    @SubscribeEvent
    public static void onRightClickFood(
            PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)
                || !player.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        ItemStack food = event.getItemStack();
        if (food.getFoodProperties(player) == null) {
            return;
        }
        SexPistolsCoordinator coordinator =
                SexPistolsAttachments.coordinator(player);
        SexPistolsEntity nearest = coordinator
                .loadedMembers(level)
                .stream()
                .filter(member -> member.canBeFedBy(
                        player, event.getHand()))
                .min(java.util.Comparator.comparingDouble(
                        member -> member.distanceToSqr(player)))
                .orElse(null);
        if (nearest == null
                || !nearest.tryFeedFrom(
                        player, event.getHand())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        SexPistolsProjectileRedirector.redirect(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        clearTransientRevolverState(original);
        clearTransientRevolverState(event.getEntity());
        if (!original.hasData(
                SexPistolsAttachments
                        .SEX_PISTOLS_COORDINATOR)) {
            return;
        }
        SexPistolsCoordinator copied =
                SexPistolsAttachments.coordinator(
                        event.getEntity());
        copied.copyPersistentFrom(
                SexPistolsAttachments.coordinator(original));
        copied.syncForTracking(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        clearTransientRevolverState(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) {
            SexPistolsConfigSync.sendTo(player);
        }
        syncIfPresent(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clearTransientRevolverState(event.getEntity());
        syncIfPresent(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        Player owner = event.getEntity();
        clearTransientRevolverState(owner);
        if (owner.level() instanceof ServerLevel level) {
            cleanupOwnedBullets(level, owner);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearTransientRevolverState(event.getEntity());
        if (!(event.getEntity()
                instanceof ServerPlayer owner)
                || owner.getServer() == null) {
            return;
        }
        ServerLevel previousLevel =
                owner.getServer().getLevel(event.getFrom());
        if (previousLevel != null) {
            cleanupOwnedBullets(previousLevel, owner);
        }
        if (owner.hasData(
                SexPistolsAttachments
                        .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .syncForTracking(owner);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer
                    && event.getTarget() instanceof LivingEntity owner
                    && owner.hasData(
                            SexPistolsAttachments
                                    .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .syncForTracking(owner);
        }
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!event.getPlayer().level().isClientSide()) {
            RevolverItem.clearTransientState(
                    event.getEntity().getItem());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityLeave(
            EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getEntity() instanceof
                rotp.addons.sexpistols
                        .content.sexpistols.SexPistolsEntity member) {
            LivingEntity owner = member.getSwarmOwner();
            if (owner != null
                    && owner.hasData(
                            SexPistolsAttachments
                                    .SEX_PISTOLS_COORDINATOR)) {
                SexPistolsAttachments.coordinator(owner)
                        .onMemberUnloaded(
                                owner,
                                member.getMemberNumber(),
                                member.getUUID());
            }
        }
        else if (event.getEntity() instanceof LivingEntity owner
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            clearTransientRevolverState(owner);
            SexPistolsAttachments.coordinator(owner)
                    .cleanup(level, owner);
        }
        else if (event.getEntity() instanceof Player owner) {
            clearTransientRevolverState(owner);
            RevolverBulletEntity.discardOwnedBy(
                    level, owner.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RevolverBulletEntity.clearSoftBlockDamage(level);
        }
    }

    private static void cleanupOwnedBullets(
            ServerLevel level, LivingEntity owner) {
        if (owner.hasData(
                SexPistolsAttachments
                        .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .cleanup(level, owner);
        }
        else if (owner instanceof Player) {
            RevolverBulletEntity.discardOwnedBy(
                    level, owner.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RevolverBulletEntity.clearAllSoftBlockDamage();
    }

    private static void syncIfPresent(Player owner) {
        if (owner instanceof ServerPlayer
                && owner.hasData(
                        SexPistolsAttachments
                                .SEX_PISTOLS_COORDINATOR)) {
            SexPistolsAttachments.coordinator(owner)
                    .syncForTracking(owner);
        }
    }

    private static void clearTransientRevolverState(
            LivingEntity owner) {
        if (owner instanceof Player player) {
            for (int slot = 0;
                    slot < player.getInventory().getContainerSize();
                    slot++) {
                RevolverItem.clearTransientState(
                        player.getInventory().getItem(slot));
            }
        }
        RevolverItem.clearTransientState(
                owner.getMainHandItem());
        RevolverItem.clearTransientState(
                owner.getOffhandItem());
    }

    private static float genericProjectileResolveMultiplier(
            Projectile projectile) {
        int ricochets = Math.max(
                0,
                projectile.getPersistentData()
                        .getInt("SexPistolsRicochets"));
        float ricochetMultiplier = 1.0F;
        for (int i = 0; i < ricochets; i++) {
            ricochetMultiplier = Math.min(
                    5.0F,
                    ricochetMultiplier * 1.25F);
        }
        return 2.5F * ricochetMultiplier;
    }
}
