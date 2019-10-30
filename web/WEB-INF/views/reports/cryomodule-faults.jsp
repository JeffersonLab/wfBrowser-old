<%@page contentType="text/html" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="title" value="Cryomodule Faults"/>
<t:report-page title="${title}">
    <jsp:attribute name="stylesheets">
        <style>
            /*Plotly svg element has some overhang on initial draw.  This 99% helps hide that fact.*/
            #barchart-container {
                width: 99%;
                justify-content: center;
                display: grid;
                grid-template-columns: 49% 49%;
                grid-gap: 4px;
                gap: 4px;
            }
            #barchart-wrapper {
                padding: 2px;
            }
            .barchart {
                border: #4c4c4c solid 1px;
                padding-right: 2.5%;
            }
            .key-value-list {
                display: inline-block;
                vertical-align: top;
            }
            #barcharts-title{
                text-align: center;
                font-weight: bold;
            }
            input[type="submit"] {
                display: block;
            }
        </style>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/plotly-v1.50.1.min.js"></script>
        <script>
            // The json should be structured like this
            /*
            [
                {"location":<location_1>,"label-combo":<csv string of cavity,fault labels>,"count":<count of events labeled this way>},
                {"location":<location_1>,"label-combo":<csv string of cavity,fault labels>,"count":<count of events labeled this way>},
                ...
                {"location":<location_N>,"label-combo":<csv string of cavity,fault labels>,"count":<count of events labeled this way>},
                ...
            ]
             */
            var label_summary = <c:out value="${tallyArray}" escapeXml="false"></c:out>;
            var fault_label_options = <c:out value="${faultLabelOptions}" escapeXml="false"></c:out>;
            var fault_color_palette = ["rgba(0,107,164,1)", "rgba(255,128,14,1)", "rgba(171,171,171,1)", "rgba(89,89,89,1)",
                "rgba(95,158,209,1)", "rgba(200,82,0,1)", "rgba(137,137,137,1)", "rgba(162,200,236,1)", "rgba(255,188,121,1)",
                "rgba(207,207,207,1)", "rgba(0,0,0,1)", "rgba(255,255,255,1)"];
            var fault_color_map = {};
            fault_color_map["no label"] = fault_color_palette[0];
            var i = 1;
            fault_label_options.forEach(function (elem) {
                fault_color_map[elem] = fault_color_palette[i];
                i = i + 1;
            })

            // Keep track of bad cavity labels are receive them.  Alert if we get any.
            var invalid_cavity_labels = [];

            // Assume valid cavity labels to display are here.
            var cavity_labels = ["1", "2", "3", "4", "5", "6", "7", "8", "multi", "none"];

            // Inspect the label_summary object for high level info
            var num_charts = label_summary.length;

            // Construct an object keyed on locations.  Each location key will contain data for generating a plot.
            var plot_data = {};
            label_summary.forEach(function (locObj) {
                var zone = locObj["location"];
                var labelCombo = locObj["label-combo"];
                var count = locObj["count"];

                // label-combo should be a CSV of string all the labels for the given combination.  The server should be
                // sending over only cavity and fault-type labels (or "NULL" if unlabeled)
                var labels = labelCombo.split(",");

                // Make sure we got some labels.  Check and throw an alert.
                if (labels.length === 0) {
                    console.log(JSON.stringify(locObj));
                    alert("Received no label data for '" + zone + "'.  Report may be missing some data.");
                    return;
                }
                // Some faults will be unlabeled and should be returned as label-combo: "NULL".  If we get a single
                // label that's not "NULL" something went wrong.
                if (labels.length === 1 && labels[0] !== "NULL") {
                    console.log(JSON.stringify(locObj));
                    alert("Received unexpected data for '" + zone + "'.  Report may be missing some data.");
                    return;
                }
                // We should not be getting more than two labels.  Check and throw an alert.
                if (labels.length > 2) {
                    console.log(JSON.stringify(locObj));
                    alert("Received to many labels for '" + zone + "'.  Report may be missing some data.");
                    return;
                }

                // Get the labels - after above checks (that single label == "NULL"), length one means it's unlabeled
                var cavity = "none";
                var fault = "no label";
                if (labels.length === 2) {
                    fault = labels[0];
                    cavity = labels[1];
                }
                // Short this so the chart looks better
                if (cavity === "multiple") {
                    cavity = "multi";
                }

                // Check for and exclude any invalid cavity labels
                if (!cavity_labels.includes(cavity)) {
                    console.log("Received invalid cavity label.  zone: " + zone + " cavity: " + cavity + " fault-type: " +
                        fault + " count: " + count);
                    invalid_cavity_labels.push(zone + " " + cavity + " " + fault + " " + count);
                    return;
                }

                // Initiate the zone specific plot data structure.  It's not clear what cavity labels will be provided.
                // Let's start with the assumption that it will be 1,2,..,8,"multiple","no label"
                if (!plot_data.hasOwnProperty(zone)) {
                    plot_data[zone] = {"faults": [], "data": []};
                }

                // Check if this is the first time the current fault has been seen.  Initialize the data structure if so,
                // or just update the appropriate counter and add the fault to the list otherwise.
                if (!plot_data[zone].faults.includes(fault)) {
                    plot_data[zone].faults.push(fault);
                    plot_data[zone].data.push({
                        x: cavity_labels,
                        y: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                        name: fault,
                        type: "bar",
                        marker: {
                            color: fault_color_map[fault],
                            line: {color: "rgba(67,67,67,1", width: 1}
                        }
                    });
                    console.log(zone + " -- " + fault + " -- " + fault_color_map[fault]);
                    plot_data[zone].layout = {

                        yaxis: {
                            title: zone,
                            fixedrange: true    // Disable "lasso zoom"
                        },
                        xaxis: {
                            title: "Cavity Label",
                            type: "category",   // Keeps plotly from doing dumb things when X axis contains numbers and strings
                            fixedrange: true    // Disable "lasso zoom"
                        },
                        barmode: 'stack',
                        autosize: true,
                        margin: {l: 40, r: 10, t: 20, b: 50}, // be careful here so that labels have room to flip orientation
                        hovermode: "closest",
                        showlegend: true
                    };
                    if (num_charts > 2) {
                        plot_data[zone].layout.height = 200;
                    }
                }

                var fault_index = plot_data[zone].faults.indexOf(fault);
                var cavity_index = plot_data[zone].data[fault_index].x.indexOf(cavity);
                plot_data[zone].data[fault_index].y[cavity_index] = count;
            });

            // Iterate over the data to determine what the y-axis max value should be.
            var max_count = 0;
            for (var zone in plot_data) {
                if (!plot_data.hasOwnProperty(zone)) {
                    continue;
                }
                var count_by_cav = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
                plot_data[zone].data.forEach(function (fault_trace) {
                    for (var i = 0; i < fault_trace.y.length; i++) {
                        count_by_cav[i] = count_by_cav[i] + fault_trace.y[i];
                    }
                });
                count_by_cav.forEach(function (elem) {
                    if (max_count < elem) {
                        max_count = elem;
                    }
                })
            }

            // Set the y-axis range for all of the of plots
            for (var zone in plot_data) {
                if (!plot_data.hasOwnProperty(zone)) {
                    continue;
                }
                plot_data[zone].layout.yaxis.range = [0, max_count];
            }

            // Alert the user if we received any unexpected cavity labels
            if (invalid_cavity_labels.length > 0) {
                var msg = "Received invalid cavity labels.  Charts may be missing some data.  Received <zone> <cav> <fault> <count>\n";
                invalid_cavity_labels.forEach(function (elem) {
                    msg += elem + "\n";
                });
                alert(msg);
            }


            var plotly_config = {
                responsive: true,   // Auto resizing
                scrollZoom: false,  // Disable scroll to zoom behavior
                editable: false,    // Can't edit the plot
                displayModeBar: false,  // Don't show the plotly control bar
                showAxisDragHandles: false,
                showAxisRangeEntryBoxes: false,
            }

            var container = document.getElementById("barchart-container");
            //console.log(JSON.stringify(plot_data));
            //
            console.log(JSON.stringify(jlab.wfb.locationSelections));
            // for (var zone in jlab.wfb.locationSelections) {
            jlab.wfb.locationSelections.forEach(function (zone) {
                console.log(zone);
                var chart = document.createElement("div");
                chart.setAttribute("id", "chart-" + zone);
                chart.setAttribute("class", "barchart");
                container.appendChild(chart);

                // If we didn't get any data for this one, print out a friendly message
                if (!plot_data.hasOwnProperty(zone)) {
                    var chart_div = document.getElementById("chart-" + zone);
                    chart_div.innerHTML = "<center><bold>No data recieved for zone " + zone + "</bold></center>";
                } else {
                    Plotly.plot(chart, plot_data[zone].data, plot_data[zone].layout, plotly_config);
                }
            });
        </script>
        <script>
            jlab.wfb.$startPicker = $('#start-date-picker');
            jlab.wfb.$endPicker = $('#end-date-picker');
            jlab.wfb.$startPicker.val(jlab.wfb.begin);
            jlab.wfb.$endPicker.val(jlab.wfb.end);
            jlab.wfb.$locationSelector = $('#location-selector');

            $(".date-time-field").datetimepicker({
                controlType: jlab.dateTimePickerControl,
                dateFormat: 'yy-mm-dd',
                timeFormat: 'HH:mm:ss'
            });

            var select2Options = {
                width: "15em"
            };
            jlab.wfb.$locationSelector.select2(select2Options);
        </script>

    </jsp:attribute>
    <jsp:body>
        <h2>Fault Counts By Cavity and Type</h2>
        <form>
            <fieldset>
                <legend>Report Controls</legend>
                <ul class="key-value-list">
                    <li>
                        <div class="li-key"><label class="required-field" for="begin" title="Earliest time to display">Start
                            Time</label>
                        </div>
                        <div class="li-value"><input type="text" id="start-date-picker" class="date-time-field"
                                                     name="begin" placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                    </li>
                    <li>
                        <div class="li-key"><label class="required-field" for="end"
                                                   title="Latest time to display.">End Time</label></div>
                        <div class="li-value"><input type="text" id="end-date-picker" class="date-time-field" name="end"
                                                     placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                    </li>
                </ul>
                <ul class="key-value-list">
                    <li>
                        <div class="li-key"><label class="required-field" for="locations"
                                                   title="Include on the following locations.">Zone</label></div>
                        <div class="li-value">
                            <select id="location-selector" name="location" multiple>
                                <c:forEach var="location" items="${requestScope.locationSelectionMap}">
                                    <option value="${location.key}" label="${location.key}"
                                            <c:if test="${location.value}">selected</c:if>>${location.key}</option>
                                </c:forEach>
                            </select>
                        </div>
                    </li>
                </ul>
                <ul class="key-value-list">
                    <li>
                        <div class="li-key"><label for="conf-input">Confidence Filter</label></div>
                        <div class="li-value">
                            <input id="conf-input" type="text" width="5" name="conf" value="${confString}"
                                   placeholder="default: no filter">
                            <select id="confOp-input" name="confOp">
                                <option value=">" <c:if test="${confOpString == '>'}">selected</c:if>>&gt;</option>
                                <option value="<" <c:if test="${confOpString == '<'}">selected</c:if>>&lt;</option>
                                <option value=">=" <c:if test="${confOpString == '>='}">selected</c:if>>&gt;=</option>
                                <option value="<=" <c:if test="${confOpString == '<='}">selected</c:if>>&lt;=</option>
                                <option value="=" <c:if test="${confOpString == '='}">selected</c:if>>=</option>
                                <option value="!=" <c:if test="${confOpString == '!='}">selected</c:if>>!=</option>
                            </select>
                        </div>
                    </li>
                </ul>
                <input type="submit" value="Submit">
            </fieldset>

        </form>
        <div id="barchart-wrapper">
            <div id="barcharts-title">Fault Label Counts By Cavity Label</div>
            <div id="barchart-container"></div>
        </div>
        <script>
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {};

            jlab.wfb.begin = "${requestScope.beginString}";
            jlab.wfb.end = "${requestScope.endString}";
            jlab.wfb.locationSelections = [<c:forEach var="location" items="${locationSelections}" varStatus="status">'${location}'<c:if test="${!status.last}">, </c:if></c:forEach>];
        </script>
    </jsp:body>
</t:report-page>
