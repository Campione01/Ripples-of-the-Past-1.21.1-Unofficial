package com.github.standobyte.jojo.mixin.damage;

import org.spongepowered.asm.mixin.Mixin;

import com.github.standobyte.jojo.customobjects.DamageSourceModified;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;

@Mixin(DamageSource.class)
public class DamageSourceMixin implements DamageSourceModified {
	private float jojo_ripples$addKnockback = 0;
	private float jojo_ripples$knockbackMultiplier = 1;
	private float jojo_ripples$verticalKnockbackStrength = 0;
	private float jojo_ripples$verticalKnockbackAngleRatio = 0;
	private float jojo_ripples$knockbackXRotDeg = 0;
	private float jojo_ripples$knockbackYRotDeg = 0;
	private float jojo_ripples$knockbackXRotAppliedStrength = 0;
	private int jojo_ripples$standInvulTicks = 0;
	private int jojo_ripples$barrageHits = 0;
	private StandPower jojo_ripples$standPower = null;
	
	@Override
	public void jojo_ripples$modifyKnockback(float add, float multiply) {
		this.jojo_ripples$addKnockback = add;
		this.jojo_ripples$knockbackMultiplier = multiply;
	}
	
	@Override
	public void jojo_ripples$verticalKnockback(float strength, float angleRatio) {
		this.jojo_ripples$verticalKnockbackStrength = Mth.clamp(strength, 0, 1);
		this.jojo_ripples$verticalKnockbackAngleRatio = Mth.clamp(angleRatio, 0, 1);
	}

	@Override
	public void jojo_ripples$knockbackXRot(float xRotDeg) {
		this.jojo_ripples$knockbackXRotDeg = Mth.clamp(xRotDeg, -90F, 90F);
	}

	@Override
	public void jojo_ripples$knockbackYRot(float yRotDeg) {
		this.jojo_ripples$knockbackYRotDeg = Mth.wrapDegrees(yRotDeg);
	}

	@Override
	public void jojo_ripples$setKnockbackXRotAppliedStrength(float strength) {
		this.jojo_ripples$knockbackXRotAppliedStrength = strength;
	}

	@Override
	public void jojo_ripples$setStandInvulTicks(int ticks) {
		this.jojo_ripples$standInvulTicks = Math.max(ticks, 0);
	}

	@Override
	public void jojo_ripples$setBarrageHitsCount(int hits) {
		this.jojo_ripples$barrageHits = Math.max(hits, 0);
	}

	@Override
	public void jojo_ripples$setStandPower(StandPower standPower) {
		this.jojo_ripples$standPower = standPower;
	}
	
	@Override
	public float jojo_ripples$knockbackMultiplier() {
		return jojo_ripples$knockbackMultiplier;
	}
	
	@Override
	public float jojo_ripples$addKnockback() {
		return jojo_ripples$addKnockback;
	}
	
	@Override
	public float jojo_ripples$verticalKnockbackStrength() {
		return jojo_ripples$verticalKnockbackStrength;
	}
	
	@Override
	public float jojo_ripples$verticalKnockbackAngleRatio() {
		return jojo_ripples$verticalKnockbackAngleRatio;
	}

	@Override
	public float jojo_ripples$knockbackXRotDeg() {
		return jojo_ripples$knockbackXRotDeg;
	}

	@Override
	public float jojo_ripples$knockbackYRotDeg() {
		return jojo_ripples$knockbackYRotDeg;
	}

	@Override
	public float jojo_ripples$knockbackXRotAppliedStrength() {
		return jojo_ripples$knockbackXRotAppliedStrength;
	}

	@Override
	public int jojo_ripples$standInvulTicks() {
		return jojo_ripples$standInvulTicks;
	}

	@Override
	public int jojo_ripples$barrageHitsCount() {
		return jojo_ripples$barrageHits;
	}

	@Override
	public StandPower jojo_ripples$standPower() {
		return jojo_ripples$standPower;
	}
}
