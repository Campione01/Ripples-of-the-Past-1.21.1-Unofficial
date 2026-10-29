package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.core.init.ModItems;
import rotp.core.powersystem.standpower.StandInstance.StandPart;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowMeleeAdmissionGameTests {
    private KingCrimsonThrowMeleeAdmissionGameTests() {}
    @GameTest(template = "empty", timeoutTicks = 2000, batch = "kc_throw_melee_admission")
    public static void throwKeepsMeleeStatAndOuterAdmissionWithoutAffordabilityVeto(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowMeleeAdmissionGameTests.class);
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowTimingFixture.class);
        var suite = new Admission(helper);
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    private static final class Admission extends KingCrimsonThrowGameTestFixture.Suite {
        int kind;
        boolean triple;
        Admission(GameTestHelper helper) { super(helper, "D-KC-THROW-MELEE-ADMISSION", 24); }
        @Override void prepare(int index) {
            kind = index % 12; triple = index >= 12;
            scene = new KingCrimsonThrowGameTestFixture(helper, "melee/" + (triple ? "triple/" : "single/") + kind,
                    KingCrimsonThrowGameTestFixture.Mode.FINITE, kind == 3 ? 0 : kind == 4 ? 119 : 600, 73);
            KingCrimsonThrowTimingFixture.stats(scene, kind == 0 ? 0 : 14, 0);
            scene.hand(new ItemStack(Items.ARROW, 3));
            if (kind == 1) scene.stand.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(0);
            if (kind == 2) scene.stand.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(0);
            if (kind == 6) scene.hand(ItemStack.EMPTY);
            if (kind == 7) scene.hand(new ItemStack(ModItems.STAND_ARROW.get()));
            if (kind == 8) scene.premise(scene.power.getStandInstance().orElseThrow().removePart(StandPart.ARMS), "Required arms removed");
            if (kind == 9) { KingCrimsonConfig.ALLOWED_PROJECTILES.set(""); scene.hand(new ItemStack(Items.SNOWBALL, 3)); }
            if (kind == 10) { scene.hand(new ItemStack(ModItems.MOLOTOV.get(), 3)); scene.offhand(ItemStack.EMPTY); }
            if (kind == 11) { scene.stand.setArmsOnlyMode(true, true); scene.user.setShiftKeyDown(true); }
            String name = triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE;
            if (kind == 5) scene.power.setAbilityCooldown(name, 50);
            scene.premise(scene.stand.canAttackMelee() == (kind >= 3), "Actual modern speed/entity/block reach predicate");
            float stamina = scene.power.getStamina(); int ammo = scene.user.getMainHandItem().getCount();
            int cooldown = scene.power.getAbilityCooldown(name);
            var press = scene.press(name, KingCrimsonThrowGameTestFixture.FIRST);
            boolean expected = kind == 3 || kind == 4 || kind == 11;
            checks.check(press.admitted() == expected && !press.buffered(), scene.id + " registered input result=" + press.admitted());
            if (!expected) {
                checks.check(scene.action == null && scene.stand.getCurStandAction() == null && scene.spawns.isEmpty()
                        && scene.power.getStamina() == stamina && scene.user.getMainHandItem().getCount() == ammo
                        && scene.power.getAbilityCooldown(name) == cooldown, scene.id + " refusal leaves action/resources/ammo/cooldown untouched");
                done = true; return;
            }
            if (!press.admitted()) { done = true; return; }
            scene.premise(scene.stand.canAttackMelee(), "Positive actual stats for admitted shortage/arms-only controls");
        }
        @Override boolean verify() {
            if (!scene.performed) return false;
            scene.checkPayment(checks, true);
            int count = triple ? 3 : 1;
            checks.check(scene.spawns.size() == count && scene.postAmmo == scene.preAmmo - count,
                    scene.id + " legal payload emits one batch without full120 veto");
            checks.check(scene.preCooldown > 0 && scene.postCooldown == scene.preCooldown,
                    scene.id + " admission cooldown retained");
            if (kind == 3 || kind == 4) checks.check(scene.inputStamina < 120 && scene.preStamina < 120,
                    scene.id + " finite shortage remains real through native perform");
            return true;
        }
        @Override void release() {}
    }
}
