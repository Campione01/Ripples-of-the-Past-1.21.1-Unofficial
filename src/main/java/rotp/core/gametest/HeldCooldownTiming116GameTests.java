package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.magiciansred.MRCrossfireHurricaneEntity;
import rotp.core.impl.stands.magiciansred.MRFlameEntity;
import rotp.core.impl.stands.magiciansred.MagiciansRedCrossfireHurricaneAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFireballAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility;
import rotp.core.impl.stands.magiciansred.MagiciansRedFlameBurstAbility.FlameBurstShot;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl set no cooldown at the click of a held action (setHeldAction): a hold-to-fire action set it only
 * as it fired (stopHeldAction(true) -> performAction -> setCooldownOnUse), and a hold-only action as its hold ended
 * (stopHeldAction, getCooldown with the ticks it was held), so a charge let go early, dropped by a failed check or ended
 * by a cooldown set from elsewhere set none. A click action set it as it performed, at its click. The core's held Stand
 * abilities with a cooldown already set their own (Inhale, the time stop), so the tests give Magician's Red's Crossfire
 * Hurricane (a charge) and Flame Burst (a held perform) a cooldown of their own: fresh ability instances for the press,
 * the registered Crossfire Hurricane for the buffered press the input buffer replays from the moveset.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeldCooldownTiming116GameTests {
	private static final short KEY = 23;
	private static final int COOLDOWN = 100;
	private static final String CROSSFIRE = "crossfire_hurricane";
	private static final String FLAME_BURST = "flame_burst";

	private HeldCooldownTiming116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aChargeTakesItsCooldownOnlyAsItFiresAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "let go early", "fired at its full length", "fired on its release after a full charge",
				"dropped by a failed check", "ended by a cooldown from elsewhere" }) {
			try (Scene s = new Scene(helper, "HeldCooldownCharge")) {
				Ability registered = s.registered(CROSSFIRE);
				EntityActionAbility crossfire = s.withCooldown(row.equals("fired on its release after a full charge")
						? new MagiciansRedCrossfireHurricaneAbility(registered.abilityType, registered.abilityId) {
							// 1.16 holdToFire(n, true): the full charge waits for the release, which fires it
							@Override
							public EntityActionInstance createActionObj() {
								return new MagiciansRedCrossfireHurricaneAbility.CrossfireShot(this) {
									@Override
									protected boolean shouldHoldPhaseAtEnd() {
										return getPhase() == ActionPhase.BUTTON_CHARGE;
									}

									@Override
									public void onButtonStopHold() {
										if (getPhase() == ActionPhase.BUTTON_CHARGE && getPhaseTick() >= getCurPhaseLength()) {
											setPhaseStart(ActionPhase.PERFORM);
										}
										else {
											super.onButtonStopHold();
										}
									}
								};
							}
						}
						: new MagiciansRedCrossfireHurricaneAbility(registered.abilityType, registered.abilityId));
				EntityActionInstance charge = s.press(crossfire, BufferingState.clickOnly());
				helper.assertTrue(charge != null && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
						"setup (" + row + "): the Crossfire Hurricane press did not start its charge: " + charge);
				helper.assertTrue(!s.power.isAbilityOnCooldown(CROSSFIRE) && charge.isCooldownTakenAfterHold(),
						"1.16 set no cooldown at the press of a hold-to-fire action (" + row + "): cooldown "
								+ s.power.getAbilityCooldown(CROSSFIRE));
				s.tick(3);
				helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE && !s.power.isAbilityOnCooldown(CROSSFIRE),
						"setup (" + row + "): the charge is not running without a cooldown: phase " + charge.getPhase()
								+ ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
				switch (row) {
					case "let go early" -> {
						s.release();
						s.tick(1);
						helper.assertTrue(charge.isOver() && !s.power.isAbilityOnCooldown(CROSSFIRE),
								"1.16 fired nothing and set no cooldown for a charge let go before it was full: phase "
										+ charge.getPhase() + ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
					}
					case "fired at its full length" -> {
						int ticks = 3;
						while (charge.getPhase() == ActionPhase.BUTTON_CHARGE && ticks < 40) {
							helper.assertTrue(!s.power.isAbilityOnCooldown(CROSSFIRE), "1.16 set no cooldown while the charge"
									+ " ran (tick " + ticks + "): " + s.power.getAbilityCooldown(CROSSFIRE));
							s.tick(1);
							ticks++;
						}
						// 1.16's power fired the full charge after the Stand's tick, whose next tick performed the shot
						helper.assertTrue(!charge.isOver() && charge.getPhase() == ActionPhase.PERFORM && charge.getPhaseTick() < 1,
								"setup: the full Crossfire Hurricane charge did not fire into its perform's start: phase "
										+ charge.getPhase() + " tick " + charge.getPhaseTick());
						helper.assertTrue(s.power.getAbilityCooldown(CROSSFIRE) == COOLDOWN
										&& s.power.getAbilityCooldownTotal(CROSSFIRE) == COOLDOWN && !charge.isCooldownTakenAfterHold(),
								"1.16 performAction set the cooldown as the charge fired: " + s.power.getAbilityCooldown(CROSSFIRE)
										+ " of " + s.power.getAbilityCooldownTotal(CROSSFIRE));
					}
					case "fired on its release after a full charge" -> {
						s.tick(30);
						helper.assertTrue(charge.getPhase() == ActionPhase.BUTTON_CHARGE
										&& charge.getPhaseTick() >= charge.getCurPhaseLength() && !s.power.isAbilityOnCooldown(CROSSFIRE),
								"setup: the full charge is not waiting for its release without a cooldown: phase " + charge.getPhase()
										+ ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
						s.release();
						helper.assertTrue(charge.getPhase() == ActionPhase.PERFORM && s.power.getAbilityCooldown(CROSSFIRE) == COOLDOWN,
								"1.16 stopHeldAction(true) -> performAction set the cooldown as the release fired the charge: phase "
										+ charge.getPhase() + ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
					}
					case "dropped by a failed check" -> {
						s.instance.removePart(StandPart.MAIN_BODY);
						s.tick(1);
						helper.assertTrue(charge.isOver() && !s.power.isAbilityOnCooldown(CROSSFIRE),
								"1.16 stopHeldAction(false) after a failed check fired nothing and set no cooldown: phase "
										+ charge.getPhase() + ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
					}
					default -> {
						s.power.setAbilityCooldown(CROSSFIRE, 40);
						s.tick(1);
						helper.assertTrue(charge.isOver() && s.power.getAbilityCooldown(CROSSFIRE) == 40,
								"1.16 ended the charge on a cooldown set from elsewhere during it and set none of its own: phase "
										+ charge.getPhase() + ", cooldown " + s.power.getAbilityCooldown(CROSSFIRE));
					}
				}
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHoldOnlyAbilityTakesItsCooldownAsItsHoldEndsAsIn116(GameTestHelper helper) {
		for (String row : new String[] { "let go", "let go, its release leaving it running", "ended by a failed check",
				"its held perform ran out", "cleared from the Stand", "scaled by the ticks held" }) {
			try (Scene s = new Scene(helper, "HeldCooldownHoldOnly")) {
				Ability registered = s.registered(FLAME_BURST);
				EntityActionAbility flameBurst = s.withCooldown(switch (row) {
					case "let go, its release leaving it running" -> new MagiciansRedFlameBurstAbility(registered.abilityType,
							registered.abilityId) {
						@Override
						public EntityActionInstance createActionObj() {
							return new FlameBurstShot(this) {
								@Override
								public void onButtonStopHold() {}
							};
						}
					};
					case "scaled by the ticks held" -> new MagiciansRedFlameBurstAbility(registered.abilityType,
							registered.abilityId) {
						@Override
						public int getCooldown(Power<?> context, int ticksHeld) {
							return 10 + ticksHeld;
						}
					};
					default -> new MagiciansRedFlameBurstAbility(registered.abilityType, registered.abilityId);
				});
				EntityActionInstance burst = s.press(flameBurst, BufferingState.clickOnly());
				helper.assertTrue(burst != null && burst.getPhase() == ActionPhase.PERFORM && flameBurst.isActionHeld(burst),
						"setup (" + row + "): the Flame Burst press did not start its held perform: " + burst);
				s.tick(4);
				helper.assertTrue(!burst.isOver() && burst.getPhase() == ActionPhase.PERFORM
								&& !s.power.isAbilityOnCooldown(FLAME_BURST),
						"1.16 set no cooldown during the hold of a hold-only action (" + row + "): phase " + burst.getPhase()
								+ ", cooldown " + s.power.getAbilityCooldown(FLAME_BURST));
				switch (row) {
					case "let go", "scaled by the ticks held" -> s.release();
					case "let go, its release leaving it running" -> {
						s.release();
						helper.assertTrue(!burst.isOver() && burst.getPhase() == ActionPhase.PERFORM,
								"setup: the Flame Burst whose release does nothing is no longer running: " + burst.getPhase());
					}
					case "ended by a failed check" -> {
						s.instance.removePart(StandPart.MAIN_BODY);
						s.tick(1);
						helper.assertTrue(burst.isOver() || burst.getPhase() == ActionPhase.RECOVERY,
								"setup: the failed check did not end the Flame Burst: phase " + burst.getPhase());
					}
					case "its held perform ran out" -> {
						burst.setPhase(ActionPhase.PERFORM, (int) Math.ceil(burst.getCurPhaseLength()));
						helper.assertTrue(burst.isOver() || burst.getPhase() == ActionPhase.RECOVERY,
								"setup: the Flame Burst's perform did not end: phase " + burst.getPhase());
					}
					default -> s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
				}
				// held 4 ticks: getCooldown(power, ticksHeld)
				int expected = row.equals("scaled by the ticks held") ? 10 + 4 : COOLDOWN;
				int cooldown = s.power.getAbilityCooldown(FLAME_BURST);
				helper.assertTrue(cooldown == expected, "1.16 stopHeldAction set a hold-only action's cooldown as its hold ended ("
						+ row + "): " + cooldown + ", expected " + expected);
				helper.assertTrue(!burst.isCooldownTakenAfterHold(), "the Flame Burst still waits for its cooldown (" + row + ")");
			}
		}
		helper.succeed();
	}

	// PowerBaseImpl.performAction set a click action's cooldown at its click; a held ability that keeps its press's
	// cooldown (setsCooldownAfterHold false) has it at its press too
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aClickAbilityAndOneThatKeepsItsPressCooldownTakeItAtThePress(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldCooldownClick")) {
			Ability registered = s.registered(CROSSFIRE);
			EntityActionAbility kept = s.withCooldown(new MagiciansRedCrossfireHurricaneAbility(registered.abilityType,
					registered.abilityId) {
				@Override
				public boolean setsCooldownAfterHold(EntityActionInstance action) {
					return false;
				}
			});
			EntityActionInstance charge = s.press(kept, BufferingState.clickOnly());
			helper.assertTrue(charge != null && charge.getPhase() == ActionPhase.BUTTON_CHARGE
							&& s.power.getAbilityCooldown(CROSSFIRE) == COOLDOWN && !charge.isCooldownTakenAfterHold(),
					"a held ability that keeps its press's cooldown did not have it at its press: " + charge + ", cooldown "
							+ s.power.getAbilityCooldown(CROSSFIRE));
			s.release();
			s.tick(1);

			Ability fireball = s.registered("fireball");
			EntityActionAbility clicked = s.withCooldown(new MagiciansRedFireballAbility(
					fireball.abilityType, fireball.abilityId));
			EntityActionInstance shot = s.press(clicked, BufferingState.clickOnly(), InputMethod.CLICK);
			helper.assertTrue(shot != null && s.power.getAbilityCooldown("fireball") == COOLDOWN && !shot.isCooldownTakenAfterHold(),
					"1.16 set a click action's cooldown at its click: " + shot + ", cooldown " + s.power.getAbilityCooldown("fireball"));
		}
		helper.succeed();
	}

	// 1.16 StandEntity.checkInputBuffer clicked a queued action again (clickAction), a held one with setHeldAction
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aBufferedChargeTakesItsCooldownOnlyAsItFiresAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "HeldCooldownBuffered")) {
			Ability registered = s.registered(CROSSFIRE);
			s.unlock(CROSSFIRE);
			CooldownFields saved = CooldownFields.of(registered);
			try {
				registered.cooldown(0, COOLDOWN);
				Ability heavy = s.registered("heavy_punch");
				EntityActionInstance blocker = new EntityActionInstance((EntityActionType) heavy);
				blocker.phasesLength.put(ActionPhase.PERFORM, 200F);
				blocker.setStartingPhase();
				s.standAction.setAction(blocker, s.user, SyncType.NO_SYNC);
				BufferingState pressed = BufferingState.clickCanBuffer();
				AbilityInput.keyPress(KEY, registered, s.user, null, InputMethod.HOLD, 0, pressed, registered.getAbilityId());
				helper.assertTrue(pressed.shouldBuffer && !pressed.isActionSuccess && s.stand.getCurStandAction() == blocker
								&& !s.power.isAbilityOnCooldown(CROSSFIRE),
						"setup: the Crossfire Hurricane press was not buffered behind the running action");
				s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
				s.input.tick();
				EntityActionInstance charge = s.stand.getCurStandAction();
				helper.assertTrue(charge != null && charge.ability == registered && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
						"setup: the buffered Crossfire Hurricane did not start once the Stand was free: " + charge);
				helper.assertTrue(!s.power.isAbilityOnCooldown(CROSSFIRE) && charge.isCooldownTakenAfterHold(),
						"1.16 set no cooldown as a queued hold-to-fire action started: " + s.power.getAbilityCooldown(CROSSFIRE));
				s.tick(2);
				s.release();
				s.tick(1);
				helper.assertTrue(charge.isOver() && !s.power.isAbilityOnCooldown(CROSSFIRE),
						"1.16 set no cooldown for a queued charge let go early: phase " + charge.getPhase() + ", cooldown "
								+ s.power.getAbilityCooldown(CROSSFIRE));
			}
			finally {
				saved.restore(registered);
			}
		}
		helper.succeed();
	}

	/** An ability's cooldown fields, to put the registered one back as it was. */
	private record CooldownFields(Object[] values) {
		private static final String[] NAMES = { "cooldownTicks", "cooldownTechnicalTicks", "cooldownAdditionalTicks",
				"cooldownResolveMultiplier", "cooldownResolveMultiplierApplies" };

		static CooldownFields of(Ability ability) {
			Object[] values = new Object[NAMES.length];
			for (int i = 0; i < NAMES.length; i++) {
				values[i] = field(NAMES[i]).get(ability);
			}
			return new CooldownFields(values);
		}

		void restore(Ability ability) {
			for (int i = 0; i < NAMES.length; i++) {
				field(NAMES[i]).set(ability, values[i]);
			}
		}

		private static Accessor field(String name) {
			try {
				Field field = Ability.class.getDeclaredField(name);
				field.setAccessible(true);
				return new Accessor(field);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Ability." + name + " is not reachable", e);
			}
		}

		private record Accessor(Field field) {
			Object get(Object owner) {
				try {
					return field.get(owner);
				}
				catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}

			void set(Object owner, Object value) {
				try {
					field.set(owner, value);
				}
				catch (IllegalAccessException e) {
					throw new IllegalStateException(e);
				}
			}
		}
	}

	/** A survival player with Magician's Red summoned at Resolve level 4; the user's tick does not run. */
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final StandType standType;
		private final StandPower power;
		private final StandInstance instance;
		private final StandEntity stand;
		private final LivingComponentAction standAction;
		private final EntityActionInputState input;

		private Scene(GameTestHelper helper, String name) {
			this.helper = helper;
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			helper.assertTrue(standType != null, "Missing Stand type magicians_red");
			user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
			user.getAbilities().instabuild = false;
			Vec3 pos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
			instance = power.getStandInstance().orElse(null);
			power.setResolveLevel(4);
			helper.assertTrue(instance != null && standType.summon(user, power), "Could not summon Magician's Red");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Magician's Red entity is missing");
			// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());
			standAction = LivingComponentAction.getComponent(stand);
			input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		private Ability registered(String name) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing Magician's Red ability " + name);
			return found;
		}

		// as a player who can press it has it; the scene does not run the user's tick, whose Resolve rule unlocks skills
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
		}

		private EntityActionAbility withCooldown(EntityActionAbility ability) {
			ability.cooldown(0, COOLDOWN);
			unlock(ability.name());
			helper.assertTrue(ability.getCooldown(power, 0) > 0, "fixture: " + ability.name() + " has no cooldown");
			return ability;
		}

		private EntityActionInstance press(Ability ability, BufferingState state) {
			return press(ability, state, InputMethod.HOLD);
		}

		// the server's press of a key, as ClAbilityInputPacket makes it
		private EntityActionInstance press(Ability ability, BufferingState state, InputMethod inputMethod) {
			AbilityInput.keyPress(KEY, ability, user, null, inputMethod, 0, state, null);
			return stand.getCurStandAction();
		}

		private void release() {
			AbilityInput.keyRelease(KEY, user);
		}

		private void tick(int count) {
			for (int tick = 0; tick < count; tick++) {
				standAction.tick();
			}
		}

		@Override
		public void close() {
			release();
			if (instance != null) {
				for (StandPart part : StandPart.values()) {
					instance.addPart(part);
				}
			}
			if (power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			AABB around = new AABB(user.blockPosition()).inflate(32);
			for (Entity shot : helper.getLevel().getEntitiesOfClass(Entity.class, around,
					entity -> entity instanceof MRCrossfireHurricaneEntity || entity instanceof MRFlameEntity)) {
				shot.discard();
			}
			user.discard();
		}
	}
}
