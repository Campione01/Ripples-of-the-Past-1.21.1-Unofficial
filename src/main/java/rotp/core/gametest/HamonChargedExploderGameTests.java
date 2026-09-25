package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.EntityHamonChargeState;
import rotp.core.init.ModDamageTypes;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonChargedExploderGameTests {
	private HamonChargedExploderGameTests() {}

	// 1.16 HamonUtil.hamonChargedCreeperBlast: a charged exploder adds a Hamon blast scaled by the charge.
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void chargedCreeperExplosionReleasesHamonBlast(GameTestHelper helper) {
		Creeper creeper = helper.spawn(EntityType.CREEPER, new Vec3(1.5D, 2.0D, 1.5D));
		Pig pig = helper.spawn(EntityType.PIG, new Vec3(3.5D, 2.0D, 1.5D));
		creeper.setNoAi(true);
		pig.setNoAi(true);
		List<Float> hamonAmounts = new ArrayList<>();
		List<Entity> hamonDirect = new ArrayList<>();
		Consumer<LivingIncomingDamageEvent> recorder = event -> {
			if (event.getEntity() == pig) {
				if (event.getSource().is(ModDamageTypes.HAMON)) {
					hamonAmounts.add(event.getAmount());
					hamonDirect.add(event.getSource().getDirectEntity());
				}
				// keep the pig alive and in place
				event.setCanceled(true);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, recorder);
		try {
			EntityHamonChargeState state = EntityHamonChargeState.get(creeper);
			state.setHamonCharge(0.5F, 200, null, 0.0F);
			explode(helper, creeper);
			helper.assertTrue(hamonAmounts.size() == 1, "Charged creeper explosion hit the pig with Hamon "
					+ hamonAmounts.size() + " times, expected 1");
			helper.assertTrue(hamonAmounts.get(0) > 0.0F, "Hamon blast dealt no damage");
			helper.assertTrue(hamonDirect.get(0) == creeper, "Hamon blast source was not the charged creeper");

			state.setHamonCharge(1.5F, 200, null, 0.0F);
			explode(helper, creeper);
			helper.assertTrue(hamonAmounts.size() == 2, "Second charged explosion did not release a Hamon blast");
			float ratio = hamonAmounts.get(1) / hamonAmounts.get(0);
			helper.assertTrue(Math.abs(ratio - 3.0F) < 0.01F,
					"Hamon blast damage is not scaled by the charge damage (ratio " + ratio + ", expected 3)");

			// a cleared charge leaves the attachment but must not blast
			state.clear();
			explode(helper, creeper);
			helper.assertTrue(hamonAmounts.size() == 2, "Uncharged creeper explosion released a Hamon blast");

			Creeper plain = helper.spawn(EntityType.CREEPER, new Vec3(1.5D, 2.0D, 2.5D));
			plain.setNoAi(true);
			explode(helper, plain);
			helper.assertTrue(hamonAmounts.size() == 2, "Plain creeper explosion released a Hamon blast");
			plain.discard();
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(recorder);
			creeper.discard();
			pig.discard();
		}
	}

	private static void explode(GameTestHelper helper, Creeper creeper) {
		// small radius keeps the blast (reach 2 * radius) away from neighbouring tests
		helper.getLevel().explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 1.5F,
				Level.ExplosionInteraction.NONE);
	}
}
