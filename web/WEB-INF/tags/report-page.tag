<%@tag description="The Report Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<%@attribute name="title"%>
<%@attribute name="stylesheets" fragment="true"%>
<%@attribute name="scripts" fragment="true"%>
<t:page title="Reports - ${title}"> 
    <jsp:attribute name="stylesheets">       
        <jsp:invoke fragment="stylesheets"/>
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <jsp:invoke fragment="scripts"/>
    </jsp:attribute>    
    <jsp:body>
        <div id="two-columns">
            <div id="left-column">
                <section>
                    <h2 id="left-column-header">Reports</h2>
                    <nav id="secondary-nav">
                        <ul>
                            <li${'/reports/report-one' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/reports/report-one">Report One</a></li>
                            <li${'/reports/report-two' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/reports/report-two">Report Two</a></li>
                            <li${'/reports/report-three' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/reports/report-three">Report Three</a></li>                      
                        </ul>
                    </nav>
                </section>
            </div>
            <div id="right-column">
                <jsp:doBody/>
            </div>
        </div>       
    </jsp:body>         
</t:page>
