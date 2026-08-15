package com.github.standobyte.jojoimpl.stands.hierophant;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.entityattachment.ComponentUtil;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectType;
import com.github.standobyte.jojo.event.RipplesAbilityKeyPressEvent;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.init.ModSpecialActions;
import com.github.standobyte.jojo.init.power.ModStandAbilities;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.ability.Ability;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInstance;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandUtil;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectInstance;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity.StandFlag;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.EntityComponentController;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.ClientEntityController;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.client.mob.ClientMobController;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityManualControlToggle;

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
