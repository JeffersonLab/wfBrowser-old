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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jlab.wfbrowser.business.filter.SeriesFilter;
import org.jlab.wfbrowser.business.filter.SeriesSetFilter;
import org.jlab.wfbrowser.business.util.SqlUtil;
import org.jlab.wfbrowser.model.Series;
import org.jlab.wfbrowser.model.SeriesSet;

/**
 *
 * @author adamc
 */
public class SeriesService {

    private static final Logger LOGGER = Logger.getLogger(EventService.class.getName());

    public List<Series> getSeries(SeriesFilter filter) throws SQLException {
        List<Series> seriesList = new ArrayList<>();

        String sql = "SELECT series_id, system_name, pattern, series_name, description, units"
                + " FROM series"
                + " JOIN system_type"
                + " ON system_type.system_id = series.system_id"
                + filter.getWhereClause()
                + " ORDER BY series_name";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        System.out.println(sql);
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(sql);
            filter.assignParameterValues(pstmt);
            rs = pstmt.executeQuery();

            String name, system, pattern, description, units;
            int id;
            while (rs.next()) {
                id = rs.getInt("series_id");
                name = rs.getString("series_name");
                system = rs.getString("system_name");
                pattern = rs.getString("pattern");
                description = rs.getString("description");
                units = rs.getString("units");
                seriesList.add(new Series(name, id, pattern, system, description, units));
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
    public void addSeries(String name, String pattern, String system, String description, String units) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "INSERT INTO series (pattern, series_name, system_id, description, units) VALUES (?,?,?,?,?)";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, pattern);
            pstmt.setString(2, name);
            pstmt.setInt(3, systemId);
            pstmt.setString(4, description);
            pstmt.setString(5, units);
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

    public void updateSeries(int seriesId, String name, String pattern, String description, String system, String units) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "UPDATE series set pattern = ?, series_name = ?, system_id = ?, description = ?, units = ? "
                + "WHERE series_id = ?";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, pattern);
            pstmt.setString(2, name);
            pstmt.setInt(3, systemId);
            pstmt.setString(4, description);
            pstmt.setString(5, units);
            pstmt.setInt(6, seriesId);
            int n = pstmt.executeUpdate();
            if (n < 1) {
                conn.rollback();
                String msg = "Error adding series to database.  No change made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            } else if (n > 1) {
                conn.rollback();
                String msg = "Error adding series to database.  More than one row would be updated.  No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }
            conn.commit();
        } finally {
            SqlUtil.close(pstmt, conn);
        }
    }

    public void updateSeriesSet(int setId, String name, Set<Series> set, String description, String system) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "UPDATE series_sets set set_name = ?, system_id = ?, description = ?"
                + " WHERE set_id = ?";
        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setInt(2, systemId);
            pstmt.setString(3, description);
            pstmt.setInt(4, setId);
            int n = pstmt.executeUpdate();
            if (n < 1) {
                conn.rollback();
                String msg = "Error updating series set in database.  No change made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            } else if (n > 1) {
                conn.rollback();
                String msg = "Error updating series set in database.  More than one row would be updated.  No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }

            pstmt.close();
            String delSql = "DELETE FROM series_set_contents WHERE set_id = ?";
            pstmt = conn.prepareStatement(delSql);
            pstmt.setLong(1, setId);
            n = pstmt.executeUpdate();
            if (n < 1) {
                conn.rollback();
                String msg = "Error updating series set in database.  Found no existing series in series set.  No change made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }

            pstmt.close();
            String insSql = "INSERT INTO series_set_contents (set_id, series_id) VALUES(?,?)";
            pstmt = conn.prepareStatement(insSql);
            for (Series series : set) {
                pstmt.setLong(1, setId);
                pstmt.setLong(2, series.getId());
                n = pstmt.executeUpdate();
                if (n != 1) {
                    conn.rollback();
                    String msg = "Error updating series set in database.  Error updating series in series set.  No change made.";
                    LOGGER.log(Level.WARNING, msg);
                    throw new SQLException(msg);
                }
            }
            conn.commit();
        } catch (SQLException ex) {
            if (conn != null) {
                conn.rollback();
            }
            throw new SQLException(ex);
        } finally {
            SqlUtil.close(pstmt, conn);
        }
    }

    public void deleteSeries(int seriesId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "DELETE FROM series WHERE series_id = ?";

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

    public void deleteSeriesSet(int setId) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;

        String sql = "DELETE FROM series_sets WHERE set_id = ?";

        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, setId);

            int numUpdates = pstmt.executeUpdate();
            if (numUpdates < 1) {
                conn.rollback();
                String msg = "Error deleting series set.  No series sets were deleted.  No changes made.";
                LOGGER.log(Level.WARNING, "{0}  SQL: {1} setId: {2}", new Object[]{msg, sql, setId});
                throw new SQLException(msg);
            }
            if (numUpdates > 1) {
                conn.rollback();
                String msg = "Error deleting series set.  More than one series set would have been deleted. No changes made.";
                LOGGER.log(Level.WARNING, msg);
                throw new SQLException(msg);
            }
        } finally {
            SqlUtil.close(pstmt, conn);
        }
    }

    public List<SeriesSet> getSeriesSets(SeriesSetFilter filter) throws SQLException {
        String setSql = "SELECT set_id, system_name, set_name, description"
                + " FROM series_sets"
                + " JOIN system_type ON system_type.system_id = series_sets.system_id" + filter.getWhereClause();

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<SeriesSet> ss = new ArrayList<>();
        try {
            conn = SqlUtil.getConnection();
            pstmt = conn.prepareStatement(setSql);
            filter.assignParameterValues(pstmt);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int setId = rs.getInt("set_id");
                String setName = rs.getString("set_name");
                String systemName = rs.getString("system_name");
                String description = rs.getString("description");
                ss.add(new SeriesSet(new HashSet<Series>(), setName, setId, systemName, description));
            }

            pstmt.close();
            String seriesSql = "SELECT series_name,series.series_id,pattern,description,units"
                    + " FROM series"
                    + " JOIN series_set_contents ON series_set_contents.series_id = series.series_id"
                    + " WHERE set_id = ?";
            pstmt = conn.prepareStatement(seriesSql);
            for (SeriesSet seriesSet : ss) {
                pstmt.setInt(1, seriesSet.getId());
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    String seriesName = rs.getString("series_name");
                    int seriesId = rs.getInt("series_id");
                    String pattern = rs.getString("pattern");
                    String seriesDescription = rs.getString("description");
                    String units = rs.getString("units");
                    seriesSet.addSeries(new Series(seriesName, seriesId, pattern, seriesSet.getSystemName(), seriesDescription, units));
                }
            }
        } finally {
            SqlUtil.close(pstmt, rs, conn);
        }
        return ss;
    }

    public void addSeriesSet(String name, String system, String description, Set<Series> set) throws SQLException {
        SystemService ss = new SystemService();
        int systemId = ss.getSystemId(system);

        String setSql = "INSERT INTO series_sets (system_id, set_name, description) VALUES (?, ?, ?)";
        String seriesSql = "INSERT INTO series_set_contents (set_id, series_id) VALUES (?,?)";

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = SqlUtil.getConnection();
            conn.setAutoCommit(false);
            pstmt = conn.prepareStatement(setSql, Statement.RETURN_GENERATED_KEYS);
            pstmt.setInt(1, systemId);
            pstmt.setString(2, name);
            pstmt.setString(3, description);
            if (pstmt.executeUpdate() <= 0) {
                conn.rollback();
                throw new SQLException("Unable to create series set.  No row created in database.");
            }

            ResultSet rse = pstmt.getGeneratedKeys();
            long setId;
            if (rse != null && rse.next()) {
                setId = rse.getLong(1);
            } else {
                conn.rollback();
                throw new RuntimeException("Error querying database for last inserted set_id");
            }

            pstmt = conn.prepareStatement(seriesSql);
            for (Series series : set) {
                pstmt.setLong(1, setId);
                pstmt.setInt(2, series.getId());
                pstmt.execute();
            }
            conn.commit();
        } finally {
            SqlUtil.close(rs, pstmt, conn);
        }
    }
}
