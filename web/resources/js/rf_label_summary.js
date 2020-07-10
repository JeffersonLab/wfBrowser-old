var jlab = jlab || {};
jlab.wfb = jlab.wfb || {};

// Handle all of the data prep and presentation code
/*
* Customization of dygraphs point highlight.  Normally highlights nearest point of nearest row.  This now
* highlights the nearest point regardless of row.
*/
Dygraph.prototype.mouseMove_ = function (event) {
    // This prevents JS errors when mousing over the canvas before data loads.
    var points = this.layout_.points;
    if (points === undefined || points === null) return;

    var canvasCoords = this.eventToDomCoords(event);
    var canvasx = canvasCoords[0];
    var canvasy = canvasCoords[1];

    var highlightSeriesOpts = this.getOption("highlightSeriesOpts");
    var selectionChanged = false;
    if (highlightSeriesOpts && !this.isSeriesLocked()) {
        var closest;
        if (this.getBooleanOption("stackedGraph")) {
            closest = this.findStackedPoint(canvasx, canvasy);
        } else {
            closest = this.findClosestPoint(canvasx, canvasy);
        }
        selectionChanged = this.setSelection(closest.row, closest.seriesName);
    } else {
        var closest = this.findClosestPoint(canvasx, canvasy);
        var idx = closest.point.idx;
        selectionChanged = this.setSelection(idx);
    }

    var callback = this.getFunctionOption("highlightCallback");
    if (callback && selectionChanged) {
        callback.call(this, event,
            this.lastx_,
            this.selPoints_,
            this.lastRow_,
            this.highlightSet_);
    }
};
/* ------ End of Dygraphs customization -------*/

// "Plugin" function for dygraphs that unzooms out to the specified valueRange setting.
const doubleClickZoomOutPlugin = {
    activate: function (g) {
        // Save the initial y-axis range for later.
        const initialValueRange = g.getOption('valueRange');
        return {
            dblclick: function (e) {
                console.log(g.getOption("valueRange"), g.getOption("axes"));
                e.dygraph.updateOptions({
                    dateWindow: null,  // zoom all the way out
                    axes: {y: {valueRange: initialValueRange}}  // zoom to a specific y-axis range.
                });
                e.preventDefault();  // prevent the default zoom out action.
            }
        };
    }
};


jlab.wfb.pad = function (n, width, z) {
    z = z || '0';
    n = n + ""; // convert to string
    return n.length >= width ? n : new Array(width - n.length + 1).join(z) + n;
};

// An object that knows how to turn a string variable into one of several levels of a categorical variable
// levels - an array of strings of the names of the levels
// name_mapper - a map of strings that should be converted to specific levels
jlab.wfb.Categorizer = function (levels, name_mapper) {
    var mapper = name_mapper;
    var get_name = function (label) {
        var out = name_mapper[label];
        if (out === undefined) {
            out = 'Other';
        }
        return out;
    };

    // This is used to get the categorical index of the label (or "No Label")
    var get_numeric_value = function (label) {
        return levels.indexOf(get_name(label));
    };

    // This ticker is used to display the appropriate ticks on a dygraph chart
    var ticker = function () {
        var n = levels.length;
        var out = new Array(n);
        for (var i = 0; i < n; i++) {
            out[i] = {v: i, label: levels[i]};
        }
        return out;
    };

    return {
        mapper: mapper,
        levels: levels,
        'get_name': get_name,
        'get_numeric_value': get_numeric_value,
        'ticker': ticker
    };
};


var alpha = 0.5;
var colors = ["rgb(0,0,0," + alpha + ")", "rgb(230,159,0, " + alpha + ")", "rgb(86,180,233," + alpha + ")",
    "rgb(0,158,115," + alpha + ")", "rgb(240,228,66," + alpha + ")", "rgb(0,114,178," + alpha + ")",
    "rgb(213,94,0," + alpha + ")", "rgb(204,121,167," + alpha + ")", "rgb(100,100,100," + alpha + ")",
    "rgb(200,200,200," + alpha + ")", "rgb(0,100,0," + alpha + ")", "rgb(0,0,100," + alpha + ")"];

jlab.wfb.rand = function (a, b) {
    return Math.random() * (b - a) + a;
};

// Check whether the occurrence of this element is the first one.  In an array.  Meant to be used with Array.filter
var distinct = function (value, index, self) {
    return self.indexOf(value) === index;
};

var eventCompare = function (e1, e2) {
    if (e1 != null && e2 == null) {
        return -1;
    } else if (e1 == null && e2 != null) {
        return 1;
    } else if (e1 == null && e2 == null) {
        return 0;
    } else if (e1[0] < e2[0]) {
        return -1;
    } else if (e1[0] > e2[0]) {
        return 1;
    } else {
        return 0;
    }

};

