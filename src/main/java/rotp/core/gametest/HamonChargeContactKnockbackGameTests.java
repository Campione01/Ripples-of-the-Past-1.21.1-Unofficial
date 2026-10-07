package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.HamonData;
import rotp.core.impl.powers.hamon.ModHamonSkills;
import rotp.core.impl.powers.hamon.entity.HamonBlockChargeEntity;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.playerpower.PlayerPower;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 HamonCharge: every successful large-charge hit calls knockback(0.75) from the charge position, also for a
 * victim standing on that position, where the zero direction leaves only the grounded hop and the halved speed.
 * The hit itself has no attacker for a charged block, so 1.16 hurt() added no knockback of its own.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonChargeContactKnockbackGameTests {
	private static final BlockPos TRIPWIRE = new BlockPos(1, 2, 1);
	private static final float CHARGE_KNOCKBACK = 0.75F;
	private static final double EPSILON = 1.0E-9D;

	private HamonChargeContactKnockbackGameTests() {}

	@GameTest(template = "empty", batch = "hamon_charge_knockback_centre", timeoutTicks = 60)
	public static void chargedCobwebKnocksBackVictimStandingOnItsCentre(GameTestHelper helper) {
		run(helper, Vec3.ZERO, (victim, samples) -> {
			List<Sample> charge = chargeKnockbacks(samples);
			helper.assertTrue(!charge.isEmpty(), "A victim on the charged block centre never got the charge knockback");
			double strength = CHARGE_KNOCKBACK * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			for (Sample sample : charge) {
				helper.assertTrue(sample.ratioX == 0.0D && sample.ratioZ == 0.0D,
						"Centre charge knockback has a direction: " + sample);
				helper.assertTrue(sample.onGround && Math.abs(sample.after.x - sample.before.x / 2.0D) < EPSILON
						&& Math.abs(sample.after.z - sample.before.z / 2.0D) < EPSILON
						&& Math.abs(sample.after.y - Math.min(0.4D, sample.before.y / 2.0D + strength)) < EPSILON,
						"Centre charge knockback is not the 1.16 grounded hop without a sideways push: " + sample);
			}
		});
	}

	@GameTest(template = "empty", batch = "hamon_charge_knockback_control", timeoutTicks = 60)
	public static void chargedCobwebKnocksOffCentreVictimAwayFromItsCentre(GameTestHelper helper) {
		Vec3 offset = new Vec3(0.3D, 0.0D, 0.2D);
		run(helper, offset, (victim, samples) -> {
			List<Sample> charge = chargeKnockbacks(samples);
			helper.assertTrue(!charge.isEmpty(), "An off-centre victim never got the charge knockback");
			Vec3 toCentre = offset.scale(-1.0D).normalize();
			double strength = CHARGE_KNOCKBACK * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
			for (Sample sample : charge) {
				helper.assertTrue(Math.abs(sample.ratioX - toCentre.x) < 1.0E-6D
						&& Math.abs(sample.ratioZ - toCentre.z) < 1.0E-6D,
						"Off-centre charge knockback does not point away from the block centre: " + sample);
				helper.assertTrue(Math.abs(sample.after.x - (sample.before.x / 2.0D - sample.ratioX * strength)) < 1.0E-6D
						&& Math.abs(sample.after.z - (sample.before.z / 2.0D - sample.ratioZ * strength)) < 1.0E-6D,
						"Off-centre charge knockback did not move the victim: " + sample);
			}
		});
	}

	@GameTest(template = "empty", batch = "hamon_charge_knockback_hurt", timeoutTicks = 60)
	public static void chargedBlockHitAddsNoKnockbackOfItsOwn(GameTestHelper helper) {
		run(helper, Vec3.ZERO, (victim, samples) -> {
			helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "The charged cobweb never hurt its victim");
			for (Sample sample : samples) {
				helper.assertTrue(!sample.duringHurt || sample.strength <= 0.0F,
						"An attacker-less charged block hit knocked its victim back: " + sample);
			}
			Vec3 speed = victim.getDeltaMovement();
			helper.assertTrue(speed.x == 0.0D && speed.z == 0.0D,
					"A victim on the charged block centre was pushed sideways: " + speed);
		});
	}

	private interface Check {
		void accept(Husk victim, List<Sample> samples);
	}

	private static List<Sample> chargeKnockbacks(List<Sample> samples) {
		return samples.stream().filter(sample -> !sample.duringHurt && sample.originalStrength == CHARGE_KNOCKBACK).toList();
	}

	private static void run(GameTestHelper helper, Vec3 victimOffset, Check check) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(TRIPWIRE);
		helper.setBlock(TRIPWIRE.below(), Blocks.STONE);
		helper.setBlock(TRIPWIRE.above(), Blocks.AIR);
		helper.setBlock(TRIPWIRE, Blocks.TRIPWIRE);
		ServerPlayer user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RopeTrapCharge"));
		user.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 2.5D);
		user.getInventory().clearContent();
		Husk victim = EntityType.HUSK.create(level);
		victim.setNoAi(true);
		victim.setPersistenceRequired();
		victim.moveTo(pos.getX() + 0.5D + victimOffset.x, pos.getY(), pos.getZ() + 0.5D + victimOffset.z, 0.0F, 0.0F);
		victim.setOnGround(true);

		List<Sample> samples = new ArrayList<>();
		List<Sample> pending = new ArrayList<>();
		Consumer<LivingKnockBackEvent> knockback = event -> {
			if (event.getEntity() != victim) {
				return;
			}
			Sample sample = new Sample(!victim.damageContainers.isEmpty(), event.getOriginalStrength(), event.getStrength(),
					event.getRatioX(), event.getRatioZ(), victim.onGround(), victim.getDeltaMovement());
			samples.add(sample);
			pending.add(sample);
		};
		Consumer<EntityTickEvent.Post> chargeTicked = event -> {
			if (event.getEntity() instanceof HamonBlockChargeEntity && event.getEntity().blockPosition().equals(pos)) {
				pending.forEach(sample -> sample.after = victim.getDeltaMovement());
				pending.clear();
			}
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(knockback);
			NeoForge.EVENT_BUS.unregister(chargeTicked);
			level.getEntitiesOfClass(HamonBlockChargeEntity.class, new AABB(pos)).forEach(HamonBlockChargeEntity::discard);
			victim.discard();
			user.discard();
			helper.setBlock(TRIPWIRE, Blocks.AIR);
			helper.setBlock(TRIPWIRE.below(), Blocks.AIR);
		};
		try {
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Rope Trap user");
			PlayerPower power = PowerClass.PLAYER_POWER.attachGet(user);
			power.setPowerType(ModPlayerPowers.HAMON.get());
			HamonData hamon = PlayerPower.getPowerData(user, ModPlayerPowers.HAMON).orElseThrow();
			helper.assertTrue(hamon.learnSkill(ModHamonSkills.ROPE_TRAP.get()), "Could not grant Rope Trap");
			hamon.setBreathStability(hamon.getMaxBreathStability());
			hamon.setEnergy(hamon.getMaxEnergy());
			// the real empty-hand right click on the string
			InteractionResult click = user.gameMode.useItemOn(user, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
			helper.assertTrue(click == InteractionResult.SUCCESS && level.getBlockState(pos).is(Blocks.COBWEB)
					&& level.getEntitiesOfClass(HamonBlockChargeEntity.class, new AABB(pos)).size() == 1,
					"The right click did not charge the string: " + click + " " + level.getBlockState(pos));
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingKnockBackEvent.class, knockback);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, chargeTicked);
			helper.assertTrue(level.addFreshEntity(victim), "Could not add the charged cobweb victim");
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}
		helper.runAfterDelay(6, () -> {
			try {
				samples.removeIf(sample -> sample.after == null);
				JojoMod.LOGGER.info("HAMON-CHARGE-KNOCKBACK offset={} health={} samples={}", victimOffset,
						victim.getHealth(), samples);
				check.accept(victim, samples);
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}

	private static final class Sample {
		final boolean duringHurt;
		final float originalStrength;
		final float strength;
		final double ratioX;
		final double ratioZ;
		final boolean onGround;
		final Vec3 before;
		Vec3 after;

		Sample(boolean duringHurt, float originalStrength, float strength, double ratioX, double ratioZ,
				boolean onGround, Vec3 before) {
			this.duringHurt = duringHurt;
			this.originalStrength = originalStrength;
			this.strength = strength;
			this.ratioX = ratioX;
			this.ratioZ = ratioZ;
			this.onGround = onGround;
			this.before = before;
		}

		@Override
		public String toString() {
			return "[duringHurt=" + duringHurt + " strength=" + originalStrength + "->" + strength + " ratio=" + ratioX
					+ "," + ratioZ + " onGround=" + onGround + " speed=" + before + "->" + after + "]";
		}
	}
}
