package org.jlab.wfbrowser.business.filter;

import org.jlab.wfbrowser.model.Event;
import org.jlab.wfbrowser.model.Label;
import org.junit.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.Assert.*;

public class LabelFilterTest {

    @Test
    public void testFilterEvents() throws Exception {

        //
        // Setup events and labels for testing
        //
        Instant t1 = Instant.now();
        Instant t2 = t1.plusMillis(-100);
        Instant t3 = t2.plusMillis(-100);
        Instant t4 = t3.plusMillis(-100);
        Instant t5 = t4.plusMillis(-100);

        List<Label> labelList1 = new ArrayList<>();
        labelList1.add(new Label(1L, t1, "testModel", "cavity", "1", 0.99));
        labelList1.add(new Label(1L, t1, "testModel", "fault-type", "E_Quench", 0.99));

        List<Label> labelList2 = new ArrayList<>();
        labelList2.add(new Label(2L, t2, "testModel", "cavity", "1", 0.75));
        labelList2.add(new Label(2L, t2, "testModel", "fault-type", "E_Quench", 0.75));

        List<Label> labelList3 = new ArrayList<>();
        labelList3.add(new Label(3L, t3, "testModel", "cavity", "1", 0.09));
        labelList3.add(new Label(3L, t3, "testModel", "fault-type", "Quench", 0.09));

        List<Label> labelList4 = new ArrayList<>();
        labelList4.add(new Label(4L, t4, "testModel", "cavity", "3", 0.99));
        labelList4.add(new Label(4L, t4, "testModel", "fault-type", "E_Quench", 0.09));


        List<Event> events = new ArrayList<>();
        events.add(new Event(1L, t1, "loc1", "testSystem", false, false,
                false, "", labelList1));
        events.add(new Event(2L, t2, "loc1", "testSystem", false, false,
                false, "", labelList2));
        events.add(new Event(3L, t3, "loc1", "testSystem", false, false,
                false, "", labelList3));
        events.add(new Event(4L, t4, "loc2", "testSystem", false, false,
                false, "", labelList4));
        events.add(new Event(5L, t5, "loc2", "testSystem", false, false,
                false, "", null));

        //
        // Start with the testing
        //
        LabelFilter lf;

        // Get all events with labels
        lf = new LabelFilter(true);
        assertEquals(events.subList(0, 4), lf.filterEvents(events));

        // Get all events WITHOUT labels
        lf = new LabelFilter(false);
        assertEquals(events.subList(4, 5), lf.filterEvents(events));

        // Get events with confidence > 0.5
        lf = new LabelFilter(null, null, null, 0.5, ">");
        assertEquals(events.subList(0, 2), lf.filterEvents(events));

        // Get events with confidence < 0.5
        lf = new LabelFilter(null, null, null, 0.5, "<");
        assertEquals(events.subList(2, 3), lf.filterEvents(events));

        // Get events with cavity labels that are "1"
        Map<String, List<String>> nvMap = new HashMap<>();
        nvMap.put("cavity", Collections.singletonList("1"));
        lf = new LabelFilter(null, null, nvMap, null, null);
        assertEquals(events.subList(0, 3), lf.filterEvents(events));

        // Get events with cavity label "1" AND fault-type label "Quench"
        nvMap = new HashMap<>();
        nvMap.put("cavity", Collections.singletonList("1"));
        nvMap.put("fault-type", Collections.singletonList("Quench"));
        lf = new LabelFilter(null, null, nvMap, null, null);
        assertEquals(events.subList(2, 3), lf.filterEvents(events));

    }
}