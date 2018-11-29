var jlab = jlab || {};
jlab.wfb = jlab.wfb || {};

var $startPicker = $("#start-date-picker");
var $endPicker = $("#end-date-picker");
var $seriesSelector = $("#series-selector");
var $seriesSetSelector = $("#series-set-selector");
var $locationSelector = $("#location-selector");
var $graphPanel = $("#graph-panel");
var timeline;
var firstUpdate = true;

// These get set by updateZoneSelector, and used by updateEventSelector
var begin, end;

/**
 * This map is used to convert named locations (zones) to a consistent set of IDs in the timeline widget.
 * @type Map
 */
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

/**
 * This map is used to apply a consistent set of colors based on a dygraph ID.  dygraph IDs are 1 indexed, so id-1 -> color
 * @type Array
 */
jlab.wfb.dygraphIdToColorArray = ["#7FC97F", "#BEAED4", "#FDC086", "#000000", "#386CB0", "#F0027F", "#BF5B17", "#666666"];

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
 * seriesArray - the array series that are to be drawn on this page (used for sizing purposes)
 * returns the dygraph object
 */
jlab.wfb.makeGraph = function (event, chartId, $graphPanel, graphOptions, series, seriesArray) {
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
    var units = "";
    var dygraphIds = [];
    var data = [];
    data.push(event.timeOffsets);
    for (var i = 0; i < event.waveforms.length; i++) {
        for (var j = 0; j < event.waveforms[i].series.length; j++) {
            if (event.waveforms[i].series[j].name === series) {
                data.push(event.waveforms[i].dataPoints);
                labels.push(event.waveforms[i].dygraphLabel);
                dygraphIds.push(event.waveforms[i].dygraphId);
                units = event.waveforms[i].series[j].units;
            }
        }
    }
    labels = ["time"].concat(labels.sort());

    // Set up colors so that they are unique to the dygraphId (which maps to cavity number)
    var colors = [];
    for (var i = 0; i < dygraphIds.length; i++) {
        colors.push(jlab.wfb.dygraphIdToColorArray[dygraphIds[i] - 1]);
    }

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

    var containerClass = "'graph-container";
    if (seriesArray.length >= 6) {
        containerClass += " graph-container-thirdwidth";
    } else if (seriesArray.length >= 4) {
        containerClass += " graph-container-halfwidth";
    }
    containerClass += "'";

    graphOptions.colors = colors;
    graphOptions.title = series + " (" + units + ")";
    graphOptions.labels = labels;
    $graphPanel.append("<div class=" + containerClass + "><div id=graph-chart-" + chartId + " class='graph-chart'></div>"
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
    for (var i = 0; i < jlab.wfb.seriesSetSelections.length; i++) {
        url += "&seriesSet=" + jlab.wfb.seriesSetSelections[i];
    }
    for (var i = 0; i < jlab.wfb.locationSelections.length; i++) {
        url += "&location=" + jlab.wfb.locationSelections[i];
    }
    window.history.replaceState(null, null, url);
};

// Make a new graph.  Looks up the jlab.wfb.eventId global variable amongst others
jlab.wfb.loadNewGraphs = (function () {
    var currEventId = null;
    var graphs = null;
    var updateInProgress = false;
    return function (eventId) {

        // When the page first loads, we have access to the already generate event data and don't need to do an AJAX call to get it
        if (typeof eventId === "object") {
            // it's an event object so the syntax looks weird
            currEventId = eventId.id;

            // Make and display the graphs. Save them to the local array so we can delete them on the next update
            graphs = jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);

            // Make sure the URL bar and UI controls reflect any changes.
            jlab.wfb.updateBrowserUrlAndControls();

            return;
        }

        if (typeof eventId === "undefined" || eventId === null) {
            window.console && console.log("Error: eventId undefined or null");
            timeline.setSelection(currEventId);
            return;
        }

        if (updateInProgress) {
            timeline.setSelection(currEventId);
            window.console && console.log("Update already in progress");
            return;
        } else {
            updateInProgress = true;
            // Make the graphs a little transparent while we're downloading the data
            $graphPanel.css({opacity: 0.5});

            // Update the global current event and local tracker of the currentEvent
            currEventId = eventId;
            jlab.wfb.currentEventId = eventId;

            // Make sure the timeline matches the current event
            timeline.setSelection(currEventId);


            var promise = jlab.doAjaxJsonGetRequest(jlab.contextPath + "/ajax/event", {id: eventId, out: "dygraph", includeData: true, requester: "graph"});
            promise.done(function (json) {

                // Sanity check - make sure the id we get back is what we asked for.
                if (json.events[0].id !== currEventId) {
                    currEventId = json.events[0].id;
                    jlab.wfb.currentEventId = json.events[0].id;
                    alert("Warning: Received different event than requested");
                }
                jlab.wfb.currentEvent = json.events[0];


                // Clear out the graph panel, set the graph panel back to opaque, and delete any existing graph data
                $graphPanel.empty();
                $graphPanel.css({opacity: 1});
                // Clear out all of the old graph data
                if (graphs !== null) {
                    for (var i = 0; i < graphs.length; i++) {
                        if (graphs[i] !== null) {
                            graphs[i].destroy();
                        }
                    }
                }

                // Make and display the graphs. Save them to the local array so we can delete them on the next update
                graphs = jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);

                // Make sure the URL bar and UI controls reflect any changes.
                jlab.wfb.updateBrowserUrlAndControls();
            });
            promise.fail(function () {
                // jlab.doAjaxJsonGetRequest handles the generic error logic.  We just need to make sure that the timeline is accurate.
                timeline.setSelection(currEventId);
            });
            promise.always(function () {
                updateInProgress = false;
            });
        }
    };
})();

