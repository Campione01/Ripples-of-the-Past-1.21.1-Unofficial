package com.github.standobyte.jojo.client.polaroid;

import java.util.UUID;

public final class PhotoCacheContractSmokeTest {
	private PhotoCacheContractSmokeTest() {}

	public static void run() {
		UUID unsolicitedServer = UUID.randomUUID();
		long unsolicitedPhoto = 9_001L;
		check(PhotosCache.getPhotoHolder(unsolicitedServer, unsolicitedPhoto) == null,
				"lookup for unsolicited photo data must not allocate a cache entry");
		check(PhotosCache.getPhotoHolder(unsolicitedServer, unsolicitedPhoto) == null,
				"repeated unsolicited lookup must remain allocation-free");

		PhotosCache.PendingAssignmentTimeout timeout =
				new PhotosCache.PendingAssignmentTimeout();
		for (int tick = 1;
				tick < PhotosCache.PHOTO_ASSIGNMENT_TIMEOUT_TICKS; tick++) {
			check(!timeout.tick(),
					"pending photo must remain available before assignment timeout");
		}
		check(timeout.tick(),
				"pending photo must expire when no assignment response arrives");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
