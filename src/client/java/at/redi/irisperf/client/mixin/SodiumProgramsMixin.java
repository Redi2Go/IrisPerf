package at.redi.irisperf.client.mixin;

import at.redi.irisperf.client.SodiumProgramsExt;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SodiumPrograms.class)
public abstract class SodiumProgramsMixin implements SodiumProgramsExt {

    @Shadow protected abstract SodiumPrograms.Pass mapTerrainRenderPass(TerrainRenderPass pass);

    @Override
    public SodiumPrograms.Pass irisPerf$mapTerrainRenderPass(TerrainRenderPass pass) {
        return mapTerrainRenderPass(pass);
    }
}
