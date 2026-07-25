package com.github.standobyte.jojo.customobjects;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.functions.MathUtil;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public interface DamageSourceModified {
	void jojo_ripples$modifyKnockback(float add, float multiply);
	void jojo_ripples$verticalKnockback(float strength, float angleRatio);
	void jojo_ripples$knockbackXRot(float xRotDeg);
	void jojo_ripples$knockbackYRot(float yRotDeg);
	void jojo_ripples$setKnockbackXRotAppliedStrength(float strength);
	void jojo_ripples$setStandInvulTicks(int ticks);
	void jojo_ripples$setBarrageHitsCount(int hits);
	void jojo_ripples$setStandPower(StandPower standPower);
	
	float jojo_ripples$knockbackMultiplier();
	float jojo_ripples$addKnockback();
	float jojo_ripples$verticalKnockbackStrength();
	float jojo_ripples$verticalKnockbackAngleRatio();
	float jojo_ripples$knockbackXRotDeg();
	float jojo_ripples$knockbackYRotDeg();
	float jojo_ripples$knockbackXRotAppliedStrength();
	int jojo_ripples$standInvulTicks();
	int jojo_ripples$barrageHitsCount();
	StandPower jojo_ripples$standPower();
	
	
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void _onKnockbackEvent(LivingKnockBackEvent event) {
		LivingEntity target = event.getEntity();
		if (!target.damageContainers.isEmpty()) {
			DamageContainer curDamage = target.damageContainers.peek();
			DamageSource dmgSource = curDamage.getSource();
			if (dmgSource instanceof DamageSourceModified kbModifier) {
				event.setStrength((event.getStrength() + kbModifier.jojo_ripples$addKnockback()) * kbModifier.jojo_ripples$knockbackMultiplier());
				float yRotDeg = kbModifier.jojo_ripples$knockbackYRotDeg();
				if (yRotDeg != 0) {
					double ratioX = event.getRatioX();
					double ratioZ = event.getRatioZ();
					float yRot = yRotDeg * MathUtil.DEG_TO_RAD;
					double sin = Math.sin(yRot);
					double cos = Math.cos(yRot);
					event.setRatioX(ratioX * cos - ratioZ * sin);
					event.setRatioZ(ratioX * sin + ratioZ * cos);
				}
			}
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void _onKnockbackXRotEvent(LivingKnockBackEvent event) {
		LivingEntity target = event.getEntity();
		if (!target.damageContainers.isEmpty()) {
			DamageContainer curDamage = target.damageContainers.peek();
			DamageSource dmgSource = curDamage.getSource();
			if (dmgSource instanceof DamageSourceModified kbModifier) {
				float xRotDeg = kbModifier.jojo_ripples$knockbackXRotDeg();
				if (xRotDeg != 0) {
					float finalStrength = event.getStrength();
					kbModifier.jojo_ripples$setKnockbackXRotAppliedStrength(finalStrength);
					float xRotRad = xRotDeg * MathUtil.DEG_TO_RAD;
					if (Math.abs(xRotDeg) < 90F) {
						event.setStrength(finalStrength * Mth.cos(xRotRad));
					}
					else {
						event.setStrength(0);
					}
				}
			}
		}
	}
	
	public static void afterKnockbackApplied(LivingEntity target, @Nullable DamageSource dmgSource) {
		if (dmgSource instanceof DamageSourceModified kbModifier) {
			float xRotDeg = kbModifier.jojo_ripples$knockbackXRotDeg();
			float appliedStrength = kbModifier.jojo_ripples$knockbackXRotAppliedStrength();
			if (xRotDeg != 0 && appliedStrength != 0) {
				float vertical = -appliedStrength * Mth.sin(xRotDeg * MathUtil.DEG_TO_RAD);
				if (vertical != 0) {
					double kbRes = AttributeUtil.getValueOrDefault(target, Attributes.KNOCKBACK_RESISTANCE);
					if (kbRes < 1) {
						vertical *= (1 - kbRes);
						target.setDeltaMovement(target.getDeltaMovement().add(0, vertical, 0));
						target.hurtMarked = true;
					}
				}
			}

			float vertical = kbModifier.jojo_ripples$verticalKnockbackStrength();
			if (vertical > 0) {
				float angle = (float) Math.PI / 2 * kbModifier.jojo_ripples$verticalKnockbackAngleRatio();
				if (angle > 0) {
					double kbRes = AttributeUtil.getValueOrDefault(target, Attributes.KNOCKBACK_RESISTANCE);
					if (kbRes < 1) {
						vertical *= (1 - kbRes);
						Vec3 movement = target.getDeltaMovement();

						float sin = Mth.sin(angle);
						float cos = Mth.cos(angle);

						Vec3 newMovement = new Vec3(movement.x * cos, movement.y + vertical * sin, movement.z * cos);

						target.setDeltaMovement(newMovement);
						target.hurtMarked = true;
					}
				}
			}
		}
	}
	
}
