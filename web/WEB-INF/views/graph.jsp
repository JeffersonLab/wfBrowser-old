<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Graph"/>
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
            #graph-panel .graph-panel-action-controls .help-dialog {
                position: absolute;
                z-index:3;
                top: 110%;
                width: 14em;
                background-color: mintcream;
                border-radius: 8px 8px 8px 8px;
                padding: 4px;
                border: 1px solid black;
                text-align: left;
                font-size: 80%;
            }

        </style>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph.2.1.0.min.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph-synchronizer.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/vis.min.js"></script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/graph.js"></script>
    </jsp:attribute>
    <jsp:body>
        <section>
            <h2 id="page-header-title"><c:out value="${title}"/></h2>
            <div id="timeline-container"></div>
            <form id="page-contrlols-form" method="GET" action="${pageContext.request.contextPath}/graph">
                <fieldset>
                    <ul class="key-value-list">
                        <li>
                            <div class="li-key"><label class="required-field" for="begin">Start</label></div>
                            <div class="li-value"><input type="text" id="start-date-picker" class="date-time-field" name="begin" placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                        </li>
                        <li>
                            <div class="li-key"><label class="required-field" for="end">End</label></div>
                            <div class="li-value"><input type="text" id="end-date-picker" class="date-time-field" name="end" placeholder="yyyy-mm-dd HH:mm:ss.S"/></div>
                        </li>
                    </ul>
                    <ul class="key-value-list">
                        <li>
                            <div class="li-key"><label class="required-field" for="locations">Zone</label></div>
                            <div class="li-value">
                                <select id="location-selector" name="location" multiple>
                                    <c:forEach var="location" items="${requestScope.locationMap}">
                                        <option value="${location.key}" label="${location.key}" <c:if test="${location.value}">selected</c:if>>${location.key}</option>
                                    </c:forEach>
                                </select>
                            </div>
                        </li>
                    </ul>
                    <ul class="key-value-list">
                        <li>
                            <div class="li-key"><label for="series">Series</label></div>
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
                            <div class="li-key"><label for="series-sets">Series Sets</label></div>
                            <div class="li-value">
                                <select id="series-set-selector" name="seriesSet" multiple>
                                    <c:forEach var="seriesSet" items="${requestScope.seriesSetMap}">
                                        <option value="${seriesSet.key}" label="${seriesSet.key}" <c:if test="${seriesSet.value}">selected</c:if>>${seriesSet.key}</option>
                                    </c:forEach>
                                </select>
                            </div>
                        </li>
                    </ul>
                    <input id="page-controls-submit" type="submit" value="Submit"/><span id="page-controls-error"></span>
                </fieldset>
            </form>
            <hr/>
            <div id="graph-panel" style="width:100%;"></div>
        </section>
        <script>
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {};
            jlab.wfb.eventId = "${requestScope.eventId}";
                    jlab.wfb.locationSelections = [<c:forEach var="location" items="${locationSelections}" varStatus="status">'${location}'<c:if test="${!status.last}">,</c:if></c:forEach>];
            jlab.wfb.begin = "${requestScope.begin}";
            jlab.wfb.end = "${requestScope.end}";
            jlab.wfb.eventArray = ${requestScope.eventListJson};
            jlab.wfb.eventArray = jlab.wfb.eventArray.events;
            jlab.wfb.currentEvent = ${requestScope.currentEvent} || {}
            ;
                    jlab.wfb.seriesSelections = [<c:forEach var="series" items="${seriesSelections}" varStatus="status">'${series}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                    jlab.wfb.seriesSetSelections = [<c:forEach var="series" items="${seriesSetSelections}" varStatus="status">'${seriesSet}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                    jlab.wfb.seriesMasterSet = [<c:forEach var="series" items="${seriesMasterSet}" varStatus="status">'${series}'<c:if test="${!status.last}">,</c:if></c:forEach>];
                </script>
    </jsp:body>  
</t:page>
