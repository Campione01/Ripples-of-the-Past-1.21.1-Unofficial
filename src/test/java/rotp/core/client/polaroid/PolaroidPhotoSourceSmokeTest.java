package rotp.core.client.polaroid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import rotp.core.network.s2c.ServerIdPacket;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * Two 1.16 Polaroid behaviours the port had lost:
 * <ul>
 * <li>ServerIdPacket on login (SaveFileUtilCap.onPlayerLogIn). The port's client learned the photo server id only
 * from its own upload, so a photo another player took (Hermit Purple's photo of that player) stayed black.</li>
 * <li>PolaroidHelper.pictureCameraSetup placed and detached the camera at the photo's own position. The port kept
 * the camera in the photographed player's eyes, so they were missing from their own photo.</li>
 * </ul>
 */
public final class PolaroidPhotoSourceSmokeTest {
	private static final String PACKETS = "src/main/java/rotp/core/PacketsRegister.java";
	private static final String HANDLER = "src/main/java/rotp/core/item/polaroid/PhotosHandler.java";
	private static final String PACKET = "src/main/java/rotp/core/network/s2c/ServerIdPacket.java";
	private static final String CACHE = "src/main/java/rotp/core/client/polaroid/PhotosCache.java";
	private static final String HELPER = "src/main/java/rotp/core/client/polaroid/PolaroidHelper.java";
	private static final String MIXIN = "src/main/java/rotp/core/mixin/client/polaroid/CameraPolaroidMixin.java";
	private static final String MIXINS = "src/main/resources/jojo_ripples.mixins.json";
	private static final String OTHER_PLAYER = "src/main/java/rotp/core/network/s2c/PhotoForOtherPlayerPacket.java";

	private PolaroidPhotoSourceSmokeTest() {}

	public static void main(String[] args) {
		run();
	}

	public static void run() {
		verifyServerIdOnLogin();
		verifyServerIdClearedOnLogout();
		verifyPhotoCamera();
		verifyPhotoQueue();
	}

	// 1.16 took a photo inside takePicture. The port takes it in a later frame, so a second request before then
	// (the player's own Polaroid and a Hermit Purple photo in one tick) must neither drop the first nor save the
	// options the first forced as the ones to restore.
	private static void verifyPhotoQueue() {
		String helper = compact(source(HELPER));
		check(!helper.contains("pendingPhoto"), "a single pending photo slot is back");
		requireInOrder(helper,
				"privatestaticfinalDeque<PendingPhoto>PENDING_PHOTOS=newArrayDeque<>();",
				"publicstaticvoidtakePicture(",
				"mc.setScreen(null);PendingPhotophoto=newPendingPhoto(cameraPos,cameraAngle,canCaptureStands,giveToPlayerId);"
						+ "if(PENDING_PHOTOS.isEmpty()){guiWasHidden=mc.options.hideGui;"
						+ "previousCameraType=mc.options.getCameraType();previousCanSeeStands=ClientGlobals.canSeeStands;"
						+ "standsHidden=false;mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);"
						+ "showStandsFor(photo);}PENDING_PHOTOS.addLast(photo);}",
				"publicstaticbooleanisTakingPhoto(){return!PENDING_PHOTOS.isEmpty();}",
				"PendingPhotocapture=PENDING_PHOTOS.pollFirst();",
				"finally{PendingPhotonext=PENDING_PHOTOS.peekFirst();if(next!=null){showStandsFor(next);}"
						+ "else{mc.options.hideGui=guiWasHidden;");
		// the saved options are written once per queue, and only takePicture writes them
		check(occurrences(helper, "guiWasHidden=") == 1 && occurrences(helper, "previousCameraType=") == 1
				&& occurrences(helper, "previousCanSeeStands=") == 1, "the options before the photos are saved more than once");
	}

	private static int occurrences(String text, String token) {
		int count = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			count++;
		}
		return count;
	}

	private static void verifyServerIdOnLogin() {
		requireInOrder(compact(source(PACKETS)),
				"publicstaticfinalStringNETWORK_PROTOCOL_VERSION=\"7\";",
				"registerPacket(registrar,PayloadRegistrar::playToClient,newServerIdPacket.Handler("
						+ "JojoMod.resLoc(\"serverid\")));");
		requireInOrder(compact(source(HANDLER)),
				"@SubscribeEventpublicstaticvoidonPlayerLoggedIn(PlayerEvent.PlayerLoggedInEventevent){"
						+ "if(event.getEntity()instanceofServerPlayerplayer){"
						+ "PacketDistributor.sendToPlayer(player,newServerIdPacket(get(player.server).serverId()));}}");
		requireInOrder(compact(source(PACKET)),
				"publicvoidhandle(ServerIdPacketpayload,IPayloadContextcontext){PhotosCache.rememberServer(payload.serverId);}");

		UUID id = UUID.randomUUID();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE);
		try {
			ServerIdPacket.Handler.STREAM_CODEC.encode(buf, new ServerIdPacket(id));
			ServerIdPacket decoded = ServerIdPacket.Handler.STREAM_CODEC.decode(buf);
			check(decoded.serverId().equals(id) && buf.readableBytes() == 0,
					"serverid must carry the server id and nothing else");
		}
		finally {
			buf.release();
		}
	}

	private static void verifyServerIdClearedOnLogout() {
		UUID id = UUID.randomUUID();
		PhotosCache.rememberServer(id);
		check(id.equals(PhotosCache.currentServerId()), "the client did not take the server id");
		PhotosCache.onLogOut(id);
		check(!id.equals(PhotosCache.currentServerId()),
				"1.16 had no server id while logged out; the port kept the last server's");
		check(PhotosCache.getOrTryLoadPhoto(PhotosCache.currentServerId(), 1L) == null,
				"a logged-out client must not load photos under an old server's id");
		requireInOrder(compact(source(CACHE)),
				"publicstaticvoidonLogOut(UUIDserverId){TO_SEND.values().forEach(SendPhotoToServer::close);"
						+ "TO_SEND.clear();currentServerId=UNKNOWN_SERVER;");
	}

	private static void verifyPhotoCamera() {
		requireInOrder(compact(source(HELPER)),
				"publicstaticVec3photoCameraPosition(){PendingPhotopending=PENDING_PHOTOS.peekFirst();"
						+ "returnpending!=null?pending.cameraPos:null;}");
		requireInOrder(compact(source(MIXIN)),
				"@Mixin(Camera.class)",
				"@Shadowprivatebooleandetached;",
				"@Inject(method=\"setup\",at=@At(\"TAIL\"))",
				"Vec3photoCamera=PolaroidHelper.photoCameraPosition();if(photoCamera!=null){"
						+ "setPosition(photoCamera);this.detached=true;}");
		check(clientMixins().contains("client.polaroid.CameraPolaroidMixin"),
				"the photo camera mixin is not registered as a client mixin");

		// 1.16 PhotoForOtherPlayerPacket: 2 blocks from the eyes at a random angle, facing back at them
		requireInOrder(compact(source(OTHER_PLAYER)),
				"Vec3cameraPos=playerPos.add(newVec3(0.0D,0.0D,2.0D).yRot(randomAngle));",
				"PolaroidHelper.takePicture(cameraPos,rot->newVector3f(0.0F,180.0F-randomAngle*180.0F/(float)Math.PI,0.0F),"
						+ "true,payload.giveToPlayerId);");
		for (float angle = 0.0F; angle < 6.28F; angle += 0.37F) {
			Vec3 offset = new Vec3(0.0D, 0.0D, 2.0D).yRot(angle);
			Vec3 look = Vec3.directionFromRotation(0.0F, 180.0F - angle * 180.0F / (float) Math.PI);
			// Mth's sine table is good to about 1e-4
			check(look.distanceTo(offset.scale(-0.5D)) < 2.0E-3D,
					"the photo camera does not face the photographed player at angle " + angle);
		}
	}

	private static String clientMixins() {
		JsonArray client = JsonParser.parseString(source(MIXINS)).getAsJsonObject().getAsJsonArray("client");
		StringBuilder names = new StringBuilder();
		for (JsonElement name : client) {
			names.append(name.getAsString()).append('\n');
		}
		return names.toString();
	}

	private static void requireInOrder(String text, String... parts) {
		int from = 0;
		for (String part : parts) {
			int at = text.indexOf(part, from);
			check(at >= 0, "missing or out of order: " + part);
			from = at + part.length();
		}
	}

	private static String compact(String source) {
		return source
				.replaceAll("(?s)/\\*.*?\\*/", "")
				.replaceAll("//[^\\n]*", "")
				.replaceAll("\\s+", "");
	}

	private static String source(String path) {
		try {
			return Files.readString(Path.of(path));
		}
		catch (IOException e) {
			throw new IllegalStateException("Could not read " + path, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
