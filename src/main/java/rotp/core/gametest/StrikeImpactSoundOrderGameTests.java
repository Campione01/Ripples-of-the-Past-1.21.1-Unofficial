package rotp.core.gametest;

import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.BlockShardEntity;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility.StandEntityPunch;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.attackTarget played a strike's impact sound after punchInstance.doHit, and a block punch
 * (StandBlockPunch.playImpactSound) sounded only for a block of some hardness, read from the block it was given before the
 * hit. So for the plain punch and, following the heavy punch, the port's charged heavy punch: the sound comes once the
 * block is broken or the entity hurt. Own batch: the block-breaking gamerule.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StrikeImpactSoundOrderGameTests {
	private static final String BATCH = "strike_impact_sound_order";
	private static Boolean oldBreakBlocks;

	private StrikeImpactSoundOrderGameTests() {}

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

	// glass (hardness 0.3) sounds once it is broken; a slime block (hardness 0) does not sound
	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aPunchSoundsItsBlockImpactAfterTheBreakAndOnlyForABlockOfSomeHardnessAsIn116(GameTestHelper helper) {
		for (BlockState struck : new BlockState[] { Blocks.GLASS.defaultBlockState(), Blocks.SLIME_BLOCK.defaultBlockState() }) {
			Scene scene = new Scene(helper, ModSoundEvents.GOLD_EXPERIENCE_PUNCH_LIGHT);
			try {
				ActionTarget target = scene.block(struck);
				StandEntityPunch punch = new StandEntityPunch((EntityActionType) scene.power.getAbility("punch")) {
					@Override
					protected ActionTarget getPunchTarget(StandEntity stand) {
						return target;
					}
				};
				scene.strike(punch);
				punch.actionPerformStart();
				boolean hard = struck.is(Blocks.GLASS);
				helper.assertTrue(!hard || scene.level().getBlockState(scene.pos).isAir(), "setup: the punch left the glass standing");
				helper.assertTrue(scene.sounds == (hard ? 1 : 0) && !scene.stoodAtSound, "1.16 played a punch's impact"
						+ " sound " + (hard ? "once, after it broke the glass" : "not at all for a slime block of no hardness")
						+ "; it played " + scene.sounds + (scene.sounds > 0 ? ", with the block " + (scene.stoodAtSound
								? "still standing" : "broken") : ""));
			}
			finally {
				scene.close();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aChargedHeavyPunchSoundsItsImpactAfterItsHitAsTheHeavyPunch(GameTestHelper helper) {
		for (boolean atBlock : new boolean[] { true, false }) {
			Scene scene = new Scene(helper, ModSoundEvents.STAND_PUNCH_HEAVY_CHARGED);
			try {
				ActionTarget target;
				if (atBlock) {
					target = scene.block(Blocks.GLASS.defaultBlockState());
				}
				else {
					Cow cow = EntityType.COW.create(scene.level());
					helper.assertTrue(cow != null, "setup: could not create a cow");
					cow.setNoAi(true);
					cow.setNoGravity(true);
					cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
					cow.setHealth(100.0F);
					cow.moveTo(scene.origin.x, scene.origin.y, scene.origin.z + 2.0D, 0.0F, 0.0F);
					helper.assertTrue(scene.level().addFreshEntity(cow), "setup: could not add a cow");
					scene.cow = cow;
					target = new ActionTarget(cow);
				}
				StandEntityChargedHeavy heavy = new StandEntityChargedHeavy((EntityActionType) scene.power.getAbility("heavy_charged")) {
					@Override
					protected ActionTarget getPerformTarget(StandEntity stand) {
						return target;
					}
				};
				scene.strike(heavy);
				heavy.actionPerformEnd();
				String what = atBlock ? "the glass" : "a cow";
				helper.assertTrue(atBlock ? scene.level().getBlockState(scene.pos).isAir() : scene.cow.getHealth() < 100.0F,
						"setup: the charged heavy punch did not break or hurt " + what);
				helper.assertTrue(scene.sounds == 1 && !scene.stoodAtSound, "The charged heavy punch played its impact sound"
						+ " once, after it " + (atBlock ? "broke the glass" : "hurt the cow") + ", as the heavy punch does (1.16"
						+ " StandEntity.attackTarget); it played " + scene.sounds + (scene.sounds > 0 ? ", with " + what + " "
								+ (scene.stoodAtSound ? (atBlock ? "still standing" : "not yet hurt") : "already "
										+ (atBlock ? "broken" : "hurt")) : ""));
			}
			finally {
				scene.close();
			}
		}
		helper.succeed();
	}

	private static final class Scene {
		final GameTestHelper helper;
		final Holder<SoundEvent> sound;
		final Vec3 origin;
		final Player user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		final BlockPos pos;
		final BlockState oldBlock;
		final Consumer<PlayLevelSoundEvent.AtPosition> listener;
		Cow cow;
		int sounds;
		// whether the block still stood, or the cow was not yet hurt, as the sound played
		boolean stoodAtSound;

		Scene(GameTestHelper helper, Holder<SoundEvent> sound) {
			this.helper = helper;
			this.sound = sound;
			origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level().addFreshEntity(user), "setup: could not add the user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "setup: could not give Gold Experience");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "setup: the Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.setNoGravity(true);
			// strong enough for the charged heavy punch's blast to break the glass it struck
			user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(16.0D);
			StandEntity.updateStandStatAttributes(stand, user);
			stand.updateStrengthMultipliers();
			power.setStamina(power.getMaxStamina());
			pos = BlockPos.containing(origin.add(0.0D, 1.0D, 2.0D));
			oldBlock = level().getBlockState(pos);
			listener = event -> {
				if (event.getLevel() == level() && event.getSound() != null && event.getSound().value() == sound.value()) {
					sounds++;
					stoodAtSound = cow != null ? cow.getHealth() >= 100.0F : !level().getBlockState(pos).isAir();
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		ActionTarget block(BlockState struck) {
			level().setBlock(pos, struck, Block.UPDATE_CLIENTS);
			return new ActionTarget(pos, Direction.NORTH);
		}

		void strike(EntityActionInstance action) {
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			helper.assertTrue(stand.getCurStandAction() == action, "setup: the strike is not the Stand's action");
		}

		ServerLevel level() {
			return helper.getLevel();
		}

		void close() {
			NeoForge.EVENT_BUS.unregister(listener);
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			if (cow != null) {
				cow.discard();
			}
			user.discard();
			level().getEntitiesOfClass(Entity.class, new AABB(pos).inflate(8),
					entity -> entity instanceof ItemEntity || entity instanceof BlockShardEntity).forEach(Entity::discard);
			level().setBlock(pos, oldBlock, Block.UPDATE_CLIENTS);
		}
	}
}
