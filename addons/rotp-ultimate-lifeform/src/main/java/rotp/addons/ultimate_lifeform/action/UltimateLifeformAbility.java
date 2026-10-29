package rotp.addons.ultimate_lifeform.action;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTarget.TargetType;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.impl.powers.hamon.HamonUtil;
import rotp.core.impl.powers.hamon.abilities.HamonAbilityHelpers;
import rotp.core.util.functions.UtilFunctions;
import rotp.addons.ultimate_lifeform.UltimateLifeformAddon;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformData;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformPowerType;
import rotp.addons.ultimate_lifeform.power.UltimateLifeformSpec;
import com.illusivesoulworks.caelus.api.CaelusApi;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class UltimateLifeformAbility
        extends EntityActionAbility {
    private static final ResourceLocation PILLAR_MAN_ANIMS =
            ResourceLocation.fromNamespaceAndPath(
                    UltimateLifeformPlayerClips.CORE_NAMESPACE,
                    UltimateLifeformPlayerClips.PILLAR_MAN);
    private static final ResourceLocation HAMON_ANIMS =
            ResourceLocation.fromNamespaceAndPath(
                    UltimateLifeformPlayerClips.CORE_NAMESPACE,
                    UltimateLifeformPlayerClips.HAMON);
    // indexed by (left hand item ? 1 : 0) + (right hand item ? 2 : 0)
    private static final ActionAnimIdentifier[] SENDO_WAVE_KICK_ANIMS = {
            kickAnim(false, false),
            kickAnim(true, false),
            kickAnim(false, true),
            kickAnim(true, true)};
    private final UltimateLifeformSpec spec;
    private final ActionAnimIdentifier playerClipAnim;

    public UltimateLifeformAbility(
            AbilityType<?> abilityType,
            AbilityId abilityId,
            UltimateLifeformSpec spec) {
        super(
                abilityType,
                abilityId,
                UltimateActionInstance::new);
        this.spec = spec;
        UltimateLifeformPlayerClips.Clip clip =
                UltimateLifeformPlayerClips.forSpec(spec);
        playerClipAnim = clip != null
                ? ActionAnimIdentifier.getOrCreate(clip.name(), false)
                : null;
        spriteName = spec.id;
        if (spec.held && spec.chargeTicks > 0) {
            setDefaultPhaseLength(
                    ActionPhase.BUTTON_CHARGE,
                    spec.chargeTicks);
            if (spec.firesOnRelease()) {
                // the server action ends the move (maxActionTicks)
                setDefaultPhaseLength(ActionPhase.PERFORM, 999999);
                setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            }
            else if (spec.continuesAfterCharge()) {
                setButtonHoldPhase(ActionPhase.PERFORM);
                setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
            }
            else {
                setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            }
        }
        else if (spec.held) {
            setButtonHoldPhase(ActionPhase.PERFORM);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
        }
        else if (spec == UltimateLifeformSpec.ULTIMATE_HAMON_KICK) {
            setDefaultPhaseLength(ActionPhase.PERFORM, Integer.MAX_VALUE);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        }
        else if (spec.scriptedPerformTicks() > 0) {
            setDefaultPhaseLength(
                    ActionPhase.PERFORM,
                    spec.scriptedPerformTicks());
            setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        }
        else {
            setDefaultPhaseLength(ActionPhase.PERFORM, 1);
            setDefaultPhaseLength(ActionPhase.RECOVERY, 2);
        }
        if (spec.ignoresPerformerStun()) {
            setIgnoresPerformerStun();
        }
        if (spec.resetsAttackStrengthOnPerform()) {
            setResetsAttackStrengthOnPerform();
        }
        if (spec.shoutsWhileSneaking()) {
            setPlaysVoiceLineOnSneak();
        }
    }

    public UltimateLifeformSpec spec() {
        return spec;
    }

    // 1.16 Action.getCooldownTechnical: the first part of cooldown(technical,
    // additional), which a Stand-bearing mob waits (+ 10) before its next
    // pick; the Tentacles' cooldown(80, 0). The cooldown itself is set from
    // the spec, not by Ability.cooldown.
    @Override
    public int getCooldownTechnical(Power<?> context) {
        return spec.technicalCooldown();
    }

    // 1.16 ActionsOverlayGui heldReadyToFire (:1157-1160): held past its
    // holdToFire length; the default covers a charge held at its end
    @Override
    public boolean isActionHeldReadyToFire(EntityActionInstance action) {
        return spec.readyToFireInHeldPerform()
                        && action.getPhase() == ActionPhase.PERFORM
                        && isActionHeld(action)
                || super.isActionHeldReadyToFire(action);
    }

    // 1.16 moves posed the player with core Hamon / Pillar Man clips
    @Override
    public ResourceLocation getEntityAnimSet(LivingEntity user) {
        UltimateLifeformPlayerClips.Clip clip =
                UltimateLifeformPlayerClips.forSpec(spec);
        if (clip == null) {
            return super.getEntityAnimSet(user);
        }
        return UltimateLifeformPlayerClips.HAMON.equals(clip.animSet())
                ? HAMON_ANIMS
                : PILLAR_MAN_ANIMS;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        UltimateLifeformPlayerClips.Clip clip =
                UltimateLifeformPlayerClips.forSpec(spec);
        if (clip == null) {
            return super.getEntityAnim(action);
        }
        LivingEntity user = action != null ? action.getPowerUser() : null;
        if (spec == UltimateLifeformSpec.ULTIMATE_HAMON_KICK && user != null) {
            boolean left = holdsItem(user, HumanoidArm.LEFT);
            boolean right = holdsItem(user, HumanoidArm.RIGHT);
            return SENDO_WAVE_KICK_ANIMS[(left ? 1 : 0) + (right ? 2 : 0)];
        }
        return playerClipAnim;
    }

    private static ActionAnimIdentifier kickAnim(boolean left, boolean right) {
        return ActionAnimIdentifier.getOrCreate(
                UltimateLifeformPlayerClips.sendoWaveKick(left, right), false);
    }

    private static boolean holdsItem(LivingEntity user, HumanoidArm side) {
        InteractionHand hand = user.getMainArm() == side
                ? InteractionHand.MAIN_HAND
                : InteractionHand.OFF_HAND;
        return !user.getItemInHand(hand).isEmpty();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        // 1.16 shout on use, skipped while sneaking unless a SHIFT variation
        String shout = spec.legacyShout();
        if (!level.isClientSide() && powerUser != null && shout != null) {
            sayShout(powerUser, UltimateLifeformAddon.SOUNDS.get(shout));
        }
        if (!level.isClientSide()
                && action instanceof UltimateActionInstance ultimate) {
            ultimate.captureInputTarget(powerUser);
        }
        if (spec == UltimateLifeformSpec.STONE_FORM) {
            UltimateLifeformData data = data(getUserPower(powerUser));
            if (data != null && data.isStoneForm()) {
                action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 80.0F);
            }
        }
    }

    @Override
    public ConditionCheck checkSpecificConditions(
            Power<?> context) {
        ConditionCheck base =
                super.checkSpecificConditions(context);
        if (!base.isPositive()) {
            return base;
        }
        UltimateLifeformData data = data(context);
        if (data == null) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 PowerBaseImpl.checkRequirements refused an action on
        // cooldown without a message.
        if (data.isAbilityOnCooldown(spec.id)) {
            return ConditionCheck.NEGATIVE;
        }
        LivingEntity user = context.getUser();
        boolean sendo = spec
                == UltimateLifeformSpec.ULTIMATE_HAMON_SENDO_OVERDRIVE;
        boolean infusion = spec == UltimateLifeformSpec.HAMON_PLANT_INFUSION
                || spec == UltimateLifeformSpec.HAMON_ORGANISM_INFUSION;
        ActionTarget target = sendo
                ? UltimateLifeformCombat.sendoTarget(user)
                : infusion
                        ? UltimateLifeformCombat.infusionTarget(user, spec)
                        : UltimateLifeformCombat.crosshairTarget(user);
        // 1.16 PowerBaseImpl.checkTarget came first: Absorption (an ENTITY
        // target) could not be pressed, so not pre-charged, without one
        if (spec == UltimateLifeformSpec.ABSORPTION
                && UltimateLifeformCombat.absorptionTarget(user, data)
                        == null) {
            Entity aimed = UltimateLifeformCombat.absorptionLookEntity(user);
            return aimed != null
                            && !ActionTargetRange.isEntityWithinRange(
                                    user, aimed,
                                    UltimateLifeformCombat.ABSORPTION_RANGE_SQ)
                    ? ConditionCheck.createNegative("target_too_far")
                    : ConditionCheck.NEGATIVE;
        }
        // 1.16 UltimateLifeformSendoOverdrive.getTargetRequirement is BLOCK:
        // PowerBaseImpl.checkTarget refused any other target (the port's
        // refusal message is kept, owner 2026-10-19) and a block beyond 10
        // blocks with "target_too_far"; perform spawns at the same target
        if (sendo) {
            ConditionCheck sendoTarget =
                    UltimateLifeformCombat.checkSendoTarget(user, target);
            if (!sendoTarget.isPositive()) {
                return sendoTarget;
            }
        }
        // 1.16 UltimateLifeformPlantInfusion: getTargetRequirement BLOCK
        // (:79-81) refused any other target (the port says "living_plant"),
        // then checkTarget (:65-76) said "turtle_egg" for an egg and
        // "living_plant" for a block that is not living
        if (spec == UltimateLifeformSpec.HAMON_PLANT_INFUSION) {
            if (target.getType() != TargetType.BLOCK) {
                return ConditionCheck.createNegative("living_plant");
            }
            if (UltimateLifeformCombat.isEggBlock(
                    user.level().getBlockState(target.getBlockPos()))) {
                return ConditionCheck.createNegative("turtle_egg");
            }
            if (!UltimateLifeformCombat.canChargeBlock(
                    user.level(), target.getBlockPos())) {
                return ConditionCheck.createNegative("living_plant");
            }
        }
        // 1.16 UltimateLifeformOrganismInfusion: TargetRequirement ANY
        // refused an empty target (the port says "living_target");
        // checkTarget (:98-121) said "living_mob" for an entity that is not
        // living and gave canChargeBlock's "living_plant" for a block
        if (spec == UltimateLifeformSpec.HAMON_ORGANISM_INFUSION) {
            if (target.getType() == TargetType.ENTITY) {
                if (!(target.getMainEntity() instanceof LivingEntity living
                        && HamonUtil.isLiving(living))) {
                    return ConditionCheck.createNegative("living_mob");
                }
            }
            else if (target.getType() == TargetType.BLOCK) {
                if (!UltimateLifeformCombat.canChargeBlock(
                        user.level(), target.getBlockPos())) {
                    return ConditionCheck.createNegative("living_plant");
                }
            }
            else {
                return ConditionCheck.createNegative("living_target");
            }
        }
        if (spec == UltimateLifeformSpec.HYPNOTIZE) {
            ConditionCheck hypnosis =
                    UltimateLifeformCombat.checkHypnosisTarget(user, target);
            if (!hypnosis.isPositive()) {
                return hypnosis;
            }
        }
        // 1.16 UltimateLifeformHamonOverdrive.getTargetRequirement ENTITY:
        // the crosshair entity within reach, asked before the Hamon
        if (spec == UltimateLifeformSpec.HAMON_OVERDRIVE) {
            ConditionCheck overdrive =
                    UltimateLifeformCombat.checkOverdriveTarget(user,
                            UltimateLifeformCombat.crosshairTarget(user));
            if (!overdrive.isPositive()) {
                return overdrive;
            }
        }
        // 1.16 UltimateLifeformFlightDash and both armors overrode
        // checkConditions and refused before UltimateLifeformAction's Hamon
        // (without a message; the port's messages are kept)
        if (spec == UltimateLifeformSpec.ULTIMATE_FLIGHT_DASH
                && !user.isFallFlying()) {
            return ConditionCheck.createNegative("ultimate_flight_dash");
        }
        // the armors asked for the other armor's effect, not the armor type
        if (spec == UltimateLifeformSpec.ARMOR_PROTECTION
                && user.hasEffect(UltimateLifeformAddon.ULTIMATE_FIRE_ARMOR)
                || spec == UltimateLifeformSpec.ARMOR_FIRE_PROTECTION
                        && user.hasEffect(UltimateLifeformAddon.ULTIMATE_ARMOR)) {
            return ConditionCheck.createNegative("other_armor_active");
        }
        // 1.16 UltimateLifeformAction.checkConditions, right after the target,
        // with no creative exemption (the port's message is kept)
        if (data.getHamonCharge() < spec.hamonThreshold()) {
            return ConditionCheck.createNegative("no_hamon_charge");
        }
        // 1.16 NonStandAction.checkConditions asked for the energy next, before
        // the hands, the action's own conditions and isUnlocked (the mode).
        // getEnergyNeeded counts the held tick cost of hold actions only
        // (Wall Climbing declares one but is no hold action).
        if (!isCreative(context)
                && !data.hasEnergy(
                        spec.energyCost
                                + (spec.held ? spec.holdEnergyCost : 0.0F))) {
            return ConditionCheck.createNegative(
                    "no_energy_ultimate_lifeform");
        }
        // 1.16 UltimateLifeformWallClimb.checkHeldItems: MCUtil.areHandsFree
        // for both hands, where open-finger gloves count as a free hand.
        if (spec == UltimateLifeformSpec.WALL_CLIMBING
                && !UtilFunctions.areHandsFree(user,
                        InteractionHand.MAIN_HAND,
                        InteractionHand.OFF_HAND)) {
            return ConditionCheck.createNegative("hands");
        }
        ConditionCheck hands = checkHeldItems(user);
        if (!hands.isPositive()) {
            return hands;
        }
        // 1.16 checkSpecificConditions wants a wall to grip on every press,
        // also while climbing (a press then moves to that wall)
        if (spec == UltimateLifeformSpec.WALL_CLIMBING
                && !UltimateLifeformCombat.canStartWallClimbing(
                        user, target)) {
            return ConditionCheck.createNegative("wall");
        }
        // 1.16 UltimateLifeformHamonShock.checkSpecificConditions (:102-105):
        // the shock costs no energy but needs some
        if (spec == UltimateLifeformSpec.HAMON_SHOCK
                && data.getEnergy() <= 0.0F) {
            return ConditionCheck.createNegative("some_energy");
        }
        if (spec == UltimateLifeformSpec
                        .ULTIMATE_HAMON_TURQUOISE_BLUE_OVERDRIVE
                && !user.isInWaterOrBubble()) {
            return ConditionCheck.createNegative("underwater");
        }
        // 1.16 UltimateLifeformEnderTeleport.checkSpecificConditions (:52-54)
        if (spec == UltimateLifeformSpec.ENDERMAN_TELEPORT
                && user.isInWaterOrBubble()) {
            return ConditionCheck.createNegative("not_underwater");
        }
        // 1.16 UltimateLifeformHamonKick.checkSpecificConditions:
        // ActionConditionResult.noMessage(onGround), after the hands (the
        // port's message is kept)
        if (spec == UltimateLifeformSpec.ULTIMATE_HAMON_KICK
                && !user.onGround()) {
            return ConditionCheck.createNegative("ground");
        }
        // 1.16 UltimateLifeformSendoOverdrive.checkSpecificConditions: silent
        // while a Hamon Sendo Overdrive already targets that block face
        if (spec == UltimateLifeformSpec
                        .ULTIMATE_HAMON_SENDO_OVERDRIVE
                && UltimateLifeformCombat.isHamonSendoOverdriveOn(
                        user.level(),
                        target.getBlockPos(),
                        target.getFace())) {
            return ConditionCheck.NEGATIVE;
        }
        // 1.16 UltimateLifeformLifeMagnetism.checkSpecificConditions
        if (spec == UltimateLifeformSpec.HAMON_LIFE_MAGNETISM
                && !UltimateLifeformCombat.hasLeavesTargetOrItem(
                        user, target)) {
            return ConditionCheck.createNegative("leaves");
        }
        ConditionCheck own = checkOwnConditions(user, data);
        if (!own.isPositive()) {
            return own;
        }
        return checkMode(data);
    }

    /*
     * 1.16 PowerBaseImpl.tickHeldAction re-ran NonStandAction.checkConditions
     * on every held tick: the energy the hold still needed, the hands, the
     * action's own checkSpecificConditions, then isUnlocked (the mode). A
     * failure ended the hold with its message.
     */
    @Override
    protected ConditionCheck checkHeldSpecificConditions(
            EntityActionInstance action, Power<?> context) {
        if (!spec.held) {
            return ConditionCheck.POSITIVE;
        }
        UltimateLifeformData data = data(context);
        LivingEntity user = context.getUser();
        if (data == null || user == null) {
            return ConditionCheck.NEGATIVE;
        }
        // the target came first; without one the hold-only Absorption went
        // on without absorbing and nothing else was asked
        if (spec == UltimateLifeformSpec.ABSORPTION
                && UltimateLifeformCombat.absorptionTarget(user, data)
                        == null) {
            return ConditionCheck.NEGATIVE_CONTINUE_HOLD;
        }
        if (data.getHamonCharge() < spec.hamonThreshold()) {
            return ConditionCheck.NEGATIVE;
        }
        if (!isCreative(context)
                && !data.hasEnergy(heldEnergyNeeded(action))) {
            return ConditionCheck.createNegative(
                    "no_energy_ultimate_lifeform");
        }
        ConditionCheck hands = checkHeldItems(user);
        if (!hands.isPositive()) {
            return hands;
        }
        // 1.16 PillarmanDivineSandstorm.checkSpecificConditions, held only
        if ((spec == UltimateLifeformSpec.DIVINE_SANDSTORM
                        || spec == UltimateLifeformSpec.ATMOSPHERIC_RIFT)
                && data.getEnergy() == 0.0F) {
            return ConditionCheck.createNegative("no_energy_pillarman");
        }
        ConditionCheck own = checkOwnConditions(user, data);
        if (!own.isPositive()) {
            return own;
        }
        return checkMode(data);
    }

    // NonStandAction.getEnergyNeeded(ticksHeld): the energy cost is paid when
    // the charge fires, so a charge needs it on every tick; once the port's
    // server action runs, only the held tick cost is left.
    private float heldEnergyNeeded(EntityActionInstance action) {
        if (action.getPhase() == ActionPhase.BUTTON_CHARGE) {
            int toFire = (int) action.phasesLength.getFloat(
                    ActionPhase.BUTTON_CHARGE);
            return spec.energyCost + spec.holdEnergyCost * Math.max(
                    toFire - (int) action.getPhaseTick(), 1);
        }
        return spec.heldTickEnergyCost();
    }

    private ConditionCheck checkHeldItems(LivingEntity user) {
        return checkHeldItems(user, spec);
    }

    static ConditionCheck checkHeldItems(
            LivingEntity user, UltimateLifeformSpec spec) {
        // 1.16 UltimateLifeformLifeMagnetism.checkHeldItems: leaves in either
        // hand pass; otherwise the main hand must be free
        if (spec == UltimateLifeformSpec.HAMON_LIFE_MAGNETISM
                && (UltimateLifeformCombat.isLeavesItem(user.getMainHandItem())
                        || UltimateLifeformCombat.isLeavesItem(
                                user.getOffhandItem()))) {
            return ConditionCheck.POSITIVE;
        }
        // 1.16 MCUtil.isHandFree: open-finger gloves leave a hand free, and a
        // hand something else keeps busy is not free
        if (spec.requiresMainHandFree()
                && !UtilFunctions.isHandFree(user, InteractionHand.MAIN_HAND)
                || spec.requiresOffHandFree()
                && !UtilFunctions.isHandFree(user, InteractionHand.OFF_HAND)) {
            // 1.16 Action.checkHeldItems said "hand" for a move that needs
            // only a free main hand; the two-handed moves' own said "hands"
            return ConditionCheck.createNegative(
                    spec.requiresBothHandsFree() ? "hands" : "hand");
        }
        // 1.16 read the Caelus base value, which only the Ultimate Lifeform's own glide set to 1; here that
        // is the module's own modifier, so another mod's flight modifier does not count
        AttributeInstance flight = user.getAttribute(
                CaelusApi.getInstance().getFallFlyingAttribute());
        if (spec.requiresBothHandsFree()
                && flight != null
                && flight.hasModifier(
                        UltimateLifeformPowerType.FLIGHT_MODIFIER_ID)
                && !user.getItemBySlot(EquipmentSlot.CHEST)
                        .is(Items.ELYTRA)) {
            return ConditionCheck.createNegative("hands");
        }
        return ConditionCheck.POSITIVE;
    }

    private ConditionCheck checkOwnConditions(
            LivingEntity user, UltimateLifeformData data) {
        // 1.16 PillarmanAbsorption.checkSpecificConditions
        if (spec == UltimateLifeformSpec.ABSORPTION
                && user.level().getDifficulty() == Difficulty.PEACEFUL) {
            return ConditionCheck.createNegative("peaceful");
        }
        if (spec == UltimateLifeformSpec.UNLIMITED_POWER
                && data.getEnergy()
                        >= UltimateLifeformData.MAX_ENERGY) {
            return ConditionCheck.createNegative(
                    "unlimited_power");
        }
        // 1.16 read its own step counter, not the user's Absorption
        if (spec == UltimateLifeformSpec
                        .ABSOLUTE_IMMORTALITY
                && user.getHealth() >= user.getMaxHealth()
                && data.getImmortalityLevel() > 4) {
            return ConditionCheck.createNegative(
                    "absolute_immortality");
        }
        return ConditionCheck.POSITIVE;
    }

    private ConditionCheck checkMode(UltimateLifeformData data) {
        if (spec.requiredMode != null
                && data.getPreviousMode()
                        != spec.requiredMode) {
            return ConditionCheck.createNegative(
                    "wrong_ultimate_lifeform_mode");
        }
        return ConditionCheck.POSITIVE;
    }

    private boolean consumeInitial(LivingEntity user) {
        Power<?> context = getUserPower(user);
        UltimateLifeformData data = data(context);
        if (data == null) {
            return false;
        }
        boolean creative = isCreative(context);
        if (!creative && !data.hasEnergy(spec.energyCost)
                || data.getHamonCharge() < spec.hamonSpentOnStart()) {
            return false;
        }
        if (!creative) {
            data.consumeEnergy(user, spec.energyCost);
        }
        // 1.16 UltimateLifeformData.consumeHamonCharge subtracted before its
        // creative check, so a creative user paid Hamon too
        data.consumeHamonCharge(spec.hamonSpentOnStart());
        // 1.16 PowerBaseImpl.stopHeldAction gave a hold-only action its
        // cooldown when the hold ended (UltimateLifeformData.releaseHeld)
        if (!spec.holdOnly()) {
            data.setAbilityCooldown(
                    spec.id, spec.cooldownTicks(creative, -1));
        }
        data.syncOnUpdate(user);
        return true;
    }

    private static UltimateLifeformData data(
            Power<?> context) {
        return context != null
                && context.getCurTypeData()
                        instanceof UltimateLifeformData data
                ? data
                : null;
    }

    private static boolean isCreative(Power<?> context) {
        return context != null
                && context.getUser()
                        instanceof Player player
                && player.getAbilities().instabuild;
    }

    public static final class UltimateActionInstance
            extends EntityActionInstance {
        private boolean startedHeld;
        private ActionTarget inputTarget = ActionTarget.EMPTY;

        public UltimateActionInstance(EntityActionType ability) {
            super(ability);
            if (ability instanceof UltimateLifeformAbility ultimate) {
                userWalkSpeed = ultimate.spec.heldWalkSpeed();
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            super.onSetPhase(newPhase);
            if (ability instanceof UltimateLifeformAbility ultimate) {
                // 1.16: the Ultimate Hamon Overdrive's strike instance walked
                // at 0.5 (getWalkSpeed), 0.1 only while the key charged it
                userWalkSpeed = ultimate.spec
                                        == UltimateLifeformSpec
                                                .ULTIMATE_HAMON_OVERDRIVE
                                && newPhase == ActionPhase.PERFORM
                        ? 0.5F
                        : ultimate.spec.heldWalkSpeed();
                LivingEntity user = getPowerUser();
                if (user != null) {
                    UltimateLifeformCombat.playChargePhaseSound(
                            this, user, ultimate.spec, newPhase);
                }
                UltimateLifeformCombat.playSummonChargeSound(
                        user, ultimate.spec, newPhase);
                UltimateLifeformCombat.playHeldLoopSound(
                        this, user, ultimate.spec, newPhase);
                if (!level().isClientSide()
                        && user != null
                        && showsBlades(ultimate.spec)) {
                    UltimateLifeformData data = data(
                            ultimate.getUserPower(user));
                    if (data != null) {
                        data.setBladesVisible(
                                user,
                                newPhase == ActionPhase.BUTTON_CHARGE
                                        || newPhase == ActionPhase.PERFORM);
                    }
                }
            }
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()
                    || !(ability
                            instanceof UltimateLifeformAbility
                                    ultimateAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            // 1.16 performed at the target its checks passed; nothing is paid
            // when the Sendo Overdrive's or Hypnotize's target is gone by now
            if (user != null
                    && ultimateAbility.spec == UltimateLifeformSpec
                            .ULTIMATE_HAMON_SENDO_OVERDRIVE
                    && !UltimateLifeformCombat.checkSendoTarget(user,
                            UltimateLifeformCombat.sendoTarget(user))
                            .isPositive()) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (user != null
                    && ultimateAbility.spec == UltimateLifeformSpec.HYPNOTIZE
                    && !UltimateLifeformCombat.checkHypnosisTarget(user,
                            UltimateLifeformCombat.crosshairTarget(user))
                            .isPositive()) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (user != null
                    && ultimateAbility.spec
                            == UltimateLifeformSpec.HAMON_OVERDRIVE
                    && !UltimateLifeformCombat.checkOverdriveTarget(user,
                            UltimateLifeformCombat.crosshairTarget(user))
                            .isPositive()) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            if (user == null
                    || !ultimateAbility.consumeInitial(user)) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            UltimateLifeformData data = data(
                    ultimateAbility.getUserPower(user));
            if (data == null) {
                forceStop();
                syncPhaseChanges();
                return;
            }
            UltimateLifeformSpec spec =
                    ultimateAbility.spec;
            UltimateLifeformAcceptance.recordAccepted(
                    user, data, spec);
            if (spec.startsServerAction()) {
                startedHeld = data.beginHeld(
                        user, spec, inputTarget);
                if (!startedHeld) {
                    forceStop();
                    syncPhaseChanges();
                }
                else {
                    UltimateLifeformCombat.started(
                            user, data, spec);
                }
            }
            else {
                UltimateLifeformCombat.perform(
                        user,
                        data,
                        spec,
                        spec.chargeTicks);
            }
        }

        @Override
        public void actionTick() {
            if (level().isClientSide()
                    && ability instanceof UltimateLifeformAbility ultimate
                    && getPowerUser() != null) {
                UltimateLifeformCombat.tickClient(
                        this, getPowerUser(), ultimate.spec);
            }
            if (level().isClientSide()
                    || !startedHeld
                    || !(ability
                            instanceof UltimateLifeformAbility
                                    ultimateAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            UltimateLifeformData data = user != null
                    ? data(ultimateAbility.getUserPower(user))
                    : null;
            if (data == null
                    || data.getHeldAbility()
                            != ultimateAbility.spec) {
                startedHeld = false;
                startRecovery();
                syncPhaseChanges();
                return;
            }
            int maxTicks =
                    ultimateAbility.spec.maxActionTicks();
            if (maxTicks > 0
                    && data.getHeldTicks() >= maxTicks) {
                data.releaseHeld(user);
                startedHeld = false;
                startRecovery();
                syncPhaseChanges();
            }
        }

        @Override
        public void onButtonStopHold() {
            if (!(ability
                    instanceof UltimateLifeformAbility
                            ultimateAbility)) {
                return;
            }
            if (!ultimateAbility.spec.held) {
                return;
            }
            if (ultimateAbility.spec.chargeHeldUntilRelease()) {
                // 1.16 stopHeldAction(true) fired a charged hold on release
                // if it still passed its checks; the move then runs on its own
                if (getPhase() == ActionPhase.BUTTON_CHARGE) {
                    if (getPhaseTick() >= ultimateAbility.spec.chargeTicks
                            && ultimateAbility.canFireReleasedHold(this)) {
                        setPhaseStart(ActionPhase.PERFORM);
                    }
                    else {
                        forceStop();
                    }
                    syncPhaseChanges();
                }
                return;
            }
            LivingEntity user = getPowerUser();
            if (startedHeld && user != null) {
                UltimateLifeformData data = data(
                        ultimateAbility.getUserPower(user));
                if (data != null
                        && data.getHeldAbility()
                                == ultimateAbility.spec) {
                    data.releaseHeld(user);
                }
                startedHeld = false;
            }
            forceStop();
            syncPhaseChanges();
        }

        @Override
        public void onActionCleared(
                EntityActionInstance newAction) {
            if (!(ability instanceof UltimateLifeformAbility
                    ultimateAbility)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user != null) {
                UltimateLifeformData data = data(
                        ultimateAbility.getUserPower(user));
                if (data != null) {
                    if (startedHeld
                            && data.getHeldAbility()
                                    == ultimateAbility.spec) {
                        data.releaseHeld(user);
                    }
                    if (showsBlades(ultimateAbility.spec)) {
                        data.setBladesVisible(user, false);
                    }
                }
            }
            startedHeld = false;
        }

        @Override
        protected boolean shouldHoldPhaseAtEnd() {
            return getPhase() == ActionPhase.BUTTON_CHARGE
                    && ability instanceof UltimateLifeformAbility ultimate
                    && ultimate.spec.chargeHeldUntilRelease();
        }

        /*
         * 1.16 PowerBaseImpl.checkRequirements refused any other action while
         * one was held, unless it heldAllowsOtherAction (only Absorption), and
         * Action.checkConditions refused any while a player action ran; here
         * the press waits in the core input buffer instead of cancelling.
         */
        @Override
        public boolean canBeCancelledInto(
                EntityActionType cancellingAbility) {
            return getPhase() == ActionPhase.RECOVERY
                    || ability instanceof UltimateLifeformAbility ultimate
                            && ultimate.spec == UltimateLifeformSpec.ABSORPTION;
        }

        // 1.16 PillarmanAbsorption.heldAllowsOtherAction (:124-126), which
        // UltimateLifeformAbsorbtion inherited: the power's other actions
        // stayed usable (and bright on the HUD) while it was held
        @Override
        public boolean heldAllowsOtherAbilities() {
            return ability instanceof UltimateLifeformAbility ultimate
                    && ultimate.spec == UltimateLifeformSpec.ABSORPTION;
        }

        // 1.16 set the continuous player action in perform, so a charge before
        // it was none; the port's RECOVERY has no 1.16 counterpart
        @Override
        public boolean isPlayerAction() {
            return getPhase() == ActionPhase.PERFORM
                    && ability instanceof UltimateLifeformAbility ultimate
                    && ultimate.spec.isPlayerAction();
        }

        // 1.16 held the client's mouse target as sent, nothing behind an
        // empty one
        private void captureInputTarget(LivingEntity user) {
            inputTarget = captureActionTargetFromAim(user);
        }

        private static boolean showsBlades(
                UltimateLifeformSpec spec) {
            return spec == UltimateLifeformSpec.BLADE_SLASH
                    || spec == UltimateLifeformSpec.BLADE_DASH_ATTACK
                    || spec == UltimateLifeformSpec.BLADE_BARRAGE
                    || spec == UltimateLifeformSpec.LIGHT_FLASH
                    || spec == UltimateLifeformSpec.LIGHT_FLASH_DECOY;
        }
    }
}
