package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandAction.isUnlocked was the action's learning entry, which StandType.onNewResolveLevel added once the stored
 * Resolve level reached the action's (StandAction.canBeUnlocked) and which only a full Stand clear removed: a lower level
 * later, by /standlevel or a weaker Resolve effect, relocked nothing, and the Resolve effect raised the stored level for
 * good at its start (ResolveCounter.onResolveEffectStarted).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandResolveUnlockGameTests {
	private static final String CROSSFIRE = "crossfire_hurricane";

	private StandResolveUnlockGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStandAbilityStaysUnlockedOnceItsResolveLevelWasReachedAsIn116(GameTestHelper helper) {
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(standType != null, "Missing Magician's Red Stand type");
		Player user = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ResolveUnlock"));
		try {
			user.getAbilities().instabuild = false;
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Resolve unlock player");
			StandPower power = PowerClass.STAND.attachGet(user);
			grant(helper, power, standType);
			Ability crossfire = power.getAbility(CROSSFIRE);
			helper.assertTrue(crossfire != null && crossfire.getRequiredResolveLevel(power) == 4,
					"fixture: Crossfire Hurricane does not need Resolve level 4");

			helper.assertTrue(!crossfire.isAbilityUnlocked(power), "Crossfire Hurricane is unlocked at Resolve level 0");
			power.setResolveLevel(3);
			helper.assertTrue(!crossfire.isAbilityUnlocked(power), "Crossfire Hurricane is unlocked at Resolve level 3, never 4");
			power.setResolveLevel(4);
			helper.assertTrue(crossfire.isAbilityUnlocked(power), "Crossfire Hurricane is still locked at Resolve level 4");
			power.setResolveLevel(0);
			helper.assertTrue(power.getResolveLevel() == 0, "fixture: the Resolve level did not drop to 0");
			helper.assertTrue(crossfire.isAbilityUnlocked(power) && crossfire.isAbilityAvailable(power)
					&& resolve(power, crossfire),
					"1.16 kept Crossfire Hurricane unlocked (its learning entry) for presses and the HUD after the Resolve"
							+ " level reached 4 and dropped to 0");

			// what the server saves and sends keeps it
			StandTypePersistentData data = power.getCurTypeData();
			CompoundTag nbt = data.serializeNBT(helper.getLevel().registryAccess());
			StandTypePersistentData loaded = standType.newDataInstance();
			loaded.deserializeNBT(helper.getLevel().registryAccess(), nbt);
			helper.assertTrue(loaded.getResolveLevel() == 0 && loaded.getHighestResolveLevel() == 4,
					"a saved Stand forgot the Resolve level it had reached: level " + loaded.getResolveLevel() + ", highest "
							+ loaded.getHighestResolveLevel());
			CompoundTag olderSave = nbt.copy();
			olderSave.remove("HighestResolveLevel");
			olderSave.putInt("ResolveLevel", 3);
			StandTypePersistentData older = standType.newDataInstance();
			older.deserializeNBT(helper.getLevel().registryAccess(), olderSave);
			helper.assertTrue(older.getHighestResolveLevel() == 3,
					"a save without the highest level reads its stored level as reached: " + older.getHighestResolveLevel());
			for (boolean tracking : new boolean[] { false, true }) {
				FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
				try {
					data.toBuf(buf, tracking);
					StandTypePersistentData received = standType.newDataInstance();
					received.fromBuf(buf, tracking);
					helper.assertTrue(received.getResolveLevel() == 0 && received.getHighestResolveLevel() == 4
							&& buf.readableBytes() == 0, "a client (tracking " + tracking + ") did not get the highest Resolve level: "
									+ received.getHighestResolveLevel() + ", bytes left " + buf.readableBytes());
				}
				finally {
					buf.release();
				}
			}

			// a full Stand clear took the learning away (1.16 fullStandClear)
			power.applyDestructiveTransition(true);
			grant(helper, power, standType);
			crossfire = power.getAbility(CROSSFIRE);
			helper.assertTrue(!crossfire.isAbilityUnlocked(power), "Crossfire Hurricane stayed unlocked after a full Stand clear");

			// the Resolve effect raised the stored level for good at its start
			user.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, 400, 3));
			helper.assertTrue(power.getResolveLevel() == 4, "fixture: a Resolve effect of amplifier 3 did not raise the level to 4: "
					+ power.getResolveLevel());
			user.removeEffect(ModStatusEffects.RESOLVE);
			power.setResolveLevel(0);
			helper.assertTrue(crossfire.isAbilityUnlocked(power),
					"1.16 kept Crossfire Hurricane unlocked after a Resolve effect raised the level to 4 and the level dropped");
			helper.succeed();
		}
		finally {
			user.removeEffect(ModStatusEffects.RESOLVE);
			user.discard();
		}
	}

	// a player who can press it has its skill; the test does not run the user's tick, whose Resolve rule unlocks skills
	private static void grant(GameTestHelper helper, StandPower power, StandType standType) {
		helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
				== StandPowerTransitions.Status.APPLIED, "Could not grant Magician's Red");
		helper.assertTrue(power.usesResolve() && power.getResolveLevel() == 0, "fixture: a new Magician's Red does not use Resolve"
				+ " from level 0");
		power.getCurTypeData()._lockedAbilities.remove(CROSSFIRE);
	}

	// the HUD's and the press's list of the moves the user has now
	private static boolean resolve(StandPower power, Ability ability) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(ability) != null;
	}
}
