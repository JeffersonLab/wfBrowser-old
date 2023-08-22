/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.presentation.controller.admin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
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
@WebServlet(name = "SeriesAdmin", urlPatterns = {"/admin/series"})
public class SeriesAdmin extends HttpServlet {

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.  Used for displaying the form for adding new series.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        SeriesFilter filter = new SeriesFilter(null, null, null);
        SeriesService ss = new SeriesService();
        List<Series> seriesList;
        try {
            seriesList = ss.getSeries(filter);
        } catch (SQLException ex) {
            Logger.getLogger(SeriesAdmin.class.getName()).log(Level.SEVERE, null, ex);
            throw new ServletException(ex);
        }
        request.setAttribute("seriesList", seriesList);
        request.getRequestDispatcher("/WEB-INF/views/admin/series.jsp").forward(request, response);
    }
}
