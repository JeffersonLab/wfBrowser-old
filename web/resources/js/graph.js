var jlab = jlab || {};
jlab.wfb = jlab.wfb || {};

var $startPicker = $("#start-date-picker");
var $endPicker = $("#end-date-picker");
var $seriesSelector = $("#series-selector");
var $seriesSetSelector = $("#series-set-selector");
var $zoneSelector = $("#zone-selector");
var $graphPanel = $("#graph-panel");
var timeline;
var firstUpdate = true;

// These get set by updateZoneSelector, and used by updateEventSelector
var begin, end;

jlab.wfb.locationToGroupMap = new Map([
    ["0L04", 0],
    ["1L22", 1],
    ["1L23", 2],
    ["1L24", 3],
    ["1L25", 4],
    ["1L26", 5],
    ["2L22", 6],
    ["2L23", 7],
    ["2L24", 8],
    ["2L25", 9],
    ["2L26", 10]
]);

jlab.wfb.convertUTCDateStringToLocalDate = function (dateString) {
    var date = new Date(dateString);
    return new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(),
            date.getMinutes(), date.getSeconds(), date.getMilliseconds()));
};

// This takes an event JSON object (from ajax/event ajax query) and converts it to a form that is expected by visjs DataSet
jlab.wfb.eventToItem = function (event) {
    var date = jlab.wfb.convertUTCDateStringToLocalDate(event.datetime_utc);
    var item = {
        id: event.id,
        content: "",
        start: jlab.dateToDateTimeString(date),
        group: jlab.wfb.locationToGroupMap.get(event.location),
        location: event.location,
        date: new Date(date)
    };
    return item;
};




// Setup the groups for the timeline
var groupArray = new Array(jlab.wfb.locationSelections.length);
for (var i = 0; i < jlab.wfb.locationSelections.length; i++) {
    groupArray[i] = {id: jlab.wfb.locationToGroupMap.get(jlab.wfb.locationSelections[i]), content: jlab.wfb.locationSelections[i]};
}
var groups = new vis.DataSet(groupArray);

// Setup the items for the timeline
var itemArray = new Array(jlab.wfb.eventArray.length);
for (var i = 0; i < jlab.wfb.eventArray.length; i++) {
    itemArray[i] = jlab.wfb.eventToItem(jlab.wfb.eventArray[i]);
}
var items = new vis.DataSet(itemArray);





// Get the previous item from the same group
jlab.wfb.getPrevItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return(item.group == curr.group);
        }
    });

    var prev = null;
    for (var i = 0; i < subset.length; i++) {
        if (prev === null) {
            if (curr.date > subset[i].date) {
                prev = subset[i];
            }
        } else {
            if (prev.date < subset[i].date && curr.date > subset[i].date) {
                prev = subset[i];
            }
        }
    }
    return prev;
};

jlab.wfb.getNextItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return(item.group == curr.group);
        }
    });

    var next = null;
    for (var i = 0; i < subset.length; i++) {
        if (next === null) {
            if (curr.date < subset[i].date) {
                next = subset[i];
            }
        } else {
            if (next.date > subset[i].date && curr.date < subset[i].date) {
                next = subset[i];
            }
        }
    }
    return next;
};

jlab.wfb.getFirstItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return(item.group == curr.group);
        }
    });

    var first = null;
    for (var i = 0; i < subset.length; i++) {
        if (first === null) {
            first = subset[i];
        } else {
            if (first.date > subset[i].date) {
                first = subset[i];
            }
        }
    }
    return first;
};

jlab.wfb.getLastItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return(item.group == curr.group);
        }
    });

    var last = null;
    for (var i = 0; i < subset.length; i++) {
        if (last === null) {
            last = subset[i];
        } else {
            if (last.date < subset[i].date) {
                last = subset[i];
            }
        }
    }
    return last;
};



/* 
 * eventId - The waveform eventId to query information for
 * chartId - typically a number, is appended to "graph-chart-" to create the id of a div that acts as the dygraph container
 * $graphPanel - jQuery object that is the parent object of all dygraph containers
 * graphOptions - set of dygraph graph options
 * series - the waveform event series name the display on this graph
 * returns the dygraph object
 */
