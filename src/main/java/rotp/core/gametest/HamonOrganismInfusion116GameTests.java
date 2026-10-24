package rotp.core.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.abilities.HamonOrganismInfusionAbility;
import rotp.core.impl.powers.hamon.entity.HamonBlockChargeEntity;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 HamonOrganismInfusion.overrideVanillaMouseTarget (:108-126; TargetRequirement.ANY, run on either side by
 * PowerBaseImpl.checkTarget :290-293): a pressed block's outline gave way to the first animal or ambient mob inside it, not
 * the user and without a Hamon charge, which the click then infused (the entity's energy, HamonUtil.isLiving); with none
 * the block stayed and was infused as a plant. Plant Infusion (TargetRequirement.BLOCK) had no such override. A vanilla
 * pick meets a small animal standing in short grass only behind the grass's outline, so the press carries the grass; the
 * scenes set the user's aim as the press packet stores it and press through the server's slot resolution. Own batches.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonOrganismInfusion116GameTests {
	private static final String BATCH = "hamon_organism_infusion_116_";
	private static final short KEY = 37;
	private static final String ORGANISM = "organism_infusion";
	private static final String PLANT = "plant_infusion";
	private static final float ENTITY_COST = 1000.0F;
	private static final float PLANT_COST = 200.0F;
	private static final float EPS = 0.01F;

	private HamonOrganismInfusion116GameTests() {}

	/*
	 * A chicken standing inside short grass, the grass on the aim: 1.16 infused the chicken for the entity's energy and
	 * charged no block.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "chicken")
	public static void chickenInsideShortGrassTakesTheChargeAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.shortGrass();
			Mob chicken = scene.mob(EntityType.CHICKEN, grass);
			scene.aimAt(grass);
			float spent = scene.press(ORGANISM);
			helper.assertTrue(EntityHamonChargeState.get(chicken).hasHamonCharge(), "1.16's Organism Infusion pressed at short"
					+ " grass with a chicken inside it infused the chicken; the port's left it uncharged, block charges: "
					+ scene.blockCharges(grass));
			helper.assertTrue(scene.blockCharges(grass).isEmpty(), "1.16 charged no block when the chicken inside the grass took"
					+ " the infusion; the port charged " + scene.blockCharges(grass));
			helper.assertTrue(Math.abs(spent - ENTITY_COST) < EPS, "1.16 spent the entity's " + ENTITY_COST + " energy on the"
					+ " chicken; the port spent " + spent);
		}
		helper.succeed();
	}

	/*
	 * The same chicken already holding a Hamon charge: the outline gave way to nothing, and the grass was charged as a
	 * plant for the plant's energy.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "charged_chicken")
	public static void aChargedChickenLeavesTheGrassChargedAsAPlantAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.shortGrass();
			Mob chicken = scene.mob(EntityType.CHICKEN, grass);
			EntityHamonChargeState.get(chicken).setHamonCharge(1.0F, 200, null, 0.0F);
			scene.aimAt(grass);
			float spent = scene.press(ORGANISM);
			helper.assertTrue(scene.blockCharges(grass).size() == 1, "1.16 charged the grass as a plant when the chicken inside"
					+ " it already held a Hamon charge; the port's block charges there: " + scene.blockCharges(grass));
			helper.assertTrue(Math.abs(spent - PLANT_COST) < EPS, "1.16 spent the plant's " + PLANT_COST + " energy on the"
					+ " grass; the port spent " + spent);
		}
		helper.succeed();
	}

	/*
	 * A skeleton horse (an animal, not living for Hamon) inside the grass: the outline gave way to it, and checkTarget
	 * refused it with living_mob, so the press did nothing to the grass either.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "undead")
	public static void anUndeadAnimalInsideTheGrassIsRefusedAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.shortGrass();
			scene.mob(EntityType.SKELETON_HORSE, grass);
			scene.aimAt(grass);
			String refusal = scene.refusal(ORGANISM);
			helper.assertTrue("living_mob".equals(refusal), "1.16's Organism Infusion pressed at short grass with a skeleton"
					+ " horse inside it took the horse and refused it with living_mob; the port answered " + refusal);
			scene.press(ORGANISM);
			helper.assertTrue(scene.blockCharges(grass).isEmpty(), "1.16 left the grass uncharged; the port charged "
					+ scene.blockCharges(grass));
		}
		helper.succeed();
	}

	/*
	 * Which mobs inside the grass took the press: an ambient mob (a bat) too, a villager (neither an animal nor an ambient
	 * mob) not.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "kinds")
	public static void onlyAnimalsAndAmbientMobsTakeThePressAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.shortGrass();
			ActionTarget pressed = new ActionTarget(grass, Direction.NORTH);
			Mob villager = scene.mob(EntityType.VILLAGER, grass);
			ActionTarget target = HamonOrganismInfusionAbility.organismInfusionTarget(scene.user, scene.level, pressed);
			helper.assertTrue(target.getType() == ActionTarget.TargetType.BLOCK && grass.equals(target.getBlockPos()),
					"1.16 kept the grass with a villager inside it; the port took " + target.getType() + " " + target.getEntity());
			villager.discard();
			Mob bat = scene.mob(EntityType.BAT, grass);
			target = HamonOrganismInfusionAbility.organismInfusionTarget(scene.user, scene.level, pressed);
			helper.assertTrue(target.getType() == ActionTarget.TargetType.ENTITY && target.getEntity() == bat, "1.16 took the"
					+ " bat (an ambient mob) inside the grass; the port took " + target.getType() + " " + target.getEntity());
		}
		helper.succeed();
	}

	/*
	 * Plant Infusion (TargetRequirement.BLOCK) at the same grass with an uncharged chicken inside: 1.16 charged the grass
	 * and not the chicken.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "plant")
	public static void plantInfusionStillChargesTheGrassAsIn116(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos grass = scene.shortGrass();
			Mob chicken = scene.mob(EntityType.CHICKEN, grass);
			scene.aimAt(grass);
			float spent = scene.press(PLANT);
			helper.assertTrue(scene.blockCharges(grass).size() == 1 && !EntityHamonChargeState.get(chicken).hasHamonCharge(),
					"1.16's Plant Infusion charged the grass and not the chicken inside it; the port's block charges there: "
							+ scene.blockCharges(grass) + ", chicken charged "
							+ EntityHamonChargeState.get(chicken).hasHamonCharge());
			helper.assertTrue(Math.abs(spent - PLANT_COST) < EPS, "Plant Infusion spent " + spent + ", not " + PLANT_COST);
		}
		helper.succeed();
	}

	/*
	 * A block without an outline (a light block) gives no target, which 1.16 refused silently; the owner kept the port's
	 * refusal messages, so the port still answers living_plant there.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH + "no_outline")
	public static void aBlockWithoutAnOutlineKeepsThePortsRefusalMessage(GameTestHelper helper) {
		try (Scene scene = new Scene(helper)) {
			BlockPos light = scene.feet.south(2);
			scene.block(light, Blocks.LIGHT.defaultBlockState());
			helper.assertTrue(scene.level.getBlockState(light).getShape(scene.level, light).isEmpty(),
					"Fixture: the light block has an outline");
			scene.aimAt(light);
			ActionTarget target = HamonOrganismInfusionAbility.organismInfusionTarget(scene.user, scene.level,
					new ActionTarget(light, Direction.NORTH));
			helper.assertTrue(target.getType() == ActionTarget.TargetType.EMPTY, "1.16's override gave a block without an"
					+ " outline no target; the port's gave " + target.getType());
			String refusal = scene.refusal(ORGANISM);
			helper.assertTrue("living_plant".equals(refusal), "Organism Infusion pressed at a light block answered " + refusal
					+ "; the port's refusal message living_plant is kept");
		}
		helper.succeed();
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final ServerLevel level;
		final Player user;
		final PlayerPower power;
		final HamonData hamon;
		final LivingComponentAction component;
		final BlockPos feet;
		final List<Entity> entities = new ArrayList<>();
		final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

		// a Survival Hamon user who learned Plant and Animal Infusion (not Hamon Spread), floating, looking south
		Scene(GameTestHelper helper) {
			this.helper = helper;
			level = helper.getLevel();
			feet = helper.absolutePos(new BlockPos(1, 3, 1));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atBottomCenterOf(feet);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			GameType.SURVIVAL.updatePlayerAbilities(user.getAbilities());
			user.setNoGravity(true);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Hamon user");
			entities.add(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			component = LivingComponentAction.getComponent(user);
			hamon.learnSkill(ModHamonSkills.PLANT_BLOCK_INFUSION.get());
			hamon.learnSkill(ModHamonSkills.ANIMAL_INFUSION.get());
			helper.assertTrue(!hamon.isSkillLearned(ModHamonSkills.HAMON_SPREAD.get()) && ability(ORGANISM).isAbilityAvailable(power)
					&& ability(PLANT).isAbilityAvailable(power), "Fixture: Organism or Plant Infusion is not available");
		}

		Ability ability(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found != null, "Fixture: Hamon has no " + name);
			return found;
		}

		// short grass two blocks south of the user's feet on a grass block, kept up without neighbour updates
		BlockPos shortGrass() {
			BlockPos grass = feet.south(2);
			block(grass.below(), Blocks.GRASS_BLOCK.defaultBlockState());
			block(grass, Blocks.SHORT_GRASS.defaultBlockState());
			helper.assertTrue(!level.getBlockState(grass).getShape(level, grass).isEmpty()
					&& level.getBlockState(grass).getCollisionShape(level, grass).isEmpty(), "Fixture: the short grass");
			return grass;
		}

		// a mob standing in the block, its box inside the block's outline
		Mob mob(EntityType<? extends Mob> type, BlockPos at) {
			Mob mob = type.create(level);
			helper.assertTrue(mob != null, "Could not create " + type);
			mob.setNoAi(true);
			mob.setNoGravity(true);
			Vec3 pos = Vec3.atBottomCenterOf(at);
			mob.moveTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(mob), "Could not add " + mob);
			entities.add(mob);
			AABB outline = level.getBlockState(at).getShape(level, at).bounds().move(at);
			helper.assertTrue(mob.getBoundingBox().intersects(outline), "Fixture: " + mob + " is not inside " + at);
			return mob;
		}

		// the user's aim as the press packet stores it: the block the client's vanilla pick met
		void aimAt(BlockPos pos) {
			component.entityAim.setTarget(new ActionTarget(pos, Direction.NORTH));
		}

		AbilityConditionCheck resolve(String slot) {
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			AbilityConditionCheck resolved = available.getContextVariationContainer(slot);
			helper.assertTrue(resolved != null, "Fixture: " + slot + " is not in the moveset");
			return resolved;
		}

		// the message key the slot refuses a press with; "none" for a silent refusal, "accepted" otherwise
		String refusal(String slot) {
			AbilityConditionCheck resolved = resolve(slot);
			if (resolved.conditionCheck.isPositive()) {
				return "accepted";
			}
			if (resolved.conditionCheck.getWarning() == null) {
				return "none";
			}
			return resolved.conditionCheck.getWarning().getContents() instanceof TranslatableContents contents
					? contents.getKey().substring(contents.getKey().lastIndexOf('.') + 1)
					: resolved.conditionCheck.getWarning().getString();
		}

		// as ClAbilityInputPacket handles a click: the slot as the server resolves it, its check, the press and its
		// release, then the action to its end; the Hamon energy it spent
		float press(String slot) {
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			float before = hamon.getEnergy();
			helper.assertTrue(before >= ENTITY_COST, "Fixture: " + before + " Hamon energy");
			AbilityConditionCheck resolved = resolve(slot);
			if (AbilityInput.withConditionCheck(resolved, user, InputMethod.CLICK)) {
				AbilityInput.keyPress(KEY, resolved.ability, user, null, InputMethod.CLICK, 0.0F,
						BufferingState.clickCanBuffer(), ability(slot).getAbilityId());
				AbilityInput.keyRelease(KEY, user);
				for (int i = 0; i < 20 && component.getAction() != null; i++) {
					user.tickCount++;
					component.tick();
				}
				helper.assertTrue(component.getAction() == null, "Fixture: " + slot + " did not end within 20 ticks");
			}
			return before - hamon.getEnergy();
		}

		List<HamonBlockChargeEntity> blockCharges(BlockPos pos) {
			return level.getEntitiesOfClass(HamonBlockChargeEntity.class, new AABB(pos).inflate(0.5D), Entity::isAlive);
		}

		void block(BlockPos pos, BlockState state) {
			blocks.putIfAbsent(pos, level.getBlockState(pos));
			level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			component.entityAim.setTarget(ActionTarget.EMPTY);
			for (BlockPos pos : blocks.keySet()) {
				blockCharges(pos).forEach(Entity::discard);
			}
			entities.forEach(Entity::discard);
			List<BlockPos> placed = new ArrayList<>(blocks.keySet());
			for (int i = placed.size() - 1; i >= 0; i--) {
				level.setBlock(placed.get(i), blocks.get(placed.get(i)), Block.UPDATE_CLIENTS);
			}
		}
	}
}
