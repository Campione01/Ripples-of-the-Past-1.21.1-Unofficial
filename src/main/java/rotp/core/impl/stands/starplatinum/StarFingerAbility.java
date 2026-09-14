package rotp.core.impl.stands.starplatinum;

import org.jetbrains.annotations.Nullable;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionOBB;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.subsystems.hitboxes.ExtendableOBB;
import rotp.core.subsystems.hitboxes.OrientedBoundingBox;
import rotp.core.util.functions.JojoModUtil;
import rotp.core.util.functions.MathUtil;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class StarFingerAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier STAR_FINGER_ANIM = ActionAnimIdentifier.getOrCreate("star_finger", false);
	private static final float STAR_FINGER_STAMINA_COST = 375F;

	public StarFingerAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, StarFingerInstance::new);
		setDefaultPhaseLength(ActionPhase.PERFORM, 20);
		partsRequired(StandPart.ARMS);
		cooldown(20, 60);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> context) {
		ConditionCheck check = super.checkSpecificConditions(context);
		return check.isPositive() ? StandAbilityStamina.check(context, STAR_FINGER_STAMINA_COST) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return STAR_FINGER_ANIM;
	}

	@Override
	public void initActionFromConfig(EntityActionInstance action, Level level,
			LivingEntity powerUser, LivingEntity performer) {
		super.initActionFromConfig(action, level, powerUser, performer);
		if (!level.isClientSide() && powerUser != null) {
			JojoModUtil.sayVoiceLine(powerUser, ModSoundEvents.JOTARO_STAR_FINGER);
		}
	}

    public static class StarFingerInstance extends EntityActionInstance implements ActionOBB {
        public StarFingerInstance(EntityActionType ability) {
            super(ability);
        }

        private ExtendableOBB starFingerBB;

        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            setStandOffset(0, 0.5, StandOffsetFromUser.Rotations.BODY, false);
            OrientedBoundingBox obb = new OrientedBoundingBox(new Vec3(0, 1.35, 0), 0.125d, 0.125d, 0.8d, getPerformer().getYRot(), getPerformer().getXRot());
            this.starFingerBB = new ExtendableOBB(obb, 0.3F, 0.9F, (int) phasesLength.getFloat(ActionPhase.PERFORM), 4, new Vec3(0, 1.35, 0));
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower standPower = user != null ? StandPower.get(user) : null;
            if (user == null || !StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAR_FINGER_STAMINA_COST)) {
                startRecovery();
                return;
            }
            if (getPerformer() instanceof StandEntity stand) {
                SPStarFingerEntity starFinger = new SPStarFingerEntity(stand, level());
                starFinger.setLifeSpan(Mth.ceil(phasesLength.getFloat(ActionPhase.PERFORM)));
                starFinger.setShootingPosOf(stand);
                addProjectileWithStandStats(starFinger);
            }
        }

        @Override
        public void actionTick() {
            if (getPhase() == ActionPhase.PERFORM && extendableOBB() != null){
                Vec3 pos = getPerformer().position();
                Vec3 offset = new Vec3(0.07, 1.5, 1)
                        .yRot(-getPerformer().yBodyRot * MathUtil.DEG_TO_RAD);
                this.extendableOBB().updatePosition(level(), pos, offset, getPerformer().getXRot(), getPerformer().getYRot());
                this.extendableOBB().tick();
                if (this.extendableOBB().isRetracted()){
                    setPhaseStart(ActionPhase.RECOVERY);
                    syncPhaseChanges();
                }
            }
        }

        @Override
        public void onSetPhase(ActionPhase newPhase) {
            Level level = level();
            if (newPhase == ActionPhase.PERFORM){
                if (level.isClientSide() && performer instanceof StandEntity stand) {
                    if (ClientGlobals.canHearStand(stand)){
                        level.playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
                                        ModSoundEvents.STAR_PLATINUM_STAR_FINGER.get(), stand),
                                stand.getSoundSource(), 1, 1);
                    }
                }
            }
        }

        @Override
        public ExtendableOBB extendableOBB() {
            return starFingerBB;
        }
    }
}
