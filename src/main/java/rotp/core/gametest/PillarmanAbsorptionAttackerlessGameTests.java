package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.pillarman.PillarmanData;
import rotp.core.impl.powers.pillarman.PillarmanMode;
import rotp.core.impl.powers.pillarman.abilities.PillarmanAbsorptionAbility;
import rotp.core.impl.powers.vampirism.abilities.VampirismBloodDrainAbility;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 PillarmanAbsorption.absorb builds an EntityDamageSource with the absorber only for HamonUtil.preventBlockDamage;
 * the damage itself is DamageUtil.dealPillarmanAbsorptionDamage(target, amount, null), the plain pillarManAbsorption
 * source with no attacker. So the drain gives no kill credit and no retaliation target, a shield does not stop it, and
 * it does not count as an attack on the victim's held actions. Each test holds the registered Absorption on a victim.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PillarmanAbsorptionAttackerlessGameTests {
	private static final String BATCH = "pillarman_absorption_attackerless";
	private static final double SEPARATION = 1.5D;

	private PillarmanAbsorptionAttackerlessGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void absorptionKillGivesNoAttackerNoKillCreditAndNoExperience(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper)) {
			Cow cow = fixture.cow(SEPARATION);
			cow.setHealth(1.0F);
			helper.assertTrue(fixture.level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT) && !cow.isBaby(),
					"ABSORPTION premise: the cow would drop no experience for any killer");
			fixture.holdOn(cow);
			fixture.tick();
			List<ExperienceOrb> orbs = fixture.level.getEntitiesOfClass(ExperienceOrb.class, fixture.space);
			helper.assertTrue(fixture.hits.size() == 1 && cow.isDeadOrDying(),
					"ABSORPTION premise: one held tick did not kill the cow with one hit: hits=" + fixture.describeHits()
							+ " health=" + cow.getHealth());
			DamageSource source = fixture.hits.get(0);
			helper.assertTrue(source.is(ModDamageTypes.PILLAR_MAN_ABSORPTION) && source.getEntity() == null
					&& source.getDirectEntity() == null && source.getSourcePosition() == null
					&& cow.getLastHurtByMob() == null && cow.getKillCredit() == null && orbs.isEmpty(),
					"1.16 deals pillarManAbsorption with no attacker, so the kill has no killer and drops no experience: hit="
							+ fixture.describeHits() + " lastHurtByMob=" + cow.getLastHurtByMob() + " killCredit="
							+ cow.getKillCredit() + " experienceOrbs=" + orbs.size());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void absorptionIsNotStoppedByARaisedShield(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper)) {
			Victim victim = fixture.victim(SEPARATION, 180.0F);
			victim.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			fixture.holdOn(victim);
			victim.raiseShield(helper);
			Vec3 toAbsorber = fixture.user.position().subtract(victim.position()).normalize();
			helper.assertTrue(victim.getViewVector(1.0F).dot(toAbsorber) > 0.9D,
					"ABSORPTION premise: the victim does not face the absorber with its shield");
			float health = victim.getHealth();
			float energy = fixture.data.getEnergy();
			fixture.tick();
			helper.assertTrue(health - victim.getHealth() == 2.0F && fixture.hits.size() == 1
					&& fixture.data.getEnergy() > energy,
					"1.16 pillarManAbsorption has no attacker and no position, so a raised shield does not block it: health="
							+ health + "->" + victim.getHealth() + " hits=" + fixture.describeHits() + " absorberEnergy="
							+ energy + "->" + fixture.data.getEnergy());
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void absorptionDoesNotStopTheVictimsHeldAction(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper)) {
			Victim victim = fixture.victim(SEPARATION, 0.0F);
			PlayerPower victimPower = PowerClass.PLAYER_POWER.attachGet(victim);
			victimPower.setPowerType(ModPlayerPowers.VAMPIRISM.get());
			Ability drain = victimPower.getAbility("vampirism_blood_drain");
			Cow prey = fixture.cow(SEPARATION * 2.0D);
			LivingComponentAction victimComponent = LivingComponentAction.getComponent(victim);
			victimComponent.entityAim.setTarget(new ActionTarget(prey));
			helper.assertTrue(drain instanceof VampirismBloodDrainAbility && drain.checkConditions(victimPower).isPositive(),
					"ABSORPTION premise: the victim's Blood Drain is not usable on its prey");
			HeldInputEntry victimHeld = AbilityInput.keyPress(Fixture.VICTIM_KEY, drain, victim, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), drain.getAbilityId());
			fixture.beforeDiscard = () -> {
				AbilityInput.keyRelease(Fixture.VICTIM_KEY, victim);
				victimComponent.entityAim.setTarget(ActionTarget.EMPTY);
				victimComponent.setAction(null, victim, SyncType.NO_SYNC);
			};
			EntityActionInstance victimAction = victimComponent.getAction();
			helper.assertTrue(victimHeld != null && victimAction != null && victimHeld.action == victimAction
					&& victimAction.getPhase() == ActionPhase.PERFORM, "ABSORPTION premise: the victim's hold did not start");

			fixture.holdOn(victim);
			float health = victim.getHealth();
			fixture.tick();
			helper.assertTrue(victim.getHealth() < health && fixture.hits.size() == 1,
					"ABSORPTION premise: one held tick did not drain the victim once: health=" + health + "->"
							+ victim.getHealth() + " hits=" + fixture.describeHits());
			helper.assertTrue(victimComponent.getAction() == victimAction && !victimAction.isOver()
					&& AbilityInput.isHeldByKey(victim, victimAction),
					"1.16 pillarManAbsorption has no attacker, so it does not stop the victim's held Blood Drain: hit="
							+ fixture.describeHits() + " holdOver=" + victimAction.isOver() + " current="
							+ (victimComponent.getAction() == victimAction));
		}
		helper.succeed();
	}

	// 1.16 HamonUtil.preventBlockDamage still gets the absorber: the split must not lose the Hamon check
	@GameTest(template = "empty", batch = BATCH)
	public static void hamonUserStillPreventsTheAbsorptionWithEnergy(GameTestHelper helper) {
		try (Fixture fixture = Fixture.open(helper)) {
			Victim victim = fixture.victim(SEPARATION, 180.0F);
			PlayerPower victimPower = PowerClass.PLAYER_POWER.attachGet(victim);
			victimPower.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(victim, ModPlayerPowers.HAMON).orElseThrow();
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			fixture.holdOn(victim);
			float hamonEnergy = hamon.getEnergy();
			float health = victim.getHealth();
			float energy = fixture.data.getEnergy();
			helper.assertTrue(hamonEnergy >= 1.0F, "ABSORPTION premise: the Hamon user has no energy: " + hamonEnergy);
			fixture.tick();
			helper.assertTrue(victim.getHealth() == health && fixture.hits.isEmpty()
					&& Math.abs(hamonEnergy - hamon.getEnergy() - 1.0F) < 1.0E-4F && fixture.data.getEnergy() <= energy,
					"1.16 a Hamon user pays half the 2 damage in energy and takes no absorption: health=" + health + "->"
							+ victim.getHealth() + " hits=" + fixture.describeHits() + " hamonEnergy=" + hamonEnergy + "->"
							+ hamon.getEnergy() + " absorberEnergy=" + energy + "->" + fixture.data.getEnergy());
		}
		helper.succeed();
	}

	/** GameTestPlayers' damageable survival mock, able to raise its shield within the test's own tick. */
	private static final class Victim extends Player {
		Victim(Level level) {
			super(level, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "test-mock-player"));
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isCreative() {
			return false;
		}

		// 8 item use steps as LivingEntity.tick runs them; vanilla isBlocking needs 5
		void raiseShield(GameTestHelper helper) {
			startUsingItem(InteractionHand.OFF_HAND);
			for (int i = 0; i < 8 && isUsingItem(); i++) {
				updateUsingItem(getUseItem());
			}
			helper.assertTrue(isBlocking(), "ABSORPTION premise: the victim's shield is not raised");
		}
	}

	private static final class Fixture implements AutoCloseable {
		private static final short KEY = 73;
		private static final short VICTIM_KEY = 74;
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<Entity> entities = new ArrayList<>();
		private final List<DamageSource> hits = new ArrayList<>();
		private Consumer<LivingDamageEvent.Post> damageListener;
		private Runnable beforeDiscard = () -> {};
		private Vec3 origin;
		private AABB space;
		private Player user;
		private PlayerPower power;
		private PillarmanData data;
		private Ability ability;
		private LivingComponentAction component;
		private LivingEntity target;
		private boolean held;

		private Fixture(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private static Fixture open(GameTestHelper helper) {
			Fixture fixture = new Fixture(helper);
			try {
				fixture.setUp();
				return fixture;
			}
			catch (RuntimeException | Error error) {
				fixture.close();
				throw error;
			}
		}

		private void setUp() {
			helper.assertTrue(level.getDifficulty() == Difficulty.NORMAL, "ABSORPTION premise: requires the Normal difficulty world");
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			origin = new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 6.5D);
			space = new AABB(origin.x - 3, origin.y - 2, origin.z - 3, origin.x + 3, origin.y + 4, origin.z + 6);
			helper.assertTrue(level.getEntities((Entity) null, space).isEmpty(), "ABSORPTION premise: the scene is not empty");
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "ABSORPTION premise: could not add the absorber");
			entities.add(user);
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.PILLAR_MAN.get());
			data = PlayerPower.getPowerData(user, ModPlayerPowers.PILLAR_MAN).orElseThrow();
			data.setEvolutionStage(2, user);
			data.setMode(PillarmanMode.NONE, user);
			data.setEnergy(user, 10.0F);
			ability = power.getAbility("pillarman_absorption");
			component = LivingComponentAction.getComponent(user);
			helper.assertTrue(ability instanceof PillarmanAbsorptionAbility && user.getMainHandItem().isEmpty(),
					"ABSORPTION premise: the registered pillarman_absorption is missing");
			damageListener = event -> {
				if (event.getEntity() == target) {
					hits.add(event.getSource());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingDamageEvent.Post.class, damageListener);
		}

		private Cow cow(double separation) {
			Cow cow = EntityType.COW.create(level);
			helper.assertTrue(cow != null, "ABSORPTION premise: could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + separation, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(cow), "ABSORPTION premise: could not add a cow");
			entities.add(cow);
			return cow;
		}

		private Victim victim(double separation, float yaw) {
			Victim victim = new Victim(level);
			victim.setNoGravity(true);
			victim.moveTo(origin.x, origin.y, origin.z + separation, yaw, 0.0F);
			victim.setYHeadRot(yaw);
			helper.assertTrue(level.addFreshEntity(victim), "ABSORPTION premise: could not add the victim");
			entities.add(victim);
			return victim;
		}

		// the registered HOLD on the victim, through the server side of the key press; no damage before the first tick
		private void holdOn(LivingEntity victim) {
			target = victim;
			float health = victim.getHealth();
			component.entityAim.setTarget(new ActionTarget(victim));
			AvailableAbilities available = new AvailableAbilities();
			available.update(power, power.getMoveset());
			helper.assertTrue(AbilityInput.withConditionCheck(available.getContextVariationContainer(ability), user, InputMethod.HOLD),
					"ABSORPTION premise: the registered HOLD on the victim was not admitted");
			HeldInputEntry entry = AbilityInput.keyPress(KEY, ability, user, null, InputMethod.HOLD,
					0.0F, BufferingState.clickOnly(), ability.getAbilityId());
			held = true;
			EntityActionInstance action = component.getAction();
			helper.assertTrue(entry != null && action != null && entry.action == action
					&& action.getPhase() == ActionPhase.PERFORM && victim.getHealth() == health && hits.isEmpty(),
					"ABSORPTION premise: the registered HOLD did not install an undamaging PERFORM hold");
		}

		private void tick() {
			component.tick();
		}

		private String describeHits() {
			return hits.stream().map(hit -> "[" + hit.typeHolder().getRegisteredName() + " attacker=" + hit.getEntity()
					+ " direct=" + hit.getDirectEntity() + " position=" + hit.getSourcePosition() + "]").toList().toString();
		}

		@Override
		public void close() {
			if (damageListener != null) NeoForge.EVENT_BUS.unregister(damageListener);
			try {
				beforeDiscard.run();
				if (user != null) {
					if (held) AbilityInput.keyRelease(KEY, user);
					if (component != null) {
						component.entityAim.setTarget(ActionTarget.EMPTY);
						component.setAction(null, user, SyncType.NO_SYNC);
					}
				}
			}
			finally {
				entities.forEach(Entity::discard);
				if (space != null) {
					level.getEntitiesOfClass(Entity.class, space, entity -> entity instanceof ItemEntity
							|| entity instanceof ExperienceOrb).forEach(Entity::discard);
				}
			}
		}
	}
}
