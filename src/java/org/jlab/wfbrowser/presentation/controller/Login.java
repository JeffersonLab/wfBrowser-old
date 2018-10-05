package org.jlab.wfbrowser.presentation.controller;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author ryans
 */
@WebServlet(name = "Login", urlPatterns = {"/login"})
public class Login extends HttpServlet {

    /**
     * Handles the HTTP
     * <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    /**
     * Handles the HTTP
     * <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        /*
         * Called in one of three ways:
         * 1. User submits form on login page (requester param = login)
         * 2. User requests protected resource via POST and isn't authenticated 
         * causing security check to forward here
         * 3. User requests /login via POST directly (likely vulnerability hack)
         */
        String requester = request.getParameter("requester");
        if ("login".equals(requester)) { // login attempt
            String username = request.getParameter("username");
            String password = request.getParameter("password");
            try {
                request.login(username, password);
                // If here then login success
                String returnUrl = request.getParameter("returnUrl");
                if (returnUrl == null || returnUrl.isEmpty()) {
                    returnUrl = request.getContextPath();
                }
                response.sendRedirect(response.encodeRedirectURL(returnUrl));
            } catch (ServletException e) {
                /* either: 
                 * (1) container doesn't support auth
                 * (2) user already authenticated
                 * (3) authentication failed (bad username/password)
                 */
                request.setAttribute("message", "Invalid username or password");
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);

            }
        } else { // either unauthenticated request for resource or direct post; either way show login page
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
        }
    }
}
