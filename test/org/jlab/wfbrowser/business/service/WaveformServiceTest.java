/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jlab.wfbrowser.business.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.naming.NamingException;
import org.jlab.wfbrowser.business.filter.WaveformFilter;
import org.jlab.wfbrowser.connectionpools.StandaloneConnectionPools;
import org.jlab.wfbrowser.connectionpools.StandaloneJndi;
import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Waveform;
import org.junit.AfterClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 *
 * @author adamc
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WaveformServiceTest {

    private static StandaloneConnectionPools pools;
    private static long eventId, eventIdCompressed;
    private static Instant now, end;
    private static Event e, eCompressed;
    private static List<Event> eventList;
    private static List<Long> eventIds;

    public WaveformServiceTest() {
    }

    @BeforeClass
    public static void oneTimeSetUp() throws NamingException, SQLException {
        new StandaloneJndi();
        pools = new StandaloneConnectionPools();

        // Construct an example event for use in tests.  These should reflect the values in the "waveforms/data/rf/test" directory
        Instant t1 = LocalDateTime.of(2018, 9, 14, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);
        Instant t2 = LocalDateTime.of(2017, 9, 14, 10, 0, 0).atZone(ZoneId.systemDefault()).toInstant().plusMillis(100);
        List<Double> to1 = Arrays.asList(-102.4, -102.35, -102.3, -102.25);
        List<Double> to2 = Arrays.asList(-2102.4, -2102.35, -2102.3, -2102.25);
        List<Double> to3 = Arrays.asList(-3102.4, -3102.35, -3102.3, -3102.25);
        List<Double> v1 = Arrays.asList(9.0, -4.0, 0.0, -18.1934);
        List<Double> v2 = Arrays.asList(10.0, 5.0, 0.0, 11.5247);
        List<Double> v3 = Arrays.asList(12.0, -1.0, 0.0, -18.6768);
        List<Double> v4 = Arrays.asList(10.0, -2.0, 0.0, -32.4426);

        List<Double> v21 = Arrays.asList(29.0, -24.0, 20.0, -218.1934);
        List<Double> v22 = Arrays.asList(210.0, 25.0, 20.0, 211.5247);
        List<Double> v23 = Arrays.asList(212.0, -21.0, 20.0, -218.6768);
        List<Double> v24 = Arrays.asList(210.0, -22.0, 20.0, -232.4426);

        List<Double> v31 = Arrays.asList(39.0, -34.0, 30.0, -318.1934);
        List<Double> v32 = Arrays.asList(310.0, 35.0, 30.0, 311.5247);
        List<Double> v33 = Arrays.asList(312.0, -31.0, 30.0, -318.6768);
        List<Double> v34 = Arrays.asList(310.0, -32.0, 30.0, -332.4426);

        List<Waveform> waveforms = new ArrayList<>();
        waveforms.add(new Waveform("W1", to1, v1));
        waveforms.add(new Waveform("W2", to1, v2));
        waveforms.add(new Waveform("W3", to1, v3));
        waveforms.add(new Waveform("W4", to1, v4));
        waveforms.add(new Waveform("W21", to2, v21));
        waveforms.add(new Waveform("W22", to2, v22));
        waveforms.add(new Waveform("W23", to2, v23));
        waveforms.add(new Waveform("W24", to2, v24));
        waveforms.add(new Waveform("W31", to3, v31));
        waveforms.add(new Waveform("W32", to3, v32));
        waveforms.add(new Waveform("W33", to3, v33));
        waveforms.add(new Waveform("W34", to3, v34));

        e = new Event(t1, "test", "rf", false, waveforms);
        eCompressed = new Event(t2, "test", "rf", false, waveforms);
        
        now = Instant.now();
        end = now.plusMillis(5000);
        eventList = new ArrayList<>();
        eventIds = new ArrayList<>();
        eventList.add(new Event(now.plusMillis(1000), "test", "rf", false, null));
        eventList.add(new Event(now.plusMillis(2000), "test", "rf", false, null));
        eventList.add(new Event(now.plusMillis(3000), "test", "rf", false, null));
    }

    @AfterClass
    public static void oneTimeTearDown() throws IOException {
        if (pools.isOpen()) {
            pools.close();
        }
    }

    /**
     * Test of addEvent method, of class WaveformService.
     *
     * @throws java.lang.Exception
     */
    @Test
    public void test1AddEvent() throws Exception {
        System.out.println("addEvent");
        WaveformService instance = new WaveformService();

        // Set the test class parameter for use in other tests
        eventId = instance.addEvent(e, false);
        e.setEventId(eventId);
        List<Long> eventIdList = new ArrayList<>();
        eventIdList.add(eventId);
        WaveformFilter filter = new WaveformFilter(eventIdList, null, null, null, null, null, null);

        List<Event> expResult = new ArrayList<>();
        expResult.add(e);
        List<Event> result = instance.getEventList(filter);

        assertEquals(expResult, result);

        // Add an event that doesn't exist in the data.  This should fail.
        boolean threwException = false;
        try {
            instance.addEvent(new Event(Instant.now(), "test", "rf", false, null), false);
        } catch (FileNotFoundException ex) {
            threwException = true;
        }
        assertEquals(true, threwException);
    }

    /**
     * Test of addEvent method, of class WaveformService. Testing with
     * compressed event
     *
     * @throws java.lang.Exception
     */
    //@Test
    public void test1AddEventCompressed() throws Exception {
        System.out.println("addEvent");
        WaveformService instance = new WaveformService();

        // Set the test class parameter for use in other tests
        eventIdCompressed = instance.addEvent(eCompressed, false);
        eCompressed.setEventId(eventIdCompressed);
        List<Long> eventIdList = new ArrayList<>();
        eventIdList.add(eventIdCompressed);
        WaveformFilter filter = new WaveformFilter(eventIdList, null, null, null, null, null, null);

        List<Event> expResult = new ArrayList<>();
        expResult.add(eCompressed);
        List<Event> result;
        result = instance.getEventList(filter);
        assertEquals(expResult, result);

    }

    /**
     * Test of getEventList method, of class WaveformService.
     */
    @Test
    public void test2GetEventList() throws Exception {
        System.out.println("getEvent");

        List<Event> expResult = new ArrayList<>();
        expResult.add(e);
        eventIds.add(eventId);

        WaveformService instance = new WaveformService();
        WaveformFilter filter = new WaveformFilter(eventIds, null, null, null, null, null, null);
        List<Event> result = instance.getEventList(filter);
        assertEquals(expResult, result);
    }

    /**
     * Test of deleteEvent method, of class WaveformService.
     */
    @Test
    public void test4SetEventDeleteFlag() throws Exception {
        System.out.println("deleteEvent");
        WaveformService instance = new WaveformService();
        int expResult = 1;
        int result = instance.setEventDeleteFlag(eventId);
        assertEquals(expResult, result);
    }

    /**
     * Test of setEventArchiveFlag method, of class WaveformService.
     */
    @Test
    public void test3SetEventArchiveFlag() throws Exception {
        System.out.println("setEventArchiveFlag");
        WaveformService instance = new WaveformService();
        int expResult = 1;
        int result = instance.setEventArchiveFlag(eventId, true);
        WaveformFilter filter = new WaveformFilter(Arrays.asList(eventId), null, null, null, null, null, null);
        List<Event> temp = instance.getEventList(filter);
        assertEquals(true, temp.get(0).isArchive());
        assertEquals(expResult, result);

        instance.setEventArchiveFlag(eventId, false);
        temp = instance.getEventList(filter);
        assertEquals(false, temp.get(0).isArchive());

    }

    /**
     * Test of the addEventList, getEventList, and deleteEventList method of
     * class WaveformService.
     */
    @Test
    public void test5AddGetDeleteEventList() throws Exception {
        System.out.println("addGetDeleteEventList");
        WaveformService instance = new WaveformService();
        WaveformFilter filter = new WaveformFilter(null, now, end, "rf", "test", null, null);

        System.out.println("  addEventList");
        instance.addEventList(eventList, true);

        List<Event> result = instance.getEventListWithoutData(filter);
        // Since these numbers will be different with every test its difficult to tell if we're getting the correct value.
        // Set them to null so that the match the expected results / the list we added
        for (Event event : result) {
            eventIds.add(event.getEventId());
            event.setEventId(null);
        }
        List<Event> expResult = eventList;
        assertEquals(expResult, result);

        System.out.println("  deleteEventList");
        instance.setEventDeleteFlag(eventIds);
        // Filter on to_be_deleted flag is set
        result = instance.getEventListWithoutData(new WaveformFilter(null, now, end, "rf", "test", null, true));
        // Since these numbers will be different with every test its difficult to tell if we're getting the correct value.
        // Set them to null so that the match the expected results / the list we added
        for (Event event : result) {
            eventIds.add(event.getEventId());
            event.setEventId(null);
        }
        assertEquals(expResult, result);
    }
    
    @Test
    public void test6DeleteEvents() throws Exception {
        System.out.println("Deleting Test Events");
        WaveformService instance = new WaveformService();
        instance.deleteEvent(eventId, true);
        for(long id : eventIds) {
            instance.deleteEvent(id, true);
        }
    }
}
