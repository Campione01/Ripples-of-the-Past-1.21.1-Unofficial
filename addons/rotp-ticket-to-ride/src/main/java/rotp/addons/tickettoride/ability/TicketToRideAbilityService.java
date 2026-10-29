package rotp.addons.tickettoride.ability;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;

import rotp.addons.tickettoride.TicketToRideAddon;
import rotp.addons.tickettoride.ability.TicketToRideAbility.Mode;
import rotp.addons.tickettoride.entity.DeadPhotoMementoEntity;
import rotp.addons.tickettoride.entity.TicketToRideStandEntity;
import rotp.addons.tickettoride.state.LoveTrainService;
import rotp.addons.tickettoride.state.TearsWoundService;
import rotp.addons.tickettoride.state.TicketToRideBehaviorService;
import rotp.addons.tickettoride.state.TraceEnchantService;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

public final class TicketToRideAbilityService {
    private static final double VIEW_CONE_DEGREES = 45.0D;

    private TicketToRideAbilityService() {}

    public static ConditionCheck canUse(
            Mode mode, LivingEntity user) {
        if (user == null) {
            return ConditionCheck.createNegative(
                    "ticket_to_ride_not_summoned");
        }
        return switch (mode) {
            // 1.16 ExplodAction, MobCrash, SummonTaggedMobsAction,
            // SingleLightingHit, lightinghit and SummonDeadphotomemtorAction
            // had no condition: they charged and fired with no marked mob,
            // which only their standPerform looked for.
            case EXPLOD, LIGHTNING_SINGLE, CRASH, LIGHTNING, RANDOM_MOB,
                    METEOR -> ConditionCheck.POSITIVE;
            case TRACE ->
                    user instanceof Player player
                            && traceable(player) != null
                            ? ConditionCheck.POSITIVE
                            : ConditionCheck.createNegative(
                                    "no_enchantable_item");
            case LOVE_TRAIN ->
                    LoveTrainService.isActive(user)
                            ? ConditionCheck.createNegative(
                                    "already_has_love_train")
                            : ConditionCheck.POSITIVE;
            case REMOVE_LOVE_TRAIN ->
                    LoveTrainService.isActive(user)
                            ? ConditionCheck.POSITIVE
                            : ConditionCheck.createNegative(
                                    "no_love_train_to_dispel");
            case SHARE_LOVE_TRAIN, REMOVE_SHARED ->
                    checkCrosshairTarget(user);
        };
    }

    public static boolean perform(
            ServerLevel level,
            LivingEntity user,
            StandPower power,
            Mode mode) {
        return perform(level, user, power, mode, pressedEntity(mode, user));
    }

    // 1.16 Action.perform(world, user, power, target): share and remove act
    // on the target their press was checked with.
    public static boolean perform(
            ServerLevel level,
            LivingEntity user,
            StandPower power,
            Mode mode,
            @Nullable Entity pressed) {
        return switch (mode) {
            // 1.16 set the cooldown on every fire, with or without a
            // marked target to hit.
            case EXPLOD -> {
                explodeMisfortune(level, user, power);
                yield true;
            }
            // 1.16 set the cooldown at the fire, whether or not a hand was left
            // to trace (TraceOnAction.standPerform :88-91 only told the user)
            case TRACE -> {
                trace(level, user);
                yield true;
            }
            case CRASH -> {
                horseCrash(level, user);
                yield true;
            }
            case RANDOM_MOB -> {
                randomMobs(level, user, power);
                yield true;
            }
            case LIGHTNING_SINGLE -> {
                lightning(level, user, true);
                yield true;
            }
            case LIGHTNING -> {
                lightning(level, user, false);
                yield true;
            }
            case METEOR -> {
                meteor(level, user, power);
                yield true;
            }
            case LOVE_TRAIN -> LoveTrainService.activate(user);
            case SHARE_LOVE_TRAIN ->
                    pressed instanceof LivingEntity living
                            && LoveTrainService.share(user, living);
            case REMOVE_SHARED ->
                    pressed instanceof LivingEntity living
                            && LoveTrainService.removeShared(living);
            case REMOVE_LOVE_TRAIN ->
                    LoveTrainService.deactivate(user);
        };
    }

