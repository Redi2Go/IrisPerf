package at.redi.irisperf.client;

import at.redi.irisperf.client.buffer.GlMemoryManager;
import at.redi.irisperf.client.buffer.SimpleMemoryOwner;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;


import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class IrisperfClient implements ClientModInitializer {
    public static Map<String, ShaderProfile> shaderProfiles = new HashMap<>();

    public static String selectedProfileShader;

    public static GlMemoryManager traceMemoryManager;
    public static SimpleMemoryOwner traceMemoryOwner;

    @Override
    public void onInitializeClient() {
        traceMemoryManager = new GlMemoryManager("irisPerf_trace_block", 1 << 12, false);
        traceMemoryOwner = new SimpleMemoryOwner(traceMemoryManager, traceMemoryManager.getCapacity());
        FloatBuffer traceBuffer = traceMemoryOwner.getMemory().getBuffer().asFloatBuffer();
        for (int i = 0; i < traceBuffer.capacity(); i++)
            traceBuffer.put(0);
        traceMemoryManager.queueUpload(traceMemoryOwner);

        RenderSystem.recordRenderCall(() -> {
            traceMemoryManager.upload();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("perf_list").executes(context -> {
                    context.getSource().sendFeedback(Component.literal(shaderProfiles.keySet().toString()));
                    selectedProfileShader = "";
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
                        selectedProfileShader = StringArgumentType.getString(context, "shader");
                        return 0;
                    })
            ));
        });
    }
}