jlab.wfb.makeGraph = function (event, chartId, $graphPanel, graphOptions, series) {
    if (typeof series === "undefied" || series === null) {
        window.console && console.log("Required argument series not supplied to jlab.wfb.makeGraph");
        return;
    }
    if (typeof event === "undefined" || event === null) {
        window.console && console.log("event is undefined or null");
        return;
    }

    // Get the data, labels, etc. needed for this chart out of the currentEvent object
    var labels = [];
    var ylabel = "";
    var dygraphIds = [0];
    var data = [];
    data.push(event.timeOffsets);
    for (var i = 0; i < event.waveforms.length; i++) {
        for (var j = 0; j < event.waveforms[i].series.length; j++) {
            if (event.waveforms[i].series[j].name === series) {
                data.push(event.waveforms[i].dataPoints);
                labels.push(event.waveforms[i].dygraphLabel);
                dygraphIds.push(event.waveforms[i].dygraphId);
                ylabel = event.waveforms[i].series[j].units;
            }
        }
    }
    labels = ["time"].concat(labels.sort());

    // We have to transpose the data array here since dygraphs wants it as though it was the rows a of a CSV file.
    var tempData = new Array(data[0].length);
    for (var i = 0; i < tempData.length; i++) {
        tempData[i] = new Array(data.length);
    }

    for (var i = 0; i < data.length; i++) {
        for (var j = 0; j < data[i].length; j++) {
            tempData[j][i] = data[i][j];
        }
    }
    data = tempData;

    graphOptions.title = series;
    graphOptions.labels = labels;
    graphOptions.ylabel = ylabel;
    $graphPanel.append("<div class=graph-container><div id=graph-chart-" + chartId + " class='graph-chart'></div>"
            + "<div class='graph-legend' id=graph-legend-" + chartId + " ></div></div>");
    graphOptions.labelsDiv = document.getElementById("graph-legend-" + chartId);
    var g = new Dygraph(
            // containing div
            document.getElementById("graph-chart-" + chartId),
            data,
            graphOptions
            );

    // This event handler allows the users to highlight/unhighlight a single series
    var onclick = function (ev) {
        if (g.isSeriesLocked()) {
            g.clearSelection();
        } else {
            g.setSelection(g.getSelection(), g.getHighlightSeries(), true);
        }
    };

    g.updateOptions({clickCallback: onclick}, true);
    g.setSelection(false, g.getHighlightSeries());
    return g;
};

jlab.wfb.updateBrowserUrlAndControls = function () {
    $startPicker.val(jlab.wfb.begin);
    $endPicker.val(jlab.wfb.end);

    // Update the URL so someone could navigate back to or bookmark or copy paste the URL 
    var url = jlab.contextPath + "/graph"
            + "?begin=" + jlab.wfb.begin.replace(/ /, '+').encodeXml()
            + "&end=" + jlab.wfb.end.replace(/ /, '+').encodeXml()
            + "&eventId=" + jlab.wfb.eventId;
    for (var i = 0; i < jlab.wfb.seriesSelections.length; i++) {
        url += "&series=" + jlab.wfb.seriesSelections[i];
    }
    for (var i = 0; i < jlab.wfb.locationSelections.length; i++) {
        url += "&location=" + jlab.wfb.locationSelections[i];
    }
    window.history.replaceState(null, null, url);
};

// Make a new graph.  Looks up the jlab.wfb.eventId global variable amongst others
jlab.wfb.loadNewGraphs = function () {
    jlab.wfb.updateBrowserUrlAndControls();

    var promise = jlab.doAjaxJsonGetRequest(jlab.contextPath + "/ajax/event", {id: jlab.wfb.eventId, out: "dygraph", includeData: true});
    $graphPanel.css({opacity: 0.5});
    promise.done(function (json) {
        jlab.wfb.currentEvent = json.events[0];
        $graphPanel.empty();
        $graphPanel.css({opacity: 1});
        jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);
    });
};

/*
 * Make all of the request waveform graphs.  One chart per series.
 * @param long eventId - The ID of the waveform event to graph
 * @param jQuery selector object $graphPanel The div in which to create waveform graphs
 * @param String[] series
 * @returns {undefined}
 */
