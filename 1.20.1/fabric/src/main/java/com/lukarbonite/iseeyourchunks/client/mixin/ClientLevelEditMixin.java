package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.compat.CachedChunkEditIngest;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports every client-side block change to {@link CachedChunkEditIngest}, so an edit in a cached chunk Voxy is
 * drawing (rather than Sodium) reaches Voxy's LOD. {@code setBlocksDirty} runs for local edits and for the server's
 * block-update packets alike; it is the same hook Voxy uses for its own, much narrower, live ingest.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelEditMixin {
	@Inject(method = "setBlocksDirty", at = @At("TAIL"))
	private void iSeeYourChunks$noteBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		if (oldState != newState) {
			CachedChunkEditIngest.noteBlockChanged((ClientLevel) (Object) this, pos);
		}
	}
}