var zone_mapper = jlab.wfb.Categorizer(['0L04', '1L07', '1L22', '1L23', '1L24', '1L25', '1L26', '2L22', '2L23', '2L24', '2L25', '2L26'], {
    '0L04': '0L04',
    '1L07': '1L07', '1L22': '1L22', '1L23': '1L23', '1L24': '1L24', '1L25': '1L25', '1L26': '1L26',
    '2L22': '2L22', '2L23': '2L23', '2L24': '2L24', '2L25': '2L25', '2L26': '2L26'
});

var cavity_mapper = jlab.wfb.Categorizer(['Multi', '1', '2', '3', '4', '5', '6', '7', '8', 'Other', 'No_Label'], {
    "Multi": "Multi",
    'multiple': "Multi",
    '0': 'Multi',
    '1': '1',
    '2': '2',
    '3': '3',
    '4': '4',
    '5': '5',
    '6': '6',
    '7': '7',
    '8': '8',
    'Other': 'Other',
    "null": "No_Label",
    "No_Label": "No_Label"
});

var fault_mapper = jlab.wfb.Categorizer(["Single_Cav", "Multi_Cav", "Quench", "E_Quench", "Quench_3ms", "Quench_100ms", "Microphonics",
    "Controls_Fault", "Other", "No_Label"], {
    "Single_Cav": "Single_Cav",
    "Single Cav Turn off": "Single_Cav",
    "Single Cavity Turn off": "Single_Cav",
    "Single Cavity Turn Off": "Single_Cav",
    "Multi_Cav": "Multi_Cav",
    "Multi Cav turn Off": "Multi_Cav",
    "Multi Cav Turn off": "Multi_Cav",
    "Multi Cav turn off": "Multi_Cav",
    "Multi Cavity turn Off": "Multi_Cav",
    "Multi Cavity Turn off": "Multi_Cav",
    "Multi Cavity Turn Off": "Multi_Cav",
    "Quench": "Quench",
    "E_Quench": "E_Quench",
    "Quench_3ms": "Quench_3ms",
    "Quench_100ms": "Quench_100ms",
    "Microphonics": "Microphonics",
    "Controls Faults": "Controls_Fault",
    "Controls_Fault": "Controls_Fault",
    "Other": 'Other',
    "null": "No_Label",  // fault_name_mapper[null]
    "No_Label": "No_Label"
});

// Returns a string representing the Linac based on zone name
jlab.wfb.get_linac = function (zone) {
    var linac = zone.substring(0, 2);
    if (linac == '0L') {
        return "INJ";
    } else if (linac == '1L') {
        return "NL";
    } else if (linac == '2L') {
        return "SL";
    }

    return null;
};


jlab.wfb.process_event_data_to_heatmaps = function (event_data, column_mapper, row_mapper, labeled_only=true,
                                                    facet_on=null, locations) {
    var events = event_data.events;
    var n_cols = column_mapper.levels.length; // columns
    var n_rows = row_mapper.levels.length; // rows

    var heatmaps = {}; // Object containing heatmaps, keyed on facet names

    events.forEach(function (event) {
        // Determine the "raw" column name
        var column = null;
        var row = null;

        if (event.labels != null) {
            event.labels.forEach(function (label) {
                if (label.name === 'cavity') {
                    column = label.value;
                }
                if (label.name === 'fault-type') {
                    row = label.value;
                }
            });
        } else if (labeled_only) {
            // no labels for the event and we only want the labeled ones.
            return;
        }

        // Convert the raw strings into corresponding index value
        var column_number = column_mapper.get_numeric_value(column);
        var row_number = row_mapper.get_numeric_value(row);

        // Update the heatmap count for the column/value pair
        var facet = "All";
        if (facet_on == "linac") {
            facet = jlab.wfb.get_linac(event.location);
        } else if (facet_on == "zone") {
            facet = event.location;
        }

        if (!heatmaps.hasOwnProperty(facet)) {
            heatmaps[facet] = new Array(n_rows);
            for (var i = 0; i < n_rows; i++) {
                heatmaps[facet][i] = new Array(n_cols).fill(0);
            }
        }
        heatmaps[facet][row_number][column_number]++;
    });

    // Make sure that every facet level has a heatmap matrix, even if it's all zeros.
    var expected_facets = [];
    locations.forEach(function (loc) {
        // Figure out what facet should be based on the location and the facet_on setting
        var facet;
        if (facet_on == "linac") {
            facet = jlab.wfb.get_linac(loc);
        } else if (facet_on == "zone") {
            facet = loc;
        } else {
            facet = "All";
        }

        // Just add every facet we come across.  We can dedupe afterwards.
        expected_facets.push(facet);
    });

    // Remove any duplicates from expected_facets
    Object.values(expected_facets).filter(distinct);

    // Fill in any missing facet values with a zero matrix
    expected_facets.forEach(function (facet) {
        if (!heatmaps.hasOwnProperty(facet)) {
            heatmaps[facet] = new Array(n_rows);
            for (var i = 0; i < n_rows; i++) {
                heatmaps[facet][i] = new Array(n_cols).fill(0);
            }
        }
    });

    return heatmaps;
};

