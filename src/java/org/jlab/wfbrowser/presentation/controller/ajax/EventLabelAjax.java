/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Label;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.stream.JsonParsingException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A controller for allowing the update of an event's label
 * @author adamc
 */
@WebServlet(name = "EventLabelAjax", urlPatterns = {"/ajax/event-label"})
public class EventLabelAjax extends HttpServlet {

    private final static Logger LOGGER = Logger.getLogger(EventLabelAjax.class.getName());

    /**
     * Handles the HTTP <code>POST</code> method.
     * This method is used to label an event in the database accroding to the output a model determining which cavity
     * faulted and the type of fault that occurred.  It expects to get a label parameter that is a valid JSON string
     * for creating a Label object.  Only one label can exist per event.  Providing force=true will overwrite the existing
     * label with the supplied label.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

//        String userName = request.getUserPrincipal().getName();
        String userName = "testUser";
        LOGGER.log(Level.INFO, "User " + userName + " attempting to label event");

        String eventId = request.getParameter("id");
        String labelParam = request.getParameter("label");
        String forceParam = request.getParameter("force");

        // Check that we got an id parameter
        if (eventId == null || eventId.isEmpty()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"id parameter required\"}");
            }
            return;
        }

        // Process the id parameter
        long id;
        try {
            id = Long.parseLong(eventId);
        } catch (NumberFormatException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error process id - " + e.getMessage() + "\"}");
            }
            return;
        }

        // Check that we got an label parameter
        if (labelParam == null || labelParam.isEmpty()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"label parameter required\"}");
            }
            return;
        }

        // Process the label parameter
        Label label;
        if (labelParam.equals("null")) {
            label = null;
        } else {
            try {
                label = new Label(Json.createReader(new StringReader(labelParam)).readObject());
            } catch (JsonException ex) {
                LOGGER.log(Level.WARNING, "Problem processing label - " + ex.getMessage());
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter pw = response.getWriter()) {
                    pw.write("{\"error\":\"Problem processing label - " + ex.getMessage() + "\"}");
                }
                return;
            }
        }

        // Set the label
        boolean force = forceParam == null ? Boolean.FALSE : Boolean.parseBoolean(forceParam);
        EventService es = new EventService();
        try {
            if (label == null) {
                es.deleteEventLabel(id);
            } else {
                es.setEventLabel(id, label, force);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error setting label in database.", ex);
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error setting label in database - " + ex.getMessage() + "\"}");
            }
            return;
        }

        // Send out the response
        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"success\":\"Update made\"}");
        }
        if (labelParam.equals("null")) {
            LOGGER.log(Level.INFO, "User " + userName + " successfully unlabeled event " + eventId);
        } else {
            LOGGER.log(Level.INFO, "User " + userName + " successfully labeled event " + eventId);
        }
    }
}
