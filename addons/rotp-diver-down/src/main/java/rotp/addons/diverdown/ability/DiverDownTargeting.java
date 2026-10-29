package rotp.addons.diverdown.ability;

import java.util.Optional;

import rotp.addons.diverdown.entity.DiverDownStandEntity;
import rotp.addons.diverdown.state.DiverDownAuthority;
import rotp.core.api.stand.StandSkillWalls;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.StandPrecisionRay;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.util.functions.AttributeUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DiverDownTargeting {
    // 1.16: the kinetic trap's strike aimed at 5 blocks, the dive took the
    // client's target within the user's reach of 5 (the steal's and the trap
    // press's pick follow the user's reach and game mode, see crosshair); those
    // rays clipped blocks by their outline, so grass and flowers stop them
    public static final double RANGE = 5.0D;
    private static final double INFLATION = 0.75D;
    private static final double SURVIVAL_BLOCK_REACH = 4.5D;
    private static final double CREATIVE_PICK_BONUS = 0.5D;
    private static final double SURVIVAL_ENTITY_REACH_SQR = 9.0D;
    private static final double CREATIVE_RAY = 6.0D;
    // ServerPlayer.CREATIVE_BLOCK_INTERACTION_RANGE_MODIFIER, put on or taken off on the player's next tick
    private static final ResourceLocation CREATIVE_BLOCK_RANGE =
            ResourceLocation.withDefaultNamespace("creative_mode_block_range");

    private DiverDownTargeting() {}

    public static LivingEntity living(
            LivingEntity owner,
            DiverDownStandEntity stand) {
        return living(owner, stand, true);
    }

    /**
     * @param stand null when Diver Down is not summoned
     * @param wallsHideEntities false: a living target behind blocks within
     *        the range is picked, for a skill marked through walls
     */
    public static LivingEntity living(
            LivingEntity owner,
            DiverDownStandEntity stand,
            boolean wallsHideEntities) {
        if (owner == null) {
            return null;
        }
        Entity aimer = aimer(owner, stand);
        if (aimer != stand) {
            return userPick(owner, stand, wallsHideEntities);
        }
        if (aimer == stand) {
            // 1.16 took the client's pick from the manually controlled Stand's eyes first: an entity up to 3 blocks
            // away in Survival, the user's pick range in Creative, and the user is pickable from there
            HitResult pick = vanillaPick(owner, stand, stand);
            if (pick instanceof EntityHitResult hit) {
                // StandEntity.canAttack refused the user (StandEntityAction.checkStandTarget) silently, so
                // DiverDownEntityPhasing's dd_self was never reached
                if (hit.getEntity() == owner) {
                    return null;
                }
                // checkStandTarget refused any other picked entity that is not living or may not be taken as well:
                // the press failed, the ray below is only for a pick that met a block or nothing
                return hit.getEntity() instanceof LivingEntity picked
                        && DiverDownAuthority.canAffect(owner, picked, stand)
                        ? picked
                        : null;
            }
            ActionTarget pressed = blockWithoutCollision(owner, pick);
            if (pressed != null && wallsHideEntities) {
                return pastBlockWithoutCollision(owner, stand, pressed, true);
            }
            if (pressed != null) {
                // through walls a torch is looked past as stone is; the 1.16 override only stands in when that ray
                // finds nothing
                LivingEntity rayed = inflatedRay(owner, stand, aimer, false);
                return rayed != null ? rayed : pastBlockWithoutCollision(owner, stand, pressed, false);
            }
        }
        return inflatedRay(owner, stand, aimer, wallsHideEntities);
    }

    private static LivingEntity inflatedRay(
            LivingEntity owner, DiverDownStandEntity stand, Entity aimer, boolean wallsHideEntities) {
        Vec3 start = aimer.getEyePosition();
        Vec3 fullEnd = start.add(
                aimer.getLookAngle().scale(aimer == stand ? standReach(stand) : RANGE));
        BlockHitResult blockHit = owner.level().clip(
                new ClipContext(
                        start,
                        fullEnd,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        aimer));
        Vec3 end = !wallsHideEntities
                || blockHit.getType() != HitResult.Type.BLOCK
                ? fullEnd
                : blockHit.getLocation();
        AABB search = aimer.getBoundingBox()
                .expandTowards(end.subtract(start))
                .inflate(INFLATION);

        LivingEntity best = null;
        double bestDistance = start.distanceToSqr(end);
        for (Entity candidate : owner.level().getEntities(
                aimer,
                search,
                entity -> entity instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) candidate;
            if (!DiverDownAuthority.canAffect(
                    owner, living, stand)) {
                continue;
            }
            AABB box = living.getBoundingBox().inflate(
                    living.getPickRadius() + INFLATION);
            Optional<Vec3> intersection = box.contains(start)
                    ? Optional.of(start)
                    : box.clip(start, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distance =
                    start.distanceToSqr(intersection.get());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = living;
            }
        }
        return best;
    }

    /**
     * Entity Phasing's target picked from the user as 1.16's client picked it (InputHandler.pickMouseTarget,
     * EntityStandType.clientHitResult :121-151): the vanilla pick, which meets an entity 3 blocks away at most in
     * Survival; with Diver Down out a pick that met nothing goes on with the Stand's precision ray at the user's reach,
     * under the aim assist as the port's other Stand aims. A picked entity the dive may not take is refused, as 1.16's
     * StandEntity.canAttack refused it, not passed over. A pick at a block without a collision shape gives way to what
     * the summoned Diver Down's collision ray meets ({@link #pastBlockWithoutCollision}); any other block is no target.
     * The dive aims through walls only where its skill is marked so; there the pick passes a torch as it passes stone,
     * and Diver Down's collision ray stands in for a torch only when that pick finds nothing.
     *
     * @param stand Diver Down if it was out before the press (a Stand the press summons gave the client no precision ray)
     */
    private static LivingEntity userPick(
            LivingEntity owner, DiverDownStandEntity stand, boolean wallsHideEntities) {
        // 1.16's pick stopped at the first block by its outline, the walls rule aside
        HitResult outline = vanillaPick(owner, owner, stand);
        ActionTarget pressed = blockWithoutCollision(owner, outline);
        if (pressed != null && wallsHideEntities) {
            return pastBlockWithoutCollision(owner, stand, pressed, true);
        }
        if (wallsHideEntities) {
            return userLine(owner, stand, outline, true);
        }
        // through walls a torch is looked past as stone is; the 1.16 override only stands in when that finds nothing
        LivingEntity through = userLine(owner, stand, vanillaPick(owner, owner, stand, false), false);
        return through != null || pressed == null
                ? through
                : pastBlockWithoutCollision(owner, stand, pressed, false);
    }

    // the pick's living target, else, with Diver Down out and a pick that met nothing, the precision ray's
    private static LivingEntity userLine(
            LivingEntity owner, DiverDownStandEntity stand, HitResult pick, boolean wallsHideEntities) {
        if (pick instanceof EntityHitResult hit) {
            return hit.getEntity() instanceof LivingEntity picked
                    && DiverDownAuthority.canAffect(owner, picked, stand)
                    ? picked
                    : null;
        }
        if (stand == null || pick.getType() == HitResult.Type.BLOCK) {
            return null;
        }
        double reach = StandPrecisionRay.reach(owner, stand);
        ActionTarget aim = StandEntityPunchAbility.aimAssist(owner)
                ? StandPrecisionRay.trace(owner, stand, reach, 0.0D, true, wallsHideEntities)
                : StandPrecisionRay.crosshair(owner, stand, reach, true, wallsHideEntities);
        return aim.getType() == ActionTarget.TargetType.ENTITY
                && aim.getEntity() instanceof LivingEntity living
                && DiverDownAuthority.canAffect(owner, living, stand)
                ? living
                : null;
    }

    // the pick as a pressed block without a collision shape (grass, a flower, a torch), else null
    private static ActionTarget blockWithoutCollision(LivingEntity owner, HitResult pick) {
        if (!(pick instanceof BlockHitResult block) || block.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        ActionTarget pressed = new ActionTarget(block.getBlockPos(), block.getDirection());
        return StandEntityAbility.isBlockWithoutCollision(owner.level(), pressed) ? pressed : null;
    }

    /**
     * 1.16 StandEntityAction.overrideVanillaMouseTarget, which PowerBaseImpl.checkTarget ran on the dive's target
     * (DiverDownEntityPhasing, TargetRequirement.ENTITY) after the press had summoned Diver Down: the pressed block gave
     * way to what Diver Down's collision ray met within its own reach (the core's
     * StandEntityAbility.pastBlockWithoutCollision, from the user's summoned Stand). A living entity there is the target
     * if the dive may take it; anything else refused the press.
     */
    private static LivingEntity pastBlockWithoutCollision(
            LivingEntity owner, DiverDownStandEntity stand, ActionTarget pressed, boolean wallsHideEntities) {
        ActionTarget past = StandEntityAbility.pastBlockWithoutCollision(owner, pressed, wallsHideEntities);
        return past.getType() == ActionTarget.TargetType.ENTITY
                && past.getEntity() instanceof LivingEntity living
                && DiverDownAuthority.canAffect(owner, living, stand)
                ? living
                : null;
    }

    /**
     * The client's crosshair of 1.16, which Steal Item took as its target (InputHandler.pickMouseTarget, through
     * EntityStandType.clientHitResult): the vanilla pick, and with a Stand out, a pick that found nothing is looked at
     * again with the Stand's precision ray, whose entity (never the Stand or its user) or block is the target. The ray
     * follows the user's aim assist as the port's other Stand aims do (the strict crosshair at the same reach when it
     * is off). No team, game mode, invulnerability or PvP filter applies to it.
     *
     * @param stand the user's summoned Diver Down, or null
     * @param skill the Steal Item ability: its own walls answer rules the crosshair ray, not the answer of the Stand's
     *        marked attack the press may fall in (the aimer's current action)
     */
    public static HitResult crosshair(
            LivingEntity owner, DiverDownStandEntity stand, Ability skill) {
        Entity aimer = aimer(owner, stand);
        return crosshair(owner, stand, aimer, false, StandSkillWalls.wallsHideEntities(owner.level(), skill));
    }

    /**
     * {@link #crosshair(LivingEntity, DiverDownStandEntity)} for the Kinetic Trap press: the precision ray follows the
     * walls rule of the skill, as the trap's strike does (owner, 2026-10-16).
     */
    public static HitResult crosshair(
            LivingEntity owner, DiverDownStandEntity stand, boolean wallsHideEntities) {
        return crosshair(owner, stand, aimer(owner, stand), wallsHideEntities, wallsHideEntities);
    }

    private static HitResult crosshair(
            LivingEntity owner,
            DiverDownStandEntity stand,
            Entity aimer,
            boolean wallsHideTracedEntities,
            boolean wallsHideCrosshairEntities) {
        HitResult vanilla = vanillaPick(owner, aimer, stand);
        if (stand == null
                || vanilla.getType() != HitResult.Type.MISS) {
            return vanilla;
        }
        double reach = aimer == stand ? standReach(stand) : StandPrecisionRay.reach(owner, stand);
        ActionTarget aim = StandEntityPunchAbility.aimAssist(owner)
                ? StandPrecisionRay.trace(aimer, stand, reach, 0.0D, true, wallsHideTracedEntities)
                : StandPrecisionRay.crosshair(aimer, stand, reach, true, wallsHideCrosshairEntities);
        if (aim.getType() == ActionTarget.TargetType.ENTITY
                && aim.getEntity() != null) {
            return new EntityHitResult(
                    aim.getEntity(),
                    aim.getClipPos().orElse(aim.getEntity().position()));
        }
        if (aim.getType() != ActionTarget.TargetType.BLOCK) {
            return vanilla;
        }
        return new BlockHitResult(
                aim.getClipPos().orElseGet(
                        () -> Vec3.atCenterOf(aim.getBlockPos())),
                aim.getFace(),
                aim.getBlockPos(),
                false);
    }

    /**
     * 1.16 GameRenderer.pick: a block by its outline within the pick range ({@link #pickRange}: 4.5, 5 in Creative,
     * for an unmodified reach), then the nearest pickable entity that is not a spectator, in front of that block (or
     * of the pick range's end) on a ray of the pick range, 6 blocks in Creative. A Survival player reaches an entity
     * 3 blocks away at most whatever the reach, and one beyond that hides the block behind it. A Stand is pickable by
     * the client of the user only when that client sees Stands (StandEntity.isPickable: ClientUtil.canSeeStands,
     * never the user's own).
     */
    private static HitResult vanillaPick(
            LivingEntity owner, Entity aimer, DiverDownStandEntity stand) {
        return vanillaPick(owner, aimer, stand, true);
    }

    /**
     * @param blocksHideEntities false for a skill marked through walls: a block on the way neither stops the entity
     *        search nor is the result
     */
    private static HitResult vanillaPick(
            LivingEntity owner, Entity aimer, DiverDownStandEntity stand, boolean blocksHideEntities) {
        boolean creative = owner instanceof Player player && player.isCreative();
        double reach = pickRange(owner, creative);
        Vec3 start = aimer.getEyePosition();
        Vec3 look = aimer.getLookAngle();
        BlockHitResult blockHit = owner.level().clip(
                new ClipContext(
                        start,
                        start.add(look.scale(reach)),
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        aimer));
        if (!blocksHideEntities) {
            Vec3 end = start.add(look.scale(reach));
            blockHit = BlockHitResult.miss(
                    end, Direction.getNearest(look.x, look.y, look.z), BlockPos.containing(end));
        }
        double rayLength = creative ? CREATIVE_RAY : reach;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                aimer,
                start,
                start.add(look.scale(rayLength)),
                aimer.getBoundingBox()
                        .expandTowards(look.scale(rayLength))
                        .inflate(1.0D),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != stand
                        && (!(entity instanceof StandEntity other)
                                || other.getUser() != owner
                                        && StandUtil.entityCanSeeStands(owner)),
                start.distanceToSqr(blockHit.getLocation()));
        if (entityHit == null) {
            return blockHit;
        }
        Vec3 at = entityHit.getLocation();
        return !creative && start.distanceToSqr(at) > SURVIVAL_ENTITY_REACH_SQR
                ? BlockHitResult.miss(
                        at,
                        Direction.getNearest(look.x, look.y, look.z),
                        BlockPos.containing(at))
                : entityHit;
    }

    /**
     * 1.16 PlayerController.getPickRange as Forge patched it (RotP's MCUtil.getPickRange copies it): the user's
     * REACH_DISTANCE, 5 unless modified, half a block less outside Creative. The 1.21 block interaction range is that
     * Survival value; the Creative bonus the game puts on it at the player's next tick is left out and the game mode
     * adds it here, as StandPrecisionRay.reach does. 1.16 had no entity reach attribute: its 3 and 6 were constants.
     */
    private static double pickRange(LivingEntity owner, boolean creative) {
        AttributeInstance range = owner.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        double survival = range != null
                ? AttributeUtil.calcValueWithoutModifiers(range, CREATIVE_BLOCK_RANGE)
                : SURVIVAL_BLOCK_REACH;
        return creative ? survival + CREATIVE_PICK_BONUS : survival;
    }

    /**
     * What 1.16's precision ray of {@code aiming} met first within the range (StandEntity.precisionRayTrace): a block
     * by its outline, an entity but the Stand and its user, or a miss.
     */
    public static HitResult firstHit(
            Entity aiming, DiverDownStandEntity stand) {
        Vec3 start = aiming.getEyePosition();
        Vec3 fullEnd = start.add(aiming.getLookAngle().scale(RANGE));
        BlockHitResult blockHit = aiming.level().clip(new ClipContext(
                start,
                fullEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                aiming));
        Vec3 end = blockHit.getType() == HitResult.Type.BLOCK
                ? blockHit.getLocation()
                : fullEnd;
        LivingEntity user = stand != null ? stand.getUser() : null;
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                aiming,
                start,
                end,
                aiming.getBoundingBox()
                        .expandTowards(end.subtract(start))
                        .inflate(1.0D),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && entity != stand
                        && entity != user,
                start.distanceToSqr(end));
        return entityHit != null ? entityHit : blockHit;
    }

    public static BlockHitResult block(Entity aiming) {
        Vec3 start = aiming.getEyePosition();
        Vec3 end = start.add(aiming.getLookAngle().scale(RANGE));
        return aiming.level().clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                aiming));
    }

    /**
     * Where 1.16 picked the client's target from (InputHandler.pickMouseTarget, GameRenderer.pick,
     * EntityStandType.clientHitResult): the camera entity, which manual control makes the Stand (StandUtil.java:160),
     * else the user. The pick range stays the user's (PlayerController.getPickRange).
     */
    static Entity aimer(LivingEntity owner, DiverDownStandEntity stand) {
        return stand != null && stand.isManuallyControlled() ? stand : owner;
    }

    // 1.16 StandEntity.getAimDistance under manual control: the Stand's own REACH_DISTANCE
    private static double standReach(DiverDownStandEntity stand) {
        return stand.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
    }
}
