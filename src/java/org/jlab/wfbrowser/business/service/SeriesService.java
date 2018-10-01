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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.model.Series;

/**
 *
 * @author adamc
 */
public class SeriesService {

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());

    public List<Series> getSeries(SeriesFilter filter) throws SQLException {
        List<Series> seriesList = new ArrayList<>();
        
        String sql = "SELECT pattern_id, system_name, pattern, series_name, comment"
                + " FROM waveforms.series_patterns"
                + " JOIN waveforms.system_type"
                + " ON system_type.system_id = series_patterns.system_id";
        sql += filter.getWhereClause();
        
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            filter.assignParameterValues(pstmt);
            rs = pstmt.executeQuery();
            
            String name, system, pattern, comment;
            int id;
            while(rs.next()) {
                id = rs.getInt("pattern_id");
                name = rs.getString("series_name");
                system = rs.getString("system_name");
                pattern = rs.getString("pattern");
                comment = rs.getString("comment");
                seriesList.add(new Series(name, id, pattern, system, comment));
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
        
        return seriesList;
    }
    
    /**
     * Add a named series lookup pattern to the database
     *
     * @param name The name of the series to lookup. Must be unique.
     * @param pattern The SQL "like" pattern to be used to match a series
     * @param system The system for which the pattern is intended
     * @param comment A user created comment for the series
     * @throws java.sql.SQLException
     */
    public void addSeries(String name, String pattern, String system, String comment) throws SQLException {

        String systemSql = "SELECT system_id FROM waveforms.system_type WHERE system_name = ?";
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        int systemId = -1; // -1 shouldn't match any system in the database.
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(systemSql);
            pstmt.setString(1, system);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                systemId = rs.getInt("system_id");
                if (!rs.isAfterLast()) {
                    // A system name should be unique.  If it isn't, then throw an error.
                    throw new SQLException("System name matched more than one system ID");
                }
            }
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }

        String sql = "INSERT INTO waveforms.series_patterns (pattern, series_name, system_id, comment) VALUES (?,?,?,?)";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, pattern);
            pstmt.setString(2, name);
            pstmt.setInt(3, systemId);
            pstmt.setString(4, comment);
            int n = pstmt.executeUpdate();
            if (n < 1) {
                throw new SQLException("Error adding series to database.  No change made");
            } else if (n > 1) {
                conn.rollback();
                throw new SQLException("Error adding series to database.  More than one row updated");
            }
        } finally {
            SqlUtil.close(pstmt, conn);
        }

    }
}
