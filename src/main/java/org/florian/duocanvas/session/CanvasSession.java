package org.florian.duocanvas.session;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.javalin.websocket.WsContext;

import io.javalin.websocket.WsMessageContext;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.florian.duocanvas.Main;
import org.florian.duocanvas.canvas.CanvasPixel;
import org.florian.duocanvas.json.requests.CanvasRequest;
import org.florian.duocanvas.json.requests.DrawRequest;
import org.florian.duocanvas.json.requests.ImageRequest;
import org.florian.duocanvas.json.responses.CanvasResponse;
import org.florian.duocanvas.json.responses.DrawResponse;
import org.florian.duocanvas.json.responses.DrawUpdate;
import org.florian.duocanvas.json.responses.ImageResponse;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class CanvasSession implements Serializable {
    public String canvasCode;
    private final Set<String> participants = new HashSet<>();
    public CanvasPixel[][] canvasData = new CanvasPixel[1000][1000];

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LogManager.getLogger();

    public CanvasSession(String sessionCode, String creatorUUID) {
        this.canvasCode = sessionCode;
        this.participants.add(creatorUUID);
    }

    public void addPixelToCanvas(int x, int y, String color, String participantUUID) {
        canvasData[x][y] = new CanvasPixel(participantUUID, x, y, color);
    }

    public Set<String> getParticipants() {
        return this.participants;
    }

    public void addParticipant(String participantUUID) {
        this.participants.add(participantUUID);
    }

    public void removeParticipant(String participantUUID) {
        this.participants.remove(participantUUID);
    }

    public void handlePacket(WsMessageContext ctx, Object decodedJson) throws IOException {
        if (decodedJson instanceof CanvasRequest) {
            handleCanvasRequest(ctx);
        } else if (decodedJson instanceof DrawRequest) {
            handleDrawRequest(ctx, (DrawRequest) decodedJson);
        } else if (decodedJson instanceof ImageRequest) {
            handleImageRequest(ctx);
        } else {
            LOGGER.debug(decodedJson.toString());
        }
    }

    private void handleCanvasRequest(WsMessageContext ctx) throws JsonProcessingException {
        try {
            this.addParticipant(ctx.sessionId());
            ArrayList<CanvasPixel> canvasPixels = new ArrayList<>();

            for (CanvasPixel[] i : this.canvasData) {
                for (CanvasPixel j : i) {
                    if (j != null) {
                        canvasPixels.add(j);
                    }
                }
            }
            String canvasContent = OBJECT_MAPPER.writeValueAsString(canvasPixels);
            ctx.send(OBJECT_MAPPER.writeValueAsString(
                    new CanvasResponse("canvasResponse", canvasContent))
            );

            LOGGER.debug("Loaded Canvas for: {}", ctx.sessionId());

        } catch (IOException e) {
            LOGGER.debug("Canvas request failed", e);
            ctx.send(OBJECT_MAPPER.writeValueAsString(false));
        }
    }

    private void handleDrawRequest(WsMessageContext ctx, DrawRequest decodedJson) throws JsonProcessingException {
        int x = decodedJson.x();
        int y = decodedJson.y();
        String color = decodedJson.color();

        if (color == null) {
            cancelDrawResponse(ctx);
            return;
        }
        try {
            this.addPixelToCanvas(x, y, color, ctx.sessionId());
            ctx.send(OBJECT_MAPPER.writeValueAsString(
                    new DrawResponse("drawResponse", true))
            );
            for (WsContext user : Main.USERS) {
                user.send(OBJECT_MAPPER.writeValueAsString(new DrawUpdate("canvasUpdate", x, y, color)));
            }
            LOGGER.debug("A new pixel has been added to the canvas.");
        } catch (Exception e) {
            cancelDrawResponse(ctx);
            LOGGER.debug(e);
        }
    }

    private void handleImageRequest(WsMessageContext ctx) throws IOException {
        try{
            String base64Image = generateCanvasImage();
            ctx.send(OBJECT_MAPPER.writeValueAsString(new ImageResponse("imageResponse", base64Image)));
        }catch (Exception e){
            LOGGER.debug(e);
        }
    }


    private String generateCanvasImage() throws IOException {
        BufferedImage image = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        for (int j = 0; j < 1000; j++) {
            for (int i = 0; i < 1000; i++) {
                CanvasPixel currentPixel = canvasData[i][j];
                Color color = Color.WHITE;

                if (currentPixel != null) {
                    String pixelColor = currentPixel.getColor();
                    color = new Color(Integer.parseInt(pixelColor.replace("#", ""), 16));
                }
                g.setColor(color);
                g.drawRect(i, j, 2, 2);
            }
        }

        File imageFile= new File(canvasCode + ".png");
        ImageIO.write(image, "png", imageFile);
        byte[] fileContent = FileUtils.readFileToByteArray(imageFile);

        boolean isDeleted = imageFile.getAbsoluteFile().delete();
        LOGGER.debug("Successfully cleaned up?: {}", isDeleted);

        return Base64.getEncoder().encodeToString(fileContent);
    }

    private void cancelDrawResponse(WsMessageContext ctx) throws JsonProcessingException {
        ctx.send(OBJECT_MAPPER.writeValueAsString(new DrawResponse("drawResponse", false)));
    }
}
