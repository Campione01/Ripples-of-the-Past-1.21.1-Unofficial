package rotp.addons.cream;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.cream.entity.CreamEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = CreamAddon.MOD_ID)
public final class CreamGameEvents {
    private CreamGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                CreamAddon.CREAM_ENTITY.get(),
                CreamEntity.createAttributes().build());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().hasEffect(CreamAddon.BLEEDING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        CreamEntity active = CreamEntity.activeFor(target);
        if (active != null
                && active.isInVoidState()
                && !event.getSource().is(
                        DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }

        // A hit during a void charge is the core held-action rule
        // (CreamVoidAbility opts in behind its config options).
        applyCreamMelee(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAbilityUse(
            RipplesAbilityKeyPressEvent event) {
        CreamEntity stand = CreamEntity.activeFor(
                event.getEntity());
        if (stand == null || !stand.isInVoidState()) {
            return;
        }
        ResourceLocation abilityId =
                event.getAbility().abilityType.registryKey;
        String path = abilityId.getPath();
        if (!CreamAddon.CREAM_VOID_DASH_NAME.equals(path)
                && !CreamAddon.CREAM_VOID_BALL_CANCEL_NAME
                        .equals(path)
                && !CreamAddon.CREAM_SEMI_VOID_CANCEL_NAME
                        .equals(path)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        boolean trueBlind =
                living.hasEffect(CreamAddon.TRUE_BLINDNESS);
        if (trueBlind) {
            living.setSprinting(false);
            living.addEffect(new MobEffectInstance(
                    MobEffects.BLINDNESS,
                    4,
                    0,
                    false,
                    false,
                    false));
            living.addEffect(new MobEffectInstance(
                    MobEffects.DARKNESS,
                    4,
                    0,
                    false,
                    false,
                    false));
        }

        if ((living.hasEffect(CreamAddon.VOIDED)
                        || living.hasEffect(
                                CreamAddon.INSIDE_CREAM))
                && !CreamEntity.isInVoidState(living)) {
            living.removeEffect(CreamAddon.VOIDED);
            living.removeEffect(CreamAddon.INSIDE_CREAM);
            living.removeEffect(CreamAddon.TRUE_BLINDNESS);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(PlayerEvent.BreakSpeed event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (isInteractionRestricted(event.getPlayer())) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(
            BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
                && isInteractionRestricted(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (isInteractionRestricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        cleanup(event.getEntity());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        cleanup(event.getOriginal());
        cleanup(event.getEntity());
    }

    private static void applyCreamMelee(
            LivingIncomingDamageEvent event) {
        Entity source = event.getSource().getEntity();
        CreamEntity stand = source instanceof CreamEntity cream
                ? cream
                : event.getSource().getDirectEntity()
                        instanceof CreamEntity cream
                                ? cream
                                : null;
        if (stand == null) {
            return;
        }
        EntityActionInstance action = stand.getCurStandAction();
        ResourceLocation id = action != null
                        && action.ability instanceof Ability ability
                ? ability.abilityType.registryKey
                : null;
        if (id == null || !CreamAddon.MOD_ID.equals(id.getNamespace())) {
            return;
        }

        LivingEntity target = event.getEntity();
        String path = id.getPath();
        switch (path) {
            case CreamAddon.CREAM_CLAW_NAME -> {
                event.setAmount(Math.max(
                        1.0F,
                        (float) stand.getAttackDamage() - 10.0F));
                applyStackingBleeding(target, 25);
                reduceKnockback(event, 0.5F);
            }
            case CreamAddon.CREAM_HEAVY_CLAW_NAME -> {
                event.setAmount(Math.max(
                        1.0F,
                        (float) stand.getAttackDamage() - 0.5F));
                applyEffect(target, CreamAddon.BLEEDING, 35, 1);
                interruptBlocking(target);
                reduceKnockback(event, 0.35F);
            }
            case CreamAddon.CREAM_DOUBLE_CLAW_FINISHER_NAME -> {
                applyEffect(target, CreamAddon.BLEEDING, 40, 2);
                interruptBlocking(target);
                reduceKnockback(event, 0.1F);
            }
            case CreamAddon.CREAM_FINISHER_PUNCH_NAME -> {
                applyEffect(
                        target,
                        MobEffects.MOVEMENT_SLOWDOWN,
                        120,
                        1);
                applyEffect(target, MobEffects.CONFUSION, 120, 0);
                interruptBlocking(target);
                reduceKnockback(event, 0.1F);
            }
            case CreamAddon.CREAM_CHOP_NAME -> {
                event.setAmount(
                        (float) stand.getAttackDamage() * 1.05F);
                applyEffect(target, CreamAddon.WEAK_NECK, 35, 0);
                applyEffect(target, CreamAddon.BLEEDING, 35, 2);
                applyEffect(
                        target,
                        MobEffects.MOVEMENT_SLOWDOWN,
                        120,
                        1);
                applyEffect(target, MobEffects.POISON, 120, 0);
                interruptBlocking(target);
                reduceKnockback(event, 0.25F);
            }
            default -> {}
        }
    }

    private static void applyStackingBleeding(
            LivingEntity target, int duration) {
        MobEffectInstance current =
                target.getEffect(CreamAddon.BLEEDING);
        int amplifier = current == null
                ? 0
                : Math.min(2, current.getAmplifier() + 1);
        target.removeEffect(CreamAddon.BLEEDING);
        applyEffect(
                target, CreamAddon.BLEEDING, duration, amplifier);
    }

    private static void applyEffect(
            LivingEntity target,
            net.minecraft.core.Holder<
                    net.minecraft.world.effect.MobEffect> effect,
            int duration,
            int amplifier) {
        target.addEffect(new MobEffectInstance(
                effect,
                duration,
                amplifier,
                false,
                false,
                true));
    }

    private static void reduceKnockback(
            LivingIncomingDamageEvent event, float multiplier) {
        if (event.getSource()
                instanceof DamageSourceModified modified) {
            modified.jojo_ripples$modifyKnockback(
                    0.0F, multiplier);
        }
    }

    private static void interruptBlocking(LivingEntity target) {
        if (target.isBlocking()) {
            target.stopUsingItem();
        }
    }

    private static boolean isInteractionRestricted(Player player) {
        return CreamEntity.isInVoidState(player)
                || player.hasEffect(CreamAddon.VOIDED)
                || player.hasEffect(CreamAddon.INSIDE_CREAM);
    }

    private static void cleanup(LivingEntity living) {
        if (living == null || living.level().isClientSide()) {
            return;
        }
        CreamEntity active = CreamEntity.activeFor(living);
        if (active != null) {
            active.endVoidMode(false);
        }
        living.removeEffect(CreamAddon.VOIDED);
        living.removeEffect(CreamAddon.INSIDE_CREAM);
        living.removeEffect(CreamAddon.TRUE_BLINDNESS);
    }
}
