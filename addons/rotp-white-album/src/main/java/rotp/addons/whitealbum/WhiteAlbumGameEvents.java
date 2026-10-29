package rotp.addons.whitealbum;

import rotp.core.JojoModConfig;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.customobjects.entity_projectile.OwnerBoundProjectileEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.whitealbum.ability.WhiteAlbumFreezeAbility;
import rotp.addons.whitealbum.entity.WhiteAlbumControllerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = WhiteAlbumAddon.MOD_ID)
public final class WhiteAlbumGameEvents {
    private static final ResourceLocation ARMOR_MODIFIER =
            WhiteAlbumAddon.id("white_album_armor");

    private WhiteAlbumGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                WhiteAlbumAddon.WHITE_ALBUM_CONTROLLER.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity user = event.getEntity();
        // 1.16 blockDamage ran on every accepted hit, a 0-damage one too, so
        // it still froze the attacker; only a player's 0 hit never got here.
        if (user.level().isClientSide()) {
            return;
        }

        WhiteAlbumControllerEntity controller =
                WhiteAlbumAddon.controller(user);
        if (controller == null) {
            return;
        }

        // Donor blockDamage tested the direct entity, so an arrow's shooter
        // is not an attacker; a Stand resolves to its user for the living
        // reduction and break test only.
        Entity direct = event.getSource().getDirectEntity();
        LivingEntity resolved = resolveLiving(direct);
        LivingEntity attacker = resolved != user ? resolved : null;
        // The donor froze the direct entity, the Stand itself for a Stand's
        // hit; Freeze refuses cold-immune Stands, as it did in 1.16.
        LivingEntity frozen = direct instanceof StandEntity stand
                ? stand
                : attacker;
        // The same test LivingEntity.hurt makes before it subtracts lastHurt.
        boolean hurtCooldown = user.invulnerableTime > 10
                && !event.getSource().is(
                        DamageTypeTags.BYPASSES_COOLDOWN);

        // 1.16 canceled only the entity-less sun ultraviolet while the Stand
        // was out; its cold cancel sat under a direct-entity test that the
        // entity-less COLD never passed, so cold hits land. Vanilla freezing
        // (powder snow, newer than 1.16) stays canceled as a port choice.
        if (direct == null
                && (event.getSource().is(
                                ModDamageTypes.ULTRAVIOLET)
                        || event.getSource().is(
                                        DamageTypeTags.IS_FREEZING)
                                && !event.getSource().is(
                                        ModDamageTypes.VAMPIRE_FREEZE))) {
            event.setCanceled(true);
            return;
        }

