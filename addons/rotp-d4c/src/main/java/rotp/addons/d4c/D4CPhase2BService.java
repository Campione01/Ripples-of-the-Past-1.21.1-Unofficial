package rotp.addons.d4c;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

import rotp.core.api.stand.StandSkillWalls;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;

import rotp.addons.d4c.ability.D4CPhase2BAbility.Mode;
import rotp.addons.d4c.entity.D4CAnnihilationCloneEntity;
import rotp.addons.d4c.entity.D4CMarkerEntity;
import rotp.addons.d4c.entity.D4CMinionAiMode;
import rotp.addons.d4c.entity.D4CMinionEntity;
import rotp.addons.d4c.entity.D4CSummonKickEntity;
import rotp.addons.d4c.network.AnnihilationMarkPayload;
import rotp.addons.d4c.network.D4CLoveTrainSync;
import rotp.addons.d4c.network.OpenMinionAiSelectorPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.api.CuriosApi;

public final class D4CPhase2BService {
    private static final String ANNIHILATION_TARGET =
            "rotp_d4c.phase2b.annihilation_target";
    private static final String LOVE_TRAIN_CORPSE =
            "rotp_d4c.phase2b.love_train_corpse";
    // 1.16 feedback keys
    public static final String MSG_LOVE_TRAIN_REQUIRES_CORPSE =
            "message.rotp_d4c.love_train_requires_corpse";
    public static final String MSG_LOVE_TRAIN_CONSUME_CORPSE =
            "message.rotp_d4c.love_train_consume_corpse";
    public static final String MSG_LOVE_TRAIN_MANUAL_EXIT =
            "message.rotp_d4c.love_train_manual_exit";
    public static final String MSG_LOVE_TRAIN_EXPIRE_RETURN_CORPSE =
            "message.rotp_d4c.love_train_expire_return_corpse";
    public static final String MSG_LIGHT_WALL_REQUIRES_LOVE_TRAIN =
            "message.rotp_d4c.light_wall_requires_love_train";
    public static final String MSG_LIGHT_WALL_NOT_ACTIVE =
            "message.rotp_d4c.light_wall_not_active";
    public static final String MSG_LIGHT_WALL_ACTIVATED =
            "message.rotp_d4c.light_wall_activated";
    public static final String MSG_LIGHT_WALL_DEACTIVATED =
            "message.rotp_d4c.light_wall_deactivated";
    public static final String MSG_TARGET_TOO_FAR =
            "message.rotp_d4c.target_too_far";
    public static final String MSG_NOT_TARGETING_OWN_MINION =
            "d4c.condition.not_targeting_own_minion";
    public static final String MSG_ANNIHILATION_MARK_NEED_ENEMY =
            "jojo.message.action_condition.annihilation_mark_need_enemy";
    public static final String MSG_ANNIHILATION_NEED_MARK =
            "jojo.message.action_condition.annihilation_need_mark";
    // ConditionCheck postfix of jojo.message.action_condition.*
    public static final String MSG_SUMMON_KICK_NEED_TARGET =
            "summon_kick_need_target";
    // 1.16 aa/aa/ad.java:55 had it as hard-coded text (no minion found)
    public static final String MSG_MINION_NOT_FOUND =
            "message.rotp_d4c.minion_not_found";

    private D4CPhase2BService() {}

    /**
     * Lang key for why canExecute refused, or null for a silent refusal.
     * Only meaningful after canExecute returned false.
     */
    @Nullable
    public static String refusalKey(Mode mode, LivingEntity user) {
        if (!(user instanceof net.minecraft.world.entity.player.Player)) {
            return null;
        }
        boolean server = user instanceof ServerPlayer;
        return switch (mode) {
            case LOVE_TRAIN -> !user.hasEffect(D4CAddon.LOVE_TRAIN)
                    ? MSG_LOVE_TRAIN_REQUIRES_CORPSE : null;
            case LIGHT_WALL -> !user.hasEffect(D4CAddon.LOVE_TRAIN)
                    ? MSG_LIGHT_WALL_REQUIRES_LOVE_TRAIN : null;
            case LIGHT_WALL_DEACTIVATE -> MSG_LIGHT_WALL_NOT_ACTIVE;
            // target admission is server-side; the client defers
            case PULL_IN_TRAVEL -> server ? MSG_TARGET_TOO_FAR : null;
            case SWAP_WITH_MINION ->
                    server ? MSG_NOT_TARGETING_OWN_MINION : null;
            case ANNIHILATION_MARK ->
                    server ? MSG_ANNIHILATION_MARK_NEED_ENEMY : null;
            // 1.16 aa/aa.java:69-71
            case ANNIHILATION -> server ? MSG_ANNIHILATION_NEED_MARK : null;
            case MINION_AI_CONTROL -> server ? MSG_MINION_NOT_FOUND : null;
            default -> null;
        };
    }

