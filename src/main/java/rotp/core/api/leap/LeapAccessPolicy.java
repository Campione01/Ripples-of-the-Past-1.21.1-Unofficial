package rotp.core.api.leap;

@FunctionalInterface
public interface LeapAccessPolicy {
	boolean denies(LeapAccessQuery query);
}