// Ths produces dygraph data 2D array for fault event data that is suitable for producing a strip chart/dot plot for
// 2D categorical data (e.g., cavity labels by zone for each event).  It also produces a collection of heat map 2D
// arrays faceted on the specified factor.  Here the value argument is used for the row indexes.
//
// event_data - A JSON object that is returned by the wfbrowser/ajax/event endpoint.  Expected format is
//     { events: [ {location: <zone>, datetime_utc: <YYYY-mm-dd HH:MM:SS.s>,
//                  labels: [{name 'cavity', value: <cav_label>}, {name 'fault-type', value: <ft_label>}], ...}, ...
//               ]
//     }
//     NOTE: labels array will be null if the event was unlabeled
// columns: A string that should be of the value cavity, fault, or zone.  Controls what part of data is used as column headers.
//          This value is what is treated as a series in Dygraphs and what determines the color coding of points
// values: A string that should be of the value cavity, fault, or zone.  Controls what part of data is used as value levels
// column_mapper: A Categorizer object for processing data used as column headers
// value_mapper: A Categorizer object for processing data used as 2D array values
// labeled_only: Boolean for should the output only include labeled events
// facet_on: A string or null.  What should the heat map data be faceted on.  Valid values are null, linac, or zone
//
// returns {dotplot: <2D array based on function selections>, heatmaps: {'facet1': <2D count array> , 'facet2': ...}}
jlab.wfb.process_event_data = function (event_data, columns, values, column_mapper, value_mapper, labeled_only=true) {
    var events = event_data.events;
    var n_columns = column_mapper.levels.length;

    var value_by_column = [];
    events.forEach(function (event) {
        // Timestamp, Level 1, Level_2, ..., Level_n.  +1 is for Timestamp.  One for each of the groupings
        var point = new Array(n_columns + 1).fill(null);

        // Determine the "raw" column name
        var column;
        if (columns == 'zone') {
            column = event.location;
        } else {
            // This effectively causes fault-type to be the default column selection
            var label_name = (columns == "cavity" ? "cavity" : "fault-type");
            if (event.labels === null) {
                if (labeled_only) {
                    return;
                }
                column = null;
            } else {
                event.labels.forEach(function (label) {
                    if (label.name == label_name) {
                        column = label.value;
                    }
                });
            }
        }

        // Determine the 'raw' value level name
        var value;
        if (values == 'zone') {
            value = event.location;
        } else {
            // This effectively causes cavity to be the default value selection.  Should probably be different than
            // default column type.
            var label_name = (values == "fault" ? "fault-type" : "cavity");
            if (event.labels === null) {
                value = null;
            } else {
                event.labels.forEach(function (label) {
                    if (label.name == label_name) {
                        value = label.value;
                    }
                });
            }
        }


        // Convert the raw strings into corresponding index value
        var column_number = column_mapper.get_numeric_value(column);
        var value_number = value_mapper.get_numeric_value(value);

        // Construct the point and add it to the output dotplot data.
        point[0] = new Date(event.datetime_utc + "Z");
        point[column_number + 1] = value_number + jlab.wfb.rand(-0.05, 0.05);
        value_by_column.push(point);
    });

    return value_by_column.sort(eventCompare);
};