        StandPower power = StandPower.get(user);
        if (!controller.isStandBlocking()
                || direct == null
                || power == null) {
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
                hitReactions(controller, frozen, direct, container.getNewDamage() > 0.0F);
                return reduction;
            });
            return;
        }

        float incoming = event.getAmount();
        boolean livingAttacker = attacker != null;
        boolean standAttack = event.getSource().is(
                ModDamageTypes.STAND_ATTACK);
        float multiplier = standAttack
                ? JojoModConfig
                        .getCommonConfigInstance(false)
                        .standDamageMultiplier
                        .get()
                        .floatValue()
                : 1.0F;
        boolean projectile = event.getSource().is(
                DamageTypeTags.IS_PROJECTILE);
        boolean irreflectable = direct.getType().is(
                WhiteAlbumTags.IRREFLECTABLE);
        // Donor blockDamage charged and judged its LivingHurtEvent amount:
        // the full hit, or amount - lastHurt inside the cooldown.
        float[] hurtAmount = {incoming};
        float removed;
        if (hurtCooldown) {
            // 1.16 compared the raw hit with a raw lastHurt and reduced only
            // the excess, so leave it raw and cut the excess where vanilla
            // subtracts lastHurt, still before armor.
            removed = 0.0F;
            event.addReductionModifier(DamageContainer.Reduction.INVULNERABILITY, (container, lastHurt) -> {
                float amount = container.getNewDamage();
                hurtAmount[0] = Math.max(amount - lastHurt, 0.0F);
                return amount - guardReduce(
                        hurtAmount[0], livingAttacker, standAttack, multiplier, projectile, irreflectable);
            });
        }
        else {
            float amount = guardReduce(
                    incoming, livingAttacker, standAttack, multiplier, projectile, irreflectable);
            removed = incoming - amount;
            event.setAmount(amount);
        }
        // Armor reduction callbacks run only after shield and hurt-cooldown rejection.
        event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> {
            hitReactions(controller, frozen, direct, container.getNewDamage() > 0.0F);
            if (!hurtCooldown && container.getBlockedDamage() <= 0.0F) {
                // Vanilla stored the reduced fresh hit as lastHurt; 1.16
                // stored the raw one, which the next hit is compared with.
                user.lastHurt += removed;
            }
            float hurt = hurtAmount[0];
            boolean paid = power.consumeStamina(
                    hurt * WhiteAlbumSpec.GUARD_STAMINA_PER_DAMAGE);
            if (frozen != null) {
                frozen.addEffect(new MobEffectInstance(ModStatusEffects.FREEZE, 30));
            }
            // No sound per hit: 1.16 played ice_shield only as the guard's start shout.
            if (guardBreaks(hurt, livingAttacker, standAttack, multiplier) || !paid
                    || !power.isStaminaInfinite() && power.getStamina() <= 0.0F) {
                breakGuard(controller);
            }
            return reduction;
        });
    }

    // Donor blockDamage froze the direct entity for 20 ticks on every hit
    // the hurt cooldown let through. The 1.16 core stopped a held Freeze
    // (cancelHeldOnGettingAttacked) only after such a hit still dealt
    // damage, and only when it had a direct entity.
    private static void hitReactions(
            WhiteAlbumControllerEntity controller,
            LivingEntity frozen,
            Entity direct,
            boolean dealsDamage) {
        if (frozen != null) {
            frozen.addEffect(
                    new MobEffectInstance(
                            ModStatusEffects.FREEZE,
                            20));
        }
        EntityActionInstance action =
                controller.getCurStandAction();
        if (direct != null
                && dealsDamage
                && action != null
                && action.ability
                        instanceof WhiteAlbumFreezeAbility
                && action.getPhase() == ActionPhase.PERFORM) {
            action.startRecovery();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(
            PlayerTickEvent.Post event) {
        Player user = event.getEntity();
        if (user.level().isClientSide()) {
            return;
        }

        WhiteAlbumControllerEntity controller =
                WhiteAlbumAddon.controller(user);
        // 1.16 GameplayHandler.highPlayerTick: suited with an empty main
        // hand, the user's own attacks hurt Stands; unsuited, it is removed.
        if (WhiteAlbumAddon.isWhiteAlbumUser(user)) {
            if (controller == null) {
                user.removeEffect(ModStatusEffects.INTEGRATED_STAND);
            }
            else if (user.getMainHandItem().isEmpty()) {
                user.addEffect(new MobEffectInstance(
                        ModStatusEffects.INTEGRATED_STAND,
                        20, 0, false, false, false));
            }
        }
        AttributeInstance armor =
                user.getAttribute(Attributes.ARMOR);
        if (controller == null) {
            if (armor != null) {
                armor.removeModifier(ARMOR_MODIFIER);
            }
            return;
        }

        StandPower power = StandPower.get(user);
        if (power == null) {
            return;
        }

        user.removeEffect(ModStatusEffects.FREEZE);
        user.setTicksFrozen(0);
        refreshArmor(user, armor);
        applyIceSpeed(user);
        applyAura(user, controller, false);

        if (controller.pollGuardStarted()) {
            // 1.16 WA_BLOCK .shout(): the only ice_shield cue, a user voice
            // line that stays silent while sneaking.
            Ability guard = power.getAbility(WhiteAlbumAddon.GUARD_NAME);
            if (guard != null) {
                guard.sayShout(user, WhiteAlbumAddon.ICE_SHIELD);
            }
        }

        if (controller.isStandBlocking()) {
            EntityActionInstance action =
                    controller.getCurStandAction();
            if (action != null) {
                action.userWalkSpeed =
                        WhiteAlbumSpec
                                .GUARD_USER_WALK_SPEED;
            }
            // No upkeep: 1.16's guard was a plain held StandAction, whose
            // staminaCostTick(6) only set the cost drawn on its icon; the
            // core charged per-tick stamina for Stand entity actions alone.
            applyAura(user, controller, true);
        }

        if (controller.isGentlyWeepsActive()
                && !power.consumeStamina(
                        WhiteAlbumSpec
                                .GENTLY_WEEPS_STAMINA_PER_TICK,
                        true)) {
            controller.setGentlyWeepsActive(false);
        }

        if (controller.isFreezeFloorActive()) {
            if (power.consumeStamina(
                    WhiteAlbumSpec
                            .FREEZE_FLOOR_STAMINA_PER_TICK,
                    true)) {
                WhiteAlbumIce.freezeFloor(
                        user, user.level());
            }
            else {
                controller.setFreezeFloorActive(false);
            }
        }

        if (!power.isStaminaInfinite()) {
            // Donor highPlayerTick cleared both toggles at 2 stamina or less;
            // its guard branch cleared them below 2.5 and dropped the guard
            // only once stamina reached 0.
            float stamina = power.getStamina();
            boolean guarding = controller.isStandBlocking();
            if (stamina <= 2.0F || guarding && stamina < 2.5F) {
                controller.setGentlyWeepsActive(false);
                controller.setFreezeFloorActive(false);
            }
            if (guarding && stamina <= 0.0F) {
                breakGuard(controller);
            }
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(
            ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide()
                || projectile
                        instanceof OwnerBoundProjectileEntity
                || !(event.getRayTraceResult()
                        instanceof EntityHitResult hit)
                || !(hit.getEntity()
                        instanceof LivingEntity user)) {
            return;
        }

        WhiteAlbumControllerEntity controller =
                WhiteAlbumAddon.controller(user);
        if (controller == null
                || !controller.isGentlyWeepsActive()) {
            return;
        }

        StandPower power = StandPower.get(user);
        if (power == null) {
            controller.setGentlyWeepsActive(false);
            return;
        }

        Vec3 incoming = projectile.getDeltaMovement();
        Vec3 reflected = incoming.lengthSqr() > 1.0E-6D
                ? incoming.scale(-1.0D)
                : user.getLookAngle().normalize().scale(
                        WhiteAlbumSpec.MIN_DEFLECT_SPEED);
        Vec3 direction = reflected.normalize();
        double clearance = Math.max(
                0.5D,
                user.getBbWidth() * 0.5D
                        + projectile.getBbWidth() * 0.5D
                        + 0.25D);
        Vec3 position = user.getEyePosition()
                .add(direction.scale(clearance));

        projectile.setOwner(user);
        projectile.setPos(position);
        projectile.setDeltaMovement(reflected);
        projectile.hasImpulse = true;
        projectile.hurtMarked = true;
        if (projectile
                instanceof ModdedProjectileEntity modded) {
            modded.setIsDeflected(reflected, position);
        }
        event.setCanceled(true);

        boolean paid = power.consumeStamina(
                WhiteAlbumSpec
                        .GENTLY_WEEPS_DEFLECT_STAMINA);
        if (!paid
                || !power.isStaminaInfinite()
                        && power.getStamina() <= 0.0F) {
            controller.setGentlyWeepsActive(false);
        }

        user.playSound(
                WhiteAlbumAddon.WA_DEFLECT.get(),
                1.0F,
                1.0F);
        user.playSound(
                WhiteAlbumAddon.WA_GW.get(),
                0.8F,
                1.0F);
    }

    private static void refreshArmor(
            Player user,
            AttributeInstance armor) {
        if (armor == null) {
            return;
        }
        int equippedArmor = 0;
        for (ItemStack stack : user.getArmorSlots()) {
            if (stack.getItem() instanceof ArmorItem item) {
                equippedArmor += item.getDefense();
            }
        }
        double bonus = Math.min(
                WhiteAlbumSpec.MAX_ARMOR_BONUS,
                Math.max(
                        0.0D,
                        WhiteAlbumSpec.ARMOR_TARGET
                                - equippedArmor));
        armor.addOrUpdateTransientModifier(
                new AttributeModifier(
                        ARMOR_MODIFIER,
                        bonus,
                        AttributeModifier.Operation.ADD_VALUE));
    }

    private static void applyIceSpeed(Player user) {
        if (user.level()
                        .getBlockState(
                                user.blockPosition().below())
                        .is(WhiteAlbumTags.ICE_SPEED)
                || user.level()
                        .getBlockState(user.blockPosition())
                        .is(WhiteAlbumTags.ICE_LAYER_SPEED)) {
            user.addEffect(
                    new MobEffectInstance(
                            MobEffects.MOVEMENT_SPEED,
                            20,
                            2));
        }
    }

    private static void applyAura(
            Player user,
            WhiteAlbumControllerEntity controller,
            boolean guarding) {
        for (LivingEntity target :
                user.level().getEntitiesOfClass(
                        LivingEntity.class,
                        user.getBoundingBox().inflate(
                                WhiteAlbumSpec
                                        .AURA_SCAN_RADIUS),
                        target -> target.isAlive()
                                && !target.isRemoved())) {
            float distance = target.distanceTo(user);
            if (distance
                            > WhiteAlbumSpec
                                    .AURA_EFFECT_RADIUS
                    || target == user) {
                continue;
            }
            if (!guarding
                    && (target.isOnFire()
                            || WhiteAlbumAddon.controller(
                                    target) != null)) {
                continue;
            }
            int amplifier = guarding
                    ? Math.round(
                            4.0F
                                    - 2.0F
                                            * distance
                                            / 5.0F)
                    : Math.round(
                            2.0F - distance / 5.0F);
            target.addEffect(
                    new MobEffectInstance(
                            ModStatusEffects.FREEZE,
                            WhiteAlbumSpec
                                    .AURA_EFFECT_TICKS,
                            amplifier));
        }
    }

    private static void breakGuard(
            WhiteAlbumControllerEntity controller) {
        controller.breakStandBlocking(
                WhiteAlbumSpec.GUARD_BREAK_LOCK_TICKS);
    }

    // Donor blockDamage break tests in its order: over 15 after the living
    // flat reduction, then the Stand branch on what the living branch left.
    private static boolean guardBreaks(
            float amount,
            boolean livingAttacker,
            boolean standAttack,
            float multiplier) {
        if (livingAttacker) {
            amount = Math.max(
                    amount
                            - WhiteAlbumSpec
                                    .GUARD_FLAT_REDUCTION,
                    0.0F);
            if (amount
                    > WhiteAlbumSpec
                            .GUARD_BREAK_POST_REDUCTION_DAMAGE) {
                return true;
            }
        }
        return standAttack
                && amount
                        > multiplier
                                * WhiteAlbumSpec
                                        .GUARD_BREAK_POST_REDUCTION_DAMAGE;
    }

    // Donor blockDamage reductions in its order: the living flat cut, the
    // Stand cut scaled by the damage multiplier, then projectiles (reflected
    // ones to zero, irreflectable ones by the flat amount).
    private static float guardReduce(
            float amount,
            boolean livingAttacker,
            boolean standAttack,
            float multiplier,
            boolean projectile,
            boolean irreflectable) {
        if (livingAttacker) {
            amount = Math.max(
                    amount
                            - WhiteAlbumSpec
                                    .GUARD_FLAT_REDUCTION,
                    0.0F);
        }
        if (standAttack) {
            amount = Math.max(
                    amount
                            - multiplier
                                    * WhiteAlbumSpec
                                            .GUARD_FLAT_REDUCTION,
                    0.0F);
        }
        if (projectile) {
            amount = irreflectable
                    ? Math.max(
                            amount
                                    - WhiteAlbumSpec
                                            .GUARD_FLAT_REDUCTION,
                            0.0F)
                    : 0.0F;
        }
        return amount;
    }

    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }
}
