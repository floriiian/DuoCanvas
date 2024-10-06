package org.florian.duocanvas.db;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.florian.duocanvas.Main;
import org.florian.duocanvas.session.CanvasSession;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;

public class CanvasDatabase {

    private static Connection CONNECTION = null;
    private static final Logger LOGGER = LogManager.getLogger();

    public static boolean initiateDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            CONNECTION = DriverManager.getConnection("jdbc:sqlite:rplace.db");

            LOGGER.debug("Connection to database initialized.");

            Statement stmt = CONNECTION.createStatement();

            String createStorage = "CREATE TABLE IF NOT EXISTS canvasStorage"
                    + "(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "canvas_code TEXT NOT NULL,"
                    + "canvas_data BLOB NOT NULL"
                    + ")";
            stmt.executeUpdate(createStorage);
            stmt.close();
            return true;
        } catch (Exception e) {
            LOGGER.debug(e);
            return false;
        }
    }

    public static void addCanvasToDatabase(CanvasSession session) throws IOException {

        String canvasCode = session.canvasCode;
        byte[] canvasData = getCanvasDataAsBytes(session); // TODO !


        try {
            String sql = "INSERT INTO canvasStorage (canvas_code, canvas_data) VALUES (?, ?)";

            PreparedStatement preparedStmt = CONNECTION.prepareStatement(sql);
            preparedStmt.setString(1, canvasCode);
            preparedStmt.setBytes(2, canvasData);

            preparedStmt.executeUpdate();
            preparedStmt.close();
        } catch (Exception e) {
            LOGGER.debug(e);
            return;
        }

        LOGGER.debug("Canvas with code: {} has been successfully added.", canvasCode);
    }

    public static void removeCanvasFromDatabase(String canvasCode) {
        try {
            String deleteCanvas = "DELETE FROM canvasStorage WHERE canvas_code = ?";
            PreparedStatement preparedSTMT = CONNECTION.prepareStatement(deleteCanvas);

            preparedSTMT.setString(1, canvasCode);
            preparedSTMT.executeUpdate(deleteCanvas);
            preparedSTMT.close();
        } catch (Exception e) {
            LOGGER.debug(e);
        }
    }

    public static void backupCanvasData() throws IOException {

        for (CanvasSession session : Main.ACTIVE_CANVAS_SESSIONS.values()) {

            LOGGER.debug(session.canvasCode);

            try {
                String canvasCode = session.canvasCode;
                byte[] canvasData = getCanvasDataAsBytes(session);

                String getResults = "SELECT id FROM canvasStorage WHERE canvas_code = ?";
                PreparedStatement preparedResults = CONNECTION.prepareStatement(getResults);
                preparedResults.setString(1, canvasCode);

                ResultSet results = preparedResults.executeQuery();

                if (results.next()) {

                    String backupCanvas = "UPDATE canvasStorage SET canvas_data = ? WHERE canvas_code = ?";
                    PreparedStatement preparedStmt = CONNECTION.prepareStatement(backupCanvas);

                    preparedStmt.setBytes(1, canvasData);
                    preparedStmt.setString(2, canvasCode);
                    preparedStmt.executeUpdate();
                    preparedStmt.close();
                    LOGGER.debug("{} has been backed up.", canvasCode);
                } else {
                    LOGGER.debug("{} is no longer backed up.", canvasCode);
                    Main.ACTIVE_CANVAS_SESSIONS.remove(canvasCode);
                }
                preparedResults.close();
            } catch (Exception e) {
                LOGGER.debug(e);
            }
        }
    }

    public static byte[] getCanvasDataAsBytes(CanvasSession session) throws IOException {

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);

        objectOutputStream.writeObject(session); // TODO: <--!
        objectOutputStream.flush();
        objectOutputStream.close();

        return byteArrayOutputStream.toByteArray();
    }

    public static CanvasSession getCanvasDataFromBytes(byte[] canvasBytes) throws IOException, ClassNotFoundException {

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(canvasBytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);

        return (CanvasSession) objectInputStream.readObject();
    }

    public static byte[] getCanvasBytesFromDatabase(String canvasCode) {
        try {
            String selectQuery = "SELECT canvas_data FROM canvasStorage WHERE canvas_code = ?";
            PreparedStatement preparedStmt = CONNECTION.prepareStatement(selectQuery);
            preparedStmt.setString(1, canvasCode);

            ResultSet results = preparedStmt.executeQuery();

            if (results.next()) {
                return results.getBytes("canvas_data");
            } else {
                LOGGER.debug("{} doesn't exist.", canvasCode);
            }
            preparedStmt.close();
        } catch (Exception e) {
            LOGGER.debug(e);
        }
        return null;
    }

    public static ArrayList<String> getCanvasCodesFromDatabase() {

        ArrayList<String> canvasCodes = new ArrayList<>();

        try {
            Statement stmt = CONNECTION.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT canvas_code FROM canvasStorage;");

            while (rs.next()) {
                canvasCodes.add(rs.getString("canvas_code"));
            }
        } catch (Exception e) {
            LOGGER.debug(e);
            return null;
        }
        return canvasCodes;
    }

}
