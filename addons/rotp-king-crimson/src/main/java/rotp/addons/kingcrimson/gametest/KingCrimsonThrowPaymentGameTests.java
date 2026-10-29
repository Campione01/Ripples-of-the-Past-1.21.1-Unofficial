package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.gametest.KingCrimsonThrowGameTestFixture.Mode;
import rotp.core.powersystem.entityaction.ActionPhase;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowPaymentGameTests {
    private static PaymentSuite active;
    private KingCrimsonThrowPaymentGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2500, batch = "kc_throw_01")
    public static void throwPaysAfterNormalPerformWithoutAffordabilityVeto(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowPaymentGameTests.class);
        helper.assertTrue(active == null, "No overlapping throw payment suite");
        PaymentSuite suite = new PaymentSuite(helper); active = suite;
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    @AfterBatch(batch = "kc_throw_01")
    public static void cleanupPayment(ServerLevel level) { if (active != null) active.close(); }

    private static final class PaymentSuite extends KingCrimsonThrowGameTestFixture.Suite {
        int kind;
        boolean triple;
        Mode mode;
        PaymentSuite(GameTestHelper helper) { super(helper, "D-KC-THROW-01", 28); }
        @Override void prepare(int index) {
            kind = index % 14; triple = index >= 14;
            mode = switch (kind) {
                case 8 -> Mode.CREATIVE;
                case 9 -> Mode.STAMINA_OFF;
                case 10, 11 -> Mode.RESOLVE;
                default -> Mode.FINITE;
            };
            float stamina = switch (kind) { case 0, 11 -> 0; case 1 -> 119; case 2 -> 120; default -> 600; };
            scene = new KingCrimsonThrowGameTestFixture(helper, "payment/" + (triple ? "triple/" : "single/") + kind, mode, stamina, 73);
            int ammo = kind == 12 ? 2 : kind == 2 ? 1 : 3;
            scene.hand(new ItemStack(kind == 13 ? Items.FIRE_CHARGE : Items.ARROW, ammo));
            scene.aim(23, -17);
            var press = scene.press(triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE,
                    KingCrimsonThrowGameTestFixture.FIRST);
            checks.check(press.admitted() && !press.buffered(), scene.id + " registered condition-checked CLICK admits at stamina=" + scene.inputStamina);
            if (!press.admitted()) { done = true; return; }
            scene.premise(press.action() != null && press.action().getPhase() == ActionPhase.WINDUP, "Real positive windup");
            checks.check(KingCrimsonThrowGameTestFixture.near(scene.power.getStamina(), scene.inputStamina), scene.id + " no admission debit");
            scene.duringWindup = () -> {
                scene.aim(triple ? -61 : 91, kind % 2 == 0 ? -33 : 19);
                if (kind == 6) scene.hand(ItemStack.EMPTY);
                if (kind == 7) scene.hand(new ItemStack(Items.DIRT, 3));
            };
            if (kind == 4 || kind == 5) scene.beforePerform = () -> scene.power.setStamina(kind == 4 ? 0 : 119);
        }
        @Override boolean verify() {
            if (!scene.performed) return false;
            scene.premise(scene.windupChanged, "Windup mutation after native tick");
            scene.checkPayment(checks, true);
            if (kind == 4 || kind == 5) scene.premise(scene.preStamina == (kind == 4 ? 0 : 119), "Late shortage immediately before native perform");
            if (kind == 0 || kind == 11) scene.premise(scene.preStamina < 120, "Regeneration did not mask zero-stamina premise");
            int expected = kind == 6 || kind == 7 ? 0 : kind == 13 ? 1 : Math.min(scene.preAmmo, triple ? 3 : 1);
            scene.checkSpawns(checks, expected, kind == 13 ? LargeFireball.class : Arrow.class);
            checks.check(scene.postAmmo == scene.preAmmo - (mode == Mode.CREATIVE ? 0 : expected), scene.id + " exact live ammo / Creative retention");
            return true;
        }
        @Override void release() { if (active == this) active = null; }
    }
}
