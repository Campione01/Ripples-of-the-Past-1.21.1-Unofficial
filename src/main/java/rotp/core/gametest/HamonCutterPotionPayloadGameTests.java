package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import rotp.core.core.JojoMod;
import rotp.core.impl.powers.hamon.entity.HamonCutterEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// 1.16 HamonCutterEntity: the potion rides the cutter through a save, and instant effects get the dose 3 / 16 == 0.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HamonCutterPotionPayloadGameTests {
	private static final float TARGET_HEALTH = 5.0F;

	private HamonCutterPotionPayloadGameTests() {}

	@GameTest(template = "empty", batch = "hamon_cutter_potion_reload", timeoutTicks = 60)
	public static void reloadedCutterKeepsItsPotionPayload(GameTestHelper helper) {
		run(helper, true, List.of(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, true, false)),
				(target, heals) -> {
					MobEffectInstance slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
					helper.assertTrue(slowness != null, "A cutter saved and reloaded in flight lost its potion effect");
					helper.assertTrue(slowness.getAmplifier() == 1 && slowness.getDuration() > 150
							&& slowness.getDuration() <= 200 && slowness.isAmbient() && !slowness.isVisible(),
							"The reloaded cutter changed its potion effect: " + slowness);
				});
	}

	@GameTest(template = "empty", batch = "hamon_cutter_potion_instant", timeoutTicks = 60)
	public static void instantPotionCutterGivesTheDonorZeroDose(GameTestHelper helper) {
		run(helper, false, List.of(new MobEffectInstance(MobEffects.HEAL, 1, 0),
				new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0)),
				(target, heals) -> {
					helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
							"The potion cutter did not apply its payload");
					helper.assertTrue(heals.stream().noneMatch(amount -> amount > 0.0F),
							"An Instant Health cutter healed its target, 1.16 gives a zero dose: " + heals);
				});
	}

	private interface Check {
		void accept(Pig target, List<Float> heals);
	}

	private static void run(GameTestHelper helper, boolean reload, List<MobEffectInstance> effects, Check check) {
		ServerLevel level = helper.getLevel();
		BlockPos template = helper.absolutePos(BlockPos.ZERO);
		ChunkPos chunk = new ChunkPos(template);
		double x = chunk.getMinBlockX() + 8.5D;
		double y = template.getY() + 80.0D;
		double z = chunk.getMinBlockZ() + 3.5D;
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Pig target = EntityType.PIG.create(level);
		List<Float> heals = new ArrayList<>();
		Consumer<LivingHealEvent> listener = event -> {
			if (event.getEntity() == target) {
				heals.add(event.getAmount());
			}
		};
		Runnable cleanup = () -> {
			NeoForge.EVENT_BUS.unregister(listener);
			level.getEntitiesOfClass(HamonCutterEntity.class, user.getBoundingBox().inflate(160.0D)).forEach(Entity::discard);
			target.discard();
			user.discard();
		};
		try {
			user.setNoGravity(true);
			user.moveTo(x, y, z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.yBodyRot = 0.0F;
			target.setNoAi(true);
			target.setNoGravity(true);
			target.setPersistenceRequired();
			// the cutter leaves 0.1 below the eyes
			target.moveTo(x, user.getEyeY() - 0.1D - target.getBbHeight() / 2.0D, z + 4.0D, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user) && level.addFreshEntity(target)
					&& level.isPositionEntityTicking(user.blockPosition())
					&& level.isPositionEntityTicking(target.blockPosition()), "Could not add the ticking cutter actors");
			target.setHealth(TARGET_HEALTH);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingHealEvent.class, listener);

			ItemStack potion = new ItemStack(Items.POTION);
			potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(), effects));
			// as HamonCutterAbility builds each cutter, without the spread
			HamonCutterEntity cutter = new HamonCutterEntity(user, level, potion);
			cutter.setHamonStatPoints(50.0F);
			cutter.shootFromRotation(user, 1.5F, 0.0F);
			if (reload) {
				CompoundTag saved = new CompoundTag();
				helper.assertTrue(cutter.save(saved), "The cutter cannot be saved");
				Entity loaded = EntityType.create(saved, level).orElse(null);
				helper.assertTrue(loaded instanceof HamonCutterEntity && loaded != cutter, "The saved cutter did not load");
				cutter = (HamonCutterEntity) loaded;
			}
			helper.assertTrue(level.addFreshEntity(cutter), "Could not add the cutter");
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}
		helper.runAfterDelay(8, () -> {
			try {
				helper.assertTrue(target.getHealth() < TARGET_HEALTH || heals.stream().anyMatch(amount -> amount > 0.0F),
						"The cutter never hit its target");
				check.accept(target, heals);
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}
}
