package com.lukarbonite.iseeyourchunks.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.lukarbonite.iseeyourchunks.client.render.FarEntityFogPass;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives {@link FarEntityFogPass} in lockstep with vanilla's feature frame: prepared right after vanilla prepares its
 * own, executed inside the main pass right after each matching vanilla phase (so far entities depth-test against the
 * same terrain), and closed right after vanilla closes its frame. Also ORs our outline state into vanilla's, so a
 * glowing far entity still gets vanilla's outline pass.
 */
@Mixin(LevelRenderer.class)
abstract class FarEntityFogPassMixin {
	@Unique
	private static final String RENDER =
		"render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V";
	@Unique
	private static final String PREPARED_FRAME = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;";

	@Inject(
		method = RENDER,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;prepareFrame(Lnet/minecraft/client/renderer/SubmitNodeStorage;)" + PREPARED_FRAME, shift = At.Shift.AFTER)
	)
	private void iSeeYourChunks$prepareFarFrame(CallbackInfo ci) {
		FarEntityFogPass.prepare();
	}

	@Inject(
		method = RENDER,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;close()V", shift = At.Shift.AFTER)
	)
	private void iSeeYourChunks$closeFarFrame(CallbackInfo ci) {
		FarEntityFogPass.endFrame();
	}

	@WrapOperation(
		method = {RENDER, "addMainPass"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;hasAnyOutline()Z")
	)
	private boolean iSeeYourChunks$includeFarOutlines(FeatureRenderDispatcher.PreparedFrame frame, Operation<Boolean> original) {
		return original.call(frame) || FarEntityFogPass.hasAnyOutline();
	}

	@Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farSolid(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, FeatureRenderDispatcher.PreparedFrame frame, ResourceHandle<?> b, ResourceHandle<?> c,
		ResourceHandle<?> d, ResourceHandle<?> e, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.SOLID, worldFog);
	}

	@Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farTranslucent(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, FeatureRenderDispatcher.PreparedFrame frame, ResourceHandle<?> b, ResourceHandle<?> c,
		ResourceHandle<?> d, ResourceHandle<?> e, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.TRANSLUCENT, worldFog);
	}

	@Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeOutline()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farOutline(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, FeatureRenderDispatcher.PreparedFrame frame, ResourceHandle<?> b, ResourceHandle<?> c,
		ResourceHandle<?> d, ResourceHandle<?> e, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.OUTLINE, worldFog);
	}

	@Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucentAfterTerrain()V", shift = At.Shift.AFTER))
	private void iSeeYourChunks$farTranslucentAfterTerrain(
		GpuBufferSlice worldFog, LevelRenderState state, ProfilerFiller profiler, ChunkSectionsToRender sections,
		ResourceHandle<?> a, FeatureRenderDispatcher.PreparedFrame frame, ResourceHandle<?> b, ResourceHandle<?> c,
		ResourceHandle<?> d, ResourceHandle<?> e, CallbackInfo ci
	) {
		FarEntityFogPass.execute(FarEntityFogPass.Phase.TRANSLUCENT_AFTER_TERRAIN, worldFog);
	}
}
