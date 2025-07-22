package ru.zkir.josm.plugins.z3dviewer;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

/**
 * This class will handle the 3D rendering using JOGL (OpenGL).
 */
public class Renderer3D implements GLEventListener {

    @Override
    public void init(GLAutoDrawable drawable) {
        // Called when the GL context is first created.
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        // Called when the GL context is destroyed.
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        // Called for each frame to be rendered.
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        // Called when the window is resized.
    }
}
