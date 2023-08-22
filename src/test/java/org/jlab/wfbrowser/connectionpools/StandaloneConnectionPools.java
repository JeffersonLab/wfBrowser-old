package org.jlab.wfbrowser.connectionpools;

import java.io.IOException;
import java.sql.SQLException;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.mariadb.jdbc.MariaDbPoolDataSource;

/**
 * JNDI DataSources are required the SqlUtil class to operate and generally
 * these are configured inside an application server such as Tomcat or Wildfly.
 * This class creates DataSources using the MariaDbPoolDataSource
 * (https://mariadb.com/kb/en/pool-datasource-implementation/).
 *
 * @author adamc
 * @implNote I found that the tests would run into problems when multiple datasources had been created and closed.  The
 * second datasource would not open properly (maybe the first didn't really close on the database side?).  This now
 * creates a single database pool and uses it throughout the tests.  The tests should not try to close the database
 * pool connection as it will terminate with the tests.
 */
public class StandaloneConnectionPools {

    private static final InitialContext initCtx;
    private static final Context envCtx;

    static {
        new StandaloneJndi();
        try {
            initCtx = new InitialContext();
            envCtx = (Context) initCtx.lookup("java:comp/env");
        } catch (NamingException e) {
            throw new RuntimeException(e);
        }
    }

    private static MariaDbPoolDataSource ds = null;
    private static String ds_name = "jdbc/waveforms_rw";

    /**
     * This can be called as many times as desired.  It only has effect if no data source exists.
     * @throws NamingException
     * @throws SQLException
     */
    public static void setupConnectionPool() throws NamingException, SQLException {
        if (ds != null) {
            return;
        }
        String user = "wftest_writer";
        String password = "password";

        // Port is same for all hosts
        int port = 3306;
        String host = "localhost";

        String url = "jdbc:mariadb://" + host + ":" + port + "/waveformstest?user=" + user + "&password=" + password
                + "&maxIdleTime=60";

        ds = new MariaDbPoolDataSource(url);
        envCtx.rebind(ds_name, ds);
    }

    /**
     * Creates DataSources and publishes them to JNDI.
     *
     * @throws javax.naming.NamingException
     * @throws java.sql.SQLException
     */
    private StandaloneConnectionPools() {}

    /**
     * Don't call this.  Seems like the datasource does not close in a way that allows a new one to be created/setup.
     * I left it here as a reference for what tearDown would look like.
     * @throws IOException
     */
    public static void tearDownConnectionPool() throws IOException {
        try {
            ds.close();
            envCtx.unbind(ds_name);
            ds = null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
