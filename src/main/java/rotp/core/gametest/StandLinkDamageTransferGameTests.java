package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.actuallyHurt: a hit on a health-linked Stand passes the Stand-resisted amount (standDamageResistance,
 * damage * (1 - physical resistance)) to the user, whatever armor the user wears, through hurtThroughInvulTicks, which
 * gives the user's own hurt cooldown (invulnerableTime, lastHurt) back afterwards.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandLinkDamageTransferGameTests {
	private static final float HIT = 10.0F;

	private StandLinkDamageTransferGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void linkedHitPassesTheStandResistedAmountAndKeepsUserCooldown(GameTestHelper helper) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = null;
		Cow attacker = null;
		try {
			helper.assertTrue(standType != null, "Missing Star Platinum Stand type");
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.getAbilities().invulnerable = false;
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the linked user");
			user.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
			user.setHealth(100);
			// Full diamond armor (worn armor only counts once the player ticks): the user would take far less
			// from the hit itself than the Stand passes on.
			user.getAttribute(Attributes.ARMOR).setBaseValue(20);
			user.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Star Platinum: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user && stand.requiresUser(),
					"Summoned Star Platinum is missing or not health-linked");
			stand.summonLockTicks = 0;
			stand.setCanFollowUser(false);
			stand.setNoGravity(true);
			stand.moveTo(origin.x, origin.y, origin.z + 1, 0, 0);
			stand.setYHeadRot(0);
			// A weak Stand (resistance 0.5): it passes half of the hit on.
			user.getAttribute(ModEntityAttributes.STAND_STRENGTH).setBaseValue(4);
			user.getAttribute(ModEntityAttributes.STAND_DURABILITY).setBaseValue(4);
			StandEntity.updateStandStatAttributes(stand, user);
			stand.updateStrengthMultipliers();

			// Behind the Stand, so it neither guards nor auto-guards.
			attacker = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(attacker != null, "Could not create the attacker");
			attacker.setNoAi(true);
			attacker.setNoGravity(true);
			attacker.moveTo(origin.x, origin.y, origin.z - 0.5, 0, 0);
			helper.assertTrue(helper.getLevel().addFreshEntity(attacker), "Could not add the attacker");
			helper.assertTrue(!stand.canBlockFromAngle(attacker.position()) && !stand.isStandBlocking(),
					"The Stand must be hit from behind, unguarded");

			float resisted = HIT * (1 - StandStatFormulas.getPhysicalResistance(stand.getDurability(), stand.getAttackDamage(), 0, HIT));
			helper.assertTrue(Math.abs(resisted - 5) < 0.01F, "Unexpected Stand-resisted amount " + resisted);

			// No cooldown on the user.
			hitStand(helper, user, stand, attacker, 0, 0, resisted, "a user out of cooldown");
			// The user is still in the cooldown of a bigger hit of their own.
			hitStand(helper, user, stand, attacker, 15, 20, resisted, "a user in their own hurt cooldown");

			// 1.16 link damage was bypassMagic: neither the user's effects nor their enchantments cut it
			helper.assertTrue(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_HEALTH_LINK).is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
					&& DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_HEALTH_LINK).is(DamageTypeTags.BYPASSES_EFFECTS),
					"The Stand health link damage type is not tagged to bypass enchantments and effects, as 1.16 bypassMagic did");
			Holder<Enchantment> protection = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
					.getOrThrow(Enchantments.PROTECTION);
			ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			chestplate.enchant(protection, 4);
			user.setItemSlot(EquipmentSlot.CHEST, chestplate);
			hitStand(helper, user, stand, attacker, 0, 0, resisted, "a user in Protection IV");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			if (attacker != null) {
				attacker.discard();
			}
			user.discard();
		}
	}

	private static void hitStand(GameTestHelper helper, Player user, StandEntity stand, Cow attacker,
			int userInvulTime, float userLastHurt, float resisted, String scene) {
		user.setHealth(100);
		user.invulnerableTime = userInvulTime;
		user.lastHurt = userLastHurt;
		stand.invulnerableTime = 0;
		float userHealth = user.getHealth();
		DamageSource source = DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK, attacker);
		helper.assertTrue(stand.hurt(source, HIT), "The hit on the Stand was refused (" + scene + ")");
		float passed = userHealth - user.getHealth();
		helper.assertTrue(Math.abs(passed - resisted) < 0.01F, "The Stand passed " + passed
				+ " to " + scene + " in diamond armor; 1.16 passed the Stand-resisted " + resisted);
		helper.assertTrue(user.invulnerableTime == userInvulTime && Math.abs(user.lastHurt - userLastHurt) < 0.001F,
				"The linked hit changed the hurt cooldown of " + scene + ": invulnerableTime " + userInvulTime + " -> "
						+ user.invulnerableTime + ", lastHurt " + userLastHurt + " -> " + user.lastHurt);
	}
}
