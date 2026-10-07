package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModItemDataComponents;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GEBodyTissueItem.use / GoldExperienceHeal.giveGEHealEffect: every tissue heal plays the Gold Experience heal
 * sound (1.0 volume, 0.95-1.05 pitch, AMBIENT), also for a tissue without creator data, whose power is null.
 * 1.16 GoldExperienceHealingItem.standPerform: a fish bucket used as the material releases its fish at the block of
 * StandAction.getControlledEntity (the Stand under manual control, otherwise the user), for any user.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceHealingItemGameTests {
	private GoldExperienceHealingItemGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void bodyTissueWithoutCreatorDataPlaysHealSound(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		player.moveTo(pos.x, pos.y, pos.z);
		helper.assertTrue(level.addFreshEntity(player), "Could not add the body tissue player");
		List<PlayLevelSoundEvent.AtPosition> heard = new ArrayList<>();
		Consumer<PlayLevelSoundEvent.AtPosition> listener = event -> {
			Holder<SoundEvent> sound = event.getSound();
			if (event.getLevel() == level && sound != null && sound.value() == ModSoundEvents.GOLD_EXPERIENCE_HEAL.get()
					&& event.getPosition().distanceToSqr(player.position()) < 0.01) {
				heard.add(event);
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, PlayLevelSoundEvent.AtPosition.class, listener);
		try {
			// the creator left the world: 1.16 consumes the tissue without a heal or a sound
			player.setHealth(10.0F);
			ItemStack orphan = new ItemStack(ModItems.GE_BODY_TISSUE.get());
			orphan.set(ModItemDataComponents.GE_USER.get(), UUID.fromString("7c1e0b48-0000-4000-8000-00000000b051"));
			player.setItemInHand(InteractionHand.MAIN_HAND, orphan);
			InteractionResultHolder<ItemStack> orphanUse = orphan.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(orphanUse.getResult() == InteractionResult.CONSUME && orphan.isEmpty(),
					"A tissue whose creator is gone was not consumed: " + orphanUse.getResult() + " " + orphan);
			helper.assertTrue(!player.hasEffect(MobEffects.REGENERATION) && heard.isEmpty(),
					"A tissue whose creator is gone healed or played the heal sound " + heard.size() + " time(s)");

			// no creator data (/give, Creative): 1.16 heals with a null power and still plays the heal sound
			ItemStack tissue = new ItemStack(ModItems.GE_BODY_TISSUE.get());
			player.setItemInHand(InteractionHand.MAIN_HAND, tissue);
			InteractionResultHolder<ItemStack> use = tissue.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(use.getResult() == InteractionResult.CONSUME && tissue.isEmpty(),
					"The body tissue was not consumed: " + use.getResult() + " " + tissue);
			MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
			helper.assertTrue(regen != null && regen.getAmplifier() == 0 && regen.getDuration() <= 100,
					"The body tissue did not give its 100-tick Regeneration I: " + regen);
			helper.assertTrue(heard.size() == 1,
					"1.16 giveGEHealEffect: a body tissue without creator data plays the Gold Experience heal sound once, but it played "
							+ heard.size() + " time(s)");
			PlayLevelSoundEvent.AtPosition sound = heard.get(0);
			helper.assertTrue(sound.getSource() == SoundSource.AMBIENT && sound.getOriginalVolume() == 1.0F
					&& sound.getOriginalPitch() >= 0.95F && sound.getOriginalPitch() <= 1.05F,
					"The heal sound lost its 1.16 values: source=" + sound.getSource() + " volume=" + sound.getOriginalVolume()
							+ " pitch=" + sound.getOriginalPitch());
			helper.succeed();
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void fishBucketMaterialIsReleasedAtManuallyControlledStand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GEHealingItemFish"));
		Scene scene = new Scene(helper, user);
		try {
			scene.start();
			Vec3 standPos = scene.userPos.add(0.0D, 0.0D, 3.0D);

			// not manually controlled: the fish appears at the user
			Cod atUser = scene.makeTissueFromCodBucket(standPos, false);
			helper.assertTrue(atUser.blockPosition().equals(user.blockPosition()),
					"1.16 getControlledEntity: without manual control the fish is released at the user " + user.blockPosition()
							+ ", but it appeared at " + atUser.blockPosition());
			helper.assertTrue(user.getOffhandItem().isEmpty(), "The fish bucket was not spent: " + user.getOffhandItem());
			helper.assertTrue(user.getInventory().countItem(ModItems.GE_BODY_TISSUE.get()) == 1,
					"The user did not receive one body tissue");
			atUser.discard();

			// manually controlled: the fish appears at the Stand
			Cod atStand = scene.makeTissueFromCodBucket(standPos, true);
			helper.assertTrue(!scene.stand.blockPosition().equals(user.blockPosition()),
					"Fixture: the Stand is in the user's block");
			helper.assertTrue(atStand.blockPosition().equals(scene.stand.blockPosition()),
					"1.16 getControlledEntity: under manual control the fish is released at the Stand "
							+ scene.stand.blockPosition() + ", but it appeared at " + atStand.blockPosition()
							+ " (user " + user.blockPosition() + ")");
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void fishBucketMaterialIsReleasedForNonPlayerUser(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie user = EntityType.ZOMBIE.create(level);
		helper.assertTrue(user != null, "Could not create the zombie user");
		user.setNoAi(true);
		user.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400));
		Scene scene = new Scene(helper, user);
		try {
			scene.start();
			Cod cod = scene.makeTissueFromCodBucket(scene.userPos.add(0.0D, 0.0D, 3.0D), false);
			helper.assertTrue(cod.blockPosition().equals(user.blockPosition()),
					"The fish of a non-player user appeared at " + cod.blockPosition() + " instead of " + user.blockPosition());
			helper.assertTrue(user.getOffhandItem().isEmpty(), "The fish bucket was not spent: " + user.getOffhandItem());
			helper.succeed();
		}
		finally {
			scene.close();
		}
	}

	/** A user with a summoned Gold Experience that turns an off-hand cod bucket into body tissue. */
	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final LivingEntity user;
		final Vec3 userPos;
		final AABB area;
		StandType standType;
		StandPower power;
		StandEntity stand;

		Scene(GameTestHelper helper, LivingEntity user) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.user = user;
			this.userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			this.area = new AABB(userPos, userPos).inflate(8.0D);
		}

		void start() {
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			helper.assertTrue(standType != null, "Missing Gold Experience Stand type");
			user.moveTo(userPos.x, userPos.y, userPos.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Gold Experience user");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED,
					"Could not grant Gold Experience: " + inserted.status());
			helper.assertTrue(standType.summon(user, power), "Could not summon Gold Experience");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Gold Experience entity is missing");
			// only StandEntity.tick counts the summon lock down, and the lock skips every action tick
			stand.summonLockTicks = 0;
			helper.assertTrue(level.getEntitiesOfClass(Cod.class, area).isEmpty(), "Fixture: a cod is already in the test area");
		}

		Cod makeTissueFromCodBucket(Vec3 standPos, boolean manualControl) {
			LivingComponentAction component = LivingComponentAction.getComponent(stand);
			component.setAction(null, user, SyncType.NO_SYNC);
			stand.setManuallyControlled(manualControl);
			helper.assertTrue(stand.isManuallyControlled() == manualControl, "Fixture: manual control is not " + manualControl);
			stand.moveTo(standPos.x, standPos.y, standPos.z, 0, 0);
			power.setStamina(power.getMaxStamina());
			user.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COD_BUCKET));
			Ability ability = power.getAbility("healing_item");
			helper.assertTrue(ability instanceof EntityActionType, "Gold Experience's healing item is not an entity action");
			EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(level, user, stand, null);
			component.setAction(action, user, SyncType.NO_SYNC);
			for (int tick = 0; tick < 60 && !user.getOffhandItem().isEmpty() && component.getAction() == action; tick++) {
				component.tick();
			}
			helper.assertTrue(user.getOffhandItem().isEmpty(),
					"Fixture: the healing item action did not spend the cod bucket [phase=" + action.getPhase()
							+ " phaseTick=" + action.getPhaseTick() + " current=" + (component.getAction() == action)
							+ " stamina=" + power.getStamina() + "]");
			List<Cod> cods = level.getEntitiesOfClass(Cod.class, area);
			helper.assertTrue(cods.size() == 1,
					"1.16 checkExtraContent: a cod bucket spent as healing material releases its cod, but " + cods.size()
							+ " cod(s) appeared (user " + user.getType() + ")");
			return cods.get(0);
		}

		void close() {
			try {
				if (power != null && standType != null && power.isSummoned()) {
					standType.forceUnsummon(user, power);
				}
			}
			finally {
				for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
						entity -> entity instanceof Cod || entity instanceof ItemEntity)) {
					entity.discard();
				}
				user.discard();
			}
		}
	}
}
