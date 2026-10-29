package rotp.addons.kingcrimson.gametest;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;

/**
 * 1.16 KingCrimsonProjectileThrow.createProjectile built its own entity only for the allowedProjectiles entries
 * (arrow, spectral arrow); every other ArrowItem became a plain ArrowEntity with setPotionEffect(stack), so only a
 * tipped arrow kept effects.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowArrowGameTests {
    private KingCrimsonThrowArrowGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 1600, batch = "kc_throw_arrow")
    public static void throwBuildsDonorArrows(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowArrowGameTests.class);
        var suite = new ArrowSuite(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }

    private static final class ArrowSuite extends KingCrimsonThrowGameTestFixture.Suite {
        enum Kind { ARROW, TIPPED, SPECTRAL_ALLOWED, SPECTRAL_EXCLUDED }
        Kind kind;
        boolean triple;
        ItemStack held;

        ArrowSuite(GameTestHelper helper) { super(helper, "KC-THROW-ARROW", 8); }

        @Override void prepare(int index) {
            kind = Kind.values()[index % 4]; triple = index >= 4;
            scene = new KingCrimsonThrowGameTestFixture(helper, "arrow/" + (triple ? "triple/" : "single/") + kind,
                    KingCrimsonThrowGameTestFixture.Mode.FINITE, 600, 73);
            held = switch (kind) {
                case ARROW -> new ItemStack(Items.ARROW, 5);
                case TIPPED -> PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON).copyWithCount(5);
                case SPECTRAL_ALLOWED, SPECTRAL_EXCLUDED -> new ItemStack(Items.SPECTRAL_ARROW, 5);
            };
            KingCrimsonConfig.ALLOWED_PROJECTILES.set(kind == Kind.SPECTRAL_EXCLUDED ? "arrow,snowball" : "arrow,spectral_arrow,snowball");
            scene.premise(KingCrimsonConfig.allowsProjectile(Items.SPECTRAL_ARROW) == (kind != Kind.SPECTRAL_EXCLUDED),
                    "Configured spectral arrow entry");
            scene.hand(held.copy());
            var press = scene.press(triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE,
                    KingCrimsonThrowGameTestFixture.FIRST);
            scene.premise(press.conditions() && press.admitted() && !press.buffered(), "Arrow throw admitted");
        }

        @Override boolean verify() {
            if (!scene.performed) return false;
            int count = triple ? 3 : 1;
            boolean spectral = kind == Kind.SPECTRAL_ALLOWED;
            scene.checkPayment(checks, true);
            scene.checkSpawns(checks, count, spectral ? SpectralArrow.class : Arrow.class);
            checks.check(scene.postAmmo == scene.preAmmo - count, scene.id + " consumed " + (scene.preAmmo - scene.postAmmo));
            ItemStack pickup = switch (kind) {
                case ARROW, SPECTRAL_EXCLUDED -> new ItemStack(Items.ARROW);
                case TIPPED, SPECTRAL_ALLOWED -> held.copyWithCount(1);
            };
            for (var spawn : scene.spawns) {
                boolean entity = spawn.entity() instanceof AbstractArrow arrow
                        && arrow.getType() == (spectral ? EntityType.SPECTRAL_ARROW : EntityType.ARROW)
                        && ItemStack.isSameItemSameComponents(arrow.getPickupItem(), pickup);
                checks.check(entity, scene.id + " " + spawn.entity().getType() + " picks up as "
                        + (spawn.entity() instanceof AbstractArrow arrow ? arrow.getPickupItem() : "none") + " expected " + pickup);
                boolean effects = spawn.entity() instanceof Arrow arrow && arrow.getColor() != -1;
                if (!spectral) {
                    checks.check(effects == (kind == Kind.TIPPED), scene.id + " potion effects=" + effects
                            + " contents=" + (spawn.entity() instanceof AbstractArrow arrow
                                    ? arrow.getPickupItem().get(DataComponents.POTION_CONTENTS) : null));
                }
            }
            return true;
        }

        @Override void release() {}
    }
}