jlab.wfb.makeGraphs = function (event, $graphPanel, series) {
    if (typeof event === "undefined" || event === null) {
        window.console && console.log("Received undefined or null waveform event");
        $graphPanel.prepend("<div class='graph-panel-title'>No event displayed</div>");
        return;
    }

    var date = jlab.wfb.convertUTCDateStringToLocalDate(event.datetime_utc);
    var headerHtml = "<div class='graph-panel-header'>" +
            "<div class='graph-panel-title-wrapper'><div class='graph-panel-title'></div><div class='graph-panel-controls'></div></div>" +
            "<div class='graph-panel-date-wrapper'><span class='graph-panel-prev-controls'></span><span class='graph-panel-date'></span><span class='graph-panel-next-controls'></div></div>" +
            "</span>";
    $graphPanel.prepend(headerHtml);
    $("#graph-panel .graph-panel-title").prepend(event.location);
    $("#graph-panel .graph-panel-date").prepend(jlab.dateToDateTimeString(date));
    console.log("" + event);
    var firstItem = jlab.wfb.getFirstItem(items, event.id);
    var prevItem = jlab.wfb.getPrevItem(items, event.id);
    var nextItem = jlab.wfb.getNextItem(items, event.id);
    var lastItem = jlab.wfb.getLastItem(items, event.id);

    if (firstItem !== null && firstItem.id !== event.id) {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='first-button' data-event-id='" + firstItem.id + "'>First</button>");
        $("#first-button").on("click", function () {
            jlab.wfb.eventId = $(this).data("event-id");
            timeline.setSelection(jlab.wfb.eventId);
            jlab.wfb.loadNewGraphs();
        });
    } else {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='first-button' disabled>First</button>");
    }
    if (prevItem !== null) {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='prev-button' data-event-id='" + prevItem.id + "'>Prev</button>");
        $("#prev-button").on("click", function () {
            jlab.wfb.eventId = $(this).data("event-id");
            timeline.setSelection(jlab.wfb.eventId);
            jlab.wfb.loadNewGraphs();
        });
    } else {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='prev-button' disabled>Prev</button>");
    }
    if (nextItem !== null) {
        $("#graph-panel .graph-panel-next-controls").append("<button id='next-button' data-event-id='" + nextItem.id + "'>Next</button>");
        $("#next-button").on("click", function () {
            jlab.wfb.eventId = $(this).data("event-id");
            timeline.setSelection(jlab.wfb.eventId);
            jlab.wfb.loadNewGraphs();
        });
    } else {
        $("#graph-panel .graph-panel-next-controls").append("<button id='next-button' disabled>Next</button>");
    }

    if (lastItem !== null && lastItem.id !== event.id) {
        $("#graph-panel .graph-panel-next-controls").append("<button id='last-button' data-event-id='" + lastItem.id + "'>Last</button>");
        $("#last-button").on("click", function () {
            jlab.wfb.eventId = $(this).data("event-id");
            timeline.setSelection(jlab.wfb.eventId);
            jlab.wfb.loadNewGraphs();
        });
    } else {
        $("#graph-panel .graph-panel-next-controls").append("<button id='last-button' disabled>Last</button>");
    }

    var graphOptions = {
        legend: "always",
        labelsSeparateLines: true,
        highlightCircleSize: 2,
        strokeWidth: 1,
        highlightSeriesOpts: {
            strokeWidth: 2,
            strokeBorderWidth: 1,
            highlightCircleSize: 5
        }
    };

    var graphs = [];
    for (var i = 0; i < series.length; i++) {
        var g = jlab.wfb.makeGraph(event, i, $graphPanel, graphOptions, series[i]);
        graphs.push(g);
    }
    if (graphs.length > 1) {
        Dygraph.synchronize(graphs, {range: false});
    }
};

/*
 * Setup the timeline widget
 * begin  - starting datetime string of the timeline
 * end     - ending datetime string of the timeline
 * zones - array of zone names to be included in the timeline
 * events - array of events to be drawn
 */
//            jlab.wfb.makeTimeline = function (container, begin, end, zones, events, eventId) {
//jlab.wfb.makeTimeline = function (container, zones, events) {


//
//    var groupArray = new Array(zones.length);
//    for (var i = 0; i < zones.length; i++) {
//        groupArray[i] = {id: jlab.wfb.locationToGroupMap.get(zones[i]), content: zones[i]};
//    }
//    var groups = new vis.DataSet(groupArray);
//
//    var itemArray = new Array(events.length);
//    for (var i = 0; i < events.length; i++) {
//        itemArray[i] = jlab.wfb.eventToItem(events[i]);
//    }
//    var items = new vis.DataSet(itemArray);

