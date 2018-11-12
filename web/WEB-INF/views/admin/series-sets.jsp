<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Series Sets Management"/>
<t:admin-page title="${title}">  
    <jsp:attribute name="stylesheets">
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/series-sets.js"></script>
    </jsp:attribute>        
    <jsp:body>
        <section>
            <h2 id="page-header-title"><c:out value="${title}"/></h2>
            <%--<c:if test="${fn:length(seriesList) > 0}">--%>
            <t:editable-row-table-controls excludeAdd="${false}" excludeDelete="${false}" excludeEdit="${false}"/>
            <div id="chart-wrap" class="chart-wrap-backdrop">
                <table class="data-table stripped-table uniselect-table editable-row-table">
                    <thead>
                        <tr>
                            <th>Series Set Name</th>
                            <th>Description</th>
                            <th>Series</th>
                            <th>System</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach items="${seriesSetList}" var="seriesSet">
                            <tr data-series-set-id="${seriesSet.id}">
                                <td ><c:out value="${seriesSet.name}"/></td>
                                <td><c:out value="${seriesSet.description}"/></td>
                                <td><c:forEach var="series" items="${seriesSet.set}" varStatus="status">${series.name}<c:if test="${!status.last}">,</c:if></c:forEach></td>
                                <td><c:out value="${seriesSet.systemName}"/>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
            <%--</c:if>--%>            

            <t:editable-row-table-dialog>
                <form id="row-form">
                    <ul class="key-value-list">
                        <li>
                            <div class="li-key"><label class="required-field" for="name">Series Name</label></div>
                            <div class="li-value"><input type="text" id="row-name" name="name" placeholder="Ex. GMES"/></div>
                        </li>
                        <li>
                            <div class="li-key"><label class="required-field" for="description">Description</label></div>
                            <div class="li-value"><input type="text" id="row-description" name="description"/></div>
                        </li>                                       
                        <li>
                            <div class="li-key"><label class="required-field" for="set">Series Names</label></div>
                            <div class="li-value"><input type="text" id="row-set" name="set" placeholder="Ex. GMES,PMES"/></div>
                        </li>                                       
                    </ul>
                </form>
            </t:editable-row-table-dialog>
        </section>
    </jsp:body>  
</t:admin-page>
