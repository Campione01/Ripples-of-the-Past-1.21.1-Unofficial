package rotp.addons.killerqueenlion.state;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.core.powersystem.PowerClass;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockBombRangeGameTests {
    private BlockBombRangeGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_addon_api_smoke", timeoutTicks = 20)
    public static void blockBombRangeUsesOwnedStandAndLegacyCorner(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BombRangeProbe"));
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 3));
        Vec3 corner = Vec3.atLowerCornerOf(position);
        var original = level.getBlockState(position);
        try {
            owner.setPos(corner.add(1, 0, 0));
            level.addNewPlayer(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                    "Could not summon the range caster");
            var stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing owned Stand");
            level.setBlock(position, Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, position), "Initial mark failed");
            owner.setPos(corner.add(55, 0, 0));
            stand.setPos(corner.add(47, 0, 0));
            helper.assertTrue(KillerQueenAuthority.validController(owner, stand)
                            && position.equals(KillerQueenStateService.blockBombPos(owner)),
                    "Owner distance incorrectly rejected an in-range Stand");
            KillerQueenStateService.validateOwner(owner);
            helper.assertTrue(KillerQueenLionAddon.state(owner).hasBlockBomb(),
                    "Validation cleared the Stand's in-range bomb");

            KillerQueenStateService.clearBlockBomb(owner);
            owner.setPos(corner.add(1, 0, 0));
            stand.setPos(owner.position());
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, position), "Second mark failed");
            owner.setPos(corner.add(48, 0, 0));
            stand.setPos(corner.add(56, 0, 0));
            helper.assertTrue(KillerQueenStateService.blockBombPos(owner) == null,
                    "Owner distance admitted an out-of-range Stand");
            KillerQueenStateService.validateOwner(owner);
            helper.assertTrue(!KillerQueenLionAddon.state(owner).hasBlockBomb(),
                    "Validation retained an out-of-range bomb");

            owner.setPos(corner.add(1, 0, 0));
            stand.setPos(owner.position());
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, position), "Edge mark failed");
            owner.setPos(corner.add(48, 0, 0));
            stand.setPos(corner.add(50, 0, 0));
            helper.assertTrue(position.equals(KillerQueenStateService.blockBombPos(owner)),
                    "Exactly50 from the block corner was rejected");
            stand.setPos(corner.add(50.001D, 0, 0));
            helper.assertTrue(KillerQueenStateService.blockBombPos(owner) == null,
                    "Beyond50 from the block corner was accepted");
            helper.succeed();
        } finally {
            KillerQueenStateService.clearBlockBomb(owner);
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) {
                power.getPowerType().forceUnsummon(owner, power);
            }
            level.setBlock(position, original, 2);
            owner.getInventory().clearContent();
            owner.discard();
        }
    }
}
