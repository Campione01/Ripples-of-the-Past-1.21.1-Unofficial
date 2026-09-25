package rotp.core.gametest;

import java.util.List;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 ShearsItemMixin: shears used on a shearable Gold Experience lifeform do not shear it; the shearer takes
 * 1 player-attack damage and the interaction still succeeds.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceShearsGameTests {
	private GoldExperienceShearsGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void shearingGoldExperienceSheepHurtsTheShearerInstead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer geUser = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GeShearsUser"));
		Sheep sheep = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(1, 2, 1));
		Player shearer = addShearer(helper);
		AABB area = sheep.getBoundingBox().inflate(1.5);
		try {
			makeLifeform(geUser, sheep);
			ItemStack shears = new ItemStack(Items.SHEARS);
			shearer.setItemInHand(InteractionHand.MAIN_HAND, shears);
			float before = shearer.getHealth();
			InteractionResult result = shearer.interactOn(sheep, InteractionHand.MAIN_HAND);
			float lost = before - shearer.getHealth();
			helper.assertTrue(result.consumesAction(), "shearing a GE lifeform should still succeed: " + result);
			helper.assertFalse(sheep.isSheared(), "a GE lifeform sheep was sheared (1.16 refused the shears)");
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
					"shearing a GE lifeform dropped items");
			helper.assertTrue(lost > 0.5F && lost < 1.01F,
					"the shearer should take 1 damage from a GE lifeform, lost " + lost);
			helper.assertTrue(shears.getDamageValue() == 0, "refused shears still lost durability");
			helper.succeed();
		}
		finally {
			cleanup(level, area, geUser, shearer, sheep);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void shearsRefusalOnlyForShearableGoldExperienceLifeforms(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer geUser = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GeShearsUser"));
		Sheep plain = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(1, 2, 1));
		Sheep lifeform = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(3, 2, 1));
		Sheep shearedLifeform = helper.spawnWithNoFreeWill(EntityType.SHEEP, new BlockPos(1, 2, 3));
		Player shearer = addShearer(helper);
		AABB area = plain.getBoundingBox().inflate(3.0);
		try {
			makeLifeform(geUser, lifeform);
			shearedLifeform.setSheared(true);
			makeLifeform(geUser, shearedLifeform);

			shearer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
			float lost = interact(shearer, plain);
			helper.assertTrue(plain.isSheared(), "a plain sheep was not sheared");
			helper.assertTrue(lost == 0.0F, "shearing a plain sheep hurt the shearer by " + lost);

			shearer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
			lost = interact(shearer, lifeform);
			helper.assertFalse(lifeform.isSheared(), "a stick sheared a GE lifeform sheep");
			helper.assertTrue(lost == 0.0F, "a non-shears item on a GE lifeform hurt the player by " + lost);

			shearer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
			lost = interact(shearer, shearedLifeform);
			helper.assertTrue(lost == 0.0F, "shears on an already sheared GE lifeform hurt the player by " + lost);
			helper.succeed();
		}
		finally {
			cleanup(level, area, geUser, shearer, plain, lifeform, shearedLifeform);
		}
	}

	private static Player addShearer(GameTestHelper helper) {
		Player shearer = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		shearer.moveTo(origin.x, origin.y, origin.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(shearer), "Could not add the shears test player");
		return shearer;
	}

	private static void makeLifeform(FakePlayer geUser, Sheep sheep) {
		StandPower power = PowerClass.STAND.attachGet(geUser);
		GECreatedLifeformEffect effect = ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().create(sheep.level());
		effect.setSourceItem(new ItemStack(Items.WHITE_WOOL));
		effect.withTarget(sheep);
		power.userStandEffects.addEffect(effect);
	}

	private static float interact(Player player, Sheep sheep) {
		// each check starts outside the post-hit invulnerability window
		player.invulnerableTime = 0;
		float before = player.getHealth();
		player.interactOn(sheep, InteractionHand.MAIN_HAND);
		return before - player.getHealth();
	}

	private static void cleanup(ServerLevel level, AABB area, FakePlayer geUser, Player shearer, Sheep... sheep) {
		StandPower power = StandPower.get(geUser);
		if (power != null) {
			for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
				power.userStandEffects.removeEffect(effect);
			}
		}
		// ending a lifeform effect starts turning it back into its source
		level.getEntities(ModEntityTypes.GE_LIFEFORM_TRANSFORMATION.get(), area, transformation -> true)
				.forEach(GETransformationEntity::discard);
		level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
		for (Sheep one : sheep) {
			one.discard();
		}
		shearer.discard();
		geUser.discard();
	}
}
