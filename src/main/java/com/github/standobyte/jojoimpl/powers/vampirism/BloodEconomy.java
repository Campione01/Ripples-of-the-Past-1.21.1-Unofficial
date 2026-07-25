package com.github.standobyte.jojoimpl.powers.vampirism;

/**
 * Slice 5a framework extension — Vampirism blood-economy minimum API.
 *
 * <p>Anchor for blood-energy resource tracking, drain rate, and difficulty-scaled
 * multipliers. Used by VampirismBloodDrain / VampirismBloodGift / VampirismFreeze
 * and the broader transformation lane (the latter routed to Category 6 future
 * authorization).</p>
 *
 * <p>Slice 5a delivers the data carrier + getters/setters. Tick hook integration
 * (sun-burn, daytime drain, low-blood weakness) is consumed by Slice 5b Vampirism
 * family follow-up.</p>
 */
public final class BloodEconomy {

	public static final float DEFAULT_MAX_BLOOD = 1000.0F;
	public static final float DEFAULT_DRAIN_PER_TICK = 0.005F;

	private float currentBlood;
	private float maxBlood;
	private float drainPerTick;

	public BloodEconomy() {
		this(DEFAULT_MAX_BLOOD);
	}

	public BloodEconomy(float maxBlood) {
		this.maxBlood = Math.max(maxBlood, 1.0F);
		this.currentBlood = 0.0F;
		this.drainPerTick = DEFAULT_DRAIN_PER_TICK;
	}

	public float current() {
		return currentBlood;
	}

	public float max() {
		return maxBlood;
	}

	public float drainPerTick() {
		return drainPerTick;
	}

	public void setDrainPerTick(float drainPerTick) {
		this.drainPerTick = drainPerTick;
	}

	public void setCurrent(float amount) {
		currentBlood = Math.max(0.0F, Math.min(maxBlood, amount));
	}

	public void setMax(float maxBlood, boolean preserveRatio) {
		float sanitizedMax = Math.max(maxBlood, 1.0F);
		float ratio = this.maxBlood > 0.0F ? currentBlood / this.maxBlood : 0.0F;
		this.maxBlood = sanitizedMax;
		setCurrent(preserveRatio ? ratio * sanitizedMax : currentBlood);
	}

	public void consume(float amount) {
		currentBlood = Math.max(0.0F, currentBlood - amount);
	}

	public void replenish(float amount) {
		currentBlood = Math.min(maxBlood, currentBlood + amount);
	}

	public boolean isExhausted() {
		return currentBlood <= 0.0F;
	}
}
