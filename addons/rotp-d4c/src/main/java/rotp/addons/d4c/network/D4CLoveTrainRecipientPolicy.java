package rotp.addons.d4c.network;

public final class D4CLoveTrainRecipientPolicy {
    private D4CLoveTrainRecipientPolicy() {}

    public static boolean shouldSend(
            boolean sameDimension,
            boolean ownerSelf,
            boolean trackingChunk,
            boolean broadcastAllowed,
            double horizontalDistanceSquared,
            double maxDistanceSquared,
            boolean hasChannel) {
        return sameDimension
                && hasChannel
                && (ownerSelf
                        || trackingChunk
                                && broadcastAllowed
                                && horizontalDistanceSquared
                                        <= maxDistanceSquared);
    }
}
