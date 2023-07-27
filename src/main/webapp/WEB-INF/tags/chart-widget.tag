<%@tag description="Chart Widget" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<div class="chart-legend-panel">
    <div class="chart-panel">
        <div id="chart-wrap">
            <div id="chart-placeholder"></div>
        </div>
    </div>
    <div class="legend-panel">
        <jsp:doBody/>       
    </div>       
</div>