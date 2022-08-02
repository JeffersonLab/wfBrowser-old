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
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
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
@WebServlet(name = "SeriesAjax", urlPatterns = {"/ajax/series"})
public class SeriesAjax extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SeriesAjax.class.getName());
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Process the request parameters
        String name = request.getParameter("name");
        String system = request.getParameter("system");
        String[] ids = request.getParameterValues("id");
        List<Integer> idList = null;
        if (ids != null) {
            idList = new ArrayList<>();
            for (String id : ids) {
                idList.add(Integer.parseInt(id));
            }
        }

        List<String> nameList;
        if (name != null) {
            nameList = Arrays.asList(name);
        } else {
            nameList = new ArrayList<>();
        }
        SeriesFilter filter = new SeriesFilter(nameList, system, idList);
        SeriesService ss = new SeriesService();
        try {
            List<Series> seriesList = ss.getSeries(filter);

            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonArrayBuilder jab = Json.createArrayBuilder();
            if (seriesList != null) {
                for (Series s : seriesList) {
                    jab.add(s.toJsonObject());
                }
            }
            job.add("series", jab.build());

            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write(job.build().toString());
            }
        } catch (SQLException ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error querying database - " + ex.getMessage() + "\"}");
            }
        }

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

        // Process the request parameters
        String system = request.getParameter("system");
        String name = request.getParameter("name");
        String pattern = request.getParameter("pattern");
        String description = request.getParameter("description");
        String units = request.getParameter("units");
        Double yMin, yMax;

        try {
            yMin = request.getParameter("ymin") == "" ? null : Double.valueOf(request.getParameter("ymin"));
            yMax = request.getParameter("ymax") == "" ? null : Double.valueOf(request.getParameter("ymax"));
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'yMin' or 'yMax' not empty or a valid double\"}");
            }
            return;
        }


        String error = "";
        if (system == null || system.isEmpty()) {
            error += " system";
        }
        if (name == null || name.isEmpty()) {
            error += " name";
        }
        if (pattern == null || pattern.isEmpty()) {
            error += " pattern";
        }
        if (!error.isEmpty()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameters - " + error + "\"}");
            }
        }

        SeriesService ss = new SeriesService();
        try {
            ss.addSeries(name, pattern, system, description, units, yMin, yMax);
        } catch (SQLException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error updating database - " + e.getMessage() + "\"}");
                LOGGER.log(Level.SEVERE, "Error updating database - " + e.getMessage());
            }
            return;
        }

        try (PrintWriter pw = response.getWriter()) {
            response.setContentType("application/json");
            pw.write("{\"message\":\"Series lookup successfully added to the database.\"}");
            LOGGER.log(Level.INFO, "Added series '" + name + "', '" + pattern + "', '" + units + "', '" + description + "', '" + system);
        }
    }

}
