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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.model.Series;

/**
 *
 * @author adamc
 */
@WebServlet(name = "SeriesSetAddAjax", urlPatterns = {"/ajax/series-set-update"})
public class SeriesSetUpdateAjax extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SeriesSetUpdateAjax.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html");
        System.out.println(request.toString());
    }

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
        String id = request.getParameter("id");
        String system = request.getParameter("system");
        String name = request.getParameter("name");
        String set = request.getParameter("set");
        String description = request.getParameter("description");

        if (id == null || system == null || system.isEmpty() || name == null || name.isEmpty() || set == null || set.isEmpty()
                || description == null || description.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameters - id, system, name, set, or description\"}");
            }
            return;
        }

        int setId;
        try {
            setId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'id' not a valid integer\"}");
            }
            return;
        }

        SeriesService ss = new SeriesService();
        try {
            List<String> requestSeries = Arrays.asList(set.split(","));
            SeriesFilter filter = new SeriesFilter(requestSeries, "rf", null);
            Set<Series> seriesSet = new HashSet<>(ss.getSeries(filter));
            
            // Check that the database returned the same number of series as the user wanted to have in the update.
            // It could be that the user entered in a series name that doesn't exist or that they included a name twice.
            if (requestSeries.size() != seriesSet.size()) {
                List<String> returnedSeries = new ArrayList<>();
                for(Series s : seriesSet) {
                    returnedSeries.add(s.getName());
                }
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter pw = response.getWriter()) {
                    pw.write("{\"error\":\"Database found different number of series than specified.  Specified: [" + set
                            + "]  Returned: [" + String.join(",", returnedSeries) + "]\"}");
                }
                return;
            }
            
            ss.updateSeriesSet(setId, name, seriesSet, description, system);
        } catch (SQLException ex) {
            String msg = "Error updating database - " + ex.getMessage();
            LOGGER.log(Level.WARNING, "Error updating database", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\":\"" + msg + "\"}");
            }
            return;
        }

        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.print("{\"message\":\"Successful update\"}");
        }

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
