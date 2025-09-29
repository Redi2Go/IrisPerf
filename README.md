## About

Experimental mod that inserts clockARB queries in the shader code 
to profile function execution times of all functions inside a shader

#### Currently only tested with BSL v8.2.09

## Usage
- ``/perf_list``: Lists all¹ shader passes with the function timing of ``main()``
- ``/perf <shader>``: List the execution time of each function in the shader pass
- ``/perf_source``: Print the patched shader source to stdout

## How it works

When Iris applies its Shader Patches using GLSL-Transformer, IrisPerf hooks into the function and applies
its own patches (TransformPatcherMixin.java) to each GLSL-function.
The IrisPerf-Transformer compares the value of clockARB() at the beginning and at the end of the function and adds
the time that has passed to a SSBO containing the aggregated timings of each function.

So the code of 
```glsl
#version 120

float GetLinearDepth(float depth) {
   return (2.0 * near) / (far + near - depth * (far - near));
}
```
gets turned into
```glsl
#version 450 core
#extension GL_ARB_gpu_shader_int64: enable
#extension GL_ARB_shader_clock: require

float GetLinearDepth(float depth) {
   uint64_t irisPerf_begin = clockARB();
   {
      float irisPerf_tmp = (2.0f * near) / (far + near - depth * (far - near));
      if (irisPerf_sample) irisPerf_trace_array[7] += int(clockARB() - irisPerf_begin);
      return irisPerf_tmp;
   }
}
```

After each render pass, the timing information is downloaded from the SSBO.

#### ¹Info: Currently, the timing information is only downloaded for Composite render passes, gbuffer and compute shader passes are ignored (for now)

## Project Structure

- buffer
  - Boilerplate code for SSBO handling (copied from Photonics)
- mixin
  - CompositeRendererMixin 
    - Captures the passName
    - Binds the SSBO before rendering
    - Downloads the function timings after rendering is done
  - IrisMixin
    - Clears the function-timings, when the shaderpack is reloaded
  - TransformPatcherMixin
    - Patches the shader code
  - IrisPerfClient
    - Registers the commands
  - ShaderProfile
    - Stores the function name and its timing
  - TraceTransformer
    - Patches all the functions of a particular shader