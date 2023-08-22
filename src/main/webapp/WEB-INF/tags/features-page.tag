<%@tag description="The Feature Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<%@attribute name="title"%>
<%@attribute name="stylesheets" fragment="true"%>
<%@attribute name="scripts" fragment="true"%>
<t:page title="Features - ${title}"> 
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
                    <h2 id="left-column-header">Features</h2>
                    <nav id="secondary-nav">
                        <ul>
                            <li${'/features/multiselect-datatable' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/multiselect-datatable">Multiselect Datatable</a></li>
                            <li${'/features/single-select-datatable' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/single-select-datatable">Single Select Datatable</a></li>
                            <li${'/features/autocomplete' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/autocomplete">Autocomplete Input</a></li>
                            <li${'/features/parameter-persistence' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/parameter-persistence">Parameter Persistence</a></li>  
                            <li${'/features/bracket-nav' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/bracket-nav">Bracket Navigation</a></li>
                            <li${'/features/flyout-nav' eq currentPath ? ' class="current-secondary"' : ''}><a href="${pageContext.request.contextPath}/features/flyout-nav">Flyout Menu Navigation</a></li>
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
