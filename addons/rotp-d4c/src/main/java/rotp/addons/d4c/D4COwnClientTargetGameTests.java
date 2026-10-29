package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.network.AnnihilationMarkPayload;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

/**
 * 1.16 ran the checks of annihilation_mark (aa/ab.java:52-62: its own 15-block look ray, or the mark), annihilation
 * (aa/aa.java:63-72: the mark), pullin_parallel_world_travel (aa/aj.java:67-73: a living box within 4 blocks of the
 * look), minion_ai_control (aa/aa/ad.java:48-57: an own minion near) and summon_kick (its client's mouse target,
 * PowerBaseImpl.checkTarget :289-313, Action.checkRangeAndTarget :162-204) on the user's own client as on the server,
 * for its HUD and its own press; the server told that client its mark (al/aa.java:47-79, ak/aa/aa/aa.java). The first
 * two were checkStandConditions, which StandEntityAction.checkConditions (:98-106) asked only with the Stand out. The
 * client's copy of the user stands in as the core's OwnClientAim116GameTests do: a player that is its own client's
 * (isLocalPlayer), with the server player's UUID, outside the level, its aim in LivingComponentAction.CLIENT_PLAYER_AIM.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4COwnClientTargetGameTests {
    private static final String TOO_FAR = "jojo.message.action_condition.target_too_far";

    private D4COwnClientTargetGameTests() {}

    @GameTest(template = "empty", batch = "d4cOwnClientTargets", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void targetSpecialsOnTheUsersOwnClientCheckAsIn116(GameTestHelper helper) {
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 16)
                .whenTicking(() -> {}, h -> body(h));
    }

    private static void body(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ServerLevel level = helper.getLevel();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-own-client", true)) {
            // above the barrier walls that close the template, in open air
            Vec3 open = helper.absoluteVec(new Vec3(4.5D, 12.0D, 1.5D));
            scene.owner.moveTo(open.x, open.y, open.z, 180.0F, 0.0F);
            scene.owner.setYHeadRot(180.0F);
            scene.stand.setPos(scene.owner.position().add(-1.0D, 0.0D, 0.0D));

            ClientUser client = new ClientUser(level, scene.owner.getGameProfile());
            client.moveTo(open.x, open.y + 6.0D, open.z, 180.0F, 0.0F);
            client.setYHeadRot(180.0F);
            StandPower clientPower = PowerClass.STAND.attachGet(client);
            clientPower.setStand(D4CAddon.D4C.get());
            clientPower.skipProgression();
            clientPower.setStamina(clientPower.getMaxStamina());
            helper.assertTrue(clientPower.getSummonedStandEntity() == null,
                    "Setup: the client copy starts with D4C away.");

            // the server's rule (premise): its stored aim names a cow off the look line
            Cow serverSide = scene.cow(new Vec3(3.0D, 0.0D, 0.0D));
            aim(scene.owner, new ActionTarget(serverSide));
            row(wrong, scene.power, "annihilation_mark", "server, its client's aim on a cow beside it", true);
            aim(scene.owner, ActionTarget.EMPTY);
            row(wrong, scene.power, "annihilation_mark", "server, no aim and nothing in view", false);
            row(wrong, scene.power, "annihilation", "server, no mark", false);

            // the user's own client with D4C out: its look ray, the aim it sends, nothing
            scene.showStandOnClientCopy(clientPower, true);
            helper.assertTrue(clientPower.getSummonedStandEntity() == scene.stand,
                    "Setup: the client copy does not have D4C out.");
            Cow ahead = scene.cow(new Vec3(0.0D, 6.0D, -3.0D));
            Cow beside = scene.cow(new Vec3(3.0D, 6.0D, 0.0D));
            lookAt(client, ahead);
            row(wrong, clientPower, "annihilation_mark", "client, a cow 3 blocks ahead in view", true);
            row(wrong, clientPower, "pullin_parallel_world_travel", "client, a cow 3 blocks ahead in view", true);
            client.setYRot(0.0F);
            client.setXRot(0.0F);
            client.setYHeadRot(0.0F);
            row(wrong, clientPower, "annihilation_mark", "client, nothing in view, no aim, no mark", false);
            row(wrong, clientPower, "annihilation", "client, no mark", false);
            row(wrong, clientPower, "pullin_parallel_world_travel", "client, nothing within 4 blocks of the look",
                    false);
            // 1.16 asked the first two only with the Stand out (StandEntityAction.checkConditions :98-106, their
            // checkStandConditions): with D4C away its HUD showed them usable and the server refused once it had
            // summoned D4C; pullin's checkConditions it ran either way
            scene.showStandOnClientCopy(clientPower, false);
            row(wrong, clientPower, "annihilation_mark", "client, D4C away, nothing in view, no aim, no mark", true);
            row(wrong, clientPower, "annihilation", "client, D4C away, no mark", true);
            row(wrong, clientPower, "pullin_parallel_world_travel",
                    "client, D4C away, nothing within 4 blocks of the look", false);
            scene.showStandOnClientCopy(clientPower, true);
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(beside));
            row(wrong, clientPower, "annihilation_mark", "client, the aim it sends on a cow beside it", true);
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);

            // the mark: the server tells the client, the client checks with it
            Cow marked = scene.cow(new Vec3(2.0D, 0.0D, 2.0D));
            scene.owner.getPersistentData().putUUID(D4CGameTestScene.ANNIHILATION_TARGET, marked.getUUID());
            row(wrong, scene.power, "annihilation", "server, the cow marked", true);
            int sentBefore = scene.payloads(AnnihilationMarkPayload.class).size();
            D4CPhase2BService.syncMarkToClient(scene.owner);
            List<AnnihilationMarkPayload> sent = scene.payloads(AnnihilationMarkPayload.class);
            if (sent.size() != sentBefore + 1 || sent.get(sent.size() - 1).entityId() != marked.getId()) {
                wrong.add("the server did not tell its client the mark it set (1.16 ak/aa/aa/aa.java): sent " + ids(sent)
                        + ", the cow is " + marked.getId());
            }
            D4CPhase2BService.syncMarkToClient(scene.owner);
            if (scene.payloads(AnnihilationMarkPayload.class).size() != sent.size()) {
                wrong.add("the server told its client an unchanged mark again");
            }
            if (!sent.isEmpty()) {
                D4CPhase2BService.acceptClientMark(sent.get(sent.size() - 1).entityId());
            }
            row(wrong, clientPower, "annihilation", "client, told the cow is marked", true);
            row(wrong, clientPower, "annihilation_mark", "client, nothing in view, told the cow is marked", true);
            client.local = false;
            row(wrong, clientPower, "annihilation", "another player's copy on a client (no mark of its own)", false);
            client.local = true;
            scene.owner.getPersistentData().remove(D4CGameTestScene.ANNIHILATION_TARGET);
            D4CPhase2BService.syncMarkToClient(scene.owner);
            sent = scene.payloads(AnnihilationMarkPayload.class);
            if (sent.isEmpty() || sent.get(sent.size() - 1).entityId() != AnnihilationMarkPayload.NONE) {
                wrong.add("the server did not tell its client the mark was lifted: sent " + ids(sent));
            }
            else {
                D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);
            }
            row(wrong, clientPower, "annihilation", "client, told the mark was lifted", false);
            row(wrong, clientPower, "annihilation_mark", "client, nothing in view, the mark lifted", false);
            scene.showStandOnClientCopy(clientPower, false);
            row(wrong, clientPower, "annihilation", "client, D4C away, the mark lifted", true);
            row(wrong, clientPower, "annihilation_mark", "client, D4C away, nothing in view, the mark lifted", true);

            // minion_ai_control: an own minion near (its owner is synced), D4C away on the client copy
            row(wrong, clientPower, "minion_ai_control", "client, no minion", false);
            scene.minion(new Vec3(-3.0D, 0.0D, -3.0D));
            row(wrong, scene.power, "minion_ai_control", "server, an own minion near", true);
            row(wrong, clientPower, "minion_ai_control", "client, an own minion near", true);

            // summon_kick: the client's mouse target, D4C not out on the client copy
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(ahead));
            lookAt(client, ahead);
            row(wrong, clientPower, "summon_kick", "client, the aim it sends on a cow 3 blocks ahead", true);
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            row(wrong, clientPower, "summon_kick", "client, the aim it sends empty", false);
            Cow far = scene.cow(new Vec3(0.0D, 6.0D, -10.0D));
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(new ActionTarget(far));
            lookAt(client, far);
            ConditionCheck tooFar = clientPower.getAbility("summon_kick").checkConditions(clientPower);
            if (tooFar.isPositive() || !TOO_FAR.equals(key(tooFar))) {
                wrong.add("client, the aim it sends on a cow 10 blocks away: summon_kick " + describe(tooFar)
                        + ", 1.16 refused it with target_too_far");
            }

            // the server with D4C away refuses before its summon, with the message 1.16 gave after it
            D4CAddon.D4C.get().forceUnsummon(scene.owner, scene.power);
            aim(scene.owner, ActionTarget.EMPTY);
            refusedWith(wrong, scene.power, "annihilation_mark", "server, D4C away, nothing in view, no mark",
                    D4CPhase2BService.MSG_ANNIHILATION_MARK_NEED_ENEMY);
            refusedWith(wrong, scene.power, "annihilation", "server, D4C away, no mark",
                    D4CPhase2BService.MSG_ANNIHILATION_NEED_MARK);
        }
        finally {
            LivingComponentAction.CLIENT_PLAYER_AIM.setTarget(ActionTarget.EMPTY);
            D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 checked D4C's target specials on the user's own client as on the"
                + " server: " + wrong);
        helper.succeed();
    }

    private static void row(List<String> wrong, StandPower power, String name, String setup, boolean usable) {
        Ability ability = power.getAbility(name);
        if (ability == null) {
            wrong.add("fixture: D4C has no " + name);
            return;
        }
        ConditionCheck check = ability.checkConditions(power);
        if (check.isPositive() != usable) {
            wrong.add(setup + ": " + name + " " + describe(check) + ", 1.16 " + (usable ? "showed it usable"
                    : "refused it"));
        }
    }

    private static void refusedWith(List<String> wrong, StandPower power, String name, String setup, String key) {
        ConditionCheck check = power.getAbility(name).checkConditions(power);
        if (check.isPositive() || !key.equals(key(check))) {
            wrong.add(setup + ": " + name + " " + describe(check) + ", 1.16 refused it with " + key);
        }
    }

    private static String describe(ConditionCheck check) {
        return check.isPositive() ? "usable" : "refused (" + key(check) + ")";
    }

    private static String key(ConditionCheck check) {
        if (check.getWarning() == null) {
            return null;
        }
        return check.getWarning().getContents() instanceof TranslatableContents contents ? contents.getKey()
                : check.getWarning().getString();
    }

    private static List<Integer> ids(List<AnnihilationMarkPayload> sent) {
        List<Integer> ids = new ArrayList<>();
        sent.forEach(payload -> ids.add(payload.entityId()));
        return ids;
    }

    private static void aim(Player player, ActionTarget target) {
        LivingComponentAction.getComponent(player).entityAim.setTarget(target);
    }

    private static void lookAt(Player player, Entity target) {
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(player.getEyePosition());
        player.setYRot((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(aim.y, Math.sqrt(aim.x * aim.x + aim.z * aim.z))));
        player.setYHeadRot(player.getYRot());
    }

    // the user's own client's copy of it (LocalPlayer.isLocalPlayer) while local is set
    private static final class ClientUser extends Player {
        boolean local = true;

        ClientUser(ServerLevel level, GameProfile profile) {
            super(level, BlockPos.ZERO, 0.0F, profile);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isLocalPlayer() {
            return local;
        }
    }
}
