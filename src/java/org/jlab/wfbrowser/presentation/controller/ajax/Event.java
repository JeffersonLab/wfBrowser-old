/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.filter.WaveformFilter;
import org.jlab.wfbrowser.business.service.WaveformService;

/**
 *
 * @author adamc
 */
@WebServlet(name = "event", urlPatterns = {"/ajax/event"})
public class Event extends HttpServlet {

    private final static Logger LOGGER = Logger.getLogger(Event.class.getName());

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method. Used to query for "Event" data
     * as REST API end point.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        String[] eArray = request.getParameterValues("id");
        List<Long> eventIdList = new ArrayList<>();
        if (eArray != null) {
            for (String eventId : eArray) {
                if (eventId != null && (!eventId.isEmpty())) {
                    eventIdList.add(Long.valueOf(eventId));
                }
            }
        }
        
        if (eventIdList.isEmpty()) {
            try (PrintWriter pw = response.getWriter()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                pw.write("{\"error\":\"No event IDs specified\"}");
            }
            return;
        }
        
        WaveformService wfs = new WaveformService();
        // Enforce an rf system filter since this is likely to be an interface for only RF systems for some time
        WaveformFilter filter = new WaveformFilter(eventIdList, null, null, "rf", null, null);

        JsonObjectBuilder job = null;
        try {
            List<org.jlab.wfbrowser.model.Event> eventList = wfs.getEventList(filter);
            job = Json.createObjectBuilder();
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (org.jlab.wfbrowser.model.Event e : eventList) {
                jab.add(e.toJsonObject());
            }
            job.add("events", jab.build());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Error querying database");
            // TODO: Update this to generate a JSON response with approriate error state, message, and HTTP status code
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\": \"error querying database - " + ex.getMessage() + "\"}");
            }
        }

        try (PrintWriter pw = response.getWriter()) {
            if (job != null) {
                pw.print(job.build().toString());
            } else {
                pw.print("{\"error\":\"null response\"}");
            }
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost(req, resp); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
