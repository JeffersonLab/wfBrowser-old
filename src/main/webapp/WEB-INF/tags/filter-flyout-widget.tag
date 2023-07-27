<%@tag description="Filter Flyout Widget" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<%@attribute name="ribbon" required="false" type="java.lang.Boolean"%>
<%@attribute name="requiredMessage" required="false" type="java.lang.Boolean"%>
<%@attribute name="clearButton" required="false" type="java.lang.Boolean"%>
<%@attribute name="resetButton" required="false" type="java.lang.Boolean"%>
<div id="filter-flyout-widget"${ribbon ? ' class="filter-flyout-ribbon"' : ''}>
    <div id="filter-flyout-button"><a id="filter-flyout-link" href="#">Choose...</a></div>
    <div id="filter-flyout-handle">
        <div id="filter-flyout-panel">
            <button id="filter-flyout-close-button" title="Close">X</button>
            <c:if test="${resetButton or clearButton}">
                <div class="reset-clear-panel">
                    (${resetButton ? '<span class="default-reset-panel"><a href="#">Reset</a></span>' : ''}
                    ${resetButton and clearButton ? ' | ' : ''}
                    ${clearButton ? '<span class="default-clear-panel"><a href="#">Clear</a></span>' : ''})
                </div>
            </c:if>
            <div id="filter-flyout-title">Choose Parameters${requiredMessage ? ' (<span class="required-field"></span> required)' : ''}</div>            
            <jsp:doBody/>
        </div>
    </div>
</div>                    