package rotp.addons.yellowtemperance;

import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;

import java.util.Optional;
import java.util.UUID;

import rotp.addons.yellowtemperance.ability.YellowTemperanceBlockAbility;
import rotp.addons.yellowtemperance.disguise.DisguiseMode;
import rotp.addons.yellowtemperance.disguise.DisguiseRetaliationService;
import rotp.addons.yellowtemperance.disguise.DisguiseScaleService;
import rotp.addons.yellowtemperance.effect.ErosionService;
import rotp.addons.yellowtemperance.entity.YellowTemperanceControllerEntity;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = YellowTemperanceAddon.MOD_ID)
public final class YellowTemperanceGameEvents {
    private YellowTemperanceGameEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                YellowTemperanceAddon.CONTROLLER.get(),
                StandEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        if (living.hasEffect(YellowTemperanceAddon.EROSION)) {
            ErosionService.tick(living);
        }
        // 1.16 YellowTemperanceEntity.tick :39-48 added it while the Stand
        // was out, the guard too: the higher level won; during the unsummon
        // the controller's own tick takes it off (:44-46)
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(living);
        if (controller == null) {
            return;
        }
        if (controller.isUnsummoning()) {
            return;
        }
        if (living.tickCount % 2 != 0) {
            return;
        }
        int amplifier = 2;
        if (living instanceof Player player) {
            int food = player.getFoodData().getFoodLevel();
            amplifier = food > 15
                    ? 3
                    : food > 10
                            ? 2
                            : food > 5
                                    ? 1
                                    : food > 0 ? 0 : -1;
        }
        if (amplifier >= 0) {
            living.addEffect(
                    new MobEffectInstance(
                            YellowTemperanceAddon.PROTECTION,
                            3,
                            amplifier,
                            false,
                            false,
                            true),
                    living);
        }
        else {
            living.removeEffect(
                    YellowTemperanceAddon.PROTECTION);
        }
    }

    // 1.16 PowerBaseImpl.tickHeldAction ran the guard's holdTick from
    // PlayerTickEvent START, after the world's entities had ticked
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            YellowTemperanceBlockAbility.reapplyGuardProtection(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (YellowTemperanceAddon.controller(target) != null
                && event.getSource().is(
                        ModDamageTypes.ULTRAVIOLET)) {
            event.setCanceled(true);
            return;
        }
        MobEffectInstance protection =
                target.getEffect(YellowTemperanceAddon.PROTECTION);
        if (protection != null
                && !isProtectionExcluded(event.getSource())) {
            float reduction = Math.min(
                    YellowTemperanceConfig
                            .PROTECTION_MAX_REDUCTION
                            .get().floatValue(),
                    protection.getAmplifier()
                            * YellowTemperanceConfig
                                    .PROTECTION_REDUCTION_PER_LEVEL
                                    .get().floatValue());
            event.setAmount(
                    event.getAmount() * (1.0F - reduction));
        }

        if (!(target instanceof Mob mob)) {
            return;
        }
        LivingEntity attacker = resolveLiving(
                event.getSource().getEntity());
        if (attacker instanceof Player player) {
            YellowTemperanceControllerEntity controller =
                    YellowTemperanceAddon.controller(player);
            if (controller != null && controller.isDisguised()) {
                DisguiseRetaliationService.mark(
                        mob,
                        player,
                        target.level().getGameTime());
            }
        }
    }

    @SubscribeEvent
    public static void onMobTargetChange(
            LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(event.getNewAboutToBeSetTarget()
                        instanceof Player player)) {
            return;
        }
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(player);
        if (controller != null
                && controller.isDisguised()
                && !DisguiseRetaliationService.canRetaliate(
                        mob,
                        player,
                        mob.level().getGameTime())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null
                && instance.is(
                        YellowTemperanceAddon.PROTECTION)
                && event.getEntity().hasEffect(
                        YellowTemperanceAddon.EROSION)) {
            ErosionService.remove(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity living = event.getEntity();
        if (YellowTemperanceAddon.isYellowTemperanceUser(living)
                || YellowTemperanceAddon.controller(living) != null) {
            ErosionService.clearOwned(living);
        }
        if (living.hasEffect(YellowTemperanceAddon.EROSION)) {
            ErosionService.remove(living);
        }
        DisguiseScaleService.reset(living);
        if (living instanceof Player player) {
            DisguiseRetaliationService.clear(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LivingEntity living
                && !event.getLevel().isClientSide()) {
            DisguiseScaleService.clearLegacyPersistence(living);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        ErosionService.clearOwned(player);
        DisguiseScaleService.reset(player);
        DisguiseRetaliationService.clear(player);
        player.removeEffect(YellowTemperanceAddon.PROTECTION);
    }

    // 1.16 ChatEventHandler: a player disguise sends chat under the impersonated name.
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        YellowTemperanceControllerEntity controller =
                YellowTemperanceAddon.controller(player);
        if (controller == null
                || controller.getDisguiseMode() != DisguiseMode.PLAYER) {
            return;
        }
        String name = controller.getDisguisePlayerName();
        if (name == null || name.isBlank()) {
            return;
        }
        event.setDisplayname(disguiseDisplayName(name, controller.getDisguisePlayer()));
    }

    // Same click, hover and insertion the impersonated player's own name carries,
    // so the user's outer name decoration does not show through.
    static MutableComponent disguiseDisplayName(String name, Optional<UUID> id) {
        UUID uuid = id.orElseGet(() -> UUIDUtil.createOfflinePlayerUUID(name));
        return Component.literal(name).withStyle(style -> style
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " "))
                .withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_ENTITY,
                        new HoverEvent.EntityTooltipInfo(
                                EntityType.PLAYER, uuid, Component.literal(name))))
                .withInsertion(name));
    }

    public static boolean canAffect(
            LivingEntity user,
            LivingEntity target,
            YellowTemperanceControllerEntity controller) {
        if (target == null
                || target == user
                || target == controller
                || target instanceof StandEntity
                || target.level() != user.level()
                || !target.isAlive()
                || target.isRemoved()
                || target.isInvulnerable()) {
            return false;
        }
        // A Creative player passes: the absorption tick answers it with 'cannot_absorb', as 1.16 absorb() did.
        if (target instanceof Player player && player.isSpectator()) {
            return false;
        }
        PlayerTeam userTeam = user.getTeam();
        PlayerTeam targetTeam = target.getTeam();
        if (userTeam != null
                && userTeam.isAlliedTo(targetTeam)
                && !userTeam.isAllowFriendlyFire()) {
            return false;
        }
        return !(user instanceof Player
                        && target instanceof Player)
                || user.getServer() == null
                || user.getServer().isPvpAllowed();
    }

    private static LivingEntity resolveLiving(Entity entity) {
        if (entity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return entity instanceof LivingEntity living
                ? living
                : null;
    }

    private static boolean isProtectionExcluded(
            DamageSource source) {
        return source.is(
                        DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.STARVE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.WITHER);
    }
}
