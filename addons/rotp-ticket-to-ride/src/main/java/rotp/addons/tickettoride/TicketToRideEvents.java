package rotp.addons.tickettoride;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rotp.core.init.ModParticles;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import rotp.addons.tickettoride.item.TearsBladeItem;
import rotp.addons.tickettoride.state.LoveTrainService;
import rotp.addons.tickettoride.state.TearsHazardService;
import rotp.addons.tickettoride.state.TearsWoundService;
import rotp.addons.tickettoride.state.TicketToRideBehaviorService;
import rotp.addons.tickettoride.state.TraceEnchantService;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = TicketToRideAddon.MOD_ID)
public final class TicketToRideEvents {
    public static final float WOUND_RECOIL_CHANCE = 0.15F;
    public static final float WOUND_RECOIL_MULTIPLIER = 0.5F;
    // 1.16 LoveTrainDamageSource "love_train_reflect"
    public static final ResourceKey<DamageType> LOVE_TRAIN_REFLECT =
            ResourceKey.create(
                    Registries.DAMAGE_TYPE,
                    TicketToRideAddon.id("love_train_reflect"));
    public static final double REFLECT_SEARCH_RADIUS = 32.0D;
    public static final float REFLECT_BASE_FACTOR = 0.15F;
    // 1.16 MobBehaviorHandler.NEGATIVE_EFFECTS
    public static final List<MobEffectInstance> TEARS_MOB_EFFECTS =
            List.of(
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SLOWDOWN, 200, 1),
                    new MobEffectInstance(MobEffects.WEAKNESS, 200, 0),
                    new MobEffectInstance(MobEffects.POISON, 100, 0),
                    new MobEffectInstance(MobEffects.CONFUSION, 300, 0),
                    new MobEffectInstance(MobEffects.BLINDNESS, 200, 0),
                    new MobEffectInstance(MobEffects.WITHER, 200, 0));

    private TicketToRideEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                TicketToRideAddon.TICKET_TO_RIDE_ENTITY.get(),
                StandEntity.createAttributes().build());
        event.put(
                TicketToRideAddon.DEAD_PHOTO_MEMENTO.get(),
                DeadPhotoMementoEntity
                        .createAttributes()
                        .build());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living
                && !living.level().isClientSide()) {
            if (living.hasData(
                    TicketToRideAddon.TEARS_WOUND_STATE)) {
                TearsWoundService.tick(living);
                TearsHazardService.tick(living);
            }
            if (living.hasData(
                    TicketToRideAddon.LOVE_TRAIN_STATE)
                    || living.hasEffect(
                            TicketToRideAddon.LOVE_TRAIN)) {
                LoveTrainService.tick(living);
            }
            dropRegeneration(living);
        }
        if (entity instanceof Mob mob
                && !mob.level().isClientSide()) {
            TicketToRideBehaviorService.tickMob(mob);
        }
    }

    // 1.16 MobBehaviorHandler.onEntitySpawn
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof LivingEntity living) {
            TearsWoundService.onJoin(living);
        }
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof Mob mob) {
            TicketToRideBehaviorService.startMisfortuneLifetime(mob);
            TicketToRideBehaviorService.addUnluckyHorseGoal(mob);
            if (mob.hasEffect(TicketToRideAddon.TEARS)) {
                TicketToRideBehaviorService.modifyMisfortuneAI(mob);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        TearsWoundService.onLevelUnload(event.getLevel());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        TearsBladeItem.enforceOwnedItem(event.getEntity());
        Player player = event.getEntity();
        // 1.16 StandAggroHandler.onPlayerTick: the user's own attacks can hurt Stands
        if (!player.level().isClientSide()
                && TicketToRideAddon.isTicketToRideUser(player)) {
            player.addEffect(new MobEffectInstance(
                    ModStatusEffects.INTEGRATED_STAND,
                    10,
                    0,
                    false,
                    false,
                    true));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        // 1.16 MobBehaviorHandler.onLivingAttack ran before Love Train's
        // lowest-priority cancel, whatever the amount.
        if (event.getSource().getEntity() instanceof Mob tearsMob
                && tearsMob.hasEffect(TicketToRideAddon.TEARS)
                && TearsWoundService.isMarked(target)) {
            applyTearsMobMisfortune(tearsMob, target);
        }
        // 1.16 StandAggroHandler.onLivingAttack: a mob hit by any player
        // turns to that player (onMobTargetChange may drop it again).
        if (event.getSource().getEntity() instanceof Player player
                && target instanceof Mob mob) {
            mob.setTarget(player);
        }
        // 1.16 TearsEffectEventHandler.onLivingAttack: a marked attacker (the
        // source's entity itself) takes half the hit back now and then, also
        // when Love Train cancels the hit.
        if (event.getSource().getEntity() instanceof LivingEntity marked
                && TearsWoundService.isMarked(marked)
                && marked.getRandom().nextFloat() < WOUND_RECOIL_CHANCE) {
            marked.hurt(marked.damageSources().generic(),
                    event.getAmount() * WOUND_RECOIL_MULTIPLIER);
            marked.playSound(
                    SoundEvents.PLAYER_HURT,
                    1.0F,
                    0.8F + marked.getRandom().nextFloat() * 0.4F);
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        LivingEntity attacker =
                resolveLivingAttacker(event.getSource());

        LivingEntity protector =
                LoveTrainService.protector(target);
        if (protector != null) {
            event.setCanceled(true);
            // 1.16 onLivingAttack: the hit lands on a random bystander
            Entity trueSource = event.getSource().getEntity();
            if (trueSource != null
                    && target.level() instanceof ServerLevel level) {
                performRandomCounterAttack(level, target, trueSource);
            }
            return;
        }

        if (attacker != null
                && attacker.level() instanceof ServerLevel level) {
            ItemStack weapon = attacker.getMainHandItem();
            if (TraceEnchantService.isTraced(weapon)) {
                TraceEnchantService.recordUse(level, weapon);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onFall(LivingFallEvent event) {
        TearsHazardService.onFall(
                event.getEntity(), event.getDistance());
    }

    // 1.16 TearsEffects.applyEffectTick (TEARS and the Love Train passenger
    // effect): a player holding it loses Regeneration every tick; other
    // healing still works.
    private static void dropRegeneration(LivingEntity living) {
        if (living instanceof Player
                && (living.hasEffect(TicketToRideAddon.TEARS)
                        || living.hasEffect(
                                TicketToRideAddon.SHARED_LOVE_TRAIN))
                && living.hasEffect(MobEffects.REGENERATION)) {
            living.removeEffect(MobEffects.REGENERATION);
        }
    }

    // 1.16 LoveTrainEventHandler.onKnockback (LOWEST)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKnockback(LivingKnockBackEvent event) {
        if (LoveTrainService.isProtected(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // 1.16 LoveTrainEventHandler.onExplosion (LOWEST): no blast push
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedEntities().removeIf(entity ->
                entity instanceof LivingEntity living
                        && LoveTrainService.isProtected(living));
    }

    // 1.16 StandAggroHandler.onLivingSetAttackTarget ran after the target
    // was set and set it back to null, not to the one before. That
    // setAttackTarget(null) never touched a brain's ATTACK_TARGET memory, so
    // the brain targets of piglins, hoglins and the like stay as they are.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMobTargetChange(
            LivingChangeTargetEvent event) {
        if (event.getTargetType()
                        == LivingChangeTargetEvent.LivingTargetType.MOB_TARGET
                && event.getEntity() instanceof Mob mob
                && event.getNewAboutToBeSetTarget()
                        instanceof Player player
                && TicketToRideBehaviorService
                        .dropsTicketUserTarget(mob, player)) {
            event.setNewAboutToBeSetTarget(null);
            TicketToRideBehaviorService.cancelGroupAggro(mob, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult hit)
                || !(hit.getEntity()
                        instanceof LivingEntity target)
                // 1.16 removed projectiles only at Love Train holders
                || !LoveTrainService.isActive(target)) {
            return;
        }
        projectile.discard();
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDoorUse(
            PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel)
                || !TearsWoundService.isMarked(player)
                || player.getRandom().nextFloat() >= 0.15F) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(
                event.getPos());
        if (!(state.getBlock() instanceof DoorBlock)) {
            return;
        }
        // 1.16 onDoorInteract: bleeding and the iron trapdoor's sound
        TearsHazardService.applyBleeding(player);
        player.playSound(
                SoundEvents.IRON_TRAPDOOR_CLOSE,
                1.0F,
                0.8F + player.getRandom().nextFloat() * 0.4F);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLavaBucketUse(
            PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)
                || !event.getItemStack().is(
                        Items.LAVA_BUCKET)
                || !TearsWoundService.isMarked(player)
                || player.getRandom().nextFloat() >= 0.10F) {
            return;
        }
        // 1.16 onLavaBucketUse: the lava pours out at the feet, the bucket
        // empties into the inventory
        level.setBlockAndUpdate(
                player.blockPosition(),
                Blocks.LAVA.defaultBlockState());
        player.hurt(player.damageSources().lava(), 2.0F);
        player.playSound(SoundEvents.LAVA_POP, 1.0F, 1.0F);
        event.getItemStack().shrink(1);
        player.addItem(new ItemStack(Items.BUCKET));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (!(entity instanceof StandEntity stand)
                || stand.getUser() == null
                || !stand.getUser().isAlive()) {
            TearsWoundService.clear(entity);
        }
        LoveTrainService.deactivate(entity);
        TearsBladeItem.purgeAll(entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        TearsWoundService.clear(original);
        TearsWoundService.clear(player);
        LoveTrainService.deactivate(original);
        LoveTrainService.deactivate(player);
        TearsBladeItem.purgeAll(original);
        TearsBladeItem.purgeAll(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        TearsBladeItem.purgeAll(player);
        if (player.hasData(
                TicketToRideAddon.TEARS_WOUND_STATE)) {
            TearsWoundService.tick(player);
            TicketToRideAddon.syncWoundState(player);
        }
        LoveTrainService.tick(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        TearsBladeItem.purgeAll(player);
        if (player.hasData(
                TicketToRideAddon.TEARS_WOUND_STATE)) {
            TearsWoundService.tick(player);
            TicketToRideAddon.syncWoundState(player);
        }
        LoveTrainService.tick(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        TearsBladeItem.purgeAll(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget()
                        instanceof LivingEntity target
                && target.hasData(
                        TicketToRideAddon.TEARS_WOUND_STATE)) {
            TicketToRideAddon.syncWoundState(player, target);
        }
    }

    // 1.16 MobBehaviorHandler: three of NEGATIVE_EFFECTS, shuffled.
    private static void applyTearsMobMisfortune(
            Mob attacker, LivingEntity target) {
        List<MobEffectInstance> effects =
                new ArrayList<>(TEARS_MOB_EFFECTS);
        Util.shuffle(effects, attacker.getRandom());
        for (int i = 0; i < 3; i++) {
            target.addEffect(new MobEffectInstance(effects.get(i)));
        }
    }

    // 1.16 performRandomCounterAttack
    static void performRandomCounterAttack(
            ServerLevel level,
            LivingEntity protectedEntity,
            Entity attacker) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(
                LivingEntity.class,
                protectedEntity.getBoundingBox()
                        .inflate(REFLECT_SEARCH_RADIUS),
                e -> e.isAlive()
                        && e != protectedEntity
                        && !e.hasEffect(TicketToRideAddon.LOVE_TRAIN)
                        && !e.hasEffect(
                                TicketToRideAddon.SHARED_LOVE_TRAIN));
        if (candidates.isEmpty()) {
            return;
        }
        LivingEntity victim = candidates.get(
                level.getRandom().nextInt(candidates.size()));
        float damage = protectedEntity.getMaxHealth()
                * REFLECT_BASE_FACTOR
                * (0.8F + level.getRandom().nextFloat() * 1.2F);
        if (!victim.hurt(
                DamageUtil.make(level, LOVE_TRAIN_REFLECT), damage)) {
            return;
        }
        spawnReflectParticles(
                level, attacker.position(), victim.position());
        if (victim instanceof Player player) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.rotp_ttr.love_train_reflect",
                            String.format(
                                    Locale.ROOT, "%.1f", damage)),
                    true);
        }
    }

    private static void spawnReflectParticles(
            ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 direction = to.subtract(from).normalize();
        double distance = from.distanceTo(to);
        int particles = Math.min(15, (int) (distance * 2.0D));
        for (int i = 0; i < particles; i++) {
            double progress = (double) i / particles;
            Vec3 pos = from.add(direction.scale(progress * distance));
            level.sendParticles(
                    ModParticles.HAMON_SPARK.get(),
                    pos.x, pos.y + 0.5D, pos.z,
                    1, 0.0D, 0.05D, 0.0D, 0.15F);
        }
    }

    private static LivingEntity resolveLivingAttacker(
            DamageSource source) {
        LivingEntity indirect = resolveLiving(
                source.getEntity());
        return indirect != null
                ? indirect
                : resolveLiving(source.getDirectEntity());
    }

    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand
                && stand.getUser() != null) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
