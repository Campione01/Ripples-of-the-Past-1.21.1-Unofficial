package com.github.standobyte.jojoimpl.stands.starplatinum;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.powersystem.ability.AbilityId;
import com.github.standobyte.jojo.powersystem.ability.AbilityType;
import com.github.standobyte.jojo.powersystem.entityaction.type.EntityActionType;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandStatFormulas;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityHeavyPunchAbility;

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
