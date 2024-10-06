package org.florian.duocanvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import org.apache.commons.text.RandomStringGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.florian.duocanvas.db.CanvasDatabase;
import org.florian.duocanvas.json.requests.CanvasRequest;
import org.florian.duocanvas.json.requests.DrawRequest;
import org.florian.duocanvas.json.responses.SessionResponse;
import org.florian.duocanvas.session.CanvasSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

enum RequestType {
    GENERATE_CANVAS, LOAD_CANVAS, DRAW_PIXEL
}

public class Main {

    public static Logger LOGGER = LogManager.getLogger();
    public static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static final int BACKUP_DELAY = 30;

    public static final Set<WsContext> USERS = new HashSet<>();
    private static final Map<RequestType, Class<?>> REQUEST_HANDLERS = new HashMap<>();
    public static final Map<String, CanvasSession> ACTIVE_CANVAS_SESSIONS = new HashMap<>();

    public static void main() {

        REQUEST_HANDLERS.put(RequestType.GENERATE_CANVAS, SessionResponse.class);
        REQUEST_HANDLERS.put(RequestType.LOAD_CANVAS, CanvasRequest.class);
        REQUEST_HANDLERS.put(RequestType.DRAW_PIXEL, DrawRequest.class);


        if (!CanvasDatabase.initiateDatabase()) {
            return;
        }
        ArrayList<String> canvasCodes = CanvasDatabase.getCanvasCodesFromDatabase();
        if (canvasCodes != null) {
            canvasCodes.forEach(canvasCode -> {

                byte[] canvasBytes = CanvasDatabase.getCanvasBytesFromDatabase(canvasCode);

                try {
                    CanvasSession sessionBackup = CanvasDatabase.getCanvasDataFromBytes(canvasBytes);
                    ACTIVE_CANVAS_SESSIONS.put(sessionBackup.canvasCode, sessionBackup);
                    LOGGER.debug("Loaded: {}", canvasCode);
                } catch (Exception e) {
                    LOGGER.debug(e);
                }
            });
        }

        Javalin app = Javalin.create().start(7777);

        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);

        service.scheduleWithFixedDelay(() -> {
            try {
                CanvasDatabase.backupCanvasData();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, BACKUP_DELAY, BACKUP_DELAY, TimeUnit.SECONDS);


        app.ws("/canvas", ws -> {

            ws.onConnect(USERS::add);

            ws.onMessage(ctx -> {

                String requestedData = ctx.message();
                JsonNode jsonData = OBJECT_MAPPER.readTree(requestedData);

                if (jsonData.isEmpty()) {
                    return;
                }

                LOGGER.debug(jsonData);

                String requestType = jsonData.get("requestType").asText();
                String canvasCode = jsonData.get("canvasCode").asText();

                if (requestType.equals("none") && canvasCode.equals("session")) {
                    return;
                }

                switch (requestType) {
                    case "session":
                        ctx.send(OBJECT_MAPPER.writeValueAsString(new SessionResponse(
                                "sessionResponse", generateCanvasSession(ctx.sessionId())))
                        );
                        break;
                    case "canvas":
                        ACTIVE_CANVAS_SESSIONS.get(canvasCode).handlePacket(ctx,
                                REQUEST_HANDLERS.get(RequestType.LOAD_CANVAS)
                        );
                        break;
                    case "draw":
                        ACTIVE_CANVAS_SESSIONS.get(canvasCode).handlePacket(ctx,
                                REQUEST_HANDLERS.get(RequestType.DRAW_PIXEL)
                        );
                        break;
                }
            });

            ws.onClose(ctx -> {
                for (CanvasSession session : ACTIVE_CANVAS_SESSIONS.values()) {

                    String participantUUID = ctx.sessionId();

                    if (session.getParticipants().contains(participantUUID)) {
                        session.removeParticipant(participantUUID);
                    }
                }
                USERS.remove(ctx);
            });
        });
    }

    private static String generateCanvasSession(String creatorUUID) throws IOException {

        RandomStringGenerator generator = new RandomStringGenerator.Builder().withinRange('A', 'Z').get();
        String canvasCode = "";

        boolean isUniqueCanvasCode = false;
        while (!isUniqueCanvasCode) {
            canvasCode = generator.generate(8);
            isUniqueCanvasCode = !ACTIVE_CANVAS_SESSIONS.containsKey(canvasCode);
        }
        CanvasSession newCanvasSession = new CanvasSession(canvasCode, creatorUUID);

        ACTIVE_CANVAS_SESSIONS.put(canvasCode, newCanvasSession);

        CanvasDatabase.addCanvasToDatabase(newCanvasSession); // TODO:  NotSerializableException
        return canvasCode;
    }
}