    private static void explodeMisfortune(
            ServerLevel level,
            LivingEntity user,
            StandPower power) {
        LivingEntity target = firstMarkedInView(user);
        if (target == null) {
            return;
        }
        StandEntity stand = power.getSummonedStandEntity();
        playStandUnsummon(level, stand, power);
        switch (user.getRandom().nextInt(3)) {
            case 0 -> {
                PrimedTnt tnt = new PrimedTnt(
                        level,
                        target.getX(),
                        target.getY(),
                        target.getZ(),
                        null);
                tnt.setFuse(10);
                level.addFreshEntity(tnt);
            }
            // 1.16 Explosion.Mode.BREAK from the Stand entity: its user is
            // not spared and mobGriefing is not asked. TNT is that
            // explosion class in 1.21.
            case 1 -> level.explode(
                    stand,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    4.0F,
                    false,
                    Level.ExplosionInteraction.TNT);
            default -> {
                Creeper creeper =
                        EntityType.CREEPER.create(level);
                if (creeper != null) {
                    creeper.moveTo(target.position());
                    creeper.ignite();
                    level.addFreshEntity(creeper);
                }
            }
        }
    }

    // 1.16 ExplodAction: the first marked entity of the view cone in the
    // level's order, not the nearest.
    @Nullable
    private static LivingEntity firstMarkedInView(LivingEntity user) {
        Vec3 look = user.getLookAngle();
        Vec3 eyes = user.getEyePosition();
        return user.level().getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(eyes, eyes).inflate(100.0D),
                        target -> target != user
                                && Math.toDegrees(Math.acos(
                                        look.dot(target.position()
                                                .subtract(eyes)
                                                .normalize())))
                                        <= VIEW_CONE_DEGREES)
                .stream()
                .filter(TearsWoundService::isMarked)
                .findFirst()
                .orElse(null);
    }

    // 1.16 standEntity.playSound(STAND_UNSUMMON_SOUND, 1.0f, 1.0f): at the
    // Stand, for those who hear it.
    private static void playStandUnsummon(
            ServerLevel level,
            @Nullable StandEntity stand,
            StandPower power) {
        if (stand != null && !stand.isSilent()) {
            StandUtil.broadcastSound(
                    level,
                    stand.position(),
                    ModSoundEvents.STAND_UNSUMMON,
                    stand.onlyVisibleToStandUsers(),
                    power,
                    stand.getSoundSource(),
                    1.0F,
                    1.0F);
        }
    }

    private static boolean trace(
            ServerLevel level, LivingEntity user) {
        if (!(user instanceof Player player)) {
            return false;
        }
        ItemStack stack = traceable(player);
        if (stack == null) {
            // 1.16 TraceOnAction.standPerform :88-91
            player.displayClientMessage(Component.translatable(
                    "message.trace_on.none"), true);
            return false;
        }
        if (!TraceEnchantService.apply(level, stack)) {
            return false;
        }
        // 1.16 TraceOnAction.playEffects: player.playSound (heard by the
        // others) and the action-bar message with the hand and the 2 left
        player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable(
                        "message.trace_on.success",
                        stack == player.getMainHandItem() ? "\u4e3b\u624b" : "\u526f\u624b",
                        TraceEnchantService.MAX_USES - 1),
                true);
        return true;
    }

    private static boolean horseCrash(
            ServerLevel level, LivingEntity user) {
        List<LivingEntity> targets = marked(user, 100.0D);
        if (targets.isEmpty()) {
            return false;
        }
        TicketToRideBehaviorService.spawnHorse(
                level, user, targets.get(0));
        return true;
    }

    // 1.16 SummonTaggedMobsAction.standPerform took every marked entity in
    // the user's box grown by 10, the user too (marked by another Ticket to
    // Ride user's tears blade), and played the unsummon sound at the Stand
    // once for each.
    private static boolean randomMobs(
            ServerLevel level, LivingEntity user, StandPower power) {
        int count = 0;
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                user.getBoundingBox().inflate(10.0D),
                target -> target.isAlive()
                        && TearsWoundService.isMarked(target))) {
            count += TicketToRideBehaviorService
                    .spawnMisfortuneMobs(
                            level, user, target);
            playStandUnsummon(
                    level, power.getSummonedStandEntity(), power);
        }
        return count > 0;
    }

    private static boolean lightning(
            ServerLevel level,
            LivingEntity user,
            boolean single) {
        // 1.16 lightinghit.standPerform: every marked entity but the user in
        // the user's box grown by 100, in the level's order, no spectators.
        List<LivingEntity> targets = single
                ? one(nearestMarkedInView(user, 100.0D))
                : level.getEntitiesOfClass(
                                LivingEntity.class,
                                user.getBoundingBox().inflate(100.0D))
                        .stream()
                        .filter(target -> TearsWoundService.isMarked(target)
                                && target != user)
                        .toList();
        if (targets.isEmpty()) {
            return false;
        }
        int count = level.isThundering()
                ? 3
                : level.isRaining() ? 2 : 1;
        float damage = level.isThundering()
                ? 10.0F
                : level.isRaining() ? 8.0F : 6.0F;
        if (single) {
            strikeSingle(level, targets.get(0), count, damage);
            return true;
        }
        // 1.16 lightinghit.strikeEntityWithLightning: the same real bolts,
        // then the Stand summon sound at the target, from the level's random.
        for (LivingEntity target : targets) {
            strikeSingle(level, target, count, damage);
            level.playSound(
                    null,
                    target.getX(),
                    target.getY(),
                    target.getZ(),
                    ModSoundEvents.STAND_SUMMON.get(),
                    SoundSource.HOSTILE,
                    1.0F,
                    0.8F + level.getRandom().nextFloat() * 0.4F);
        }
        return true;
    }

    // 1.16 SingleLightingHit.standPerform and lightinghit: real bolts with
    // setDamage, each up to 1 block off the target on x and z, from the
    // level's random.
    private static void strikeSingle(
            ServerLevel level,
            LivingEntity target,
            int count,
            float damage) {
        Vec3 pos = target.position();
        for (int i = 0; i < count; i++) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) {
                continue;
            }
            bolt.moveTo(
                    pos.x + (level.getRandom().nextDouble() - 0.5D) * 2.0D,
                    pos.y,
                    pos.z + (level.getRandom().nextDouble() - 0.5D) * 2.0D);
            bolt.setDamage(damage);
            level.addFreshEntity(bolt);
        }
    }

    // 1.16 SummonDeadphotomemtorAction.standPerform: the healthiest marked
    // entity but the user in the box of the Stand's block grown by 128.
    private static boolean meteor(
            ServerLevel level,
            LivingEntity user,
            StandPower power) {
        if (!(power.getSummonedStandEntity()
                instanceof TicketToRideStandEntity controller)) {
            return false;
        }
        LivingEntity target = level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(controller.blockPosition())
                                .inflate(128.0D),
                        entity -> entity != user
                                && entity.isAlive()
                                && TearsWoundService.isMarked(entity))
                .stream()
                .max(Comparator.comparingDouble(
                        LivingEntity::getHealth))
                .orElse(null);
        if (target == null) {
            return false;
        }
        DeadPhotoMementoEntity meteor =
                TicketToRideAddon.DEAD_PHOTO_MEMENTO.get()
                        .create(level);
        if (meteor == null) {
            return false;
        }
        meteor.initialize(user, controller);
        // 1.16 summonDeadphotomemtorAboveTarget (:54-62)
        meteor.moveTo(
                target.getX() - 15.0D,
                target.getY() + 21.0D,
                target.getZ() + 15.1D,
                0.0F,
                0.0F);
        return level.addFreshEntity(meteor);
    }

    // 1.16 SingleLightingHit.holdTick: applyKnockback(0.2f, user.x - e.x,
    // user.z - e.z) on the marked entities in the box of the user's block
    // grown by 5. Knockback goes against its ratio, so they are pushed away.
    public static void pullMarked(LivingEntity user) {
        for (LivingEntity target : user.level().getEntitiesOfClass(
                LivingEntity.class,
                new AABB(user.blockPosition()).inflate(5.0D),
                target -> target != user
                        && TearsWoundService.isMarked(target))) {
            knockback116(
                    target,
                    0.2F,
                    user.getX() - target.getX(),
                    user.getZ() - target.getZ());
        }
    }

    // 1.16 LivingEntity.applyKnockback had no random direction for a ratio
    // near zero: Vector3d.normalize gave zero below a length of 1.0E-4.
    private static void knockback116(
            LivingEntity target, float strength, double x, double z) {
        double length = Math.sqrt(x * x + z * z);
        if (length >= 1.0E-4D) {
            double scale = Math.max(1.0D, 0.01D / length);
            target.knockback(strength, x * scale, z * scale);
            return;
        }
        LivingKnockBackEvent event =
                CommonHooks.onLivingKnockBack(target, strength, x, z);
        if (event.isCanceled()) {
            return;
        }
        double resisted = event.getStrength() * (1.0D
                - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (resisted <= 0.0D) {
            return;
        }
        target.hasImpulse = true;
        Vec3 ratio = new Vec3(event.getRatioX(), 0.0D, event.getRatioZ());
        Vec3 push = ratio.length() < 1.0E-4D
                ? Vec3.ZERO
                : ratio.normalize().scale(resisted);
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(
                motion.x / 2.0D - push.x,
                target.onGround()
                        ? Math.min(0.4D, motion.y / 2.0D + resisted)
                        : motion.y,
                motion.z / 2.0D - push.z);
    }

    // 1.16 SingleLightingHit.onHoldTickClientEffect: on every held tick a
    // love_train_light particle where the user's 100-block ray hit. The
    // soul fire flames of lightinghit.onHoldTickClientEffect never showed:
    // it ran on the client, where TearsBlade.isEntityMarked read persistent
    // data the server never synced.
    @Nullable
    public static Vec3 holdParticleAt(
            Mode mode, LivingEntity user, @Nullable Entity stand) {
        return mode == Mode.LIGHTNING_SINGLE
                ? rayTrace116(user, 100.0D, stand)
                : null;
    }

    // 1.16 JojoModUtil.rayTrace(user, reach, e -> e != stand): the nearest
    // pickable entity on the look ray, blocks or not, else the first block
    // outline or the ray's end.
    public static Vec3 rayTrace116(
            LivingEntity user, double reach, @Nullable Entity stand) {
        Vec3 start = user.getEyePosition();
        Vec3 ray = user.getLookAngle().normalize().scale(reach);
        Vec3 end = start.add(ray);
        Vec3 nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Entity candidate : user.level().getEntities(
                user,
                user.getBoundingBox().expandTowards(ray).inflate(1.0D),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != stand)) {
            AABB box = candidate.getBoundingBox()
                    .inflate(candidate.getPickRadius());
            Optional<Vec3> clip = box.clip(start, end);
            double distanceSqr;
            if (box.contains(start)) {
                distanceSqr = 0.0D;
            }
            else if (clip.isPresent()
                    && start.distanceToSqr(clip.get()) < reach * reach
                    && !(candidate.getRootVehicle() == user.getRootVehicle()
                            && !candidate.canRiderInteract())) {
                distanceSqr = start.distanceToSqr(clip.get());
            }
            else {
                continue;
            }
            if (distanceSqr < nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = clip.orElse(start);
            }
        }
        return nearest != null
                ? nearest
                : user.level().clip(new ClipContext(
                                start,
                                end,
                                ClipContext.Block.OUTLINE,
                                ClipContext.Fluid.NONE,
                                user))
                        .getLocation();
    }

    // 1.16 getTargetsInViewCone: living entities in a box around the
    // user's eyes, by the angle between the look vector and the direction
    // from the eyes to the entity's position (its feet).
    @Nullable
    private static LivingEntity nearestMarkedInView(
            LivingEntity user, double radius) {
        Vec3 look = user.getLookAngle();
        Vec3 eyes = user.getEyePosition();
        return user.level().getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(eyes, eyes).inflate(radius),
                        target -> target != user
                                && target.isAlive()
                                && TearsWoundService
                                        .isMarked(target)
                                && Math.toDegrees(Math.acos(
                                        look.dot(target.position()
                                                .subtract(eyes)
                                                .normalize())))
                                        <= VIEW_CONE_DEGREES)
                .stream()
                .min(Comparator.comparingDouble(
                        user::distanceToSqr))
                .orElse(null);
    }

    private static List<LivingEntity> marked(
            LivingEntity user, double radius) {
        AABB area = user.getBoundingBox().inflate(radius);
        return user.level().getEntitiesOfClass(
                        LivingEntity.class,
                        area,
                        target -> target != user
                                && target.isAlive()
                                && TearsWoundService
                                        .isMarked(target))
                .stream()
                .sorted(Comparator.comparingDouble(
                        user::distanceToSqr))
                .toList();
    }

    // 1.16 GiveLFAction / RemoveLFAction: TargetRequirement.ENTITY, then
    // Action.checkRangeAndTarget: 8 blocks from the Stand while it is out
    // (StandAction.getPerformer), 4 out of its sight, else "target_too_far".
    // Any entity but the user passed; perform only touched living ones.
    private static ConditionCheck checkCrosshairTarget(LivingEntity user) {
        Entity target = crosshairEntity(user);
        if (target == null || target == user) {
            return ConditionCheck.createNegative("no_target");
        }
        return ActionTargetRange.isEntityWithinRange(
                        ActionTargetRange.standPerformer(user),
                        target,
                        ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ)
                ? ConditionCheck.POSITIVE
                : ConditionCheck.createNegative("target_too_far");
    }

    // The entity share / remove are pressed at; null for the other modes.
    @Nullable
    public static Entity pressedEntity(Mode mode, LivingEntity user) {
        return mode == Mode.SHARE_LOVE_TRAIN || mode == Mode.REMOVE_SHARED
                ? crosshairEntity(user)
                : null;
    }

    // 1.16 took the target the user's client sent with the press,
    // EntityStandType.clientHitResult: the vanilla pick (the user's aim) when
    // it held an entity other than the Stand, else, with the Stand out,
    // StandEntity.precisionRayTrace from the user with friendly fire on, so
    // teammates and tamed animals too. The Stand's synced aim keeps only
    // entities the Stand may attack, so it is not read. Only the user's
    // client has its own aim.
    @Nullable
    private static Entity crosshairEntity(LivingEntity user) {
        Level level = user.level();
        ActionTarget target;
        if (level.isClientSide()) {
            if (user != ClientProxy.getClientPlayer()) {
                return null;
            }
            target = ClientsideAim.playerAim.getTarget();
        }
        else {
            ActionTargetAim aim = LivingComponentAction.getAim(user);
            // 1.16 PowerBaseImpl.checkTarget emptied a press at an entity the
            // server did not have.
            if (aim == null || aim.isClientEntityMissing()) {
                return null;
            }
            target = aim.getTarget();
        }
        StandEntity stand = StandUtil.getSummonedStand(user);
        Entity entity = entityOf(target, level);
        if (stand != null && (entity == stand || target == null
                || target.getType() == ActionTarget.TargetType.EMPTY)) {
            double reach = StandEntityPunchAbility.userAimReach(user, stand);
            boolean wallsHideEntities = StandSkillWalls.wallsHideEntities(user);
            entity = entityOf(StandEntityPunchAbility.aimAssist(user)
                            ? StandPrecisionRay.trace(
                                    user, stand, reach, 0.0D, true,
                                    wallsHideEntities)
                            : StandPrecisionRay.crosshair(
                                    user, stand, reach, true,
                                    wallsHideEntities),
                    level);
        }
        return entity != stand ? entity : null;
    }

    @Nullable
    private static Entity entityOf(
            @Nullable ActionTarget target, Level level) {
        if (target == null
                || target.getType() != ActionTarget.TargetType.ENTITY) {
            return null;
        }
        target.resolveEntityId(level);
        Entity entity = target.getMainEntity();
        return entity != null && !entity.isRemoved() ? entity : null;
    }

    @Nullable
    private static ItemStack traceable(Player player) {
        RegistryAccess registries = player.level().registryAccess();
        if (TraceEnchantService.isTraceable(
                registries, player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        return TraceEnchantService.isTraceable(
                registries, player.getOffhandItem())
                ? player.getOffhandItem()
                : null;
    }

    private static List<LivingEntity> one(
            @Nullable LivingEntity target) {
        return target != null ? List.of(target) : List.of();
    }
}
