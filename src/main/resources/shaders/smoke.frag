#version 120
varying vec2 v_uv;
uniform sampler2D u_texture;

void main() {
    vec4 tex = texture2D(u_texture, v_uv);
    // Multiply texture color by the alpha calculated in vertex shader (stored in gl_Color)
    gl_FragColor = tex * gl_Color;
}
