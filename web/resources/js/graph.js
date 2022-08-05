var jlab = jlab || {};
var vis = vis || (window.console && console.log("vis object from vis.js not loaded"));
var Dygraph = Dygraph || (window.console && console.log("Dypgraph object from dypgraphs.js not loaded"));

jlab.wfb = jlab.wfb || {};

jlab.wfb.$startPicker = $("#start-date-picker");
jlab.wfb.$endPicker = $("#end-date-picker");
jlab.wfb.$seriesSelector = $("#series-selector");
jlab.wfb.$seriesSetSelector = $("#series-set-selector");
jlab.wfb.$locationSelector = $("#location-selector");
jlab.wfb.$classificationSelector = $("#classification-selector");

// gets overwritten onload - here for informational purposes
jlab.wfb.timeline = null;


/**
 * This map is used to apply a consistent set of colors based on a dygraph ID.  dygraph IDs are 1 indexed, so id-1 -> color
 * @type Array
 */
jlab.wfb.dygraphIdToColorArray = ['#e41a1c', '#377eb8', '#4daf4a', '#984ea3', '#ff7f00', '#444444', '#a65628', '#e077ae'];


/**
 * This converts a UTC date string to a Date object in localtime
 * @param {type} dateString
 * @returns {Date}
 */
jlab.wfb.convertUTCDateStringToLocalDate = function (dateString) {
    var date = new Date(dateString);
    return new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(),
        date.getMinutes(), date.getSeconds(), date.getMilliseconds()));
};

/**
 * This takes an event JSON object (from ajax/event ajax query) and converts it to a form that is expected by visjs DataSet
 * @param {type} event
 * @returns {jlab.wfb.eventToItem.item}
 */
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


/**
 * This function returns the previous item from a specific group of a VisJS items object
 * @param {type} items A VisJS items object
 * @param {type} id The id of the item for which we want to get the previous item in it's group
 * @returns {jlab.wfb.getFirstItem.subset|jlab.wfb.getFirstItem.first}
 */
jlab.wfb.getPrevItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return (item.group === curr.group);
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

/**
 * This function returns the next item from a specific group of a VisJS items object
 * @param {type} items A VisJS items object
 * @param {type} id The id of the item for which we want to get the next item in it's group
 * @returns {jlab.wfb.getFirstItem.subset|jlab.wfb.getFirstItem.first}
 */
jlab.wfb.getNextItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return (item.group === curr.group);
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

/**
 * This function returns the first item from a specific group of a VisJS items object
 * @param {type} items A VisJS items object
 * @param {type} id The id of an item belonging to the group from which we want to get the first item for
 * @returns {jlab.wfb.getFirstItem.subset|jlab.wfb.getFirstItem.first}
 */
jlab.wfb.getFirstItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return (item.group === curr.group);
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

/**
 * This function returns the last item from a specific group of a VisJS items object
 * @param {type} items A VisJS items object
 * @param {type} id The id of an item belonging to the group from which we want to get the last item for
 * @returns {jlab.wfb.getFirstItem.subset|jlab.wfb.getFirstItem.first}
 */
