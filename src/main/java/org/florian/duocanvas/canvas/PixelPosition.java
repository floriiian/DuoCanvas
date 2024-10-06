package org.florian.duocanvas.canvas;

import java.io.Serializable;

public record PixelPosition(int x, int y) implements Serializable {

    public int getX(){
        return x;
    }
    public int getY(){
        return y;
    }
}
