package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.IrisperfClient;
import at.redi.irisperf.client.ShaderProfile;
import at.redi.irisperf.client.SodiumProgramsExt;
import at.redi.irisperf.client.buffer.GlMemoryManager;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL42;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public class DefaultChunkRendererMixin {
    @Unique
    ShaderProfile shaderProfile = null;

    @Unique
    private List<Map.Entry<Integer, GlMemoryManager>> foundGlMemories;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;begin(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Lnet/caffeinemc/mods/sodium/client/util/FogParameters;)V",
                    shift = At.Shift.AFTER
            )
    )
    public void renderBegin(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters parameters, boolean indexedRenderingEnabled, CallbackInfo ci) {
        int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        ProgramId program = ((SodiumProgramsExt) Objects.requireNonNull(Iris.getPipelineManager().getPipelineNullable()).getSodiumPrograms()).irisPerf$mapTerrainRenderPass(renderPass).getOriginalId();

        shaderProfile = null;
        while (program != null && shaderProfile == null) {
            shaderProfile = IrisperfClient.shaderProfiles.get(program.getSourceName() + ".fsh");
            program = program.getFallback().orElse(null);
        }
        if (shaderProfile == null)
            return;

        if (foundGlMemories == null) {
            foundGlMemories = new ArrayList<>();

            GlMemoryManager glMemoryManager = IrisperfClient.traceMemoryManager;

            int blockIndex = glMemoryManager.findInProgram(programId);
            if (blockIndex == GL31.GL_INVALID_INDEX)
                return;

            foundGlMemories.add(Map.entry(blockIndex, glMemoryManager));
        }

        int bindingPointIndex = 64;
        for (Map.Entry<Integer, GlMemoryManager> glMemoryManager : foundGlMemories) {
            glMemoryManager.getValue().bind(programId, glMemoryManager.getKey(), bindingPointIndex++);
        }
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V",
                    shift = At.Shift.BEFORE
            )
    )
    public void renderFinished(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters parameters, boolean indexedRenderingEnabled, CallbackInfo ci) {
        if (shaderProfile == null)
            return;

        GL42.glMemoryBarrier(-1);

        IrisperfClient.traceMemoryManager.download(byteBuffer -> {
            IntBuffer traceBuffer = byteBuffer.asIntBuffer();

            for (int i = 0; i < shaderProfile.functionCount; i++) {
                int value = traceBuffer.get(i);
                traceBuffer.put(i, 0);

                if (value < 0)
                    continue;

                shaderProfile.functionTime[i] += value;
                shaderProfile.functionSamples[i]++;
            }
        });
    }
}
