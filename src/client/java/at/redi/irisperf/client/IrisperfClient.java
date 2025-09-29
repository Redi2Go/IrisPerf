package at.redi.irisperf.client;

import at.redi.irisperf.client.buffer.GlMemoryManager;
import at.redi.irisperf.client.buffer.SimpleMemoryOwner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

import java.nio.IntBuffer;
import java.util.*;

public class IrisperfClient implements ClientModInitializer {
    public static Map<String, ShaderProfile> shaderProfiles = new HashMap<>();

    public static GlMemoryManager traceMemoryManager;
    public static SimpleMemoryOwner traceMemoryOwner;

    @Override
    public void onInitializeClient() {
        traceMemoryManager = new GlMemoryManager("irisPerf_trace_block", 1 << 12, false);
        traceMemoryOwner = new SimpleMemoryOwner(traceMemoryManager, traceMemoryManager.getCapacity());
        IntBuffer traceBuffer = traceMemoryOwner.getMemory().getBuffer().asIntBuffer();
        for (int i = 0; i < traceBuffer.capacity(); i++)
            traceBuffer.put(0);
        traceMemoryManager.queueUpload(traceMemoryOwner);

        RenderSystem.recordRenderCall(() -> {
            traceMemoryManager.upload();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("perf_list").executes(context -> {

                    StringBuilder builder = new StringBuilder();

                    List<Map.Entry<Double, String>> passes = new ArrayList<>();

                    for (Map.Entry<String, ShaderProfile> entry : shaderProfiles.entrySet()) {
                        double runtime = Arrays.stream(entry.getValue().functionTime, 0, entry.getValue().functionCount).max().orElse(0);
                        passes.add(Map.entry(runtime, entry.getKey()));
                    }

                    passes.sort(Comparator.comparingDouble(Map.Entry::getKey));

                    for (Map.Entry<Double, String> entry : passes)
                        builder.append("Pass " + entry.getValue() + ": " + entry.getKey() + "\n");

                    System.out.println(builder);
                    context.getSource().sendFeedback(Component.literal(builder.toString()));

                    return 0;
                })
            );
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager
                        .literal("perf")
                        .then(ClientCommandManager.argument("shader", StringArgumentType.string())
                        .executes(context -> {
                    String passName = StringArgumentType.getString(context, "shader");
                    ShaderProfile shaderProfile = shaderProfiles.get(passName);

                    StringBuilder builder = new StringBuilder();
                    builder.append("Pass " + passName + ": " + "\n");

                    List<Map.Entry<Long, String>> functions = new ArrayList<>();

                    for (int i = 0; i < shaderProfile.functionCount; i++)
                        functions.add(Map.entry(shaderProfile.functionTime[i], shaderProfile.functionNames[i]));

                    functions.sort(Comparator.comparingDouble(Map.Entry::getKey));

                    for (Map.Entry<Long, String> entry : functions)
                        builder.append(entry.getValue() + ": \n" + entry.getKey() + "\n\n");

                    System.out.println(builder);
                    context.getSource().sendFeedback(Component.literal(builder.toString()));

                    return 0;
                })
            ));
        });
    }
}
