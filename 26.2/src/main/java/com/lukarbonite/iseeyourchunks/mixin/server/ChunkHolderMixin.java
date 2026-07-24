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
 * Marks a streamed far chunk dirty whenever vanilla broadcasts a per-chunk change for it (block update,
 * block-entity data, light). Vanilla only broadcasts to players tracking the chunk through normal view
 * distance; our streamed viewers are not among them, so their far terrain would otherwise freeze at first
 * sight. Rather than relay the delta packet - which a client cannot apply to an out-of-range chunk it never
 * cached - the streamer re-sends the whole chunk on its next update interval (see
 * {@link FarChunkStreamer#markStreamedChunkChanged}), which the client re-ingests into Voxy.
 *
 * <p>The {@code levelHeightAccessor} ChunkHolder is constructed with is the owning {@link ServerLevel}.
 */
@Mixin(ChunkHolder.class)
abstract class ChunkHolderMixin {
	@Shadow @Final private LevelHeightAccessor levelHeightAccessor;

	@Inject(method = "broadcast(Ljava/util/List;Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
	private void iSeeYourChunks$markFarChunkChanged(List<ServerPlayer> players, Packet<?> packet, CallbackInfo ci) {
		if (this.levelHeightAccessor instanceof ServerLevel level) {
			ChunkPos pos = ((GenerationChunkHolder) (Object) this).getPos();
			FarChunkStreamer.markStreamedChunkChanged(level, pos.pack());
		}
	}
}
