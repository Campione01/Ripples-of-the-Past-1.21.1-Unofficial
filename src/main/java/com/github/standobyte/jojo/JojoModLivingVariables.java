package com.github.standobyte.jojo;

import javax.annotation.Nullable;

import com.github.standobyte.jojo.entityattachment.ComponentUtil;
import com.github.standobyte.jojo.entityattachment.SynchronizablePlayerData;
import com.github.standobyte.jojo.entityattachment.TickingEntityData;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.init.ModDataAttachmentTypes;
import com.github.standobyte.jojo.network.s2c.TrDyingBodyTimerPacket;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

public class JojoModLivingVariables implements INBTSerializable<CompoundTag>, TickingEntityData, SynchronizablePlayerData {
	protected final LivingEntity entity;
	
	private static final AttributeModifier DYING_BODY_ATTACK_DAMAGE = new AttributeModifier(
			JojoMod.resLoc("dying_body_attack_damage"), -0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier DYING_BODY_ATTACK_SPEED = new AttributeModifier(
			JojoMod.resLoc("dying_body_attack_speed"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier DYING_BODY_MOVEMENT_SPEED = new AttributeModifier(
			JojoMod.resLoc("dying_body_movement_speed"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier DYING_BODY_SWIMMING_SPEED = new AttributeModifier(
			JojoMod.resLoc("dying_body_swimming_speed"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	
	public boolean isDyingBody = false;
	private int dyingBodyTimer = -1;
	private int dyingBodyDuration = 1;
	public Vec3 bleedingParticlesPos = null;
	
	public boolean foundAnArrow;
	public int findMoreArrowsTimer = -1;
	public int knivesThrewTicks;
	
	public JojoModLivingVariables(LivingEntity entity) {
		this.entity = entity;
		addTicking(entity);
		addSynchronization(entity);
	}
	
	public void tick() {
		bleedingParticlesPos = null;
		if (findMoreArrowsTimer >= 0) findMoreArrowsTimer--;
		if (knivesThrewTicks > 0) {
			knivesThrewTicks--;
		}
		tickDyingBody();
	}

	public boolean isDyingBody() {
		return dyingBodyTimer >= 0;
	}

	private void tickDyingBody() {
		if (isDyingBody()) {
			if (!entity.level().isClientSide()) {
				if (entity instanceof Player player) {
					player.getFoodData().setFoodLevel(17);
				}
				entity.setAirSupply(entity.getMaxAirSupply());
			}
			if (dyingBodyTimer > 0) {
				if (--dyingBodyTimer == 0 && entity.tickCount % 200 == 0) {
					entity.setHealth(entity.getHealth() - entity.getMaxHealth() / 30.0F);
				}
				updateDyingBodyFlag();
				updateDyingBodyDebuffs();
			}
		}
	}

	public void setDyingBodyTimer(int timer) {
		setDyingBodyTimer(timer, timer);
	}

	public void setDyingBodyTimer(int timer, int fullDuration) {
		this.dyingBodyTimer = timer;
		this.dyingBodyDuration = Math.max(fullDuration, 1);
		if (!entity.level().isClientSide()) {
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TrDyingBodyTimerPacket(entity.getId(), dyingBodyTimer, dyingBodyDuration));
		}
		updateDyingBodyFlag();
		updateDyingBodyDebuffs();
	}

	public float getDyingBodyProgress() {
		if (isDyingBody()) {
			return 1.0F - (float) dyingBodyTimer / dyingBodyDuration;
		}
		return 0.0F;
	}

	public int getDyingBodyTicksLeft() {
		return dyingBodyTimer;
	}

	private void updateDyingBodyFlag() {
		isDyingBody = isDyingBody();
	}

	private void updateDyingBodyDebuffs() {
		float progress = getDyingBodyProgress();
		float debuffLevel = progress > 0.8F ? 1.0F - 5.0F * (1.0F - progress) : 0.0F;
		updateAttributeModifier(entity, Attributes.ATTACK_DAMAGE, DYING_BODY_ATTACK_DAMAGE, debuffLevel);
		updateAttributeModifier(entity, Attributes.ATTACK_SPEED, DYING_BODY_ATTACK_SPEED, debuffLevel);
		updateAttributeModifier(entity, Attributes.MOVEMENT_SPEED, DYING_BODY_MOVEMENT_SPEED, debuffLevel);
		updateAttributeModifier(entity, NeoForgeMod.SWIM_SPEED, DYING_BODY_SWIMMING_SPEED, debuffLevel);
	}

	private static void updateAttributeModifier(LivingEntity entity, Holder<Attribute> attribute, AttributeModifier modifier, float multiplier) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(modifier.id());
		if (multiplier != 0.0F) {
			instance.addTransientModifier(new AttributeModifier(
					modifier.id(), modifier.amount() * multiplier, modifier.operation()));
		}
	}

	@Override
	public void syncToTracking(ServerPlayer trackingPlayer) {
		if (dyingBodyTimer >= 0) {
			PacketDistributor.sendToPlayer(trackingPlayer, new TrDyingBodyTimerPacket(entity.getId(), dyingBodyTimer, dyingBodyDuration));
		}
	}

	@Override
	public void syncToPlayer(ServerPlayer entityAsPlayer) {
		if (dyingBodyTimer >= 0) {
			PacketDistributor.sendToPlayer(entityAsPlayer, new TrDyingBodyTimerPacket(entity.getId(), dyingBodyTimer, dyingBodyDuration));
		}
	}

	@Override
	public void onPlayerClone(Player newPlayer, boolean wasDeath) {
		JojoModLivingVariables newData = get(newPlayer);
		newData.foundAnArrow = this.foundAnArrow;
		if (!wasDeath) {
			newData.dyingBodyTimer = this.dyingBodyTimer;
			newData.dyingBodyDuration = this.dyingBodyDuration;
			newData.updateDyingBodyFlag();
			newData.updateDyingBodyDebuffs();
		}
	}

	@Override
	public CompoundTag serializeNBT(Provider provider) {
		CompoundTag nbt = new CompoundTag();
		nbt.putBoolean("DyingBody", isDyingBody());
		nbt.putInt("DeadBody", dyingBodyTimer);
		nbt.putInt("DeadBodyDuration", dyingBodyDuration);
		nbt.putBoolean("FoundArrow", foundAnArrow);
		nbt.putInt("FindMoreArrowsTimer", findMoreArrowsTimer);
		return nbt;
	}

	@Override
	public void deserializeNBT(Provider provider, CompoundTag nbt) {
		dyingBodyTimer = nbt.contains("DeadBody") ? nbt.getInt("DeadBody") : nbt.getBoolean("DyingBody") ? 0 : -1;
		dyingBodyDuration = Math.max(nbt.getInt("DeadBodyDuration"), 1);
		updateDyingBodyFlag();
		foundAnArrow = nbt.getBoolean("FoundArrow");
		findMoreArrowsTimer = nbt.getInt("FindMoreArrowsTimer");
	}
	
	
	public static JojoModLivingVariables get(LivingEntity entity) {
		return entity.getData(ModDataAttachmentTypes.LIVING_VARS);
	}
	
	@Nullable
	public static JojoModLivingVariables getIfPresent(LivingEntity entity) {
		return ComponentUtil.getExistingDataOrNull(entity, ModDataAttachmentTypes.LIVING_VARS);
	}
	
}