jlab.wfb.getLastItem = function (items, id) {
    var curr = items.get(id);
    var subset = items.get({
        filter: function (item) {
            return (item.group === curr.group);
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


/**
 * eventId - The waveform eventId to query information for
 * chartId - typically a number, is appended to "graph-chart-" to create the id of a div that acts as the dygraph container
 * $graphPanel - jQuery object that is the parent object of all dygraph containers
 * graphOptions - set of dygraph graph options
 * series - the waveform event series name the display on this graph
 * seriesArray - the array series that are to be drawn on this page (used for sizing purposes)
 * returns the dygraph object
 */
jlab.wfb.makeGraph = function (event, chartId, $graphPanel, graphOptions, series, seriesArray) {
    if (typeof series === "undefined" || series === null) {
        window.console && console.log("Required argument series not supplied to jlab.wfb.makeGraph");
        return;
    }
    if (typeof event === "undefined" || event === null) {
        window.console && console.log("event is undefined or null");
        return;
    }

    // Need a local copy so that each graph can have different options after visibility updates.
    var opts = JSON.parse(JSON.stringify(graphOptions));

    // Get the data, labels, etc. needed for this chart out of the currentEvent object
    var labels = [];
    var units = "";
    var dygraphIds = [];
    var data = [];
    var ymin = null;
    var ymax = null;
    data.push(event.timeOffsets);
    for (var i = 0; i < event.waveforms.length; i++) {
        for (var j = 0; j < event.waveforms[i].series.length; j++) {
            if (event.waveforms[i].series[j].name === series) {
                data.push(event.waveforms[i].dataPoints);
                labels.push(event.waveforms[i].dygraphLabel);
                dygraphIds.push(event.waveforms[i].dygraphId);
                units = event.waveforms[i].series[j].units;

                // Figure out the y-axis bounds.  All series should be the same, but we should check since each waveform
                // has its own entry for this.
                if (event.waveforms[i].series[j]['y-min'] !== null) {
                    if (ymin === null) {
                        ymin = event.waveforms[i].series[j]['y-min'];
                    }
                    else if (ymin > event.waveforms[i].series[j]['y-min']) {
                        ymin = event.waveforms[i].series[j]['y-min'];
                    }
                }
                if (event.waveforms[i].series[j]['y-max'] !== null) {
                    if (ymax === null) {
                        ymax = event.waveforms[i].series[j]['y-max'];
                    }
                    else if (ymax > event.waveforms[i].series[j]['y-max']) {
                        ymax = event.waveforms[i].series[j]['y-max'];
                    }
                }
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

    // Figure out what size graph we want based on the number of series (i.e., graphs) that we will be displaying
    var containerClass = "'graph-container";
    if (seriesArray.length >= 6) {
        containerClass += " graph-container-thirdwidth";
    } else if (seriesArray.length >= 4) {
        containerClass += " graph-container-halfwidth";
    }
    containerClass += "'";

    // Append the div structure needed to hold this graph
    $graphPanel.append("<div class=" + containerClass + "><div id=graph-chart-" + chartId + " class='graph-chart'></div>"
        + "<div class='graph-legend' id=graph-legend-" + chartId + " ></div>" +
        "<div class='graph-y-control' id='graph-y-controls-'" + chartId + ">" +
        "<ul class='key-value-list'>" +
        "<li><div class='li-key'><label>y-max</label></div><div class='li-value'><input id='graph-y-max-" + chartId + "' type='number' value='" + ymax + "'></div></li>" +
        "<li><div class='li-key'><label>y-min</label></div><div class='li-value'><input id='graph-y-min-" + chartId + "' type='number' value='" + ymin + "'></div></li>" +
        "</ul></div></div>");

    // Set up the needed options
    opts.colors = colors;
    opts.title = series;
    opts.labels = labels;
    opts.axes.y.valueRange = [ymin, ymax];
    opts.xlabel = "ms";
    if (units === "") {
        opts.ylabel = "&ltNO UNITS&gt";
    } else {
        opts.ylabel = units;
    }
    opts.labelsDiv = document.getElementById("graph-legend-" + chartId);

    const doubleClickZoomOutPlugin = {
        activate: function(g) {
            // This zooms all the way back out to the currently set axis range.  That's all of the x data, and the
            // currently set y valueRange.  If y valueRange is [null, null], then you get all of the data displayed.
            return {
                dblclick: e => {
                    e.dygraph.updateOptions({
                        dateWindow: null,  // zoom all the way out
                        valueRange: null  // zoom out based on the current valueRange setting.
                    });
                    e.preventDefault();  // prevent the default zoom out action.
                }
            }
        }
    };
    const crossHairPlugin = new Dygraph.Plugins.Crosshair({direction: "vertical"});
    opts.plugins = [doubleClickZoomOutPlugin, crossHairPlugin];

    var g = new Dygraph(
        // containing div
        document.getElementById("graph-chart-" + chartId),
        data,
        opts
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

    // Set a listener that updates the y valueRange of this plot based on the values specified in input boxes.
    var ymaxElement = document.querySelector('#graph-y-max-' + chartId);
    var yminElement = document.querySelector('#graph-y-min-' + chartId);
    var updateYRange = function(event) {
        var ymin = yminElement.value === "" ? null : parseFloat(yminElement.value);
        var ymax = ymaxElement.value === "" ? null : parseFloat(ymaxElement.value);
        g.updateOptions({axes: {y: {valueRange: [ymin, ymax]}}});
    };
    ymaxElement.addEventListener('change', updateYRange);
    yminElement.addEventListener('change', updateYRange);

    return g;
};


/**
 * This function is responsible for updating the window's URL and form controls to match the currently displayed page.
 * It also updates the history so that the last displayed event is what will be displayed on a "back" button event
 * @returns {undefined}
 */
jlab.wfb.updateBrowserUrlAndControls = function () {

    // Update the URL so someone could navigate back to or bookmark or copy paste the URL 
    var url = jlab.contextPath + "/graph"
        + "?begin=" + jlab.wfb.begin.replace(/ /, '+').encodeXml()
        + "&end=" + jlab.wfb.end.replace(/ /, '+').encodeXml()
        + "&eventId=" + jlab.wfb.currentEvent.id
        + "&system=" + jlab.wfb.system;
    for (var i = 0; i < jlab.wfb.seriesSelections.length; i++) {
        url += "&series=" + jlab.wfb.seriesSelections[i];
    }
    for (var i = 0; i < jlab.wfb.seriesSetSelections.length; i++) {
        url += "&seriesSet=" + jlab.wfb.seriesSetSelections[i];
    }
    for (var i = 0; i < jlab.wfb.locationSelections.length; i++) {
        url += "&location=" + jlab.wfb.locationSelections[i];
    }
    if (jlab.wfb.minCF !== "") {
        url += "&minCF=" + jlab.wfb.minCF;
    }

    // Update the current state of the window so that should a user navigate away, the back button will return them to the last currently displayed event.
    window.history.replaceState(null, null, url);
};


/**
 * This closure is used to update the display dygraphs of event series data.  If an object is supplied, then the function assumes it is an
 * event object of the form returned by the wfbrowser ajax event end point.  If an ID is supplied, it downloads the corresponding event
 * from that ajax endpoint.  This function is responsible for calling any routines to keep the page state in sync with the display.
 * @type Function
 */
jlab.wfb.loadNewGraphs = (function () {
    var $graphPanel = $("#graph-panel");
    var currEventId = null;
    var graphs = null;
    var updateInProgress = false;
    return function (eventId) {

        // When the page first loads, we have access to the already generate event data and don't need to do an AJAX call to get it
        if (typeof eventId === "object") {
            // Make and display the graphs. Save them to the local array so we can delete them on the next update
            graphs = jlab.wfb.makeGraphs(eventId, $graphPanel, jlab.wfb.seriesMasterSet);

            // Make sure the URL bar and UI controls reflect any changes.
            jlab.wfb.updateBrowserUrlAndControls();
            return;
        }

        if (typeof eventId === "undefined" || eventId === null) {
            window.console && console.log("Error: eventId undefined or null");
            jlab.wfb.timeline.setSelection(currEventId);
            return;
        }

        if (updateInProgress) {
            jlab.wfb.timeline.setSelection(currEventId);
            window.console && console.log("Update already in progress");
            return;
        } else {
            updateInProgress = true;
            // Make the graphs a little transparent while we're downloading the data
            $graphPanel.css({opacity: 0.5});

            // Update the global current event and local tracker of the currentEvent
            currEventId = eventId;

            // Make sure the jlab.wfb.timeline matches the current event
            jlab.wfb.timeline.setSelection(currEventId);

            // Request the event object - only the needed series.
            var promise = jQuery.ajax({
                url: jlab.contextPath + "/ajax/event",
                type: "GET",
                dataType: "json",
                data: {
                    id: eventId,
                    out: "dygraph",
                    includeData: true,
                    requester: "graph",
                    series: jlab.wfb.seriesMasterSet,
                },
                traditional: true
            });

            promise.error(function (xhr, textStatus) {
                var json;

                try {
                    json = $.parseJSON(xhr.responseText);
                } catch (err) {
                    window.console && console.log('Response is not JSON: ' + xhr.responseText);
                    json = {};
                }

                var message = json.error || 'Server did not handle request';
                if(quiet) {
                    window.console && console.log('Unable to perform request: ' + message);
                } else {
                    alert('Unable to perform request: ' + message);
                }
            });

            promise.done(function (json) {
                // Sanity check - make sure the id we get back is what we asked for.
                if (json.events[0].id !== currEventId) {
                    alert("Warning: Received different event than requested");
                }
                jlab.wfb.currentEvent = json.events[0];

                // Get the position so we can put it back after updating the graphs
                var xOffset = window.pageXOffset;
                var yOffset = window.pageYOffset;

                // Delete old graph data
                if (graphs !== null) {
                    for (var i = 0; i < graphs.length; i++) {
                        if (graphs[i] !== null) {
                            graphs[i].destroy();
                        }
                    }
                }
                // Clear out the graph panel, set the graph panel back to opaque
                $graphPanel.empty();
                $graphPanel.css({opacity: 1});

                // Make and display the graphs. Save them to the local array so we can delete them on the next update
                graphs = jlab.wfb.makeGraphs(jlab.wfb.currentEvent, $graphPanel, jlab.wfb.seriesMasterSet);

                // Make sure the URL bar and UI controls reflect any changes.
                jlab.wfb.updateBrowserUrlAndControls();

                // Scroll the screen back to the same position
                window.scrollTo(xOffset, yOffset);
            });
            promise.fail(function () {
                // jlab.doAjaxJsonGetRequest handles the generic error logic.  We just need to make sure that the timeline is accurate.
                jlab.wfb.timeline.setSelection(currEventId);
            });
            promise.always(function () {
                updateInProgress = false;
            });
        }
    };
})();

/**
 * Change the visibility on all series in all of the graphs to on or off.
 * @param graphs: An array of Dygraph Objects
 * @param isVisible: A boolean.  true if all should be visible, false if not.
 */
jlab.wfb.setAllVisibility = function(graphs, isVisible) {
    for (var i = 0; i < graphs.length; i++) {
        console.log(graphs[i]);
        console.log("graph " + i);
        // Figure out which column the labeled series is in.  Time is the first column so, the series number is one less.
        var visibility = graphs[i].visibility();
        for (var j = 0; j < visibility.length; j++) {
            visibility[j] = isVisible;
        }
        graphs[i].setVisibility(visibility);
        console.log("graph " + i + " done");
    }
};

/**
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

    // Figure out which series are present in these charts
    var dygraphLabelIdMap = new Map();
    for (var i = 0; i < event.waveforms.length; i++) {
        dygraphLabelIdMap.set(event.waveforms[i].dygraphLabel, event.waveforms[i].dygraphId);
    }

    // Construct checkboxes that will control the visibility of inidividual cavity series.  Once we've created the graphs, we can bind a click event handler
    var checkBoxNum = 0;
    var allVisibility = document.createElement("button");
    allVisibility.appendChild(document.createTextNode("All"));
    allVisibility.setAttribute("style", "width:3em; font-size: 10px; margin: 0px 2px 0px 10px; vertical-align: top;");
    var noneVisibility = document.createElement("button");
    noneVisibility.appendChild(document.createTextNode("None"));
    noneVisibility.setAttribute("style", "width:3em; font-size: 10px; margin: 0px 2px 0px 10px; vertical-align: top;");

    var color = jlab.wfb.dygraphIdToColorArray[checkBoxNum];

    switch (jlab.wfb.system) {
        case "rf":
            dygraphLabelIdMap.forEach(function (id, label, map) {
                // RF has 8 cavities.  It's better to leave a disabled checkbox, than throw off the alignment
                while (id > checkBoxNum+1) {
                    color = jlab.wfb.dygraphIdToColorArray[checkBoxNum];
                    $("#graph-panel .graph-panel-visibility-controls fieldset").append(
                        '<label style="font-weight: bold; color: ' + color + ';" for="' + forName + '">C' + (checkBoxNum+1) + '</label>' +
                        '<input type="checkbox" id="cav-toggle-' + (checkBoxNum+1) + '" class="cavity-toggle" data-label="' + label + '"  disabled="disabled">');
                    if (checkBoxNum === 4) {
                        $("#graph-panel .graph-panel-visibility-controls fieldset").append(allVisibility);
                        $("#graph-panel .graph-panel-visibility-controls fieldset").append("<br>");
                    }
                    checkBoxNum++;
                }
                if (checkBoxNum === 4) {
                    $("#graph-panel .graph-panel-visibility-controls fieldset").append(allVisibility);
                    $("#graph-panel .graph-panel-visibility-controls fieldset").append("<br>");
                }
                var forName = "cav-toggle-" + checkBoxNum;
                // For RF we can assign nicer cavity number labels instead of just a colored line.
                $("#graph-panel .graph-panel-visibility-controls fieldset").append(
                    '<label style="font-weight: bold; color: ' + color + ';" for="' + forName + '">C' + id + '</label>' +
                    '<input type="checkbox" id="cav-toggle-' + checkBoxNum + '" class="cavity-toggle" data-label="' + label + '" checked="checked">');
                checkBoxNum++;
            });
            while (checkBoxNum < 8) {
                color = jlab.wfb.dygraphIdToColorArray[checkBoxNum];
                var forName = "cav-toggle-" + checkBoxNum;

                $("#graph-panel .graph-panel-visibility-controls fieldset").append(
                    '<label style="font-weight: bold; color: ' + color + ';" for="' + forName + '">C' + (checkBoxNum+1) + '</label>' +
                    '<input type="checkbox" id="cav-toggle-' + (checkBoxNum+1) + '" class="cavity-toggle" disabled="disabled">');
                checkBoxNum++;
            }
            $("#graph-panel .graph-panel-visibility-controls fieldset").append(noneVisibility);
            break;
        default:
            dygraphLabelIdMap.forEach(function (id, label, map) {
               if (checkBoxNum === 4) {
                    $("#graph-panel .graph-panel-visibility-controls fieldset").append("<br>");
                }
                var forName = "cav-toggle-" + checkBoxNum;
                var color = jlab.wfb.dygraphIdToColorArray[id - 1];
                // Give a colored line as a label.  Dygraph already does this for their labels, so just reuse their div with the color we specified earler
                $("#graph-panel .graph-panel-visibility-controls fieldset").append('<label style="font-weight: bold; color: ' + color + ';" for="' + forName + '"><div class="dygraph-legend-line" style="border-bottom-color: ' + color + ';"></div></label><input type="checkbox" id="cav-toggle-' + checkBoxNum + '" class="cavity-toggle" data-label="' + label + '" checked="checked">');
                checkBoxNum++;
            });
            $("#graph-panel .graph-panel-visibility-controls fieldset").append(allVisibility);
            $("#graph-panel .graph-panel-visibility-controls fieldset").append(noneVisibility);
            break;
    }

    // Setup the download button
    $("#graph-panel .graph-panel-action-controls .download").on("click", function () {
        window.location = jlab.contextPath + "/ajax/event?id=" + event.id + "&out=orig&includeData=true";
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
    $("#graph-panel .graph-panel-action-controls").prepend("<button class='help'>Help</button>");
    $("#graph-panel .graph-panel-action-controls .help").on("click", (function () {
        var isShown = false;
        return function () {
            if (!isShown) {
                $("#graph-panel .graph-panel-action-controls").prepend(helpHtml);
                isShown = true;
            } else {
                $("#graph-panel .graph-panel-action-controls .help-dialog").remove();
                isShown = false;
            }
        };
    })());

    // Undocumented function to get the set of items underlying the timeline
    var items = jlab.wfb.timeline.itemSet.getItems();

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
        axes: {
            x: {pixelsPerLabel: 30},
            y: {pixelsPerLabel: 20}
        },
        legend: "always",
        labelsSeparateLines: true,
        highlightCircleSize: 3,
        connectSeparatedPoints: true,
        strokeWidth: 2,
        highlightSeriesOpts: {
            strokeWidth: 2,
            strokeBorderWidth: 2,
            highlightCircleSize: 7
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

    // Set event listeners the check/uncheck all visibility boxes, and show/hide all series signals on all graphs.
    allVisibility.addEventListener("click", function(ev) {
        $("#graph-panel .graph-panel-visibility-controls fieldset input:checkbox").each(function (index, element){
            if (!element.disabled && !element.checked) {
                element.checked = true;
            }
        });
        jlab.wfb.setAllVisibility(graphs, true);
    });
    noneVisibility.addEventListener("click", function(ev) {
        $("#graph-panel .graph-panel-visibility-controls fieldset input:checkbox").each(function (index, element){
            if (!element.disabled && element.checked) {
                element.checked = false;
            }
        });
        jlab.wfb.setAllVisibility(graphs, false);
    });

    // Set a listener that toggles visibility on series
    $(".cavity-toggle").on("change", function () {
        var label = $(this).data("label");
        for (var i = 0; i < graphs.length; i++) {
            // Figure out which column the labeled series is in.  Time is the first column so, the series number is one less.
            var props = graphs[i].getPropertiesForSeries(label);

            // Some graphs may have a different number of dygraph series (system acclrm, I'm looking at you)
            if (props !== null) {
                var seriesId = props.column - 1;
                if (graphs[i].visibility()[seriesId]) {
                    graphs[i].setVisibility(seriesId, false);
                } else {
                    graphs[i].setVisibility(seriesId, true);
                }
            }
        }
    });

    return graphs;
};

/**
 * Setup the timeline widget
 * @param container - element that will hold the timeline widget
 * @param groups - viz.DataSet containing row group names
 * @param items  viz.DataSet containing the items to place on the timeline
 */
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

    var timeline = new vis.Timeline(container, items, groups, options);
    if (typeof jlab.wfb.currentEvent === "object" && jlab.wfb.currentEvent !== null) {
        timeline.setSelection(jlab.wfb.currentEvent.id);
    }

    timeline.on("select", function (params) {
        jlab.wfb.loadNewGraphs(params.items[0]);
    });

    return timeline;
};

jlab.wfb.validateForm = function () {
    var $err = $("#page-controls-error");
    $err.empty();

    // Make sure that we will have some sort of series to display in the graphs
    if (jlab.wfb.$seriesSelector.val() === null && jlab.wfb.$seriesSetSelector.val() === null) {
        $err.append("At least one series or series set must be supplied.");
        return false;
    }

    // Make sure that the timeline will have some sort of location to show and that we will have a group of events to pick from
    if (jlab.wfb.$locationSelector.val() === null) {
        $err.append("At least one zone must be supplied.");
        return false;
    }

    // Make sure we got start/end times
    if (jlab.wfb.$startPicker.val() === null || jlab.wfb.$startPicker.val() === "") {
        $err.append("Start time required.");
        return false;
    }
    if (jlab.wfb.$endPicker.val() === null || jlab.wfb.$endPicker.val() === "") {
        $err.append("End time required.");
        return false;
    }

    // Check that the date range isn't too large.  The timeline widget uses DOM elements and too many of them can slow down the browser.
    var start = new Date(jlab.wfb.$startPicker.val());
    var end = new Date(jlab.wfb.$endPicker.val());
    var day = 1000 * 60 * 60 * 24; // millis to days
    if (((end.getTime() - start.getTime()) / day) > 14) {
        var result = window.confirm("Large date ranges can cause the interface to become sluggish.  Continue?");

        if (result === false) {
            return false;
        }
    }
    // Everything passed the checks.  Return true;
    return true;
};


$(function () {

    var select2Options = {
        width: "15em"
    };
    jlab.wfb.$seriesSelector.select2(select2Options);
    jlab.wfb.$seriesSetSelector.select2(select2Options);
    jlab.wfb.$locationSelector.select2(select2Options);
    if (jlab.wfb.system === 'acclrm') {
        jlab.wfb.$classificationSelector.select2(select2Options);
    }
    jlab.wfb.$startPicker.val(jlab.wfb.begin);
    jlab.wfb.$endPicker.val(jlab.wfb.end);
    $(".date-time-field").datetimepicker({
        controlType: jlab.dateTimePickerControl,
        dateFormat: 'yy-mm-dd',
        timeFormat: 'HH:mm:ss'
    });

    $("#help-container .help").on("click", function () {
        $helpDialog = $(this).siblings(".help-dialog");
        $helpDialog.toggle({duration: 0});
    });

    $("#page-controls-submit").on("click", jlab.wfb.validateForm);

    // Setup the groups for the timeline
    var groupArray = new Array(jlab.wfb.locationSelections.length);
    for (var i = 0; i < jlab.wfb.locationSelections.length; i++) {
        groupArray[i] = {
            id: jlab.wfb.locationToGroupMap.get(jlab.wfb.locationSelections[i]),
            content: jlab.wfb.locationSelections[i]
        };
    }
    var groups = new vis.DataSet(groupArray);

    // Setup the items for the timeline
    var itemArray = new Array(jlab.wfb.eventArray.length);
    for (var i = 0; i < jlab.wfb.eventArray.length; i++) {
        itemArray[i] = jlab.wfb.eventToItem(jlab.wfb.eventArray[i]);
    }
    var items = new vis.DataSet(itemArray);

    var timelineDiv = document.getElementById("timeline-container");
    jlab.wfb.timeline = jlab.wfb.makeTimeline(timelineDiv, groups, items);
    // Having a weird problem where chrome won't display timeline unless the div is resized.
    // This is a hack, but it works
    var timeLineWidth = window.getComputedStyle(timelineDiv).width;
    timelineDiv.setAttribute("style", "width:" + timeLineWidth + 1);
    timelineDiv.setAttribute("style", "");

    jlab.wfb.timeline.setOptions({'zoomKey': 'ctrlKey'});

    if (typeof jlab.wfb.currentEvent === "object" && typeof jlab.wfb.currentEvent.id === "number") {
        jlab.wfb.loadNewGraphs(jlab.wfb.currentEvent);
    } else {
        $("#graph-panel").append("<div style='font-weight: bold;'>No events found</div>");
    }
});