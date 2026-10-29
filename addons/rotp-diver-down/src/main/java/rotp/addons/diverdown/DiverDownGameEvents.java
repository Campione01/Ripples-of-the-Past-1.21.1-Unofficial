package rotp.addons.diverdown;

import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.diverdown.entity.BoneShardEntity;
import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.ability.InsideProtectionAbility;
import rotp.addons.diverdown.state.DiverDownAuthority;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = DiverDownAddon.MOD_ID)
public final class DiverDownGameEvents {
    private DiverDownGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                DiverDownAddon.DIVER_DOWN_ENTITY.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAcceptedDamage(
            LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getSource().is(
                DiverDownDamageTypes.PROTECTION_FEEDBACK)) {
            return;
        }
        DiverDownStandEntity protecting =
                protectingStand(level, target);
        if (protecting == null) {
            return;
        }
        LivingEntity owner = protecting.getUser();
        if (!DiverDownAuthority.validController(
                owner,
                protecting,
                DiverDownAddon.INSIDE_PROTECTION_NAME)) {
            protecting.clearEmbeddedTarget();
            return;
        }
        // Pre precedes absorption: leave its full reduction for vanilla to consume.
        float half = Math.max(
                event.getNewDamage() - target.getAbsorptionAmount(), 0.0F) * 0.5F;
        if (half <= 0.0F) {
            return;
        }
        event.setNewDamage(event.getNewDamage() - half);
        DamageSource feedback = DamageUtil.make(
                level,
                DiverDownDamageTypes.PROTECTION_FEEDBACK,
                protecting,
                owner);
        DamageUtil.hurtThroughInvulTicks(owner, feedback, half);
    }

    public static float applyAcceptedBoneDamage(
            LivingEntity target, DamageContainer container, float amount) {
        if (!(target.level() instanceof ServerLevel level) || amount <= 0.0F) {
            return amount;
        }
        if (target.hasEffect(
                DiverDownAddon.BONE_SHARD_BOMB_EFFECT)
                && DiverDownAuthority.validateEffect(
                        target,
                        DiverDownAuthority.EffectKind
                                .BONE_SHARD_BOMB)) {
            LivingEntity owner = DiverDownAuthority.ownerOf(
                    target,
                    DiverDownAuthority.EffectKind
                            .BONE_SHARD_BOMB);
            DiverDownStandEntity stand =
                    DiverDownAuthority.standOf(
                            target,
                            DiverDownAuthority.EffectKind
                                    .BONE_SHARD_BOMB);
            if (owner != null && stand != null) {
                amount *= 2.0F;
                container.setNewDamage(amount);
                spawnBoneShards(
                        level, target, owner, stand, container.getSource().getDirectEntity());
                // 1.16 GameplayEvents.onEntityHurt :83: no particles, only the icon
                target.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN,
                        200,
                        1,
                        false,
                        false,
                        true));
            }
            DiverDownAuthority.clearEffectAuthority(
                    target, DiverDownAuthority.EffectKind.BONE_SHARD_BOMB);
        }

        return amount;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()
                || !living.hasEffect(
                        DiverDownAddon.SPRING_LEGS_EFFECT)) {
            return;
        }
        if (!DiverDownAuthority.validateEffect(
                living,
                DiverDownAuthority.EffectKind.SPRING_LEGS)) {
            return;
        }
        // 1.16 GameplayEvents.onEntityTick: only on the ground, the fall distance grows by twice the amplifier and the
        // entity is thrown straight up; SpringLegsEffect had no duration tick, so it never hurt by itself
        if (!living.onGround() || !living.isAlive()) {
            return;
        }
        MobEffectInstance effect = living.getEffect(
                DiverDownAddon.SPRING_LEGS_EFFECT);
        int amplifier = effect != null ? effect.getAmplifier() : 0;
        living.fallDistance += 2.0F * amplifier;
        living.setDeltaMovement(0.0D, (amplifier + 1) / 2.0D, 0.0D);
        living.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()
                && DiverDownAuthority.ownerHasAuthority(player)
                && DiverDownAddon.activeStand(player) == null) {
            DiverDownAuthority.clearOwnedBy(
                    player.getServer(), player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        DiverDownAuthority.clearEffectAuthority(entity);
        clearOwnedBy(entity);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        clearOwnedBy(original);
        clearOwnedBy(player);
        DiverDownAuthority.clearEffectAuthority(original);
        DiverDownAuthority.clearEffectAuthority(player);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(
            PlayerEvent.PlayerRespawnEvent event) {
        clearPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity living
                && (living.hasEffect(
                                DiverDownAddon
                                        .SPRING_LEGS_EFFECT)
                        || living.hasEffect(
                                DiverDownAddon
                                        .BONE_SHARD_BOMB_EFFECT))) {
            DiverDownAuthority.clearEffectAuthority(living);
        }
        if (entity instanceof DiverDownStandEntity stand
                && event.getLevel() instanceof ServerLevel level) {
            DiverDownAuthority.clearForStand(
                    level, stand.getUUID());
        }
    }

    private static DiverDownStandEntity protectingStand(
            ServerLevel level, LivingEntity target) {
        for (DiverDownStandEntity stand :
                level.getEntitiesOfClass(
                        DiverDownStandEntity.class,
                        target.getBoundingBox().inflate(0.5D))) {
            EntityActionInstance action =
                    stand.getCurStandAction();
            if (stand.getEmbeddedTarget() == target
                    && action != null
                    && action.ability instanceof InsideProtectionAbility) {
                return stand;
            }
        }
        return null;
    }

    // 1.16 GameplayEvents.onEntityHurt :225-232: 4 to 15 shards over a half circle of yaw ((i + 1) / count / 2 x 360),
    // up to 39 degrees above the bombed entity's view, at 0.45 to 0.65 with an inaccuracy of 1, carrying the motion of
    // what hit it (shootFromRotation), from where the shard's constructor puts it (the entity's eyes)
    private static void spawnBoneShards(
            ServerLevel level,
            LivingEntity origin,
            LivingEntity controller,
            DiverDownStandEntity stand,
            Entity directAttacker) {
        RandomSource random = origin.getRandom();
        int count = 4 + random.nextInt(12);
        for (int index = 0; index < count; index++) {
            float yaw = (index + 1) / (float) count / 2.0F * 360.0F;
            float pitch = origin.getViewXRot(2.0F) - random.nextInt(40);
            float velocity = 0.45F + random.nextFloat() * 0.2F;
            BoneShardEntity shard = new BoneShardEntity(
                    origin, controller, stand, level);
            if (directAttacker != null) {
                shard.shootFromRotation(directAttacker, pitch, yaw, 0.0F, velocity, 1.0F);
            }
            else {
                float rad = Mth.DEG_TO_RAD;
                shard.shoot(
                        -Mth.sin(yaw * rad) * Mth.cos(pitch * rad),
                        -Mth.sin(pitch * rad),
                        Mth.cos(yaw * rad) * Mth.cos(pitch * rad),
                        velocity,
                        1.0F);
            }
            level.addFreshEntity(shard);
        }
    }

    private static void clearPlayer(Player player) {
        if (player == null || player.level().isClientSide()) {
            return;
        }
        DiverDownAuthority.clearEffectAuthority(player);
        clearOwnedBy(player);
    }

    // Only an owner that bound an effect or a shard (marked by bindEffect and BoneShardEntity.bind) has anything to clean
    // up: the clean-up walks a copy of every entity of every level, which every mob death would pay for otherwise.
    private static void clearOwnedBy(LivingEntity owner) {
        MinecraftServer server = owner.getServer();
        if (server != null && DiverDownAuthority.ownerHasAuthority(owner)) {
            DiverDownAuthority.clearOwnedBy(server, owner.getUUID());
        }
    }
}
