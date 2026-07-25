package com.github.standobyte.jojo.powersystem.standpower.entity;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.github.standobyte.jojo.client.ClientProxy;
import com.github.standobyte.jojo.init.ModCriteriaTriggers;
import com.github.standobyte.jojo.init.ModEntityTypes;
import com.github.standobyte.jojo.network.s2c.TrSetStandEntityPacket;
import com.github.standobyte.jojo.powersystem.MovesetBuilder;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.StandStats;
import com.github.standobyte.jojo.powersystem.standpower.datapack.StandTypeClass;
import com.github.standobyte.jojo.powersystem.standpower.type.StandType;
import com.github.standobyte.jojo.util.objects_java.DefaultedValue;
import com.github.standobyte.jojoimpl.stands._entitybase.StandEntityManualControlToggle;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

// TODO stand hitbox size parameter (+the size to stretch the model to)
public class EntityStandType extends StandType {
	static {
		StandTypeClass.registerStandClass(EntityStandType.class, "entity", EntityStandType::new);
	}
	
	protected DefaultedValue<EntityType<? extends StandEntity>> entityType;
	public EntityDimensions standDimensions = ModEntityTypes.HUMANOID_STAND.get().getDimensions();
	protected boolean manualControlEnabled = true;
	protected boolean standLeapEnabled = true;
	
	public EntityStandType(StandStats stats, MovesetBuilder moveset, 
			ResourceLocation id) {
		this(stats, moveset, ModEntityTypes.HUMANOID_STAND.get(), id);
	}
	
	public EntityStandType(StandStats stats, MovesetBuilder moveset, 
			EntityType<? extends StandEntity> standEntityType, 
			ResourceLocation id) {
		super(stats, moveset, id);
		Objects.requireNonNull(standEntityType);
		this.entityType = new DefaultedValue<>(standEntityType);
	}

	public <T extends EntityStandType> T standDimensions(float width, float height) {
		return init(stand -> stand.standDimensions = EntityDimensions.scalable(width, height));
	}
	
	@Override
	public JsonObject makeConfigTemplate() {
		JsonObject json = super.makeConfigTemplate();
		json.addProperty("entityType", EntityType.getKey(this.entityType.defaultValue).toString());
		json.addProperty("manualControlEnabled", this.manualControlEnabled);
		json.addProperty("standLeapEnabled", this.standLeapEnabled);
		return json;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void applyConfig(JsonElement json) {
		super.applyConfig(json);
		JsonObject config = json.getAsJsonObject();
		Optional.ofNullable(config.get("entityType"))
			.map(JsonElement::getAsString)
			.flatMap(id -> EntityType.byString(id))
			.ifPresent(entityType -> {
				this.entityType.value = (EntityType<? extends StandEntity>) entityType;
			});
		Optional.ofNullable(config.get("manualControlEnabled"))
				.map(JsonElement::getAsBoolean)
				.ifPresent(value -> this.manualControlEnabled = value);
		Optional.ofNullable(config.get("standLeapEnabled"))
				.map(JsonElement::getAsBoolean)
				.ifPresent(value -> this.standLeapEnabled = value);
	}

	@Override
	public void restoreDefaults() {
		super.restoreDefaults();
		entityType.reset();
		manualControlEnabled = true;
		standLeapEnabled = true;
	}
	
	@Override
	public void onUserSummonCommand(LivingEntity user, StandPower standPower) {
		if (!standPower.isSummoned()) {
			if (onTrySummon(user, standPower)) {
				summon(user, standPower);
			}
			return;
		}
		
		StandEntity standEntity = standPower.getSummonedStandEntity();
		if (standEntity != null && standEntity.isArmsOnlyMode()) {
			standEntity.fullSummonFromArms();
			triggerFullSummonAdvancement(user, standEntity);
		}
		else {
			unsummon(user, standPower);
		}
	}

	@Override
	public boolean summon(LivingEntity user, StandPower standPower) {
		return summon(user, standPower, entity -> {}, true);
	}

	public boolean summon(LivingEntity user, StandPower standPower, Consumer<StandEntity> beforeTheSummon, boolean addToWorld) {
		if (!standPower.canUsePower()) {
			return false;
		}
//		if (!withoutNameVoiceLine && !user.isShiftKeyDown()) {
//			SoundEvent shout = summonShoutSupplier.get();
//			if (shout != null) {
//				JojoModUtil.sayVoiceLine(user, shout);
//			}
//		}
//		triggerAdvancement(standPower, standPower.getStandManifestation());
		
		Level level = user.level();
		if (!level.isClientSide()) {
			StandEntity standEntity = entityType.value.create(level/*, EntitySpawnReason.NATURAL*/)
					.withStandType(this);
			standEntity.refreshDimensions();
			standEntity.copyPosition(user);
			standEntity.copyStandUserRotation(user);
			standEntity.setCustomName(standPower.getName());
			standPower.setSummonedStand(standEntity);
			beforeTheSummon.accept(standEntity);
			
			if (addToWorld) {
				playSummonShout(user);
				finalizeStandSummonFromAction(user, standPower, standEntity, true);
			}
			
//			standEntity.onStandSummonServerSide();
		}
		return true;
	}
	
	public void finalizeStandSummonFromAction(LivingEntity user, StandPower standPower, StandEntity standEntity, boolean addToWorld) {
		Level level = user.level();
		if (!level.isClientSide() && !standEntity.isAddedToLevel()) {
			if (addToWorld) {
				level.addFreshEntity(standEntity);
				standEntity.playStandSummonSound();
				PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrSetStandEntityPacket(user.getId(), standEntity.getId()));
				triggerFullSummonAdvancement(user, standEntity);
			}
			else {
				forceUnsummon(user, standPower);
			}
		}
	}
	
	public void triggerFullSummonAdvancement(LivingEntity user, StandEntity standEntity) {
		if (user instanceof ServerPlayer player && standEntity != null && !standEntity.isArmsOnlyMode()) {
			ModCriteriaTriggers.triggerSummonStand(player);
		}
	}

	@Override
	public void unsummon(LivingEntity user, StandPower standPower) {
		if (!user.level().isClientSide()) {
			StandEntity standEntity = ((StandEntity) standPower.getSummonedStand());
			if (standEntity != null) {
				standEntity.onUnsummonUserInput();
			}
		}
	}

	@Override
	public void forceUnsummon(LivingEntity user, StandPower standPower) {
		if (!user.level().isClientSide()) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null) {
				standPower.setSummonedStand(null);
				standEntity.remove(Entity.RemovalReason.DISCARDED);
			}
			PacketDistributor.sendToPlayersTrackingEntityAndSelf(user, new TrSetStandEntityPacket(user.getId(), 0));
		}
		else if (user.is(ClientProxy.getClientPlayer())) {
			StandEntity standEntity = standPower.getSummonedStandEntity();
			if (standEntity != null && standEntity.isManuallyControlled()) {
				StandEntityManualControlToggle.off(user.level(), standEntity, false);
			}
		}
	}

	public boolean canBeManuallyControlled() {
		return manualControlEnabled;
	}

	public boolean canLeap() {
		return standLeapEnabled;
	}
	
	public EntityType<?> getEntityType() {
		return entityType.value;
	}
	
}
