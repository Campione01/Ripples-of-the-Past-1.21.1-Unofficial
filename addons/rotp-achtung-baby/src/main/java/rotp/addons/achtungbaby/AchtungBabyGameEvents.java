package rotp.addons.achtungbaby;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.achtungbaby.network.AchtungBabyStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AchtungBabyAddon.MOD_ID)
public final class AchtungBabyGameEvents {
    private AchtungBabyGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                AchtungBabyAddon.ACHTUNG_BABY_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    /**
     * Breaking a block that was placed from an invisible item gives the invisible item back.
     *
     * <p>This is the working half of 1.16's invisible-block code (see
     * {@code rotp.addons.achtungbaby.mixin.BlockItemMixin} for what the rest of it turned out to be). 1.16 ran on
     * {@code BlockEvent.BreakEvent} and then tagged <i>every</i> item entity within two blocks, which also caught a
     * neighbouring block's drops and anything lying on the ground. {@code BlockDropsEvent} hands over exactly the
     * drops of the block that broke, so the tag goes only where it belongs.
     */
    @SubscribeEvent
    public static void returnInvisibleBlockDrops(BlockDropsEvent event) {
        if (!AchtungBabyService.takeBlockInvisible(
                event.getLevel(), event.getPos())) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            AchtungBabyService.setInvisible(drop.getItem());
        }
    }

    /**
     * 1.16 AchtungBabyEventHandler.onLivingAttack (LivingAttackEvent): whoever attacks an Achtung Baby user has a 30%
     * chance of Weakness II and Slowness II for 100 ticks - a player only if they have a Stand of their own.
     */
    @SubscribeEvent
    public static void weakenAttacker(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)
                || attacker == target
                || !AchtungBabyAddon.isUser(target)
                || target.getRandom().nextFloat() >= 0.3F) {
            return;
        }
        if (attacker instanceof Player) {
            StandPower power = StandPower.get(attacker);
            if (power == null || !power.hasPower()) {
                return;
            }
        }
        attacker.addEffect(new MobEffectInstance(
                MobEffects.WEAKNESS, 100, 1));
        attacker.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
    }

    /**
     * 1.16 InvisibilityAggroHandler: a mob that sets its target on a player in Perfect Invisibility is left with no
     * target. 1.16 cleared it right after Mob.setTarget; the change event does the same before it lands.
     */
    @SubscribeEvent
    public static void ignorePerfectlyInvisible(LivingChangeTargetEvent event) {
        if (event.getTargetType()
                        == LivingChangeTargetEvent.LivingTargetType.MOB_TARGET
                && event.getEntity() instanceof Mob
                && event.getNewAboutToBeSetTarget() instanceof Player player
                && AchtungBabyAddon.state(player).perfectInvisible()) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchtungBabyService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchtungBabyService.cleanup(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            AchtungBabyService.cleanup(original);
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            AchtungBabyAddon.state(player).clear();
            AchtungBabyService.sync(player);
        }
    }

    /**
     * Sends the saved state to the returning player; Perfect Invisibility is still on if it was on at logout. A Ghost
     * mode left in the save by a server that stopped without the logout event is ended here.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (AchtungBabyAddon.state(player).ghostMode()) {
                AchtungBabyService.endGhost(player);
            } else {
                AchtungBabyService.sync(player);
            }
        }
    }

    /**
     * 1.16 AchtungBabyEventHandler.onPlayerLogout: only Ghost mode ends, and the game mode it replaced is restored
     * before the player is saved. Perfect Invisibility stays (1.16 PerfectInvisibilityManager only dropped a cache).
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchtungBabyService.endGhost(player);
        }
    }

    // The client builds a new local player on a dimension change, without the state the old one had.
    @SubscribeEvent
    public static void onChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchtungBabyService.sync(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer
                && event.getTarget() instanceof ServerPlayer target) {
            PacketDistributor.sendToPlayer(
                    observer,
                    AchtungBabyStatePayload.from(target));
        }
    }
}