// Draw the dygraph-based dotplot
// div - HTML container element for the plot
// data - dygraph data needed for the plot in javascript native format (2D array)
// column_mapper - A Categorizer for the columns (colored series) of data to be displayed
// value_mapper - A Categorizer for the values (rows) of data to be displayed
// title - The chart title to display
// begin - A date time string for the beginning of the plot time range
// end - A date time string for the end of the plot time range
jlab.wfb.plot_dotplot = function (div, data, column_mapper, value_mapper, title, begin, end) {

    // Setup the HTML divs to contain the plot structure
    var wrapper_div = document.createElement("div");
    wrapper_div.classList.add("dotplot-wrapper");
    var title_div = document.createElement("div");
    title_div.classList.add("chart-title");
    title_div.textContent = title;
    var legend_div = document.createElement("div");
    legend_div.classList.add('dotplot-legend');
    var plot_div = document.createElement("div");
    plot_div.classList.add("dotplot-container");

    div.appendChild(wrapper_div);
    wrapper_div.appendChild(title_div);
    wrapper_div.appendChild(legend_div);
    wrapper_div.appendChild(plot_div);

    // Setup the plot that goes in that HTML structure
    colors = ['rgb(159,1,98)', 'rgb(0,159,129)', 'rgb(200, 130, 175)', 'rgb(132,0,205)', 'rgb(0,141,249)',
        'rgb(0,194,249)', 'rgb(164,1,34)', 'rgb(226,1,52)', 'rgb(255,110,58)', 'rgb(255,195,59)'];
    if (column_mapper.levels.length > 10) {
        colors = ['rgb(159,1,98)', 'rgb(0,159,129)', 'rgb(255, 90, 175)', 'rgb(0,252,207)', 'rgb(132,0,205)', 'rgb(0,141,249)',
            'rgb(0,194,249)', 'rgb(255,178,253)', 'rgb(164,1,34)', 'rgb(226,1,52)', 'rgb(255,110,58)', 'rgb(255,195,59)'];
    }
    // colors = ['rgb(0,0,0)', 'rgb(80,80,80)', 'rgb(170, 170, 170)', 'rgb(252,140,0)', 'rgb(132,0,205)', 'rgb(0,141,249)',
    //     'rgb(0,194,249)', 'rgb(255,178,253)', 'rgb(164,1,34)', 'rgb(226,1,52)', 'rgb(255,110,58)', 'rgb(255,195,59)'];

    var labels = [].concat.apply([], [["Timestamp"], Object.values(column_mapper.levels).filter(distinct)]);
    var config = {
        labelsDiv: legend_div,
        drawPoints: true,
        strokeWidth: 0,
        pointSize: 4,
        highlightSeriesBackgroundAlpha: 0.25,
        highlightSeriesOpts: {
            strokeWidth: 0,
            strokeBorderWidth: 2,
            highlightCircleSize: 6
        },
        highlightCircleSize: 6,
        labels: labels,
        colors: colors,
        legend: "always",
        legendFormatter: function (data) {
            if (data.x == null) {
                // This happens when there's no selection and {legend: 'always'} is set.
                return data.series.map(function (series) {
                    return series.dashHTML +
                        ' <span style="font-weight: bold; color: ' + series.color + '">' + series.labelHTML + '</span>'
                }).join(' ');
            }

            var html = this.getLabels()[0] + ': ' + data.xHTML;
            data.series.forEach(function (series) {
                if (!series.isVisible) return;
                var labeledData = series.labelHTML + ': ' + series.yHTML;
                if (series.isHighlighted) {
                    labeledData = '<span style="font-weight: bold; color: ' + series.color + '">' + series.labelHTML + "(" + value_mapper.levels[Math.round(series.yHTML)] + ')</span>';
                }
                html += '<br>' + series.dashHTML + ' ' + labeledData;
            });
            return html;
        },
        dateWindow: [new Date(begin).getTime(), new Date(end).getTime()],
        axes: {
            x: {

                axisLabelWidth: 75,
                axisLabelFormatter: function (d) {
                    return d.getFullYear() + "-" + jlab.wfb.pad(d.getMonth() + 1, 2, '0') + "-" + jlab.wfb.pad(d.getDate(), 2, '0') + "\n"
                        + jlab.wfb.pad(d.getHours(), 2, '0') + ":" + jlab.wfb.pad(d.getMinutes(), 2, '0') + ":"
                        + jlab.wfb.pad(d.getSeconds(), 2, '0') + "." + d.getMilliseconds();
                }
            },
            y: {
                // axisLabelFormatter: number_to_cavity,
                ticker: value_mapper.ticker
            },
        },
        valueRange: [-0.5, value_mapper.levels.length + 0.5],
        plugins: [doubleClickZoomOutPlugin]
    };

    return new Dygraph(plot_div, data, config);
};


