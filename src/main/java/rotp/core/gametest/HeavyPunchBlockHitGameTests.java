package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands.theworld.TheWorldKickAbility.TheWorldKick;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HeavyPunchBlockInstance.doHit: a heavy punch or kick on a block first tries the plain block punch
 * (StandEntity.breakBlock with the strength rule), then makes its blast. The blast hurts with a fresh Stand source
 * through hurtTarget: no Stand-attack immunity is checked or left behind. Own batch: the block-breaking gamerule.
 * The blast reaches about 5.6 blocks, past the cell beside it, so each test that weighs its blast on cows runs in a
 * batch of its own: a neighbour's blast hurt one cow of a pair and not the other.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyPunchBlockHitGameTests {
	private static final String BATCH = "heavy_punch_block_hit";
	private static final String IMMUNITY_BATCH = "heavy_punch_block_hit_blast_immunity";
	private static final String PROTECTION_BATCH = "heavy_punch_block_hit_blast_protection";
	private static final short KEY = 163;
	private static final PowerSkillUnlocks.Owner UNLOCKS = PowerSkillUnlocks.register(
			JojoMod.resLoc(HeavyPunchBlockHitGameTests.class.getSimpleName().toLowerCase(Locale.ROOT)));
	private static Boolean oldBreakBlocks;

	private HeavyPunchBlockHitGameTests() {}

	@BeforeBatch(batch = BATCH)
	public static void allowBlockBreaking(ServerLevel level) {
		oldBreakBlocks = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
		level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, level.getServer());
	}

	@AfterBatch(batch = BATCH)
	public static void restoreBlockBreaking(ServerLevel level) {
		if (oldBreakBlocks != null) {
			level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreakBlocks, level.getServer());
			oldBreakBlocks = null;
		}
	}

	@BeforeBatch(batch = IMMUNITY_BATCH)
	public static void allowBlockBreakingForTheImmunityBlast(ServerLevel level) {
		allowBlockBreaking(level);
	}

	@AfterBatch(batch = IMMUNITY_BATCH)
	public static void restoreBlockBreakingAfterTheImmunityBlast(ServerLevel level) {
		restoreBlockBreaking(level);
	}

	@BeforeBatch(batch = PROTECTION_BATCH)
	public static void allowBlockBreakingForTheProtectionBlast(ServerLevel level) {
		allowBlockBreaking(level);
	}

	@AfterBatch(batch = PROTECTION_BATCH)
	public static void restoreBlockBreakingAfterTheProtectionBlast(ServerLevel level) {
		restoreBlockBreaking(level);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
	public static void heavyPunchBreaksObsidianBeforeItsBlast(GameTestHelper helper) {
		new Scene(helper, "star_platinum", 16, false).run(scene -> {
			helper.assertTrue(scene.level().getBlockState(scene.block).isAir(),
					"A strength-16 heavy punch left obsidian standing; 1.16 broke it before the blast");
			helper.assertTrue(scene.hitSounds == 0, "The broken obsidian also played " + scene.hitSounds + " hit sounds");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
	public static void kickBreaksObsidianBeforeItsBlast(GameTestHelper helper) {
		new Scene(helper, "the_world", 16, true).run(scene -> {
			helper.assertTrue(scene.level().getBlockState(scene.block).isAir(),
					"A strength-16 kick left obsidian standing; 1.16 broke it before the blast");
		});
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
	public static void weakHeavyPunchOnlyKnocksOnObsidian(GameTestHelper helper) {
		new Scene(helper, "star_platinum", 8, false).run(scene -> {
			helper.assertTrue(scene.level().getBlockState(scene.block).is(Blocks.OBSIDIAN),
					"A strength-8 heavy punch broke obsidian, which 1.16 refused");
			helper.assertTrue(scene.hitSounds == 1,
					"A refused heavy punch block hit played " + scene.hitSounds + " block hit sounds instead of one");
		});
	}

	// 1.16 HeavyPunchBlockInstance.playImpactSound returned true: the heavy impact sounds on any block
	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
	public static void heavyPunchOnAZeroHardnessBlockPlaysItsImpact(GameTestHelper helper) {
		new Scene(helper, "star_platinum", 16, false, Blocks.SLIME_BLOCK.defaultBlockState()).run(scene -> {
			helper.assertTrue(scene.level().getBlockState(scene.block).isAir(), "Fixture: the slime block still stands");
			helper.assertTrue(scene.impactSounds == 1, "A heavy punch on a slime block (hardness 0) played "
					+ scene.impactSounds + " heavy impact sounds; 1.16 played one");
		});
	}

	// a getPunchTarget override (an add-on's blink punch) can hand over a block target that is already air
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void heavyPunchOnABlockAlreadyGoneDoesNotThrow(GameTestHelper helper) {
		Scene scene = new Scene(helper, "star_platinum", 16, false, Blocks.AIR.defaultBlockState());
		try {
			StandEntityHeavyPunch action = new StandEntityHeavyPunch((EntityActionType) scene.power.getAbility("heavy_punch")) {
				@Override
				protected ActionTarget getPunchTarget(StandEntity stand) {
					return new ActionTarget(scene.block, Direction.NORTH);
				}
			};
			LivingComponentAction.getComponent(scene.stand).setAction(action, scene.user, SyncType.NO_SYNC);
			try {
				action.actionPerformStart();
			}
			catch (RuntimeException e) {
				throw new GameTestAssertException("A heavy punch on a block target already gone threw " + e);
			}
			helper.assertTrue(action.punchedTarget != null && action.punchedTarget.getType() == ActionTarget.TargetType.BLOCK,
					"The heavy punch did not finish its block hit: " + action.punchedTarget);
			helper.assertTrue(scene.impactSounds == 0,
					"A heavy punch on air played " + scene.impactSounds + " heavy impact sounds");
		}
		finally {
			LivingComponentAction.getComponent(scene.stand).setAction(null, scene.user, SyncType.NO_SYNC);
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = IMMUNITY_BATCH)
	public static void heavyPunchBlastLeavesNoStandImmunity(GameTestHelper helper) {
		Scene scene = new Scene(helper, "star_platinum", 16, false);
		Cow fresh = scene.cow(1.6);
		Cow primed = scene.cow(-1.6);
		boolean[] primedHitWhileImmune = {false};
		Consumer<LivingIncomingDamageEvent> incoming = event -> {
			if (event.getEntity() == primed && event.getSource().getDirectEntity() == scene.stand
					&& StandEntity.isStandAttackInvulnerable(primed) && event.getAmount() > 0) {
				primedHitWhileImmune[0] = true;
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
		scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(incoming));
		scene.beforePress = () -> {
			// a punch that left primed in its 10-tick Stand-attack immunity
			DamageSource source = DamageUtil.make(scene.level(), rotp.core.init.ModDamageTypes.STAND_ATTACK, scene.stand);
			((DamageSourceModified) source).jojo_ripples$setStandInvulTicks(10);
			helper.assertTrue(scene.stand.hurtWithStandAttack(primed, source, 1) && StandEntity.isStandAttackInvulnerable(primed),
					"Fixture: could not leave the primed cow in Stand-attack immunity");
		};
		float[] health = new float[2];
		scene.afterPress = () -> { health[0] = fresh.getHealth(); health[1] = primed.getHealth(); };
		scene.run(s -> {
			helper.assertTrue(fresh.getHealth() < health[0], "The heavy punch blast did not hurt the cow beside the block");
			helper.assertTrue(!StandEntity.isStandAttackInvulnerable(fresh),
					"The heavy punch blast left its victim immune to Stand attacks; 1.16 hurtTarget set none");
			helper.assertTrue(primedHitWhileImmune[0] && primed.getHealth() < health[1],
					"The heavy punch blast skipped a cow still immune after a punch; 1.16 hurtTarget ignored that immunity");
		});
	}

	// 1.16 explosionDmgSource: the blast is the Stand's own source flagged as an explosion, so Blast Protection cuts it
	@GameTest(template = "empty", timeoutTicks = 80, batch = PROTECTION_BATCH)
	public static void heavyPunchBlastIsAStandExplosion(GameTestHelper helper) {
		Scene scene = new Scene(helper, "star_platinum", 16, false);
		Cow protectedCow = scene.cow(1.6);
		Cow bareCow = scene.cow(-1.6);
		Holder<Enchantment> blastProtection = scene.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.BLAST_PROTECTION);
		ItemStack enchanted = new ItemStack(Items.LEATHER_HELMET);
		enchanted.enchant(blastProtection, 4);
		protectedCow.setItemSlot(EquipmentSlot.HEAD, enchanted);
		bareCow.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		DamageSource[] blast = new DamageSource[1];
		Consumer<LivingIncomingDamageEvent> incoming = event -> {
			if (event.getEntity() == bareCow && event.getSource().getDirectEntity() == scene.stand) {
				blast[0] = event.getSource();
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, incoming);
		scene.cleanup.add(() -> NeoForge.EVENT_BUS.unregister(incoming));
		float[] health = new float[2];
		scene.afterPress = () -> { health[0] = protectedCow.getHealth(); health[1] = bareCow.getHealth(); };
		scene.run(s -> {
			helper.assertTrue(blast[0] != null && blast[0].is(DamageTypeTags.IS_EXPLOSION)
					&& blast[0] instanceof DamageSourceModified modified
					&& modified.jojo_ripples$standPower() == scene.power && modified.jojo_ripples$standInvulTicks() == 0,
					"1.16 hurt with the Stand's own source flagged as an explosion, without Stand invulnerability ticks: "
							+ (blast[0] != null ? blast[0].typeHolder().getRegisteredName() : null));
			float protectedLoss = health[0] - protectedCow.getHealth();
			float bareLoss = health[1] - bareCow.getHealth();
			helper.assertTrue(bareLoss > 0 && protectedLoss < bareLoss - 0.5F,
					"Blast Protection IV did not cut the heavy punch blast: " + protectedLoss + " against " + bareLoss);
		});
	}

	private static final class Scene {
		final GameTestHelper helper;
		final Player user;
		final StandPower power;
		final StandType type;
		final StandEntity stand;
		final BlockPos block;
		final boolean kick;
		final Vec3 origin;
		final List<Runnable> cleanup = new ArrayList<>();
		final List<Entity> entities = new ArrayList<>();
		final BlockState oldBlock;
		Runnable beforePress = () -> {};
		Runnable afterPress = () -> {};
		EntityActionInstance action;
		int hitSounds;
		int impactSounds;
		int ticks;
		boolean finished;

		Scene(GameTestHelper helper, String standId, double strength, boolean kick) {
			this(helper, standId, strength, kick, Blocks.OBSIDIAN.defaultBlockState());
		}

		Scene(GameTestHelper helper, String standId, double strength, boolean kick, BlockState struck) {
			this.helper = helper;
			this.kick = kick;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(level().addFreshEntity(user), "Could not add the heavy punch user");
			entities.add(user);
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			power.setResolveLevel(2);
			helper.assertTrue(type.summon(user, power), "Could not summon " + standId);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user, "Missing Stand entity");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z, 0, 0);
			stand.setYHeadRot(0);
			user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(strength);
			user.getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(28);
			user.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(8);
			user.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(8);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			helper.assertTrue(Math.abs(stand.getAttackDamage() - strength) < 1.0E-4,
					"Fixture: effective strength " + stand.getAttackDamage() + " instead of " + strength);
			user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standAttackTargetLock = true;
			block = BlockPos.containing(origin.add(0, 1, 2));
			oldBlock = level().getBlockState(block);
			level().setBlock(block, struck, Block.UPDATE_CLIENTS);
			Vec3 centre = Vec3.atCenterOf(block);
			Consumer<PlayLevelSoundEvent.AtPosition> sounds = event -> {
				if (event.getLevel() == level() && event.getSource() == SoundSource.BLOCKS
						&& event.getPosition().distanceToSqr(centre) < 0.01) {
					hitSounds++;
				}
				if (event.getLevel() == level() && event.getSound() != null
						&& event.getSound().value() == ModSoundEvents.STAR_PLATINUM_PUNCH_HEAVY.get()
						&& event.getPosition().distanceToSqr(centre) < 0.01) {
					impactSounds++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, sounds);
			cleanup.add(() -> NeoForge.EVENT_BUS.unregister(sounds));
		}

		Cow cow(double x) {
			Cow cow = EntityType.COW.create(level());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
			cow.setHealth(100);
			cow.moveTo(origin.x + x, origin.y, origin.z + 1.5, 0, 0);
			helper.assertTrue(level().addFreshEntity(cow), "Could not add a cow");
			entities.add(cow);
			return cow;
		}

		void run(Consumer<Scene> verify) {
			helper.onEachTick(() -> {
				if (finished) return;
				try {
					if (++ticks == 3) press();
					else if (action != null && action.punchedTarget != null) {
						helper.assertTrue(action.punchedTarget.getType() == ActionTarget.TargetType.BLOCK
								&& action.punchedTarget.getBlockPos().equals(block), "The heavy attack hit " + action.punchedTarget);
						verify.accept(this);
						finished = true;
						close();
						helper.succeed();
					}
					else if (ticks > 60) {
						helper.fail("The heavy attack never landed on the block");
					}
				}
				catch (RuntimeException | Error error) {
					finished = true;
					close();
					throw error;
				}
			});
		}

		void press() {
			if (kick) {
				var result = UNLOCKS.forceUnlock(power, "kick");
				helper.assertTrue(result == PowerSkillUnlocks.Result.UNLOCKED || result == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
						"Could not unlock the kick");
			}
			stand.setFinisherMeter(kick ? 0.75F : 0);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(block, Direction.NORTH));
			beforePress.run();
			Ability base = power.getAbility("heavy_punch");
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			helper.assertTrue(selected != null && AbilityInput.withConditionCheck(selected, user, InputMethod.CLICK),
					"The heavy attack was not admitted");
			BufferingState buffer = BufferingState.clickOnly();
			var held = AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.CLICK, 0, buffer, base.getAbilityId());
			helper.assertTrue(buffer.isActionSuccess, "The heavy attack key press failed");
			action = held != null && held.action instanceof EntityActionInstance a ? a : stand.getCurStandAction();
			helper.assertTrue(action instanceof StandEntityHeavyPunch && (action instanceof TheWorldKick) == kick,
					"Wrong heavy action " + action);
			afterPress.run();
		}

		ServerLevel level() {
			return helper.getLevel();
		}

		void close() {
			cleanup.forEach(Runnable::run);
			AbilityInput.keyRelease(KEY, user);
			if (power.isSummoned()) type.forceUnsummon(user, power);
			entities.forEach(Entity::discard);
			level().getEntitiesOfClass(ItemEntity.class, new AABB(block).inflate(4)).forEach(Entity::discard);
			level().setBlock(block, oldBlock, Block.UPDATE_CLIENTS);
		}
	}
}
