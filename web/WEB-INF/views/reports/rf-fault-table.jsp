<%@page contentType="text/html" pageEncoding="UTF-8" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:url var="domainRelativeReturnUrl" scope="request" context="/"
       value="${requestScope['javax.servlet.forward.request_uri']}${requestScope['javax.servlet.forward.query_string'] ne null ? '?'.concat(requestScope['javax.servlet.forward.query_string']) : ''}"/>
<c:set var="title" value="RF Fault Table"/>
<t:report-page title="${title}">
    <jsp:attribute name="stylesheets">
        <style>
            .key-value-list {
                display: inline-block;
                vertical-align: top;
            }

            input[type="submit"] {
                display: block;
            }

            #download-wrapper {
                position: relative;
            }

            #download-button a {
                position: absolute;
                right: 10px;
                bottom: -5px;
                border: gray 1px solid;
                background-color: lightgray;
                color: #1a1a1a;
                padding: 2px;
                float: right;
                box-shadow: #aaaaaa;
            }

            #download-button a:hover {
                background-color: aliceblue;
                border: deepskyblue 1px solid;
            }

            #download-button a:active {
                background-color: lightblue;
                border: deepskyblue 1px solid;
            }
        </style>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script>
            // Setup variables that are loaded from the controller / HTTP request
            var jlab = jlab || {};
            jlab.wfb = jlab.wfb || {};

            jlab.wfb.isLabeled = ${requestScope.isLabeled};
            jlab.wfb.begin = "${requestScope.beginString}";
            jlab.wfb.end = "${requestScope.endString}";
            jlab.wfb.locationSelections = [<c:forEach var="location" items="${locationSelections}" varStatus="status">'${location}'<c:if test="${!status.last}">, </c:if></c:forEach>];

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

            // Setup the  UI elements
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
        <h2>RF Fault Table</h2>
        <div id="download-wrapper">
            <c:set var="fBegin" value="${fn:replace(beginString, ' ', '_')}"></c:set>
            <c:set var="fEnd" value="${fn:replace(endString, ' ', '_')}"></c:set>
            <div id="download-button">
                <a href="${domainRelativeReturnUrl}&out=csv" download="rf-faults-${fBegin}-${fEnd}.csv">Download CSV</a>
            </div>
        </div>
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
                    <li>
                        <div class="li-key"><label for="isLabeled-checkbox-input">Labeled Only</label></div>
                        <div class="li-value"><input id="isLabeled-checkbox-input" type="checkbox"
                                                     <c:if test="${requestScope.isLabeled}">checked</c:if> ></div>
                        <input id="isLabeled-input" name="isLabeled" type="hidden">
                    </li>
                </ul>
                <input type="submit" value="Submit">
            </fieldset>

        </form>
        <div id="table-wrapper">
            <c:choose>
                <c:when test="${empty eventList}">
                    <center><strong>No Events Found</strong></center>
                </c:when>
                <c:otherwise>
                    <table class="data-table stripped-table">
                        <thead>
                        <tr>
                            <th></th>
                            <th>Timestamp</th>
                            <th>Location</th>
                            <th>Cavity Label</th>
                            <th>Cavity Confidence</th>
                            <th>Fault Type Label</th>
                            <th>Fault Type Confidence</th>
                            <th>Label Model</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="event" items="${eventList}">
                            <tr>
                                <td><a href="${pageContext.request.contextPath}/graph?eventId=${event.eventId}">View</span></a></td>
                                <td>${event.eventTimeStringLocal}</td>
                                <td>${event.location}</td>
                                <c:choose>
                                    <c:when test="${event.labelList == null || empty event.labelList}">
                                        <td>N/A</td>
                                        <td>N/A</td>
                                        <td>N/A</td>
                                        <td>N/A</td>
                                        <td>N/A</td>
                                    </c:when>
                                    <c:otherwise>
                                        <c:forEach var="label" items="${event.labelList}">
                                            <c:choose>
                                                <c:when test="${label.name == 'cavity'}">
                                                    <c:set var="cavLabel" value="${label.value}"></c:set>
                                                    <c:set var="cavConf" value="${label.confidence}"></c:set>
                                                    <c:set var="cavModel" value="${label.modelName}"></c:set>
                                                </c:when>
                                                <c:when test="${label.name == 'fault-type'}">
                                                    <c:set var="faultLabel" value="${label.value}"></c:set>
                                                    <c:set var="faultConf" value="${label.confidence}"></c:set>
                                                    <c:set var="faultModel" value="${label.modelName}"></c:set>
                                                </c:when>
                                            </c:choose>
                                        </c:forEach>
                                        <td>${cavLabel}</td>
                                        <td><fmt:formatNumber type="number" maxFractionDigits="2" minFractionDigits="2"
                                                              value="${cavConf}"></fmt:formatNumber></td>
                                        <td>${faultLabel}</td>
                                        <td><fmt:formatNumber type="number" maxFractionDigits="2" minFractionDigits="2"
                                                              value="${faultConf}"></fmt:formatNumber></td>
                                        <c:choose>
                                            <c:when test="${cavModel != faultModel}">
                                                <td>${cavModel}/${faultModel}</td>
                                            </c:when>
                                            <c:otherwise>
                                                <td>${cavModel}</td>
                                            </c:otherwise>
                                        </c:choose>
                                    </c:otherwise>
                                </c:choose>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>
    </jsp:body>
</t:report-page>