//
jlab.wfb.makeTimeline = function (container, groups, items) {
    var options = {
        start: jlab.wfb.begin,
        end: jlab.wfb.end,
        stack: false,
        selectable: true,
        multiselect: false,
        min: jlab.wfb.begin,
        max: jlab.wfb.end
    };

    timeline = new vis.Timeline(container, items, groups, options);
    if (typeof jlab.wfb.eventId !== "undefined" && jlab.wfb.eventId !== null) {
        timeline.setSelection(jlab.wfb.eventId);
    }

    // Currently a bug in the timeline widget is keeping this from working as desired.  I may be able to work around it.
    // the bug seems to be that calling timeline.getItemRange().min inside of on("rangechagned", ...) cause the timeline
    // to display items incorrectly.
//                timeline.on("rangechanged", function (params) {
//                    var timeLineStart = params.start;
//                    var timeLineEnd = params.end;
//                    var byUser = params.byUser;
//                    var event = params.event;
//
//
//                    if (timeline.getItemRange() === null
//                            || timeLineStart.getTime() < timeline.getItemRange().min.getTime()
//                            || timeLineEnd.getTime() > timeline.getItemRange().max.getTime()) {
//
//                        var queryStart, queryEnd;
//                        if (timeline.getItemRange() === null) {
//                            queryStart = timeLineStart;
//                            queryEnd = timeLineEnd;
//                        } else if (timeLineStart.getTime() < timeline.getItemRange().min.getTime()) {
//                            queryStart = timeLineStart;
//                            queryEnd = timeline.getItemRange().min;
//                        } else if (timeLineEnd.getTime() > timeline.getItemRange().max.getTime()) {
//                            queryStart = timeline.getItemRange().max;
//                            queryEnd = timeLineEnd;
//                        }
//
//                        jlab.wfb.begin = jlab.dateToDateTimeString(timeLineStart);
//                        jlab.wfb.end = jlab.dateToDateTimeString(timeLineEnd);
//
//                        if (byUser) {
//                            jlab.wfb.updateBrowserUrlAndControls();
//
//                            var url = jlab.contextPath + "/ajax/event";
//                            var data = {
//                                begin: jlab.dateToDateTimeString(queryStart),
//                                end: jlab.dateToDateTimeString(queryEnd),
//                                location: jlab.wfb.locationSelections
//                            };
//                            var settings = {
//                                "url": url,
//                                type: "GET",
//                                traditional: true,
//                                "data": data,
//                                dataType: "json"
//                            };
//                            var promise = $.ajax(settings);
//
//                            // Basically copy and paste of the smoothness doAjaxJsonGetRequest error handler.
//                            // Done since I needed to be able to pass the "traditional" setting
//                            promise.error(function (xhr, textStatus) {
//                                var json;
//
//                                try {
//                                    json = $.parseJSON(xhr.responseText);
//                                } catch (err) {
//                                    window.console && console.log('Response is not JSON: ' + xhr.responseText);
//                                    json = {};
//                                }
//
//                                var message = json.error || 'Server did not handle request';
//                                alert('Unable to perform request: ' + message);
//                            });
//
//                            promise.done(function (json) {
//                                var eventArray = json.events;
//                                var newItems = new Array(eventArray.length);
//                                for (var i = 0; i < eventArray.length; i++) {
//                                    newItems[i] = jlab.wfb.eventToItem(eventArray[i]);
//                                }
//                                items.add(newItems);
//                            });
//                        }
//                    }
//                });

    timeline.on("select", function (params) {
        jlab.wfb.eventId = params.items[0];
        jlab.wfb.loadNewGraphs();
//        jlab.wfb.updateBrowserUrlAndControls();
//
//        var promise = jlab.doAjaxJsonGetRequest(jlab.contextPath + "/ajax/event", {id: jlab.wfb.eventId, out: "dygraph", includeData: true});
//        $graphPanel.css({opacity: 0.5});
//        promise.done(function (json) {
//            jlab.wfb.currentEvent = json.events[0];
//            $graphPanel.empty();
//            $graphPanel.css({opacity: 1});
//            jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesSelections);
//        });

    });
};


$(function () {
    $seriesSelector.select2();
    $seriesSetSelector.select2();
    $zoneSelector.select2();
    $startPicker.val(jlab.wfb.begin);
    $endPicker.val(jlab.wfb.end);
    $(".date-time-field").datetimepicker({
        controlType: jlab.dateTimePickerControl,
        dateFormat: 'yy-mm-dd',
        timeFormat: 'HH:mm:ss'
    });

    var timelineDiv = document.getElementById("timeline-container");
//    jlab.wfb.makeTimeline(timelineDiv, jlab.wfb.locationSelections, jlab.wfb.eventArray);
    jlab.wfb.makeTimeline(timelineDiv, groups, items);

    if (typeof jlab.wfb.eventId !== "undefined" && jlab.wfb.eventId !== null && jlab.wfb.eventId !== "") {
        jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);
    }
});