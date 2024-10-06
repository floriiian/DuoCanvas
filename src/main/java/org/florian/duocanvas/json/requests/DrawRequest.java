package org.florian.duocanvas.json.requests;

public record DrawRequest(String requestType, String canvasCode, int x, int y, String color, int date) {

}