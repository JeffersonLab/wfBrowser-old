/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jlab.wfbrowser.business.service.SeriesService;

/**
 *
 * @author adamc
 */
@WebServlet(name = "SeriesDeleteAjax", urlPatterns = {"/ajax/series-delete"})
public class SeriesDeleteAjax extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(SeriesDeleteAjax.class.getName());

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

        if (id == null || id.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameter - id\"}");
            }
            return;
        }

        int seriesId;
        try {
            seriesId = Integer.parseInt(id);
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
            ss.deleteSeries(seriesId);
        } catch (SQLException ex) {
            String msg = "Error udpating database - " + ex.getMessage();
            LOGGER.log(Level.WARNING, msg);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\":\"" + msg + "\"}");
            }
            return;
        }
        
        try (PrintWriter pw = response.getWriter()) {
            response.setContentType("application/json");
            pw.print("{\"message\":\"Successful deletion\"}");
        }
    }

}
