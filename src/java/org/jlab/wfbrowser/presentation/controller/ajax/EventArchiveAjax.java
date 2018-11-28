/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.filter.EventFilter;
import org.jlab.wfbrowser.business.service.EventService;

/**
 *
 * @author adamc
 */
@WebServlet(name = "EventArchiveAjax", urlPatterns = {"/ajax/event-archive"})
public class EventArchiveAjax extends HttpServlet {

    private final static Logger LOGGER = Logger.getLogger(EventArchiveAjax.class.getName());

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String eventId = request.getParameter("id");
        String aString = request.getParameter("archive");

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
        Long id;
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

        // Check that we got an archive parameter and process it
        if (aString == null || aString.isEmpty()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"archive parameter required\"}");
            }
            return;
        }
        boolean archive = Boolean.parseBoolean(aString);

        // If we are unarchiving, make sure the user is an admin
        if (!archive) {
            if (!request.isUserInRole("ADMIN")) {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter pw = response.getWriter()) {
                    pw.write("{\"error\":\"permission denied.  Only admins can unarchive an event\"}");
                }
                return;
            }
        }

        // Set the archive flag
        EventService es = new EventService();
        try {
            es.setEventArchiveFlag(id, archive);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Error setting archive flag in database.", ex);
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error setting archive flag in database - " + ex.getMessage() + "\"}");
            }
            return;
        }

        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"success\":\"Update made\"}");
        }
    }
}
