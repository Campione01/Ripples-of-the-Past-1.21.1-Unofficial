package rotp.addons.stone_legacy_zombie_orders;

import java.util.List;
import java.util.UUID;

import rotp.core.api.playerpower.PlayerPowerTransitions;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.impl.powers.vampirism.entity.HungryZombieEntity;
import rotp.core.impl.powers.zombie.ZombieData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ZombieOrdersAddon.MOD_ID)
public final class ZombieOrdersEvents {
    private static final List<EquipmentSlot> DROP_SLOTS =
            List.of(
                    EquipmentSlot.MAINHAND,
                    EquipmentSlot.OFFHAND,
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET);

    private ZombieOrdersEvents() {}

    @SubscribeEvent
    public static void registerCommands(
            RegisterCommandsEvent event) {
        ZombieOwnerCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onZombieTick(
            EntityTickEvent.Post event) {
        if (!(event.getEntity()
                        instanceof HungryZombieEntity zombie)
                || zombie.level().isClientSide()) {
            return;
        }
        var state = ZombieOrdersAddon.state(zombie);
        boolean ownerNeedsHelp = ZombieOwnership
                .resolveOwner(zombie)
                .filter(owner ->
                        zombie.distanceToSqr(owner) < 144.0D
                                && owner.getLastHurtByMob()
                                        != null)
                .isPresent();
        boolean active = state.orderedToWait()
                && !zombie.isInWater()
                && zombie.onGround()
                && !ownerNeedsHelp;
        state.setActiveWaitingPose(zombie, active);
        if (active) {
            zombie.getNavigation().stop();
            zombie.setTarget(null);
            var velocity = zombie.getDeltaMovement();
            zombie.setDeltaMovement(
                    0.0D, velocity.y, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onConvertedTick(
            EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living instanceof HungryZombieEntity
                || living.level().isClientSide()
                || !living.hasData(
                        ZombieOrdersAddon.CONVERSION_STATE)
                || !ZombieOrdersAddon.conversion(living)
                        .isConverted()) {
            return;
        }
        // A Hungry Zombie asks getOwner() every tick; a converted
        // entity checks its owner the same way.
        ZombieOwnership.releaseIfOwnerLostVampirism(living);
    }

    @SubscribeEvent
    public static void onDarkAura(
            PlayerTickEvent.Post event) {
        if (!(event.getEntity()
                        instanceof ServerPlayer player)) {
            return;
        }
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(player);
        if (action == null
                || action.getPhase() != ActionPhase.PERFORM
                || !"vampirism_dark_aura".equals(
                        action.ability
                                .getAbilityId()
                                .nameInMoveset())) {
            return;
        }
        releaseOwnedWithinRange(player);
    }

    @SubscribeEvent
    public static void onDespawn(
            MobDespawnEvent event) {
        if (event.getEntity()
                instanceof HungryZombieEntity) {
            event.setResult(MobDespawnEvent.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(
            LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (ZombieOwnership.ownerUuid(living).isPresent()) {
            ZombieOwnership.removeFromOwnerRoster(living);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(
            PlayerEvent.Clone event) {
        if (!event.isWasDeath()
                || event.getEntity().level().isClientSide()) {
            return;
        }
        Player original = event.getOriginal();
        var conversion =
                ZombieOrdersAddon.conversion(original);
        if (!conversion.isConverted()) {
            return;
        }
        PlayerPower source = PlayerPower
                .getOptional(original)
                .orElse(null);
        PlayerPower target = PlayerPower
                .getOptional(event.getEntity())
                .orElse(null);
        if (conversion.convertedFromNoPower()) {
            if (source != null && target != null
                    && source.getPowerType() == ModPlayerPowers.ZOMBIE.get()
                    && target.getPowerType() == ModPlayerPowers.ZOMBIE.get()) {
                target.setPowerType(null);
            }
            ZombieOwnership.removeFromOwnerRoster(original);
            endConversion(original, event.getEntity());
            return;
        }
        if (source == null || target == null) {
            return;
        }
        PlayerPowerTransitions.Snapshot snapshot =
                conversion.previousPower().orElse(null);
        float deathTimeZombieEnergy = source
                .getCurTypeData(ModPlayerPowers.ZOMBIE)
                .map(ZombieData::getEnergy)
                .orElse(0.0F);
        if (PlayerPowerTransitions.restoreAfterDeath(
                target, source, snapshot)) {
            ZombiePowerEnergy.setCurrent(
                    target,
                    event.getEntity(),
                    deathTimeZombieEnergy);
            ZombieOwnership.removeFromOwnerRoster(original);
            endConversion(original, event.getEntity());
        }
    }

    // 1.16 copied ZombieUtilCap's owner through every clone, death
    // included, so a player restored by death still cannot hurt the
    // vampire who converted them (onConvertedAttack).
    private static void endConversion(
            Player original, Player respawned) {
        var conversion = ZombieOrdersAddon.conversion(original);
        UUID owner = conversion.ownerUuid().orElse(null);
        conversion.clear();
        var kept = ZombieOrdersAddon.conversion(respawned);
        kept.clear();
        if (owner != null) {
            kept.setOwnerUuid(owner);
        }
    }

    // 1.16 ZombieHandler.onHurt: a player cannot hurt the vampire whose
    // summon converted them. The owner mark is read from the attacking
    // entity itself, as 1.16 read its ZombieUtilCap, so hits dealt by a
    // Stand, which carries no mark, still land.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onConvertedAttack(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player target)
                || !(event.getSource().getEntity()
                        instanceof LivingEntity attacker)
                || !(StandUtil.getStandUser(attacker)
                        instanceof Player user)
                || user == target
                || !attacker.hasData(
                        ZombieOrdersAddon.CONVERSION_STATE)) {
            return;
        }
        if (ZombieOrdersAddon.conversion(attacker)
                .ownerUuid()
                .filter(target.getUUID()::equals)
                .isPresent()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (!(event.getEntity()
                        instanceof HungryZombieEntity zombie)
                || event.getSource().getEntity() == null) {
            return;
        }
        wakeNearbyOwned(zombie);
        if (!(event.getSource().getEntity()
                        instanceof Player)
                && !(event.getSource().getDirectEntity()
                        instanceof AbstractArrow)) {
            event.setAmount(
                    (event.getAmount() + 1.0F) / 2.0F);
        }
    }

    @SubscribeEvent
    public static void onInteract(
            PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget()
                        instanceof HungryZombieEntity zombie)
                || zombie.getOwner() != event.getEntity()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        ZombieOwnership.trackHungryZombie(
                zombie, player);
        ItemStack held =
                player.getItemInHand(event.getHand());
        if (held.getItem() instanceof DiggerItem
                || held.getItem() instanceof SwordItem) {
            swapEquipment(
                    player,
                    zombie,
                    event.getHand(),
                    EquipmentSlot.MAINHAND);
            return;
        }
        if (held.getItem() instanceof ArmorItem armor) {
            swapEquipment(
                    player,
                    zombie,
                    event.getHand(),
                    armor.getEquipmentSlot());
            return;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            dropEquipment(zombie);
            return;
        }

        boolean wait = !ZombieOrdersAddon.state(zombie)
                .orderedToWait();
        ZombieOrdersAddon.state(zombie)
                .setOrderedToWait(zombie, wait);
    }

    private static void swapEquipment(
            Player player,
            HungryZombieEntity zombie,
            net.minecraft.world.InteractionHand hand,
            EquipmentSlot slot) {
        ItemStack held =
                player.getItemInHand(hand);
        ItemStack old = zombie.getItemBySlot(slot).copy();
        zombie.setItemSlot(slot, held.copy());
        zombie.setDropChance(slot, 2.0F);
        if (!old.isEmpty()
                || !player.getAbilities().instabuild) {
            player.setItemInHand(hand, old);
        }
    }

    private static void dropEquipment(
            HungryZombieEntity zombie) {
        for (EquipmentSlot slot : DROP_SLOTS) {
            ItemStack stack =
                    zombie.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                zombie.spawnAtLocation(stack.copy());
                zombie.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void wakeNearbyOwned(
            HungryZombieEntity source) {
        LivingEntity owner = ZombieOwnership
                .resolveOwner(source)
                .orElse(null);
        if (owner == null) {
            return;
        }
        double range = source.getAttributeValue(
                Attributes.FOLLOW_RANGE);
        ZombieOwnership.ownedInLevel(owner).stream()
                .filter(HungryZombieEntity.class::isInstance)
                .map(HungryZombieEntity.class::cast)
                .filter(zombie ->
                        zombie.distanceToSqr(source)
                                <= range * range)
                .forEach(zombie ->
                        ZombieOrdersAddon.state(zombie)
                                .setOrderedToWait(
                                        zombie, false));
    }

    private static void releaseOwnedWithinRange(
            LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        int range =
                16 * level.getDifficulty().getId() - 8;
        if (range <= 0) {
            return;
        }
        ZombieOwnership.ownedInLevel(owner).stream()
                .filter(HungryZombieEntity.class::isInstance)
                .map(HungryZombieEntity.class::cast)
                .filter(zombie ->
                        zombie.distanceToSqr(owner)
                                <= (double) range * range)
                .forEach(zombie ->
                        ZombieOrdersAddon.state(zombie)
                                .setOrderedToWait(
                                        zombie, false));
    }
}
