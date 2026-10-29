package rotp.addons.heavensdoor;

import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.heavensdoor.entity.HeavensDoorStandEntity;
import rotp.addons.heavensdoor.state.BookCommandLeaseService;
import rotp.addons.heavensdoor.state.BookCommandSessionService;
import rotp.addons.heavensdoor.state.BookStateService;
import rotp.addons.heavensdoor.state.BookStateRules;
import rotp.addons.heavensdoor.state.HeavensDoorRestrictionRules;
import rotp.addons.heavensdoor.state.CannotMoveRules;
import rotp.addons.heavensdoor.state.WrittenCommandService;
import rotp.addons.heavensdoor.state.RestrictedCommandOwners;
import rotp.addons.heavensdoor.network.BookCommand;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = HeavensDoorAddon.MOD_ID)
public final class HeavensDoorEvents {
    // Last game tick each "You forgot how to ..." line was shown. One map
    // per side: Entity equality is by id, which both sides share.
    private static final Map<Player, Map<String, Long>> SERVER_FORGOT_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Player, Map<String, Long>> CLIENT_FORGOT_TICKS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long FORGOT_MESSAGE_INTERVAL = 20L;
    private static final ResourceLocation FORGOT_RUN_SPEED =
            HeavensDoorAddon.id("forgot_run_speed");
    private static final ResourceLocation FORGOT_SWIM_SPEED =
            HeavensDoorAddon.id("forgot_swim_speed");

    private HeavensDoorEvents() {}

    public static void registerAttributes(
            EntityAttributeCreationEvent event) {
        event.put(
                HeavensDoorAddon.HEAVENS_DOOR_ENTITY.get(),
                HeavensDoorStandEntity.createAttributes().build());
    }

