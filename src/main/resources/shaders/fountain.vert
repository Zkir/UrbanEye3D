#version 120
varying vec2 v_uv;
uniform float u_time;

void main() {
    v_uv = gl_MultiTexCoord0.xy;
    // UV scrolling: shift V coordinate based on time
    v_uv.y -= u_time * 0.5; 
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
