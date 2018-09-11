package org.jlab.wfbrowser.connectionpools;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import java.io.IOException;
import java.nio.channels.Channel;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.commons.dbcp2.datasources.SharedPoolDataSource;

/**
 * Stolen and modified from the jmya API test suite. Updated to use newer
 * Connector/J. Allows use of the PooledNexus when outside of a Java application
 * server such as Tomcat.
 *
 * JNDI DataSources are required for the PooledNexus to operate and generally
 * these are configured inside of an application server such as Tomcat. This
 * class creates DataSources using the DBCP pooling library to fill the role of
 * an application server when there isn't one configured.
 *
 * @author slominskir
 */
public class StandaloneConnectionPools implements Channel {

    private final InitialContext initCtx;
    private final Context envCtx;
    private final List<SharedPoolDataSource> dsList = new ArrayList<>();

    /**
     * Creates DataSources and publishes them to JNDI.
     *
     * @throws javax.naming.NamingException
     * @throws java.sql.SQLException
     */
    public StandaloneConnectionPools() throws NamingException, SQLException {
        initCtx = new InitialContext();
        envCtx = (Context) initCtx.lookup("java:comp/env");

        String user = "wfb_writer";
        String password = "password";

        // Port is same for all hosts
        int port = 3306;
        String host = "devl21.acc.jlab.org";

        String url = "jdbc:mysql://" + host + ":" + port + "/waveforms";

        MysqlConnectionPoolDataSource pds = new MysqlConnectionPoolDataSource();
        pds.setUrl(url);
        pds.setUser(user);
        pds.setPassword(password);

        pds.setUseCompression(true);
        pds.setNoAccessToProcedureBodies(true);

        // Specific to Connections from a pool
        pds.setCacheCallableStmts(true);
        pds.setCachePrepStmts(true);

        pds.setPrepStmtCacheSqlLimit(1024); // I assume this is max length of SQL query string

        pds.setPrepStmtCacheSize(1024); // Each PV table requires a separate stmt
        pds.setCallableStmtCacheSize(8);

        pds.setServerTimezone("America/New_York");
        
        // These look inteteresting, but can't find any docs on them
        //pds.setUseServerPreparedStmts(true); // Should be true by default?
        //pds.setAutoClosePStmtStreams(true);
        //pds.setAutoReconnect(true);
        //pds.setConnectTimeout(1000);
        //pds.setEnablePacketDebug(true);
        //pds.setDumpQueriesOnException(true);
        //pds.setGatherPerfMetrics(true);
        //pds.setMaxAllowedPacket(1000);
        //pds.setUseStreamLengthsInPrepStmts(true);
        //pds.setUltraDevHack(true);
        //pds.setStrictFloatingPoint(true);
        //pds.setSlowQueryThresholdMillis(1000);
        //pds.setResultSetSizeThreshold(1000);
        //pds.setNetTimeoutForStreamingResults(1000);
        SharedPoolDataSource ds = new SharedPoolDataSource();
        ds.setConnectionPoolDataSource(pds);

        ds.setMaxTotal(20); // Max connections in the pool (regardless of idle vs active)

        envCtx.rebind("jdbc/waveforms_rw", ds);
        dsList.add(ds);
    }

    @Override
    public boolean isOpen() {
        return !dsList.isEmpty();
    }

    @Override
    public void close() throws IOException {
        int errorCount = 0;
        for (SharedPoolDataSource ds : dsList) {
            try {
                ds.close();
            } catch (Exception e) {
                errorCount++;
            }
        }

        if (errorCount > 0) {
            throw new IOException("Unable to close");
        }

        dsList.clear();
    }
}
