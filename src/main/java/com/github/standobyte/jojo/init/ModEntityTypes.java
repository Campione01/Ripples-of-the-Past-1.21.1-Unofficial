package com.github.standobyte.jojo.init;

import com.github.standobyte.jojo.adventure.npc.PowerUserMobEntity;
import com.github.standobyte.jojo.core.JojoMod;
import com.github.standobyte.jojo.customobjects.AfterimageEntity;
import com.github.standobyte.jojo.customobjects.ObjectEntity;
import com.github.standobyte.jojo.customobjects.RoadRollerEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.BladeHatEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.BlockShardEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.ClackersEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.KnifeEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.LightBeamEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.MolotovEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.SpaceRipperStingyEyesEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.TommyGunBulletEntity;
import com.github.standobyte.jojo.customobjects.entity_projectile.ThrownNuggetBearingEntity;
import com.github.standobyte.jojo.mechanics.clothes.mannequin.MannequinEntity;
import com.github.standobyte.jojo.mechanics.standarrow.StandArrowEntity;
import com.github.standobyte.jojo.mrpresident.CocoJumboTurtleEntity;
import com.github.standobyte.jojo.powersystem.standpower.entity.StandEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.AngeloRockEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBlockBulletEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDBloodCutterEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDEyeOfEnderInsideEntity;
import com.github.standobyte.jojoimpl.stands.crazydiamond.CrazyDFireworkInsideEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HGBarrierEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HGEmeraldEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HGGrappleEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HGStringEntity;
import com.github.standobyte.jojoimpl.stands.hierophant.HierophantGreenEntity;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRCrossfireHurricaneEntity;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRDetectorEntity;
import com.github.standobyte.jojoimpl.npc.rps.RockPaperScissorsKidEntity;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRRedBindEntity;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRFireballEntity;
import com.github.standobyte.jojoimpl.stands.magiciansred.MRFlameEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleBarrierEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleCutterEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBubbleEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonBlockChargeEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonCutterEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonMasterEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonProjectileShieldEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonSendoOverdriveEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonTurquoiseBlueOverdriveEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.HamonZoomPunchEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.LeavesGliderEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.CrimsonBubbleEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfBindingEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SatiporojaScarfEntity;
import com.github.standobyte.jojoimpl.powers.hamon.entity.SnakeMufflerEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanDivineSandstormEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanHornEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanRibEntity;
import com.github.standobyte.jojoimpl.powers.pillarman.PillarmanVeinEntity;
import com.github.standobyte.jojoimpl.powers.vampirism.entity.HungryZombieEntity;
import com.github.standobyte.jojo.subsystems.soul.SoulEntity;
import com.github.standobyte.jojo.worldgen.structure.PillarmanTempleEngravingEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCFlameSwingEntity;
import com.github.standobyte.jojoimpl.stands.silverchariot.SCRapierEntity;
import com.github.standobyte.jojoimpl.stands.starplatinum.SPStarFingerEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class ModEntityTypes {
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, JojoMod.MOD_ID);
	
	@SubscribeEvent
	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(CHARACTER.get(), Player.createAttributes().add(Attributes.FOLLOW_RANGE, 16.0).build());
		event.put(HUMANOID_STAND.get(), StandEntity.createAttributes().build());
		event.put(STAR_PLATINUM_STAND.get(), StandEntity.createAttributes().build());
		event.put(THE_WORLD_STAND.get(), StandEntity.createAttributes().build());
		event.put(HIEROPHANT_GREEN_STAND.get(), StandEntity.createAttributes().build());
		event.put(SILVER_CHARIOT_STAND.get(), StandEntity.createAttributes().build());
		event.put(MAGICIANS_RED_STAND.get(), StandEntity.createAttributes().build());
		event.put(CRAZY_DIAMOND_STAND.get(), StandEntity.createAttributes().build());
		event.put(GOLD_EXPERIENCE_STAND.get(), StandEntity.createAttributes().build());
		event.put(MANNEQUIN.get(), ArmorStand.createAttributes().build());
		event.put(COCO_JUMBO_TURTLE.get(), CocoJumboTurtleEntity.createAttributes().build());
		event.put(RPS_KID.get(), RockPaperScissorsKidEntity.createAttributes().build());
		event.put(GE_LIFEFORM_TRANSFORMATION.get(), com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity.createAttributes().build());
		event.put(HUNGRY_ZOMBIE.get(), HungryZombieEntity.createAttributes().build());
		event.put(HAMON_MASTER.get(), HamonMasterEntity.createAttributes().build());
	}

	/**
	 * Slice 5d-3 NPC encounter pool — RPS Kid spawn placement.
	 *
	 * <p>Legacy 1.16.5 RPS Kid never naturally world-spawns; instead it spawns by converting an
	 * existing villager kid via {@code MobEntity.convertTo} guarded by
	 * {@code ForgeEventFactory.canLivingConvert} (legacy entity extends VillagerEntity). The
	 * villager-conversion trigger requires the full RPS gameplay system (interactive game,
	 * pick screen, 4-packet protocol, /rps command, RPSPvpGamesMap, advancement criterion) and
	 * is sub-routed to {@code [Category 6 shared-core authorization required] → future
	 * RPS gameplay system slice (user-granted authorization)}.</p>
	 *
	 * <p>This 5d-3 spawn-config registration adds a placement-type/heightmap entry so the entity
	 * type is well-formed for /summon, datapack mob-loot, and biome-modifier integration, but
	 * the predicate always returns {@code false} — natural worldgen spawn is intentionally
	 * disabled to match legacy semantics. {@link RegisterSpawnPlacementsEvent.Operation#OR}
	 * is used so future depth passes (or addon mods) can re-introduce a natural-spawn predicate
	 * additively without replacing the no-spawn baseline.</p>
	 */
	@SubscribeEvent
	public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
		event.register(RPS_KID.get(),
				SpawnPlacementTypes.ON_GROUND,
				Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(entityType, level, spawnType, pos, random) -> false,
				RegisterSpawnPlacementsEvent.Operation.OR);
	}

	
	public static final DeferredHolder<EntityType<?>, EntityType<PowerUserMobEntity>> CHARACTER = ENTITY_TYPES.register("character", key -> 
			EntityType.Builder.<PowerUserMobEntity>of(PowerUserMobEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));
	
	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> HUMANOID_STAND = ENTITY_TYPES.register("humanoid_stand", key -> 
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.6F, 1.8F)
			.eyeHeight(1.62F)
			.vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> STAR_PLATINUM_STAND = ENTITY_TYPES.register("star_platinum_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.7F, 2.1F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> THE_WORLD_STAND = ENTITY_TYPES.register("the_world_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.7F, 2.1F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HierophantGreenEntity>> HIEROPHANT_GREEN_STAND = ENTITY_TYPES.register("hierophant_green_stand", key ->
			EntityType.Builder.<HierophantGreenEntity>of(HierophantGreenEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.6F, 1.9F)
			.eyeHeight(1.62F)
			.vehicleAttachment(Player.DEFAULT_VEHICLE_ATTACHMENT)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> SILVER_CHARIOT_STAND = ENTITY_TYPES.register("silver_chariot_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.6F, 1.95F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> MAGICIANS_RED_STAND = ENTITY_TYPES.register("magicians_red_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.65F, 1.95F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> CRAZY_DIAMOND_STAND = ENTITY_TYPES.register("crazy_diamond_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.65F, 1.95F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandEntity>> GOLD_EXPERIENCE_STAND = ENTITY_TYPES.register("gold_experience_stand", key ->
			EntityType.Builder.of(StandEntity::new, MobCategory.MISC)
			.noSave()
			.noSummon()
			.sized(0.65F, 1.95F)
			.clientTrackingRange(32)
			.updateInterval(2)
			.build(createIDFor(key)));
	
	public static final DeferredHolder<EntityType<?>, EntityType<MannequinEntity>> MANNEQUIN = ENTITY_TYPES.register("mannequin", key ->
			EntityType.Builder.<MannequinEntity>of(MannequinEntity::new, MobCategory.MISC)
			.sized(0.5F, 1.975F)
			.eyeHeight(1.7775F)
			.clientTrackingRange(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CocoJumboTurtleEntity>> COCO_JUMBO_TURTLE = ENTITY_TYPES.register("coco_jumbo_turtle", key ->
			EntityType.Builder.<CocoJumboTurtleEntity>of(CocoJumboTurtleEntity::new, MobCategory.CREATURE)
			.sized(1.2F, 0.4F)
			.clientTrackingRange(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<BlockShardEntity>> BLOCK_SHARD = ENTITY_TYPES.register("block_shard", key ->
			EntityType.Builder.<BlockShardEntity>of(BlockShardEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<ObjectEntity>> OBJECT = ENTITY_TYPES.register("util_object", key ->
			EntityType.Builder.<ObjectEntity>of(ObjectEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<ThrownNuggetBearingEntity>> NUGGET_BEARING = ENTITY_TYPES.register("nugget_bearing", key -> 
			EntityType.Builder.<ThrownNuggetBearingEntity>of(ThrownNuggetBearingEntity::new, MobCategory.MISC)
//			.noLootTable()
			.sized(0.125F, 0.125F)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<ClackersEntity>> CLACKERS = ENTITY_TYPES.register("clackers", key ->
			EntityType.Builder.<ClackersEntity>of(ClackersEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<RoadRollerEntity>> ROAD_ROLLER = ENTITY_TYPES.register("road_roller", key ->
			EntityType.Builder.<RoadRollerEntity>of(RoadRollerEntity::new, MobCategory.MISC)
			.sized(4.0F, 2.0F)
			.updateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<BladeHatEntity>> BLADE_HAT = ENTITY_TYPES.register("blade_hat", key ->
			EntityType.Builder.<BladeHatEntity>of(BladeHatEntity::new, MobCategory.MISC)
			.sized(0.6F, 0.375F)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<KnifeEntity>> KNIFE = ENTITY_TYPES.register("knife", key ->
			EntityType.Builder.<KnifeEntity>of(KnifeEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MolotovEntity>> MOLOTOV = ENTITY_TYPES.register("molotov", key ->
			EntityType.Builder.<MolotovEntity>of(MolotovEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.updateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<TommyGunBulletEntity>> TOMMY_GUN_BULLET = ENTITY_TYPES.register("tommy_gun_bullet", key ->
			EntityType.Builder.<TommyGunBulletEntity>of(TommyGunBulletEntity::new, MobCategory.MISC)
			.fireImmune()
			.sized(0.0625F, 0.0625F)
			.clientTrackingRange(16)
			.updateInterval(1)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<LightBeamEntity>> AJA_STONE_BEAM = ENTITY_TYPES.register("aja_stone_beam", key ->
			EntityType.Builder.<LightBeamEntity>of(LightBeamEntity::new, MobCategory.MISC)
			.noSummon()
			.noSave()
			.sized(0.25F, 0.25F)
			.clientTrackingRange(16)
			.updateInterval(1)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SpaceRipperStingyEyesEntity>> SPACE_RIPPER_STINGY_EYES = ENTITY_TYPES.register("space_ripper_stingy_eyes", key ->
			EntityType.Builder.<SpaceRipperStingyEyesEntity>of(SpaceRipperStingyEyesEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(0.25F, 0.25F)
			.clientTrackingRange(16)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonProjectileShieldEntity>> HAMON_PROJECTILE_SHIELD = ENTITY_TYPES.register("hamon_projectile_shield", key ->
			EntityType.Builder.<HamonProjectileShieldEntity>of(HamonProjectileShieldEntity::new, MobCategory.MISC)
			.noSummon()
			.noSave()
			.fireImmune()
			.sized(3.0F, 3.0F)
			.clientTrackingRange(32)
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonBlockChargeEntity>> HAMON_BLOCK_CHARGE = ENTITY_TYPES.register("hamon_block_charge", key ->
			EntityType.Builder.<HamonBlockChargeEntity>of(HamonBlockChargeEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(1.0F, 1.0F)
			.clientTrackingRange(16)
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(5)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<LeavesGliderEntity>> LEAVES_GLIDER = ENTITY_TYPES.register("leaves_glider", key ->
			EntityType.Builder.<LeavesGliderEntity>of(LeavesGliderEntity::new, MobCategory.MISC)
			.sized(2.5F, 0.125F)
			.clientTrackingRange(10)
			.setUpdateInterval(2)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonCutterEntity>> HAMON_CUTTER = ENTITY_TYPES.register("hamon_cutter", key ->
			EntityType.Builder.<HamonCutterEntity>of(HamonCutterEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.125F)
			.clientTrackingRange(16)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonZoomPunchEntity>> HAMON_ZOOM_PUNCH = ENTITY_TYPES.register("zoom_punch", key ->
			EntityType.Builder.<HamonZoomPunchEntity>of(HamonZoomPunchEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonSendoOverdriveEntity>> SENDO_HAMON_OVERDRIVE = ENTITY_TYPES.register("sendo_hamon_overdrive", key ->
			EntityType.Builder.<HamonSendoOverdriveEntity>of(HamonSendoOverdriveEntity::new, MobCategory.MISC)
			.sized(4.0F, 4.0F)
			.fireImmune()
			.clientTrackingRange(32)
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonTurquoiseBlueOverdriveEntity>> TURQUOISE_BLUE_OVERDRIVE = ENTITY_TYPES.register("turquoise_blue_overdrive", key ->
			EntityType.Builder.<HamonTurquoiseBlueOverdriveEntity>of(HamonTurquoiseBlueOverdriveEntity::new, MobCategory.MISC)
			.sized(4.0F, 4.0F)
			.noSummon()
			.fireImmune()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonBubbleEntity>> HAMON_BUBBLE = ENTITY_TYPES.register("hamon_bubble", key ->
			EntityType.Builder.<HamonBubbleEntity>of(HamonBubbleEntity::new, MobCategory.MISC)
			.sized(0.15F, 0.15F)
			.clientTrackingRange(16)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonBubbleBarrierEntity>> HAMON_BUBBLE_BARRIER = ENTITY_TYPES.register("hamon_bubble_barrier", key ->
			EntityType.Builder.<HamonBubbleBarrierEntity>of(HamonBubbleBarrierEntity::new, MobCategory.MISC)
			.sized(2.0F, 2.0F)
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonBubbleCutterEntity>> HAMON_BUBBLE_CUTTER = ENTITY_TYPES.register("hamon_bubble_cutter", key ->
			EntityType.Builder.<HamonBubbleCutterEntity>of(HamonBubbleCutterEntity::new, MobCategory.MISC)
			.sized(0.325F, 0.0875F)
			.clientTrackingRange(16)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CrimsonBubbleEntity>> CRIMSON_BUBBLE = ENTITY_TYPES.register("crimson_bubble", key ->
			EntityType.Builder.<CrimsonBubbleEntity>of(CrimsonBubbleEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(0.625F, 0.625F)
			.clientTrackingRange(16)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SatiporojaScarfEntity>> SATIPOROJA_SCARF = ENTITY_TYPES.register("satiporoja_scarf", key ->
			EntityType.Builder.<SatiporojaScarfEntity>of(SatiporojaScarfEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(16)
			.setUpdateInterval(1)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SatiporojaScarfBindingEntity>> SATIPOROJA_SCARF_BINDING = ENTITY_TYPES.register("satiporoja_scarf_binding", key ->
			EntityType.Builder.<SatiporojaScarfBindingEntity>of(SatiporojaScarfBindingEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(16)
			.setUpdateInterval(1)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SnakeMufflerEntity>> SNAKE_MUFFLER = ENTITY_TYPES.register("snake_muffler", key ->
			EntityType.Builder.<SnakeMufflerEntity>of(SnakeMufflerEntity::new, MobCategory.MISC)
			.noSummon()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(16)
			.setUpdateInterval(1)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CrazyDBlockBulletEntity>> CD_BLOCK_BULLET = ENTITY_TYPES.register("cd_block_bullet", key -> 
			EntityType.Builder.<CrazyDBlockBulletEntity>of(CrazyDBlockBulletEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.noSummon()
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CrazyDBloodCutterEntity>> CD_BLOOD_CUTTER = ENTITY_TYPES.register("cd_blood_cutter", key -> 
			EntityType.Builder.<CrazyDBloodCutterEntity>of(CrazyDBloodCutterEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.noSummon()
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CrazyDEyeOfEnderInsideEntity>> EYE_OF_ENDER_INSIDE = ENTITY_TYPES.register("eye_of_ender_inside", key ->
			EntityType.Builder.<CrazyDEyeOfEnderInsideEntity>of(CrazyDEyeOfEnderInsideEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.setUpdateInterval(4)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<CrazyDFireworkInsideEntity>> FIREWORK_INSIDE = ENTITY_TYPES.register("firework_inside", key ->
			EntityType.Builder.<CrazyDFireworkInsideEntity>of(CrazyDFireworkInsideEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(4)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<AngeloRockEntity>> ANGELO_ROCK = ENTITY_TYPES.register("angelo_rock", key ->
			EntityType.Builder.<AngeloRockEntity>of(AngeloRockEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.75F)
			.clientTrackingRange(10)
			.updateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<StandArrowEntity>> STAND_ARROW = ENTITY_TYPES.register("stand_arrow", key ->
			EntityType.Builder.<StandArrowEntity>of(StandArrowEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F).eyeHeight(0.13F)
			.clientTrackingRange(4)
			.updateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<AfterimageEntity>> AFTERIMAGE = ENTITY_TYPES.register("afterimage", key ->
			EntityType.Builder.<AfterimageEntity>of(AfterimageEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.8F)
			.noSummon()
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SPStarFingerEntity>> SP_STAR_FINGER = ENTITY_TYPES.register("sp_star_finger", key ->
			EntityType.Builder.<SPStarFingerEntity>of(SPStarFingerEntity::new, MobCategory.MISC)
			.sized(0.125F, 0.125F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HGEmeraldEntity>> HG_EMERALD = ENTITY_TYPES.register("hg_emerald", key ->
			EntityType.Builder.<HGEmeraldEntity>of(HGEmeraldEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HGStringEntity>> HG_STRING = ENTITY_TYPES.register("hg_string", key ->
			EntityType.Builder.<HGStringEntity>of(HGStringEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HGGrappleEntity>> HG_GRAPPLE = ENTITY_TYPES.register("hg_grappling_string", key ->
			EntityType.Builder.<HGGrappleEntity>of(HGGrappleEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HGBarrierEntity>> HG_BARRIER = ENTITY_TYPES.register("hg_barrier", key ->
			EntityType.Builder.<HGBarrierEntity>of(HGBarrierEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRFlameEntity>> MR_FLAME = ENTITY_TYPES.register("mr_flame", key ->
			EntityType.Builder.<MRFlameEntity>of(MRFlameEntity::new, MobCategory.MISC)
			.sized(0.0625F, 0.0625F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRDetectorEntity>> MR_DETECTOR = ENTITY_TYPES.register("mr_detector", key ->
			EntityType.Builder.<MRDetectorEntity>of(MRDetectorEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<PillarmanHornEntity>> PILLAR_MAN_HORN = ENTITY_TYPES.register("pm_horn", key ->
			EntityType.Builder.<PillarmanHornEntity>of(PillarmanHornEntity::new, MobCategory.MISC)
			.sized(0.125F, 0.125F)
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<PillarmanRibEntity>> PILLAR_MAN_RIBS = ENTITY_TYPES.register("pillarman_ribs", key ->
			EntityType.Builder.<PillarmanRibEntity>of(PillarmanRibEntity::new, MobCategory.MISC)
			.sized(0.35F, 0.35F)
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<PillarmanVeinEntity>> PILLAR_MAN_VEINS = ENTITY_TYPES.register("pillarman_veins", key ->
			EntityType.Builder.<PillarmanVeinEntity>of(PillarmanVeinEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<PillarmanDivineSandstormEntity>> PILLAR_MAN_DIVINE_SANDSTORM = ENTITY_TYPES.register("pillarman_divine_sandstorm", key ->
			EntityType.Builder.<PillarmanDivineSandstormEntity>of(PillarmanDivineSandstormEntity::new, MobCategory.MISC)
			.sized(1.8F, 1.8F)
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<PillarmanTempleEngravingEntity>> PILLARMAN_TEMPLE_ENGRAVING = ENTITY_TYPES.register("pillarman_temple_engraving", key ->
			EntityType.Builder.<PillarmanTempleEngravingEntity>of(PillarmanTempleEngravingEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.noSummon()
			.setShouldReceiveVelocityUpdates(false)
			.setUpdateInterval(Integer.MAX_VALUE)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SCFlameSwingEntity>> SC_FLAME = ENTITY_TYPES.register("sc_flame", key ->
			EntityType.Builder.<SCFlameSwingEntity>of(SCFlameSwingEntity::new, MobCategory.MISC)
			.sized(0.0625F, 0.0625F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRFireballEntity>> MR_FIREBALL = ENTITY_TYPES.register("mr_fireball", key ->
			EntityType.Builder.<MRFireballEntity>of(MRFireballEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRCrossfireHurricaneEntity>> MR_CROSSFIRE_HURRICANE = ENTITY_TYPES.register("mr_crossfire_hurricane", key ->
			EntityType.Builder.<MRCrossfireHurricaneEntity>of(MRCrossfireHurricaneEntity::new, MobCategory.MISC)
			.sized(1.25F, 1.8F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRCrossfireHurricaneEntity>> MR_CROSSFIRE_HURRICANE_SPECIAL = ENTITY_TYPES.register("mr_crossfire_hurricane_special", key ->
			EntityType.Builder.<MRCrossfireHurricaneEntity>of(MRCrossfireHurricaneEntity::new, MobCategory.MISC)
			.sized(0.625F, 0.9F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<MRRedBindEntity>> MR_RED_BIND = ENTITY_TYPES.register("mr_red_bind", key ->
			EntityType.Builder.<MRRedBindEntity>of(MRRedBindEntity::new, MobCategory.MISC)
			.sized(0.25F, 0.25F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(20)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<RockPaperScissorsKidEntity>> RPS_KID = ENTITY_TYPES.register("rps_kid", key ->
			EntityType.Builder.<RockPaperScissorsKidEntity>of(RockPaperScissorsKidEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 1.5F)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HungryZombieEntity>> HUNGRY_ZOMBIE = ENTITY_TYPES.register("hungry_zombie", key ->
			EntityType.Builder.<HungryZombieEntity>of(HungryZombieEntity::new, MobCategory.MONSTER)
			.sized(0.6F, 1.95F)
			.eyeHeight(1.74F)
			.clientTrackingRange(8)
			.updateInterval(3)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<HamonMasterEntity>> HAMON_MASTER = ENTITY_TYPES.register("hamon_master", key ->
			EntityType.Builder.<HamonMasterEntity>of(HamonMasterEntity::new, MobCategory.MISC)
			.sized(0.6F, 1.95F)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity>> GE_LIFEFORM_TRANSFORMATION = ENTITY_TYPES.register("ge_lifeform", key ->
			EntityType.Builder.<com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity>of(com.github.standobyte.jojoimpl.stands.goldexperience.GETransformationEntity::new, MobCategory.MISC)
			.sized(1.0F, 1.0F)
			.noSummon()
			.clientTrackingRange(32)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SCRapierEntity>> SC_RAPIER = ENTITY_TYPES.register("sc_rapier", key ->
			EntityType.Builder.<SCRapierEntity>of(SCRapierEntity::new, MobCategory.MISC)
			.sized(0.125F, 0.125F)
			.noSummon()
			.noSave()
			.clientTrackingRange(32)
			.setUpdateInterval(10)
			.build(createIDFor(key)));

	public static final DeferredHolder<EntityType<?>, EntityType<SoulEntity>> SOUL = ENTITY_TYPES.register("soul", key ->
			EntityType.Builder.<SoulEntity>of(SoulEntity::new, MobCategory.MISC)
			.sized(0.5F, 0.5F)
			.noSummon()
			.noSave()
			.clientTrackingRange(8)
			.updateInterval(10)
			.build(createIDFor(key)));



//	public static ResourceKey<EntityType<?>> createIDFor(ResourceLocation key) {
//		return ResourceKey.create(Registries.ENTITY_TYPE, key);
//	}
	
	public static String createIDFor(ResourceLocation key) {
		return key.toString();
	}
	
}
