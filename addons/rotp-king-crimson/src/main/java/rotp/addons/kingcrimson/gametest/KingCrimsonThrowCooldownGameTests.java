package rotp.addons.kingcrimson.gametest;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.gametest.KingCrimsonThrowGameTestFixture.Mode;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonThrowCooldownGameTests {
    private static CooldownSuite active;
    private KingCrimsonThrowCooldownGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 2200, batch = "kc_throw_03")
    public static void throwCommitsCooldownOnSuccessfulInputAndNativeReplay(GameTestHelper helper) {
        KingCrimsonThrowGameTestFixture.identity(helper, KingCrimsonThrowCooldownGameTests.class);
        helper.assertTrue(active == null, "No overlapping throw cooldown suite");
        CooldownSuite suite = new CooldownSuite(helper); active = suite;
        try { suite.start(); } catch (RuntimeException | Error error) { suite.close(); throw error; }
    }
    @AfterBatch(batch = "kc_throw_03")
    public static void cleanupCooldown(ServerLevel level) { if (active != null) active.close(); }

    private static final class CooldownSuite extends KingCrimsonThrowGameTestFixture.Suite {
        int kind, duration, admittedAge, recoverySamples, lastSampleAge;
        boolean triple, bufferedCase, replayed, checkedPerform, interrupted;
        String requested;
        Ability requestedAbility;
        EntityActionInstance blocker;
        CooldownSuite(GameTestHelper helper) { super(helper, "D-KC-THROW-03", 22); }
        @Override void prepare(int index) {
            kind = index % 11; triple = index >= 11; bufferedCase = kind == 5;
            replayed = false; checkedPerform = false; interrupted = false; recoverySamples = 0; lastSampleAge = -1;
            Mode mode = switch (kind) { case 6 -> Mode.CREATIVE; case 7 -> Mode.RESOLVE;
                case 8 -> Mode.NO_COOLDOWN; case 10 -> Mode.STAMINA_OFF; default -> Mode.FINITE; };
            int configured = kind == 0 ? 37 : kind == 1 ? 97 : kind == 9 ? 0 : 73;
            scene = new KingCrimsonThrowGameTestFixture(helper, "cooldown/" + (triple ? "triple/" : "single/") + kind, mode, 600, configured);
            requested = triple ? KingCrimsonThrowGameTestFixture.TRIPLE : KingCrimsonThrowGameTestFixture.SINGLE;
            requestedAbility = scene.power.getAbility(requested);
            duration = requestedAbility.getCooldown(scene.power, 0);
            scene.premise(duration == (kind >= 6 && kind <= 9 ? 0 : configured), "Actual configured cooldown and zero exemptions");
            scene.hand(kind == 4 ? ItemStack.EMPTY : new ItemStack(Items.ARROW, 12));
            scene.aim(57, -21);
            if (bufferedCase) {
                String opposite = triple ? KingCrimsonThrowGameTestFixture.SINGLE : KingCrimsonThrowGameTestFixture.TRIPLE;
                var busy = scene.press(opposite, KingCrimsonThrowGameTestFixture.FIRST);
                scene.premise(busy.admitted() && busy.action().getPhase() == ActionPhase.WINDUP, "Real opposite throw owns busy Stand");
                blocker = busy.action();
                scene.premise(requestedAbility instanceof EntityActionType type && !blocker.canBeCancelledInto(type),
                        "Opposite throw is not yet cancellable");
                var queued = scene.press(requested, KingCrimsonThrowGameTestFixture.SECOND, true);
                scene.premise(queued.conditions() && queued.buffered() && !queued.admitted()
                        && scene.stand.getCurStandAction() == blocker, "Actual registered buffered request, not admission");
                checks.check(scene.power.getAbilityCooldown(requested) == 0, scene.id + " queue creation commits no requested cooldown");
                scene.listen(EntityTickEvent.Post.class, event -> {
                    if (event.getEntity() != scene.user || replayed || scene.eventFailure != null) return;
                    try {
                        EntityActionInstance current = scene.stand.getCurStandAction();
                        if (current != null && current != blocker && current.ability == requestedAbility) {
                            replayed = true; admittedAge = scene.user.tickCount;
                            checks.check(scene.power.getAbilityCooldown(requested) == duration,
                                    scene.id + " natural replay commits full configured cooldown=" + duration
                                            + " actual=" + scene.power.getAbilityCooldown(requested));
                            scene.spawns.forEach(spawn -> spawn.entity().discard()); scene.spawns.clear();
                            scene.action = current; scene.ability = requestedAbility; scene.performing = false; scene.performed = false;
                            scene.windupChanged = false; scene.inputUserAge = admittedAge; scene.inputStandAge = scene.stand.tickCount;
                            KingCrimsonThrowGameTestFixture.identity(helper, current.getClass());
                            KingCrimsonThrowGameTestFixture.log("REPLAY " + scene.id + " userAge=" + admittedAge
                                    + " standAge=" + scene.stand.tickCount + " cooldown=" + scene.power.getAbilityCooldown(requested));
                        } else checks.check(scene.power.getAbilityCooldown(requested) == 0, scene.id + " busy queue has no cooldown before replay");
                    } catch (RuntimeException error) { scene.eventFailure = error; }
                });
                return;
            }
            var press = scene.press(requested, KingCrimsonThrowGameTestFixture.FIRST);
            if (kind == 4) {
                checks.check(!press.admitted() && !press.buffered() && scene.power.getAbilityCooldown(requested) == 0,
                        scene.id + " initial empty-hand refusal commits no cooldown");
                done = true; return;
            }
            scene.premise(press.admitted() && press.action().getPhase() == ActionPhase.WINDUP, "Actual accepted CLICK with native windup");
            admittedAge = scene.user.tickCount;
            checks.check(scene.power.getAbilityCooldown(requested) == duration,
                    scene.id + " accepted input commits configured cooldown=" + duration + " actual=" + scene.power.getAbilityCooldown(requested));
            scene.duringWindup = () -> {
                if (kind == 2) scene.hand(ItemStack.EMPTY);
                if (kind == 3) {
                    interrupted = true;
                    KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(scene.user, scene.power);
                    scene.premise(scene.stand.isRemoved() && !scene.power.isSummoned(), "Production forced-unsummon interrupts admitted windup");
                }
            };
        }
        @Override boolean verify() {
            if (bufferedCase && !replayed) return false;
            int age = scene.user.tickCount;
            int expected = Math.max(0, duration - (age - admittedAge));
            if (age != lastSampleAge) {
                checks.check(scene.power.getAbilityCooldown(requested) == expected,
                        scene.id + " cooldown follows actual user ticks elapsed=" + (age - admittedAge)
                                + " expected=" + expected + " actual=" + scene.power.getAbilityCooldown(requested));
                lastSampleAge = age;
            }
            if (kind == 3) {
                if (!interrupted || age - admittedAge < 8) return false;
                checks.check(scene.spawns.isEmpty() && !scene.performed && scene.power.getStamina() >= scene.inputStamina,
                        scene.id + " interruption keeps committed cooldown without a perform debit/emission");
                return true;
            }
            if (!scene.performed) return false;
            if (!checkedPerform) {
                scene.checkPayment(checks, true);
                int expectedPre = Math.max(0, duration - (scene.preUserAge - admittedAge));
                checks.check(scene.preCooldown == expectedPre && scene.postCooldown == expectedPre,
                        scene.id + " no PERFORM cooldown refresh pre=" + scene.preCooldown + " post=" + scene.postCooldown
                                + " elapsed=" + (scene.preUserAge - admittedAge));
                int count = kind == 2 ? 0 : triple ? 3 : 1;
                scene.checkSpawns(checks, count, Arrow.class);
                checks.check(scene.postAmmo == scene.preAmmo - (kind == 6 ? 0 : count), scene.id + " unchanged ammo policy");
                checkedPerform = true;
            }
            if (scene.action.getPhase() == ActionPhase.RECOVERY) {
                recoverySamples++;
                KingCrimsonThrowGameTestFixture.log("RECOVERY " + scene.id + " userAge=" + age + " elapsed="
                        + (age - admittedAge) + " cooldown=" + scene.power.getAbilityCooldown(requested));
            }
            if (!scene.action.isOver()) return false;
            scene.premise(recoverySamples > 0, "Native recovery was observed before completion");
            checks.check(scene.power.getAbilityCooldown(requested) == expected, scene.id + " native completion does not reset cooldown");
            return true;
        }
        @Override void release() { if (active == this) active = null; }
    }
}
