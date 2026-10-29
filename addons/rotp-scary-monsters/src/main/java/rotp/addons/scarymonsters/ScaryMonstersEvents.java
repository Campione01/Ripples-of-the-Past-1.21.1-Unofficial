package rotp.addons.scarymonsters;

import rotp.addons.scarymonsters.entity.ScaryDinosaurEntity;
import rotp.addons.scarymonsters.entity.ScaryMonstersStandEntity;
import rotp.addons.scarymonsters.network.ScaryMonstersStatePayload;
import rotp.addons.scarymonsters.state.ScaryMonstersState;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = ScaryMonstersAddon.MOD_ID)
public final class ScaryMonstersEvents {
    private ScaryMonstersEvents() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ScaryMonstersAddon.STAND_ENTITY.get(),
                ScaryMonstersStandEntity.createAttributes().build());
        event.put(ScaryMonstersAddon.DINOSAUR.get(),
                ScaryDinosaurEntity.createDinosaurAttributes().build());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ScaryMonstersState state = ScaryMonstersAddon.state(player);
        state.tickOwnedEffects();
        if (!ScaryMonstersAddon.isUser(player)) {
            if (state.dynamicVision() || state.dragonForm()
                    || state.dinosaurForm()
                    || state.controlledDinosaur() != null) {
                ScaryMonstersService.cleanupAll(player);
            }
            return;
        }
        ScaryMonstersService.tickDynamicVision(player);
        if (state.dinosaurForm() && state.controlledDinosaur() != null
                && player.serverLevel().getEntity(state.controlledDinosaur()) == null) {
            ScaryMonstersService.disableDinosaurForm(player, false);
        }
        if (player.tickCount % 40 == 0) ScaryMonstersService.sync(player);
    }

    // 1.16 KnockdownEvents: the tail sweep's knockdown ticks from every living
    // entity's tick and again from a player's.
    @SubscribeEvent
    public static void onLivingTickKnockdown(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) {
            ScaryMonstersService.tickKnockdowns(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerTickKnockdown(PlayerTickEvent.Post event) {
        ScaryMonstersService.tickKnockdowns(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ScaryMonstersService.clearKnockdowns();
    }

    // A knockdown ends with its target (any living entity); LOWEST and not
    // cancelled, so a death another mod stops keeps it. Server side only:
    // the maps belong to the server thread.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockdownTargetDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide()) {
            ScaryMonstersService.forgetKnockdown(event.getEntity().getUUID());
        }
    }

    // Discarded or killed-and-removed; an unload or a portal keeps it.
    @SubscribeEvent
    public static void onKnockdownTargetLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity living
                && living.getRemovalReason() != null
                && living.getRemovalReason().shouldDestroy()) {
            ScaryMonstersService.forgetKnockdown(living.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDinosaurJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof ScaryDinosaurEntity dinosaur && !event.getLevel().isClientSide()) {
            ScaryMonstersService.trackLoadedPossession(dinosaur);
        }
    }

    @SubscribeEvent
    public static void onDinosaurLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ScaryDinosaurEntity dinosaur
                && !event.getLevel().isClientSide()
                && dinosaur.getRemovalReason() != null && dinosaur.getRemovalReason().shouldDestroy()) {
            ScaryMonstersService.onPossessedDinosaurTerminal(dinosaur, false);
        }
    }

    // Burnt, blown up, fallen out of the world, killed or taken by a hopper; an unload or a portal keeps it.
    @SubscribeEvent
    public static void onCamouflageStoneLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof ItemEntity stone
                && !event.getLevel().isClientSide()
                && stone.getRemovalReason() != null && stone.getRemovalReason().shouldDestroy()) {
            ScaryMonstersService.onCamouflageStoneDestroyed(stone);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersState state = ScaryMonstersAddon.state(player);
            if (state.dragonForm()) {
                event.setAmount(event.getAmount()
                        * (float) (1.0D
                                - ScaryMonstersConfig
                                        .DRAGON_FORM_DAMAGE_REDUCTION.get()));
            }
        }
        // The block is the core Stand guard now (1.16 StandEntityBlock), which blocks the Stand's damage itself.
    }

    // 1.16 cancelled LivingHurtEvent, which fires inside actuallyHurt: only the damage went; the knockback, the hurt
    // flash, the invulnerability window (a crowd is not countered once per mob) and the attacker's on-hit effects
    // stayed. LivingDamageEvent.Pre is that point in 1.21, where the core ports its own LivingHurtEvent handlers;
    // LOWEST so the hit still counts toward resolve first, as it did in 1.16.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDynamicVisionCounter(LivingDamageEvent.Pre event) {
        // 1.16 blockDamage cancelled a hit the Stand's guard took whole, so the counter never saw it
        if (event.getEntity() instanceof ServerPlayer player
                && !StandUserGuard.blockedWhole(event.getContainer())
                && ScaryMonstersService.counterMeleeAttack(player, event.getSource())) {
            event.setNewDamage(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersService.restoreOwnedCreaturesOnDeath(player);
            ScaryMonstersService.cleanupAll(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            ScaryMonstersService.cleanupAll(original);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersAddon.state(player).clearAll();
            ScaryMonstersAddon.state(player).setPossessedDinosaur(
                    ScaryMonstersAddon.state(event.getOriginal()).possessedDinosaur());
            ScaryMonstersService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersService.recoverPossessionOnLogin(player);
            ScaryMonstersService.sync(player);
        }
    }

    // A relog does not come back knocked down.
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersService.cleanupOnLogout(player);
            ScaryMonstersService.forgetKnockdown(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ScaryMonstersService.cleanupAll(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(
                    observer, ScaryMonstersStatePayload.from(target));
        }
    }
}
