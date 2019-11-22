<%@tag description="The Site Page Template" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@attribute name="title"%>
<%@attribute name="stylesheets" fragment="true"%>
<%@attribute name="scripts" fragment="true"%>
<c:url var="domainRelativeReturnUrl" scope="request" context="/" value="${requestScope['javax.servlet.forward.request_uri']}${requestScope['javax.servlet.forward.query_string'] ne null ? '?'.concat(requestScope['javax.servlet.forward.query_string']) : ''}"/>
<c:set var="currentPath" scope="request" value="${requestScope['javax.servlet.forward.servlet_path']}"/>
<!DOCTYPE html>
<html>
    <head>
        <meta name="google" content="notranslate">
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title><c:out value="${initParam.appShortName}"/> - ${title}</title>
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/img/favicon.ico"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-ui/1.10.3/theme/smoothness/jquery-ui.min.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-plugins/timepicker/jquery-ui-timepicker-1.3.1.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jquery-plugins/select2/3.5.2/select2.css"/>
        <link rel="stylesheet" type="text/css" href="${initParam.cdnContextPath}/jlab-theme/smoothness/1.6/css/smoothness.min.css"/>
        <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/wfbrowser.css"/>
        <jsp:invoke fragment="stylesheets"/>
    </head>
    <body class="${param.print eq 'Y' ? 'print ' : ''} ${param.fullscreen eq 'Y' ? 'fullscreen' : ''}">
        <c:if test="${initParam.notification ne null}">
            <div id="notification-bar"><c:out value="${initParam.notification}"/></div>
        </c:if>
        <div id="page">
            <header>
                <h1><span id="page-header-logo"></span> <span id="page-header-text"><c:out value="${initParam.appName}"/></span></h1>
                <div id="auth">
                    <c:choose>
                        <c:when test="${pageContext.request.userPrincipal ne null}">
                            <div id="username-container">
                                <c:out value="${requestScope.simpleRemoteUser}"/>
                            </div>
                            <form id="logout-form" action="${pageContext.request.contextPath}/logout" method="post">
                                <button type="submit" value="Logout">Logout</button>
                                <input type="hidden" name="returnUrl" value="${fn:escapeXml(domainRelativeReturnUrl)}"/>
                            </form>
                        </c:when>
                        <c:otherwise>
                            <c:url value="/sso" var="loginUrl">
                                <c:param name="returnUrl" value="${domainRelativeReturnUrl}"/>
                            </c:url>
                            <c:url value="/sso" var="suUrl">
                                <c:param name="kc_idp_hint" value="kc_idp_hint=ace-su-keycloak-oidc"/>
                                <c:param name="returnUrl" value="${domainRelativeReturnUrl}"/>
                            </c:url>
                            <a id="login-link" href="${loginUrl}">Login</a> (<a id="SU" href="${suUrl}">SU</a>)
                        </c:otherwise>
                    </c:choose>
                </div>
                <nav id="primary-nav">
                    <ul>
                        <li${'/graph' eq currentPath ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/graph">Graph</a>
                        </li>
                        <li ${fn:startsWith(currentPath, '/reports') ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/reports/rf-label-summary">Reports</a>
                        </li>
                        <c:if test='${pageContext.request.isUserInRole("wfb_admin")}'>
                            <li${'/admin' eq currentPath ? ' class="current-primary"' : ''}>
                                <a href="${pageContext.request.contextPath}/admin/series">Admin</a>
                            </li>
                        </c:if>
                        <li${'/help' eq currentPath ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/help">Help</a>
                        </li>
                    </ul>
                </nav>
            </header>
            <div id="content">
                <div id="content-liner">
                    <jsp:doBody/>
                </div>
                <div id="version-info">Version: ${initParam.releaseNumber}, Released: ${initParam.releaseDate}</div>
            </div>
        </div>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery/1.10.2.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-ui/1.10.3/jquery-ui.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/uri/uri-1.14.1.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-plugins/timepicker/jquery-ui-timepicker-1.3.1.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jquery-plugins/select2/3.5.2/select2.min.js"></script>
        <script type="text/javascript" src="${initParam.cdnContextPath}/jlab-theme/smoothness/1.6/js/smoothness.min.js"></script>
        <script type="text/javascript">
            jlab.contextPath = "${pageContext.request.contextPath}";
            // THIS VARIABLE SHOULD ONLY BE USED FOR UI PURPOSES AND NOT FOR ANY REAL SECURITY
            jlab.isUserAdmin = false;
            <c:if test='${pageContext.request.isUserInRole("wfb_admin")}'>
            jlab.isUserAdmin = true;
            </c:if>
        </script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/js/wfbrowser.js"></script>
        <jsp:invoke fragment="scripts"/>
    </body>
</html>