    public static void notify(LivingEntity user, String key) {
        if (user instanceof net.minecraft.world.entity.player.Player player
                && !player.level().isClientSide()) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    public static boolean canExecute(Mode mode, LivingEntity user) {
        if (!(user instanceof ServerPlayer player)) {
            if (!(user instanceof net.minecraft.world.entity.player.Player
                    clientPlayer)) {
                return mode == Mode.RAPID_PUNCH
                        || mode == Mode.HAND_CHOP
                        || mode == Mode.ITEM_THROW;
            }
            return switch (mode) {
                // 1.16 checked nothing at their press (aa/ak.java,
                // aa/al.java:60-62, aa/ad.java:99-101): its HUD showed them
                case SUMMON_MINION, SWAP_WITH_MINION, FLAG_TRAVEL ->
                        D4CRuntimeRules.deferTargetAdmissionToServer(
                                user.level().isClientSide(),
                                true);
                // 1.16 ran these on the user's client as on the server
                // (HUD and press): aa/ab.java:52-62 with the mark its server
                // sent (ak/aa/aa/aa.java), aa/aa.java:63-72, aa/aj.java:67-73,
                // aa/aa/ad.java:48-57. The first two were checkStandConditions,
                // which StandEntityAction.checkConditions (:98-106) asked only
                // with the Stand out; with D4C away the server refuses.
                case ANNIHILATION_MARK -> !standOut(clientPlayer)
                        || findLookTarget(clientPlayer, 15.0D,
                                markWallsHideEntities(clientPlayer)) != null
                        || clientMarkedTarget(clientPlayer) != null;
                case ANNIHILATION -> !standOut(clientPlayer)
                        || clientMarkedTarget(clientPlayer) != null;
                case PULL_IN_TRAVEL -> findPullInTarget(clientPlayer) != null;
                case MINION_AI_CONTROL -> !ownedMinions(
                        clientPlayer, D4CPhase2BSpec.MINION_AI_CONTROL_RANGE)
                        .isEmpty();
                case LOVE_TRAIN ->
                        !user.hasEffect(D4CAddon.LOVE_TRAIN)
                                && hasCorpseItem(
                                        user,
                                        D4CAddon.COMPLETE_SAINT_CORPSE.get());
                case LOVE_TRAIN_DEACTIVATE ->
                        user.hasEffect(D4CAddon.LOVE_TRAIN);
                case LIGHT_WALL ->
                        user.hasEffect(D4CAddon.LOVE_TRAIN)
                                && !user.hasEffect(
                                        D4CAddon.LOVE_TRAIN_LIGHT_WALL);
                case LIGHT_WALL_DEACTIVATE ->
                        user.hasEffect(D4CAddon.LOVE_TRAIN_LIGHT_WALL);
                case ITEM_THROW -> !user.getOffhandItem().isEmpty();
                default -> true;
            };
        }
        return switch (mode) {
            case SUMMON_MINION -> ownedMinions(player, 50.0D).size()
                    < D4CPhase2BSpec.MAX_MINIONS;
            case MINION_AI_CONTROL -> !ownedMinions(
                    player, D4CPhase2BSpec.MINION_AI_CONTROL_RANGE).isEmpty();
            case SWAP_WITH_MINION ->
                    !swapCandidates(player).isEmpty();
            case ANNIHILATION_MARK ->
                    findLookTarget(player, 15.0D,
                            markWallsHideEntities(player)) != null
                            || markedTarget(player) != null;
            case ANNIHILATION -> markedTarget(player) != null;
            case PULL_IN_TRAVEL -> findPullInTarget(player) != null;
            case LOVE_TRAIN ->
                    !player.hasEffect(D4CAddon.LOVE_TRAIN)
                            && hasCorpseItem(
                                    player,
                                    D4CAddon.COMPLETE_SAINT_CORPSE.get());
            case LOVE_TRAIN_DEACTIVATE ->
                    player.hasEffect(D4CAddon.LOVE_TRAIN);
            case LIGHT_WALL ->
                    player.hasEffect(D4CAddon.LOVE_TRAIN)
                            && !player.hasEffect(
                                    D4CAddon.LOVE_TRAIN_LIGHT_WALL);
            case LIGHT_WALL_DEACTIVATE ->
                    player.hasEffect(D4CAddon.LOVE_TRAIN_LIGHT_WALL);
            case ITEM_THROW -> !player.getOffhandItem().isEmpty();
            default -> true;
        };
    }

    private static boolean standOut(LivingEntity user) {
        StandPower power = StandPower.get(user);
        return power != null && power.getSummonedStandEntity() != null;
    }

    public static float staminaCost(Mode mode, LivingEntity user) {
        if (mode == Mode.ANNIHILATION_MARK) {
            if (user.level().isClientSide()) {
                return 25.0F;
            }
            if (!(user instanceof ServerPlayer player)) {
                return mode.stamina();
            }
            LivingEntity looked = findLookTarget(
                    player, 15.0D, markWallsHideEntities(player));
            LivingEntity marked = markedTarget(player);
            if (marked != null
                    && (looked == null
                            || looked.getUUID().equals(
                                    marked.getUUID()))) {
                return 25.0F;
            }
        }
        return mode.stamina();
    }

    public static boolean execute(
            Mode mode, ServerLevel level, LivingEntity user) {
        if (!canExecute(mode, user)) {
            return false;
        }
        return switch (mode) {
            case SUMMON_MINION -> summonMinion(level, (ServerPlayer) user);
            case MINION_AI_CONTROL ->
                    openMinionAiSelector((ServerPlayer) user);
            case SWAP_WITH_MINION -> swapWithMinion((ServerPlayer) user);
            case ANNIHILATION_MARK -> toggleMark((ServerPlayer) user);
            case ANNIHILATION -> spawnAnnihilationClone(
                    level, (ServerPlayer) user);
            case FLAG_TRAVEL -> spawnTravelMarker(
                    level,
                    (ServerPlayer) user,
                    (ServerPlayer) user,
                    D4CMarkerEntity.Mode.FLAG);
            case PULL_IN_TRAVEL -> spawnPullMarker(
                    level, (ServerPlayer) user);
            case ITEM_THROW -> throwOffhand(level, user);
            case LOVE_TRAIN -> activateLoveTrain((ServerPlayer) user);
            case LOVE_TRAIN_DEACTIVATE ->
                    deactivateLoveTrain((ServerPlayer) user);
            case LIGHT_WALL -> activateLightWall(user);
            case LIGHT_WALL_DEACTIVATE -> deactivateLightWall(user);
            case RAPID_PUNCH, HAND_CHOP -> true;
        };
    }

    private static boolean summonMinion(
            ServerLevel level, ServerPlayer owner) {
        D4CMinionEntity minion = D4CAddon.SUMMONED_PLAYER.get()
                .create(level);
        if (minion == null) {
            return false;
        }
        Vec3 pos = owner.position().add(
                owner.getLookAngle().multiply(2.0D, 0.0D, 2.0D));
        minion.moveTo(
                pos.x,
                owner.getY(),
                pos.z,
                owner.getYRot(),
                0.0F);
        if (!level.noCollision(minion)) {
            minion.moveTo(
                    owner.getX(),
                    owner.getY(),
                    owner.getZ(),
                    owner.getYRot(),
                    0.0F);
        }
        minion.setOwner(owner);
        // 1.16 aa.ak: summon in the owner's chosen mode
        minion.setAiMode(D4CMinionAiMode.stored(owner.getPersistentData()));
        level.addFreshEntity(minion);
        level.sendParticles(
                D4CAddon.GOLD_LIGHT.get(),
                minion.getX(),
                minion.getY(0.5D),
                minion.getZ(),
                20,
                0.4D,
                0.8D,
                0.4D,
                0.03D);
        return true;
    }

    // 1.16 aa/aa/ad.java:60-72: the press opened the mode screen on its
    // user's client (ak/aa/aa/ai.java); the choice comes back as
    // SelectMinionAiModePayload
    private static boolean openMinionAiSelector(ServerPlayer owner) {
        if (owner.connection == null
                || !owner.connection.hasChannel(
                        OpenMinionAiSelectorPayload.TYPE)) {
            return false;
        }
        PacketDistributor.sendToPlayer(
                owner, OpenMinionAiSelectorPayload.INSTANCE);
        return true;
    }

    // 1.16 ak/aa/ab/aa.java:39-46: the chosen mode became the owner's (for
    // minions summoned later) and every own minion's within 1000 blocks
    public static boolean selectMinionAiMode(ServerPlayer owner, int mode) {
        if (mode < 0 || mode >= D4CMinionAiMode.COUNT) {
            return false;
        }
        D4CMinionAiMode.store(owner.getPersistentData(), mode);
        for (D4CMinionEntity minion : ownedMinions(
                owner, D4CPhase2BSpec.MINION_AI_APPLY_RANGE)) {
            minion.setAiMode(mode);
        }
        owner.displayClientMessage(
                Component.translatable(
                        "rotp_d4c.message.minion_ai_mode",
                        Component.translatable(
                                D4CMinionAiMode.langKey(mode))),
                true);
        return true;
    }

    private static boolean swapWithMinion(ServerPlayer owner) {
        Vec3 look = owner.getLookAngle();
        LivingEntity minion = swapCandidates(owner)
                .stream()
                .filter(candidate -> {
                    Vec3 d = candidate.position().subtract(owner.position());
                    return D4CRuntimeRules.inSwapCone(
                            look.x, look.y, look.z, d.x, d.y, d.z);
                })
                .min(Comparator.comparingDouble(owner::distanceToSqr))
                .orElse(null);
        // 1.16 al.standPerform
        if (minion == null) {
            notify(owner, MSG_NOT_TARGETING_OWN_MINION);
            return false;
        }
        Vec3 ownerPos = owner.position();
        Vec3 minionPos = minion.position();
        AABB ownerAtMinion = owner.getBoundingBox().move(
                minionPos.x - owner.getX(),
                minionPos.y - owner.getY(),
                minionPos.z - owner.getZ());
        AABB minionAtOwner = minion.getBoundingBox().move(
                ownerPos.x - minion.getX(),
                ownerPos.y - minion.getY(),
                ownerPos.z - minion.getZ());
        if (!owner.serverLevel().noCollision(owner, ownerAtMinion)
                || !owner.serverLevel().noCollision(minion, minionAtOwner)) {
            return false;
        }
        float ownerHealth = owner.getHealth();
        float minionHealth = minion.getHealth();
        float ownerYaw = owner.getYRot();
        float ownerPitch = owner.getXRot();
        float minionYaw = minion.getYRot();
        float minionPitch = minion.getXRot();
        ServerLevel level = owner.serverLevel();
        // 1.16 al.aa: positions and facing trade places
        owner.teleportTo(
                level,
                minionPos.x,
                minionPos.y,
                minionPos.z,
                minionYaw,
                minionPitch);
        minion.moveTo(
                ownerPos.x,
                ownerPos.y,
                ownerPos.z,
                ownerYaw,
                ownerPitch);
        minion.setYHeadRot(ownerYaw);
        minion.setYBodyRot(ownerYaw);
        owner.setHealth(Math.min(owner.getMaxHealth(), minionHealth));
        minion.setHealth(Math.min(minion.getMaxHealth(), ownerHealth));
        // 1.16 al.aa: a flag at each old spot, one block down
        spawnSwapFlag(level, owner, ownerPos);
        spawnSwapFlag(level, owner, minionPos);
        level.playSound(
                null,
                owner.getX(),
                owner.getY(),
                owner.getZ(),
                D4CAddon.D4C_TELEPORT.get(),
                owner.getSoundSource(),
                1.0F,
                1.0F);
        // 1.16 al.standPerform: portal burst at both ends
        for (LivingEntity end : List.of(owner, minion)) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    end.getX(),
                    end.getY() + 1.0D,
                    end.getZ(),
                    20,
                    0.5D,
                    1.0D,
                    0.5D,
                    0.1D);
        }
        return true;
    }

    private static void spawnSwapFlag(
            ServerLevel level, ServerPlayer owner, Vec3 at) {
        D4CMarkerEntity flag =
                D4CAddon.SUMMON_KICK_FLAG.get().create(level);
        if (flag == null) {
            return;
        }
        flag.initializeFixed(owner, at.x, at.y - 1.0D, at.z);
        level.addFreshEntity(flag);
    }

    // 1.16 al.aa: own minions or any annihilation player clone
    private static List<LivingEntity> swapCandidates(ServerPlayer owner) {
        return owner.level().getEntitiesOfClass(
                LivingEntity.class,
                owner.getBoundingBox().inflate(20.0D),
                entity -> entity != owner
                        && D4CRuntimeRules.isSwapCandidate(
                                entity.isAlive(),
                                entity instanceof D4CMinionEntity own
                                        && own.isOwnedBy(owner.getUUID()),
                                entity instanceof D4CAnnihilationCloneEntity clone
                                        && clone.isPlayerClone()));
    }

    private static boolean toggleMark(ServerPlayer owner) {
        LivingEntity looked = findLookTarget(
                owner, 15.0D, markWallsHideEntities(owner));
        LivingEntity marked = markedTarget(owner);
        if (looked != null
                && marked != null
                && looked.getUUID().equals(marked.getUUID())) {
            owner.getPersistentData().remove(ANNIHILATION_TARGET);
            owner.level().playSound(
                    null,
                    owner.blockPosition(),
                    SoundEvents.ITEM_BREAK,
                    SoundSource.PLAYERS,
                    0.5F,
                    1.0F);
            return true;
        }
        if (looked == null) {
            if (marked != null) {
                owner.getPersistentData().remove(ANNIHILATION_TARGET);
                return true;
            }
            return false;
        }
        owner.getPersistentData().putUUID(
                ANNIHILATION_TARGET, looked.getUUID());
        owner.level().playSound(
                null,
                looked.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                1.0F,
                1.5F);
        return true;
    }

    private static boolean spawnAnnihilationClone(
            ServerLevel level, ServerPlayer owner) {
        // 1.16 aa/aa.java:19-31 spawned the clone at a dying target too
        LivingEntity target = markedTarget(owner);
        if (target == null) {
            owner.getPersistentData().remove(ANNIHILATION_TARGET);
            return false;
        }
        boolean alreadyExists = level.getEntitiesOfClass(
                        D4CAnnihilationCloneEntity.class,
                        target.getBoundingBox().inflate(50.0D),
                        clone -> clone.targets(target.getUUID()))
                .stream()
                .findAny()
                .isPresent();
        if (alreadyExists) {
            return false;
        }
        var type = target instanceof ServerPlayer
                ? D4CAddon.ANNIHILATION_PLAYER_CLONE.get()
                : D4CAddon.ANNIHILATION_CLONE.get();
        D4CAnnihilationCloneEntity clone = type.create(level);
        if (clone == null) {
            return false;
        }
        Vec3 ahead = owner.position().add(
                owner.getLookAngle().multiply(2.0D, 0.0D, 2.0D));
        // 1.16 aa.aa: snap to the floor near the caster's feet
        double spawnY = annihilationFloorY(ahead.y, y -> {
            BlockPos at = BlockPos.containing(ahead.x, y, ahead.z);
            return !level.getBlockState(at.below())
                            .getCollisionShape(level, at.below()).isEmpty()
                    && level.getBlockState(at)
                            .getCollisionShape(level, at).isEmpty()
                    && level.getBlockState(at.above())
                            .getCollisionShape(level, at.above()).isEmpty();
        });
        Vec3 spawn = new Vec3(ahead.x, spawnY, ahead.z);
        clone.moveTo(spawn.x, spawn.y, spawn.z, owner.getYRot(), 0.0F);
        clone.initialize(owner, target);
        level.addFreshEntity(clone);
        owner.getPersistentData().remove(ANNIHILATION_TARGET);
        // 1.16 aa.aa: portal burst and d4c_teleport at the spawn point
        for (int i = 0; i < 30; i++) {
            level.sendParticles(
                    ParticleTypes.PORTAL,
                    spawn.x + (level.random.nextDouble() - 0.5D) * 3.0D,
                    spawn.y + level.random.nextDouble() * 3.0D,
                    spawn.z + (level.random.nextDouble() - 0.5D) * 3.0D,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
        level.playSound(
                null,
                spawn.x,
                spawn.y,
                spawn.z,
                D4CAddon.D4C_TELEPORT.get(),
                owner.getSoundSource(),
                1.0F,
                1.0F);
        level.playSound(
                null,
                owner.blockPosition(),
                D4CAddon.VALENTINE_ANNIHILATION.get(),
                SoundSource.VOICE,
                1.0F,
                1.0F);
        return true;
    }

    // 1.16 aa.aa: first standable y from feet+2 down to feet-3, else feet
    static double annihilationFloorY(double feetY, IntPredicate standable) {
        int base = (int) Math.floor(feetY);
        for (int y = base + 2; y >= base - 3; y--) {
            if (standable.test(y)) {
                return y;
            }
        }
        return feetY;
    }

    private static boolean spawnPullMarker(
            ServerLevel level, ServerPlayer owner) {
        LivingEntity target = findPullInTarget(owner);
        if (target == null || !spawnTravelMarker(
                level, owner, target, D4CMarkerEntity.Mode.PULL)) {
            return false;
        }
        D4CParallelItemService.markPulledByD4C(target);
        return true;
    }

    private static boolean spawnTravelMarker(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity target,
            D4CMarkerEntity.Mode mode) {
        D4CMarkerEntity marker = D4CAddon.FLAG.get().create(level);
        if (marker == null) {
            return false;
        }
        marker.initialize(owner, target, mode);
        level.addFreshEntity(marker);
        level.playSound(
                null,
                owner.blockPosition(),
                D4CAddon.D4C_FLANG.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        return true;
    }

    public static boolean canSummonKick(LivingEntity user) {
        return checkSummonKick(user).isPositive();
    }

    // summon_kick (D4CSummonKickAbility): a mob never kicks. 1.16
    // PowerBaseImpl.checkTarget :289-313, on the user's client (HUD and
    // press) as on the server: no entity under the crosshair refused
    // silently (TargetRequirement ENTITY), Action.checkRangeAndTarget
    // :162-204 refused one out of range with target_too_far, then
    // aa/aa/ae.java:56-65 wanted a living entity.
    public static ConditionCheck checkSummonKick(LivingEntity user) {
        if (!(user instanceof net.minecraft.world.entity.player.Player
                player)) {
            return ConditionCheck.NEGATIVE;
        }
        ActionTarget picked = summonKickPick(player);
        Entity entity = picked.getType() == ActionTarget.TargetType.ENTITY
                ? picked.getEntity() : null;
        if (entity == null) {
            return ConditionCheck.NEGATIVE;
        }
        if (!isWithinSummonKickRange(player, entity)) {
            return ConditionCheck.createNegative("target_too_far");
        }
        if (!(entity instanceof LivingEntity)) {
            return ConditionCheck.createNegative(MSG_SUMMON_KICK_NEED_TARGET);
        }
        return ConditionCheck.noMessage(
                summonKickTarget(player, picked) != null);
    }

    // 1.16 kick took its client's mouse target (InputHandler.pickMouseTarget
    // :697-711): the vanilla pick, and behind a miss with D4C out D4C's
    // precision ray at the user's reach (EntityStandType.clientHitResult
    // :122-151), which made the user's own Stand a miss.
    private static ActionTarget summonKickPick(
            net.minecraft.world.entity.player.Player user) {
        StandPower power = StandPower.get(user);
        StandEntity stand = power != null
                ? power.getSummonedStandEntity() : null;
        ActionTarget target = LivingComponentAction.getAimTarget(user);
        if (stand == null) {
            return target;
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() == stand) {
            return ActionTarget.EMPTY;
        }
        if (target.getType() != ActionTarget.TargetType.EMPTY) {
            return target;
        }
        boolean walls = kickWallsHideEntities(user);
        double reach = StandPrecisionRay.reach(user, stand);
        ActionTarget ray = StandEntityPunchAbility.aimAssist(user)
                ? StandPrecisionRay.trace(user, stand, reach, 0.0D, true, walls)
                : StandPrecisionRay.crosshair(user, stand, reach, true, walls);
        return ray.resolveEntityId(user.level());
    }

    // 1.16 Action.getMaxRangeSqEntityTarget 64, from StandAction.getPerformer
    // (D4C while out, else the user); the donor's 16.0 (ae.java:124-126)
    // overrode no core method.
    private static boolean isWithinSummonKickRange(
            LivingEntity user, Entity target) {
        return ActionTargetRange.isEntityWithinRange(
                ActionTargetRange.standPerformer(user),
                target,
                ActionTargetRange.DEFAULT_ENTITY_RANGE_SQ);
    }

    @Nullable
    private static LivingEntity summonKickTarget(
            LivingEntity user, ActionTarget picked) {
        if (picked.getType() != ActionTarget.TargetType.ENTITY
                || !(picked.getEntity() instanceof LivingEntity living)
                || living == user
                || !living.isAlive()
                || living.isRemoved()
                || !isWithinSummonKickRange(user, living)) {
            return null;
        }
        return living;
    }

    public static boolean summonKick(
            ServerLevel level, LivingEntity user) {
        if (!(user instanceof ServerPlayer owner)) {
            return false;
        }
        LivingEntity target = summonKickTarget(owner, summonKickPick(owner));
        if (target == null) {
            return false;
        }
        D4CSummonKickEntity kick =
                D4CAddon.SUMMON_KICK_ENTITY.get().create(level);
        if (kick == null) {
            return false;
        }
        kick.initialize(owner, target);
        level.addFreshEntity(kick);
        level.playSound(
                null,
                owner.blockPosition(),
                D4CAddon.D4C_TELEPORT.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.2F);
        spawnKickFlag(
                level,
                owner,
                owner,
                D4CMarkerEntity.Mode.KICK_ORIGIN);
        spawnKickFlag(
                level,
                owner,
                kick,
                D4CMarkerEntity.Mode.KICK_FLAG);
        return true;
    }

    private static void spawnKickFlag(
            ServerLevel level,
            ServerPlayer owner,
            LivingEntity anchor,
            D4CMarkerEntity.Mode mode) {
        D4CMarkerEntity flag =
                D4CAddon.SUMMON_KICK_FLAG.get().create(level);
        if (flag == null) {
            return;
        }
        flag.initialize(
                owner, anchor, mode);
        level.addFreshEntity(flag);
    }

    private static boolean throwOffhand(
            ServerLevel level, LivingEntity user) {
        ItemStack held = user.getOffhandItem();
        if (held.isEmpty()) {
            return false;
        }
        // 1.16 aa/aa/ab.java:73-95
        level.playSound(
                null,
                user.getX(),
                user.getY(),
                user.getZ(),
                SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS,
                1.2F,
                0.8F);
        ItemStack thrown = held.copyWithCount(1);
        held.shrink(1);
        ItemEntity item = new ItemEntity(
                level,
                user.getX(),
                user.getEyeY() - 0.1D,
                user.getZ(),
                thrown);
        Vec3 look = user.getLookAngle();
        item.setDeltaMovement(look.scale(2.2D).add(0.0D, 0.2D, 0.0D));
        item.setThrower(user);
        item.setPickUpDelay(40);
        item.lifespan = 6000;
        if (user instanceof ServerPlayer player) {
            item.setTarget(player.getUUID());
        }
        // Tossed, not parallel-origin: keeps block annihilation too.
        D4CParallelItemService.markPlayerTossed(item);
        level.addFreshEntity(item);
        Vec3 eye = user.getEyePosition();
        for (int step = 0; step < 15; step++) {
            Vec3 at = eye.add(
                    look.x * step * 0.8D,
                    look.y * step * 0.8D + 0.2D * step * 0.5D,
                    look.z * step * 0.8D);
            level.sendParticles(ParticleTypes.CRIT,
                    at.x, at.y, at.z, 2, 0.15D, 0.15D, 0.15D, 0.05D);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    at.x, at.y, at.z, 1, 0.1D, 0.1D, 0.1D, 0.02D);
        }
        return true;
    }

    private static boolean activateLoveTrain(ServerPlayer player) {
        if (!consumeCorpseItem(
                player, D4CAddon.COMPLETE_SAINT_CORPSE.get())) {
            return false;
        }
        player.getPersistentData().putBoolean(LOVE_TRAIN_CORPSE, true);
        // 1.16 aa/ae.java:142: no swirl particles, icon kept.
        player.addEffect(new MobEffectInstance(
                D4CAddon.LOVE_TRAIN,
                D4CPhase2BSpec.LOVE_TRAIN_DURATION_TICKS,
                0,
                false,
                false,
                true));
        player.addEffect(new MobEffectInstance(
                D4CAddon.LUCKY_GODDESS,
                D4CPhase2BSpec.LOVE_TRAIN_DURATION_TICKS,
                0,
                false,
                false,
                true));
        StandPower power = StandPower.get(player);
        if (power != null && power.getSummonedStandEntity() != null) {
            power.getSummonedStandEntity().addEffect(
                    new MobEffectInstance(
                            D4CAddon.LOVE_TRAIN,
                            D4CPhase2BSpec.LOVE_TRAIN_DURATION_TICKS,
                            0,
                            false,
                            false,
                            true));
        }
        player.level().playSound(
                null,
                player.blockPosition(),
                D4CAddon.D4C_LOVE_TRAIN_SUMMON.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        player.level().playSound(
                null,
                player.blockPosition(),
                D4CAddon.VALENTINE_LOVE_TRAIN.get(),
                SoundSource.VOICE,
                1.0F,
                1.0F);
        D4CLoveTrainSync.commit(player);
        notify(player, MSG_LOVE_TRAIN_CONSUME_CORPSE);
        return true;
    }

    private static boolean deactivateLoveTrain(ServerPlayer player) {
        if (!player.hasEffect(D4CAddon.LOVE_TRAIN)) {
            return false;
        }
        player.removeEffect(D4CAddon.LOVE_TRAIN);
        player.removeEffect(D4CAddon.LUCKY_GODDESS);
        player.removeEffect(D4CAddon.LOVE_TRAIN_LIGHT_WALL);
        StandPower power = StandPower.get(player);
        if (power != null && power.getSummonedStandEntity() != null) {
            power.getSummonedStandEntity()
                    .removeEffect(D4CAddon.LOVE_TRAIN);
        }
        D4CLoveTrainSync.cancel(player);
        returnLoveTrainCorpse(player);
        notify(player, MSG_LOVE_TRAIN_MANUAL_EXIT);
        return true;
    }

    private static boolean activateLightWall(LivingEntity user) {
        if (!user.hasEffect(D4CAddon.LOVE_TRAIN)) {
            return false;
        }
        user.addEffect(new MobEffectInstance(
                D4CAddon.LOVE_TRAIN_LIGHT_WALL,
                D4CPhase2BSpec.LIGHT_WALL_DURATION_TICKS,
                0,
                false,
                false,
                true));
        user.level().playSound(
                null,
                user.blockPosition(),
                D4CAddon.D4C_LIGHT.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F);
        notify(user, MSG_LIGHT_WALL_ACTIVATED);
        return true;
    }

    private static boolean deactivateLightWall(LivingEntity user) {
        if (!user.removeEffect(D4CAddon.LOVE_TRAIN_LIGHT_WALL)) {
            return false;
        }
        notify(user, MSG_LIGHT_WALL_DEACTIVATED);
        return true;
    }

    public static void returnLoveTrainCorpse(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(LOVE_TRAIN_CORPSE)) {
            return;
        }
        data.remove(LOVE_TRAIN_CORPSE);
        ItemStack corpse =
                new ItemStack(D4CAddon.COMPLETE_SAINT_CORPSE.get());
        if (!player.getInventory().add(corpse)) {
            player.drop(corpse, false);
        }
    }

    public static boolean ownsLoveTrainCorpse(ServerPlayer player) {
        return player.getPersistentData().getBoolean(LOVE_TRAIN_CORPSE);
    }

    public static boolean hasCorpseItem(
            LivingEntity entity, net.minecraft.world.item.Item item) {
        if (entity instanceof net.minecraft.world.entity.player.Player player
                && player.getInventory().contains(stack -> stack.is(item))) {
            return true;
        }
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(handler -> handler.findFirstCurio(item))
                .isPresent();
    }

    private static boolean consumeCorpseItem(
            ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0;
                slot < player.getInventory().getContainerSize();
                slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.findFirstCurio(item)
                        .map(result -> {
                            handler.setEquippedCurio(
                                    result.slotContext().identifier(),
                                    result.slotContext().index(),
                                    ItemStack.EMPTY);
                            return true;
                        }))
                .orElse(false);
    }

    private static List<D4CMinionEntity> ownedMinions(
            net.minecraft.world.entity.player.Player owner, double radius) {
        return owner.level().getEntitiesOfClass(
                D4CMinionEntity.class,
                owner.getBoundingBox().inflate(radius),
                minion -> minion.isOwnedBy(owner.getUUID()));
    }

    @Nullable
    public static LivingEntity findPullInTarget(LivingEntity user) {
        Vec3 start = user.getEyePosition();
        Vec3 end = start.add(user.getLookAngle().scale(4.0D));
        LivingEntity nearest = null;
        double nearestDistance = 4.0D;
        // 1.16 aa/aj:129-146: raw living boxes; blocks and synced aim do not participate.
        for (LivingEntity candidate : user.level().getEntitiesOfClass(
                LivingEntity.class, new AABB(start, end).inflate(1.0D),
                entity -> entity != user && !entity.isSpectator())) {
            var hit = candidate.getBoundingBox().clip(start, end);
            if (hit.isPresent()) {
                double distance = start.distanceTo(hit.get());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    /**
     * @param wallsHideEntities false: the entity ray runs the whole range
     * through blocks, as 1.16's rayTraceMultipleEntities did
     */
    @Nullable
    public static LivingEntity findLookTarget(
            LivingEntity user, double range, boolean wallsHideEntities) {
        // on the user's own client the aim it sends, as the server reads it
        ActionTarget syncedTarget = LivingComponentAction.getAimTarget(user);
        LivingEntity resolved = resolveLivingTarget(
                user, syncedTarget, range);
        if (resolved != null) {
            return resolved;
        }
        ActionTarget clipped = HitResultUtil.clip(
                user.getEyePosition(),
                user.getLookAngle(),
                range,
                range,
                user.level(),
                entity -> entity instanceof LivingEntity
                        && entity != user
                        && !(entity instanceof StandEntity stand
                                && stand.getUser() == user)
                        && !(entity instanceof D4CMinionEntity)
                        && !(entity instanceof D4CAnnihilationCloneEntity),
                user,
                0.0D,
                wallsHideEntities);
        return resolveLivingTarget(user, clipped, range);
    }

    // The Annihilation mark is laid on the target itself (1.16 aa/ab.java:114,
    // a 15-block entity ray through blocks). The summon kick's phantom
    // appears beside the user and flies to the target, so it needs sight.
    private static boolean markWallsHideEntities(LivingEntity user) {
        return StandSkillWalls.wallsHideEntities(
                user.level(),
                new AbilityId(PowerClass.STAND, D4CAddon.D4C.getId(),
                        D4CAddon.ANNIHILATION_MARK_NAME));
    }

    // The kick runs no action of its own, so the user's rays would take the
    // answer of D4C's running task (a mark's, through walls) instead.
    private static boolean kickWallsHideEntities(LivingEntity user) {
        return StandSkillWalls.wallsHideEntities(
                user.level(),
                new AbilityId(PowerClass.STAND, D4CAddon.D4C.getId(),
                        "summon_kick"));
    }

    @Nullable
    private static LivingEntity resolveLivingTarget(
            LivingEntity user,
            ActionTarget target,
            double range) {
        if (target == null
                || target.isEmpty(user.level())
                || !HitResultUtil.isTargetWithinRange(
                        target,
                        user,
                        user.level(),
                        range,
                        range)) {
            return null;
        }
        Entity entity = target.getMainEntity();
        return entity instanceof LivingEntity living
                && living != user
                && !(entity instanceof StandEntity stand
                        && stand.getUser() == user)
                && living.isAlive()
                && !living.isRemoved()
                ? living
                : null;
    }

    // 1.16 al/aa.java ab(user) :81-91, aa(world, uuid) :142-160: the marked
    // entity in the user's level, a dying one too; only the 20-tick check
    // below drops a mark
    @Nullable
    private static LivingEntity markedTarget(ServerPlayer owner) {
        CompoundTag data = owner.getPersistentData();
        if (!data.hasUUID(ANNIHILATION_TARGET)) {
            return null;
        }
        Entity entity = owner.serverLevel().getEntity(
                data.getUUID(ANNIHILATION_TARGET));
        return entity instanceof LivingEntity living ? living : null;
    }

    // 1.16 al/aa.java onServerTick :162-207: every 20 server ticks a mark was
    // dropped once its marker or its target was gone or dead, the two were
    // in different dimensions or more than 20 blocks apart
    public static void tickMarks(MinecraftServer server) {
        if (server.getTickCount()
                % D4CPhase2BSpec.MARK_CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
            dropLapsedMark(owner);
        }
    }

    static boolean dropLapsedMark(ServerPlayer owner) {
        CompoundTag data = owner.getPersistentData();
        if (!data.hasUUID(ANNIHILATION_TARGET)
                || !markLapsed(owner, data.getUUID(ANNIHILATION_TARGET))) {
            return false;
        }
        data.remove(ANNIHILATION_TARGET);
        return true;
    }

    private static boolean markLapsed(ServerPlayer owner, UUID targetId) {
        LivingEntity target = null;
        for (ServerLevel level : owner.getServer().getAllLevels()) {
            if (level.getEntity(targetId) instanceof LivingEntity living) {
                target = living;
                break;
            }
        }
        return target == null
                || !owner.isAlive()
                || !target.isAlive()
                || !owner.level().dimension().equals(target.level().dimension())
                || owner.distanceTo(target) > D4CPhase2BSpec.MARK_MAX_DISTANCE;
    }

    // a marker that left the server is gone (1.16 dropped its mark then)
    public static void forgetMark(
            net.minecraft.world.entity.player.Player owner) {
        owner.getPersistentData().remove(ANNIHILATION_TARGET);
    }

    // The mark as the user's own client knows it: the entity the server last
    // named (AnnihilationMarkPayload), read with the server's rule.
    private static volatile int clientMarkEntityId = AnnihilationMarkPayload.NONE;
    // what each player's client was last told
    private static final Map<UUID, Integer> SENT_MARKS = new ConcurrentHashMap<>();

    public static void acceptClientMark(int entityId) {
        clientMarkEntityId = entityId;
    }

    public static int clientMarkEntityId() {
        return clientMarkEntityId;
    }

    @Nullable
    private static LivingEntity clientMarkedTarget(
            net.minecraft.world.entity.player.Player player) {
        int id = clientMarkEntityId;
        if (!player.isLocalPlayer() || id == AnnihilationMarkPayload.NONE) {
            return null;
        }
        Entity entity = player.level().getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    // 1.16 al/aa.java:47-79 sent the mark to its player's client whenever it
    // was set or lifted (ak/aa/aa/aa.java)
    public static void syncMarkToClient(ServerPlayer owner) {
        LivingEntity marked = markedTarget(owner);
        int id = marked != null ? marked.getId() : AnnihilationMarkPayload.NONE;
        Integer sent = SENT_MARKS.get(owner.getUUID());
        if ((sent == null ? AnnihilationMarkPayload.NONE : sent) == id
                || owner.connection == null) {
            return;
        }
        SENT_MARKS.put(owner.getUUID(), id);
        PacketDistributor.sendToPlayer(owner, new AnnihilationMarkPayload(id));
    }

    public static void forgetSentMark(UUID player) {
        SENT_MARKS.remove(player);
    }

    public static void clearSentMarks() {
        SENT_MARKS.clear();
    }
}
