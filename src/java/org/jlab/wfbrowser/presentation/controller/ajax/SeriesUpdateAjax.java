/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.ajax;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author adamc
 */
@WebServlet(name = "SeriesAddAjax", urlPatterns = {"/ajax/series-update"})
public class SeriesUpdateAjax extends HttpServlet {

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
        String pattern = request.getParameter("pattern");
        String description = request.getParameter("description");

        try {
        int seriesId = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Parameter 'id' not a valid integer\"}");
            }
            return;
        }
        if (name == null || pattern == null || description == null || system == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            try (PrintWriter pw = response.getWriter()) {
                pw.write("{\"error\":\"Missing required parameters - name, pattern, description, or system\"}");
            }
            return;            
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
