package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.stand.StandVirusMobGiver;
import rotp.core.api.stand.StandVirusMobGiverContext;
import rotp.core.api.stand.StandVirusMobGivers;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModItems;
import rotp.core.mechanics.standarrow.StandArrowEntity;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandArrowEntity.onHitEntity quartered the damage, after the crit roll, when the target was a Stand user or a
 * mob a registered MobStandGiver matched (StandVirusEffect.mobMayGetStand); plain mobs and Stand-less players took the
 * full damage.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandArrowDamageGameTests {
	private static final float EPS = 1.0E-3F;
	// base damage 2 at speed 4: ceil(8)
	private static final float FULL = 8.0F;
	private static final String GIVER_TAG = "rotp_arrow_damage_gametest_giver";
	private static final ResourceLocation GIVER_OWNER = JojoMod.resLoc("arrow_damage_gametest_giver");

	static {
		// matches only tagged test mobs
		if (StandVirusMobGivers.get(GIVER_OWNER).isEmpty()) {
			StandVirusMobGivers.register(GIVER_OWNER, new StandVirusMobGiver() {
				@Override
				public boolean matches(LivingEntity target) {
					return target.getTags().contains(GIVER_TAG);
				}

				@Override
				public float survivalChance(StandVirusMobGiverContext context) {
					return 0.0F;
				}

				@Override
				public boolean giveStand(StandVirusMobGiverContext context) {
					return false;
				}
			});
		}
	}

	private StandArrowDamageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void standUsersAndGiverMobsTakeAQuarter(GameTestHelper helper) {
		List<Entity> created = new ArrayList<>();
		try {
			LivingEntity plainCow = create(helper, EntityType.COW, created);
			float lost = hit(helper, plainCow, created);
			helper.assertTrue(Math.abs(lost - FULL) < EPS, "1.16: a plain mob takes the full 8; lost " + lost);

			Player plainPlayer = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			created.add(plainPlayer);
			lost = hit(helper, plainPlayer, created);
			helper.assertTrue(Math.abs(lost - FULL) < EPS, "1.16: a Stand-less player takes the full 8; lost " + lost);

			LivingEntity userCow = create(helper, EntityType.COW, created);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(PowerClass.STAND.attachGet(userCow),
					new StandInstance(type)).status() == StandPowerTransitions.Status.APPLIED,
					"Could not give the cow Star Platinum");
			lost = hit(helper, userCow, created);
			helper.assertTrue(Math.abs(lost - FULL / 4) < EPS, "1.16: a Stand user takes a quarter (2); lost " + lost);

			LivingEntity giverCow = create(helper, EntityType.COW, created);
			giverCow.addTag(GIVER_TAG);
			lost = hit(helper, giverCow, created);
			helper.assertTrue(Math.abs(lost - FULL / 4) < EPS,
					"1.16: a registered Stand-giver mob takes a quarter (2); lost " + lost);

			// armor 2 turns the quartered 2 into 1.92; the full 8 would be 7.87
			LivingEntity turtle = create(helper, ModEntityTypes.COCO_JUMBO_TURTLE.get(), created);
			lost = hit(helper, turtle, created);
			helper.assertTrue(lost > 1.5F && lost < FULL / 4 + EPS,
					"1.16: the Coco Jumbo turtle takes a quarter (2 before armor); lost " + lost);
		}
		finally {
			created.forEach(Entity::discard);
		}
		helper.succeed();
	}

	private static LivingEntity create(GameTestHelper helper, EntityType<?> type, List<Entity> created) {
		Entity entity = type.create(helper.getLevel());
		helper.assertTrue(entity instanceof LivingEntity, "Could not create " + type);
		entity.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
		created.add(entity);
		return (LivingEntity) entity;
	}

	private static float hit(GameTestHelper helper, LivingEntity target, List<Entity> created) {
		Vec3 pos = helper.absoluteVec(new Vec3(0.5, 2.5, 1.5));
		StandArrowEntity arrow = new StandArrowEntity(helper.getLevel(), pos.x, pos.y, pos.z,
				new ItemStack(ModItems.STAND_ARROW.get()), null);
		created.add(arrow);
		arrow.setBaseDamage(2.0);
		arrow.setCritArrow(false);
		arrow.setDeltaMovement(4.0, 0.0, 0.0);
		float before = target.getHealth();
		try {
			Method onHit = StandArrowEntity.class.getDeclaredMethod("onHitEntity", EntityHitResult.class);
			onHit.setAccessible(true);
			onHit.invoke(arrow, new EntityHitResult(target));
		}
		catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Could not call StandArrowEntity.onHitEntity", error);
		}
		return before - target.getHealth();
	}
}
