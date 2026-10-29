package rotp.addons.achtungbaby.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.addons.achtungbaby.AchtungBabyService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

/**
 * Placing an invisible item records where it went, so breaking it hands an invisible item back.
 *
 * <p><b>What 1.16 actually did here, because it is not what its file names suggest.</b> The donor has
 * BlockItemMixin, InvisibleBlockManager, ClientInvisibleBlockManager, InvisibleBlockSyncPacket and
 * InvisibleBlockRenderer - and read from its own bytecode, most of it never ran:
 * InvisibleBlockRenderer declares nothing but a constructor, InvisibleBlockEventHandler's
 * {@code syncInvisibleBlockToClient} body is a bare {@code return}, InvisibleBlockSyncPacket is referenced by no
 * other class in the jar (never registered, never sent), and BlockItemMixin's own sync call sits behind
 * {@code world.isClientSide()} inside a branch already guarded by {@code !world.isClientSide()}. <b>A placed
 * "invisible block" was never invisible in 1.16 either.</b>
 *
 * <p>So this ports the half that worked: the level remembers the position, and
 * {@code AchtungBabyGameEvents.returnInvisibleBlockDrops} re-tags the drops when it breaks. Making the block itself
 * invisible would be inventing a mechanic the donor never shipped, not restoring a lost one.
 *
 * <p>Two details 1.16 got wrong and this does not. The injection point is the {@code ItemStack.consume} call, not
 * {@code RETURN}: {@code place} consumes the placed item before it returns, so reading the stack at RETURN sees an
 * empty stack whenever the player placed their last one, and the position would go unrecorded. And
 * {@code BlockPlaceContext.getClickedPos} already resolves to where the block actually goes - 1.16 offset the clicked
 * position by the clicked face itself, which lands on the wrong block whenever the clicked block was replaceable.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(
            method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)"
                    + "Lnet/minecraft/world/InteractionResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;"
                            + "consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
    private void rotp_ab$recordInvisibleBlock(
            BlockPlaceContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel() instanceof ServerLevel level
                && AchtungBabyService.isInvisible(context.getItemInHand())) {
            AchtungBabyService.markBlockInvisible(level, context.getClickedPos());
        }
    }
}
