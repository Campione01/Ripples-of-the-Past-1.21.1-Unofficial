package rotp.addons.d4c;

import java.util.ArrayList;
import java.util.List;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.d4c.network.AnnihilationMarkPayload;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;

/**
 * 1.16 kept the Annihilation Mark in a server map (015 al/aa.java :44-45). Reading it (ab(user) :81-91) only looked
 * the target up in the user's world; onServerTick :162-207 dropped it, once every 20 server ticks (:171), when the
 * marker or the target was found in no world, either was dead, the two were in different dimensions, or they were more
 * than 20 blocks apart (:43, :199-201). The client's copy (ak/aa/aa/aa.java) is the payload the port sends when the
 * server's mark changes.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMarkLifetimeGameTests {
    private static final String TAG = D4CGameTestScene.ANNIHILATION_TARGET;

    private D4CMarkLifetimeGameTests() {}

    @GameTest(template = "empty", batch = "d4cMarkLifetimeRules", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void markLapsesOnlyOn116sChecks(GameTestHelper helper) {
        Vec3 start = helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D));
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 8)
                .cover(start.add(20.5D, 0.0D, 0.0D))
                .cover(start.add(55.0D, 0.0D, 0.0D))
                .whenTicking(() -> {}, h -> rules(h));
    }

    private static void rules(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-mark-rules")) {
            Cow cow = scene.cow(new Vec3(19.5D, 0.0D, 0.0D));
            mark(scene, cow);
            check(wrong, scene, "a cow 19.5 blocks away", false);
            usable(wrong, scene.power, "a cow 19.5 blocks away, after the check", true);

            cow.moveTo(scene.owner.position().add(55.0D, 0.0D, 0.0D));
            usable(wrong, scene.power, "the cow 55 blocks away, before the next check", true);
            ClientUser client = new ClientUser(helper.getLevel(), scene.owner.getGameProfile());
            client.moveTo(scene.owner.position());
            StandPower clientPower = PowerClass.STAND.attachGet(client);
            clientPower.setStand(D4CAddon.D4C.get());
            clientPower.skipProgression();
            clientPower.setStamina(clientPower.getMaxStamina());
            // the client checks the mark only with D4C out (1.16 checkStandConditions)
            scene.showStandOnClientCopy(clientPower, true);
            D4CPhase2BService.acceptClientMark(cow.getId());
            usable(wrong, clientPower, "client, told the cow 55 blocks away is marked", true);
            D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);

            cow.moveTo(scene.owner.position().add(20.5D, 0.0D, 0.0D));
            check(wrong, scene, "the cow 20.5 blocks away", true);
            ConditionCheck after = scene.power.getAbility("annihilation").checkConditions(scene.power);
            if (after.isPositive() || after.getWarning() == null
                    || !(after.getWarning().getContents() instanceof TranslatableContents contents)
                    || !D4CPhase2BService.MSG_ANNIHILATION_NEED_MARK.equals(contents.getKey())) {
                wrong.add("the mark dropped: annihilation " + (after.isPositive() ? "usable" : "refused without "
                        + D4CPhase2BService.MSG_ANNIHILATION_NEED_MARK));
            }

            cow.moveTo(scene.owner.position().add(5.0D, 0.0D, 0.0D));
            mark(scene, cow);
            scene.owner.setHealth(0.0F);
            check(wrong, scene, "the marker dead", true);
            scene.owner.setHealth(scene.owner.getMaxHealth());

            mark(scene, cow);
            cow.kill();
            // ab(user) :81-91 still found it until the check below or its removal
            usable(wrong, scene.power, "the marked cow dying, before the next check", true);
            D4CPhase2BService.acceptClientMark(cow.getId());
            usable(wrong, clientPower, "client, told the dying cow is marked", true);
            D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);
            check(wrong, scene, "the cow dead", true);

            Cow gone = scene.cow(new Vec3(5.0D, 0.0D, 0.0D));
            mark(scene, gone);
            gone.discard();
            check(wrong, scene, "the cow gone", true);

            Cow kept = scene.cow(new Vec3(5.0D, 0.0D, 0.0D));
            mark(scene, kept);
            EventHooks.firePlayerLoggedOut(scene.owner);
            if (scene.owner.getPersistentData().hasUUID(TAG)) {
                wrong.add("the marker left the server: the mark stayed, 1.16 found no marker and dropped it");
            }
        }
        finally {
            D4CPhase2BService.acceptClientMark(AnnihilationMarkPayload.NONE);
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 al/aa.java onServerTick :162-207: " + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cMarkLifetimeDimension", timeoutTicks = 80 + GameTestChunkTickets.WAIT_TICKS)
    public static void markOnATargetInAnotherDimensionLapsesAsIn116(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "fixture: the server has no Nether");
        Vec3 start = helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D));
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 8)
                .whenTicking(() -> {}, h -> new GameTestChunkTickets(h, nether).cover(start)
                        .whenTicking(() -> {}, later -> dimension(later, nether)));
    }

    private static void dimension(GameTestHelper helper, ServerLevel nether) {
        List<String> wrong = new ArrayList<>();
        Cow cow = EntityType.COW.create(nether);
        helper.assertTrue(cow != null, "fixture: no cow");
        try (D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-mark-dimension")) {
            cow.setNoAi(true);
            cow.setNoGravity(true);
            cow.moveTo(scene.owner.position().add(2.0D, 0.0D, 0.0D));
            helper.assertTrue(nether.addFreshEntity(cow) && nether.getEntity(cow.getUUID()) == cow,
                    "fixture: the cow did not join the Nether");
            mark(scene, cow);
            check(wrong, scene, "the cow 2 blocks off, in the Nether", true);
        }
        finally {
            cow.discard();
        }
        helper.assertTrue(wrong.isEmpty(), "1.16 al/aa.java :196-197: " + wrong);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "d4cMarkLifetimeTicks", timeoutTicks = 100 + GameTestChunkTickets.WAIT_TICKS)
    public static void markIsCheckedEveryTwentyServerTicksAsIn116(GameTestHelper helper) {
        Vec3 start = helper.absoluteVec(new Vec3(4.5D, 4.0D, 1.5D));
        GameTestChunkTickets.forTest(helper).coverAround(helper.absolutePos(BlockPos.ZERO), 8)
                .cover(start.add(25.0D, 0.0D, 0.0D))
                .whenTicking(() -> {}, h -> ticks(h));
    }

    private static void ticks(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        // a player in the server's list, as the check walks it
        D4CGameTestScene scene = new D4CGameTestScene(helper, "d4c-mark-ticks", true);
        Cow cow = scene.cow(new Vec3(25.0D, 0.0D, 0.0D));
        List<String> wrong = new ArrayList<>();
        int[] setAt = {-1};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            int now = server.getTickCount();
            if (setAt[0] < 0) {
                if (now % 20 == 5) {
                    mark(scene, cow);
                    setAt[0] = now;
                    D4CPhase2BService.syncMarkToClient(scene.owner);
                    List<AnnihilationMarkPayload> sent = scene.payloads(AnnihilationMarkPayload.class);
                    if (sent.isEmpty() || sent.get(sent.size() - 1).entityId() != cow.getId()) {
                        wrong.add("the set mark was not sent to the client");
                    }
                }
                return;
            }
            int check = setAt[0] + 15;
            boolean marked = scene.owner.getPersistentData().hasUUID(TAG);
            if (now < check && !marked) {
                wrong.add("server tick " + now + ": dropped before the check at tick " + check);
            }
            if (now > check && marked) {
                wrong.add("server tick " + now + ": still marked after the check at tick " + check);
            }
            if (now > check + 1 || now > setAt[0] + 40) {
                D4CPhase2BService.syncMarkToClient(scene.owner);
                List<AnnihilationMarkPayload> sent = scene.payloads(AnnihilationMarkPayload.class);
                if (sent.isEmpty() || sent.get(sent.size() - 1).entityId() != AnnihilationMarkPayload.NONE) {
                    wrong.add("the dropped mark was not lifted on the client");
                }
                done[0] = true;
                scene.close();
                helper.assertTrue(wrong.isEmpty(), "1.16 al/aa.java :171: a cow 25 blocks away stays marked until"
                        + " the next server tick that is a multiple of 20: " + wrong);
                helper.succeed();
            }
        });
    }

    private static void mark(D4CGameTestScene scene, Cow cow) {
        scene.owner.getPersistentData().putUUID(TAG, cow.getUUID());
    }

    private static void check(List<String> wrong, D4CGameTestScene scene, String setup, boolean drops) {
        boolean dropped = D4CPhase2BService.dropLapsedMark(scene.owner);
        boolean marked = scene.owner.getPersistentData().hasUUID(TAG);
        if (dropped != drops || marked == drops) {
            wrong.add(setup + ": the check " + (dropped ? "dropped" : "kept") + " the mark, 1.16 "
                    + (drops ? "dropped" : "kept") + " it");
        }
    }

    private static void usable(List<String> wrong, StandPower power, String setup, boolean usable) {
        ConditionCheck check = power.getAbility("annihilation").checkConditions(power);
        if (check.isPositive() != usable) {
            wrong.add(setup + ": annihilation " + (check.isPositive() ? "usable" : "refused") + ", 1.16 "
                    + (usable ? "usable" : "refused"));
        }
    }

    // the user's own client's copy of it (LocalPlayer.isLocalPlayer), as D4COwnClientTargetGameTests stages it
    private static final class ClientUser extends Player {
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
            return true;
        }
    }
}
