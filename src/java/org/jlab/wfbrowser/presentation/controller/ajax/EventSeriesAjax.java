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
import org.jlab.wfbrowser.business.service.EventService;
import org.jlab.wfbrowser.model.Series;

/**
 *
 * @author adamc
 */
@WebServlet(name = "EventSeriesAjax", urlPatterns = {"/ajax/event-series"})
public class EventSeriesAjax extends HttpServlet {

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
        String[] ids = request.getParameterValues("id");
        if (ids == null || ids.length == 0) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameter - id\"}");
            }
            return;
        }

        List<Long> eventIdList = new ArrayList<>();
        try {
            for (String id : ids) {
                eventIdList.add(Long.parseLong(id));
            }
        } catch (NumberFormatException ex) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'id' is not a valid integer\"}");
            }
            return;
        }

        EventService es = new EventService();
        List<Series> seriesList;

        try {
            seriesList = es.getSeries(eventIdList);
        } catch (SQLException e) {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'id' is not a valid integer\"}");
            }
            return;
        }

        response.setContentType("application/json");
        try (PrintWriter pw = response.getWriter()) {
            pw.write("{\"series\":[");
            if (seriesList != null && !seriesList.isEmpty()) {
                pw.write("\"");
                List<String> names = new ArrayList<>();
                for (Series series : seriesList) {
                    names.add(series.getName());
                }
                pw.write(String.join("\",\"", names));
                pw.write("\"");
            }
            pw.write("]}");
        }
    }
}
