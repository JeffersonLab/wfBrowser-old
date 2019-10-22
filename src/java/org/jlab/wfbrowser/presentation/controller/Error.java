package org.jlab.wfbrowser.presentation.controller;

import java.io.IOException;
import java.io.PrintWriter;
import javax.ejb.EJBAccessException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author ryans
 */
@WebServlet(name = "Error", urlPatterns = {"/error"})
public class Error extends HttpServlet {

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Throwable throwable = (Throwable) request.getAttribute("javax.servlet.error.exception");
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        String servletName = (String) request.getAttribute("javax.servlet.error.servlet_name");
        String requestUri = (String) request.getAttribute("javax.servlet.error.request_uri");

        String message = "An unknown error has occurred.";

        if (throwable != null) {
            System.err.println("Throwable Class: " + throwable.getClass());

            if (throwable instanceof EJBAccessException) {
                message = "You are not authorized to perform the requested action.";
            } else {
                message = throwable.getMessage();
            }
        }

        System.err.println("Status Code: " + statusCode);
        System.err.println("Servlet Name: " + servletName);
        System.err.println("Request URI: " + requestUri);

        String contentType = response.getContentType();
        if (contentType.startsWith("application/json")) {
            try (PrintWriter pw = response.getWriter()) {
                pw.print("{\"error\":\"" + message + "\"}");
            }
        } else {
            request.setAttribute("message", message);
            request.getRequestDispatcher("/WEB-INF/views/error.jsp").forward(request, response);
        }
    }


    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }
}
