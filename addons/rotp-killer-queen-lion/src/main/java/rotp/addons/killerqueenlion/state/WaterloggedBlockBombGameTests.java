package rotp.addons.killerqueenlion.state;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.killerqueenlion.KillerQueenLionAddon;
import rotp.addons.killerqueenlion.ability.BlockBombDetonateAbility;
import rotp.core.init.ModGamerules;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;

@GameTestHolder(KillerQueenLionAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaterloggedBlockBombGameTests {
    private WaterloggedBlockBombGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_addon_api_smoke", timeoutTicks = 20)
    public static void waterloggedSolidKeepsAndDetonatesItsBlockBomb(GameTestHelper helper) {
        var level = helper.getLevel();
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "BlockBombProbe"));
        owner.setPos(helper.absoluteVec(new Vec3(1, 2, 1)));
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 3));
        var original = level.getBlockState(position);
        boolean oldBreakRule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
        try {
            level.addNewPlayer(owner);
            var power = PowerClass.STAND.attachGet(owner);
            power.setStand(KillerQueenLionAddon.KILLER_QUEEN.get());
            power.setResolveLevel(4);
            power.setStamina(power.getMaxStamina());
            helper.assertTrue(KillerQueenLionAddon.KILLER_QUEEN.get().summon(owner, power),
                    "Could not summon the registered bomb caster");
            var stand = power.getSummonedStandEntity();
            helper.assertTrue(stand != null, "Missing bomb Stand");
            var dry = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false);
            level.setBlock(position, dry, 2);
            helper.assertTrue(KillerQueenStateService.markBlockBomb(owner, position)
                            && position.equals(KillerQueenStateService.blockBombPos(owner)),
                    "Dry stairs could not retain a block bomb");
            level.setBlock(position, dry.setValue(BlockStateProperties.WATERLOGGED, true), 2);
            helper.assertTrue(position.equals(KillerQueenStateService.blockBombPos(owner)),
                    "Waterlogging a solid block lost its bomb");
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(true, level.getServer());
            var ability = (BlockBombDetonateAbility) power.getAbility(KillerQueenLionAddon.BLOCK_BOMB_DETONATE_NAME);
            helper.assertTrue(ability != null, "Missing registered block detonation ability");
            var action = ability.initActionOnAbilityUse(level, owner, stand, null);
            action.setPhaseStart(ActionPhase.PERFORM);
            var component = LivingComponentAction.getComponent(stand);
            component.setAction(action, owner, SyncType.NO_SYNC);
            helper.assertTrue(component.getAction() == action, "Detonation action was rejected");
            action._tickAction();
            helper.assertTrue(!level.getBlockState(position).is(Blocks.OAK_STAIRS)
                            && KillerQueenStateService.blockBombPos(owner) == null,
                    "Waterlogged stairs were not destroyed and the marker cleared");
            component.setAction(null, owner, SyncType.NO_SYNC);
            for (var forbidden : new net.minecraft.world.level.block.Block[] {Blocks.WATER, Blocks.LAVA}) {
                level.setBlock(position, forbidden.defaultBlockState(), 2);
                KillerQueenStateService.markBlockBomb(owner, position);
                helper.assertTrue(KillerQueenStateService.blockBombPos(owner) == null,
                        "Pure liquid unexpectedly retained a bomb");
                KillerQueenStateService.clearBlockBomb(owner);
            }
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(!KillerQueenStateService.markBlockBomb(owner, position)
                            && KillerQueenStateService.blockBombPos(owner) == null,
                    "Air unexpectedly accepted a bomb");
            helper.succeed();
        } finally {
            KillerQueenStateService.clearBlockBomb(owner);
            var power = PowerClass.STAND.get(owner);
            if (power != null && power.hasPower()) {
                var stand = power.getSummonedStandEntity();
                if (stand != null) {
                    LivingComponentAction.getComponent(stand).setAction(null, owner, SyncType.NO_SYNC);
                }
                power.getPowerType().forceUnsummon(owner, power);
            }
            level.setBlock(position, original, 2);
            level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(oldBreakRule, level.getServer());
            owner.getInventory().clearContent();
            owner.discard();
        }
    }
}
