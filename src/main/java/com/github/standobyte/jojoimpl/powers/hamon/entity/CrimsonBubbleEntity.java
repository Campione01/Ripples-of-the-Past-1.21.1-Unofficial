package com.github.standobyte.jojoimpl.powers.hamon.entity;

import java.util.List;
import java.util.Optional;

import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.init.ModSoundEvents;
import com.github.standobyte.jojo.init.power.ModPlayerPowers;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.util.functions.JojoModUtil;
import com.github.standobyte.jojoimpl.powers.hamon.HamonData;
import com.github.standobyte.jojoimpl.powers.hamon.HamonUtil;
import com.github.standobyte.jojoimpl.powers.hamon.ModHamonSkills;
import com.github.standobyte.jojoimpl.powers.hamon.abilities.HamonAbilityHelpers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CrimsonBubbleEntity extends Entity {
	private int hamonStrengthPoints;
	private int hamonControlPoints;
	private Vec3 initialPoint;

	public CrimsonBubbleEntity(Level level) {
		this(ModEntityTypes.CRIMSON_BUBBLE.get(), level);
		setDeltaMovement(new Vec3(
				random.nextDouble() - 0.5D,
				(random.nextDouble() - 0.5D) * 0.5D,
				random.nextDouble() - 0.5D).normalize().scale(0.002D));
	}

	public CrimsonBubbleEntity(EntityType<? extends CrimsonBubbleEntity> type, Level level) {
		super(type, level);
	}

	public void setHamonPoints(int hamonStrengthPoints, int hamonControlPoints) {
		this.hamonStrengthPoints = hamonStrengthPoints;
		this.hamonControlPoints = hamonControlPoints;
	}

	public void putItem(ItemEntity item) {
		item.setPickUpDelay(2);
		item.startRiding(this, true);
	}

	@Override
	public Vec3 getPassengerRidingPosition(Entity passenger) {
		return position();
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			if (hamonControlPoints == 0 && hamonStrengthPoints == 0) {
				discard();
				return;
			}
			for (Entity entity : getPassengers()) {
				if (entity instanceof ItemEntity item) {
					item.setPickUpDelay(2);
				}
			}

			List<LivingEntity> entities = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(),
					entity -> entity.isAlive() && !isCreativeOrSpectator(entity));
			for (LivingEntity entity : entities) {
				collideWithEntity(entity);
			}

			if (hamonStrengthPoints > 0 && random.nextFloat() < 0.2F) {
				hamonStrengthPoints--;
			}
			if (hamonControlPoints > 0 && random.nextFloat() < 0.2F) {
				hamonControlPoints--;
			}

			if (initialPoint != null) {
				if (tickCount % 100 == 0 || position().distanceToSqr(initialPoint) > 2.25D) {
					Vec3 vecToInitialPos = initialPoint.subtract(position());
					setDeltaMovement(vecToInitialPos.add(
							random.nextDouble() - 0.5D,
							(random.nextDouble() - 0.5D) * 0.5D,
							random.nextDouble() - 0.5D).normalize().scale(0.002D));
				}
			}
			else {
				initialPoint = position();
			}
		}
		move(MoverType.SELF, getDeltaMovement());
	}

	private static boolean isCreativeOrSpectator(LivingEntity entity) {
		return entity instanceof Player player && (player.isCreative() || player.isSpectator());
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	private void collideWithEntity(LivingEntity entity) {
		Optional<HamonData> hamonOptional = PlayerPower.getPowerData(entity, ModPlayerPowers.HAMON);
		if (hamonOptional.isPresent()) {
			HamonData hamon = hamonOptional.get();
			hamon.setHamonStatPoints(HamonData.HamonStat.STRENGTH,
					hamon.getHamonStrengthPoints() + (int) (hamonStrengthPoints * 0.8F), true, false);
			hamon.setHamonStatPoints(HamonData.HamonStat.CONTROL,
					hamon.getHamonControlPoints() + (int) (hamonControlPoints * 0.8F), true, false);
			hamon.syncOnUpdate(entity);
			HamonUtil.emitHamonSparkParticles(level(), entity instanceof Player player ? player : null, entity.position(), 2.0F);
			if (hamon.characterIs(ModHamonSkills.CHARACTER_JOSEPH.get())) {
				JojoModUtil.sayVoiceLine(entity, ModSoundEvents.JOSEPH_CRIMSON_BUBBLE_REACTION);
			}
			if (entity instanceof ServerPlayer player) {
				ModCriteriaTriggers.triggerLastHamon(player, this);
			}
			hamonStrengthPoints = 0;
			hamonControlPoints = 0;
		}
		else {
			hurtWithStoredHamon(entity);
			hamonStrengthPoints = Math.max(hamonStrengthPoints - 1, 0);
			hamonControlPoints = Math.max(hamonControlPoints - 1, 0);
		}
	}

	@Override
	public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (isInvulnerableTo(source)) {
			return false;
		}
		if (source.isCreativePlayer()) {
			hamonControlPoints = 0;
			hamonStrengthPoints = 0;
		}
		else {
			if (source.getDirectEntity() instanceof LivingEntity living) {
				hurtWithStoredHamon(living);
			}
			hamonStrengthPoints = Math.max(hamonStrengthPoints - (int) amount, 0);
			hamonControlPoints = Math.max(hamonControlPoints - (int) amount, 0);
		}
		return true;
	}

	private boolean hurtWithStoredHamon(LivingEntity target) {
		return HamonAbilityHelpers.hamonHurt(target, getDamage(), this, this);
	}

	private float getDamage() {
		return (float) (hamonStrengthPoints + hamonControlPoints) * 20F / (float) HamonData.MAX_HAMON_POINTS;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {}

	@Override
	protected void readAdditionalSaveData(CompoundTag compound) {
		hamonStrengthPoints = compound.getInt("StrengthPoints");
		hamonControlPoints = compound.getInt("ControlPoints");
		if (compound.get("InitialPoint") instanceof ListTag list && list.getElementType() == Tag.TAG_DOUBLE && list.size() >= 3) {
			initialPoint = new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag compound) {
		compound.putInt("StrengthPoints", hamonStrengthPoints);
		compound.putInt("ControlPoints", hamonControlPoints);
		if (initialPoint != null) {
			compound.put("InitialPoint", newDoubleList(initialPoint.x, initialPoint.y, initialPoint.z));
		}
	}
}
