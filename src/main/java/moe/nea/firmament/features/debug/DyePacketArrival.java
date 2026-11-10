package moe.nea.firmament.features.debug;

public interface DyePacketArrival {
	void firmament$setDyeArrivalNanos(long arrivalNanos);

	Long firmament$getDyeArrivalNanos();

	static Long receivedAt(Object packet) {
		return packet instanceof DyePacketArrival timing ? timing.firmament$getDyeArrivalNanos() : null;
	}
}
