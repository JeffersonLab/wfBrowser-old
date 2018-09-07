/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.NamingException;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.connectionpools.StandaloneJndi;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Waveform;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author adamc
 */
public class WaveformServiceTest {

    StandaloneConnectionPools pools;

    public WaveformServiceTest() {
    }

    @Before
    public void setUp() throws NamingException, SQLException {
        new StandaloneJndi();
        pools = new StandaloneConnectionPools();
    }

    @After
    public void tearDown() throws IOException {
        if (pools.isOpen()) {
            pools.close();
        }
    }

    /**
     * Test of addEvent method, of class WaveformService.
     * @throws java.lang.Exception
     */
    @Test
    public void testAddEvent() throws Exception {
        System.out.println("addEvent");
        List<Double> time1 = Arrays.asList(1.1, 2.1, 3.1);
        List<Double> vals1 = Arrays.asList(1.5, 2.5, 0.5);
        List<Double> time2 = Arrays.asList(100.1, 200.5, 300.3);
        List<Double> vals2 = Arrays.asList(1.15, 32.5, 10.5);
        Waveform w1 = new Waveform("test1", time1, vals1);
        Waveform w2 = new Waveform("test2", time2, vals2);
        List<Waveform> waveforms = new ArrayList<>();
        waveforms.add(w1);
        waveforms.add(w2);

        Instant now = Instant.now();
        Event e = new Event(now, "Bldg87", "rf", false, waveforms);
        WaveformService instance = new WaveformService();
        long eventId = instance.addEvent(e);
        e.setEventId(eventId);
        
        Event expResult = new Event(eventId, now, "Bldg87", "rf", false, waveforms);
        Event result = instance.getEvent(eventId);
        
        System.out.println(expResult);
        System.out.println(result);
        
        assertEquals(result, expResult);
    }

}
