package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.lukarbonite.iseeyourchunks.client.FarChunkEntityTicking;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The 1.20.1 form of the shared {@code ClientChunkCacheEntityTickingMixin}, which this version's mixin config lists in
 * its place: on 1.20.1 {@code ClientChunkCache.drop} takes the chunk's coordinates rather than a {@link ChunkPos}.
 * Feeds chunk arrivals and drops to {@link FarChunkEntityTicking}, so mobs in streamed far chunks keep ticking.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheEntityTickingLegacyMixin {
	@Shadow
	@Final
	ClientLevel level;

	@Inject(method = "replaceWithPacketData", at = @At("RETURN"))
	private void iSeeYourChunks$tickFarChunkEntities(
		CallbackInfoReturnable<LevelChunk> cir,
		@Local(ordinal = 0, argsOnly = true) int chunkX,
		@Local(ordinal = 1, argsOnly = true) int chunkZ
	) {
		FarChunkEntityTicking.onChunkArrived(this.level, chunkX, chunkZ, cir.getReturnValue() != null);
	}

	@Inject(method = "drop(II)V", at = @At("HEAD"))
	private void iSeeYourChunks$stopTickingFarChunk(int chunkX, int chunkZ, CallbackInfo ci) {
		FarChunkEntityTicking.onChunkDropped(this.level, new ChunkPos(chunkX, chunkZ));
	}
}
