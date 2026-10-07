package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.RoadRollerEntity;
import rotp.core.customobjects.entity_projectile.ModdedProjectileEntity;
import rotp.core.customobjects.entity_projectile.TommyGunBulletEntity;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.impl.stands.crazydiamond.CrazyDEyeOfEnderInsideEntity;
import rotp.core.impl.stands.hierophant.HGEmeraldEntity;
import rotp.core.init.ModBlocks;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.mechanics.BleedingEffect;
import rotp.core.network.c2s.ClAngeloRockButtonPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 LivingEntity.hurt knocked back only when source.getEntity() != null, and DamageUtil.knockbackReduction took
 * the knockback of blood drain, cold and Road Roller damage down to nothing. 1.21 hurt() knocks back for every damage type outside
 * minecraft:no_knockback, in a random direction when the hit has no origin. Each test deals the damage of one site
 * that 1.16 dealt without knockback and reads the victim's knockback and speed.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AttackerlessKnockbackGameTests {
	private static final Vec3 VICTIM = new Vec3(2.5D, 3.0D, 2.5D);
	private static final short KEY = 77;

	private static final List<ResourceKey<DamageType>> NEVER_KNOCK_BACK = List.of(
			ModDamageTypes.STONE_MASK, ModDamageTypes.CURED_VAMPIRE_BLOOD, ModDamageTypes.BLOOD_DRAIN,
			ModDamageTypes.BLOOD_GIFT, ModDamageTypes.VAMPIRE_FREEZE, ModDamageTypes.ROCK_BROKEN,
			ModDamageTypes.ROCK_RESPAWN, ModDamageTypes.EYE_OF_ENDER_SHARDS, ModDamageTypes.ROAD_ROLLER,
			ModDamageTypes.STAND_HEALTH_LINK, ModDamageTypes.BLEED_OUT_DEATH, ModDamageTypes.ULTRAVIOLET,
			ModDamageTypes.PILLAR_MAN_SELF_DETONATION, ModDamageTypes.SUFFOCATION, ModDamageTypes.STAND_ARROW,
			ModDamageTypes.STAND_ARROW_VIRUS);
	private static final List<ResourceKey<DamageType>> KNOCK_BACK_FROM_AN_ORIGIN_ONLY = List.of(
			ModDamageTypes.STAND_ATTACK, ModDamageTypes.STAND_PROJECTILE, ModDamageTypes.STAND_PROJECTILE_FIRE,
			ModDamageTypes.STAND_EXPLOSION_FIRE, ModDamageTypes.MOD_PROJECTILE, ModDamageTypes.MOD_PROJECTILE_FIRE,
			ModDamageTypes.ENTITY_FLEW_INTO, ModDamageTypes.HAMON,
			ModDamageTypes.ULTRAVIOLET_ENTITY, ModDamageTypes.PILLAR_MAN_ABSORPTION);

	private AttackerlessKnockbackGameTests() {}

	@GameTest(template = "empty", batch = "attackerless_knockback_tags")
	public static void damageTypeTagsCarryTheDonorKnockbackRules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<String> wrong = new ArrayList<>();
		for (ResourceKey<DamageType> type : NEVER_KNOCK_BACK) {
			if (!DamageUtil.make(level, type).is(DamageTypeTags.NO_KNOCKBACK)) {
				wrong.add(type.location() + " is not in minecraft:no_knockback");
			}
		}
		for (ResourceKey<DamageType> type : KNOCK_BACK_FROM_AN_ORIGIN_ONLY) {
			DamageSource source = DamageUtil.make(level, type);
			if (source.is(DamageTypeTags.NO_KNOCKBACK)) {
				wrong.add(type.location() + " is in minecraft:no_knockback, but 1.16 knocked back with it from an attacker");
			}
			if (!source.is(ModDamageTypes.NO_SOURCELESS_KNOCKBACK)) {
				wrong.add(type.location() + " is not in jojo_ripples:no_sourceless_knockback");
			}
		}
		for (DamageSource vanilla : List.of(level.damageSources().magic(), level.damageSources().cactus(),
				level.damageSources().generic(), DamageUtil.make(level, DamageTypes.MOB_ATTACK))) {
			if (vanilla.is(ModDamageTypes.NO_SOURCELESS_KNOCKBACK)) {
				wrong.add("vanilla " + vanilla.type().msgId() + " is in jojo_ripples:no_sourceless_knockback");
			}
		}
		helper.assertTrue(wrong.isEmpty(), "Damage type tags differ from the 1.16 knockback rules: " + wrong);
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_hook")
	public static void rotpHitWithoutAnOriginDoesNotKnockBack(GameTestHelper helper) {
		play(helper, "A Hamon hit with no entity and no position", true, scene -> {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			Probe probe = scene.probe(victim);
			helper.assertTrue(victim.hurt(DamageUtil.make(scene.level, ModDamageTypes.HAMON), 1.0F),
					"premise: the plain Hamon hit did not land");
			probe.requireHitsOf(ModDamageTypes.HAMON);
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_attacker")
	public static void rotpHitFromAnAttackerStillKnocksBack(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			Cow attacker = scene.cow(helper.absoluteVec(VICTIM.add(0.0D, 0.0D, 2.0D)));
			Probe probe = scene.probe(victim);
			helper.assertTrue(victim.hurt(DamageUtil.make(scene.level, ModDamageTypes.HAMON, attacker), 1.0F),
					"premise: the attributed Hamon hit did not land");
			Vec3 speed = victim.getDeltaMovement();
			helper.assertTrue(probe.pushes.size() == 1 && speed.x == 0.0D && Math.abs(speed.z + 0.4D) < 1.0E-6D,
					"A Hamon hit from an attacker lost its knockback away from that attacker: knockback="
							+ probe.pushes + " speed=" + speed);
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_vanilla")
	public static void vanillaHitWithoutAnOriginKeepsIts121Knockback(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			Probe probe = scene.probe(victim);
			// vanilla keeps its own origin-less types in minecraft:no_knockback; this one is outside it
			DamageSource plain = DamageUtil.make(scene.level, DamageTypes.MOB_ATTACK);
			helper.assertTrue(!plain.is(DamageTypeTags.NO_KNOCKBACK) && victim.hurt(plain, 1.0F),
					"premise: the plain mob_attack hit did not land");
			Vec3 speed = victim.getDeltaMovement();
			helper.assertTrue(probe.pushes.size() == 1 && Math.abs(speed.horizontalDistance() - 0.4D) < 1.0E-6D,
					"A vanilla damage type lost the 1.21 knockback of a hit with no origin: knockback=" + probe.pushes
							+ " speed=" + speed);
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_stone_mask")
	public static void ajaStoneMaskDeathDoesNotKnockBack(GameTestHelper helper) {
		play(helper, "The Aja Stone Mask killing a wearer who is no Pillar Man", false, scene -> {
			Player wearer = scene.player(helper.absoluteVec(VICTIM));
			PowerClass.PLAYER_POWER.attachGet(wearer);
			wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.AJA_STONE_MASK.get()));
			Probe probe = scene.probe(wearer);
			// Player.die gives the body its own speed, so only the knockback itself is read
			probe.readSpeed = false;
			BleedingEffect.splashBlood(scene.level, wearer.getBoundingBox().getCenter(), 2.0D, 5.0F, OptionalInt.empty(), null);
			probe.requireHitsOf(ModDamageTypes.STONE_MASK);
			helper.assertTrue(wearer.isDeadOrDying(), "premise: the Aja Stone Mask did not kill its wearer");
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_cured_blood")
	public static void curedVampireBloodNeitherKnocksBackNorStopsTheDrain(GameTestHelper helper) {
		play(helper, "A cured vampire's own blood damage", false, scene -> {
			Player user = scene.player(helper.absoluteVec(VICTIM));
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			Ability drain = power.getAbility("vampirism_blood_drain");
			Cow prey = scene.cow(helper.absoluteVec(VICTIM.add(0.0D, 0.0D, 1.5D)));
			LivingComponentAction component = LivingComponentAction.getComponent(user);
			component.entityAim.setTarget(new ActionTarget(prey));
			helper.assertTrue(drain instanceof VampirismBloodDrainAbility && drain.checkConditions(power).isPositive(),
					"premise: the registered Blood Drain is not usable on the cow");
			HeldInputEntry held = AbilityInput.keyPress(KEY, drain, user, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), drain.getAbilityId());
			scene.beforeDiscard = () -> {
				AbilityInput.keyRelease(KEY, user);
				component.entityAim.setTarget(ActionTarget.EMPTY);
				component.setAction(null, user, SyncType.NO_SYNC);
			};
			EntityActionInstance action = component.getAction();
			helper.assertTrue(held != null && action != null && held.action == action
					&& action.getPhase() == ActionPhase.PERFORM, "premise: the Blood Drain hold did not start");

			Probe probe = scene.probe(user);
			helper.assertTrue(VampirismBloodDrainAbility.hurtWithCuredVampireBlood(user, 1.0F),
					"premise: the cured blood damage did not land");
			probe.requireHitsOf(ModDamageTypes.CURED_VAMPIRE_BLOOD);
			DamageSource source = probe.hits.get(0);
			helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null
					&& component.getAction() == action && !action.isOver() && AbilityInput.isHeldByKey(user, action),
					"1.16 curedVampireBlood has no attacker, so it does not stop the held Blood Drain it comes from: attacker="
							+ source.getEntity() + " direct=" + source.getDirectEntity() + " holdOver=" + action.isOver());
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_cold")
	public static void coldDamageWithoutAnAttackerDoesNotKnockBack(GameTestHelper helper) {
		play(helper, "Cold damage with no attacker", true, scene -> {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			Probe probe = scene.probe(victim);
			helper.assertTrue(DamageUtil.dealColdDamage(victim, 2.0F, null, null), "premise: the cold damage did not land");
			probe.requireHitsOf(ModDamageTypes.VAMPIRE_FREEZE);
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_rock_broken")
	public static void brokenAngeloRockKillDoesNotKnockBack(GameTestHelper helper) {
		rockKill(helper, false);
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_rock_respawn")
	public static void angeloRockRespawnKillDoesNotKnockBack(GameTestHelper helper) {
		rockKill(helper, true);
	}

	private static void rockKill(GameTestHelper helper, boolean respawn) {
		play(helper, respawn ? "The Angelo rock Respawn kill" : "The broken Angelo rock kill", false, scene -> {
			VulnerablePlayer sealed = new VulnerablePlayer(scene.level, respawn ? "RockRespawnPush" : "RockBrokenPush");
			sealed.setGameMode(GameType.SURVIVAL);
			sealed.setNoGravity(true);
			sealed.setPos(helper.absoluteVec(VICTIM));
			helper.assertTrue(scene.level.addFreshEntity(sealed), "premise: could not add the sealed player");
			scene.entities.add(sealed);
			AngeloRockEntity rock = new AngeloRockEntity(ModEntityTypes.ANGELO_ROCK.get(), scene.level);
			rock.setPos(helper.absoluteVec(VICTIM));
			helper.assertTrue(scene.level.addFreshEntity(rock), "premise: could not add the Angelo rock");
			scene.entities.add(rock);
			LivingComponentPossession.setPossessionTarget(sealed, rock, "angelo_rock");
			helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(sealed) == rock,
					"premise: the player is not sealed in the rock");

			Probe probe = scene.probe(sealed);
			if (respawn) {
				ClAngeloRockButtonPacket.handleOnServer(sealed, ClAngeloRockButtonPacket.respawn());
			}
			else {
				rock.breakRock();
				rock.tick();
				sealed.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get()).tick();
			}
			probe.requireHitsOf(respawn ? ModDamageTypes.ROCK_RESPAWN : ModDamageTypes.ROCK_BROKEN);
			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(sealed) && sealed.getHealth() <= 0.0F,
					"premise: leaving the rock did not kill the player");
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_slumbering_block")
	public static void slumberingPillarManBlockDoesNotKnockBack(GameTestHelper helper) {
		play(helper, "The Slumbering Pillar Man block absorbing an entity in it", true, scene -> {
			BlockPos relative = new BlockPos(2, 3, 2);
			scene.block(relative, ModBlocks.SLUMBERING_PILLARMAN.get().defaultBlockState());
			BlockPos pos = helper.absolutePos(relative);
			BlockState state = scene.level.getBlockState(pos);
			helper.assertTrue(state.is(ModBlocks.SLUMBERING_PILLARMAN.get()), "premise: the block was not placed");
			// off the block centre, so that a position on the hit would give the knockback a direction
			Cow victim = scene.cow(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.75D));
			Probe probe = scene.probe(victim);
			float health = victim.getHealth();
			state.entityInside(scene.level, pos, victim);
			probe.requireHitsOf(ModDamageTypes.PILLAR_MAN_ABSORPTION);
			DamageSource source = probe.hits.get(0);
			helper.assertTrue(health - victim.getHealth() == 4.0F && source.getEntity() == null
					&& source.getDirectEntity() == null && source.getSourcePosition() == null,
					"1.16 PillarmanBossMultiBlock deals 4 pillarManAbsorption with no attacker and no position: damage="
							+ (health - victim.getHealth()) + " attacker=" + source.getEntity() + " direct="
							+ source.getDirectEntity() + " position=" + source.getSourcePosition());
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_eye_of_ender")
	public static void eyeOfEnderShardsDoNotKnockBack(GameTestHelper helper) {
		play(helper, "The Eye of Ender breaking inside an entity", true, scene -> {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			CrazyDEyeOfEnderInsideEntity eye = new CrazyDEyeOfEnderInsideEntity(scene.level, victim);
			helper.assertTrue(scene.level.addFreshEntity(eye) && victim.getVehicle() == eye,
					"premise: the Eye of Ender is not inside the victim");
			scene.entities.add(eye);
			Probe probe = scene.probe(victim);
			eye.tickCount = 75;
			eye.tick();
			probe.requireHitsOf(ModDamageTypes.EYE_OF_ENDER_SHARDS);
			helper.assertTrue(eye.isRemoved() && victim.getVehicle() == null, "premise: the Eye of Ender did not break");
			DamageSource source = probe.hits.get(0);
			helper.assertTrue(source.getEntity() == null && source.getDirectEntity() == null
					&& source.getSourcePosition() == null,
					"1.16 eyeOfEnderShards is a plain damage source with no attacker and no position: attacker="
							+ source.getEntity() + " direct=" + source.getDirectEntity() + " position="
							+ source.getSourcePosition());
			return probe;
		});
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_ownerless_bullet")
	public static void ownerlessBulletDoesNotKnockBack(GameTestHelper helper) {
		ownerlessProjectile(helper, "A bullet with no shooter", ModDamageTypes.MOD_PROJECTILE,
				new TommyGunBulletEntity(ModEntityTypes.TOMMY_GUN_BULLET.get(), helper.getLevel()));
	}

	@GameTest(template = "empty", batch = "attackerless_knockback_ownerless_stand_projectile")
	public static void ownerlessStandProjectileDoesNotKnockBack(GameTestHelper helper) {
		ownerlessProjectile(helper, "A Stand projectile with no owner", ModDamageTypes.STAND_PROJECTILE,
				new HGEmeraldEntity(ModEntityTypes.HG_EMERALD.get(), helper.getLevel()));
	}

	// 1.16 IndirectEntityDamageSource(projectile, null): getEntity() is null, so hurt() did not knock back
	private static void ownerlessProjectile(GameTestHelper helper, String site, ResourceKey<DamageType> type,
			ModdedProjectileEntity projectile) {
		play(helper, site, true, scene -> {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			Vec3 centre = victim.getBoundingBox().getCenter();
			projectile.setPos(centre.x - 1.0D, centre.y, centre.z);
			projectile.setDeltaMovement(0.8D, 0.0D, 0.0D);
			helper.assertTrue(projectile.getOwner() == null && scene.level.addFreshEntity(projectile),
					"premise: could not add the ownerless projectile");
			scene.entities.add(projectile);
			Probe probe = scene.probe(victim);
			projectile.tickCount = 0;
			projectile.tick();
			probe.requireHitsOf(type);
			DamageSource source = probe.hits.get(0);
			helper.assertTrue(probe.hits.size() == 1 && source.getEntity() == null && source.getDirectEntity() == projectile,
					"premise: the hit is not one hit of the projectile alone: hits=" + probe.hits.size() + " attacker="
							+ source.getEntity() + " direct=" + source.getDirectEntity());
			return probe;
		});
	}

	// 1.16 DamageUtil.knockbackReduction: roadRoller damage has knockback factor 0
	@GameTest(template = "empty", batch = "attackerless_knockback_road_roller")
	public static void roadRollerCrushDoesNotKnockBack(GameTestHelper helper) {
		play(helper, "The Road Roller crushing an entity under it", false, scene -> {
			Cow victim = scene.cow(helper.absoluteVec(VICTIM));
			RoadRollerEntity roller = new RoadRollerEntity(scene.level);
			// its lower quarter is in the victim, off the victim's centre
			roller.setPos(victim.getX() + 0.3D, victim.getY() + 1.0D, victim.getZ());
			helper.assertTrue(scene.level.addFreshEntity(roller), "premise: could not add the Road Roller");
			scene.entities.add(roller);
			Probe probe = scene.probe(victim);
			roller.tick();
			probe.requireHitsOf(ModDamageTypes.ROAD_ROLLER);
			return probe;
		});
	}

	// act deals the damage and returns the probe on its victim, which must not be pushed now nor one tick later
	private static void play(GameTestHelper helper, String site, boolean alsoNextTick, Function<Scene, Probe> act) {
		Scene scene = new Scene(helper);
		Probe probe;
		Vec3 pos;
		try {
			probe = act.apply(scene);
			pos = probe.victim.position();
			probe.requireNotPushed(site, pos);
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
		if (!alsoNextTick) {
			scene.close();
			helper.succeed();
			return;
		}
		helper.runAfterDelay(1, () -> {
			try {
				probe.requireNotPushed(site + ", one tick later", pos);
			}
			finally {
				scene.close();
			}
			helper.succeed();
		});
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final List<Entity> entities = new ArrayList<>();
		final List<Probe> probes = new ArrayList<>();
		final List<BlockPos> blocks = new ArrayList<>();
		Runnable beforeDiscard = () -> {};

		Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		Cow cow(Vec3 pos) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "premise: could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "premise: could not add a cow");
			entities.add(cow);
			return cow;
		}

		Player player(Vec3 pos) {
			Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			player.setNoGravity(true);
			player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(player), "premise: could not add a player");
			entities.add(player);
			return player;
		}

		void block(BlockPos relative, BlockState state) {
			blocks.add(relative);
			helper.setBlock(relative, state);
		}

		Probe probe(LivingEntity victim) {
			Probe probe = new Probe(helper, victim);
			probes.add(probe);
			return probe;
		}

		void close() {
			probes.forEach(Probe::close);
			try {
				beforeDiscard.run();
			}
			finally {
				entities.forEach(Entity::discard);
				blocks.forEach(relative -> helper.setBlock(relative, Blocks.AIR));
			}
		}
	}

	private static final class Probe {
		final GameTestHelper helper;
		final LivingEntity victim;
		final List<String> pushes = new ArrayList<>();
		final List<DamageSource> hits = new ArrayList<>();
		boolean readSpeed = true;
		private final Consumer<LivingKnockBackEvent> knockback;
		private final Consumer<LivingDamageEvent.Post> damage;

		Probe(GameTestHelper helper, LivingEntity victim) {
			this.helper = helper;
			this.victim = victim;
			this.knockback = event -> {
				if (event.getEntity() == victim && !event.isCanceled() && event.getStrength() > 0.0F) {
					pushes.add("[strength=" + event.getOriginalStrength() + "->" + event.getStrength() + " ratio="
							+ event.getRatioX() + "," + event.getRatioZ() + "]");
				}
			};
			this.damage = event -> {
				if (event.getEntity() == victim) {
					hits.add(event.getSource());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damage);
		}

		void requireHitsOf(ResourceKey<DamageType> type) {
			helper.assertTrue(!hits.isEmpty() && hits.stream().allMatch(hit -> hit.is(type)),
					"premise: the victim was not hurt by " + type.location() + " alone: "
							+ hits.stream().map(hit -> hit.typeHolder().getRegisteredName()).toList());
		}

		void requireNotPushed(String site, Vec3 pos) {
			Vec3 speed = victim.getDeltaMovement();
			Vec3 now = victim.position();
			boolean still = !readSpeed || speed.x == 0.0D && speed.z == 0.0D && now.x == pos.x && now.z == pos.z;
			JojoMod.LOGGER.info("ATTACKERLESS-KNOCKBACK {} knockback={} speed={} moved={}", site, pushes, speed,
					now.subtract(pos));
			helper.assertTrue(pushes.isEmpty() && still, site + " pushed its victim, 1.16 dealt this damage without"
					+ " knockback: knockback=" + pushes + " speed=" + speed + " moved=" + now.subtract(pos));
		}

		void close() {
			NeoForge.EVENT_BUS.unregister(knockback);
			NeoForge.EVENT_BUS.unregister(damage);
		}
	}

	/** FakePlayer takes no damage at all; this one takes the real hurt, and its die() still does nothing. */
	private static final class VulnerablePlayer extends FakePlayer {
		VulnerablePlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public boolean isInvulnerableTo(DamageSource source) {
			return false;
		}
	}
}
