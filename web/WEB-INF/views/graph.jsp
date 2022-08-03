<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Graph - ${requestScope.systemDisplay}"/>
<t:page title="${title}">  
    <jsp:attribute name="stylesheets">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/dygraph.2.1.0.css" />
        <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/vis.min.css">
        <style>
            form fieldset {
                margin: 2px;
            }
            input[type="submit"] {
                display: block;
            }
            .key-value-list {
                display: inline-block;
                vertical-align: top;
            }
            /*Make the selected items pop a bit more on the timeline*/
            .vis-item.vis-point.vis-selected {
                background-color: transparent;
            }
            .vis-item.vis-dot.vis-selected {
                top: 0px;
                border-color: black;
                border-width: 2px;
                padding: 3px;
                background-color: yellow;
            }
            .graph-panel-date {
                margin-left: 5px;
                margin-right: 5px;
            }
            .vis-item {
                cursor: pointer;
            }
            /* The timeline selected dots have a z-index of 2 and appear over top of the date picker div.  Push this to the foreground */
            .ui-datepicker {
                z-index: 2 !important;
            }
            /*#graph-panel .graph-panel-action-controls .help-dialog {*/
            .help-dialog {
                position: absolute;
                z-index:3;
                top: 110%;
                width: 14em;
                background-color: mintcream;
                box-shadow: 8px 8px 8px #979797;
                border-radius: 8px 8px 8px 8px;
                padding: 4px;
                border: 1px solid black;
                text-align: left;
                font-size: 80%;
                right: -10px;
            }

            .secondary-nav-horizontal {
                /*border: 1px solid black;*/
                /*border-bottom: 1px solid black;*/
                /*border-top: 1px solid black;*/
                /*padding: 1px 2px 2px 1px;*/
                float: right;       
            }

            .secondary-nav-horizontal ul {
                padding: 0 4px;
                list-style-type: none;
                margin: 0 0 0 16px;
                display: inline-block;
            }

            .secondary-nav-horizontal li.current-primary {
                /*border-bottom: 1px solid #fff;*/
                border-radius: 3px;
                padding: 1px;
                border: 1px solid black;
                background-color: lightgray;
            }

            .secondary-nav-horizontal li {           
                padding: 3px;
                background-color: #fff;              
                display: inline-block;
            }

            .content-header {
                position: relative;
            }
            #help-container {
                display: inline-block;
                float: right;
                vertical-align: bottom;
            }
        </style>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph.2.1.0.min.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph-synchronizer.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph-crosshair.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/vis.min.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/graph.js"></script>
    </jsp:attribute>
    <jsp:body>
        <section>
            <div class="content-header">
                <h2 id="page-header-title"><c:out value="${title}"/></h2>
                <div id="help-container">
                    <button class="help">Help</button>
                    <div class='help-dialog' style='display: none;'>TIMELINE CONTROLS<hr>Zoom: Control-Scroll<br>Pan: Click-Drag<br>Select Event: Click</div>
                </div>
                <nav class="secondary-nav-horizontal">
                    System View:
                    <ul>
                        <li${'rf' eq requestScope.system ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/graph?system=rf&amp;begin=&amp;end=">RF</a>
                        </li>  
                        <li${'acclrm' eq requestScope.system ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/graph?system=acclrm&amp;begin=&amp;end=">Accelerometer</a>
                        </li>  
                    </ul>
                </nav>
            </div>
            <div id="timeline-container"></div>
            <form id="page-contrlols-form" method="GET" action="${pageContext.request.contextPath}/graph" autocomplete="off">
                <input type="hidden" name="system" value="${requestScope.system}"/>
                <fieldset>
                    <fieldset>
                        <legend>Timeline</legend>
                        <ul class="key-value-list">
                            <li>
                                <div class="li-key"><label class="required-field" for="begin" title="Earliest time to display">Start</label></div>
                                <div class="li-value"><input type="text" id="start-date-picker" class="date-time-field" name="begin" placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                            </li>
                            <li>
                                <div class="li-key"><label class="required-field" for="end" title="Latest time to display.">End</label></div>
                                <div class="li-value"><input type="text" id="end-date-picker" class="date-time-field" name="end" placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                            </li>
                        </ul>
                        <ul class="key-value-list">
                            <li>
                                <div class="li-key"><label class="required-field" for="locations" title="Include on the following locations.">Zone</label></div>
                                <div class="li-value">
                                    <select id="location-selector" name="location" multiple>
                                        <c:forEach var="location" items="${requestScope.locationMap}">
                                            <option value="${location.key}" label="${location.key}" <c:if test="${location.value}">selected</c:if>>${location.key}</option>
                                        </c:forEach>
                                    </select>
                                </div>
                            </li>
                        </ul>
                        <c:choose>
                            <c:when test="${requestScope.system == 'acclrm'}">
                                <ul class="key-value-list">
                                    <li>
                                        <div class="li-key"><label for="classification" title="Include only events with the following classification(s).  Emply implies no filter">Classification</label></div>
                                        <div class="li-value">
                                            <select id="classification-selector" name="classification" multiple>
                                                <c:forEach var="cls" items="${requestScope.classificationMap}">
                                                    <option value="${cls.key}" label="${cls.key}" <c:if test="${cls.value}">selected</c:if>>${cls.key}</option>
                                                </c:forEach>
                                            </select>
                                        </div>
                                    </li>
                                </ul>
                            </c:when>
                            <c:when test="${requestScope.system == 'rf'}">
                                <ul class="key-value-list">
                                    <li>
                                        <div class="li-key"><label for="minCF" title="Include events with data for at least this many cavities. Empty implies no filter.">Min #Cavities</label></div>
                                        <div class="li-value">
                                            <input type="text" name="minCF" value="${requestScope.minCF}">
                                        </div>
                                    </li>
                                </ul>
                            </c:when>
                        </c:choose>
                    </fieldset>
                    <fieldset>
                        <legend>Graph</legend>
                        <ul class="key-value-list">
                            <li>
                                <div class="li-key"><label for="series" title="Show charts for these series below.">Series</label></div>
                                <div class="li-value">
                                    <select id="series-selector" name="series" multiple>
                                        <c:forEach var="series" items="${requestScope.seriesMap}">
                                            <option value="${series.key}" label="${series.key}" <c:if test="${series.value}">selected</c:if>>${series.key}</option>
                                        </c:forEach>
                                    </select>
                                </div>
                            </li>
                        </ul>
                        <ul class="key-value-list">
                            <li>
                                <div class="li-key"><label for="series-sets" title="Show charts for these named sets of series below.">Series Sets</label></div>
                                <div class="li-value">
                                    <select id="series-set-selector" name="seriesSet" multiple>
                                        <c:forEach var="seriesSet" items="${requestScope.seriesSetMap}">
                                            <option value="${seriesSet.key}" label="${seriesSet.key}" <c:if test="${seriesSet.value}">selected</c:if>>${seriesSet.key}</option>
                                        </c:forEach>
                                    </select>
                                </div>
                            </li>
                        </ul>
                    </fieldset>
                    <input id="page-controls-submit" type="submit" value="Submit"/><span id="page-controls-error"></span>
                </fieldset>
            </form>
            <hr/>
            <div id="graph-panel" style="width:100%;"></div>
        </section>
        <script>
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {}
            ;
                    jlab.wfb.locationSelections = [<c:forEach var="location" items="${locationSelections}" varStatus="status">'${location}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                    jlab.wfb.locationToGroupMap = new Map([<c:forEach var="index" begin="0" end="${fn:length(locationSelections)-1}">['${locationSelections.get(index)}', '${index}']<c:if test="${index != (fn:length(locationSelections)-1)}">,</c:if></c:forEach>]);
            jlab.wfb.begin = "${requestScope.begin}";
            jlab.wfb.end = "${requestScope.end}";
            jlab.wfb.system = "${requestScope.system}";
            jlab.wfb.eventArray = ${requestScope.eventListJson};
            jlab.wfb.eventArray = jlab.wfb.eventArray.events;
            jlab.wfb.minCF = "${requestScope.minCF}";
            jlab.wfb.currentEvent = ${requestScope.currentEvent} || {}
            ;
                    jlab.wfb.seriesSelections = [<c:forEach var="series" items="${seriesSelections}" varStatus="status">'${series}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                    jlab.wfb.seriesSetSelections = [<c:forEach var="seriesSet" items="${seriesSetSelections}" varStatus="status">'${seriesSet}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                    jlab.wfb.seriesMasterSet = [<c:forEach var="series" items="${seriesMasterSet}" varStatus="status">'${series}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                </script>
    </jsp:body>  
</t:page>
