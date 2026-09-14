package rotp.core.impl.powers.pillarman.abilities;

import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.util.functions.DamageUtil;
import rotp.core.impl.powers.pillarman.PillarmanMode;

import net.minecraft.util.RandomSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class PillarmanUnnaturalAgilityAbility extends PillarmanActionAbility {

	public PillarmanUnnaturalAgilityAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, 2, PillarmanMode.NONE, false, 0.0F, 1.0F, 0.0F, 0,
				UnnaturalAgilityInstance::new);
		setButtonHoldPhase(ActionPhase.PERFORM);
	}

	public static boolean onUserIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || !target.isAlive()) {
			return false;
		}
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(target);
		if (action == null || !(action.ability instanceof PillarmanEvasionAbility
				|| action.ability instanceof PillarmanUnnaturalAgilityAbility)) {
			return false;
		}
		DamageSource source = event.getSource();
		Entity attacker = source.getDirectEntity();
		if (attacker == null || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		if (!canEvadeIncomingDamage(target, attacker)) {
			return false;
		}
		RandomSource random = target.getRandom();
		if (action.ability instanceof PillarmanUnnaturalAgilityAbility
				&& attacker instanceof LivingEntity attackerLiving
				&& !(attacker instanceof StandEntity)
				&& random.nextFloat() < 0.3F) {
			float counterDamage = DamageUtil.getDamageWithoutHeldItem(target) * 0.75F;
			if (target instanceof Player player) {
				attackerLiving.hurt(target.damageSources().playerAttack(player), counterDamage);
			}
			else {
				attackerLiving.hurt(target.damageSources().mobAttack(target), counterDamage);
			}
		}
		target.level().playSound(null, attacker, ModSoundEvents.PILLAR_MAN_EVASION.get(),
				attacker.getSoundSource(), 1.0F, 1.0F);
		return true;
	}

	public static boolean canEvadeIncomingDamage(LivingEntity target, Entity attacker) {
		if (attacker instanceof StandEntity && !StandUtil.entityCanSeeStands(target)) {
			return false;
		}
		if (attacker instanceof ModdedProjectileEntity projectile) {
			return projectile.canBeEvaded(target) && (!projectile.standDamage() || StandUtil.entityCanSeeStands(target));
		}
		return attacker instanceof LivingEntity || attacker instanceof Projectile;
	}

	public static class UnnaturalAgilityInstance extends PillarmanHeldActionInstance {

		public UnnaturalAgilityInstance(EntityActionType ability) {
			super(ability);
		}
	}
}
