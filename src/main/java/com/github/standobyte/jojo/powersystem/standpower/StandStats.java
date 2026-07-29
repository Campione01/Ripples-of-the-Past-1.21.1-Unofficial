package com.github.standobyte.jojo.powersystem.standpower;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.github.standobyte.jojo.config.JsonConfigurable;
import com.github.standobyte.jojo.init.ModEntityAttributes;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.util.functions.AttributeUtil;
import com.github.standobyte.jojo.util.objects_java.DefaultedValue;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

public class StandStats implements JsonConfigurable {
	public static final double DEFAULT_RANDOM_WEIGHT = 2.0D;

	private final DefaultedValue.Double powerBase;
	private final DefaultedValue.Double powerMax;
	private final DefaultedValue.Double speedBase;
	private final DefaultedValue.Double speedMax;
	private final DefaultedValue.Double rangeEffective;
	private final DefaultedValue.Double rangeMax;
	private final DefaultedValue.Double durabilityBase;
	private final DefaultedValue.Double durabilityMax;
	private final DefaultedValue.Double precisionBase;
	private final DefaultedValue.Double precisionMax;
	private final DefaultedValue.Double randomWeight;
	
	public StandStats(double power, double speed, double rangeEffective, double rangeMax, double durability, double precision) {
		this(power, power, speed, speed, rangeEffective, rangeMax,
				durability, durability, precision, precision,
				DEFAULT_RANDOM_WEIGHT);
	}

	public StandStats(double powerBase, double powerMax, double speedBase, double speedMax, double rangeEffective, double rangeMax,
			double durabilityBase, double durabilityMax, double precisionBase, double precisionMax) {
		this(powerBase, powerMax, speedBase, speedMax, rangeEffective,
				rangeMax, durabilityBase, durabilityMax, precisionBase,
				precisionMax, DEFAULT_RANDOM_WEIGHT);
	}

	public StandStats(double powerBase, double powerMax, double speedBase,
			double speedMax, double rangeEffective, double rangeMax,
			double durabilityBase, double durabilityMax,
			double precisionBase, double precisionMax,
			double randomWeight) {
		this.powerBase = new DefaultedValue.Double(powerBase);
		this.powerMax = new DefaultedValue.Double(Math.max(powerBase, powerMax));
		this.speedBase = new DefaultedValue.Double(speedBase);
		this.speedMax = new DefaultedValue.Double(Math.max(speedBase, speedMax));
		double effectiveRange = Math.max(rangeEffective, 1.0D);
		this.rangeEffective = new DefaultedValue.Double(effectiveRange);
		this.rangeMax = new DefaultedValue.Double(Math.max(effectiveRange, rangeMax));
		this.durabilityBase = new DefaultedValue.Double(durabilityBase);
		this.durabilityMax = new DefaultedValue.Double(Math.max(durabilityBase, durabilityMax));
		this.precisionBase = new DefaultedValue.Double(precisionBase);
		this.precisionMax = new DefaultedValue.Double(Math.max(precisionBase, precisionMax));
		this.randomWeight = new DefaultedValue.Double(randomWeight);
	}

	public double getValue(StatWithValue field) {
		return getField(field).value;
	}

	protected DefaultedValue.Double getField(StatWithValue field) {
		return switch (field) {
			case POWER -> powerBase;
			case POWER_MAX -> powerMax;
			case SPEED -> speedBase;
			case SPEED_MAX -> speedMax;
			case RANGE_EFFECTIVE -> rangeEffective;
			case RANGE_MAX -> rangeMax;
			case DURABILITY -> durabilityBase;
			case DURABILITY_MAX -> durabilityMax;
			case PRECISION -> precisionBase;
			case PRECISION_MAX -> precisionMax;
			case RANDOM_WEIGHT -> randomWeight;
			default -> throw new IllegalArgumentException("Unexpected value: " + field);
		};
	}
	
