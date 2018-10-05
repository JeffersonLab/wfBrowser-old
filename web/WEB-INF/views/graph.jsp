<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Overview"/>
<t:page title="${title}">  
    <jsp:attribute name="stylesheets">
        <link rel="stylesheet"  href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/dygraph.2.1.0.css" />
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph.2.1.0.min.js"></script>

        <script type="text/javascript">

            // These get set by updateZoneSelector, and used by updateEventSelector
            var begin, end;

            jlab.wfb.updateSeriesSelector = function () {
                var eventId = $("#event-selector").find(":selected").val();
                console.log(eventId);
                var url = jlab.contextPath + "/ajax/event-series";
                var data = {"id": eventId};
                console.log(data);
                var promise = jlab.doAjaxJsonGetRequest(url, data);

                return promise.then(function (xhr) {
                    var series = xhr.series;
                    $("#series-selector").html("");
                    for (var i = 0; i < series.length; i++) {
                        $("#series-selector").append("<option value=" + series[i] + ">" + series[i] + "</option>");
                    }
                });
            };

            jlab.wfb.updateEventSelector = function () {
                if (typeof begin === "undefined" || typeof end === "undefined") {
                    return;
                }
                var url = jlab.contextPath + "/ajax/event";
                var location = $("#zone-selector").find(":selected").val();
                var data = {"begin": begin, "end": end, "includeData": "false", "location": location};
                var promise = jlab.doAjaxJsonGetRequest(url, data);
                var eventTimes = new Map();

                return promise.then(function (xhr) {
                    var events = xhr.events;
                    for (var i = 0; i < events.length; i++) {
                        var datetime = new Date(events[i].datetime_utc);
                        eventTimes.set(events[i].id, datetime);
                    }

                    eventIds = Array.from(eventTimes.keys());
                    $("#event-selector").html("");
                    for (var i = 0; i < eventIds.length; i++) {
                        $('#event-selector').append("<option value=" + eventIds[i] + ">"
                                + jlab.dateToDateTimeString(eventTimes.get(eventIds[i])) + "</option>");
                    }
                    return jlab.wfb.updateSeriesSelector();
                });
            };

            jlab.wfb.updateZoneSelector = function () {

                var values = $("#date-picker").val().split('-');
                var year = values[0];
                var month = values[1] - 1;
                var day = values[2];

                // When created this way, Date object is supposed to in the system default timezone offset
                var beginDate = new Date(year, month, day);
                var endDate = new Date(year, month, day);
                endDate.setDate(beginDate.getDate() + 1);
                begin = jlab.dateToDateTimeString(beginDate);
                end = jlab.dateToDateTimeString(endDate);

                var url = jlab.contextPath + "/ajax/event";
                var data = {"begin": begin, "end": end, "includeData": "false"};

                var promise = jlab.doAjaxJsonGetRequest(url, data);
                var locations = new Set();

                return promise.then(function (xhr) {
                    var events = xhr.events;
                    for (var i = 0; i < events.length; i++) {
                        locations.add(events[i].location);
                    }
                    locations = Array.from(locations);
                    $("#zone-selector").html("");
                    for (var i = 0; i < locations.length; i++) {
                        $('#zone-selector').append("<option value=" + locations[i] + ">" + locations[i] + "</option>");
                    }
                    return jlab.wfb.updateEventSelector();
                });
            };

            $(function () {
                $(".date-field").datepicker({
                    dateFormat: 'yy-mm-dd'
                });
                // Update down stream menu options when the date selector changes

                $("#date-picker").on("change", jlab.wfb.updateZoneSelector);
                $("#zone-selector").on("change", jlab.wfb.updateEventSelector);
                $("#event-selector").on("change", jlab.wfb.updateSeriesSelector);

        $("#date-picker").val(jlab.wfb.date);
                jlab.wfb.updateZoneSelector().done(function () {
                    $("#zone-selector").val(jlab.wfb.location);
                    jlab.wfb.updateEventSelector().done(function () {
                        $("#event-selector").val(jlab.wfb.eventId);
                        jlab.wfb.updateSeriesSelector().done(function () {
                            $("#series-selector").val(jlab.wfb.series);
                        });
                    });
                });
                if (typeof jlab.wfb.eventId !== "undefined" && jlab.wfb.eventId !== null && jlab.wfb.eventId !== "") {
                    var seriesParams = "";
                    if (jlab.wfb.series.length > 0) {
                        seriesParams = "&series=" + jlab.wfb.series.join("&series=");
                    }

                    var $graphPanel = $("#graph-panel");
                    var graphOptions = {
                        legend: "always",
                        hideOverlayOnMouseOut: false,
                        labelsSeparateLines: true,
                        highlightSeriesOpts: {
                            strokeWidth: 2,
                            strokeBorderWidth: 1,
                            highlightCircleSize: 5
                        }
                    };
                    for (var i = 0; i < jlab.wfb.series.length; i++) {
                        $graphPanel.append("<div class=graph-container><div id=graph-chart-" + i + " style='width: 100%'></div><div id=graph-legend-" + i + " ></div></div>");
                        graphOptions.labelsDiv = document.getElementById("graph-legend-" + i);

                        g = new Dygraph(
                                // containing div
                                document.getElementById("graph-chart-" + i),
                                "/wfbrowser/ajax/event?id=" + jlab.wfb.eventId + "&includeData=true&out=csv&series=" + jlab.wfb.series[i].encodeXml(),
                                graphOptions
                                );
                    }
                }
            });
        </script>
    </jsp:attribute>
    <jsp:body>
        <section>
            <h2 id="page-header-title"><c:out value="${title}"/></h2>
            <form method="GET" action="${pageContext.request.contextPath}/graph">
                Date: <input type="text" id="date-picker" class="date-field" name="date" placeholder="yyyy-mm-dd"/>
                Zone: <select id="zone-selector" name="location"></select>
                Event: <select id="event-selector" name="eventId"></select>
                Series: <select id="series-selector" name="series" multiple></select>
                <input type="submit"/>
            </form>
            <div id="graph-panel" style="width:100%;"></div>
        </section>
        <script>
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {};
            jlab.wfb.eventId = "${requestScope.eventId}";
            jlab.wfb.location = "${requestScope.location}";
            jlab.wfb.date = "${requestScope.date}";
            jlab.wfb.series = [<c:forEach var="series" items="${seriesList}" varStatus="status">'${series}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                </script>
    </jsp:body>  
</t:page>
