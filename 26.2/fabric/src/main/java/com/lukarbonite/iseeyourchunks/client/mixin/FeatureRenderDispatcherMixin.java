package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives {@link FarEntityFogPass}'s dispatcher its own {@link StagedVertexBuffer}. Every dispatcher otherwise takes the
 * one shared buffer out of {@link RenderBuffers}, which vanilla's dispatcher uploads when it prepares its frame; a
 * second dispatcher preparing into it afterwards fails with "Cannot append draw after upload".
 */
@Mixin(FeatureRenderDispatcher.class)
abstract class FeatureRenderDispatcherMixin {
	@WrapOperation(
		method = "<init>",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderBuffers;stagedVertexBuffer()Lnet/minecraft/client/renderer/StagedVertexBuffer;")
	)
	private StagedVertexBuffer iSeeYourChunks$ownFarStagedBuffer(RenderBuffers renderBuffers, Operation<StagedVertexBuffer> original) {
		StagedVertexBuffer own = FarEntityFogPass.stagedBufferForDispatcherUnderConstruction();
		return own != null ? own : original.call(renderBuffers);
	}
}
