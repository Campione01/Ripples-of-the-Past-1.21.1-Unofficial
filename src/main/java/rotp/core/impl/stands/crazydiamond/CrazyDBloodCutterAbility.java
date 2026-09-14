package rotp.core.impl.stands.crazydiamond;

import rotp.core.powersystem.standpower.StandInstance.StandPart;

import rotp.core.client.ClientGlobals;
import rotp.core.client.sound.ClientsideSoundsHelper;
import rotp.core.client.sound.sounds.EntityLingeringSoundInstance;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public class CrazyDBloodCutterAbility extends StandEntityAbility {
	private static final ActionAnimIdentifier BLOOD_CUTTER_ANIM = ActionAnimIdentifier.getOrCreate("blood_cutter", false);
	private static final float STAMINA_COST = 25F;
	private static final float SHOT_VELOCITY = 2.0F;
	private static final float SHOT_INACCURACY = 1.0F;

	public CrazyDBloodCutterAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, CutterShot::new);
		partsRequired(StandPart.ARMS);
		setDefaultPhaseLength(ActionPhase.WINDUP, 5);
		cooldown(300);
	}

	@Override
	public ConditionCheck checkSpecificConditions(Power<?> power) {
		LivingEntity user = power.getUser();
		if (user.getHealth() >= user.getMaxHealth()
				&& !(user instanceof Player player && player.getAbilities().invulnerable)) {
			return ConditionCheck.createNegative("full_health");
		}
		ConditionCheck check = super.checkSpecificConditions(power);
		return check.isPositive() ? StandAbilityStamina.check(power, STAMINA_COST) : check;
	}

	@Override
	public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
		return BLOOD_CUTTER_ANIM;
	}

	@Override
	public int getCooldown(Power<?> context, int ticksHeld) {
		int cooldown = super.getCooldown(context, ticksHeld);
		LivingEntity user = context.getUser();
		if (cooldown > 0 && user != null && user.getMaxHealth() > 0) {
			return Mth.ceil((float) cooldown * user.getHealth() / user.getMaxHealth());
		}
		return cooldown;
	}


	public static class CutterShot extends EntityActionInstance {

		public CutterShot(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(EntityActionInstance prevAction) {
			setStandOffset(-0.1, -0.5, StandOffsetFromUser.Rotations.BODY, false);
		}

		@Override
		public void actionPerformStart() {
			Level level = level();
			if (!level.isClientSide()) {
				LivingEntity user = getPowerUser();
				if (user == null) return;
				StandPower standPower = StandPower.get(user);
				if (!StandAbilityStamina.consumeOrMessage(ability, standPower, user, STAMINA_COST)) {
					return;
				}
				CrazyDBloodCutterEntity cutter = new CrazyDBloodCutterEntity(user, level);
				cutter.setShootingPosOf(user);
				
				float inaccuracy = performer instanceof StandEntity stand
						? StandStatFormulas.projectileInaccuracyScaling(stand.getPrecision(), SHOT_INACCURACY)
						: SHOT_INACCURACY;
				cutter.shootFromRotation(user, SHOT_VELOCITY, inaccuracy);
				addProjectileWithStandStats(cutter);
			}
		}
		
		@Override
		public void onSetPhase(ActionPhase newPhase) {
			Level level = level();
			if (level.isClientSide() && performer instanceof StandEntity stand && ClientGlobals.canHearStand(stand)) {
				switch (newPhase) {
					case WINDUP -> {
						ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
								ModSoundEvents.CRAZY_DIAMOND_FIX_STARTED.get(), stand), 
								stand.getSoundSource(), 1, 1, stand, level));
						if (!stand.isArmsOnlyMode()) {
							ClientsideSoundsHelper.playNonVanillaClassSound(new EntityLingeringSoundInstance(ClientsideSoundsHelper.withStandSkin(
									ModSoundEvents.CRAZY_DIAMOND_DORA.get(), stand), 
									stand.getSoundSource(), 1, 1, stand, level));
						}
					}
					case PERFORM -> {
						level.playLocalSound(stand, ClientsideSoundsHelper.withStandSkin(
								ModSoundEvents.CRAZY_DIAMOND_BLOOD_CUTTER_SHOT.get(), stand), 
								stand.getSoundSource(), 1, 1);
					}
					default -> {}
				}
			}
		}

	}
	
	public static void onBleedingAdded(LivingEntity entity) {
		StandPower power = StandPower.get(entity);
		if (power == null) {
			return;
		}
		if (power.isAbilityUnlocked("blood_cutter")) {
			power.setAbilityCooldown("blood_cutter", 0);
		}
	}

}
