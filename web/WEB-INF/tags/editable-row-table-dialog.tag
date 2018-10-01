<%@tag description="Filter Flyout Widget" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<div id="table-row-dialog" class="dialog" title="Edit Row">
    <jsp:doBody/>
    <div class="dialog-button-panel">
        <button type="button" id="table-row-save-button" class="dialog-submit-button">Save</button>
        <button type="button" class="dialog-close-button">Cancel</button>
    </div>
</div>          