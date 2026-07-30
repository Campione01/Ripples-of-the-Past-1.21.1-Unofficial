package com.github.standobyte.jojo.item.polaroid;

import java.util.UUID;

import com.github.standobyte.jojo.network.BatchReceiver;

public final class PhotoUploadContractSmokeTest {
	private PhotoUploadContractSmokeTest() {}

	public static void run() {
		UUID owner = UUID.randomUUID();
		UUID target = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		UUID requester = UUID.randomUUID();
		PhotosHandler.PhotoSessionRegistry<UUID> sessions =
				new PhotosHandler.PhotoSessionRegistry<>();

		check(sessions.authorize(owner, owner, 17, 100L),
				"self-photo permit must be issued");
		PhotosHandler.PhotoPermit selfPermit =
				sessions.consumePermit(owner, 17, 100L).orElseThrow();
		check(selfPermit.target().equals(owner),
				"self-photo permit must retain the uploader as recipient");
		check(sessions.consumePermit(owner, 17, 100L).isEmpty(),
				"photo permit must be one-shot");

		check(sessions.authorize(owner, target, 23, 200L),
				"observer-photo permit must be issued");
		check(!sessions.authorize(owner, other, 24, 200L),
				"a second pending permit must not replace the first");
		check(sessions.consumePermit(other, 23, 200L).isEmpty(),
				"unselected uploader must not claim an observer-photo permit");
		PhotosHandler.PhotoPermit observerPermit =
				sessions.consumePermit(owner, 23, 200L).orElseThrow();
		check(observerPermit.target().equals(target),
				"rejected replacement must preserve the first authorized recipient");

		check(sessions.authorize(owner, target, 23, 210L),
				"fresh observer-photo permit must be issued");
		check(sessions.consumePermit(owner, 24, 205L).isEmpty(),
				"uploader must not redirect a permit to another entity");
		check(sessions.consumePermit(owner, 23, 205L).isEmpty(),
				"invalid assignment attempt must consume the one-shot permit");

		PhotosHandler.PhotoUpload upload =
				new PhotosHandler.PhotoUpload(
						owner, target, 300L, new BatchReceiver());
		sessions.startUpload(41L, upload);
		sessions.addEarlyRequest(41L, requester);
		check(upload.accepts(owner, 300L),
				"owner upload at the timeout boundary must be accepted");
		check(!upload.accepts(other, 200L),
				"another player must not append to an owner-bound upload");
		check(!upload.accepts(owner, 301L),
				"expired owner upload must be rejected");
		check(!sessions.authorize(owner, owner, 17, 400L),
				"second concurrent upload must be rejected");
		sessions.cleanupExpired(300L);
		check(sessions.uploadCount() == 1
						&& sessions.earlyRequestCount(41L) == 1,
				"upload and early request must survive through the timeout boundary");
		sessions.cleanupExpired(301L);
		check(sessions.uploadCount() == 0
						&& !sessions.hasActiveUpload(owner)
						&& sessions.earlyRequestCount(41L) == 0,
				"server tick cleanup must release expired upload state and request refs");

		check(sessions.authorize(owner, target, 23, 500L),
				"logout cleanup setup permit must be issued");
		PhotosHandler.PhotoPermit logoutPermit =
				sessions.consumePermit(owner, 23, 400L).orElseThrow();
		PhotosHandler.PhotoUpload logoutUpload = new PhotosHandler.PhotoUpload(
				owner, logoutPermit.target(), 500L, new BatchReceiver());
		sessions.startUpload(42L, logoutUpload);
		sessions.addEarlyRequest(42L, requester);
		check(sessions.authorize(other, target, 23, 500L),
				"independent target-bound permit must be issued");
		sessions.removePlayer(target, target::equals);
		check(sessions.permitCount() == 0 && sessions.uploadCount() == 0
						&& !sessions.hasActiveUpload(owner)
						&& sessions.earlyRequestCount(42L) == 0,
				"target logout must release permits, uploads, and early requests");

		check(sessions.authorize(owner, owner, 17, 600L),
				"server-stop cleanup setup permit must be issued");
		PhotosHandler.PhotoPermit stopPermit =
				sessions.consumePermit(owner, 17, 550L).orElseThrow();
		sessions.startUpload(43L, new PhotosHandler.PhotoUpload(
				owner, stopPermit.target(), 600L, new BatchReceiver()));
		sessions.addEarlyRequest(43L, requester);
		sessions.clear();
		check(sessions.permitCount() == 0 && sessions.uploadCount() == 0
						&& !sessions.hasActiveUpload(owner)
						&& sessions.earlyRequestCount(43L) == 0,
				"server stop must release all photo session state");

		check(PhotosHandler.isKnownPhotoId(1L, 12L),
				"first valid legacy photo id must remain requestable");
		check(PhotosHandler.isKnownPhotoId(12L, 12L),
				"highest assigned legacy photo id must remain requestable");
		check(!PhotosHandler.isKnownPhotoId(0L, 12L),
				"non-positive photo id must be rejected");
		check(!PhotosHandler.isKnownPhotoId(13L, 12L),
				"unassigned future photo id must be rejected");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
