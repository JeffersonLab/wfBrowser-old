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
import java.util.logging.Level;
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

        String sql = "SELECT series_id, system_name, pattern, series_name, description"
                + " FROM waveforms.series"
                + " JOIN waveforms.system_type"
                + " ON system_type.system_id = series.system_id";
        sql += filter.getWhereClause();

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            filter.assignParameterValues(pstmt);
            rs = pstmt.executeQuery();

            String name, system, pattern, description;
            int id;
            while (rs.next()) {
                id = rs.getInt("series_id");
                name = rs.getString("series_name");
                system = rs.getString("system_name");
                pattern = rs.getString("pattern");
                description = rs.getString("description");
                seriesList.add(new Series(name, id, pattern, system, description));
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
     * @param description A user created description for the series
     * @throws java.sql.SQLException
     */
    public void addSeries(String name, String pattern, String system, String description) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "INSERT INTO waveforms.series (pattern, series_name, system_id, description) VALUES (?,?,?,?)";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, pattern);
            pstmt.setString(2, name);
            pstmt.setInt(3, systemId);
            pstmt.setString(4, description);
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

    public void updateSeries(int seriesId, String name, String pattern, String description, String system) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "UPDATE waveforms.series set pattern = ?, series_name = ?, system_id = ?, description = ? "
                + "WHERE series_id = ?";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, pattern);
            pstmt.setString(2, name);
            pstmt.setInt(3, systemId);
            pstmt.setString(4, description);
            pstmt.setInt(5, seriesId);
            int n = pstmt.executeUpdate();
            if (n < 1) {
                conn.rollback();
                String msg = "Error adding series to database.  No change made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException("msg");
            } else if (n > 1) {
                conn.rollback();
                String msg = "Error adding series to database.  More than one row would be updated.  No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }
        } finally {
            SqlUtil.close(pstmt, conn);
        }
    }

    public void deleteSeries(int seriesId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "DELETE FROM waveforms.series WHERE series_id = ?";

        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, seriesId);
            int numUpdates = pstmt.executeUpdate();
            if (numUpdates < 1) {
                conn.rollback();
                String msg = "Error deleting series.  No series were deleted.  No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }
            if (numUpdates > 1) {
                conn.rollback();
                String msg = "Error deleting series.  More than one series would have been deleted. No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }
        } finally {
            SqlUtil.close(pstmt, conn);
        }
    }
}
