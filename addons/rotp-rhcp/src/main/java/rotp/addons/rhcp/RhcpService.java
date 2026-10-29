package rotp.addons.rhcp;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.impl.powers.hamon.abilities.HamonOverdriveBarrageAbility;
import rotp.core.impl.powers.hamon.abilities.HamonOverdriveBeatAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveAbility;
import rotp.core.impl.powers.hamon.abilities.HamonSunlightYellowOverdriveBarrageAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanBladeBarrageAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanBladeSlashAbility;
import rotp.core.impl.powers.pillarman.abilities.PillarmanHeavyPunchAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismClawLacerateAbility;
import rotp.core.impl.powers.zombie.abilities.ZombieClawLacerateAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.subsystems.entity_puppetcontrol.StandManualInput;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.util.functions.DamageUtil;

import rotp.addons.rhcp.ability.RhcpAbility;
import rotp.addons.rhcp.entity.RhcpStandEntity;
import rotp.addons.rhcp.network.RhcpStatePayload;
import rotp.addons.rhcp.state.RhcpState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.TargetBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RhcpService {
    private static final double MELEE_RANGE = 5.0D;
    private static final double DRAG_RANGE = 8.0D;
    private static final double CARRIED_TARGET_MAX_DISTANCE_SQR = 4.0D;
    // 1.16 RHCPRecharge: sources count within 3 blocks of RHCP's box,
    // lightning within 10.
    private static final double SOURCE_RANGE = 3.0D;
    private static final double LIGHTNING_RANGE = 10.0D;

    private RhcpService() {}

    public static RhcpStandEntity activeStand(LivingEntity user) {
        StandPower power = user != null ? StandPower.get(user) : null;
        return power != null
                && power.hasPower()
                && power.isSummoned()
                && power.getPowerType() == RhcpAddon.RHCP.get()
                && power.getSummonedStandEntity()
                        instanceof RhcpStandEntity stand
                && stand.isAlive()
                && !stand.isRemoved()
                ? stand : null;
    }

    public static boolean consume(
            ServerPlayer player, float base, float scaling) {
        return consume(player, base, scaling, true);
    }

    // 1.16 RHCPEntity.consumeEnergy(value, scaling, conversion): what the
    // battery cannot cover comes out of stamina, 1.5 per point with
    // conversion (15 per 10 energy) and 1:1 without.
    public static boolean consume(
            ServerPlayer player, float base, float scaling,
            boolean conversion) {
        RhcpState state = RhcpAddon.state(player);
        StandPower power = StandPower.get(player);
        float cost = energyCost(base, scaling, state.potency(),
                power != null ? power.getResolveLevel() : 0);
        float fromEnergy = Math.min(state.energy(), cost);
        state.setEnergy(state.energy() - fromEnergy);
        float remaining = cost - fromEnergy;
        boolean staminaPaid = remaining <= 0.0F
                || power != null && power.consumeStamina(
                        conversion ? remaining * 1.5F : remaining);
        sync(player);
        return staminaPaid;
    }

    // 1.16 consumeEnergy: value + (Resolve 3+ ? scaling : scaling * 2)
    // * (potency - 1); it never went below zero in practice.
    public static float energyCost(
            float base, float scaling, int potency, int resolveLevel) {
        float perLevel = resolveLevel >= 3 ? scaling : scaling * 2.0F;
        return Math.max(0.0F, base + perLevel * (potency - 1));
    }

    // 1.16 RHCPEntity.getPowerIncrease: half the stat without power, and
    // each potency level past the first adds a quarter of its dev range.
    public static double powerIncrease(
            double stat, int potency, double base, double max) {
        if (potency <= 0) {
            return stat * 0.5D;
        }
        double increase = (max / base - 1.0D) / 4.0D;
        return stat * (1.0D + increase * (potency - 1));
    }

    public static double powerIncrease(
            double stat, int potency, boolean speed) {
        if (potency <= 0) {
            return stat * 0.5D;
        }
        StandStats stats = RhcpAddon.RHCP.get().getStandStats();
        double base = speed
                ? stats.getBaseAttackSpeed() : stats.getBasePower();
        double dev = speed
                ? stats.getDevAttackSpeed(1.0F) : stats.getDevPower(1.0F);
        return powerIncrease(stat, potency, base, base + dev);
    }

    // 1.16 StandEntityDamageSource("stand", rhcp, userPower): the core
    // stand_attack type, the one Stands can be hurt by.
    public static DamageSource standAttack(
            RhcpStandEntity stand, LivingEntity user) {
        DamageSource source = new DamageSource(
                DamageUtil.type(stand.level(), ModDamageTypes.STAND_ATTACK),
                stand);
        if (source instanceof DamageSourceModified modified) {
            modified.jojo_ripples$setStandPower(StandPower.get(user));
        }
        return source;
    }

    // 1.16 RHCPRecharge.checkSpecificConditions: the battery is not full and a
    // lightning bolt, a held redstone torch or block, or a powered block is
    // near RHCP (near the user while RHCP is not out).
    public static boolean canRecharge(LivingEntity user) {
        RhcpStandEntity stand = activeStand(user);
        LivingEntity center = stand != null ? stand : user;
        return RhcpAddon.state(user).energy() < RhcpState.MAX_ENERGY
                && (!sourceLightning(center).isEmpty()
                        || !sourceHolders(center).isEmpty()
                        || !sourceBlocks(center, true).isEmpty());
    }

    /**
     * 1.16 RHCPRecharge.energyCharge: the held recharge (passive false) and
     * RHCP's own charging (passive true, RhcpStandEntity.tick). A lightning
     * bolt within 8 fills the battery and adds a potency level. Otherwise,
     * below Resolve 4 with standPos (the conductor block RHCP is in), half
     * that block's signal; else 7.5 (passive 2.5) for each redstone torch or
     * block held within 3 blocks, and half (passive a sixth) the signal of
     * every powered block within 3 blocks of RHCP's box.
     */
    public static void energyCharge(
            ServerPlayer player,
            RhcpStandEntity stand,
            @Nullable BlockPos standPos,
            boolean passive) {
        RhcpState state = RhcpAddon.state(player);
        if (state.energy() >= RhcpState.MAX_ENERGY) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (LightningBolt bolt : sourceLightning(stand)) {
            if (stand.distanceTo(bolt) <= 8.0F) {
                int potency = state.potency();
                state.setEnergy(RhcpState.MAX_ENERGY);
                bolt.discard();
                if (potency < 5) {
                    state.setPotency(potency + 1);
                }
                sync(player);
                emitElectricParticles(level, stand, 30);
                return;
            }
        }
        StandPower power = StandPower.get(player);
        float gain = 0.0F;
        if (standPos != null && power != null && power.getResolveLevel() < 4) {
            gain = signal(level, standPos) / 2.0F;
        }
        else {
            for (LivingEntity holder : sourceHolders(stand)) {
                if (generatesEnergy(holder.getMainHandItem())) {
                    gain += heldSourceCharge(passive);
                }
                if (generatesEnergy(holder.getOffhandItem())) {
                    gain += heldSourceCharge(passive);
                }
            }
            for (BlockPos pos : sourceBlocks(stand, false)) {
                gain += signalCharge(signal(level, pos), passive);
            }
        }
        if (gain <= 0.0F) {
            return;
        }
        int potency = state.potency();
        // RHCP's own trickle reaches the client with the periodic sync.
        if (state.addEnergy(gain) && (!passive || state.potency() != potency)) {
            sync(player);
        }
        if (!passive) {
            emitElectricParticles(level, stand, 8);
        }
    }

    // 1.16 energyCharge: a held redstone torch or block gives 7.5 a tick to
    // the held recharge, 2.5 to RHCP's own charging.
    public static float heldSourceCharge(boolean passive) {
        return passive ? 2.5F : 7.5F;
    }

    // 1.16 energyCharge: a powered block gives half its signal a tick to the
    // held recharge, a sixth to RHCP's own charging.
    public static float signalCharge(float signal, boolean passive) {
        return passive ? signal / 6.0F : signal / 2.0F;
    }

    // 1.16 RHCPEntity.setPotency: the Stand's movement speed is +20% a
    // potency level, -88% without power.
    public static double potencySpeedBonus(int potency) {
        return potency > 0 ? 0.2D * potency : -0.88D;
    }

    private static List<LightningBolt> sourceLightning(LivingEntity center) {
        return center.level().getEntitiesOfClass(
                LightningBolt.class,
                center.getBoundingBox().inflate(LIGHTNING_RANGE),
                Entity::isAlive);
    }

    private static List<LivingEntity> sourceHolders(LivingEntity center) {
        return center.level().getEntitiesOfClass(
                LivingEntity.class,
                center.getBoundingBox().inflate(SOURCE_RANGE),
                living -> living.isAlive()
                        && (generatesEnergy(living.getMainHandItem())
                                || generatesEnergy(living.getOffhandItem())));
    }

    // 1.16 RHCPRecharge.TheItemGeneratesEnergy: a redstone torch or block.
    private static boolean generatesEnergy(ItemStack stack) {
        return stack.is(Items.REDSTONE_TORCH) || stack.is(Items.REDSTONE_BLOCK);
    }

    // 1.16 RHCPRecharge.getSourceBlocks: the blocks in RHCP's box grown by 3
    // that give off a signal.
    private static List<BlockPos> sourceBlocks(LivingEntity center, boolean first) {
        Level level = center.level();
        AABB area = center.getBoundingBox().inflate(SOURCE_RANGE);
        BlockPos min = BlockPos.containing(
                area.minX + 0.001D, area.minY + 0.001D, area.minZ + 0.001D);
        BlockPos max = BlockPos.containing(
                area.maxX - 0.001D, area.maxY - 0.001D, area.maxZ - 0.001D);
        List<BlockPos> blocks = new ArrayList<>();
        if (!level.hasChunksAt(min, max)) {
            return blocks;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (signal(level, pos) > 0.0F) {
                blocks.add(pos.immutable());
                if (first) {
                    break;
                }
            }
        }
        return blocks;
    }

    // 1.16 RHCPRecharge.getSignalFromBlock: the strongest signal the block
    // gives off to any side.
    private static float signal(Level level, BlockPos pos) {
        int signal = 0;
        for (Direction direction : Direction.values()) {
            signal = Math.max(signal, level.getSignal(pos, direction));
            if (signal >= 15) {
                break;
            }
        }
        return signal;
    }

    public static boolean enterConductor(ServerPlayer player) {
        RhcpStandEntity stand = activeStand(player);
        if (stand == null || RhcpAddon.state(player).inConductor()) {
            return false;
        }
        BlockPos pos = pressedBlock(player);
        if (pos == null || !pressedBlockInRange(player, pos)
                || !isConductor(stand.level().getBlockState(pos))) {
            return false;
        }
        RhcpAddon.state(player).enterConductor(pos);
        // 1.16 RHCPGettingIntoTheMechanism.EnterTheBlock :269
        stand.setRecentBlock(pos);
        teleportStand(stand, conductorSpot(pos));
        setTravelling(stand, true);
        playStandSound(player, stand, RhcpAddon.RHCP_ENTERING.get(), 1.0F);
        sync(player);
        return true;
    }

    public static void leaveConductor(ServerPlayer player) {
        RhcpState state = RhcpAddon.state(player);
        if (!state.inConductor()) {
            return;
        }
        RhcpStandEntity stand = activeStand(player);
        LivingEntity carried = carriedTarget(player, state);
        boolean carriedWasNoPhysics = state.carriedTargetWasNoPhysics();
        if (carried != null) {
            carried.noPhysics = carriedWasNoPhysics;
            if (stand != null) {
                Vec3 exit = stand.position().add(1.0D, 0.0D, 0.0D);
                carried.teleportTo(exit.x, exit.y, exit.z);
                carried.setDeltaMovement(Vec3.ZERO);
            }
        }
        state.leaveConductor();
        if (stand != null) {
            // 1.16 RHCPExit.standPerform :62-68: RHCP stays where it is,
            // and the block it leaves is its recent block.
            stand.setRecentBlock(stand.blockPosition());
            setTravelling(stand, false);
        }
        playStandSound(player, stand, RhcpAddon.RHCP_ENTERING.get(), 0.8F);
        sync(player);
    }

    // 1.16 RHCPGettingIntoTheMechanism (TargetRequirement.BLOCK, no
    // overrideVanillaMouseTarget) took the mouse target as it was: the
    // vanilla pick of the camera entity, RHCP under manual control, else its
    // user. It meets blocks by their outlines (a redstone torch, wire,
    // button or lever is pressed itself) within the player's block reach
    // (4.5, 5 in Creative), and an entity before the block within the
    // entity reach (3, 5 in Creative) is the target instead, which a BLOCK
    // action refused. With RHCP out, a pick that met nothing became RHCP's
    // precision ray from the camera entity (EntityStandType.clientHitResult),
    // 5 blocks from its user. A mob user presses at its aim.
    @Nullable
    public static BlockPos pressedBlock(LivingEntity user) {
        ActionTarget target = pressedBlockTarget(user);
        return target.getType() == ActionTarget.TargetType.BLOCK
                ? target.getBlockPos() : null;
    }

    /**
     * The block {@link #pressedBlock} gives, with the face the pick met, or
     * EMPTY. A player's is its pick on either side (the HUD and its client's
     * press check run it too); only a mob user, whose checks run on the
     * server alone, reads the stored aim.
     */
    public static ActionTarget pressedBlockTarget(LivingEntity user) {
        HitResult pick;
        if (user instanceof Player player) {
            RhcpStandEntity stand = activeStand(player);
            pick = vanillaPick(player,
                    stand != null && stand.isManuallyControlled()
                            ? stand : player,
                    stand);
            if (stand != null && pick.getType() == HitResult.Type.MISS) {
                ActionTarget aim = standAimBehindMiss(player, stand);
                return aim.getType() == ActionTarget.TargetType.BLOCK
                        && aim.getBlockPos() != null
                        ? aim : ActionTarget.EMPTY;
            }
        }
        else {
            var aim = LivingComponentAction.getAim(user);
            ActionTarget target = aim != null
                    ? aim.getTarget() : ActionTarget.EMPTY;
            return target.getType() == ActionTarget.TargetType.BLOCK
                    && target.getBlockPos() != null
                    ? target : ActionTarget.EMPTY;
        }
        return pick instanceof BlockHitResult block
                && block.getType() == HitResult.Type.BLOCK
                ? new ActionTarget(block.getBlockPos(), block.getDirection())
                : ActionTarget.EMPTY;
    }

    // 1.16 StandEntity.precisionRayTrace(cameraEntity) at getAimDistance:
    // RHCP's own reach under manual control, else its user's. Not the
    // fallback to RHCP's own look that the server-side aim adds.
    private static ActionTarget standAimBehindMiss(
            Player player, RhcpStandEntity stand) {
        if (stand.isManuallyControlled()) {
            return StandEntityPunchAbility.manualStandAim(
                    stand, ActionTarget.EMPTY);
        }
        if (!StandEntityPunchAbility.aimAssist(player)) {
            return StandPrecisionRay.crosshair(player, stand,
                    StandPrecisionRay.reach(player, stand), true);
        }
        return StandEntityPunchAbility.userPrecisionRayTarget(
                stand, player, StandPrecisionRay.canTarget(stand));
    }

    // 1.16 Action.checkRangeAndTarget: the pressed block within 10 blocks
    // (getMaxRangeSqBlockTarget 100) of the performer, the Stand while it
    // is out, else "target_too_far".
    public static boolean pressedBlockInRange(LivingEntity user, BlockPos pos) {
        return ActionTargetRange.isBlockWithinRange(
                ActionTargetRange.standPerformer(user),
                user.level(),
                pos,
                ActionTargetRange.DEFAULT_BLOCK_RANGE_SQ);
    }

    // Minecraft's GameRenderer.pick. The user and RHCP are not picked: the
    // port keeps a press whose ray met its own user (an owner question).
    private static HitResult vanillaPick(
            Player player, Entity camera, @Nullable RhcpStandEntity stand) {
        double blockRange = player.blockInteractionRange();
        double entityRange = player.entityInteractionRange();
        double range = Math.max(blockRange, entityRange);
        Vec3 eyes = camera.getEyePosition();
        HitResult hit = camera.pick(range, 1.0F, false);
        double hitDistanceSqr = hit.getLocation().distanceToSqr(eyes);
        double entityRay = hit.getType() != HitResult.Type.MISS
                ? Math.sqrt(hitDistanceSqr) : range;
        Vec3 look = camera.getViewVector(1.0F);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                camera,
                eyes,
                eyes.add(look.scale(entityRay)),
                camera.getBoundingBox()
                        .expandTowards(look.scale(entityRay))
                        .inflate(1.0D),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != player
                        && entity != stand
                        && (!(entity instanceof StandEntity)
                                || StandUtil.entityCanSeeStands(player)),
                entityRay * entityRay);
        if (entityHit != null
                && entityHit.getLocation().distanceToSqr(eyes)
                        < hitDistanceSqr) {
            return withinReach(entityHit, eyes, entityRange);
        }
        return withinReach(hit, eyes, blockRange);
    }

    private static HitResult withinReach(HitResult hit, Vec3 eyes, double reach) {
        Vec3 at = hit.getLocation();
        if (at.closerThan(eyes, reach)) {
            return hit;
        }
        return BlockHitResult.miss(at,
                Direction.getNearest(
                        at.x - eyes.x, at.y - eyes.y, at.z - eyes.z),
                BlockPos.containing(at));
    }

    // 1.16 RHCPGettingIntoTheMechanism.EnterTheBlock :270-271: RHCP rests
    // on the block's bottom centre, 0.01 up.
    public static Vec3 conductorSpot(BlockPos pos) {
        return new Vec3(
                pos.getX() + 0.5D, pos.getY() + 0.01D, pos.getZ() + 0.5D);
    }

    // 1.16 canTravel(true / false) with setNoGravity(standHasNoGravity())
    // in EnterTheBlock and RHCPExit :78, at once: the Stand tick only
    // catches up after its own movement, which would let RHCP fall a tick.
    private static void setTravelling(RhcpStandEntity stand, boolean inside) {
        stand.noPhysics = inside;
        stand.setNoGravity(inside);
        if (inside) {
            stand.setDeltaMovement(Vec3.ZERO);
        }
    }

    // 1.16 RHCPExit.checkSpecificConditions :49-50: RHCP's block has the
    // full cube shape.
    public static boolean inFullBlock(RhcpStandEntity stand) {
        BlockPos pos = stand.blockPosition();
        return stand.level().getBlockState(pos)
                .getShape(stand.level(), pos) == Shapes.block();
    }

    public static LivingEntity findDragTarget(LivingEntity user) {
        RhcpStandEntity stand = activeStand(user);
        if (stand == null || !RhcpAddon.state(user).inConductor()) {
            return null;
        }
        Vec3 look = user.getViewVector(1.0F).normalize();
        Vec3 eye = user.getEyePosition(1.0F);
        for (LivingEntity candidate : user.level().getEntitiesOfClass(
                LivingEntity.class,
                user.getBoundingBox().inflate(DRAG_RANGE),
                entity -> entity.isAlive()
                        && entity != user
                        && entity != stand)) {
            Vec3 targetCenter = candidate.position().add(
                    0.0D, candidate.getBbHeight() * 0.5D, 0.0D);
            Vec3 direction = targetCenter.subtract(eye);
            if (direction.length() <= DRAG_RANGE
                    && look.dot(direction.normalize()) > 0.5D
                    && stand.distanceTo(candidate) <= DRAG_RANGE) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean dragIntoLine(ServerPlayer player) {
        RhcpState state = RhcpAddon.state(player);
        RhcpStandEntity stand = activeStand(player);
        LivingEntity target = findDragTarget(player);
        if (stand == null || target == null || !state.inConductor()) {
            return false;
        }

        releaseCarriedTarget(player, state);
        Vec3 standPosition = stand.position();
        target.teleportTo(
                standPosition.x, standPosition.y, standPosition.z);
        target.setDeltaMovement(Vec3.ZERO);
        state.setCarriedTarget(target.getId(), target.noPhysics);
        DamageUtil.hurtThroughInvulTicks(
                target,
                standAttack(stand, player),
                60.0F);
        emitElectricParticles(player.serverLevel(), stand, 50);
        return true;
    }

    public static void tickCarriedTarget(
            ServerPlayer player, RhcpStandEntity stand) {
        RhcpState state = RhcpAddon.state(player);
        LivingEntity target = carriedTarget(player, state);
        if (target == null
                || !target.isAlive()
                || !state.inConductor()
                || stand.distanceToSqr(target)
                        >= CARRIED_TARGET_MAX_DISTANCE_SQR) {
            releaseCarriedTarget(player, state);
            return;
        }

        target.noPhysics = true;
        target.teleportTo(stand.getX(), stand.getY(), stand.getZ());
        target.setDeltaMovement(Vec3.ZERO);
        target.addEffect(new MobEffectInstance(
                ModStatusEffects.FULL_INVISIBILITY,
                10,
                0,
                false,
                false,
                false));
        target.addEffect(new MobEffectInstance(
                ModStatusEffects.IMMOBILIZE,
                10,
                0,
                false,
                false,
                true));
        DamageUtil.hurtThroughInvulTicks(
                target,
                standAttack(stand, player),
                0.7F);
    }

    private static LivingEntity carriedTarget(
            ServerPlayer player, RhcpState state) {
        Entity entity = state.carriedTargetId() >= 0
                ? player.serverLevel().getEntity(state.carriedTargetId())
                : null;
        return entity instanceof LivingEntity living ? living : null;
    }

    private static void releaseCarriedTarget(
            ServerPlayer player, RhcpState state) {
        LivingEntity target = carriedTarget(player, state);
        if (target != null) {
            target.noPhysics = state.carriedTargetWasNoPhysics();
        }
        state.clearCarriedTarget();
    }

    // 1.16 RHCPGettingIntoTheMechanism.isConductorBlock (conductorBlock and
    // gadgetBlock): redstone parts and the blocks they work. The lightning rod
    // and blocks with a comparator output are the port's.
    public static boolean isConductor(BlockState state) {
        Block block = state.getBlock();
        return block instanceof RedStoneWireBlock
                || state.is(Blocks.REDSTONE_BLOCK)
                || block instanceof DiodeBlock
                || block instanceof RedstoneTorchBlock
                || block instanceof TargetBlock
                || block instanceof LecternBlock
                || block instanceof TrappedChestBlock
                || block instanceof ButtonBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof HopperBlock
                || block instanceof TripWireHookBlock
                || block instanceof LeverBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof DirectionalBlock
                        && !(block instanceof EndRodBlock)
                || block instanceof DaylightDetectorBlock
                || block instanceof DispenserBlock
                || block instanceof RedstoneLampBlock
                || block instanceof TntBlock
                || block instanceof DetectorRailBlock
                || block instanceof PoweredRailBlock
                || block instanceof NoteBlock
                || state.is(Blocks.LIGHTNING_ROD)
                || state.hasAnalogOutputSignal();
    }

    public static BlockPos nearestConductor(
            Level level, BlockPos origin, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-radius, -radius, -radius),
                origin.offset(radius, radius, radius))) {
            if (!isConductor(level.getBlockState(candidate))) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return best;
    }

    // 1.16 StandEntity.aimWithThisOrUser: unless it is manually controlled,
    // RHCP hits what its user aims at (the synced Stand aim, else a ray from
    // the user's eye), then what it faces itself.
    public static LivingEntity target(
            RhcpStandEntity stand, double range) {
        LivingEntity user = stand.getUser();
        if (!stand.isManuallyControlled() && user != null) {
            var aim = LivingComponentAction.getAim(stand);
            if (aim != null
                    && aim.getTarget().getMainEntity()
                            instanceof LivingEntity aimed
                    && isMeleeTarget(stand, aimed)
                    && stand.distanceTo(aimed) <= range) {
                return aimed;
            }
            LivingEntity fromUser = clipTarget(stand, user,
                    user.getEyePosition(), user.getLookAngle(), range);
            if (fromUser != null && stand.distanceTo(fromUser) <= range) {
                return fromUser;
            }
        }
        return clipTarget(stand, stand,
                stand.getEyePosition(), stand.getLookAngle(), range);
    }

    private static boolean isMeleeTarget(
            RhcpStandEntity stand, Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && living != stand
                && living != stand.getUser();
    }

    @Nullable
    private static LivingEntity clipTarget(
            RhcpStandEntity stand, Entity aiming,
            Vec3 eye, Vec3 look, double range) {
        ActionTarget aimed = HitResultUtil.clip(
                eye,
                look,
                range,
                range,
                stand.level(),
                entity -> isMeleeTarget(stand, entity),
                aiming,
                0.0D);
        return aimed.getMainEntity() instanceof LivingEntity living
                ? living : null;
    }

    public static boolean attack(
            ServerPlayer player,
            float damage,
            float energyCost,
            float knockback,
            boolean electrocute) {
        return attack(player, damage, energyCost, knockback, electrocute, false);
    }

    /**
     * @param heavyAttack a 1.16 HeavyPunchInstance hit: when it hurts a Stand,
     *        a barrage that Stand performs goes into recovery
     */
    public static boolean attack(
            ServerPlayer player,
            float damage,
            float energyCost,
            float knockback,
            boolean electrocute,
            boolean heavyAttack) {
        return attack(player, damage, energyCost, knockback, electrocute,
                heavyAttack, null);
    }

    /**
     * @param counterTarget the attacker a counterattack or blitz answers
     *        (1.16 set it as the task target); null hits the held lock in
     *        reach (the chase target), else what RHCP aims at
     */
    public static boolean attack(
            ServerPlayer player,
            float damage,
            float energyCost,
            float knockback,
            boolean electrocute,
            boolean heavyAttack,
            @Nullable LivingEntity counterTarget) {
        RhcpStandEntity stand = activeStand(player);
        if (stand == null || RhcpAddon.state(player).inConductor()) {
            return false;
        }
        LivingEntity target = counterTarget != null
                && counterTarget.isAlive()
                && inMeleeReach(stand, counterTarget)
                        ? counterTarget : null;
        if (target == null && counterTarget == null) {
            // 1.16 RHCPChaseTarget / RHCPPunch.onTaskSet: the lock
            // (getTargetHit) is the task target, not the user's aim.
            target = lockedTarget(player, stand);
        }
        if (target == null) {
            target = target(stand, MELEE_RANGE);
        }
        if (target == null) {
            consume(player, energyCost, energyCost * 0.5F);
            return false;
        }
        int potency = RhcpAddon.state(player).potency();
        // 1.16 punches scaled with getAttackDamage, which potency raised
        // (or halved at 0) through getPowerIncrease.
        float scaled = (float) powerIncrease(damage, potency, false);
        // 1.16 hit through StandEntityDamageSource and hurtTarget, as core
        // Stands do: the stand_attack type, through invulnerability ticks.
        DamageSource source = standAttack(stand, player);
        boolean hurt = EntityActionInstance.standEntityAttack(
                stand, target, source, scaled);
        if (hurt && knockback > 0.0F) {
            target.knockback(
                    knockback,
                    stand.getX() - target.getX(),
                    stand.getZ() - target.getZ());
        }
        // 1.16 IHasStandPunch.playPunchImpactSound: a hit plays the
        // punch's impact sound at the target.
        if (hurt) {
            playImpactSound(player, stand, target, impactSound(
                    RhcpAbility.modeOf(stand.getCurStandAction()),
                    stand.isAttackAlternate()));
        }
        consume(player, energyCost, energyCost * 0.5F);
        afterCoreHit(player, stand, target, hurt, electrocute, heavyAttack);
        return hurt;
    }

    public static boolean inMeleeReach(StandEntity stand, LivingEntity target) {
        return stand.distanceTo(target) <= MELEE_RANGE;
    }

    // 1.16 RHCPPunch / RHCPHeavyPunch / RHCPBarrage.onTaskSet: a held lock
    // (getTargetHit) becomes the task target; here, when it is in reach.
    @Nullable
    public static LivingEntity lockedTarget(ServerPlayer player, StandEntity stand) {
        LivingEntity locked = counterTarget(player);
        return locked != null && inMeleeReach(stand, locked) ? locked : null;
    }

    /**
     * After an RHCP hit (the core punch, barrage and heavy punch, and the
     * combo and chase): the port's electric extra, the target lock the
     * follow-ups read, and the sparks.
     *
     * @param heavyAttack a 1.16 HeavyPunchInstance hit that the core action
     *        does not already report
     */
    public static void afterCoreHit(
            ServerPlayer player,
            RhcpStandEntity stand,
            LivingEntity target,
            boolean hurt,
            boolean electrocute,
            boolean heavyAttack) {
        int potency = RhcpAddon.state(player).potency();
        if (hurt && electrocute && potency > 0) {
            target.hurt(
                    standAttack(stand, player),
                    0.3F + 0.1F * Math.max(0, potency - 1));
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    10 + potency * 4,
                    Math.min(3, potency - 1),
                    false,
                    true,
                    true));
        }
        // 1.16 RHCPComboPunch.punchEntity: above potency 3 the combo locks on
        // for 20 ticks (getTargetHit). The heavy's hit is the port's
        // electrocute target; other hits set no lock, as in 1.16.
        RhcpAbility.Mode mode = RhcpAbility.modeOf(stand.getCurStandAction());
        boolean comboLock = mode == RhcpAbility.Mode.COMBO_PUNCH && potency > 3;
        if (hurt && (comboLock || mode == RhcpAbility.Mode.HEAVY_PUNCH)) {
            RhcpAddon.state(player).setTargetId(target.getId());
            if (comboLock) {
                stand.setStandCount(20);
            }
            sync(player);
        }
        // 1.16 HeavyPunchInstance.afterAttack: stopOnHeavyAttack.
        if (hurt && heavyAttack) {
            EntityActionAbility.onHitByHeavyAttack(target);
        }
        emitElectricParticles(player.serverLevel(), stand, hurt ? 10 : 3);
    }

    /**
     * 1.16 GameplayEventHandler.onLivingHurt with RHCPDodge.onStandAttacked:
     * while RHCP dodges (or counters), a hit from a Stand or from a living
     * with integrated_stand that is not more than half a point faster than
     * RHCP misses, and a dodging RHCP answers it with the blitz (Shift, 3+
     * faster), a dash behind the attacker (Shift, 1.5+ faster) or the
     * counterattack (0.5+ faster).
     *
     * @return whether the hit is dodged
     */
    public static boolean dodgeStandHit(
            ServerPlayer player,
            RhcpStandEntity stand,
            DamageSource source) {
        EntityActionInstance action = stand.getCurStandAction();
        RhcpAbility.Mode mode = RhcpAbility.modeOf(action);
        boolean evading = mode == RhcpAbility.Mode.DODGE
                        && action.getPhase() != ActionPhase.RECOVERY
                || mode == RhcpAbility.Mode.COUNTERATTACK
                || mode == RhcpAbility.Mode.BLITZ_ATTACK;
        if (!evading
                || !(source.getDirectEntity() instanceof LivingEntity attacker)
                || !isStandAttacker(attacker)
                || attacker == stand
                || source.is(DamageTypeTags.IS_EXPLOSION)
                || stand.invulnerableTime > 0
                || player.invulnerableTime > 0) {
            return false;
        }
        double difference = speedDifference(stand, attacker);
        if (difference < -0.5D) {
            return false;
        }
        if (mode == RhcpAbility.Mode.DODGE) {
            // 1.16 read the user's sneak; manual control sends it apart.
            boolean shift = StandManualInput.isShiftHeld(player, stand);
            float cost = counterCost(stand, attacker);
            if (difference > 3.0D && shift) {
                stand.setAttackAlternate(true);
                startCounter(player, stand, "rhcp_blitz_attack", attacker);
            }
            else if (difference > 1.5D && shift) {
                dashBehind(player, attacker);
                // 1.16: the attacker loses its target, a Stand's task
                // (setTaskTarget(new ActionTarget(null))) or a mob's.
                if (attacker instanceof StandEntity attackerStand) {
                    attackerStand.setTaskTarget(null);
                }
                else if (attacker instanceof Mob mob) {
                    mob.setTarget(null);
                }
                // 1.16: rhcp.setRot(attacker.yRot, rhcp.xRot)
                stand.setYRot(attacker.getYRot());
                stand.setYHeadRot(attacker.getYRot());
                stand.setYBodyRot(attacker.getYRot());
            }
            else if (difference > 0.5D) {
                startCounter(player, stand, "rhcp_counterattack", attacker);
                // 1.16: electricDash(target, true, true, 0, yRot); the
                // counter keeps the attacker it was set on.
                sidestep(player, stand, 0, attacker.getYRot(), null);
                if (cost > 0.0F) {
                    consume(player, cost, 0.0F);
                }
            }
            else if (isBarrageAttacker(attacker)) {
                // 1.16: against a barrage RHCP steps clear (dodge 2).
                sidestep(player, stand, 2, attacker.getYRot(), attacker);
            }
            else {
                sidestep(player, stand, 0, attacker.getYRot(), attacker);
                if (cost > 0.0F) {
                    consume(player, cost, 0.0F);
                }
            }
        }
        return true;
    }

    // 1.16 RHCPDodge.speedDifference against a Stand: one point more against
    // a heavy attack, half a point less against a barrage.
    public static double speedDifference(
            StandEntity rhcp, StandEntity attacker) {
        EntityActionInstance action = attacker.getCurStandAction();
        return speedDifference(rhcp.getAttackSpeed(),
                attacker.getAttackSpeed(),
                isHeavyAttack(action), isBarrage(action));
    }

    public static double speedDifference(
            double rhcpSpeed, double attackerSpeed,
            boolean heavy, boolean barrage) {
        double difference = rhcpSpeed - attackerSpeed;
        return heavy ? difference + 1.0D
                : barrage ? difference - 0.5D : difference;
    }

    // 1.16 StandEntityHeavyAttack / StandEntityMeleeBarrage tasks.
    private static boolean isHeavyAttack(
            @Nullable EntityActionInstance action) {
        return action != null
                && (action.ability instanceof StandEntityHeavyPunchAbility
                        || action.ability instanceof RhcpAbility rhcp
                                && rhcp.isHeavyAttack());
    }

    private static boolean isBarrage(
            @Nullable EntityActionInstance action) {
        return action != null
                && (action.ability instanceof StandEntityBarrageAbility
                        || action.ability instanceof EntityActionAbility ability
                                && ability.stopsOnHeavyAttack(action));
    }

    // 1.16 RHCPDodge.onStandAttacked: a Stand, or a living holding
    // integrated_stand (RHCPDodge.hasIntegratedStand), looked up by id.
    public static boolean isStandAttacker(LivingEntity attacker) {
        return attacker instanceof StandEntity
                || BuiltInRegistries.MOB_EFFECT.getHolder(INTEGRATED_STAND)
                        .map(attacker::hasEffect).orElse(false);
    }

    private static final ResourceKey<MobEffect> INTEGRATED_STAND =
            ResourceKey.create(Registries.MOB_EFFECT,
                    ResourceLocation.fromNamespaceAndPath(
                            "jojo_ripples", "integrated_stand"));

    // 1.16 RHCPDodge.hasIntegratedStand(LivingEntity), same id lookup.
    private static boolean hasIntegratedStand(LivingEntity living) {
        return BuiltInRegistries.MOB_EFFECT.getHolder(INTEGRATED_STAND)
                .map(living::hasEffect).orElse(false);
    }

    // 1.16 GameplayEventHandler: a Stand's barrage, or for a living without
    // a Stand entity RHCPDodge.noStandBarrage.
    private static boolean isBarrageAttacker(LivingEntity attacker) {
        return attacker instanceof StandEntity standAttacker
                ? isBarrage(standAttacker.getCurStandAction())
                : isNoStandBarrage(livingAction(attacker));
    }

    @Nullable
    private static EntityActionInstance livingAction(LivingEntity entity) {
        LivingComponentAction component =
                LivingComponentAction.getExistingComponent(entity);
        return component != null ? component.getAction() : null;
    }

    // 1.16 RHCPDodge.noStandBarrage: a held Hamon overdrive, Sunlight
    // Yellow Overdrive or Pillar Man blade barrage.
    public static boolean isNoStandBarrage(
            @Nullable EntityActionInstance action) {
        return action != null
                && (action.ability instanceof HamonOverdriveBarrageAbility
                        || action.ability
                                instanceof HamonSunlightYellowOverdriveBarrageAbility
                        || action.ability instanceof PillarmanBladeBarrageAbility);
    }

    // 1.16 RHCPDodge.speedDifference heavy hits of a living without a Stand
    // entity: Hamon beat, Sunlight Yellow / Scarlet Overdrive, vampire and
    // zombie claw lacerate, Pillar Man heavy punch and blade slash.
    public static boolean isNoStandHeavy(
            @Nullable EntityActionInstance action) {
        return action != null
                && (action.ability instanceof HamonOverdriveBeatAbility
                        || action.ability
                                instanceof HamonSunlightYellowOverdriveAbility
                        || action.ability instanceof VampirismClawLacerateAbility
                        || action.ability instanceof ZombieClawLacerateAbility
                        || action.ability instanceof PillarmanHeavyPunchAbility
                        || action.ability instanceof PillarmanBladeSlashAbility);
    }

    // 1.16 RHCPDodge.noStandSpeed: player attack speed, or 16x movement
    // speed; a Stand's own attack speed.
    public static double attackerSpeed(LivingEntity attacker) {
        return attacker instanceof StandEntity standAttacker
                ? standAttacker.getAttackSpeed()
                : attacker instanceof Player
                        ? attacker.getAttributeValue(Attributes.ATTACK_SPEED)
                        : attacker.getAttributeValue(Attributes.MOVEMENT_SPEED)
                                * 16.0D;
    }

    // 1.16 RHCPDodge.additionalCost: how much faster the attacker is than
    // RHCP's unpowered speed (RHCPEntity.minimumSpeed), over 1.25.
    private static float counterCost(
            RhcpStandEntity rhcp, LivingEntity attacker) {
        double minimum = RhcpAddon.RHCP.get().getStandStats()
                .getBaseAttackSpeed() * rhcp.getStandEfficiency();
        return (float) ((attackerSpeed(attacker) - minimum) / 1.25D);
    }

    // 1.16 RHCPEntity.counterattack: the Stand drops its task and starts
    // the counter at its windup, with the attacker as its target.
    private static boolean startCounter(
            ServerPlayer player,
            RhcpStandEntity stand,
            String abilityName,
            LivingEntity attacker) {
        StandPower power = StandPower.get(player);
        if (power == null
                || !(power.getAbility(abilityName)
                        instanceof EntityActionType type)) {
            return false;
        }
        RhcpAddon.state(player).setTargetId(attacker.getId());
        EntityActionInstance counter = type.createActionObj();
        type.initActionFromConfig(counter, stand.level(), player, stand);
        counter.setStartingPhase();
        LivingComponentAction.getComponent(stand).setAction(
                counter, player, SyncType.TRACKING_AND_SELF);
        // 1.16 GameplayEventHandler: standCount = 20 with the counter.
        stand.setStandCount(20);
        sync(player);
        return true;
    }

    // The target a counterattack or blitz answers (1.16 getTargetHit).
    @Nullable
    public static LivingEntity counterTarget(ServerPlayer player) {
        Entity entity = player.level().getEntity(
                RhcpAddon.state(player).targetId());
        return entity instanceof LivingEntity living && living.isAlive()
                ? living : null;
    }

    // 1.16 electricDash with Shift: RHCP lands behind the target, or behind
    // a Stand's user in dash reach; the dash does not turn it.
    public static boolean dashBehind(
            ServerPlayer player, LivingEntity target) {
        return electricDash(player, new ActionTarget(target), true, true);
    }

    // 1.16 electricDash without Shift: RHCP lands in front of the target
    // along its own look.
    public static boolean dashTo(ServerPlayer player, LivingEntity target) {
        return electricDash(player, new ActionTarget(target), false, true);
    }

    /**
     * 1.16 RHCPEntity.tick while RHCP_DODGE runs: the nearest enemy Stand
     * projectile close enough (2.2 to 3 blocks, farther for faster ones) and
     * not faster than RHCP makes it, with Shift and the shooter in dash
     * reach, dash at the shooter (1.5+ faster: behind it, then side-step;
     * 3+ faster: blitz), and otherwise side-step out of its path.
     */
    public static void evadeProjectiles(
            ServerPlayer player, RhcpStandEntity stand) {
        Vec3 center = stand.getBoundingBox().getCenter();
        Projectile nearest = null;
        for (Projectile projectile : stand.level().getEntitiesOfClass(
                Projectile.class,
                stand.getBoundingBox().inflate(2.0D),
                projectile -> (isStandProjectile(projectile)
                                || integratedStandOwner(projectile) != null)
                        && isEnemyProjectile(stand, projectile)
                        && center.distanceToSqr(projectile.position())
                                < projectileEvadeRadiusSqr(projectile
                                        .getDeltaMovement().lengthSqr()))) {
            if (nearest == null
                    || stand.position().distanceTo(projectile.position())
                            < stand.position().distanceTo(nearest.position())) {
                nearest = projectile;
            }
        }
        if (nearest == null) {
            return;
        }
        double projectileSpeed = nearest instanceof ModdedProjectileEntity modded
                ? modded.getSpeedFactor()
                : nearest.getDeltaMovement().normalize().length();
        if (stand.getAttackSpeed() < projectileSpeed) {
            return;
        }
        // 1.16: a Stand projectile's owner, else a shooter holding
        // integrated_stand.
        LivingEntity owner = nearest instanceof ModdedProjectileEntity modded
                ? modded.getOwner() : integratedStandOwner(nearest);
        float yRot = nearest.getYRot();
        if (owner != null && StandManualInput.isShiftHeld(player, stand)
                && owner.position().distanceTo(stand.position())
                        <= dashMaxDistance(stand, RhcpAddon.state(player), 3.0D
                                * RhcpAddon.state(player).potency())) {
            double difference = speedDifference(stand, owner);
            if (difference > 3.0D) {
                dashTo(player, owner);
                stand.setAttackAlternate(true);
                startCounter(player, stand, "rhcp_blitz_attack", owner);
                return;
            }
            if (difference > 1.5D) {
                dashBehind(player, owner);
            }
        }
        sidestep(player, stand, 1, yRot);
    }

    // 1.16 RHCPEntity.isPickable: a Stand projectile RHCP can evade, or a
    // projectile shot by a living holding integrated_stand, is near.
    public static boolean evadableProjectileNear(RhcpStandEntity stand) {
        return !stand.level().getEntitiesOfClass(
                Projectile.class,
                stand.getBoundingBox().inflate(2.0D),
                projectile -> projectile instanceof ModdedProjectileEntity modded
                                && modded.standDamage()
                                && modded.canBeEvaded(stand)
                        || integratedStandOwner(projectile) != null).isEmpty();
    }

    private static boolean isStandProjectile(Projectile projectile) {
        return projectile instanceof ModdedProjectileEntity modded
                && modded.standDamage();
    }

    // 1.16 RHCPDodge.hasIntegratedStand(ProjectileEntity): the shooter is a
    // living holding integrated_stand.
    @Nullable
    private static LivingEntity integratedStandOwner(Projectile projectile) {
        return projectile.getOwner() instanceof LivingEntity owner
                && hasIntegratedStand(owner) ? owner : null;
    }

    // 1.16: another team, no team, or a team with friendly fire.
    private static boolean isEnemyProjectile(
            RhcpStandEntity stand, Projectile projectile) {
        var team = stand.getTeam();
        return projectile.getTeam() != team || team == null
                || team.isAllowFriendlyFire();
    }

    // 1.16: squared reach 5 for a still projectile, 9 from speed 3.15^0.5.
    public static double projectileEvadeRadiusSqr(double speedSqr) {
        return 9.0D - (4.0D - 4.0D * (Math.min(speedSqr, 3.15D) / 3.15D));
    }

    // 1.16 RHCPDodge.speedDifference against a living without a Stand
    // entity: player attack speed, or 16x movement speed, with the same
    // heavy and barrage shifts for its non-Stand heavy hits and barrages.
    public static double speedDifference(
            StandEntity rhcp, LivingEntity attacker) {
        if (attacker instanceof StandEntity standAttacker) {
            return speedDifference(rhcp, standAttacker);
        }
        EntityActionInstance action = livingAction(attacker);
        return speedDifference(rhcp.getAttackSpeed(), attackerSpeed(attacker),
                isNoStandHeavy(action), isNoStandBarrage(action));
    }

    /**
     * 1.16 RHCPElectricDash.calcPos with mustDodge: RHCP steps to the
     * farther free side of yRot (dodge 1 then tries yRot and its own facing;
     * dodge 2 first goes straight on when that is farthest), else a short
     * random side step. It costs 30 energy a block: none (dodge 0), a third
     * (dodge 1) or half (dodge 2).
     */
    public static boolean sidestep(
            ServerPlayer player, RhcpStandEntity stand, int dodge, float yRot) {
        return sidestep(player, stand, dodge, yRot, null);
    }

    // target: 1.16 electricDash's shift target, locked even without a step
    // (a Stand's user in dash reach replaces the Stand).
    public static boolean sidestep(
            ServerPlayer player, RhcpStandEntity stand, int dodge, float yRot,
            @Nullable LivingEntity target) {
        RhcpState state = RhcpAddon.state(player);
        StandPower power = StandPower.get(player);
        if (target != null) {
            LivingEntity lock = target;
            if (target instanceof StandEntity targetStand
                    && targetStand.getUser() != null
                    && targetStand.getUser().position().distanceTo(
                            stand.position()) <= dashMaxDistance(
                                    stand, state, 3.0D * state.potency())) {
                lock = targetStand.getUser();
            }
            state.setTargetId(lock.getId());
        }
        double side = dashMaxDistance(stand, state, 1.5D);
        Vec3 from = stand.position();
        Vec3 ahead = stepPos(stand, dashMaxDistance(stand, state, 3.0D), yRot);
        Vec3 right = stepPos(stand, side, yRot + 90.0F);
        Vec3 left = stepPos(stand, side, yRot - 90.0F);
        boolean random = stand.level().getRandom().nextFloat() >= 0.5F;
        Vec3 pos = null;
        boolean both = right != null && left != null;
        double toRight = right != null ? from.distanceTo(right) : 0.0D;
        double toLeft = left != null ? from.distanceTo(left) : 0.0D;
        if (dodge == 2 && both && ahead != null
                && from.distanceTo(ahead) > toRight
                && from.distanceTo(ahead) > toLeft) {
            pos = ahead;
            if (power != null) {
                power.setAbilityCooldown("rhcp_dodge", Math.min(
                        dashCooldown(from.distanceTo(ahead), state.potency(),
                                power.isUserCreative())
                                + stand.ticksEvaded(), 400));
            }
        }
        else if (dodge == 1 || dodge == 2) {
            if (both && toRight == toLeft && toRight > 0.6D) {
                pos = random ? right : left;
            }
            else if (both && toRight > toLeft && toRight > 0.6D) {
                pos = right;
            }
            else if (both && toRight < toLeft && toLeft > 0.6D) {
                pos = left;
            }
            else if (dodge == 1) {
                Vec3 straight = stepPos(stand, side, yRot);
                Vec3 facing = stepPos(stand, side, stand.getYRot());
                pos = straight != null && from.distanceTo(straight) > 0.6D
                        ? straight
                        : facing != null && from.distanceTo(facing) > 0.6D
                                ? facing : null;
            }
        }
        if (pos != null && power != null) {
            power.setAbilityCooldown("rhcp_electric_dash", dashCooldown(
                    from.distanceTo(pos), state.potency(),
                    power.isUserCreative()) / 2);
        }
        if (pos == null) {
            pos = stepPos(stand, dashMaxDistance(stand, state, 0.1D),
                    yRot + (random ? 90.0F : -90.0F));
        }
        if (pos == null) {
            return false;
        }
        Vec3 landing = pos;
        stand.level().getEntitiesOfClass(Mob.class,
                stand.getBoundingBox().inflate(8.0D),
                mob -> mob.getTarget() == stand && mob.getLookAngle().dot(
                        mob.getEyePosition().subtract(landing)) >= 0.0D)
                .forEach(mob -> mob.setTarget(null));
        float cost = dodgeDashCost(from.distanceTo(pos), dodge);
        if (cost > 0.0F) {
            consume(player, cost, 0.0F);
        }
        teleportStand(stand, pos);
        emitElectricParticles(player.serverLevel(), stand, 8);
        return true;
    }

    // 1.16 RHCPElectricDash.calcPos(rhcp, maxDistance, yRot): the step's
    // end on the ground, or none when a block or a living is in the way.
    @Nullable
    private static Vec3 stepPos(
            RhcpStandEntity stand, double distance, float yRot) {
        Level level = stand.level();
        Vec3 start = stand.getEyePosition();
        Vec3 end = start.add(Vec3.directionFromRotation(stand.getXRot(), yRot)
                .scale(distance));
        LivingEntity user = stand.getUser();
        if (level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE, stand)).getType()
                        != HitResult.Type.MISS
                || ProjectileUtil.getEntityHitResult(level, stand, start, end,
                        stand.getBoundingBox().expandTowards(end.subtract(start))
                                .inflate(1.0D),
                        entity -> entity instanceof LivingEntity
                                && entity != user) != null) {
            return null;
        }
        BlockPos pos = BlockPos.containing(end);
        while (pos.getY() > level.getMinBuildHeight()
                && level.isEmptyBlock(pos.below())) {
            pos = pos.below();
        }
        return new Vec3(end.x, pos.getY() > level.getMinBuildHeight()
                ? pos.getY() : stand.getY(), end.z);
    }

    private static double dashMaxDistance(
            RhcpStandEntity stand, RhcpState state, double range) {
        return dashMaxDistance(
                dashEffectiveRange(range, state.energy()),
                stand.getAttributeValue(Attributes.MOVEMENT_SPEED),
                StandStatFormulas.getMovementSpeed(RhcpAddon.RHCP.get()
                        .getStandStats().getBaseAttackSpeed()),
                state.potency());
    }

    // 1.16 RHCPElectricDash.effectiveRange: shorter below 30 energy a block.
    public static double dashEffectiveRange(double range, float energy) {
        return range <= 0.0D ? 0.0D
                : range * (Math.min(30.0D * range, energy) / (30.0D * range));
    }

    // 1.16 RHCPElectricDash.getMaxDistance: the range scaled by RHCP's speed
    // over its base speed with the potency bonus; nothing without power.
    public static double dashMaxDistance(
            double range, double speed, double baseSpeed, int potency) {
        return potency < 1 || potency > 5 ? 0.0D
                : range * (speed / (baseSpeed * (1.0D + 0.2D * potency)));
    }

    // 1.16 RHCPElectricDash.getCooldown.
    public static int dashCooldown(
            double distance, int potency, boolean creative) {
        return creative ? 0 : Mth.ceil(distance * (20 - 2 * (potency - 1)));
    }

    // 1.16 RHCPElectricDash.electricDash with mustDodge.
    public static float dodgeDashCost(double distance, int dodge) {
        double cost = 30.0D * distance;
        return (float) (dodge == 0 ? 0.0D
                : dodge == 1 ? cost / 3.0D : cost / 2.0D);
    }

    // 1.16 RHCPElectricDash.maxRange: 3 blocks a potency level, shorter
    // below 30 energy a block, then scaled by RHCP's speed.
    public static double dashReach(RhcpStandEntity stand, RhcpState state) {
        return dashMaxDistance(stand, state, 3.0D * state.potency());
    }

    // 1.16 electricDash: 30 energy a block travelled.
    public static float dashEnergyCost(double distance) {
        return (float) (30.0D * distance);
    }

    // 1.16 calcPos: the distance cooldown adds to what is left.
    public static int stackedDashCooldown(int current, int added) {
        return current > 0 ? current + added : added;
    }

    // 1.16 RHCPElectricDash.targetBeforePerform: the user's crosshair, or
    // RHCP's own view under manual control, within the dash reach.
    public static ActionTarget dashAim(ServerPlayer player) {
        RhcpStandEntity stand = activeStand(player);
        if (stand == null) {
            return ActionTarget.EMPTY;
        }
        LivingEntity aim = stand.isManuallyControlled() ? stand : player;
        double reach = dashReach(stand, RhcpAddon.state(player));
        return HitResultUtil.clip(
                aim.getEyePosition(),
                aim.getLookAngle(),
                reach,
                reach,
                aim.level(),
                entity -> isMeleeTarget(stand, entity),
                aim,
                stand.getPrecision());
    }

    // A blitz with no one to answer (port extra): stacks the distance
    // cooldown like the counter dashes.
    public static boolean dash(ServerPlayer player) {
        return electricDash(player, dashAim(player),
                StandManualInput.isShiftHeld(player, activeStand(player)),
                true);
    }

    // The dash key (aimed before manual control turns on). 1.16 set the
    // base cooldown after calcPos, overwriting the distance cooldown; the
    // port sets it at the press, so the pressed dash leaves it alone.
    // Shift is read at the press too (1.16 preTaskInit), before manual
    // control turns on.
    public static boolean dash(ServerPlayer player, ActionTarget aimed,
            boolean shift) {
        return electricDash(player, aimed, shift, false);
    }

    // 1.16 calcPos then setCooldownOnUse: only unpressed dashes keep the
    // stacked distance cooldown.
    public static int dashCooldownAfter(int current, int added, boolean stack) {
        return stack ? stackedDashCooldown(current, added) : current;
    }

    // 1.16 RHCPElectricDash.electricDash without mustDodge.
    private static boolean electricDash(ServerPlayer player,
            ActionTarget aimed, boolean shift, boolean stackCooldown) {
        RhcpStandEntity stand = activeStand(player);
        RhcpState state = RhcpAddon.state(player);
        if (stand == null || state.inConductor() || state.potency() == 0) {
            return false;
        }
        double maxDistance = dashReach(stand, state);
        ActionTarget target = aimed != null ? aimed : ActionTarget.EMPTY;
        // 1.16 :124 - with Shift a Stand's user in reach replaces the Stand.
        if (shift && target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() instanceof StandEntity targetStand
                && targetStand.getUser() != null
                && targetStand.getUser().position().distanceTo(
                        stand.position()) <= maxDistance) {
            target = new ActionTarget(targetStand.getUser());
        }
        Vec3 from = stand.position();
        Vec3 pos = dashPos(stand, target, maxDistance, shift);
        StandPower power = StandPower.get(player);
        if (pos != null && power != null) {
            int cooldown = dashCooldown(from.distanceTo(pos),
                    state.potency(), power.isUserCreative());
            int current = power.getAbilityCooldown("rhcp_electric_dash");
            int next = dashCooldownAfter(current, cooldown, stackCooldown);
            if (cooldown > 0 && next != current) {
                power.setAbilityCooldown("rhcp_electric_dash", next);
            }
        }
        if (pos != null) {
            Vec3 landing = pos;
            stand.level().getEntitiesOfClass(Mob.class,
                    stand.getBoundingBox().inflate(8.0D),
                    mob -> mob.getTarget() == stand && mob.getLookAngle().dot(
                            mob.getEyePosition().subtract(landing)) >= 0.0D)
                    .forEach(mob -> mob.setTarget(null));
        }
        LivingEntity locked = target.getType() == ActionTarget.TargetType.ENTITY
                && target.getEntity() instanceof LivingEntity living
                ? living : null;
        if (locked != null) {
            state.setTargetId(locked.getId());
            // The lock outlives the dash by 5 ticks.
            stand.setStandCount(5);
        }
        if (pos == null) {
            sync(player);
            return false;
        }
        consume(player, dashEnergyCost(from.distanceTo(pos)), 0.0F);
        teleportStand(stand, pos);
        playStandSound(
                player,
                stand,
                RhcpAddon.RHCP_ELECTRIC_DASH_SOUND.get(),
                1.0F);
        emitElectricParticles(player.serverLevel(), stand, 20);
        return true;
    }

    // 1.16 RHCPElectricDash.calcPos without mustDodge: by an entity, on or
    // against a block, else RHCP's own look ray end dropped to the ground.
    @Nullable
    private static Vec3 dashPos(RhcpStandEntity stand, ActionTarget target,
            double maxDistance, boolean shift) {
        Level level = stand.level();
        if (target.getType() == ActionTarget.TargetType.EMPTY) {
            Vec3 eye = stand.getEyePosition();
            Vec3 look = stand.getLookAngle();
            target = HitResultUtil.clip(eye, look, maxDistance, maxDistance,
                    level, entity -> isMeleeTarget(stand, entity), stand,
                    0.0D);
            if (target.getType() == ActionTarget.TargetType.EMPTY) {
                Vec3 end = eye.add(look.scale(maxDistance));
                BlockPos pos = BlockPos.containing(end);
                while (pos.getY() > level.getMinBuildHeight()
                        && level.isEmptyBlock(pos.below())) {
                    pos = pos.below();
                }
                return new Vec3(end.x, pos.getY() > level.getMinBuildHeight()
                        ? pos.getY() : stand.getY(), end.z);
            }
        }
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            BlockPos hit = target.getBlockPos();
            return Vec3.atBottomCenterOf(level.isEmptyBlock(hit.above())
                    ? hit.above() : hit.relative(target.getFace()));
        }
        Entity entity = target.getEntity();
        if (entity == null) {
            return null;
        }
        // 1.16 getEntityTargetPos: behind its back with Shift, else in
        // front of it along RHCP's look (or in place when already close).
        double gap = entity.getBbWidth() + stand.getBbWidth();
        if (shift) {
            return entity.position().subtract(Vec3.directionFromRotation(
                    0.0F, entity.getYRot()).scale(gap));
        }
        return stand.distanceToSqr(entity) > gap * gap
                ? entity.position().subtract(stand.getLookAngle().scale(gap))
                : stand.position();
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery: the heavy's task target,
    // the entity its punch struck; lastTick is the heavy's last recovery tick.
    public static boolean electrocuteTarget(ServerPlayer player, @Nullable Entity entity,
            boolean lastTick) {
        RhcpStandEntity stand = activeStand(player);
        RhcpState state = RhcpAddon.state(player);
        // 1.16 RHCPElectrocuteTarget.standTickRecovery :82-86 asked only for
        // a living task target and a last punch that dealt damage: no power,
        // conductor or reach gate, and what the battery lacks comes out of
        // stamina (consume, RHCPEntity.consumeEnergy :224-226).
        if (stand == null || stand.lastHeavyDamage() <= 0.0F
                || !(entity instanceof LivingEntity target)
                || !target.isAlive()) {
            return false;
        }
        int potency = state.potency();
        float damage = 0.3F + (potency > 3 ? 0.1F
                : potency > 2 ? 0.043F : potency > 1 ? 0.02F : 0.0F);
        DamageUtil.hurtThroughInvulTicks(
                target, standAttack(stand, player), damage);
        target.addEffect(new MobEffectInstance(
                ModStatusEffects.IMMOBILIZE, 2, 0, false, false, true));
        consume(player, electrocuteEnergyCost(potency), 0.0F);
        emitElectricParticles(player.serverLevel(), stand, 3);
        if (lastTick) {
            dragIntoRecentBlock(player, stand, target);
        }
        return true;
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery :91-104: a manually
    // controlled RHCP less than 2 blocks from its recent block goes back into
    // it, if it is a conductor, and takes along a zapped target that is not a
    // Stand (setTargetHit(id, true), the carried target).
    private static void dragIntoRecentBlock(
            ServerPlayer player, RhcpStandEntity stand, LivingEntity target) {
        if (target instanceof StandEntity || !stand.isManuallyControlled()) {
            return;
        }
        BlockPos pos = stand.recentBlock();
        stand.setRecentBlock(pos);
        Vec3 spot = conductorSpot(pos);
        if (stand.distanceToSqr(spot) >= 4.0D
                || !isConductor(stand.level().getBlockState(pos))) {
            return;
        }
        RhcpState state = RhcpAddon.state(player);
        releaseCarriedTarget(player, state);
        state.setCarriedTarget(target.getId(), target.noPhysics);
        state.enterConductor(pos);
        // 1.16 EnterTheBlock, the target to the same spot (:97-99)
        teleportStand(stand, spot);
        setTravelling(stand, true);
        target.teleportTo(spot.x, spot.y, spot.z);
        target.setDeltaMovement(Vec3.ZERO);
        sync(player);
    }

    // 1.16 RHCPElectrocuteTarget.standTickRecovery :90: consumeEnergy(1.0 +
    // 0.1 / 0.2 / 0.3 above potency 1 / 2 / 3, scaling 0), so Resolve does
    // not change it and potency 5 pays what 4 does.
    public static float electrocuteEnergyCost(int potency) {
        return 1.0F + (potency > 3 ? 0.3F
                : potency > 2 ? 0.2F : potency > 1 ? 0.1F : 0.0F);
    }

    public static void boost(ServerPlayer player, int actionTick) {
        RhcpState state = RhcpAddon.state(player);
        state.setBoosting(true);
        if (actionTick % 5 == 0
                && state.potency() < 5
                && state.energy() > 100.0F) {
            // 1.16 RHCPBoost only raises potency; it spends no energy.
            state.setPotency(state.potency() + 1);
            sync(player);
            RhcpStandEntity stand = activeStand(player);
            emitElectricParticles(player.serverLevel(), stand, 24);
            if (stand != null && !state.inConductor()) {
                player.serverLevel().sendParticles(
                        RhcpAddon.RHCP_FLASH.get(),
                        stand.getX(),
                        stand.getY() + stand.getBbHeight() * 0.5D,
                        stand.getZ(),
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                flash(player, stand, actionTick / 5);
            }
        }
    }

    // 1.16 RHCPBoost.increasePotency: the flash hits Stand-seeing entities
    // within 16 blocks of RHCP that see it; non-player mobs are also stunned.
    private static void flash(ServerPlayer player, RhcpStandEntity stand,
            int strength) {
        if (strength <= 0) {
            return;
        }
        for (LivingEntity living : player.serverLevel().getEntitiesOfClass(
                LivingEntity.class,
                stand.getBoundingBox().inflate(16.0D),
                entity -> entity.isAlive()
                        && canSeeStands(entity)
                        && entity.hasLineOfSight(stand)
                        && entity != player
                        && entity != stand)) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    flashBlindnessTicks(stand.distanceTo(living), strength),
                    0, true, true, false));
            if (!(living instanceof Player)
                    && !(living instanceof StandEntity)) {
                living.addEffect(new MobEffectInstance(ModStatusEffects.STUN,
                        flashStunTicks(strength), 0, true, true, false));
            }
        }
    }

    // 1.16 RHCPBoost.canSeeStands.
    static boolean canSeeStands(LivingEntity entity) {
        return StandUtil.isEntityStandUser(entity)
                || entity.hasEffect(ModStatusEffects.SPIRIT_VISION);
    }

    // 1.16: 25 ticks per strength within 5 blocks of RHCP, else 10.
    public static int flashBlindnessTicks(double distance, int strength) {
        return (distance < 5.0D ? 25 : 10) * strength;
    }

    public static int flashStunTicks(int strength) {
        return 8 * strength;
    }

    public static void cleanupTransient(ServerPlayer player) {
        RhcpState state = RhcpAddon.state(player);
        boolean carriedTargetChanged = state.carriedTargetId() >= 0;
        releaseCarriedTarget(player, state);
        RhcpStandEntity stand = activeStand(player);
        if (stand != null) {
            stand.noPhysics = false;
            stand.setNoGravity(false);
            stand.setInvisible(false);
            stand.setAttackAlternate(false);
        }
        if (state.clearTransient() || carriedTargetChanged) {
            sync(player);
        }
    }

    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player, RhcpStatePayload.from(player));
    }

    // 1.16 RHCPElectricDash / EnterTheBlock: setPos plus TrDirectEntityPosPacket.
    // A manually controlled RHCP ignores vanilla move packets on its user's
    // client, which would send the old spot back and undo the move.
    public static void teleportStand(RhcpStandEntity stand, Vec3 pos) {
        Vec3 from = stand.position();
        stand.teleportTo(pos.x, pos.y, pos.z);
        if (stand.level().isClientSide()) {
            return;
        }
        stand.markDirectPos(from);
        TrDirectEntityPosPacket packet =
                new TrDirectEntityPosPacket(stand.getId(), stand.position());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(stand, packet);
        // The user may be out of the Stand's tracking range; a repeat is harmless.
        if (stand.getUser() instanceof ServerPlayer user) {
            PacketDistributor.sendToPlayer(user, packet);
        }
    }

    public static void emitElectricParticles(
            ServerLevel level,
            RhcpStandEntity stand,
            int count) {
        if (stand == null || count <= 0) {
            return;
        }
        level.sendParticles(
                RhcpAddon.RHCP_PARTICLE.get(),
                stand.getX(),
                stand.getY() + stand.getBbHeight() * 0.55D,
                stand.getZ(),
                count,
                stand.getBbWidth() * 0.6D,
                stand.getBbHeight() * 0.4D,
                stand.getBbWidth() * 0.6D,
                0.03D);
    }

    public static void play(
            ServerPlayer player, SoundEvent sound) {
        player.playSound(sound, 1.0F, 1.0F);
    }

    // 1.16 impact sounds: InitStands punchSound / barrageHitSound,
    // RHCPHeavyPunch and RHCPCounterattack punchEntity (attackAlt).
    @Nullable
    public static Holder<SoundEvent> impactSound(
            @Nullable RhcpAbility.Mode mode, boolean attackAlternate) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case PUNCH_OFF -> ModSoundEvents.STAND_PUNCH_LIGHT;
            case PUNCH, BARRAGE -> RhcpAddon.RHCP_PUNCH_LIGHT;
            case COMBO_PUNCH, CHASE_TARGET -> ModSoundEvents.STAND_PUNCH_HEAVY;
            case HEAVY_PUNCH -> attackAlternate
                    ? RhcpAddon.RHCP_PUNCH_HEAVY
                    : RhcpAddon.RHCP_PUNCH_HEAVY_ENTITY;
            case COUNTERATTACK, BLITZ_ATTACK -> attackAlternate
                    ? RhcpAddon.RHCP_CUT
                    : RhcpAddon.RHCP_PUNCH_LIGHT;
            default -> null;
        };
    }

    // Played at the target's center for Stand users, as core punches do.
    private static void playImpactSound(
            ServerPlayer player,
            RhcpStandEntity stand,
            LivingEntity target,
            @Nullable Holder<SoundEvent> sound) {
        StandPower power = StandPower.get(player);
        if (sound != null && power != null && !stand.isSilent()) {
            StandUtil.broadcastSound(player.serverLevel(),
                    target.getBoundingBox().getCenter(), sound, true, power,
                    stand.getSoundSource(), 1.0F, 1.0F);
        }
    }

    // 1.16 played these as stand sounds (standSound), which the user hears
    // too; ServerPlayer.playSound leaves the player out.
    private static void playStandSound(
            ServerPlayer player,
            RhcpStandEntity stand,
            SoundEvent sound,
            float pitch) {
        if (stand != null) {
            StandUtil.playStandEntitySound(stand, sound, 1.0F, pitch);
        }
        else {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    sound,
                    player.getSoundSource(),
                    1.0F,
                    pitch);
        }
    }
}
