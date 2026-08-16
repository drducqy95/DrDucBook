# Legado Local AI runtime

This runtime is pinned to `sjl623/llama.cpp` commit
`781aadf8749c9fa26bcf12f27d891dff86916b44`, the head used by upstream STQ PR
`ggml-org/llama.cpp#22836` when this integration was created.

Run `scripts/setup-local-ai-runtime.ps1` once before building an APK with Local AI. The downloaded
source is checksum-verified and intentionally ignored by Git. Gradle then builds native libraries
for `arm64-v8a` and `x86_64`; 32-bit `armeabi-v7a` keeps running the app but Local AI is unavailable.

The runtime is CPU-first. It loads llama.cpp CPU variants dynamically so supported Arm NEON/dot
product kernels and x86-64 kernels are selected at runtime. Vulkan remains disabled until a device
capability probe and model-specific benchmark demonstrate a reliable speedup.
