package org.florian.duocanvas.canvas;

import java.io.Serializable;

public class CanvasPixel implements Serializable {

    private final PixelPosition position;
    private final String owner;
    private final String color;

    public CanvasPixel(String owner, int x, int y, String color) {
        this.position = new PixelPosition(x, y);
        this.owner = owner;
        this.color = color;
    }

    public String getOwner() {
        return owner;
    }

    public String getColor() {
        return color;
    }

    public PixelPosition getPosition() {
        return position;
    }
}
