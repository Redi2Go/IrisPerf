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

//        RenderSystem.queueFencedTask(() -> {
//            traceMemoryManager.upload();
//        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("perf_list").executes(context -> {

                    StringBuilder builder = new StringBuilder();

                    List<Map.Entry<Long, String>> passes = new ArrayList<>();

                    for (Map.Entry<String, ShaderProfile> entry : shaderProfiles.entrySet()) {
                        long runtime = Arrays.stream(entry.getValue().getAverageTiming()).max().orElse(0);
                        passes.add(Map.entry(runtime, entry.getKey()));

                        entry.getValue().reset();
                    }

                    passes.sort(Comparator.comparingLong(Map.Entry::getKey));

                    for (Map.Entry<Long, String> entry : passes)
                        builder.append("Pass " + entry.getValue() + ": " + String.format("%,d", entry.getKey()) + "\n");

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

                    long[] averageTiming = shaderProfile.getAverageTiming();
                    for (int i = 0; i < averageTiming.length; i++)
                        functions.add(Map.entry(averageTiming[i], shaderProfile.functionNames[i]));

                    shaderProfile.reset();

                    functions.sort(Comparator.comparingLong(Map.Entry::getKey));

                    for (Map.Entry<Long, String> entry : functions)
                        builder.append(entry.getValue() + ": \n" + String.format("%,d", entry.getKey()) + "\n\n");

                    System.out.println(builder);
                    context.getSource().sendFeedback(Component.literal(builder.toString()));

                    return 0;
                })
            ));
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager
                    .literal("perf_source")
                    .then(ClientCommandManager.argument("shader", StringArgumentType.string())
                        .executes(context -> {
                            String passName = StringArgumentType.getString(context, "shader");
                            ShaderProfile shaderProfile = shaderProfiles.get(passName);

                            System.out.println(shaderProfile.patchedShader);
                            context.getSource().sendFeedback(Component.literal("Printed patched shader source to stdout"));

                            return 0;
                        })
                    ));
        });
    }
}
