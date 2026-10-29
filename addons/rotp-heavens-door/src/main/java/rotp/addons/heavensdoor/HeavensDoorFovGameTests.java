package rotp.addons.heavensdoor;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.heavensdoor.client.CannotMoveFovRules;
import rotp.addons.heavensdoor.network.BookCommand;
import rotp.addons.heavensdoor.state.WrittenCommandService;

@GameTestHolder(HeavensDoorAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavensDoorFovGameTests {
    private HeavensDoorFovGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cannotMoveVisualSpeedPreservesSprintAndAllForeignAttributeOperations(GameTestHelper helper) {
        ServerPlayer player = HeavensDoorOwnerRulesGameTests.owner(helper, "HdFovSpeed");
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        try {
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            player.setSprinting(true);
            speed.addTransientModifier(new AttributeModifier(HeavensDoorAddon.id("fov_test_flat"), 0.025D, AttributeModifier.Operation.ADD_VALUE));
            speed.addTransientModifier(new AttributeModifier(HeavensDoorAddon.id("fov_test_base"), 0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            speed.addTransientModifier(new AttributeModifier(HeavensDoorAddon.id("fov_test_total"), 0.25D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            double normal = speed.getValue();
            double base = speed.getBaseValue();
            helper.assertTrue(CannotMoveFovRules.movementSpeedForFov(player, normal) == normal, "Unrelated player did not retain original FOV speed input");
            helper.assertTrue(WrittenCommandService.apply(writer, player, BookCommand.FREEZE), "FOV fixture did not apply actual Cannot Move");
            var modifiers = speed.getModifiers();
            helper.assertTrue(speed.getValue() == 0.0D && Math.abs(CannotMoveFovRules.movementSpeedForFov(player, 0.0D) - normal) < 1.0E-9D,
                    "Cannot Move visual speed lost sprint/flat/base/total modifiers or retained its own zero-speed term");
            helper.assertTrue(speed.getValue() == 0.0D && speed.getBaseValue() == base && speed.getModifiers().equals(modifiers),
                    "FOV compensation mutated live movement attributes");
            player.removeEffect(HeavensDoorAddon.CANNOT_MOVE);
            helper.assertTrue(Math.abs(speed.getValue() - normal) < 1.0E-9D
                            && CannotMoveFovRules.movementSpeedForFov(player, normal) == normal, "Cannot Move removal changed foreign modifiers/FOV input");
            helper.succeed();
        }
        finally {
            writer.discard();
            HeavensDoorOwnerRulesGameTests.close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void heldWalkModifierWithoutTheEffectStillKeepsTheNormalView(GameTestHelper helper) {
        ServerPlayer player = HeavensDoorOwnerRulesGameTests.owner(helper, "HdFovRelease");
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        try {
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            player.setSprinting(true);
            double normal = speed.getValue();
            helper.assertTrue(WrittenCommandService.apply(writer, player, BookCommand.FREEZE), "FOV fixture did not apply actual Cannot Move");
            AttributeModifier held = speed.getModifier(HeavensDoorAddon.id("cannot_move_walk"));
            helper.assertTrue(held != null, "Cannot Move did not add its held walk-speed modifier");
            player.removeEffect(HeavensDoorAddon.CANNOT_MOVE);
            // A client sees the effect removal before the attribute sync, and a spectator camera never sees the effect.
            speed.addTransientModifier(held);
            helper.assertTrue(!player.hasEffect(HeavensDoorAddon.CANNOT_MOVE) && speed.getValue() == 0.0D
                            && Math.abs(CannotMoveFovRules.movementSpeedForFov(player, 0.0D) - normal) < 1.0E-9D,
                    "Held walk-speed modifier without the effect zoomed the view in");
            helper.succeed();
        }
        finally {
            writer.discard();
            HeavensDoorOwnerRulesGameTests.close(player);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void visualSpeedDoesNotUndoAForeignZeroOrRewriteUnrelatedInputs(GameTestHelper helper) {
        ServerPlayer player = HeavensDoorOwnerRulesGameTests.owner(helper, "HdFovForeign");
        Cow writer = helper.spawnWithNoFreeWill(EntityType.COW, 1, 2, 2);
        try {
            var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            var foreign = HeavensDoorAddon.id("fov_test_foreign_zero");
            speed.addTransientModifier(new AttributeModifier(foreign, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            helper.assertTrue(speed.getValue() == 0.0D && CannotMoveFovRules.movementSpeedForFov(player, 0.37D) == 0.37D,
                    "An unrelated foreign-zero player had its original FOV input replaced");
            helper.assertTrue(WrittenCommandService.apply(writer, player, BookCommand.FREEZE), "FOV fixture failed");
            var modifiers = speed.getModifiers();
            helper.assertTrue(CannotMoveFovRules.movementSpeedForFov(player, 0.0D) == 0.0D
                            && speed.getValue() == 0.0D && speed.getModifiers().equals(modifiers) && speed.hasModifier(foreign),
                    "Visual speed erased a foreign zero-speed contribution or temporarily changed the live attribute");
            helper.succeed();
        }
        finally {
            writer.discard();
            HeavensDoorOwnerRulesGameTests.close(player);
        }
    }
}