	public double power() { return getBasePower(); }
	public double speed() { return getBaseAttackSpeed(); }
	public double rangeEffective() { return rangeEffective.value; }
	public double rangeMax() { return rangeMax.value; }
	public double durability() { return getBaseDurability(); }
	public double precision() { return getBasePrecision(); }
	public double randomWeight() { return randomWeight.value; }
	public double power(float devProgress) { return getBasePower() + getDevPower(devProgress); }
	public double speed(float devProgress) { return getBaseAttackSpeed() + getDevAttackSpeed(devProgress); }
	public double durability(float devProgress) { return getBaseDurability() + getDevDurability(devProgress); }
	public double precision(float devProgress) { return getBasePrecision() + getDevPrecision(devProgress); }
	public double getBasePower() { return powerBase.value; }
	public double getBaseAttackSpeed() { return speedBase.value; }
	public double getBaseDurability() { return durabilityBase.value; }
	public double getBasePrecision() { return precisionBase.value; }
	public double getRandomWeight() { return randomWeight.value; }
	public double getDevPower(float devProgress) { return devProgress * (powerMax.value - powerBase.value); }
	public double getDevAttackSpeed(float devProgress) { return devProgress * (speedMax.value - speedBase.value); }
	public double getDevDurability(float devProgress) { return devProgress * (durabilityMax.value - durabilityBase.value); }
	public double getDevPrecision(float devProgress) { return devProgress * (precisionMax.value - precisionBase.value); }
	
	public static class Builder {
		private double powerBase;
		private double powerMax;
		private double speedBase;
		private double speedMax;
		private double rangeEffective;
		private double rangeMax;
		private double durabilityBase;
		private double durabilityMax;
		private double precisionBase;
		private double precisionMax;
		private double randomWeight = DEFAULT_RANDOM_WEIGHT;
		
		public Builder power(double power) {
			return power(power, power);
		}

		public Builder power(double powerBase, double powerMax) {
			this.powerBase = Math.max(powerBase, 0);
			this.powerMax = Math.max(this.powerBase, powerMax);
			return this;
		}
		
		public Builder speed(double speed) {
			return speed(speed, speed);
		}

		public Builder speed(double speedBase, double speedMax) {
			this.speedBase = Math.max(speedBase, 0);
			this.speedMax = Math.max(this.speedBase, speedMax);
			return this;
		}
		
		public Builder range(double rangeEffective, double rangeMax) {
			this.rangeEffective = Math.max(rangeEffective, 1.0D);
			this.rangeMax = Math.max(this.rangeEffective, rangeMax);
			return this;
		}
		
		public Builder durability(double durability) {
			return durability(durability, durability);
		}

		public Builder durability(double durabilityBase, double durabilityMax) {
			this.durabilityBase = Math.max(durabilityBase, 0);
			this.durabilityMax = Math.max(this.durabilityBase, durabilityMax);
			return this;
		}
		
		public Builder precision(double precision) {
			return precision(precision, precision);
		}

		public Builder precision(double precisionBase, double precisionMax) {
			this.precisionBase = Math.max(precisionBase, 0);
			this.precisionMax = Math.max(this.precisionBase, precisionMax);
			return this;
		}

		public Builder randomWeight(double randomWeight) {
			this.randomWeight = randomWeight;
			return this;
		}
		
		public Builder fieldValue(StatWithValue field, double value) {
			switch (field) {
				case POWER -> powerBase = Math.max(value, 0);
				case POWER_MAX -> powerMax = Math.max(powerBase, value);
				case SPEED -> speedBase = Math.max(value, 0);
				case SPEED_MAX -> speedMax = Math.max(speedBase, value);
				case RANGE_EFFECTIVE -> rangeEffective = value;
				case RANGE_MAX -> rangeMax = value;
				case DURABILITY -> durabilityBase = Math.max(value, 0);
				case DURABILITY_MAX -> durabilityMax = Math.max(durabilityBase, value);
				case PRECISION -> precisionBase = Math.max(value, 0);
				case PRECISION_MAX -> precisionMax = Math.max(precisionBase, value);
				case RANDOM_WEIGHT -> randomWeight = value;
				default -> throw new IllegalArgumentException("Unexpected value: " + field);
			};
			return this;
		}
		
		public StandStats build() {
			return new StandStats(powerBase, powerMax, speedBase, speedMax,
					rangeEffective, rangeMax, durabilityBase, durabilityMax,
					precisionBase, precisionMax, randomWeight);
		}
	}
	
