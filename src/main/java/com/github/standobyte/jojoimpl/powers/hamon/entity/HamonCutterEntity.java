package com.github.standobyte.jojoimpl.powers.hamon.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.customobjects.entity_projectile.ModdedProjectileEntity;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonPowerType;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class HamonCutterEntity extends ModdedProjectileEntity {
	private static final Vec3 MOUTH_POS_OFFSET = new Vec3(0.0D, -0.1D, 0.0D);
	private final List<MobEffectInstance> potionEffects = new ArrayList<>();
	private int color = -1;
	private float hamonStatPoints;

	public HamonCutterEntity(LivingEntity shooter, Level level, ItemStack sourceItem) {
		super(ModEntityTypes.HAMON_CUTTER.get(), shooter, level);
		PotionContents contents = sourceItem.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			color = contents.getColor();
			for (MobEffectInstance effect : contents.getAllEffects()) {
				potionEffects.add(new MobEffectInstance(effect));
			}
		}
	}

	public HamonCutterEntity(EntityType<? extends HamonCutterEntity> type, Level level) {
		super(type, level);
	}

	public HamonCutterEntity setColor(int color) {
		this.color = color;
		return this;
	}

	public void setHamonStatPoints(float points) {
		this.hamonStatPoints = points;
	}

	public int getColor() {
		return color;
	}

	@Override
	protected Vec3 getOwnerRelativeOffset() {
		return MOUTH_POS_OFFSET;
	}

	@Override
	protected boolean hurtTarget(Entity target, @Nullable LivingEntity owner) {
		if (target instanceof LivingEntity living && owner != null) {
			HamonAbilityHelpers.hamonHurt(living, owner, getBaseDamage());
			return true;
		}
		return false;
	}

	@Override
	protected void afterEntityHit(EntityHitResult entityRayTraceResult, boolean entityHurt) {
		if (entityHurt && entityRayTraceResult.getEntity() instanceof LivingEntity target) {
			if (target.isAffectedByPotions()) {
				for (MobEffectInstance effectInstance : potionEffects) {
					MobEffect effect = effectInstance.getEffect().value();
					if (effect.isInstantenous()) {
						effect.applyInstantenousEffect(this, getOwner(), target, effectInstance.getAmplifier(), 3.0D / 16.0D);
					}
					else {
						target.addEffect(new MobEffectInstance(effectInstance.getEffect(),
								Mth.floor(effectInstance.getDuration()),
								effectInstance.getAmplifier(),
								effectInstance.isAmbient(),
								effectInstance.isVisible()));
					}
				}
			}
			addStrengthPoints();
		}
	}

	private void addStrengthPoints() {
		LivingEntity owner = getOwner();
		if (owner != null && hamonStatPoints > 0.0F) {
			PlayerPower.getPowerData(owner, HamonPowerType.HAMON).ifPresent(hamon -> {
				hamon.hamonPointsFromAction(HamonData.HamonStat.STRENGTH, hamonStatPoints);
				hamon.syncOnUpdate(owner);
			});
		}
	}

	@Override
	public int ticksLifespan() {
		return 100;
	}

	@Override
	protected float getBaseDamage() {
		return 2.0F;
	}

	@Override
	protected float getMaxHardnessBreakable() {
		return 0.0F;
	}

	@Override
	public boolean standDamage() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("Color", color);
		nbt.putFloat("HamonStatPoints", hamonStatPoints);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		color = nbt.getInt("Color");
		hamonStatPoints = nbt.getFloat("HamonStatPoints");
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		super.writeSpawnData(buffer);
		buffer.writeInt(color);
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
		super.readSpawnData(additionalData);
		color = additionalData.readInt();
	}
}
