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
        String[] ids = request.getParameterValues("id");
        List<Integer> idList = null;
        if (ids != null) {
            idList = new ArrayList<>();
            for (String id : ids) {
                idList.add(Integer.parseInt(id));
            }
        }

        SeriesFilter filter = new SeriesFilter(name, "rf", idList);
        SeriesService ss = new SeriesService();
        try {
            List<Series> seriesList = ss.getSeries(filter);
            
            String out = "{\"series\":[";
            for(Series s : seriesList) {
                out += s + ",";
            }
            out += "]}";
            
            response.setContentType("application/json");
            try(PrintWriter pw = response.getWriter()) {
                pw.write(out);
            }
        } catch (SQLException ex) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try(PrintWriter pw = response.getWriter()) {
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
System.out.println(description);
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
            ss.addSeries(name, pattern, system, description);
        } catch (SQLException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Error updating database - " + e.getMessage() + "\"}");
            }
        }

        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"message\":\"Series lookup successfully added to the database.\"}");
        }
    }

}