	public enum StatWithValue {
		POWER("power"),
		POWER_MAX("powerMax"),
		SPEED("speed"),
		SPEED_MAX("speedMax"),
		RANGE_EFFECTIVE("rangeEffective"),
		RANGE_MAX("rangeMax"),
		DURABILITY("durability"),
		DURABILITY_MAX("durabilityMax"),
		PRECISION("precision"),
		PRECISION_MAX("precisionMax"),
		RANDOM_WEIGHT("randomWeight");
		
		private final String nameInJson;
		
		private StatWithValue(String nameInJson) {
			this.nameInJson = nameInJson;
		}
	}
	
	public enum CosmeticStat {
		POWER,
		SPEED,
		RANGE,
		DURABILITY,
		PRECISION,
		DEV_POTENTIAL
	}
	
	
	public StandStats.Builder defaultToBuilder() {
		return new StandStats.Builder()
				.power(powerBase.defaultValue, powerMax.defaultValue)
				.speed(speedBase.defaultValue, speedMax.defaultValue)
				.range(rangeEffective.defaultValue, rangeMax.defaultValue)
				.durability(durabilityBase.defaultValue, durabilityMax.defaultValue)
				.precision(precisionBase.defaultValue, precisionMax.defaultValue)
				.randomWeight(randomWeight.defaultValue);
	}
	
	public static StandStats fromJson(JsonObject json) {
		var builder = new StandStats.Builder();
		for (StatWithValue field : StatWithValue.values()) {
			Optional.ofNullable(json.getAsJsonPrimitive(field.nameInJson)).ifPresent(jsonValue -> {
				builder.fieldValue(field, jsonValue.getAsDouble());
			});
		}
		return builder.build();
	}
	
	@Override
	public JsonElement makeConfigTemplate() {
		JsonObject json = new JsonObject();
		for (StatWithValue field : StatWithValue.values()) {
			json.addProperty(field.nameInJson, getField(field).defaultValue);
		}
		return json;
	}
	
	@Override
	public void applyConfig(JsonElement json) {
		JsonObject config = json.getAsJsonObject();
		for (StatWithValue field : StatWithValue.values()) {
			Optional.ofNullable(config.getAsJsonPrimitive(field.nameInJson)).ifPresent(jsonValue -> {
				getField(field).value = jsonValue.getAsDouble();
			});
		}
		clampConfiguredMaxValues();
	}

	private void clampConfiguredMaxValues() {
		powerMax.value = Math.max(powerBase.value, powerMax.value);
		speedMax.value = Math.max(speedBase.value, speedMax.value);
		rangeEffective.value = Math.max(rangeEffective.value, 1.0D);
		rangeMax.value = Math.max(rangeEffective.value, rangeMax.value);
		durabilityMax.value = Math.max(durabilityBase.value, durabilityMax.value);
		precisionMax.value = Math.max(precisionBase.value, precisionMax.value);
	}
	
	@Override
	public void restoreDefaults() {
		for (StatWithValue field : StatWithValue.values()) {
			getField(field).reset();
		}
	}
	
	
	public static void updateStandStatAttributes(@Nonnull StandPower standPower, @Nonnull LivingEntity user) {
		if (user.level().isClientSide()) return;
		StandStats stats = standPower.getStandInstance().map(StandInstance::getStandType).map(StandType::getStandStats).orElse(null);
		float statsDevelopment = standPower.getStatsDevelopment();
		AttributeMap attributes = user.getAttributes();
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_STRENGTH, stats != null ? stats.power(statsDevelopment) : 0);
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_SPEED, stats != null ? stats.speed(statsDevelopment) : 0);
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_EFFECTIVE_RANGE, stats != null ? stats.rangeEffective() : 0);
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_MAX_RANGE, stats != null ? stats.rangeMax() : 0);
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_DURABILITY, stats != null ? stats.durability(statsDevelopment) : 0);
		AttributeUtil.setBaseValue(attributes, ModEntityAttributes.STAND_PRECISION, stats != null ? stats.precision(statsDevelopment) : 0);
	}
	
}
