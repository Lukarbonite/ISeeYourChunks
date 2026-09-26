package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.compat.FarChunkLightCapture;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Exposes a far chunk's real light to our Voxy ingest. {@code handleLevelChunkWithLight} calls
 * {@code ClientChunkCache.replaceWithPacketData} - where we ingest far chunks - synchronously on the same
 * thread, so stashing the packet's light at HEAD and clearing it at RETURN lets the ingest read it back and
 * feed Voxy true block+sky light (torches, lava, real sky exposure) instead of a heightmap approximation.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@Inject(method = "handleLevelChunkWithLight", at = @At("HEAD"))
	private void iSeeYourChunks$captureFarChunkLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		FarChunkLightCapture.begin(packet.getX(), packet.getZ(), packet.getLightData());
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
	private void iSeeYourChunks$clearFarChunkLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		FarChunkLightCapture.end();
	}
}
