package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.IrisperfClient;
import at.redi.irisperf.client.ShaderProfile;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = Iris.class, remap = false)
public class IrisMixin {

    @Inject(
            method = "loadShaderpack",
            at = @At("HEAD")
    )
    private static void loadShaderpackHead(CallbackInfo ci) {
        for (Map.Entry<String, ShaderProfile> entry : IrisperfClient.shaderProfiles.entrySet())
            entry.getValue().reset();
    }
}
