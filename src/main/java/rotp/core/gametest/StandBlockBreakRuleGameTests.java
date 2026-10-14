package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerBlockDestroyTracker;
import rotp.core.subsystems.target.ActionTarget;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.breakBlock, shared by every Stand block hit: a block within the strength rule (hardness, x0.6 when
 * no tool is needed, under e^(strength/4); harvest level under strength/4) breaks in one hit after the user's
 * playerWillDestroy, any other block only plays its hit sound. Silver Chariot's visible rapier has its own rule.
 * Own batch: the tests switch the shared block-breaking gamerule.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandBlockBreakRuleGameTests {
	private static final String BATCH = "stand_block_break_rule";
	private static final BlockPos BLOCK = new BlockPos(2, 3, 3);

	private StandBlockBreakRuleGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void lightPunchBreaksByTheDonorStrengthRule(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "star_platinum", "rule")) {
			f.expect(16, Blocks.OBSIDIAN, true);
			f.expect(16, Blocks.ANCIENT_DEBRIS, true);
			f.expect(16, Blocks.DEEPSLATE_DIAMOND_ORE, true);
			f.expect(8, Blocks.OBSIDIAN, false);
			f.expect(4, Blocks.IRON_ORE, false);
			f.expect(4, Blocks.STONE, true);
			// below strength 4 the 1.16 harvest level 0 of dirt, sand and wood is still too high
			f.expect(2, Blocks.DIRT, false);
			f.expect(2, Blocks.SAND, false);
			f.expect(2, Blocks.OAK_PLANKS, false);
			f.expect(2, Blocks.GLASS, true);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void silverChariotDashBlockHitKeepsTheRapierRule(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "silver_chariot", "rapier")) {
			helper.assertTrue(JojoMod.resLoc("silver_chariot").equals(f.stand.getStandType())
					&& f.stand.isSilverChariotRapierVisible(), "Fixture: Silver Chariot without a visible rapier");
			// the dash hits blocks through StandEntityPunch.hitBlockTarget, as 1.16 SilverChariotDashAttack did
			f.expect(15, Blocks.STONE, false);
			f.expect(15, Blocks.GLASS, true);
			f.expect(2, Blocks.NETHERRACK, true);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void creativeStandBreakKeepsShulkerBoxContents(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, "star_platinum", "shulker")) {
			helper.assertTrue(f.user.setGameMode(GameType.CREATIVE) && f.user.isCreative()
					&& f.user.getAbilities().instabuild, "Fixture: the user is not in Creative");
			f.strength(16);
			f.shulker();
			StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(f.target(), f.level(), f.stand, false);
			f.assertShulkerDropped("light punch");

			EntityActionInstance barrage = f.initAction("barrage");
			helper.assertTrue(barrage instanceof StandEntityBarrageAbility.StandEntityBarrage,
					"Star Platinum barrage resolved the wrong action: " + barrage);
			f.shulker();
			invoke(barrageHitBlock(), barrage, f.target(), f.level(), f.stand);
			f.assertShulkerDropped("barrage");
		}
		helper.succeed();
	}

	// A Stand whose own block rule replaces the strength rule (1.16 RHCP unpowered broke soft blocks whatever its strength)
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void aStandsOwnBlockRuleReplacesTheStrengthRule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BLOCK);
		BlockState old = level.getBlockState(pos);
		try {
			level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			BlockState dirt = level.getBlockState(pos);
			StandEntity plain = new StandEntity(ModEntityTypes.STAR_PLATINUM_STAND.get(), level);
			StandEntity ownRule = new OwnBlockRuleStand(ModEntityTypes.STAR_PLATINUM_STAND.get(), level);
			plain.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
			ownRule.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2);
			helper.assertTrue(plain.getAttackDamage() < 4 && ownRule.getAttackDamage() < 4,
					"Fixture: strength " + plain.getAttackDamage() + " passes the 1.16 harvest level of dirt");
			helper.assertTrue(!StandEntityPunchAbility.StandEntityPunch.canStandBreakBlockByStrength(plain, dirt, level, pos),
					"Fixture: a Stand under strength 4 broke dirt by the strength rule");
			helper.assertTrue(StandEntityPunchAbility.StandEntityPunch.canStandBreakBlockByStrength(ownRule, dirt, level, pos),
					"A Stand with its own block rule was still held to the strength rule");
		}
		finally {
			level.setBlockAndUpdate(pos, old);
		}
		helper.succeed();
	}

	private static final class OwnBlockRuleStand extends StandEntity {
		OwnBlockRuleStand(EntityType<? extends StandEntity> type, Level level) {
			super(type, level);
		}

		@Override
		public boolean replacesStandBreakStrengthRule(BlockState blockState, Level level, BlockPos blockPos) {
			return true;
		}
	}

	private static Method barrageHitBlock() {
		try {
			Method method = StandEntityBarrageAbility.StandEntityBarrage.class.getDeclaredMethod(
					"hitBlock", ActionTarget.class, Level.class, StandEntity.class);
			method.setAccessible(true);
			return method;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Barrage hitBlock is missing", e);
		}
	}

	private static void invoke(Method method, Object instance, Object... args) {
		try {
			method.invoke(instance, args);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Barrage hitBlock failed", e);
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final FakePlayer user;
		private final StandType standType;
		private final StandPower power;
		private final StandEntity stand;
		private final boolean breakBlocks;
		private final BlockPos pos;
		private final List<ItemEntity> drops = new ArrayList<>();
		private int hitSounds;
		private final Consumer<PlayLevelSoundEvent.AtPosition> listener;

		private Fixture(GameTestHelper helper, String standId, String name) {
			this.helper = helper;
			String profileName = "StandBlockRule_" + name;
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
					UUID.nameUUIDFromBytes(profileName.getBytes(StandardCharsets.US_ASCII)), profileName));
			user.setGameMode(GameType.SURVIVAL);
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(0, 2, 0)));
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add block-rule player");
			breakBlocks = helper.getLevel().getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			helper.getLevel().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, helper.getLevel().getServer());
			power = PowerClass.STAND.attachGet(user);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(standType != null, "Missing Stand type " + standId);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + standId);
			helper.assertTrue(standType.summon(user, power), "Could not summon " + standId);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Stand entity");
			stand.moveTo(origin.x, origin.y, origin.z);
			pos = helper.absolutePos(BLOCK);
			Vec3 centre = Vec3.atCenterOf(pos);
			listener = event -> {
				if (event.getLevel() == helper.getLevel() && event.getSound() != null
						&& event.getSource() == SoundSource.BLOCKS && event.getPosition().distanceToSqr(centre) < 0.01) {
					hitSounds++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		private void strength(double strength) {
			user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(strength);
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			helper.assertTrue(Math.abs(stand.getAttackDamage() - strength) < 1.0E-4,
					"Fixture: effective strength " + stand.getAttackDamage() + " instead of " + strength);
		}

		private void expect(double strength, Block block, boolean breaks) {
			strength(strength);
			level().setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
			hitSounds = 0;
			StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(target(), level(), stand, true);
			String what = block + " at strength " + strength;
			helper.assertTrue(level().getBlockState(pos).isAir() == breaks,
					what + (breaks ? " did not break in one hit" : " broke, 1.16 refused it"));
			helper.assertTrue(ServerBlockDestroyTracker.getBlockDestroyProgress(level(), pos) == 0,
					what + " got mining progress instead of the 1.16 one-hit rule");
			helper.assertTrue(hitSounds == (breaks ? 0 : 1),
					what + " played " + hitSounds + " block hit sounds; 1.16 plays one only when the block holds");
			clearBlock();
		}

		private void shulker() {
			level().setBlock(pos, Blocks.SHULKER_BOX.defaultBlockState(), Block.UPDATE_CLIENTS);
			helper.assertTrue(level().getBlockEntity(pos) instanceof ShulkerBoxBlockEntity,
					"Fixture: no shulker box block entity");
			((ShulkerBoxBlockEntity) level().getBlockEntity(pos)).setItem(0, new ItemStack(Items.DIAMOND, 3));
		}

		private void assertShulkerDropped(String what) {
			helper.assertTrue(level().getBlockState(pos).isAir(), what + " did not break the shulker box");
			List<ItemEntity> items = level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
			drops.addAll(items);
			boolean kept = items.stream().anyMatch(item -> item.getItem().is(Items.SHULKER_BOX)
					&& item.getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
							.nonEmptyStream().anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 3));
			helper.assertTrue(kept, "Creative " + what + " broke a filled shulker box without dropping it with its "
					+ "contents (playerWillDestroy was skipped); items=" + items.size());
			items.forEach(ItemEntity::discard);
			clearBlock();
		}

		private ActionTarget target() {
			return new ActionTarget(pos, Direction.UP);
		}

		private EntityActionInstance initAction(String abilityName) {
			Ability ability = power.getAbility(abilityName);
			helper.assertTrue(ability instanceof EntityActionType, "Missing production action " + abilityName);
			power.setStamina(power.getMaxStamina());
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level(), user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			return action;
		}

		private void clearBlock() {
			if (!level().getBlockState(pos).isAir()) {
				ServerBlockDestroyTracker.addBlockDestroyProgress(level(), null, pos, level().getBlockState(pos), -1.0F);
			}
			level().removeBlockEntity(pos);
			level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}

		private ServerLevel level() {
			return helper.getLevel();
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(listener);
			clearBlock();
			drops.forEach(ItemEntity::discard);
			level().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(breakBlocks, level().getServer());
			if (power.isSummoned()) standType.forceUnsummon(user, power);
			user.setGameMode(GameType.SURVIVAL);
			user.discard();
		}
	}
}
