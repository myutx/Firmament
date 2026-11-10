package moe.nea.firmament.mixins;

import moe.nea.firmament.features.debug.DyePacketArrival;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({
	ClientboundSetEquipmentPacket.class,
	ClientboundContainerSetSlotPacket.class,
	ClientboundContainerSetContentPacket.class,
	ClientboundSetPlayerInventoryPacket.class
})
public abstract class DyePacketArrivalMixin implements DyePacketArrival {
	@Unique
	private volatile Long firmament$dyeArrivalNanos;

	@Override
	public void firmament$setDyeArrivalNanos(long arrivalNanos) {
		firmament$dyeArrivalNanos = arrivalNanos;
	}

	@Override
	public Long firmament$getDyeArrivalNanos() {
		return firmament$dyeArrivalNanos;
	}
}
