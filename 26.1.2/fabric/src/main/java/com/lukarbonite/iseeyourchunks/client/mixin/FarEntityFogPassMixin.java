package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives {@link FarEntityFogPass} in lockstep with vanilla's feature rendering inside the main pass: each far phase
 * runs right after the matching vanilla phase (so far entities depth-test against the same terrain and their
 * outlines join vanilla's outline batch), and the far submissions are dropped right after vanilla clears its own.
 */
@Mixin(LevelRenderer.class)
abstract class FarEntityFogPassMixin {
	@Unique
	private static final String MAIN_PASS = "lambda$addMainPass$0";

	@Inject(method = MAIN_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderSolidFeatures()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farSolid(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, ResourceHandle<?> b, ResourceHandle<?> c, ResourceHandle<?> d, ResourceHandle<?> e,
		boolean renderBlockOutline, Matrix4fc modelView, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.SOLID, worldFog);
	}

	@Inject(method = MAIN_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farTranslucent(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, ResourceHandle<?> b, ResourceHandle<?> c, ResourceHandle<?> d, ResourceHandle<?> e,
		boolean renderBlockOutline, Matrix4fc modelView, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.TRANSLUCENT, worldFog);
	}

	@Inject(method = MAIN_PASS, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;clearSubmitNodes()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$endFarFrame(CallbackInfo ci) {
		FarEntityFogPass.endFrame();
	}
}
