package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.IrisperfClient;
import at.redi.irisperf.client.ShaderProfile;
import at.redi.irisperf.client.buffer.GlMemoryManager;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.minecraft.client.MinecraftClient;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
import java.util.stream.IntStream;

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
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V")
    )
    public void renderAllPushGroup(int id, String name) {
        passName = name;
    }

    @Redirect(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/gl/program/Program;use()V"
            )
    )
    public void use(Program instance, @Local(ordinal = 0) int i) {
        instance.use();

        shaderProfile = IrisperfClient.shaderProfiles.get(passName + ".fsh");
        if (shaderProfile == null)
            return;

        if (foundGlMemories == null) {
            foundGlMemories = new ArrayList<>();

            GlMemoryManager glMemoryManager = IrisperfClient.traceMemoryManager;

            int blockIndex = glMemoryManager.findInProgram(instance.getProgramId());
            if (blockIndex == GL31.GL_INVALID_INDEX)
                return;

            foundGlMemories.add(Map.entry(blockIndex, glMemoryManager));
        }

        int bindingPointIndex = 0;
        for (Map.Entry<Integer, GlMemoryManager> glMemoryManager : foundGlMemories) {
            glMemoryManager.getValue().bind(instance.getProgramId(), glMemoryManager.getKey(), bindingPointIndex++);
        }
    }

    @Inject(
            method = "renderAll",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/GLDebug;pushGroup(ILjava/lang/String;)V")
    )
    public void renderAllPushGroup(CallbackInfo ci) {
        if (shaderProfile == null)
            return;

        GL42.glMemoryBarrier(-1);

        IrisperfClient.traceMemoryManager.download(byteBuffer -> {
            IntBuffer traceBuffer = byteBuffer.asIntBuffer();

            for (int i = 0; i < shaderProfile.functionCount; i++) {
                shaderProfile.functionTime[i] += Math.max(traceBuffer.get(i), 0);
                traceBuffer.put(i, 0);
            }
            shaderProfile.sampleCount++;
        });

        if (!(passName + ".fsh").equals(IrisperfClient.selectedProfileShader))
            return;

        IrisperfClient.selectedProfileShader = "";

        StringBuilder builder = new StringBuilder();
        builder.append("Pass " + passName + ": " + "\n");

        SortedMap<Long, String> map = new TreeMap<>();
        for (int i = 0; i < shaderProfile.functionCount; i++)
            map.put(shaderProfile.functionTime[i] / shaderProfile.sampleCount, shaderProfile.functionNames[i]);

        for (Map.Entry<Long, String> entry : map.entrySet())
            builder.append(entry.getValue() + ": \n" + entry.getKey() + "\n");

        shaderProfile.reset();

        System.out.println(builder);
    }

}
