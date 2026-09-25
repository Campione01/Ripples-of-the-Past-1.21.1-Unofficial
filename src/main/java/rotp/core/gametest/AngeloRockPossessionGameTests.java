package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.core.JojoMod;
import rotp.core.impl.stands.crazydiamond.AngeloRockEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.network.c2s.ClAngeloRockButtonPacket;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 Angelo rock and possession exits: the rock is pickable and only player melee mines it, leaving a rock kills
 * the sealed player (rockBroken / rockRespawn), and Pillar Man hiding ends on a new Shift press.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AngeloRockPossessionGameTests {
	private AngeloRockPossessionGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void angeloRockIsPickableAndOnlyPlayerMeleeMinesIt(GameTestHelper helper) {
		RecordingPlayer miner = createPlayer(helper, "RockMiner", GameType.SURVIVAL);
		RecordingPlayer creative = createPlayer(helper, "RockCreative", GameType.CREATIVE);
		AngeloRockEntity mined = createRock(helper, new Vec3(1.5D, 2.0D, 1.5D));
		AngeloRockEntity creativeRock = createRock(helper, new Vec3(3.5D, 2.0D, 1.5D));
		try {
			helper.assertTrue(mined.isPickable(), "Angelo rock is not pickable, so the crosshair cannot target it");
			mined.setBlockDrops(List.of(new ItemStack(Items.COBBLESTONE)));
			creativeRock.setBlockDrops(List.of(new ItemStack(Items.COBBLESTONE)));

			helper.assertFalse(mined.hurt(miner.damageSources().playerAttack(miner), 1.0F),
					"Bare hand mined the Angelo rock");
			miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			helper.assertFalse(mined.hurt(miner.damageSources().arrow(null, miner), 1.0F),
					"A projectile from a pickaxe holder mined the Angelo rock (1.16 needed player melee)");
			// iron pickaxe on stone: 6 per hit, the rock breaks at 40
			for (int hit = 0; hit < 7; hit++) {
				helper.assertTrue(mined.hurt(miner.damageSources().playerAttack(miner), 1.0F),
						"Pickaxe melee did not mine the Angelo rock");
			}
			Vec3 minedPos = mined.position();
			mined.tick();
			helper.assertTrue(mined.isRemoved(), "Mined Angelo rock did not break");
			List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					mined.getBoundingBox().inflate(0.5D), item -> item.getItem().is(Items.COBBLESTONE));
			helper.assertTrue(drops.size() == 1, "Mined Angelo rock dropped " + drops.size() + " block stacks, expected 1");
			helper.assertTrue(Math.abs(drops.get(0).getY() - minedPos.y) < 0.25D,
					"Angelo rock drop spawned at y=" + drops.get(0).getY() + ", rock y=" + minedPos.y);

			helper.assertTrue(creativeRock.hurt(creative.damageSources().playerAttack(creative), 1.0F),
					"Creative melee did not break the Angelo rock");
			creativeRock.tick();
			helper.assertTrue(creativeRock.isRemoved(), "Creative hit did not break the Angelo rock");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
					creativeRock.getBoundingBox().inflate(4.0D, 256.0D, 4.0D),
					item -> item.getItem().is(Items.COBBLESTONE) && item.getX() > minedPos.x + 1.0D).isEmpty(),
					"Creative break dropped the rock blocks (1.16 DropMode.NONE)");
			helper.succeed();
		}
		finally {
			helper.getLevel().getEntitiesOfClass(ItemEntity.class, mined.getBoundingBox().inflate(8.0D, 256.0D, 8.0D))
					.forEach(ItemEntity::discard);
			mined.discard();
			creativeRock.discard();
			miner.discard();
			creative.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void brokenAngeloRockKillsSealedPlayerAsRockBroken(GameTestHelper helper) {
		RecordingPlayer sealed = createPlayer(helper, "RockSealed", GameType.SURVIVAL);
		AngeloRockEntity rock = createRock(helper, new Vec3(1.5D, 2.0D, 1.5D));
		try {
			LivingComponentPossession.setPossessionTarget(sealed, rock, "angelo_rock");
			helper.assertTrue(sealed.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "Sealed player is not a spectator");
			sealed.invulnerableTime = 20;

			rock.breakRock();
			rock.tick();
			helper.assertTrue(rock.isRemoved(), "Broken Angelo rock was not removed");
			possession(sealed).tick();

			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(sealed), "Possession outlived the broken rock");
			assertSingleRockKill(helper, sealed, ModDamageTypes.ROCK_BROKEN, "rockBroken");
			helper.succeed();
		}
		finally {
			rock.discard();
			sealed.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void angeloRockRespawnButtonKillsAsRockRespawnOnly(GameTestHelper helper) {
		RecordingPlayer sealed = createPlayer(helper, "RockRespawn", GameType.SURVIVAL);
		AngeloRockEntity rock = createRock(helper, new Vec3(1.5D, 2.0D, 1.5D));
		try {
			LivingComponentPossession.setPossessionTarget(sealed, rock, "angelo_rock");
			sealed.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 4));
			sealed.invulnerableTime = 20;

			ClAngeloRockButtonPacket.handleOnServer(sealed, ClAngeloRockButtonPacket.respawn());

			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(sealed), "Respawn button kept the possession");
			helper.assertTrue(!sealed.resistanceAtHurt.isEmpty() && !sealed.resistanceAtHurt.get(0),
					"Resistance was still active at the Respawn kill");
			assertSingleRockKill(helper, sealed, ModDamageTypes.ROCK_RESPAWN, "rockRespawn");
			helper.succeed();
		}
		finally {
			rock.discard();
			sealed.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void pillarmanHideLeavesOnNewSneakAndBlocksTeleport(GameTestHelper helper) {
		RecordingPlayer hider = createPlayer(helper, "PillarmanHider", GameType.SURVIVAL);
		Pig host = helper.spawn(EntityType.PIG, new Vec3(1.5D, 2.0D, 1.5D));
		Pig other = helper.spawn(EntityType.PIG, new Vec3(4.5D, 2.0D, 1.5D));
		AngeloRockEntity rock = createRock(helper, new Vec3(3.5D, 2.0D, 3.5D));
		host.setNoAi(true);
		other.setNoAi(true);
		try {
			hider.setShiftKeyDown(true);
			LivingComponentPossession.setPossessionTarget(hider, host, LivingComponentPossession.PILLARMAN_HIDE_IN_ENTITY);
			LivingComponentPossession data = possession(hider);
			data.tick();
			helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(hider) == host,
					"Shift already held when hiding ended the possession");

			int stopRidingBefore = hider.stopRidingCalls;
			hider.teleportTo(helper.getLevel(), other.getX(), other.getY(), other.getZ(), 0.0F, 0.0F);
			helper.assertTrue(hider.stopRidingCalls == stopRidingBefore, "Spectator teleport ran while possessing");
			hider.setCamera(other);
			helper.assertTrue(hider.getCamera() == hider, "Server camera switched to another entity while possessing");

			hider.setShiftKeyDown(false);
			data.tick();
			helper.assertTrue(LivingComponentPossession.isPossessingSomeone(hider), "Releasing Shift ended the possession");
			hider.setShiftKeyDown(true);
			data.tick();
			helper.assertTrue(!LivingComponentPossession.isPossessingSomeone(hider), "A new Shift press did not leave the host");
			helper.assertTrue(hider.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "Leaving the host did not restore the game mode");
			helper.assertTrue(hider.hurtSources.isEmpty(), "Leaving a Pillar Man host hurt the player");

			stopRidingBefore = hider.stopRidingCalls;
			hider.teleportTo(helper.getLevel(), other.getX(), other.getY(), other.getZ(), 0.0F, 0.0F);
			helper.assertTrue(hider.stopRidingCalls == stopRidingBefore + 1, "Teleport probe did not run without possession");

			// Shift does not leave an Angelo rock (1.16 cancelled that camera reset)
			hider.setShiftKeyDown(false);
			LivingComponentPossession.setPossessionTarget(hider, rock, "angelo_rock");
			data.tick();
			hider.setShiftKeyDown(true);
			data.tick();
			helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(hider) == rock, "Shift released a player from an Angelo rock");
			helper.assertTrue(hider.hurtSources.isEmpty(), "Shift in an Angelo rock hurt the player");
			helper.succeed();
		}
		finally {
			rock.discard();
			host.discard();
			other.discard();
			hider.discard();
		}
	}

	private static void assertSingleRockKill(GameTestHelper helper, RecordingPlayer player,
			ResourceKey<DamageType> expected, String msgId) {
		helper.assertTrue(player.hurtSources.size() == 1, "Expected one rock kill, got " + player.hurtSources.size());
		DamageSource source = player.hurtSources.get(0);
		helper.assertTrue(source.typeHolder().is(expected), "Rock kill used " + source.typeHolder().getRegisteredName());
		helper.assertTrue(msgId.equals(source.type().msgId()), "Rock kill death message id is " + source.type().msgId());
		helper.assertTrue(source.is(DamageTypeTags.BYPASSES_ARMOR) && source.is(DamageTypeTags.BYPASSES_INVULNERABILITY),
				"Rock kill does not bypass armor and invulnerability");
		helper.assertTrue(player.hurtAmounts.get(0) == Float.MAX_VALUE, "Rock kill was not lethal");
		helper.assertTrue(player.invulnerableAtHurt.get(0) == 0, "Invulnerability ticks were not cleared before the rock kill");
		helper.assertTrue(player.gameModeAtHurt.get(0) == GameType.SURVIVAL,
				"Rock kill hit the player in " + player.gameModeAtHurt.get(0) + ", not the restored game mode");
	}

	private static LivingComponentPossession possession(RecordingPlayer player) {
		return player.getData(ModDataAttachmentTypes.ENTITY_POSSESSION.get());
	}

	private static AngeloRockEntity createRock(GameTestHelper helper, Vec3 relativePos) {
		AngeloRockEntity rock = new AngeloRockEntity(ModEntityTypes.ANGELO_ROCK.get(), helper.getLevel());
		Vec3 pos = helper.absoluteVec(relativePos);
		rock.setPos(pos.x, pos.y, pos.z);
		helper.assertTrue(helper.getLevel().addFreshEntity(rock), "Could not add Angelo rock");
		return rock;
	}

	private static RecordingPlayer createPlayer(GameTestHelper helper, String name, GameType gameType) {
		RecordingPlayer player = new RecordingPlayer(helper.getLevel(), name);
		player.setGameMode(gameType);
		player.setNoGravity(true);
		player.setPos(helper.absoluteVec(new Vec3(2.5D, 2.0D, 2.5D)));
		helper.assertTrue(helper.getLevel().addFreshEntity(player), "Could not add test player");
		return player;
	}

	/** FakePlayer ignores damage; this records each hit and treats a lethal one as death. */
	private static final class RecordingPlayer extends FakePlayer {
		final List<DamageSource> hurtSources = new ArrayList<>();
		final List<Float> hurtAmounts = new ArrayList<>();
		final List<Integer> invulnerableAtHurt = new ArrayList<>();
		final List<GameType> gameModeAtHurt = new ArrayList<>();
		final List<Boolean> resistanceAtHurt = new ArrayList<>();
		int stopRidingCalls;

		RecordingPlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public boolean hurt(DamageSource source, float amount) {
			hurtSources.add(source);
			hurtAmounts.add(amount);
			invulnerableAtHurt.add(invulnerableTime);
			gameModeAtHurt.add(gameMode.getGameModeForPlayer());
			resistanceAtHurt.add(hasEffect(MobEffects.DAMAGE_RESISTANCE));
			if (amount >= Float.MAX_VALUE) {
				setHealth(0.0F);
			}
			return true;
		}

		@Override
		public void stopRiding() {
			stopRidingCalls++;
			super.stopRiding();
		}
	}
}
