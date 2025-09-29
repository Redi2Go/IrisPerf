package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.IrisperfClient;
import at.redi.irisperf.client.ShaderProfile;
import at.redi.irisperf.client.buffer.GlMemoryManager;
import com.llamalad7.mixinextras.sugar.Local;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL42;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;
import java.util.*;

@Mixin(value = CompositeRenderer.class, remap = false)
public class CompositeRendererMixin {
    @Unique
    private String passName = null;

    @Unique
    ShaderProfile shaderProfile = null;

    @Unique
    private List<Map.Entry<Integer, GlMemoryManager>> foundGlMemories;

    @Redirect(
            method = "renderAll",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V", ordinal = 1)
    )
    public void renderAllPushGroup(int id, String name) {
        passName = name;
    }

    @Inject(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/program/Program;use()V",
                    shift = At.Shift.AFTER
            )
    )
    public void use(CallbackInfo ci, @Local(ordinal = 0) int i) {
        int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        shaderProfile = IrisperfClient.shaderProfiles.get(passName + ".fsh");
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

        int bindingPointIndex = 0;
        for (Map.Entry<Integer, GlMemoryManager> glMemoryManager : foundGlMemories) {
            glMemoryManager.getValue().bind(programId, glMemoryManager.getKey(), bindingPointIndex++);
        }
    }

    @Inject(
            method = "renderAll",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;popGroup()V", ordinal = 1)
    )
    public void renderAllPushGroup(CallbackInfo ci) {
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

                shaderProfile.functionTime[i] = value;
            }
        });
    }

}
