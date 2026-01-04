package at.redi.irisperf.client;

import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;

public interface SodiumProgramsExt {
    SodiumPrograms.Pass irisPerf$mapTerrainRenderPass(TerrainRenderPass pass);
}
