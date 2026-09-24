package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.HamonSkill;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonSendoOverdriveEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 PowerBaseImpl.onClickAction said an action's line when it was pressed, a hold-to-fire one's too: the S.Y.O.
 * Barrage's start line on the press, then its barrage line at the release (perform, interrupt on).
 * 1.16 HamonSendoOverdrive.stoppedHolding sent the wave on any stop with a block targeted; a release that failed its
 * energy check skipped only the perform, and consumeEnergy(900) still left an empty user out of breath.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonPressShoutGameTests {
	private static final short KEY = 9;

	private HamonPressShoutGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void syoBarrageStartLineOnPressBarrageLineAtRelease(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SUNLIGHT_YELLOW_OVERDRIVE_BARRAGE.get());
				VoiceLines lines = new VoiceLines(helper, f.user)) {
			EntityActionInstance action = f.start("sunlight_yellow_overdrive_barrage", true);
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "The S.Y.O. Barrage is not charging");
			helper.assertTrue(lines.count(ModSoundEvents.JONATHAN_SYO_BARRAGE_START.get()) == 1,
					"1.16 said the S.Y.O. Barrage start line on the press: " + lines.count(ModSoundEvents.JONATHAN_SYO_BARRAGE_START.get()));
			helper.assertTrue(lines.count(ModSoundEvents.JONATHAN_SYO_BARRAGE.get()) == 0, "The barrage line came on the press");

			// held past the 200-tick repeat guard of a voice line, so a second start line would be heard
			f.tick(210);
			AbilityInput.keyRelease(KEY, f.user);
			f.tick(1);
			helper.assertTrue(action.getPhase() == ActionPhase.PERFORM, "The released S.Y.O. Barrage did not fire: " + action.getPhase());
			helper.assertTrue(lines.count(ModSoundEvents.JONATHAN_SYO_BARRAGE.get()) == 1, "1.16 said the barrage line at the release");
			helper.assertTrue(lines.count(ModSoundEvents.JONATHAN_SYO_BARRAGE_START.get()) == 1,
					"The start line was said again when the barrage fired");
			helper.succeed();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void sendoReleaseWithNoEnergyStillSendsTheWave(GameTestHelper helper) {
		try (Fixture f = new Fixture(helper, ModHamonSkills.SENDO_OVERDRIVE.get())) {
			AABB area = sendoWall(helper);
			int wavesBefore = waves(helper, area);
			EntityActionInstance action = f.start("sendo_overdrive", true);
			f.tick(35);
			helper.assertTrue(action.getPhase() == ActionPhase.WINDUP, "Sendo Overdrive is not held: " + action.getPhase());

			f.hamon.setEnergy(0.0F);
			f.hamon.setBreathStability(0.0F);
			f.user.setAirSupply(f.user.getMaxAirSupply());
			AbilityInput.keyRelease(KEY, f.user);
			f.tick(1);
			helper.assertTrue(waves(helper, area) > wavesBefore,
					"1.16 stoppedHolding sent the wave on a release that failed its energy check");
			helper.assertTrue(f.user.getAirSupply() == 0,
					"1.16 still asked for the cost, which leaves an empty user out of breath: air=" + f.user.getAirSupply());
			helper.succeed();
		}
	}

	private static AABB sendoWall(GameTestHelper helper) {
		for (int y = 2; y <= 6; y++) {
			helper.setBlock(new BlockPos(2, y, 5), Blocks.STONE.defaultBlockState());
		}
		return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12.0D);
	}

	private static int waves(GameTestHelper helper, AABB area) {
		return helper.getLevel().getEntitiesOfClass(HamonSendoOverdriveEntity.class, area).size();
	}

	// voice lines a player says (JojoModUtil.sayVoiceLine posts them at the player's position)
	private static final class VoiceLines implements AutoCloseable {
		private final List<SoundEvent> said = new ArrayList<>();
		private final Consumer<PlayLevelSoundEvent.AtPosition> listener;

		private VoiceLines(GameTestHelper helper, Player player) {
			listener = event -> {
				Holder<SoundEvent> sound = event.getSound();
				if (event.getLevel() == helper.getLevel() && sound != null
						&& event.getPosition().distanceToSqr(player.position()) < 0.01) {
					said.add(sound.value());
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		}

		private int count(SoundEvent line) {
			int count = 0;
			for (SoundEvent each : said) {
				if (each == line) {
					count++;
				}
			}
			return count;
		}

		@Override
		public void close() {
			NeoForge.EVENT_BUS.unregister(listener);
		}
	}

	private static final class Fixture implements AutoCloseable {
		private final GameTestHelper helper;
		private final Player user;
		private final PlayerPower power;
		private final HamonData hamon;
		private final LivingComponentAction component;

		private Fixture(GameTestHelper helper, HamonSkill skill) {
			this.helper = helper;
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setNoGravity(true);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Hamon test player");
			power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(skill);
			helper.assertTrue(hamon.isSkillLearned(skill), "Could not grant " + skill);
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			component = LivingComponentAction.getComponent(user);
		}

		private EntityActionInstance start(String name, boolean heldByKey) {
			Ability found = power.getAbility(name);
			helper.assertTrue(found instanceof EntityActionAbility, "Missing registered " + name);
			EntityActionInstance action = ((EntityActionAbility) found).initActionOnAbilityUse(helper.getLevel(), user, user, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			if (heldByKey) {
				EntityActionInputState input = user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
				input.heldKeys.put(KEY, new HeldInputEntry(KEY, 1L, PowerClass.PLAYER_POWER, action));
			}
			return action;
		}

		private void tick(int count) {
			// the real action lifecycle and voice line repeat guard, without entity physics or passive Hamon regeneration
			for (int tick = 0; tick < count; tick++) {
				user.tickCount++;
				component.tick();
				user.getData(ModDataAttachmentTypes.PLAYER_VOICE_LINES.get()).tick();
			}
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			component.setAction(null, SyncType.NO_SYNC);
			user.removeAllEffects();
			user.discard();
		}
	}
}
