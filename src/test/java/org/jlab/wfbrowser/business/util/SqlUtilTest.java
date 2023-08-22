package org.jlab.wfbrowser.business.util;

import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.junit.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SqlUtilTest {


    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        System.out.println("Doing one time setup");
        StandaloneConnectionPools.setupConnectionPool();
    }


    @Test
    public void testGetConnection1() throws SQLException {

        Connection conn = null;
        int numParallel = 1;
        List<Connection> conns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < numParallel; j++) {
                try {
                    conn = null;
                    conn = SqlUtil.getConnection();
                    Assert.assertNotNull(conn);
                    conns.add(conn);
                } catch (Exception ex) {
                    System.out.println("Failed on iteration " + i);
                    if (conn != null) {
                        System.out.println(conn + " ");
                        System.out.println(conn.getClientInfo() + " ");
                        System.out.println(conn.getSchema() + " ");
                        System.out.println();
                    }
                    throw ex;
                }
            }
            for (int j = 0; j < numParallel; j++) {
                conn = conns.get(j);
                SqlUtil.close(conn);
            }
            conns.clear();
        }
    }

    @Test
    public void testGetConnection2() throws SQLException, InterruptedException {
        Connection conn = null;
        int numParallel = 1;
        List<Connection> conns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < numParallel; j++) {
                try {
                    conn = null;
                    conn = SqlUtil.getConnection();
                    Assert.assertNotNull(conn);
                    conns.add(conn);
                } catch (Exception ex) {
                    System.out.println("Failed on iteration " + i);
                    if (conn != null) {
                        System.out.println(conn + " ");
                        System.out.println(conn.getClientInfo() + " ");
                        System.out.println(conn.getSchema() + " ");
                        System.out.println();
                    }
                    throw ex;
                }
            }
            for (int j = 0; j < numParallel; j++) {
                conn = conns.get(j);
                SqlUtil.close(conn);
            }
            conns.clear();
        }
    }

    @Test
    public void testGetConnection3() throws SQLException {

        Connection conn = null;
        int numParallel = 1;
        List<Connection> conns = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < numParallel; j++) {
                try {
                    conn = null;
                    conn = SqlUtil.getConnection();
                    Assert.assertNotNull(conn);
                    conns.add(conn);
                } catch (Exception ex) {
                    System.out.println("Failed on iteration " + i);
                    if (conn != null) {
                        System.out.println(conn + " ");
                        System.out.println(conn.getClientInfo() + " ");
                        System.out.println(conn.getSchema() + " ");
                        System.out.println();
                    }
                    throw ex;
                }
            }
            for (int j = 0; j < numParallel; j++) {
                conn = conns.get(j);
                SqlUtil.close(conn);
            }
            conns.clear();
        }
    }

}
