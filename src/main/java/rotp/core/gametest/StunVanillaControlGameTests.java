package rotp.core.gametest;

import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;
import rotp.core.mechanics.StunEffect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 GameplayEventHandler stun rules: a stunned player stops sprinting, every cancellable interaction fails and
 * item pickup is refused; a mob converted while stunned does not keep the stun's NoAI (releaseStun).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StunVanillaControlGameTests {

	private StunVanillaControlGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void stunnedPlayerCannotSprintInteractOrPickUp(GameTestHelper helper) {
		Player player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		Pig target = EntityType.PIG.create(helper.getLevel());
		helper.assertTrue(target != null, "fixture: could not create the target pig");
		ItemEntity item = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
				new ItemStack(Items.STICK));

		Probe free = Probe.run(player, pos, target, item);
		helper.assertTrue(free.none(), "Without a stun nothing may be blocked: " + free);

		player.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
		helper.assertTrue(ModStatusEffects.isStunned(player), "fixture: the player did not get stunned");
		Probe stunned = Probe.run(player, pos, target, item);
		helper.assertTrue(stunned.all(), "A stunned player must lose sprint, interaction and pickup: " + stunned);

		player.removeEffect(ModStatusEffects.STUN);
		Probe released = Probe.run(player, pos, target, item);
		helper.assertTrue(released.none(), "After the stun ends nothing may be blocked: " + released);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void mobConvertedWhileStunnedGetsItsAiBack(GameTestHelper helper) {
		// convertTo path (zombie villager cure, drowning): the discard clears the old mob's effects before the event.
		Pig stunned = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
		stunned.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
		helper.assertTrue(stunned.isNoAi(), "fixture: the stun did not set NoAI: " + state(stunned, null));
		ZombifiedPiglin outcome = stunned.convertTo(EntityType.ZOMBIFIED_PIGLIN, false);
		helper.assertTrue(outcome != null && outcome.isNoAi() && !ModStatusEffects.isStunned(outcome)
				&& stunned.isRemoved(),
				"fixture: convertTo must copy NoAI but not the stun, and discard the pig: " + state(stunned, outcome));
		EventHooks.onLivingConvert(stunned, outcome);
		helper.assertTrue(!outcome.isNoAi(),
				"A mob converted by convertTo while stunned must not stay frozen: " + state(stunned, outcome));

		// Manual path (pig or villager struck by lightning, tadpole): the event fires before the old mob is discarded.
		Pig struck = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 1));
		struck.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 200));
		ZombifiedPiglin replaced = EntityType.ZOMBIFIED_PIGLIN.create(helper.getLevel());
		helper.assertTrue(replaced != null, "fixture: could not create the replacing piglin");
		replaced.setNoAi(struck.isNoAi());
		helper.assertTrue(replaced.isNoAi() && ModStatusEffects.isStunned(struck),
				"fixture: the struck pig must be stunned and its NoAI copied: " + state(struck, replaced));
		EventHooks.onLivingConvert(struck, replaced);
		struck.discard();
		helper.assertTrue(!replaced.isNoAi(),
				"A mob replaced while stunned (event before discard) must not stay frozen: " + state(struck, replaced));

		Pig frozen = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 1));
		frozen.setNoAi(true);
		ZombifiedPiglin kept = frozen.convertTo(EntityType.ZOMBIFIED_PIGLIN, false);
		helper.assertTrue(kept != null, "fixture: the second conversion failed: " + state(frozen, null));
		EventHooks.onLivingConvert(frozen, kept);
		helper.assertTrue(kept.isNoAi(),
				"A NoAI mob that was not stunned must keep NoAI after converting: " + state(frozen, kept));

		outcome.discard();
		kept.discard();
		helper.succeed();
	}

	private static String state(Mob old, Mob outcome) {
		String result = outcome == null ? "null"
				: "noAi=" + outcome.isNoAi() + " stunned=" + ModStatusEffects.isStunned(outcome);
		return "old[noAi=" + old.isNoAi() + " stunned=" + ModStatusEffects.isStunned(old) + " removed="
				+ old.isRemoved() + " discardMarker=" + StunEffect.wasStunnedOnDiscard(old) + "] outcome[" + result + "]";
	}

	private record Probe(boolean sprintStopped, boolean useBlockFailed, boolean useItemFailed,
			boolean interactFailed, boolean interactAtFailed, boolean hitBlockCancelled, boolean pickupDenied) {

		static Probe run(Player player, BlockPos pos, Entity target, ItemEntity item) {
			player.setSprinting(true);
			NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
			boolean sprintStopped = !player.isSprinting();
			player.setSprinting(false);

			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
			var useBlock = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
			NeoForge.EVENT_BUS.post(useBlock);
			var useItem = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
			NeoForge.EVENT_BUS.post(useItem);
			var interact = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, target);
			NeoForge.EVENT_BUS.post(interact);
			var interactAt = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND,
					target, Vec3.ZERO);
			NeoForge.EVENT_BUS.post(interactAt);
			var hitBlock = new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP,
					PlayerInteractEvent.LeftClickBlock.Action.START);
			NeoForge.EVENT_BUS.post(hitBlock);
			var pickup = new ItemEntityPickupEvent.Pre(player, item);
			NeoForge.EVENT_BUS.post(pickup);

			return new Probe(sprintStopped,
					useBlock.isCanceled() && useBlock.getCancellationResult() == InteractionResult.FAIL,
					useItem.isCanceled() && useItem.getCancellationResult() == InteractionResult.FAIL,
					interact.isCanceled() && interact.getCancellationResult() == InteractionResult.FAIL,
					interactAt.isCanceled() && interactAt.getCancellationResult() == InteractionResult.FAIL,
					hitBlock.isCanceled(),
					pickup.canPickup() == TriState.FALSE);
		}

		boolean all() {
			return sprintStopped && useBlockFailed && useItemFailed && interactFailed && interactAtFailed
					&& hitBlockCancelled && pickupDenied;
		}

		boolean none() {
			return !sprintStopped && !useBlockFailed && !useItemFailed && !interactFailed && !interactAtFailed
					&& !hitBlockCancelled && !pickupDenied;
		}
	}
}