// Produces one heatmap for each facet in the data.
// div - HTML container element for the plot
// data - An object where each top level key points to a 2D array of count data to be used as a heatmap
// column_mapper - A Categorizer object for describing the columns
// row_mapper - A Categorizer object for describing the rows
jlab.wfb.plot_heatmaps = function (div, data, cavity_mapper, fault_mapper) {
    var num_plots = 0;
    var max = 1; // Set to one since we always want to have a zero-bounded scale and plotly updates the scale if min=max
    for (var key in data) {
        if (data.hasOwnProperty(key)) {
            num_plots += 1;
            for (var i = 0; i < data[key].length; i++) {
                for (var j = 0; j < data[key][i].length; j++) {
                    if (max < data[key][i][j]) {
                        max = data[key][i][j];
                    }
                }
            }
        }
    }

    // Determine layout for plots, at most 4 per row, with 1% spacing on either side of the plots
    var plots_per_row = 2;
    var plot_width = Math.floor((1 / Math.min(num_plots, plots_per_row) * 100)) - 2;

    var plot = 1;
    var row_div = document.createElement("div");
    row_div.classList.add("heatmap-plot-row");
    div.appendChild(row_div);
    var x = cavity_mapper.levels.map(function (level) {
        return isNaN(level) ? level : "cav " + level;
    });
    var y = fault_mapper.levels;
    var y_values = new Array(y.length);
    for (var i = 0; i < y.length; i++) {
        y_values[i] = i;
    }

    Object.keys(data).sort().forEach(function (key) {
        if (data.hasOwnProperty(key)) {
            if (plot > plots_per_row) {
                plot = plot - plots_per_row;
                row_div = document.createElement("div");
                row_div.classList.add("heatmap-plot-row");
                div.appendChild(row_div);
            }

            var plot_div = row_div.appendChild(document.createElement('div'));
            plot_div.style.width = plot_width + '%';

            // Modified Plotly "Blues" scale that removes some of the continuous gradation at both extremes.  The idea
            // is that we want to detect non-zero values easily, and anything at the high end requires attention.  This
            // scheme focuses nuanced differentiation in the middle, where users may want to apply some individual
            // judgement.
            var blue_scale = [[0, 'rgb(247,247,255)'], [1 / 4, 'rgb(158,202,225)'], [2 / 4, 'rgb(107,174,214)'],
                [3 / 4, 'rgb(8,81,156)'], [1, 'rgb(8,48,107)']]

            var plot_data = [
                {
                    x: x,
                    y: y,
                    z: data[key],
                    xgap: 1,
                    ygap: 1,
                    zmin: 0,
                    zmax: max,
                    type: 'heatmap',
                    hoverongaps: false,
                    showscale: true,
                    colorscale: blue_scale
                }
            ];
            var layout = {
                margin: {
                    t: 30
                },
                title: {
                    text: key,
                    y: 0.97,
                    x: 0.5,
                    xanchor: 'left'
                },
                yaxis: {
                    showticklabels: true,
                    ticktext: y,
                    tickvals: y_values,
                    tickmode: 'array',
                    ticks: '',
                    automargin: true
                }
            };

            var config = {
                responsive: true,
                displayModeBar: false
            };

            Plotly.newPlot(plot_div, plot_data, layout, config);
            plot += 1;
        }
    });
};

jlab.wfb.create_plots = function (event_data, dp_div, heatmap_div, labeled_only, facet_on, report_mode, locations,
                                  begin, end) {
    if (report_mode == "zone") {
        var cf_data = jlab.wfb.process_event_data(event_data, "fault", 'cavity', fault_mapper, cavity_mapper, labeled_only);
        jlab.wfb.plot_dotplot(dp_div, cf_data, fault_mapper, cavity_mapper, "Fault Timeline", begin, end);
    } else {
        var fault_data = jlab.wfb.process_event_data(event_data, "fault", 'zone', fault_mapper, zone_mapper, labeled_only);
        var g1 = jlab.wfb.plot_dotplot(dp_div, fault_data, fault_mapper, zone_mapper, "Fault Types By Zone", begin, end);

        var cavity_data = jlab.wfb.process_event_data(event_data, "cavity", 'zone', cavity_mapper, zone_mapper, labeled_only);
        var g2 = jlab.wfb.plot_dotplot(dp_div, cavity_data, cavity_mapper, zone_mapper, "Cavity By Zone", begin, end);

        Dygraph.synchronize([g1, g2], {selection: false});

        // For some reason the top chart draw itself too wide the first time in chrome.  Resizing the window seems to
        // fix it.  Dispatching a resize event will trigger a redraw at the proper size.
        window.dispatchEvent(new Event('resize'));
    }

    var heatmaps = jlab.wfb.process_event_data_to_heatmaps(event_data, cavity_mapper, fault_mapper, labeled_only, facet_on, locations);
    jlab.wfb.plot_heatmaps(heatmap_div, heatmaps, cavity_mapper, fault_mapper);
};