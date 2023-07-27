/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jlab.wfbrowser.business.util.SqlUtil;

/**
 * Provides basic operations on the system_type table in the waveforms database
 * @author adamc
 */
public class SystemService {

    public int getSystemId(String systemName) throws SQLException {

        if (systemName == null || systemName.isEmpty()) {
            throw new SQLException("Invalid system name.  Found null or ''.");
        }
        String systemSql = "SELECT system_id FROM waveforms.system_type WHERE system_name = ?";
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        int systemId = 0; // -1 shouldn't match any system ID in the database.

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(systemSql);
            pstmt.setString(1, systemName);
            rs = pstmt.executeQuery();
            int i = 0;
            while (rs.next()) {
                systemId = rs.getInt("system_id");  // returns zero if database entry is null
                i++;
                if (i > 1) {
                    // A system name should be unique.  If it isn't, then throw an error.
                    throw new SQLException("System name matched more than one system ID");
                }
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        if (systemId == 0) {
            throw new SQLException("Invalid system name '" + systemName + "'.");
        }

        return systemId;
    }
}
