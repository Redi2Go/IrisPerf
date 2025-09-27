package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.IrisperfClient;
import at.redi.irisperf.client.ShaderProfile;
import at.redi.irisperf.client.TraceTransformer;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTParser;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.parameter.Parameters;
import net.irisshaders.iris.pipeline.transform.transformer.CompatibilityTransformer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CompatibilityTransformer.class, remap = false)
public class TransformPatcherMixin {

    @Inject(
            method = "transformEach",
            at = @At("HEAD")
    )
    private static void transformation(ASTParser t, TranslationUnit tree, Root root, Parameters parameters, CallbackInfo ci) {
        if (parameters.type != PatchShaderType.FRAGMENT)
            return;

        ShaderProfile shaderProfile = new ShaderProfile();
        TraceTransformer.transform(shaderProfile, t, root, tree);

//        if (parameters.name.equals("deferred")) {
//            System.out.println(ASTPrinter.printIndentedAnnotated(tree));
//        }

        IrisperfClient.shaderProfiles.put(parameters.name + ".fsh", shaderProfile);
    }
}
