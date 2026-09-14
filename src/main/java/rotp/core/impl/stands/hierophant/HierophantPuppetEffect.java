package rotp.core.impl.stands.hierophant;

import rotp.core.client.ClientProxy;
import rotp.core.core.JojoMod;
import rotp.core.entityattachment.ComponentUtil;
import rotp.core.entityattachment.custom_effect.EntityCustomEffectType;
import rotp.core.event.RipplesAbilityKeyPressEvent;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandEntity.StandFlag;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.entity_puppetcontrol.client.ClientEntityController;
import rotp.core.subsystems.entity_puppetcontrol.client.mob.ClientMobController;
import rotp.core.impl.stands._entitybase.StandEntityManualControlToggle;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public class HierophantPuppetEffect extends StandEffectInstance {

	public HierophantPuppetEffect(EntityCustomEffectType<?> effectType) {
		super(effectType);
		needsTarget = true;
	}

	@Override
	protected void start() {
		if (!level.isClientSide() || this.getEntity() == ClientProxy.getClientPlayer()) {
			LivingEntity targetEntity = getTargetLiving();
			if (targetEntity != null) {
				StandPower standPower = getUserPower();
				StandEntity hierophant = standPower.getSummonedStandEntity();
				if (hierophant != null) {
					if (!level.isClientSide()) {
						LivingComponentPossession.setPossessionTarget(hierophant, targetEntity, "hierophant");
						hierophant.setCanFollowUser(false);
					}
					setMobControl(true);
				}
			}
		}
	}

	@Override
	protected void tick() {
		if (!level.isClientSide()) {
			StandEntity hierophant = getUserPower().getSummonedStandEntity();
			if (hierophant != null && isRetractingToUnsummon(hierophant)) {
				remove();
			}
		}
	}

	@Override
	protected void stop() {
		LivingEntity user = getStandUser();
		Level level = user.level();

		if (!level.isClientSide() || user == ClientProxy.getClientPlayer()) {
			StandEntity hierophant = getUserPower().getSummonedStandEntity();
			if (hierophant != null) {
				boolean wasInMobControl = hierophant.getStandFlag(StandFlag.MANUAL_CONTROL);
				boolean wasRetracting = hierophant.isBeingRetracted()
						|| hasUnsummonAction(hierophant);
				setMobControl(false);

				LivingComponentPossession possession = ComponentUtil.getExistingDataOrNull(
						hierophant, ModDataAttachmentTypes.ENTITY_POSSESSION);
				if (possession != null) {
					possession.updatePosition();
					if (!level.isClientSide()) {
						possession.stopPossession();
					}
				}

				// setMobControl(false) clears retraction while following is disabled.
				hierophant.setCanFollowUser(true);
				if (!wasRetracting) {
					if (wasInMobControl) {
						StandEntityManualControlToggle.on(level, hierophant);
					}
					else {
						hierophant.retract();
					}
				}
			}
		}
	}

	@Override
	protected boolean shouldClearTarget(Entity target, @Nullable LivingEntity targetLiving) {
		if (super.shouldClearTarget(target, targetLiving)) {
			return true;
		}
		Entity.RemovalReason removalReason = target.getRemovalReason();
		return removalReason != null && removalReason.shouldDestroy();
	}

	private static boolean isRetractingToUnsummon(StandEntity hierophant) {
		return hierophant.isBeingRetracted() && hasUnsummonAction(hierophant);
	}

	private static boolean hasUnsummonAction(StandEntity hierophant) {
		EntityActionInstance action = hierophant.getCurStandAction();
		return action != null && action.ability == ModSpecialActions.STAND_UNSUMMON.get();
	}

	public void setMobControl(boolean control) {
		LivingEntity user = getStandUser();

		if (!user.level().isClientSide()) {
			if (control) {
				if (getTargetLiving() instanceof Mob targetMob) {
					EntityComponentController.setControlTarget(user, targetMob, "mob");
				}
			}
			else {
				EntityComponentController component = ComponentUtil.getExistingDataOrNull(
						user, ModDataAttachmentTypes.CONTROLLER);
				if (component != null) {
					component.stopControlling();
				}
			}

			StandEntity hierophant = getUserPower().getSummonedStandEntity();
			if (hierophant != null) {
				hierophant.setManuallyControlled(control);
			}
		}
		else if (user == ClientProxy.getClientPlayer()) {
			if (control) {
				if (getTargetLiving() instanceof Mob targetMob) {
					ClientEntityController.setInstance(new ClientMobController(targetMob));
				}
			}
			else {
				ClientEntityController.setInstance(null);
			}
		}
	}

	@SubscribeEvent
	public static void onManualControlToggle(RipplesAbilityKeyPressEvent event) {
		Ability ability = event.getAbility();
		if (ability.abilityId.powerClass() == PowerClass.STAND && ability.name().equals("manual_control")) {
			LivingEntity user = event.getEntity();
			StandPower standPower = StandPower.get(user);
			if (standPower != null) {
				HierophantPuppetEffect puppeting = standPower.userStandEffects
						.getEffectOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
						.orElse(null);
				if (puppeting != null) {
					event.setCanceled(true);

					StandEntity stand = StandUtil.getSummonedStand(user);
					if (stand != null) {
						puppeting.setMobControl(!stand.getStandFlag(StandFlag.MANUAL_CONTROL));
					}
				}
			}
		}
	}
}
