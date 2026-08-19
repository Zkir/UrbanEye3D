package ru.zkir.urbaneye3d.utils;

import com.jogamp.opengl.GL2;
import ru.zkir.urbaneye3d.UrbanEye3dPlugin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ShaderManager {
    private static ShaderManager instance;
    private final Map<String, Integer> programs = new HashMap<>();

    private ShaderManager() {}

    public static synchronized ShaderManager getInstance() {
        if (instance == null) {
            instance = new ShaderManager();
        }
        return instance;
    }

    public int getProgram(GL2 gl, String name) {
        if (programs.containsKey(name)) {
            return programs.get(name);
        }

        try {
            UrbanEye3dPlugin.debugMsg("Loading shader program: " + name);
            int program = createProgram(gl, "/shaders/" + name + ".vert", "/shaders/" + name + ".frag");
            programs.put(name, program);
            UrbanEye3dPlugin.debugMsg("Shader program '" + name + "' loaded successfully (ID: " + program + ")");
            return program;
        } catch (Exception e) {
            UrbanEye3dPlugin.debugMsg("Failed to create shader program '" + name + "': " + e.getMessage());
            return 0;
        }
    }

    private int createProgram(GL2 gl, String vertPath, String fragPath) throws Exception {
        int vertShader = compileShader(gl, GL2.GL_VERTEX_SHADER, vertPath);
        int fragShader = compileShader(gl, GL2.GL_FRAGMENT_SHADER, fragPath);

        int program = gl.glCreateProgram();
        gl.glAttachShader(program, vertShader);
        gl.glAttachShader(program, fragShader);
        gl.glLinkProgram(program);

        int[] linked = new int[1];
        gl.glGetProgramiv(program, GL2.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetProgramiv(program, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetProgramInfoLog(program, logLength[0], null, 0, log, 0);
            throw new Exception("Program linking failed: " + new String(log));
        }

        return program;
    }

    private int compileShader(GL2 gl, int type, String path) throws Exception {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null) throw new Exception("Shader file not found: " + path);

        String source = new BufferedReader(new InputStreamReader(is))
                .lines().collect(Collectors.joining("\n"));

        int shader = gl.glCreateShader(type);
        gl.glShaderSource(shader, 1, new String[]{source}, null);
        gl.glCompileShader(shader);

        int[] compiled = new int[1];
        gl.glGetShaderiv(shader, GL2.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            int[] logLength = new int[1];
            gl.glGetShaderiv(shader, GL2.GL_INFO_LOG_LENGTH, logLength, 0);
            byte[] log = new byte[logLength[0]];
            gl.glGetShaderInfoLog(shader, logLength[0], null, 0, log, 0);
            throw new Exception("Shader compilation failed (" + path + "): " + new String(log));
        }

        return shader;
    }

    public void disposeAll(GL2 gl) {
        for (int program : programs.values()) {
            gl.glDeleteProgram(program);
        }
        programs.clear();
    }
}
