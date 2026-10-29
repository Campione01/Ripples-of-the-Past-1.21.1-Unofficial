package rotp.addons.ger.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerUndiePayloads;
import rotp.addons.ger.state.GerLivingState;
import rotp.core.JojoModConfig;
import rotp.core.api.soul.SoulResolveEligibilityProviders;
import rotp.core.api.soul.SoulResolveQuery;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.init.ModEntityAttributes;
import rotp.core.init.ModSpecialActions;
import rotp.core.init.power.ModStands;
import rotp.core.mechanics.standdisc.StandDiscItem;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.soul.SoulEntity;

/**
 * 1.16 UndiePunch and UndieManager, with the owner's 2026-10-14 rules: a loop
 * death is seen (the body dies, is pulled back from deathTime 15 and revives)
 * but has no death effect; only the final death is a real one.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerUndieGameTests {
    private static final String UNDIE = "ger_undie_punch";
    private static final int PULL_START = 15;
    private static final int LOOPS = 10;

    private GerUndieGameTests() {}

    static GerLivingState state(LivingEntity entity) {
        return GoldExperienceRequiemAddon.livingState(entity);
    }

    /** 10 health against GER's 20 strength, and not pushed out of its chunk. */
    static ObservedCow markableCow(GerMeleeGameTestScene scene, Vec3 position) {
        ObservedCow cow = new ObservedCow(scene.level);
        cow.setNoAi(true);
        cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        return scene.add(cow, position);
    }

    static boolean isGenericDeathMessage(LivingEntity entity) {
        return entity.getCombatTracker().getDeathMessage().getContents()
                instanceof TranslatableContents message
                && message.getKey().equals("death.attack.generic");
    }

    /** The Undie payloads one client was sent about one entity, in order. */
    static List<String> undiePayloads(List<Object> received, int entityId) {
        List<String> payloads = new ArrayList<>();
        for (Object payload : received) {
            if (payload instanceof GerUndiePayloads.StatePayload state && state.entityId() == entityId) {
                payloads.add(state.phase() == GerUndiePayloads.DYING ? "dying" : "pulling");
            }
            else if (payload instanceof GerUndiePayloads.RevivePayload revive && revive.entityId() == entityId) {
                payloads.add("revive:" + revive.health());
            }
        }
        return payloads;
    }

    /** Ten seen loop deaths: each dies, is pulled back and revives at 1 health. */
    static List<String> loopPayloads() {
        List<String> payloads = new ArrayList<>();
        for (int loop = 0; loop < LOOPS; loop++) {
            payloads.addAll(List.of("dying", "pulling", "revive:1.0"));
        }
        return payloads;
    }

    static long combatKills(List<Object> received) {
        return received.stream().filter(ClientboundPlayerCombatKillPacket.class::isInstance).count();
    }

    /** Follows a victim through its loop deaths, one look per server tick. */
    static final class LoopWatch {
        final LivingEntity victim;
        final Vec3 anchor;
        final List<Integer> window = new ArrayList<>();
        List<Integer> lastWindow = List.of();
        boolean dying;
        boolean movedWhileDead;
        int windows;
        private Vec3 deathPosition;

        LoopWatch(LivingEntity victim) {
            this.victim = victim;
            this.anchor = victim.position();
        }

        /** @return true on the tick the victim came back from a seen death */
        boolean observe() {
            if (victim.getHealth() <= 0 && !victim.isRemoved()) {
                if (!dying) {
                    dying = true;
                    window.clear();
                    deathPosition = victim.position();
                }
                window.add(victim.deathTime);
                movedWhileDead |= victim.position().distanceTo(deathPosition) > 1.0E-3;
                return false;
            }
            if (dying && victim.getHealth() > 0) {
                dying = false;
                windows++;
                lastWindow = List.copyOf(window);
                return true;
            }
            return false;
        }

        /** The surface teleport may land outside the ticking test area. */
        void bringBack() {
            victim.teleportTo(anchor.x, anchor.y, anchor.z);
            victim.setDeltaMovement(Vec3.ZERO);
        }

        /** Null when the last window rose 1..15 and was then pulled back to 0. */
        String windowProblem() {
            List<Integer> times = lastWindow;
            int peak = times.indexOf(PULL_START);
            if (peak < 0 || times.get(0) != 1) {
                return "the body never died up to deathTime 15: " + times;
            }
            for (int i = 1; i < times.size(); i++) {
                int step = i <= peak ? 1 : -1;
                if (times.get(i) != times.get(i - 1) + step) {
                    return "deathTime did not rise to 15 and fall back by one a tick: " + times;
                }
            }
            return times.get(times.size() - 1) <= 2 && victim.deathTime == 0
                    ? null
                    : "revived before the pullback reached 0: " + times + " then " + victim.deathTime;
        }
    }

    /** 1.16 UndiePunch: resolveLevelToUnlock stayed at 0, so the Undie is unlocked from the start. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_undie_unlock")
    public static void undieIsUnlockedWithoutResolve(GameTestHelper helper) {
        new Sequence(helper, "undie/unlock", 150) {
            private Actor actor;
            private ObservedCow cow;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor(scene.origin, 0, null, false);
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        scene.premise(!actor.power().wasProgressionSkipped()
                                        && actor.power().getResolveLevel() == 0,
                                "a fresh GER at Resolve 0");
                        Ability undie = actor.power().getAbility(UNDIE);
                        helper.assertTrue(undie.getResolveLevelToUnlock() <= 0 && undie.isAbilityUnlocked(actor.power()),
                                scene.id + ": the Undie asks for Resolve " + undie.getResolveLevelToUnlock());
                        stage = 1;
                    }
                    case 1 -> {
                        scene.pressAction(actor, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        return true;
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** 1.16 UndiePunch tagged the target before the hit and never untagged a survivor. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_survivor")
    public static void undieMarkStaysOnASurvivingTarget(GameTestHelper helper) {
        new Sequence(helper, "undie/survivor", 250) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = markableCow(scene, scene.origin.add(0, 0, 1.5));
                        // 10 health is markable, and the absorption takes the whole hit.
                        cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);
                        cow.setAbsorptionAmount(100);
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, UNDIE, UNDIE,
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.punchedTarget != null) {
                            scene.log("health=" + cow.getHealth() + " hurts=" + cow.hurts
                                    + " caster=" + state(cow).undieCaster());
                            scene.premise(!cow.hurts.isEmpty() && cow.isAlive(), "the cow took the hit and lived");
                            helper.assertTrue(state(cow).undieActive()
                                            && actor.user().getUUID().equals(state(cow).undieCaster()),
                                    scene.id + ": the surviving target lost its Undie mark");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * 1.16 UndiePunch rewrote only the caster on a target already looping:
     * the death count and the running rekill stay.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_undie_restrike")
    public static void undieRestrikeOnlyChangesTheCaster(GameTestHelper helper) {
        new Sequence(helper, "undie/restrike", 350) {
            private Actor first;
            private Actor second;
            private ObservedCow cow;
            private LoopWatch watch;
            private EntityActionInstance strike;
            private int stage;

            @Override
            boolean step() {
                boolean revived = watch != null && watch.observe();
                switch (stage) {
                    case 0 -> {
                        first = scene.actor(scene.origin, 0, null);
                        second = scene.actor(scene.origin.add(0, 0, 3), 180, null);
                        cow = markableCow(scene, scene.origin.add(0, 0, 1.5));
                        watch = new LoopWatch(cow);
                        stage = 1;
                    }
                    case 1 -> {
                        scene.pressAction(first, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        if (state(cow).undieDeaths() == 1 && cow.getHealth() > 0) {
                            watch.bringBack();
                            // The second strike must not kill: only its marking is under test.
                            cow.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(100);
                            cow.setAbsorptionAmount(100);
                            strike = scene.pressAction(second, cow, UNDIE, UNDIE,
                                    InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (strike.punchedTarget != null) {
                            GerLivingState state = state(cow);
                            scene.log("after the second strike deaths=" + state.undieDeaths()
                                    + " caster=" + state.undieCaster() + " health=" + cow.getHealth());
                            helper.assertTrue(second.user().getUUID().equals(state.undieCaster()),
                                    scene.id + ": the second strike did not take over the caster: " + state.undieCaster());
                            helper.assertTrue(state.undieDeaths() == 1,
                                    scene.id + ": the second strike reset the loop count to " + state.undieDeaths());
                            stage = 4;
                        }
                    }
                    case 4 -> {
                        if (revived) {
                            watch.bringBack();
                        }
                        if (state(cow).undieDeaths() == 2 && cow.getHealth() > 0) {
                            helper.assertTrue(soulGoesTo(cow, second) && !soulGoesTo(cow, first),
                                    scene.id + ": the soul restriction did not follow the new caster");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    static boolean soulGoesTo(LivingEntity origin, Actor taker) {
        SoulEntity soul = new SoulEntity(origin.level(), origin, 100);
        return SoulResolveEligibilityProviders.isEligible(new SoulResolveQuery(
                soul, origin, taker.user(), taker.power(), true, false));
    }

    /**
     * 1.16 UndieManager: the body really dies, is pulled back from deathTime 15
     * to 0 with pull and revive packets for the whole dimension, and revives
     * extinguished with a fresh combat tracker. Under the owner's rule the
     * loop death itself has no death effect.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_mob_death")
    public static void undieLoopDeathIsSeenPulledBackAndRevived(GameTestHelper helper) {
        new Sequence(helper, "undie/mob-death", 250) {
            private Actor actor;
            private ObservedCow cow;
            private LoopWatch watch;
            private int deathEvents;
            private int dropped;
            private Boolean hurtWhileDead;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = markableCow(scene, scene.origin.add(0, 0, 1.5));
                        cow.setRemainingFireTicks(400);
                        watch = new LoopWatch(cow);
                        scene.listen(LivingDeathEvent.class, event -> {
                            if (event.getEntity() == cow) {
                                deathEvents++;
                            }
                        });
                        scene.listen(EntityJoinLevelEvent.class, event -> {
                            if (event.getEntity() instanceof ItemEntity || event.getEntity() instanceof ExperienceOrb) {
                                dropped++;
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        scene.pressAction(actor, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        boolean revived = watch.observe();
                        helper.assertTrue(!cow.isRemoved(), scene.id + ": the loop death removed the body");
                        if (watch.dying && hurtWhileDead == null) {
                            hurtWhileDead = cow.hurt(scene.level.damageSources().generic(), 5);
                        }
                        if (watch.dying) {
                            helper.assertTrue(cow.getPose() == Pose.DYING,
                                    scene.id + ": the dying body is in pose " + cow.getPose());
                        }
                        helper.assertTrue(revived || watch.dying || state(cow).undieDeaths() == 0,
                                scene.id + ": the cow came back without a seen death");
                        if (revived) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                List<String> payloads = undiePayloads(actor.received(), cow.getId());
                scene.log("window=" + watch.lastWindow + " payloads=" + payloads + " deathEvents=" + deathEvents
                        + " dropped=" + dropped + " fire=" + cow.getRemainingFireTicks()
                        + " message=" + cow.getCombatTracker().getDeathMessage().getString());
                String window = watch.windowProblem();
                helper.assertTrue(window == null, scene.id + ": " + window);
                helper.assertTrue(!watch.movedWhileDead, scene.id + ": the body moved before it was revived");
                helper.assertTrue(payloads.equals(List.of("dying", "pulling", "revive:1.0")),
                        scene.id + ": players in the dimension were sent " + payloads);
                helper.assertTrue(deathEvents == 0, scene.id + ": the loop death posted " + deathEvents + " death events");
                helper.assertTrue(dropped == 0, scene.id + ": the loop death dropped " + dropped + " entities");
                helper.assertTrue(Boolean.FALSE.equals(hurtWhileDead), scene.id + ": the dying body could be hurt");
                helper.assertTrue(cow.getHealth() == 1.0F && cow.deathTime == 0 && cow.getPose() == Pose.STANDING,
                        scene.id + ": revived with health " + cow.getHealth() + " pose " + cow.getPose());
                helper.assertTrue(cow.getRemainingFireTicks() <= 0, scene.id + ": the revived body still burns");
                helper.assertTrue(isGenericDeathMessage(cow),
                        scene.id + ": the combat tracker kept " + cow.getCombatTracker().getDeathMessage().getString());
                helper.assertTrue(state(cow).undieActive() && state(cow).undieDeaths() == 1,
                        scene.id + ": loop count " + state(cow).undieDeaths());
            }
        }.start();
    }

    /**
     * A player's loop death: the dying camera and the pullback, no death
     * screen, no hurt, and no action: no attack, no open container, and a
     * drop stays in the inventory.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_player_death")
    public static void undiePlayerLoopDeathHasNoDeathScreen(GameTestHelper helper) {
        new Sequence(helper, "undie/player-death", 250) {
            private Actor actor;
            private Client victim;
            private ObservedCow bystander;
            private LoopWatch watch;
            private int deathEvents;
            private int itemsAdded;
            private Boolean hurtWhileDead;
            private boolean attackedWhileDead;
            private boolean containerOpenWhileDead;
            private int dirtAfterDrop = -1;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        victim = scene.client(scene.origin.add(0, 0, 2));
                        bystander = scene.cow(scene.origin.add(1.5, 0, 2));
                        watch = new LoopWatch(victim.player());
                        Inventory inventory = victim.player().getInventory();
                        inventory.setItem(inventory.selected, new ItemStack(Items.DIRT, 3));
                        scene.listen(LivingDeathEvent.class, event -> {
                            if (event.getEntity() == victim.player()) {
                                deathEvents++;
                            }
                        });
                        scene.listen(EntityJoinLevelEvent.class, event -> {
                            if (event.getEntity() instanceof ItemEntity) {
                                itemsAdded++;
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        // past the new player's spawn protection
                        if (victim.player().tickCount > 60) {
                            victim.player().openMenu(new SimpleMenuProvider(
                                    (id, inventory, player) -> ChestMenu.threeRows(id, inventory),
                                    Component.empty()));
                            scene.premise(victim.player().containerMenu != victim.player().inventoryMenu,
                                    "the victim has a chest open");
                            state(victim.player()).armUndie(actor.user().getUUID());
                            scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                    "the armed player took the lethal hit");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        boolean revived = watch.observe();
                        helper.assertTrue(!victim.player().isRemoved(), scene.id + ": the loop death removed the player");
                        if (watch.dying && hurtWhileDead == null) {
                            hurtWhileDead = victim.player().hurt(scene.level.damageSources().generic(), 5);
                            victim.player().attack(bystander);
                            attackedWhileDead = !bystander.hurts.isEmpty();
                            containerOpenWhileDead = victim.player().containerMenu != victim.player().inventoryMenu;
                            // what the server does with a client's drop key
                            victim.player().drop(false);
                            dirtAfterDrop = victim.player().getInventory().countItem(Items.DIRT);
                        }
                        helper.assertTrue(revived || watch.dying || state(victim.player()).undieDeaths() == 0,
                                scene.id + ": the player came back without a seen death");
                        if (revived) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                List<String> own = undiePayloads(victim.received(), victim.player().getId());
                List<String> watcher = undiePayloads(actor.received(), victim.player().getId());
                scene.log("window=" + watch.lastWindow + " own=" + own + " watcher=" + watcher
                        + " combatKills=" + combatKills(victim.received()) + " deathEvents=" + deathEvents
                        + " containerOpen=" + containerOpenWhileDead + " dirtAfterDrop=" + dirtAfterDrop
                        + " itemsAdded=" + itemsAdded);
                String window = watch.windowProblem();
                helper.assertTrue(window == null, scene.id + ": " + window);
                helper.assertTrue(combatKills(victim.received()) == 0,
                        scene.id + ": the victim was sent a death screen");
                helper.assertTrue(own.equals(List.of("dying", "pulling", "revive:1.0")) && own.equals(watcher),
                        scene.id + ": the victim was sent " + own + ", another player " + watcher);
                helper.assertTrue(deathEvents == 0, scene.id + ": the loop death posted " + deathEvents + " death events");
                helper.assertTrue(Boolean.FALSE.equals(hurtWhileDead), scene.id + ": the dying player could be hurt");
                helper.assertTrue(!attackedWhileDead, scene.id + ": the dying player could attack");
                helper.assertTrue(!containerOpenWhileDead, scene.id + ": the dying player kept its chest open");
                helper.assertTrue(dirtAfterDrop == 3 && itemsAdded == 0,
                        scene.id + ": the dying player dropped an item: " + dirtAfterDrop + " dirt left, "
                                + itemsAdded + " item entities added");
                helper.assertTrue(victim.player().isAlive() && victim.player().getHealth() == 1.0F,
                        scene.id + ": revived with health " + victim.player().getHealth());
            }
        }.start();
    }

    /**
     * A dying Stand user cannot use its Stand: the summon key neither
     * unsummons nor summons it, and an ability press starts nothing.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_player_stand")
    public static void undieDyingPlayerCannotUseItsStand(GameTestHelper helper) {
        new Sequence(helper, "undie/player-stand", 250) {
            private Client puncher;
            private Client out;
            private Client away;
            private StandEntity outStand;
            private Boolean punchStarted;
            private int checkAt;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        puncher = standUser(scene, scene.origin.add(-3, 0, 2), true);
                        out = standUser(scene, scene.origin.add(0, 0, 2), true);
                        away = standUser(scene, scene.origin.add(3, 0, 2), false);
                        outStand = StandPower.get(out.player()).getSummonedStandEntity();
                        stage = 1;
                    }
                    case 1 -> {
                        if (out.player().tickCount > 60) {
                            for (Client victim : List.of(puncher, out, away)) {
                                state(victim.player()).armUndie(UUID.randomUUID());
                                scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                        "the armed player took the lethal hit");
                            }
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (List.of(puncher, out, away).stream().allMatch(victim -> victim.player().getHealth() <= 0)) {
                            StandPower puncherPower = StandPower.get(puncher.player());
                            Ability punch = puncherPower.getAbility("ger_stand_punch");
                            BufferingState buffer = BufferingState.clickOnly();
                            AbilityInput.keyPress(GerMeleeGameTestScene.LMB, punch, puncher.player(), null,
                                    InputMethod.CLICK, 0, buffer, punch.getAbilityId());
                            EntityActionInstance action = puncherPower.getSummonedStandEntity().getCurStandAction();
                            punchStarted = buffer.isActionSuccess
                                    || action != null && action.ability == punch;
                            // what the server does with a client's summon key
                            StandPower outPower = StandPower.get(out.player());
                            outPower.getPowerType().onUserSummonCommand(out.player(), outPower);
                            EntityActionInstance unsummon = outStand.getCurStandAction();
                            scene.premise(outStand.isBeingRetracted() || unsummon != null
                                            && unsummon.ability == ModSpecialActions.STAND_UNSUMMON.get(),
                                    "the summon key started the unsummon");
                            StandPower awayPower = StandPower.get(away.player());
                            awayPower.getPowerType().onUserSummonCommand(away.player(), awayPower);
                            checkAt = tick + 3;
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (tick >= checkAt) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                StandPower outPower = StandPower.get(out.player());
                StandPower awayPower = StandPower.get(away.player());
                EntityActionInstance outAction = outStand.getCurStandAction();
                scene.log("punchStarted=" + punchStarted + " outSummoned=" + outPower.isSummoned()
                        + " outStand=" + (outPower.getSummonedStandEntity() == outStand && !outStand.isRemoved())
                        + " outAction=" + (outAction != null ? outAction.ability : null)
                        + " awaySummoned=" + awayPower.isSummoned());
                scene.premise(List.of(puncher, out, away).stream()
                                .allMatch(victim -> state(victim.player()).inUndieDeath()),
                        "the victims are still dying");
                helper.assertTrue(!punchStarted, scene.id + ": the dying player's punch started");
                helper.assertTrue(outPower.isSummoned() && outPower.getSummonedStandEntity() == outStand
                                && !outStand.isRemoved()
                                && (outAction == null || outAction.ability != ModSpecialActions.STAND_UNSUMMON.get()),
                        scene.id + ": the dying player unsummoned its Stand");
                helper.assertTrue(!awayPower.isSummoned(), scene.id + ": the dying player summoned its Stand");
            }
        }.start();
    }

    /**
     * The leap key and the summon key reach the server without the alive
     * check of 1.16: a dying victim neither leaps with its Stand nor turns an
     * arms-only Stand into a full summon.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_player_leap")
    public static void undieDyingPlayerCannotLeapOrFullySummonItsArms(GameTestHelper helper) {
        new Sequence(helper, "undie/player-leap", 250) {
            private Client leaper;
            private Client arms;
            private StandEntity armsStand;
            private int checkAt;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        leaper = standUser(scene, scene.origin.add(-2, 0, 2), true);
                        arms = standUser(scene, scene.origin.add(2, 0, 2), true);
                        armsStand = StandPower.get(arms.player()).getSummonedStandEntity();
                        armsStand.setArmsOnlyMode(true, false);
                        stage = 1;
                    }
                    case 1 -> {
                        if (leaper.player().tickCount > 60) {
                            scene.premise(StandPower.get(leaper.player()).canLeap(),
                                    "the living victim can leap with its Stand");
                            scene.premise(armsStand.isArmsOnlyMode(), "the victim's Stand is arms-only");
                            for (Client victim : List.of(leaper, arms)) {
                                state(victim.player()).armUndie(UUID.randomUUID());
                                scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                        "the armed player took the lethal hit");
                            }
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (List.of(leaper, arms).stream().allMatch(victim -> victim.player().getHealth() <= 0)) {
                            // what the server does with a client's summon key
                            StandPower armsPower = StandPower.get(arms.player());
                            armsPower.getPowerType().onUserSummonCommand(arms.player(), armsPower);
                            scene.premise(!armsStand.isArmsOnlyMode(), "the summon key fully summoned the arms");
                            checkAt = tick + 3;
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (tick >= checkAt) {
                            boolean canLeap = StandPower.get(leaper.player()).canLeap();
                            scene.log("canLeap=" + canLeap + " armsOnly=" + armsStand.isArmsOnlyMode()
                                    + " main=" + armsStand.showArm(InteractionHand.MAIN_HAND)
                                    + " off=" + armsStand.showArm(InteractionHand.OFF_HAND));
                            scene.premise(List.of(leaper, arms).stream()
                                            .allMatch(victim -> state(victim.player()).inUndieDeath()),
                                    "the victims are still dying");
                            helper.assertTrue(!canLeap, scene.id + ": the dying player can leap with its Stand");
                            helper.assertTrue(StandPower.get(arms.player()).getSummonedStandEntity() == armsStand
                                            && armsStand.isArmsOnlyMode()
                                            && armsStand.showArm(InteractionHand.MAIN_HAND)
                                            && !armsStand.showArm(InteractionHand.OFF_HAND),
                                    scene.id + ": the dying player's arms became a full summon");
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /** A player with GER, its Stand out or not, whose dying runs in its connection tick. */
    static Client standUser(GerMeleeGameTestScene scene, Vec3 position, boolean summoned) {
        Client client = scene.client(position);
        StandPower power = PowerClass.STAND.attachGet(client.player());
        StandType type = GoldExperienceRequiemAddon.GER_STAND.get();
        scene.premise(StandPowerTransitions.insert(power, new StandInstance(type)).status()
                        == StandPowerTransitions.Status.APPLIED,
                "the victim has GER");
        power.skipProgression();
        if (summoned) {
            scene.premise(type.summon(client.player(), power), "the victim's GER is out");
            scene.owned.add(power.getSummonedStandEntity());
        }
        scene.onClose(() -> {
            if (power.isSummoned()) {
                type.forceUnsummon(client.player(), power);
            }
        });
        return client;
    }

    /**
     * Saved inside the window and loaded again after a crash, a player is
     * revived as it joins instead of meeting a real death screen.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_player_rejoin")
    public static void undiePlayerRejoiningInsideTheWindowIsRevived(GameTestHelper helper) {
        new Sequence(helper, "undie/player-rejoin", 250) {
            private Client victim;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        victim = scene.client(scene.origin.add(0, 0, 2));
                        stage = 1;
                    }
                    case 1 -> {
                        if (victim.player().tickCount > 60) {
                            state(victim.player()).armUndie(UUID.randomUUID());
                            scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                    "the armed player took the lethal hit");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        if (victim.player().getHealth() <= 0 && victim.player().deathTime >= 3) {
                            var registries = scene.level.registryAccess();
                            GerLivingState loaded = new GerLivingState();
                            loaded.deserializeNBT(registries, state(victim.player()).serializeNBT(registries));
                            victim.player().setData(GoldExperienceRequiemAddon.LIVING_STATE, loaded);
                            scene.premise(loaded.inUndieDeath(), "the saved player is inside the window");
                            GoldExperienceRequiemGameEvents.onPlayerLogin(
                                    new PlayerEvent.PlayerLoggedInEvent(victim.player()));
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private void verify() {
                List<String> own = undiePayloads(victim.received(), victim.player().getId());
                scene.log("health=" + victim.player().getHealth() + " phase=" + state(victim.player()).undiePhase()
                        + " deaths=" + state(victim.player()).undieDeaths() + " own=" + own);
                helper.assertTrue(victim.player().isAlive() && victim.player().getHealth() == 1.0F,
                        scene.id + ": the joining player has health " + victim.player().getHealth());
                helper.assertTrue(!state(victim.player()).inUndieDeath() && state(victim.player()).undieDeaths() == 1,
                        scene.id + ": the joining player is left in " + state(victim.player()).undiePhase()
                                + " after " + state(victim.player()).undieDeaths() + " revivals");
                helper.assertTrue(own.equals(List.of("dying", "revive:1.0")),
                        scene.id + ": the joining player was sent " + own);
            }
        }.start();
    }

    /** 1.16 UndieManager.reKillEntity: with no stored death source the rekill is OUT_OF_WORLD. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_undie_rekill_fallback")
    public static void undieRekillWithoutAStoredSourceFallsOutOfTheWorld(GameTestHelper helper) {
        new Sequence(helper, "undie/rekill-fallback", 250) {
            private Actor actor;
            private ObservedCow cow;
            private final List<DamageSource> sources = new ArrayList<>();
            private int sourcesAtRevive;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = markableCow(scene, scene.origin.add(0, 0, 1.5));
                        scene.listen(LivingIncomingDamageEvent.class, event -> {
                            if (event.getEntity() == cow) {
                                sources.add(event.getSource());
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        scene.pressAction(actor, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        if (state(cow).undieDeaths() == 1 && cow.getHealth() > 0) {
                            cow.teleportTo(scene.origin.x, scene.origin.y, scene.origin.z + 1.5);
                            // as after a reload: the death source is not saved
                            var registries = scene.level.registryAccess();
                            GerLivingState reloaded = new GerLivingState();
                            reloaded.deserializeNBT(registries, state(cow).serializeNBT(registries));
                            cow.setData(GoldExperienceRequiemAddon.LIVING_STATE, reloaded);
                            sourcesAtRevive = sources.size();
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (sources.size() > sourcesAtRevive) {
                            DamageSource rekill = sources.get(sourcesAtRevive);
                            scene.log("rekill=" + rekill.type().msgId());
                            helper.assertTrue(rekill.is(DamageTypes.FELL_OUT_OF_WORLD),
                                    scene.id + ": the rekill without a stored source was " + rekill.type().msgId());
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * Ten loop deaths of a mob: every rekill uses the death source the loop
     * accepted, and the loot and experience drop once, at the final death.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 900, batch = "ger_undie_mob_loop")
    public static void undieMobLoopDropsOnceAtTheFinalDeath(GameTestHelper helper) {
        new Sequence(helper, "undie/mob-loop", 850) {
            private Actor actor;
            private ObservedCow cow;
            private LoopWatch watch;
            private final List<DamageSource> lethal = new ArrayList<>();
            private final List<Boolean> deathEvents = new ArrayList<>();
            private final List<Integer> dropEvents = new ArrayList<>();
            private final List<Integer> experienceEvents = new ArrayList<>();
            private int droppedBeforeFinal;
            private int revivals;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = markableCow(scene, scene.origin.add(0, 0, 1.5));
                        watch = new LoopWatch(cow);
                        scene.listen(LivingIncomingDamageEvent.class, event -> {
                            if (event.getEntity() == cow && cow.getHealth() > 0) {
                                lethal.add(event.getSource());
                            }
                        });
                        scene.listen(LivingDeathEvent.class, event -> {
                            if (event.getEntity() == cow) {
                                deathEvents.add(event.isCanceled());
                            }
                        });
                        scene.listen(LivingDropsEvent.class, event -> {
                            if (event.getEntity() == cow) {
                                dropEvents.add(event.isCanceled() ? -1 : event.getDrops().size());
                            }
                        });
                        scene.listen(LivingExperienceDropEvent.class, event -> {
                            if (event.getEntity() == cow) {
                                experienceEvents.add(event.isCanceled() ? -1 : event.getDroppedExperience());
                            }
                        });
                        scene.listen(EntityJoinLevelEvent.class, event -> {
                            if ((event.getEntity() instanceof ItemEntity || event.getEntity() instanceof ExperienceOrb)
                                    && !finalDeath()) {
                                droppedBeforeFinal++;
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        scene.pressAction(actor, cow, UNDIE, UNDIE, InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        stage = 2;
                    }
                    case 2 -> {
                        watch.observe();
                        if (cow.getHealth() > 0 && state(cow).undieDeaths() > revivals) {
                            revivals = state(cow).undieDeaths();
                            watch.bringBack();
                        }
                        if (finalDeath()) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private boolean finalDeath() {
                return deathEvents.contains(Boolean.FALSE);
            }

            private void verify() {
                scene.log("revivals=" + state(cow).undieDeaths() + " deathEvents=" + deathEvents + " drops=" + dropEvents
                        + " experience=" + experienceEvents + " droppedBeforeFinal=" + droppedBeforeFinal
                        + " lethal=" + lethal.stream().map(source -> source.type().msgId()).toList());
                List<String> payloads = undiePayloads(actor.received(), cow.getId());
                helper.assertTrue(state(cow).undieDeaths() == LOOPS && payloads.equals(loopPayloads()),
                        scene.id + ": " + state(cow).undieDeaths() + " revivals, players were sent " + payloads);
                helper.assertTrue(deathEvents.equals(List.of(Boolean.FALSE)),
                        scene.id + ": death events " + deathEvents + ", expected only the final one");
                helper.assertTrue(droppedBeforeFinal == 0,
                        scene.id + ": " + droppedBeforeFinal + " drops before the final death");
                helper.assertTrue(dropEvents.size() == 1 && dropEvents.get(0) > 0,
                        scene.id + ": the loot dropped as " + dropEvents);
                helper.assertTrue(experienceEvents.size() == 1 && experienceEvents.get(0) > 0,
                        scene.id + ": the experience dropped as " + experienceEvents);
                helper.assertTrue(lethal.size() == LOOPS + 1,
                        scene.id + ": " + lethal.size() + " lethal hits for " + (LOOPS + 1) + " deaths");
                DamageSource accepted = lethal.get(0);
                helper.assertTrue(lethal.stream().allMatch(source -> source == accepted),
                        scene.id + ": a rekill did not reuse the accepted death source "
                                + accepted.type().msgId());
            }
        }.start();
    }

    /**
     * A Stand user's loop with keepStandOnDeath off and dropStandDisc on: no
     * disc and no Stand loss until the final death, which drops what an
     * ordinary death drops, once; its soul stays the caster's (1.16 SoulEntityMixin).
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 900, batch = "ger_undie_player_loop")
    public static void undiePlayerLoopKeepsTheStandAndDropsOnceAtTheFinalDeath(GameTestHelper helper) {
        new Sequence(helper, "undie/player-loop", 850) {
            private Actor caster;
            private Actor bystander;
            private Client victim;
            private LoopWatch watch;
            private final List<Boolean> deathEvents = new ArrayList<>();
            private final List<List<ItemStack>> dropEvents = new ArrayList<>();
            private final List<Integer> experienceEvents = new ArrayList<>();
            private int discsBeforeFinal;
            private int discsAtFinal;
            private boolean lostStand;
            private int revivals;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        ModConfigSpec.BooleanValue keep = config("keepStandOnDeath");
                        ModConfigSpec.BooleanValue drop = config("dropStandDisc");
                        boolean keptBefore = keep.get();
                        boolean droppedBefore = drop.get();
                        // Spec setters are memory-only; the user's file is never saved.
                        scene.onClose(() -> {
                            keep.set(keptBefore);
                            drop.set(droppedBefore);
                        });
                        keep.set(false);
                        drop.set(true);
                        caster = scene.actor();
                        bystander = scene.actor(scene.origin.add(3, 0, 0), 0, null);
                        victim = scene.client(scene.origin.add(0, 0, 2));
                        StandPower victimPower = PowerClass.STAND.attachGet(victim.player());
                        scene.premise(StandPowerTransitions.insert(victimPower,
                                        new StandInstance(ModStands.STAR_PLATINUM.get())).status()
                                        == StandPowerTransitions.Status.APPLIED,
                                "the victim has a Stand");
                        victim.player().getInventory().add(new ItemStack(Items.DIRT, 5));
                        victim.player().giveExperienceLevels(5);
                        watch = new LoopWatch(victim.player());
                        scene.listen(LivingDeathEvent.class, event -> {
                            if (event.getEntity() == victim.player()) {
                                deathEvents.add(event.isCanceled());
                            }
                        });
                        scene.listen(LivingDropsEvent.class, event -> {
                            if (event.getEntity() == victim.player()) {
                                dropEvents.add(event.getDrops().stream().map(ItemEntity::getItem).toList());
                            }
                        });
                        scene.listen(LivingExperienceDropEvent.class, event -> {
                            if (event.getEntity() == victim.player()) {
                                experienceEvents.add(event.isCanceled() ? -1 : event.getDroppedExperience());
                            }
                        });
                        scene.listen(EntityJoinLevelEvent.class, event -> {
                            if (event.getEntity() instanceof ItemEntity item
                                    && item.getItem().getItem() instanceof StandDiscItem) {
                                // the core drops the disc in its death handler, ahead of this test's
                                if (state(victim.player()).undieDeaths() >= LOOPS) {
                                    discsAtFinal++;
                                }
                                else {
                                    discsBeforeFinal++;
                                }
                            }
                        });
                        stage = 1;
                    }
                    case 1 -> {
                        if (victim.player().tickCount > 60) {
                            state(victim.player()).armUndie(caster.user().getUUID());
                            scene.premise(victim.player().hurt(scene.level.damageSources().generic(), 1000),
                                    "the armed player took the lethal hit");
                            stage = 2;
                        }
                    }
                    case 2 -> {
                        watch.observe();
                        if (victim.player().getHealth() > 0 && state(victim.player()).undieDeaths() > revivals) {
                            revivals = state(victim.player()).undieDeaths();
                            StandPower power = StandPower.get(victim.player());
                            lostStand |= power == null || !power.hasPower();
                            watch.bringBack();
                        }
                        if (finalDeath()) {
                            verify();
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private boolean finalDeath() {
                return deathEvents.contains(Boolean.FALSE);
            }

            private void verify() {
                List<ItemStack> drops = dropEvents.isEmpty() ? List.of() : dropEvents.get(0);
                scene.log("revivals=" + state(victim.player()).undieDeaths() + " deathEvents=" + deathEvents + " discs=" + discsBeforeFinal
                        + "/" + discsAtFinal + " drops=" + dropEvents + " experience=" + experienceEvents
                        + " combatKills=" + combatKills(victim.received()) + " lostStand=" + lostStand);
                List<String> payloads = undiePayloads(victim.received(), victim.player().getId());
                helper.assertTrue(state(victim.player()).undieDeaths() == LOOPS && payloads.equals(loopPayloads()),
                        scene.id + ": " + state(victim.player()).undieDeaths() + " revivals, the victim was sent "
                                + payloads);
                helper.assertTrue(deathEvents.equals(List.of(Boolean.FALSE)),
                        scene.id + ": death events " + deathEvents + ", expected only the final one");
                helper.assertTrue(discsBeforeFinal == 0, scene.id + ": the loop deaths dropped "
                        + discsBeforeFinal + " Stand discs");
                helper.assertTrue(!lostStand, scene.id + ": the victim lost its Stand in the loop");
                helper.assertTrue(discsAtFinal == 1, scene.id + ": the final death dropped " + discsAtFinal + " discs");
                helper.assertTrue(dropEvents.size() == 1
                                && drops.stream().anyMatch(stack -> stack.is(Items.DIRT) && stack.getCount() == 5),
                        scene.id + ": the inventory dropped as " + dropEvents);
                helper.assertTrue(experienceEvents.size() == 1 && experienceEvents.get(0) > 0,
                        scene.id + ": the experience dropped as " + experienceEvents);
                helper.assertTrue(combatKills(victim.received()) == 1,
                        scene.id + ": the victim saw " + combatKills(victim.received()) + " death screens");
                helper.assertTrue(soulGoesTo(victim.player(), caster) && !soulGoesTo(victim.player(), bystander),
                        scene.id + ": the final soul is not restricted to the caster");
            }
        }.start();
    }

    static ModConfigSpec.BooleanValue config(String key) {
        return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Keep Powers After Death", key));
    }

    /**
     * Donor StandEntityHeavyAttack: RECOVERY reads the attack speed when it
     * starts, for GER's Undie action subclass too (recoveryPhaseEntryTiming).
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 400, batch = "ger_undie_recovery")
    public static void undiePunchRecoveryReadsSpeedAtEntry(GameTestHelper helper) {
        new Sequence(helper, "undie/recovery", 300) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance punch;
            private float startRecovery;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        // 200 health: not markable, so no loop starts
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        actor.stand().setFinisherMeter(0);
                        stage = 1;
                    }
                    case 1 -> {
                        punch = scene.pressAction(actor, cow, UNDIE, UNDIE,
                                InputMethod.CLICK, GerMeleeGameTestScene.SPECIAL);
                        startRecovery = punch.phasesLength.getFloat(ActionPhase.RECOVERY);
                        double startSpeed = actor.stand().getAttackSpeed();
                        actor.user().getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(5);
                        StandEntity.updateStandStatAttributes(actor.stand(), actor.user());
                        actor.stand().updateStrengthMultipliers();
                        scene.premise(actor.stand().getAttackSpeed() != startSpeed,
                                "the attack speed changed during the windup");
                        stage = 2;
                    }
                    case 2 -> {
                        if (punch.getPhase() == ActionPhase.RECOVERY) {
                            float expected = StandStatFormulas.getHeavyAttackRecovery(
                                    actor.stand().getAttackSpeed(), ((StandEntityHeavyPunch) punch).finisherValue);
                            scene.log("start=" + startRecovery + " expected=" + expected + " actual="
                                    + punch.phasesLength.getFloat(ActionPhase.RECOVERY));
                            scene.premise(expected != startRecovery, "the speed change moves RECOVERY");
                            helper.assertTrue(punch.getCurPhaseLength() == expected,
                                    scene.id + ": RECOVERY " + punch.getCurPhaseLength()
                                            + " was not read at entry, expected " + expected);
                            return true;
                        }
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }
}
