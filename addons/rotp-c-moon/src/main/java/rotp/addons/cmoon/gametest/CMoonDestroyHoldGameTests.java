package rotp.addons.cmoon.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.cmoon.CMoonAddon;
import rotp.addons.cmoon.ability.DestroyAbility.DestroyAction;
import rotp.addons.cmoon.entity.CMoonStandEntity;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 Destroy had TargetRequirement.ENTITY and holdToFire(10, false). PowerBaseImpl.checkTarget refused the press
 * without an entity under the crosshair, and tickHeldAction asked the crosshair again on every held tick: the charge
 * ended (nothing fired, no cooldown) once the view left the mob, went past 8 blocks ("target_too_far"), or came to
 * the user's own wolf (StandEntity.canAttack, silently) or a Stand ("cant_attack_stand"). So it could not be charged
 * before aiming.
 */
@GameTestHolder(CMoonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CMoonDestroyHoldGameTests {
    private static final short KEY = 125;

    private CMoonDestroyHoldGameTests() {}

    @GameTest(template = "empty", batch = "cm_destroy_hold", timeoutTicks = 200)
    public static void destroyChargeStopsWhenTheViewLeavesItsTarget(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.userPos),
                        "C-Moon's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.run();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    // 1.16 PowerBaseImpl.performAction set a held StandEntityAction's cooldown as its hold fired, in the charge's last
    // tick (053 InitStands :71-73: Center holdToFire(10), Gravity Shift holdToFire(25), Destroy holdToFire(10)); the
    // Stand's task ran the perform from its next tick, so the cooldown is there before the perform's first tick.
    @GameTest(template = "empty", batch = "cm_charge_cooldown", timeoutTicks = 200)
    public static void gravityChargesTakeTheirCooldownAsTheyFireAsIn116(GameTestHelper helper) {
        Scene scene = new Scene(helper);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().isPositionEntityTicking(scene.userPos),
                        "C-Moon's chunk never started ticking entities."))
                .thenExecute(() -> {
                    try {
                        scene.runCooldowns();
                    }
                    finally {
                        scene.close();
                    }
                })
                .thenSucceed();
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos userPos;
        private final RecordingPlayer user;
        private final List<Entity> spawned = new ArrayList<>();
        private StandPower power;
        private CMoonStandEntity stand;
        private boolean closed;

        private Scene(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            BlockPos template = helper.absolutePos(new BlockPos(0, 2, 0));
            ChunkPos chunk = new ChunkPos(template);
            userPos = new BlockPos(chunk.getMinBlockX() + 2, template.getY() + 8, chunk.getMinBlockZ() + 8);
            user = makePlayer(level, userPos);
            user.setNoGravity(true);
            user.setGameMode(GameType.SURVIVAL);
        }

        private void run() {
            setUp();

            Mob cow = mob(EntityType.COW, 3.0D, 0.0D);
            Mob farCow = mob(EntityType.COW, 12.0D, 0.0D);
            Wolf wolf = (Wolf) mob(EntityType.WOLF, 2.0D, 1.5D);
            wolf.tame(user);
            helper.assertTrue(wolf.getOwner() == user, "fixture: the wolf is not the user's");

            aim(null);
            helper.assertTrue(!pressable(),
                    "1.16 refused a Destroy pressed with no entity under the crosshair, so it could not be pre-charged");

            press(cow);
            tickHold();
            helper.assertTrue(running(), "fixture: the Destroy charge must go on while the view stays on the cow");
            aim(null);
            tickHold();
            expectStopped("the view left the cow", null);

            press(cow);
            tickHold();
            aim(farCow);
            tickHold();
            expectStopped("the crosshair went to a cow 12 blocks away", "target_too_far");

            press(cow);
            tickHold();
            aim(wolf);
            tickHold();
            expectStopped("the crosshair went to the user's own wolf", null);

            press(cow);
            tickHold();
            aim(stand);
            tickHold();
            expectStopped("the crosshair went to a Stand", "cant_attack_stand");

            press(cow);
            for (int i = 0; i < 15 && running() && !stand.hasAutoAttackTarget(); i++) {
                tickHold();
            }
            helper.assertTrue(stand.getAutoAttackTarget() == cow,
                    "a charge whose view stayed on the cow sets it as Destroy's target; got "
                            + stand.getAutoAttackTarget());
            LogUtils.getLogger().info("R770_CM_DESTROY_HOLD_PASS target={}", cow.getUUID());
        }

        private void runCooldowns() {
            setUp();
            Mob cow = mob(EntityType.COW, 3.0D, 0.0D);
            List<String> wrong = new ArrayList<>();
            List<String> seen = new ArrayList<>();
            for (String name : List.of(CMoonAddon.DESTROY, CMoonAddon.GRAVITY_SHIFT, CMoonAddon.CENTER_OF_GRAVITY)) {
                power.resetAbilityCooldowns();
                power.setStamina(power.getMaxStamina());
                press(name, cow);
                EntityActionInstance action = stand.getCurStandAction();
                int charge = (int) action.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE);
                int expected = power.getAbility(name).getCooldown(power, -1);
                int ticks = 0;
                boolean early = false;
                while (running() && action.getPhase() == ActionPhase.BUTTON_CHARGE && ticks < 40) {
                    early |= power.isAbilityOnCooldown(name);
                    tickHold();
                    ticks++;
                }
                int atFire = power.getAbilityCooldown(name);
                boolean waiting = !action.isOver() && action.getPhase() == ActionPhase.PERFORM
                        && action.getPhaseTick() == 0.0F;
                tickHold();
                int afterPerform = power.getAbilityCooldown(name);
                String row = name + ": charge " + ticks + "/" + charge + ", perform waiting " + waiting
                        + ", cooldown during the charge " + early + ", at the fire " + atFire
                        + ", after the perform's first tick " + afterPerform + ", 1.16 " + expected;
                seen.add(row);
                if (ticks != charge || !waiting || early || expected <= 0 || atFire != expected
                        || afterPerform != expected) {
                    wrong.add(row);
                }
            }
            helper.assertTrue(wrong.isEmpty(), "1.16 set a fired charge's cooldown in its last tick, before the"
                    + " Stand's perform: " + String.join("; ", wrong));
            LogUtils.getLogger().info("R770_CM_CHARGE_COOLDOWN_PASS {}", seen);
        }

        private void setUp() {
            power = PowerClass.STAND.attachGet(user);
            helper.assertTrue(StandPowerTransitions.insert(power,
                            new StandInstance(CMoonAddon.C_MOON.get())).status()
                            == StandPowerTransitions.Status.APPLIED
                            && CMoonAddon.C_MOON.get().summon(user, power)
                            && power.getSummonedStandEntity() instanceof CMoonStandEntity,
                    "Could not summon C-Moon");
            power.skipProgression();
            power.setStamina(power.getMaxStamina());
            stand = (CMoonStandEntity) power.getSummonedStandEntity();
            stand.updatePosition(user);
        }

        private Mob mob(EntityType<? extends Mob> type, double east, double south) {
            Mob mob = type.create(level);
            helper.assertTrue(mob != null, "Could not make " + type);
            mob.moveTo(user.getX() + east, user.getY(), user.getZ() + south, 0.0F, 0.0F);
            mob.setNoAi(true);
            mob.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(mob), "Could not add " + type);
            spawned.add(mob);
            return mob;
        }

        // What the client sends as the user's and the Stand's crosshair.
        private void aim(Entity target) {
            ActionTarget aimed = target != null ? new ActionTarget(target) : ActionTarget.EMPTY;
            LivingComponentAction.getComponent(user).entityAim.setTarget(aimed);
            LivingComponentAction.getComponent(stand).entityAim.setTarget(aimed);
        }

        private AbilityConditionCheck selected(String name) {
            power.tick(); // moves are cached per tick
            Ability ability = power.getAbility(name);
            AbilityConditionCheck selected = power.updateAvailableMoves().getContextVariationContainer(ability);
            helper.assertTrue(selected != null, name + " is not in the moveset");
            return selected;
        }

        private boolean pressable() {
            return AbilityInput.withConditionCheck(selected(CMoonAddon.DESTROY), user, InputMethod.HOLD);
        }

        private void press(Entity target) {
            press(CMoonAddon.DESTROY, target);
        }

        private void press(String name, Entity target) {
            AbilityInput.keyRelease(KEY, user);
            EntityActionInstance before = stand.getCurStandAction();
            if (before != null) {
                before.forceStop();
                stand.getStandActionComponent().setAction(null, SyncType.NO_SYNC);
            }
            stand.clearAutoAttackTarget();
            user.messages.clear();
            aim(target);
            AbilityConditionCheck selected = selected(name);
            helper.assertTrue(AbilityInput.withConditionCheck(selected, user, InputMethod.HOLD),
                    "the press of " + name + " at " + target + " was refused: "
                            + selected.conditionCheck.getWarning());
            BufferingState state = BufferingState.clickOnly();
            AbilityInput.keyPress(KEY, selected.ability, user, null, InputMethod.HOLD, 0, state,
                    selected.ability.getAbilityId());
            EntityActionInstance started = stand.getCurStandAction();
            helper.assertTrue(state.isActionSuccess && started != null
                            && (!name.equals(CMoonAddon.DESTROY) || started instanceof DestroyAction)
                            && started.ability instanceof Ability ability && ability.name().equals(name)
                            && started.getPhase() == ActionPhase.BUTTON_CHARGE,
                    "the press did not start the " + name + " charge: " + started);
        }

        private void expectStopped(String why, String messageKey) {
            helper.assertTrue(!running(),
                    "1.16 stopped the held Destroy once " + why + "; it still runs: " + stand.getCurStandAction());
            helper.assertTrue(!stand.hasAutoAttackTarget() && !power.isAbilityOnCooldown(CMoonAddon.DESTROY),
                    "1.16 fired nothing after " + why + "; the auto-attack target is " + stand.getAutoAttackTarget()
                            + ", cooldown " + power.getAbilityCooldown(CMoonAddon.DESTROY));
            List<String> expected = messageKey != null
                    ? List.of("jojo.message.action_condition." + messageKey)
                    : List.of();
            helper.assertTrue(user.messages.equals(expected),
                    "1.16's message when " + why + " was " + expected + ", got " + user.messages);
        }

        // One server tick of the running action, with its key still down
        private void tickHold() {
            if (running()) {
                stand.getCurStandAction()._tickAction();
            }
        }

        private boolean running() {
            EntityActionInstance action = stand.getCurStandAction();
            return action != null && !action.isOver();
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                AbilityInput.keyRelease(KEY, user);
                if (power != null && power.isSummoned()) {
                    CMoonAddon.C_MOON.get().forceUnsummon(user, power);
                }
                spawned.forEach(Entity::discard);
            }
            finally {
                user.discard();
            }
        }
    }

    private static RecordingPlayer makePlayer(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "cm-destroy-hold");
        RecordingPlayer player = new RecordingPlayer(server, level, profile);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new SilentGameListener(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(Vec3.atBottomCenterOf(pos));
        level.addNewPlayer(player);
        return player;
    }

    // Keeps the translation keys of the action-bar messages the silent connection drops.
    private static final class RecordingPlayer extends ServerPlayer {
        private final List<String> messages = new ArrayList<>();

        private RecordingPlayer(MinecraftServer server, ServerLevel level, GameProfile profile) {
            super(server, level, profile, ClientInformation.createDefault());
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            messages.add(message.getContents() instanceof TranslatableContents translatable
                    ? translatable.getKey()
                    : message.getString());
        }
    }

    private static final class SilentGameListener extends ServerGamePacketListenerImpl {
        private SilentGameListener(MinecraftServer server, Connection connection, ServerPlayer player,
                CommonListenerCookie cookie) {
            super(server, connection, player, cookie);
        }

        @Override
        public void send(Packet<?> packet) {}

        @Override
        public void send(Packet<?> packet, PacketSendListener sendListener) {}
    }
}
