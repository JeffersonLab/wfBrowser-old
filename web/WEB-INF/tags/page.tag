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
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title><c:out value="${initParam.appShortName}"/> - ${title}</title>
        <link rel="shortcut icon" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/img/logo16x16.png"/>
        <link rel="stylesheet" type="text/css" href="//cdn.acc.jlab.org/jquery-ui/1.10.3/theme/smoothness/jquery-ui.min.css"/>        
        <link rel="stylesheet" type="text/css" href="//cdn.acc.jlab.org/jlab-theme/smoothness/1.6/css/smoothness.min.css"/>        
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
                        <c:when test="${fn:startsWith(currentPath, '/login')}">
                            <%-- Don't show login/logout when on login page itself! --%>
                        </c:when>
                        <c:when test="${pageContext.request.userPrincipal ne null}">
                            <div id="username-container">
                                <c:out value="${pageContext.request.userPrincipal}"/>
                            </div>
                            <form id="logout-form" action="${pageContext.request.contextPath}/logout" method="post">
                                <button type="submit" value="Logout">Logout</button>
                                <input type="hidden" name="returnUrl" value="${fn:escapeXml(domainRelativeReturnUrl)}"/>
                            </form>
                        </c:when>
                        <c:otherwise>
                            <c:url value="/login" var="loginUrl">
                                <c:param name="returnUrl" value="${domainRelativeReturnUrl}"/>
                            </c:url>
                            <a id="login-link" href="${loginUrl}">Login</a> (<a id="auto-login" href="#">Auto</a>)
                        </c:otherwise>
                    </c:choose>
                </div>
                <nav id="primary-nav">
                    <ul>
                        <li${'/overview' eq currentPath ? ' class="current-primary"' : ''}>
                            <a href="${pageContext.request.contextPath}/overview">Overview</a>
                        </li>  
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
            </div>
        </div>
        <script type="text/javascript" src="//cdn.acc.jlab.org/jquery/1.10.2.min.js"></script>
        <script type="text/javascript" src="//cdn.acc.jlab.org/jquery-ui/1.10.3/jquery-ui.min.js"></script>  
        <script type="text/javascript" src="//cdn.acc.jlab.org/uri/uri-1.14.1.min.js"></script>        
        <script type="text/javascript" src="//cdn.acc.jlab.org/jlab-theme/smoothness/1.6/js/smoothness.min.js"></script>        
        <script type="text/javascript">
            jlab.contextPath = "${pageContext.request.contextPath}";
        </script>
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/js/wfbrowser.js"></script>
        <jsp:invoke fragment="scripts"/>        
    </body>
</html>