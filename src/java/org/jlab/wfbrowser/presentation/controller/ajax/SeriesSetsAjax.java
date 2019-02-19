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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.service.SeriesService;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;

/**
 *
 * @author adamc
 */
@WebServlet(name = "SeriesSetsAjax", urlPatterns = {"/ajax/series-sets"})
public class SeriesSetsAjax extends HttpServlet {

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

        List<Long> idList = null;
        if (ids != null) {
            idList = new ArrayList<>();
            for (String id : ids) {
                idList.add(Long.parseLong(id));
            }
        }

        List<String> nameList = null;
        if (name != null) {
            nameList = Arrays.asList(name);
        }

        SeriesSetFilter filter = new SeriesSetFilter(idList, system, nameList);
        SeriesService ss = new SeriesService();
        try {
            List<SeriesSet> seriesSetList = ss.getSeriesSets(filter);

            JsonObjectBuilder job = Json.createObjectBuilder();
            JsonArrayBuilder jab = Json.createArrayBuilder();
            for (SeriesSet s : seriesSetList) {
                jab.add(s.toJsonObject());
            }
            job.add("seriesSets", jab.build());
            JsonObject jo = job.build();

            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write(jo.toString());
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
        String set = request.getParameter("set");
        String description = request.getParameter("description");

        String error = "";
        if (system == null || system.isEmpty()) {
            error += " system";
        }
        if (name == null || name.isEmpty()) {
            error += " name";
        }
        if (set == null || set.isEmpty()) {
            error += " set";
        }
        if (!error.isEmpty()) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameters - " + error + "\"}");
            }
        }

        // set should have to be not null to make it through error checking above
        List<String> nameList = Arrays.asList(set.split(","));

        Set<Series> seriesSet = new HashSet<>();
        SeriesService ss = new SeriesService();
        //Lookup the specified series by name, then create a set of them
        try {
            SeriesFilter sFilter = new SeriesFilter(nameList, system, null);
            seriesSet.addAll(ss.getSeries(sFilter));
            if (seriesSet.size() != nameList.size()) {
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                List<String> returnedSeries = new ArrayList<>();
                for (Series s : seriesSet) {
                    returnedSeries.add(s.getName());
                }
                try (PrintWriter pw = response.getWriter()) {
                    pw.write("{\"error\":\"Database found different number of series than specified.  Specified: [" + set
                            + "]  Returned: [" + String.join(",", returnedSeries) + "]\"}");
                }
                return;
            }
        } catch (SQLException ex) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error looking up series in database - " + ex.getMessage() + "\"}");
            }
            return;
        }

        // Add the new SeriesSet
        try {
            ss.addSeriesSet(name, system, description, seriesSet);
        } catch (SQLException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error updating database - " + e.getMessage() + "\"}");
            }
            return;
        }

        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"message\":\"Series lookup successfully added to the database.\"}");
        }
    }

}