    /**
     * 1.16 MobEntityMixin.addAttackAttribute: Mob.createMobAttributes()
     * gained ATTACK_DAMAGE 1.0 when absent, so every mob (villagers and
     * animals too) had it. Book aggression makes such mobs hit through
     * Mob.doHurtTarget, which reads the attribute unconditionally, and KYS
     * adds it to a mob's self-harm. FOLLOW_RANGE marks the suppliers built
     * from createMobAttributes(); a subclass's own value still wins.
     */
    public static void addMobAttackDamage(
            EntityAttributeModificationEvent event) {
        for (EntityType<? extends LivingEntity> type
                : event.getTypes()) {
            if (event.has(type, Attributes.FOLLOW_RANGE)
                    && !event.has(type, Attributes.ATTACK_DAMAGE)) {
                event.add(type, Attributes.ATTACK_DAMAGE, 1.0D);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity living) {
            WrittenCommandService.observeOwner(living);
            WrittenCommandService.tick(living);
            BookCommandSessionService.tickSelfHarmOwner(living);
            CannotMoveRules.enforce(living);
        }
        if (event.getEntity() instanceof Player player) {
            enforceForgottenLocomotion(player);
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)
                || living.level().isClientSide()) {
            return;
        }
        BookStateService.tickPageOwner(living);
        if (living.hasData(HeavensDoorAddon.BOOK_STATE)
                || living.hasEffect(HeavensDoorAddon.BOOK_EFFECT)) {
            BookStateService.tick(living);
        }
        BookCommandLeaseService.tickSubject(living);
        if (living instanceof ServerPlayer player) {
            BookCommandSessionService.tick(player);
            BookCommandLeaseService.tickIssuer(player);
        }
        enforceForgottenMovement(living);
        if (living instanceof Mob mob
                && mob.hasEffect(
                        HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT)
                && mob.getTarget() != null
                && mob.distanceToSqr(mob.getTarget()) <= 100.0D) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(
            LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()
                || event.getAmount() <= 0.0F) {
            return;
        }
        LivingEntity attacker = sourceUser(event.getSource());
        if (attacker != null
                && attacker.hasEffect(
                        HeavensDoorAddon.FORGOT_ATTACK)) {
            event.setCanceled(true);
            return;
        }
        HeavensDoorStandEntity stand =
                resolveStand(event.getSource());
        if (stand != null) {
            applyCanonicalPunchModifier(
                    stand, event.getEntity(), event);
            LivingEntity owner = stand.getUser();
            LivingEntity target = BookStateService.authorizedTarget(
                    owner, event.getEntity());
            if (target != null && isFinisher(stand)) {
                applyFinisherCommand(stand, target);
                BookStateService.clear(target);
            }
            else {
                BookStateService.markFromHit(
                        stand, event.getEntity());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEffectAdded(MobEffectEvent.Added event) {
        WrittenCommandService.captureExternalAdd(event.getEntity(), event.getEffectInstance());
    }

    // Health is already written here, but a totem or a cancelled death can
    // still save the entity: only the health rule may act on it, death waits
    // for onLivingDeath.
    @SubscribeEvent
    public static void onDamageApplied(LivingDamageEvent.Post event) {
        WrittenCommandService.observeOwner(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player
                && player.isAlive()) {
            BookCommandLeaseService.tickIssuer(player);
        }
    }

    @SubscribeEvent
    public static void onEnderEntityTeleport(
            EntityTeleportEvent.EnderEntity event) {
        if (CannotMoveRules.blocksSelf(event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAbilityPress(
            RipplesAbilityKeyPressEvent event) {
        if (event.getPower() instanceof StandPower
                && event.getEntity().hasEffect(
                        HeavensDoorAddon.FORGOT_STAND)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof Player player) {
                notifyForgot(player, "forgot_stand");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().hasEffect(
                HeavensDoorAddon.FORGOT_ATTACK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(
            PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().hasEffect(
                HeavensDoorAddon.FORGOT_BREAK)) {
            event.setCanceled(true);
            notifyForgot(event.getEntity(), "forgot_break");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer().hasEffect(
                HeavensDoorAddon.FORGOT_BREAK)) {
            event.setCanceled(true);
            notifyForgot(event.getPlayer(), "forgot_break");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(
            PlayerInteractEvent.RightClickItem event) {
        if (forgotOffhand(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(
            PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (forgotOffhand(player, event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(
            PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (forgotOffhand(player, event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(
            PlayerInteractEvent.EntityInteractSpecific event) {
        if (forgotOffhand(event.getEntity(), event.getHand())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(
            LivingEvent.LivingJumpEvent event) {
        LivingEntity living = event.getEntity();
        Vec3 movement = living.getDeltaMovement();
        if (HeavensDoorRestrictionRules
                .blocksPoweredLeap(living)) {
            living.setDeltaMovement(
                    movement.x, 0.0D, movement.z);
            // Fires on the client too: the zeroed jump never reaches the server.
            if (living instanceof Player player
                    && player.hasEffect(HeavensDoorAddon.FORGOT_JUMP)) {
                notifyForgot(player, "forgot_jump");
            }
            return;
        }
        MobEffectInstance book =
                living.getEffect(HeavensDoorAddon.BOOK_EFFECT);
        if (book != null) {
            living.setDeltaMovement(
                    movement.x,
                    book.getAmplifier() >= 1
                            ? 0.0D
                            : Math.min(movement.y, 0.1D),
                    movement.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            BookCommandSessionService.revoke(player);
        }
        BookCommandLeaseService.onEntityDeath(entity);
        MinecraftServer server = entity.getServer();
        if (server != null) {
            RestrictedCommandOwners restrictedOwners = RestrictedCommandOwners.get(server);
            if (restrictedOwners.tracks(entity.getUUID())) {
                restrictedOwners.observe(entity.getUUID(), true);
                WrittenCommandService.releaseOwnedBy(server, entity.getUUID());
            }
            WrittenCommandService.tick(entity);
        }
        BookStateService.invalidateOwnedPages(entity);
        BookStateService.clearPages(entity);
        BookStateService.clear(entity);
        BookStateService.clearOwnedBy(
                entity.getServer(), entity.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player player = event.getEntity();
        if (original instanceof ServerPlayer originalPlayer) {
            BookCommandSessionService.revoke(originalPlayer);
            BookCommandLeaseService.invalidatePlayerLifecycle(
                    originalPlayer);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BookCommandSessionService.revoke(serverPlayer);
            BookCommandLeaseService.invalidatePlayerLifecycle(
                    serverPlayer);
        }
        if (original.hasData(HeavensDoorAddon.BOOK_STATE)) {
            HeavensDoorAddon.bookState(player).deserializeNBT(player.registryAccess(),
                    HeavensDoorAddon.bookState(original).serializeNBT(original.registryAccess()));
        }
        if (event.isWasDeath()) {
            BookStateService.invalidateOwnedPages(original);
            BookStateService.clearPages(original);
            BookStateService.clearPages(player);
        }
        BookStateService.clear(original);
        BookStateService.clear(player);
        BookStateService.clearOwnedBy(
                player.getServer(), original.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            BookCommandSessionService.revoke(serverPlayer);
            BookCommandLeaseService.invalidatePlayerLifecycle(
                    serverPlayer);
        }
        if (player.hasData(HeavensDoorAddon.BOOK_STATE)) {
            BookStateService.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            BookCommandSessionService.revoke(serverPlayer);
            BookCommandLeaseService.invalidatePlayerLifecycle(
                    serverPlayer);
        }
        BookStateService.clear(player);
        BookStateService.clearOwnedBy(
                player.getServer(), player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(
            PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            BookCommandSessionService.revoke(serverPlayer);
            BookCommandLeaseService.invalidatePlayerLifecycle(
                    serverPlayer);
        }
        BookStateService.clear(player);
        BookStateService.clearOwnedBy(
                player.getServer(), player.getUUID());
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(
            EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity()
                        instanceof LivingEntity living) {
            if (living.hasData(HeavensDoorAddon.BOOK_STATE)) {
                BookStateService.tick(living);
            }
            BookCommandLeaseService.tickSubject(living);
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(
            EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()
                && event.getEntity()
                        instanceof LivingEntity living) {
            BookCommandLeaseService.onEntityUnload(living);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(
            ServerStoppedEvent event) {
        BookCommandLeaseService.releaseAllRuntime();
    }

    @SubscribeEvent
    public static void onStartTracking(
            PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getTarget() instanceof LivingEntity target
                && target.hasData(HeavensDoorAddon.BOOK_STATE)) {
            HeavensDoorAddon.syncBookState(player, target);
        }
    }

    private static HeavensDoorStandEntity resolveStand(
            DamageSource source) {
        HeavensDoorStandEntity direct =
                asHeavensDoor(source.getDirectEntity());
        return direct != null
                ? direct
                : asHeavensDoor(source.getEntity());
    }

    private static LivingEntity sourceUser(
            DamageSource source) {
        Entity sourceEntity = source.getEntity();
        if (sourceEntity instanceof StandEntity stand) {
            return stand.getUser();
        }
        return sourceEntity instanceof LivingEntity living
                ? living
                : null;
    }

    private static boolean isFinisher(
            HeavensDoorStandEntity stand) {
        return "finisher_punch".equals(actionName(stand));
    }

    private static String actionName(
            HeavensDoorStandEntity stand) {
        EntityActionInstance action =
                LivingComponentAction.getCurEntityAction(stand);
        return action != null
                ? action.ability.getAbilityId().nameInMoveset()
                : "";
    }

    private static void applyCanonicalPunchModifier(
            HeavensDoorStandEntity stand,
            LivingEntity target,
            LivingIncomingDamageEvent event) {
        float extraDamage = switch (actionName(stand)) {
            case "punch" -> 0.3F;
            case "barrage" -> 0.05F;
            case "finisher_punch" -> 0.5F;
            default -> 0.0F;
        };
        if (extraDamage <= 0.0F) {
            return;
        }
        event.setAmount(event.getAmount() + extraDamage);
        Vec3 direction = target.position()
                .subtract(stand.position());
        if (direction.lengthSqr() > 1.0E-6D) {
            Vec3 push = direction.normalize().scale(0.05D);
            target.push(push.x, 0.01D, push.z);
        }
    }

    private static void applyFinisherCommand(
            HeavensDoorStandEntity stand,
            LivingEntity target) {
        applyFinisherCommand(
                stand, target, stand.getRandom().nextInt(3));
    }

    // A written command that is refused (the user at or below the health
    // threshold, a target the effect does not apply to) becomes Fly Back,
    // so that no roll of the finisher is empty.
    static void applyFinisherCommand(
            HeavensDoorStandEntity stand,
            LivingEntity target,
            int roll) {
        switch (roll) {
            case 0 -> flyBack(stand, target);
            case 1 -> {
                if (!WrittenCommandService.apply(stand.getUser(), target, BookCommand.FREEZE)) {
                    flyBack(stand, target);
                }
            }
            case 2 -> {
                if (target.hasEffect(
                        HeavensDoorAddon.BLINDNESS_ENTITY_EFFECT)
                        || !WrittenCommandService.apply(stand.getUser(), target, BookCommand.BLINDNESS_TOGGLE)) {
                    flyBack(stand, target);
                }
            }
            default -> throw new IllegalStateException(
                    "unreachable finisher command");
        }
    }

    // 1.16 sent TriggerFlyBackwardsPacket, handled after the punch landed.
    // Queue it past this tick so the hit's own knockback cannot halve it;
    // direction is away from the Stand user, not the target's look.
    private static void flyBack(
            HeavensDoorStandEntity stand, LivingEntity target) {
        LivingEntity user = stand.getUser();
        LivingEntity caster = user != null ? user : stand;
        MinecraftServer server = target.getServer();
        if (server == null) {
            return;
        }
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (target.isAlive()
                    && !caster.isRemoved()
                    && caster.level() == target.level()) {
                BookCommandSessionService.applyFlyBack(caster, target);
            }
        }));
    }

    private static boolean forgotOffhand(
            Player player, InteractionHand hand) {
        if (hand != InteractionHand.OFF_HAND
                || !player.hasEffect(
                        HeavensDoorAddon.FORGOT_SECOND_HAND)) {
            return false;
        }
        // 1.16 only spoke up when the off-hand held something.
        if (!player.getOffhandItem().isEmpty()) {
            notifyForgot(player, "forgot_second_hand");
        }
        return true;
    }

    // 1.16 GameplayHandler.sendMessage: action-bar line, at most once a
    // second per action and side.
    public static void notifyForgot(Player player, String action) {
        long now = player.level().getGameTime();
        Map<Player, Map<String, Long>> ticks =
                player.level().isClientSide()
                        ? CLIENT_FORGOT_TICKS
                        : SERVER_FORGOT_TICKS;
        Map<String, Long> sent = ticks.computeIfAbsent(
                player, key -> new HashMap<>());
        synchronized (sent) {
            Long last = sent.get(action);
            if (last != null && now >= last
                    && now - last < FORGOT_MESSAGE_INTERVAL) {
                return;
            }
            sent.put(action, now);
        }
        player.displayClientMessage(Component.translatable(
                "message.rotp_hd." + action), true);
    }

    // 1.16 GameplayHandler.onLivingUpdate :372-395, on both sides at the
    // start of the tick: the owning client sprints again in its own aiStep,
    // so a server-only reset never slows it. 1.16 wrote the attribute base
    // (0.08; 0.5 and 0.9) and left 0.08 behind when the effect ended; a
    // modifier gives the same values and is taken back.
    private static void enforceForgottenLocomotion(Player player) {
        AttributeInstance swimSpeed =
                player.getAttribute(NeoForgeMod.SWIM_SPEED);
        if (swimSpeed != null) {
            if (!player.hasEffect(HeavensDoorAddon.FORGOT_SWIM)) {
                swimSpeed.removeModifier(FORGOT_SWIM_SPEED);
            }
            else if (player.isInWater()) {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                    slow(swimSpeed, FORGOT_SWIM_SPEED, BookStateRules.FORGOT_SPRINT_SWIM_FACTOR - 1.0D);
                    notifyForgot(player, "forgot_swim");
                }
                else {
                    slow(swimSpeed, FORGOT_SWIM_SPEED, BookStateRules.FORGOT_SWIM_FACTOR - 1.0D);
                }
            }
            else {
                swimSpeed.removeModifier(FORGOT_SWIM_SPEED);
            }
        }
        AttributeInstance movementSpeed =
                player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movementSpeed != null) {
            if (player.hasEffect(HeavensDoorAddon.FORGOT_RUN)
                    && player.isSprinting()) {
                player.setSprinting(false);
                slow(movementSpeed, FORGOT_RUN_SPEED, BookStateRules.FORGOT_RUN_FACTOR - 1.0D);
                notifyForgot(player, "forgot_run");
            }
            else {
                movementSpeed.removeModifier(FORGOT_RUN_SPEED);
            }
        }
    }

    private static void slow(
            AttributeInstance attribute,
            ResourceLocation id,
            double amount) {
        AttributeModifier current = attribute.getModifier(id);
        if (current == null || current.amount() != amount
                || current.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            attribute.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            id,
                            amount,
                            AttributeModifier.Operation
                                    .ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void enforceForgottenMovement(
            LivingEntity living) {
        if (!(living instanceof Player player)) {
            return;
        }
        if (player.hasEffect(HeavensDoorAddon.FORGOT_SHIFT)) {
            if (player.isShiftKeyDown()) {
                notifyForgot(player, "forgot_shift");
            }
            player.setShiftKeyDown(false);
        }
        if (player.hasEffect(HeavensDoorAddon.FORGOT_STAND)) {
            StandPower power = StandPower.get(player);
            if (power != null
                    && power.hasPower()
                    && power.isSummoned()) {
                power.getPowerType().forceUnsummon(
                        player, power);
                notifyForgot(player, "forgot_stand");
            }
        }
    }

    private static HeavensDoorStandEntity asHeavensDoor(
            Entity entity) {
        return entity instanceof HeavensDoorStandEntity stand
                ? stand
                : null;
    }
}
