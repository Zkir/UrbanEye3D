#version 120
varying vec2 v_uv;
varying float v_alpha;
uniform float u_time;

void main() {
    v_uv = gl_MultiTexCoord0.xy;
    
    // Extra data from color
    float phase = gl_Color.r;
    float rand_val = gl_Color.b;
    
    float lifetime = 6.0 + rand_val * 2.0;
    float t = mod(u_time + phase * lifetime, lifetime);
    float progress = t / lifetime;
    
    // 1. Center of the particle moves up and drifts with wind
    float baseZ = gl_Vertex.z;
    float rise = t * (3.5 + rand_val); // Upward speed ~3.5 m/s
    
    // Random horizontal spawn offset derived from rand_val
    float spawnOX = (fract(rand_val * 7.0) - 0.5) * 1.5; 
    float spawnOY = (fract(rand_val * 13.0) - 0.5) * 1.5;
    
    // Wind drift
    float windY = t * 2.5 + spawnOY; 
    float windX = t * 0.5 * sin(u_time * 0.4 + phase * 15.0) + spawnOX;
    
    vec4 centerWorld = vec4(windX, windY, baseZ + rise, 1.0);
    vec4 centerView = gl_ModelViewMatrix * centerWorld;
    
    // 2. Billboarding: Add local quad offsets in view space
    float scale = 0.6 + progress * 3.5; 
    vec2 offset = gl_Vertex.xy * scale;
    
    centerView.xy += offset;
    
    // 3. Visuals
    v_alpha = (1.0 - progress) * 0.7; // Max 70% opacity
    if (progress < 0.1) v_alpha *= (progress / 0.1); // Smooth fade in
    
    gl_Position = gl_ProjectionMatrix * centerView;
    gl_FrontColor = vec4(1.0, 1.0, 1.0, v_alpha);
}
