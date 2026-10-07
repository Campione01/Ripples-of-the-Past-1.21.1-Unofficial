package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.SatiporojaScarfEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModItems;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 LivingEntity.hurt knocked the target away from source.getEntity(), the Hamon user, wherever the projectile moved.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonRangedKnockbackGameTests {
	// the swing covers yaw -67.5..67.5 in 13.5 degree steps, out to 7.5 blocks and back
	private static final Vec3 EXTENDING_TARGET = polar(4.0D, -20.4D);
	private static final Vec3 RETRACTING_TARGET = polar(3.2D, 50.8D);
	private static final double MIN_RADIAL_COS = 0.9998D;

	private HamonRangedKnockbackGameTests() {}

	@GameTest(template = "empty", batch = "hamon_ranged_knockback", timeoutTicks = 80)
	public static void scarfSwingKnocksTargetsAwayFromItsOwner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
		Vec3 userPos = new Vec3(chunk.getMinBlockX() + 8.0D, helper.absolutePos(BlockPos.ZERO).getY() + 40.0D,
				chunk.getMinBlockZ() + 4.0D);

		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
		user.setYHeadRot(0.0F);
		user.yBodyRot = 0.0F;
		Husk extending = target(level, userPos.add(EXTENDING_TARGET));
		Husk retracting = target(level, userPos.add(RETRACTING_TARGET));
		List<Push> pushes = new ArrayList<>();
		Consumer<LivingKnockBackEvent> listener = event -> {
			LivingEntity hit = event.getEntity();
			if (hit != extending && hit != retracting || hit.damageContainers.isEmpty()) {
				return;
			}
			DamageContainer damage = hit.damageContainers.peek();
			pushes.add(new Push(hit, damage.getSource().is(ModDamageTypes.HAMON)
					&& damage.getSource().getDirectEntity() instanceof SatiporojaScarfEntity,
					new Vec3(-event.getRatioX(), 0.0D, -event.getRatioZ()),
					new Vec3(hit.getX() - user.getX(), 0.0D, hit.getZ() - user.getZ())));
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(listener);
			extending.discard();
			retracting.discard();
			user.discard();
		};
		try {
			helper.assertTrue(level.addFreshEntity(user) && level.addFreshEntity(extending)
					&& level.addFreshEntity(retracting), "Could not add the scarf knockback actors");
			helper.assertTrue(level.isPositionEntityTicking(user.blockPosition())
					&& level.isPositionEntityTicking(extending.blockPosition())
					&& level.isPositionEntityTicking(retracting.blockPosition()),
					"Scarf knockback actors are outside the ticking chunk");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			hamon.learnSkill(ModHamonSkills.SATIPOROJA_SCARF.get());
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			user.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SATIPOROJA_SCARF.get()));
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, listener);
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}

		helper.runAfterDelay(3, () -> {
			try {
				helper.assertTrue(user.getMainHandItem().use(level, user, InteractionHand.MAIN_HAND)
						.getResult().consumesAction(), "The scarf swing did not start");
			}
			catch (RuntimeException | Error error) {
				cleanup.run();
				throw error;
			}
		});
		// the swing lives 10 ticks
		helper.runAfterDelay(18, () -> {
			try {
				helper.assertTrue(user.position().distanceToSqr(userPos) < 1.0E-8D, "The scarf owner moved");
				assertPushedAway(helper, pushes, extending, "extending");
				assertPushedAway(helper, pushes, retracting, "retracting");
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}

	private static void assertPushedAway(GameTestHelper helper, List<Push> pushes, LivingEntity target, String half) {
		List<Push> own = pushes.stream().filter(push -> push.target == target).toList();
		helper.assertTrue(!own.isEmpty(), "The scarf swing never knocked back the target of its " + half + " half");
		for (Push push : own) {
			helper.assertTrue(push.scarfHamon, "The " + half + " target was knocked back by something else than the scarf");
			double cos = push.direction.normalize().dot(push.fromOwner.normalize());
			helper.assertTrue(cos >= MIN_RADIAL_COS, "Scarf Hamon knockback on the " + half
					+ " half is not away from the owner: push=" + push.direction.normalize()
					+ " fromOwner=" + push.fromOwner.normalize() + " cos=" + cos);
		}
	}

	private static Husk target(ServerLevel level, Vec3 pos) {
		Husk husk = EntityType.HUSK.create(level);
		husk.setNoAi(true);
		husk.setNoGravity(true);
		husk.setPersistenceRequired();
		husk.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		return husk;
	}

	private static Vec3 polar(double distance, double yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.sin(yaw) * distance, 0.0D, Math.cos(yaw) * distance);
	}

	private record Push(LivingEntity target, boolean scarfHamon, Vec3 direction, Vec3 fromOwner) {}
}
