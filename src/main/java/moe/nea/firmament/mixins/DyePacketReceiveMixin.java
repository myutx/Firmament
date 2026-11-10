package moe.nea.firmament.mixins;

import io.netty.channel.ChannelHandlerContext;
import moe.nea.firmament.features.debug.DyePacketArrival;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class DyePacketReceiveMixin {
	@Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void firmament$timestampDyePacket(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
		if (packet instanceof DyePacketArrival timing) {
			timing.firmament$setDyeArrivalNanos(System.nanoTime());
		} else if (packet instanceof ClientboundBundlePacket bundle) {
			// The connection receives an assembled bundle as a single message.
			long arrivalNanos = System.nanoTime();
			for (Packet<?> child : bundle.subPackets()) {
				if (child instanceof DyePacketArrival timing) {
					timing.firmament$setDyeArrivalNanos(arrivalNanos);
				}
			}
		}
	}
}
