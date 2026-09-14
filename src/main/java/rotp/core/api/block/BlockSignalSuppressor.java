package rotp.core.api.block;

@FunctionalInterface
public interface BlockSignalSuppressor {
	boolean suppress(BlockSignalQuery query);
}
