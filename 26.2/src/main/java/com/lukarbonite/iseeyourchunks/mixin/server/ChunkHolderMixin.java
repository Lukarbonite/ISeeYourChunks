package com.lukarbonite.iseeyourchunks.mixin.server;

import com.lukarbonite.iseeyourchunks.server.FarChunkStreamer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Forwards per-chunk broadcast packets (block updates, block-entity data, light) to viewers streaming
 * a far chunk. Vanilla only sends these to players tracking the chunk through the normal view distance;
 * our streamed viewers are not among them, so their far terrain would otherwise go stale until re-sent.
 *
 * <p>The {@code levelHeightAccessor} ChunkHolder is constructed with is the owning {@link ServerLevel},
 * which lets us relay to only the viewers in that dimension.
 */
@Mixin(ChunkHolder.class)
abstract class ChunkHolderMixin {
	@Shadow @Final private LevelHeightAccessor levelHeightAccessor;

	@Inject(method = "broadcast(Ljava/util/List;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void iSeeYourChunks$forwardToFarViewers(List<ServerPlayer> players, Packet<?> packet, CallbackInfo ci) {
		if (this.levelHeightAccessor instanceof ServerLevel level) {
			ChunkPos pos = ((GenerationChunkHolder) (Object) this).getPos();
			FarChunkStreamer.forwardChunkPacket(level, pos.pack(), packet);
		}
	}
}