/*
 * Make all of the request waveform graphs.  One chart per series.
 * @param event - An object representing the event to be displayed
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
            "<div class='graph-panel-title-wrapper'><div class='graph-panel-title'></div></div>" +
            "<div class='graph-panel-date-wrapper'><span class='graph-panel-visibility-controls'><fieldset><legend>Visibility</legend></fieldset></span><span class='graph-panel-prev-controls'></span>" +
            "<span class='graph-panel-date'></span>" +
            "<span class='graph-panel-next-controls'></span><span class='graph-panel-action-controls'><button class='download'>Download</button></span></div>";
    $graphPanel.prepend(headerHtml);

    $("#graph-panel .graph-panel-title").prepend(event.location);
    $("#graph-panel .graph-panel-date").prepend(jlab.dateToDateTimeString(date));

    // Figure out which cavities are present in these charts
    var dygraphIdSet = new Set();
    for (var i = 0; i < event.waveforms.length; i++) {
        dygraphIdSet.add(event.waveforms[i].dygraphId);
    }
    
    // Construct checkboxes that will control the visibility of inidividual cavity series.  Once we've created the graphs, we can bind a click event handler
    var checkBoxNum = 0;
    dygraphIdSet.forEach(function (value) {
        if (checkBoxNum === 4) {
            $("#graph-panel .graph-panel-visibility-controls fieldset").append("<br>");
        }
        var forName = "cav-toggle-" + checkBoxNum;
        var color = jlab.wfb.dygraphIdToColorArray[value - 1];
        $("#graph-panel .graph-panel-visibility-controls fieldset").append('<label style="font-weight: bold; color: ' + color + ';" for="' + forName + '">C' + value + '</label><input type="checkbox" id="cav-toggle-' + checkBoxNum + '" class="cavity-toggle" data-series-id="' + checkBoxNum + '" checked="checked">');
        checkBoxNum++;
    });

    // Setup the download button
    $("#graph-panel .graph-panel-action-controls .download").on("click", function () {
        window.location = jlab.contextPath + "/ajax/event?id=" + event.id + "&out=csv&includeData=true";
    });

    // Setup the archive button.  Admins see an "unarchive" button if the event is archvied.  Everyone else sees a disabled archvie button.
    if (event.archive) {
        if (jlab.isUserAdmin) {
            $("#graph-panel .graph-panel-action-controls").prepend("<button class=archive>Unarchive</button>");
            $("#graph-panel .graph-panel-action-controls .archive").on("click", function () {
                var url = jlab.contextPath + "/ajax/event-archive";
                var data = {id: event.id, archive: false};
                // Send the unarchive request and reload the page
                jlab.doAjaxJsonPostRequest(url, data, null, true);
            });
        } else {
            $("#graph-panel .graph-panel-action-controls").prepend("<button class=archive disabled>Archive</button>");
        }
    } else {
        $("#graph-panel .graph-panel-action-controls").prepend("<button class=archive>Archive</button>");
        $("#graph-panel .graph-panel-action-controls .archive").on("click", function () {
            var url = jlab.contextPath + "/ajax/event-archive";
            var data = {id: event.id, archive: true};
            // Send the unarchive request and reload the page
            jlab.doAjaxJsonPostRequest(url, data, null, true);
        });
    }

    // Add a help button with information on the controls
    var helpHtml = "<div class='help-dialog'>CHART CONTROLS<hr>Zoom: click-drag<br>Pan: shift-click-drag<br>Restore: double-click</div>";
    $("#graph-panel .graph-panel-action-controls").prepend("<span class='relative-span' style='position: relative; height: 100%;'></span><button class='help'>Help</button>");
    $("#graph-panel .graph-panel-action-controls .help").on("click", (function () {
        var isShown = false;
        return function () {
            if (!isShown) {
                $("#graph-panel .graph-panel-action-controls .relative-span").prepend(helpHtml);
                isShown = true;
            } else {
                $("#graph-panel .graph-panel-action-controls  .relative-span .help-dialog").remove();
                isShown = false;
            }
        };
    })());



    // Setup the navigation controls
    var firstItem = jlab.wfb.getFirstItem(items, event.id);
    var prevItem = jlab.wfb.getPrevItem(items, event.id);
    var nextItem = jlab.wfb.getNextItem(items, event.id);
    var lastItem = jlab.wfb.getLastItem(items, event.id);

    if (firstItem !== null && firstItem.id !== event.id) {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='first-button' data-event-id='" + firstItem.id + "'>First</button>");
        $("#first-button").on("click", function () {
            jlab.wfb.loadNewGraphs($(this).data("event-id"));
        });
    } else {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='first-button' disabled>First</button>");
    }
    if (prevItem !== null) {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='prev-button' data-event-id='" + prevItem.id + "'>Prev</button>");
        $("#prev-button").on("click", function () {
            jlab.wfb.loadNewGraphs($(this).data("event-id"));
        });
    } else {
        $("#graph-panel .graph-panel-prev-controls").append("<button id='prev-button' disabled>Prev</button>");
    }
    if (nextItem !== null) {
        $("#graph-panel .graph-panel-next-controls").append("<button id='next-button' data-event-id='" + nextItem.id + "'>Next</button>");
        $("#next-button").on("click", function () {
            jlab.wfb.loadNewGraphs($(this).data("event-id"));
        });
    } else {
        $("#graph-panel .graph-panel-next-controls").append("<button id='next-button' disabled>Next</button>");
    }

    if (lastItem !== null && lastItem.id !== event.id) {
        $("#graph-panel .graph-panel-next-controls").append("<button id='last-button' data-event-id='" + lastItem.id + "'>Last</button>");
        $("#last-button").on("click", function () {
            jlab.wfb.loadNewGraphs($(this).data("event-id"));
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
        var g = jlab.wfb.makeGraph(event, i, $graphPanel, graphOptions, series[i], series);
        graphs.push(g);
    }
    if (graphs.length > 1) {
        Dygraph.synchronize(graphs, {range: false});
    }
    $(".cavity-toggle").on("click", function () {
        var seriesId = $(this).data("series-id");
        for (var i = 0; i < graphs.length; i++) {
            if (graphs[i].visibility()[seriesId]) {
                graphs[i].setVisibility(seriesId, false);
            } else {
                graphs[i].setVisibility(seriesId, true);
            }
        }
    });

    return graphs;
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
        type: "point",
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
        jlab.wfb.loadNewGraphs(params.items[0]);
    });
};

jlab.wfb.validateForm = function () {
    var $err = $("#page-controls-error");
    $err.empty();

    // Make sure that we will have some sort of series to display in the graphs
    if ($seriesSelector.val() === null && $seriesSetSelector.val() === null) {
        $err.append("At least one series or series set must be supplied.");
        return false;
    }

    // Make sure that the timeline will have some sort of location to show and that we will have a group of events to pick from
    if ($locationSelector.val() === null) {
        $err.append("At least one zone must be supplied.");
        return false;
    }

    // Make sure we got start/end times
    if ($startPicker.val() === null || $startPicker.val() === "") {
        $err.append("Start time required.");
        return false;
    }
    if ($endPicker.val() === null || $endPicker.val() === "") {
        $err.append("End time required.");
        return false;
    }


    // Check that the date range isn't too large.  The timeline widget uses DOM elements and too many of them can slow down the browser.
    var start = new Date($startPicker.val());
    var end = new Date($endPicker.val());
    var day = 1000 * 60 * 60 * 24; // millis to days
    if (((end.getTime() - start.getTime()) / day) > 14) {
        $err.append("Date range cannot exceed two weeks.");
        return false;
    }

    // Everything passed the checks.  Return true;
    return true;
};


$(function () {
    var select2Options = {
        width: "15em"
    };
    $seriesSelector.select2(select2Options);
    $seriesSetSelector.select2(select2Options);
    $locationSelector.select2(select2Options);
    $startPicker.val(jlab.wfb.begin);
    $endPicker.val(jlab.wfb.end);
    $(".date-time-field").datetimepicker({
        controlType: jlab.dateTimePickerControl,
        dateFormat: 'yy-mm-dd',
        timeFormat: 'HH:mm:ss'
    });

    $("#page-controls-submit").on("click", jlab.wfb.validateForm);

    var timelineDiv = document.getElementById("timeline-container");
//    jlab.wfb.makeTimeline(timelineDiv, jlab.wfb.locationSelections, jlab.wfb.eventArray);
    jlab.wfb.makeTimeline(timelineDiv, groups, items);

    if (typeof jlab.wfb.eventId !== "undefined" && jlab.wfb.eventId !== null && jlab.wfb.eventId !== "") {
        jlab.wfb.loadNewGraphs(jlab.wfb.currentEvent);
//        jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);
    }
});