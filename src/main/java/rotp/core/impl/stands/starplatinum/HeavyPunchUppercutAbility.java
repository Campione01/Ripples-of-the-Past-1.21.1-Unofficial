package rotp.core.impl.stands.starplatinum;

import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public class HeavyPunchUppercutAbility extends StandEntityHeavyPunchAbility {

	public HeavyPunchUppercutAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId);
		this.createActionObj = Uppercut::new;
	}
	
	public static class Uppercut extends StandEntityHeavyPunch {

		public Uppercut(EntityActionType ability) {
			super(ability);
		}
		
		@Override
		protected void addKnockback(DamageSource dmgSource) {
			DamageSourceModified knockback = (DamageSourceModified) dmgSource;
			StandEntity stand = (StandEntity) performer;
			float strength = (float) stand.getAttackDamage();
			float uppercutKnockback = 0.5F + strength / 16 * stand.getLastHeavyFinisherValue();
			knockback.jojo_ripples$modifyKnockback(uppercutKnockback, 1);
			knockback.jojo_ripples$knockbackXRot(-60F);
		}
		
		@Override
		protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource, float dmgAmount, boolean hurt) {
			if (hurt && targetLiving instanceof StandEntity targetStand) {
				targetStand.breakStandBlocking(StandStatFormulas.getGuardBreakTicks(targetStand.getDurability()));
			}
		}
		
	}

}
