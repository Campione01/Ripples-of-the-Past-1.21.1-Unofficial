package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.core.core.JojoMod;
import rotp.core.customobjects.entity_projectile.ClackersEntity;
import rotp.core.init.ModItems;
import rotp.core.init.ModSoundEvents;

/**
 * What the server sends for a Clackers impact and pickup. The 1.16.5 Clackers were arrows: the break sound of the
 * block they stick in, the arrow hit sound on a landed entity hit, and the collect packet when they are picked up.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClackersImpactFeedbackGameTests {
    private static final String FALLING = "Motion:[0.0d,-0.5d,0.0d]";
    // 1.16 AbstractArrow: 1.2F / (random.nextFloat() * 0.2F + 0.9F)
    private static final float MIN_PITCH = 1.2F / 1.1F - 1.0E-4F;
    private static final float MAX_PITCH = 1.2F / 0.9F + 1.0E-4F;

    private ClackersImpactFeedbackGameTests() {}

    private record Heard(long time, SoundEvent sound, SoundSource source, float volume, float pitch, Vec3 pos) {
        boolean arrowLike() {
            return source == SoundSource.NEUTRAL && volume == 1.0F && pitch >= MIN_PITCH && pitch <= MAX_PITCH;
        }

        @Override
        public String toString() {
            return sound.getLocation() + " (" + source + ", volume " + volume + ", pitch " + pitch + ")";
        }
    }

    private static List<Heard> hear(ClackersScene scene) {
        List<Heard> heard = new ArrayList<>();
        scene.listen(PlayLevelSoundEvent.AtPosition.class, event -> {
            if (event.getLevel() == scene.level && event.getSound() != null && scene.room.contains(event.getPosition())) {
                heard.add(new Heard(scene.level.getGameTime(), event.getSound().value(), event.getSource(), event.getOriginalVolume(),
                        event.getOriginalPitch(), event.getPosition()));
            }
        });
        return heard;
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_landing_sound", timeoutTicks = 100)
    public static void landingClackersPlayTheBreakSoundOfTheBlockTheyHit(GameTestHelper helper) {
        ClackersScene.start(helper, "landing-sound", 80, scene -> {
            scene.stone(4, -1, 4, 12, -1, 8);
            for (int x = 4; x <= 12; x++) {
                for (int z = 9; z <= 13; z++) {
                    scene.place(scene.cell(x, -1, z), Blocks.OAK_PLANKS.defaultBlockState());
                }
            }
            List<Heard> heard = hear(scene);
            scene.then(() -> {
                scene.summon(scene.point(8.5D, 1.0D, 6.5D), "{" + FALLING + "}");
                scene.summon(scene.point(8.5D, 1.0D, 11.5D), "{" + FALLING + "}");
            });
            scene.await("both Clackers landing", () -> scene.landing(0) != null && scene.landing(1) != null);
            scene.then(() -> {
                SoundEvent[] expected = { SoundEvents.STONE_BREAK, SoundEvents.WOOD_BREAK };
                for (int i = 0; i < 2; i++) {
                    ClackersScene.Step landing = scene.landing(i);
                    Vec3 at = landing.post().pos();
                    List<Heard> here = heard.stream().filter(sound -> sound.pos().distanceTo(at) < 0.01D).toList();
                    scene.check(landing.blockImpact() != null, "fixture: Clackers " + i + " did not land on a block");
                    scene.check(here.size() == 1 && here.get(0).sound() == expected[i] && here.get(0).arrowLike()
                            && here.get(0).time() == landing.post().time(),
                            "1.16: landing Clackers play the break sound of the block they hit, " + expected[i].getLocation()
                                    + " at volume 1.0 and pitch 1.2 / (0.9 to 1.1), once; heard " + here);
                }
                scene.check(heard.stream().noneMatch(sound -> sound.sound() == ModSoundEvents.CLACKERS.get()),
                        "1.16: landing Clackers do not clack, the clack belongs to the item in the hand; heard " + heard);
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_entity_hit_sound", timeoutTicks = 120)
    public static void landedClackersHitPlaysTheArrowHitSound(GameTestHelper helper) {
        ClackersScene.start(helper, "entity-hit-sound", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(user.getX(), user.getEyeY() - 0.1D - 0.7D, user.getZ() + 2.0D), false);
            List<Heard> heard = hear(scene);
            scene.throwClackers(user);
            scene.await("the contact with the target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() < 100.0F,
                        "fixture: not one landed contact with the target");
                List<Heard> hits = heard.stream().filter(sound -> sound.sound() == SoundEvents.ARROW_HIT).toList();
                scene.check(hits.size() == 1 && hits.get(0).arrowLike() && hits.get(0).time() == contact.post().time()
                        && hits.get(0).pos().distanceTo(contact.pre().pos()) < 0.01D,
                        "1.16: a landed Clackers hit plays the arrow hit sound at volume 1.0 and pitch 1.2 / (0.9 to 1.1), once,"
                                + " where the Clackers are; heard " + hits + " among " + heard);
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_refused_hit_sound", timeoutTicks = 120)
    public static void refusedClackersHitPlaysNoArrowHitSound(GameTestHelper helper) {
        ClackersScene.start(helper, "refused-hit-sound", 100, scene -> {
            Player user = scene.thrower(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 3.5D), 0.0F);
            Cow cow = scene.cow(new Vec3(user.getX(), user.getEyeY() - 0.1D - 0.7D, user.getZ() + 2.0D), true);
            List<Heard> heard = hear(scene);
            scene.throwClackers(user);
            scene.await("the contact with the invulnerable target", () -> scene.contact(0) != null);
            scene.then(() -> {
                ClackersScene.Step contact = scene.contact(0);
                scene.check(contact.impacts().size() == 1 && contact.entityImpact().entity() == cow && cow.getHealth() == 100.0F,
                        "fixture: not one refused contact with the target");
                scene.check(heard.stream().noneMatch(sound -> sound.sound() == SoundEvents.ARROW_HIT),
                        "1.16: a refused Clackers hit plays no arrow hit sound, but heard " + heard);
            });
        });
    }

    @GameTest(template = "empty", skyAccess = true, batch = "clackers_collect_packet", timeoutTicks = 100)
    public static void pickedUpClackersSendTheCollectPacketToTheirTrackers(GameTestHelper helper) {
        ClackersScene.start(helper, "collect-packet", 80, scene -> {
            scene.stone(4, -1, 4, 12, -1, 13);
            Player toucher = scene.bystander(GameType.SURVIVAL, scene.point(8.5D, 0.0D, 6.2D), 0.0F);
            Watcher watcher = new Watcher(scene.level);
            int[] shotId = new int[1];
            scene.then(() -> {
                ClackersEntity shot = scene.summon(scene.point(8.5D, 1.0D, 7.0D), "{" + FALLING + ",pickup:1b}");
                shotId[0] = shot.getId();
                ChunkMap.TrackedEntity tracked = scene.level.getChunkSource().chunkMap.entityMap.get(shot.getId());
                scene.check(tracked != null, "fixture: the summoned Clackers are not tracked");
                // removed with the entity's tracker when the Clackers are discarded
                tracked.seenBy.add(watcher.connection);
            });
            scene.await("the Clackers being picked up", () -> scene.shots.get(0).isRemoved());
            scene.then(() -> {
                scene.check(toucher.getInventory().countItem(ModItems.CLACKERS.get()) == 1, "fixture: the Clackers were not picked up");
                List<ClientboundTakeItemEntityPacket> sent = watcher.collected;
                scene.check(sent.size() == 1 && sent.get(0).getItemId() == shotId[0] && sent.get(0).getPlayerId() == toucher.getId()
                        && sent.get(0).getAmount() == 1,
                        "1.16: Clackers are arrows, and a picked-up arrow sends the collect packet (fly-to-player animation and"
                                + " pickup sound) to everyone who sees it; packets sent: " + sent.size());
            });
        });
    }

    private static final class Watcher extends FakePlayer {
        final List<ClientboundTakeItemEntityPacket> collected = new ArrayList<>();

        Watcher(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "clackers-watcher"));
            this.connection = new RecordingConnection(level, this);
        }
    }

    private static final class RecordingConnection extends ServerGamePacketListenerImpl {
        private final List<ClientboundTakeItemEntityPacket> sink;

        RecordingConnection(ServerLevel level, Watcher player) {
            super(level.getServer(), openConnection(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sink = player.collected;
        }

        // as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
        private static Connection openConnection() {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            return connection;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            if (sink == null) return;
            if (packet instanceof ClientboundBundlePacket bundle) {
                bundle.subPackets().forEach(sub -> send(sub, null));
            }
            else if (packet instanceof ClientboundTakeItemEntityPacket take) {
                sink.add(take);
            }
        }
    }
}
