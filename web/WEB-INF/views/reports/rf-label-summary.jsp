<%@page contentType="text/html" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="title" value="RF Fault Summary"/>
<t:report-page title="${title}">
    <jsp:attribute name="stylesheets">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/dygraph.2.1.0.css" />
        <style>
            /*Plotly svg element has some overhang on initial draw.  This 99% helps hide that fact.*/
            .dotplot-container {
                width: 97%;
                height: 150px;
                justify-content: center;
                display: grid;
                grid-template-columns: 49% 49%;
                grid-gap: 4px;
                gap: 4px;
                padding-bottom: 25px;
            }

            .dotplot-wrapper {
                padding: 2px;
                padding-bottom: 10px;
            }

            .dotplot-legend {
                margin: auto;
                text-align: center;
                height: 2.5em;
                font-size: 12px;
            }


            .key-value-list {
                display: inline-block;
                vertical-align: top;
            }

            .chart-title {
                text-align: center;
                font-weight: bold;
            }

            #heatmaps-container .js-plotly-plot {
                display: inline-block;
                height: 100%;
            }

            .heatmap-plot-row {
                height: 250px;
            }

            input[type="submit"] {
                display: block;
            }

        </style>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/dygraph.2.1.0.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/plotly-v1.50.1.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/rf_label_summary.js"></script>

        <script>
            // Function for processing control-form on submit
            var submitHandler = function () {
                // function submitHandler(event){
                // Servlet is expecting a boolean, but we show a checkbox widget which sends "on" or nothing.
                // Update the hidden field that is associated with this checkbox.
                var isLabeledCheckBox = document.getElementById("isLabeled-checkbox-input");
                var isLabeled = document.getElementById("isLabeled-input");
                isLabeled.setAttribute("value", isLabeledCheckBox.checked);
            };

            // Setup the form's submit handler
            var form = document.getElementById('control-form');

            form.addEventListener('submit', submitHandler);

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

            jlab.wfb.ready_callback = function () {
                var dp_div = document.getElementById("dotplot-panel");
                var hm_div = document.getElementById("heatmaps-container");
                jlab.wfb.create_plots(jlab.wfb.events, dp_div, hm_div, jlab.wfb.isLabeled,
                    jlab.wfb.reportMode, jlab.wfb.reportMode, jlab.wfb.locationSelections);
                var done_span = document.createElement("span");
                done_span.classList.add("done");
                document.body.appendChild(done_span);
            };

            if (
                document.readyState === "complete" ||
                (document.readyState !== "loading" && !document.documentElement.doScroll)
            ) {
                jlab.wfb.ready_callback();
            } else {
                document.addEventListener("DOMContentLoaded", jlab.wfb.ready_callback);
            }
        </script>

    </jsp:attribute>
    <jsp:body>
        <h2>RF Fault Summary</h2>
        <form id="control-form">
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
                            <input id="conf-input" type="text" size=5 name="conf" value="${confString}"
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
                    <li>
                        <div class="li-key"><label for="isLabeled-checkbox-input">Labeled Only</label></div>
                        <div class="li-value"><input id="isLabeled-checkbox-input" type="checkbox"
                                                     <c:if test="${requestScope.isLabeled}">checked</c:if> ></div>
                        <input id="isLabeled-input" name="isLabeled" type="hidden">
                    </li>
                    <li>
                        <div class="li-key"><label for="reportMode-input">Report Mode</label></div>
                        <div class="li-value">
                            <select id="reportMode-input" name="reportMode">
                                <option value="all" <c:if test="${reportMode == 'all'}">selected</c:if>>All</option>
                                <option value="linac" <c:if test="${reportMode == 'linac'}">selected</c:if>>Linac
                                </option>
                                <option value="zone" <c:if test="${reportMode == 'zone'}">selected</c:if>>Zone</option>
                            </select>
                        </div>
                    </li>
                </ul>
                <input type="submit" value="Submit">
            </fieldset>

        </form>
        <div id="dotplot-panel"></div>
        <hr/>
        <div id="heatmaps-panel">
            <div id="heatmaps-title" class="chart-title">Fault vs Cavity Labels</div>
            <div id="heatmaps-container"></div>
        </div>
        <script>
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {};

            jlab.wfb.events = ${requestScope.events};
            jlab.wfb.reportMode = "${requestScope.reportMode}";
            jlab.wfb.isLabeled = ${requestScope.isLabeled};
            jlab.wfb.begin = "${requestScope.beginString}";
            jlab.wfb.end = "${requestScope.endString}";
            jlab.wfb.locationSelections = [<c:forEach var="location" items="${locationSelections}" varStatus="status">'${location}'<c:if test="${!status.last}">, </c:if></c:forEach>];
        </script>
    </jsp:body>
</t:report-page>
