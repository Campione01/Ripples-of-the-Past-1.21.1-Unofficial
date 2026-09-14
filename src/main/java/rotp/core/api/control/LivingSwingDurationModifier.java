package rotp.core.api.control;

@FunctionalInterface
public interface LivingSwingDurationModifier {
	int modifyDuration(LivingSwingDurationQuery query);
}
