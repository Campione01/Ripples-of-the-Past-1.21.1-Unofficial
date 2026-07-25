package com.github.standobyte.jojo.init;

import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

import com.github.standobyte.jojo.JojoModLivingVariables;
import com.github.standobyte.jojo.adventure.npc.PowerUserMobEntity;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.entityattachment.DataEventListeners;
import com.github.standobyte.jojo.entityattachment.PlayerVoiceLineData;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffect;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectsClass;
import com.github.standobyte.jojo.entityattachment.custom_effect.EntityCustomEffectsMap;
import com.github.standobyte.jojo.mechanics.KnockbackCollisionImpact;
import com.github.standobyte.jojo.mechanics.clothes.EntityClothesInventory;
import com.github.standobyte.jojo.mechanics.coffin.PlayerCoffinSleepData;
import com.github.standobyte.jojo.config.client.PlayerClientBroadcastedSettings;
import com.github.standobyte.jojo.powersystem.PowerClass;
import com.github.standobyte.jojo.powersystem.entityaction.EntityActionInputState;
import com.github.standobyte.jojo.powersystem.entityaction.LivingComponentAction;
import com.github.standobyte.jojo.powersystem.playerpower.PlayerPower;
import com.github.standobyte.jojo.powersystem.standpower.StandPower;
import com.github.standobyte.jojo.powersystem.standpower.effect.StandEffectsTarget;
import com.github.standobyte.jojo.subsystems.ServerBlockDestroyTracker;
import com.github.standobyte.jojo.subsystems.entity_externalcontainer.PlayerExternalContainers;
import com.github.standobyte.jojo.subsystems.entity_grab.LivingComponentGrab;
import com.github.standobyte.jojo.subsystems.movement_input_sync.PlayerMovementInputData;
import com.github.standobyte.jojo.subsystems.timestop.TimeStopState;
import com.github.standobyte.jojo.subsystems.entity_possessionv2.LivingComponentPossession;
import com.github.standobyte.jojo.subsystems.entity_puppetcontrol.EntityComponentController;
import com.github.standobyte.jojoimpl.powers.vampirism.VampirismState;
import com.github.standobyte.jojoimpl.powers.hamon.ChargedHamonEggTracker;
import com.github.standobyte.jojoimpl.powers.hamon.EntityHamonChargeState;
import com.github.standobyte.jojoimpl.powers.hamon.HamonHypnosisState;
import com.github.standobyte.jojoimpl.powers.hamon.ProjectileHamonChargeState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEProductEffectsState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GoldExperienceLifeformState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GEStuckObjectsState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GELifeshotState;
import com.github.standobyte.jojoimpl.stands.goldexperience.GETreeLeavesDecayState;
import com.github.standobyte.jojoimpl.stands.crazydiamond.brokenblocks.BrokenBlocksChunkData;
import com.github.standobyte.jojoimpl.stands.silverchariot.SilverChariotState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModDataAttachmentTypes {
	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JojoMod.MOD_ID);
	
	
	// Entity

	@ApiStatus.Internal
	public static final Supplier<AttachmentType<DataEventListeners>> DATA_EVENT_HELPER = ATTACHMENT_TYPES.register("event_listener", 
			() -> AttachmentType.builder(DataEventListeners::new).build());
	
	public static final Supplier<AttachmentType<JojoModLivingVariables>> LIVING_VARS = ATTACHMENT_TYPES.register("living_vars", 
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new JojoModLivingVariables(entity) : null).build());
	 
	public static final Supplier<AttachmentType<StandPower>> STAND_POWER = ATTACHMENT_TYPES.register("stand_power", 
			() -> AttachmentType.serializable(entity -> PowerClass._tryAttach(entity, StandPower::new)).build());

	public static final Supplier<AttachmentType<PlayerPower>> PLAYER_POWER = ATTACHMENT_TYPES.register("player_power", 
			() -> AttachmentType.serializable(entity -> PowerClass._tryAttach(entity, PlayerPower::new)).build());
	
	public static final Supplier<AttachmentType<LivingComponentAction>> LIVING_ACTION = ATTACHMENT_TYPES.register("living_action", 
			() -> AttachmentType.serializable(LivingComponentAction::create).build());

	public static final Supplier<AttachmentType<LivingComponentGrab>> LIVING_GRAB = ATTACHMENT_TYPES.register("living_grab",
			() -> AttachmentType.builder(obj -> obj instanceof LivingEntity entity ? new LivingComponentGrab(entity) : null).build());

	public static final Supplier<AttachmentType<PlayerMovementInputData>> SYNCHED_MOVEMENT_INPUT = ATTACHMENT_TYPES.register("synched_movement_input",
			() -> AttachmentType.builder(entity -> entity instanceof Player || entity instanceof PowerUserMobEntity ? new PlayerMovementInputData() : null).build());

	@ApiStatus.Internal
	public static final Supplier<AttachmentType<EntityActionInputState>> ENTITY_ABILITY_INPUT = ATTACHMENT_TYPES.register("player_ability_input", 
			() -> AttachmentType.builder(obj -> obj instanceof LivingEntity entity ? new EntityActionInputState(entity) : null).build());
	
	public static final Supplier<AttachmentType<EntityClothesInventory>> HUMANOID_CLOTHES = ATTACHMENT_TYPES.register("humanoid_clothes", 
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new EntityClothesInventory(entity) : null).build());
	
	
	public static final Supplier<AttachmentType<KnockbackCollisionImpact>> KB_IMPACT = ATTACHMENT_TYPES.register("kb_impact", 
			() -> AttachmentType.builder(obj -> obj instanceof Entity entity ? new KnockbackCollisionImpact(entity) : null).build());

	public static final Supplier<AttachmentType<EntityComponentController>> CONTROLLER = ATTACHMENT_TYPES.register("controller_player", 
			() -> AttachmentType.builder(obj -> obj instanceof Entity entity ? new EntityComponentController(entity) : null).build());

	public static final Supplier<AttachmentType<LivingComponentPossession>> ENTITY_POSSESSION = ATTACHMENT_TYPES.register("possession", 
			() -> AttachmentType.builder(obj -> obj instanceof Entity entity ? new LivingComponentPossession(entity) : null).build());
	
	public static final Supplier<AttachmentType<EntityCustomEffectsMap<EntityCustomEffect>>> ENTITY_CUSTOM_EFFECTS = ATTACHMENT_TYPES.register("entity_custom_effects", 
			() -> AttachmentType.serializable(obj -> obj instanceof Entity entity ? new EntityCustomEffectsMap<>(EntityCustomEffectsClass.OTHER, entity) : null).build());
	
	
	public static final Supplier<AttachmentType<StandEffectsTarget>> STAND_EFFECTS_TARGET = ATTACHMENT_TYPES.register("stand_effects_target",
			() -> AttachmentType.builder(obj -> obj instanceof LivingEntity entity ? new StandEffectsTarget(entity) : null).build());

	public static final Supplier<AttachmentType<SilverChariotState>> SILVER_CHARIOT_STATE = ATTACHMENT_TYPES.register("silver_chariot_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity ? new SilverChariotState() : null).build());

	public static final Supplier<AttachmentType<VampirismState>> VAMPIRISM_STATE = ATTACHMENT_TYPES.register("vampirism_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity ? new VampirismState() : null).build());

	public static final Supplier<AttachmentType<EntityHamonChargeState>> HAMON_CHARGE = ATTACHMENT_TYPES.register("hamon_charge",
			() -> AttachmentType.serializable(obj -> obj instanceof Entity entity ? new EntityHamonChargeState(entity) : null).build());

	public static final Supplier<AttachmentType<ProjectileHamonChargeState>> PROJECTILE_HAMON_CHARGE = ATTACHMENT_TYPES.register("projectile_hamon_charge",
			() -> AttachmentType.serializable(obj -> obj instanceof Projectile projectile ? new ProjectileHamonChargeState(projectile) : null).build());

	public static final Supplier<AttachmentType<HamonHypnosisState>> HAMON_HYPNOSIS_STATE = ATTACHMENT_TYPES.register("hamon_hypnosis_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new HamonHypnosisState(entity) : null).build());

	public static final Supplier<AttachmentType<GELifeshotState>> GE_LIFESHOT_STATE = ATTACHMENT_TYPES.register("ge_lifeshot_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new GELifeshotState(entity) : null).build());

	public static final Supplier<AttachmentType<GEStuckObjectsState>> GE_STUCK_OBJECTS_STATE = ATTACHMENT_TYPES.register("ge_stuck_objects_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new GEStuckObjectsState(entity) : null).build());

	public static final Supplier<AttachmentType<GoldExperienceLifeformState>> GE_LIFEFORM_STATE = ATTACHMENT_TYPES.register("ge_lifeform_state",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new GoldExperienceLifeformState(entity) : null).build());

	public static final Supplier<AttachmentType<GEProductEffectsState>> GE_PRODUCT_EFFECTS = ATTACHMENT_TYPES.register("ge_product_effects",
			() -> AttachmentType.serializable(obj -> obj instanceof LivingEntity entity ? new GEProductEffectsState(entity) : null).build());

	public static final Supplier<AttachmentType<PlayerExternalContainers>> EXTERNAL_CONTAINERS = ATTACHMENT_TYPES.register("external_containers",
			() -> AttachmentType.builder(entity -> entity instanceof Player player ? new PlayerExternalContainers(player) : null).build());

	public static final Supplier<AttachmentType<PlayerClientBroadcastedSettings>> PLAYER_BROADCASTED_SETTINGS = ATTACHMENT_TYPES.register("player_broadcasted_settings",
			() -> AttachmentType.serializable(entity -> entity instanceof Player player ? new PlayerClientBroadcastedSettings(player) : null).build());

	public static final Supplier<AttachmentType<PlayerCoffinSleepData>> PLAYER_COFFIN_SLEEP = ATTACHMENT_TYPES.register("player_coffin_sleep",
			() -> AttachmentType.serializable(entity -> entity instanceof Player player ? new PlayerCoffinSleepData(player) : null).build());

	public static final Supplier<AttachmentType<PlayerVoiceLineData>> PLAYER_VOICE_LINES = ATTACHMENT_TYPES.register("player_voice_lines",
			() -> AttachmentType.builder(entity -> entity instanceof Player player ? new PlayerVoiceLineData(player) : null).build());


	// Level
	
	public static final Supplier<AttachmentType<ServerBlockDestroyTracker>> BLOCK_DESTROY = ATTACHMENT_TYPES.register("block_destroy",
			() -> AttachmentType.builder(obj -> obj instanceof ServerLevel level ? new ServerBlockDestroyTracker(level) : null).build());

	public static final Supplier<AttachmentType<GETreeLeavesDecayState>> GE_TREE_DECAY = ATTACHMENT_TYPES.register("ge_tree_decay",
			() -> AttachmentType.builder(obj -> obj instanceof ServerLevel level ? new GETreeLeavesDecayState(level) : null).build());

	public static final Supplier<AttachmentType<ChargedHamonEggTracker>> CHARGED_HAMON_EGGS = ATTACHMENT_TYPES.register("charged_hamon_eggs",
			() -> AttachmentType.builder(obj -> obj instanceof ServerLevel ? new ChargedHamonEggTracker() : null).build());
	
	
	// Chunk
	
	public static final Supplier<AttachmentType<TimeStopState>> TIME_STOP = ATTACHMENT_TYPES.register("time_stop",
			() -> AttachmentType.builder(obj -> obj instanceof ServerLevel level ? new TimeStopState(level) : null).build());

	public static final Supplier<AttachmentType<BrokenBlocksChunkData>> BROKEN_BLOCKS = ATTACHMENT_TYPES.register("broken_blocks",
			() -> AttachmentType.serializable(obj -> obj instanceof LevelChunk chunk ? new BrokenBlocksChunkData(chunk) : null).build());
	
}
