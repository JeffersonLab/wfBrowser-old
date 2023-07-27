<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Login"/>
<t:page title="${title}">  
    <jsp:attribute name="stylesheets">
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript">
            $(document).ready(function() {
                $('#username').focus();
            });            
        </script>
    </jsp:attribute>        
    <jsp:body>
        <section>
            <h2><c:out value="${title}"/></h2>
            <c:choose>
                <c:when test="${pageContext.request.userPrincipal ne null}">
                    <form id="logout-form" action="${pageContext.request.contextPath}/logout" method="post">
                        <span>You are already logged in. Not <c:out value="${pageContext.request.userPrincipal}"/>? <button type="submit" value="Logout">Logout</button></span>
                    </form>
                </c:when>
                <c:otherwise>
                    <form method="post" action="${pageContext.request.contextPath}/login">
                        <fieldset>
                            <ul class="key-value-list">
                                <li>
                                    <div class="li-key">
                                        <label for="username">Username</label>
                                    </div>
                                    <div class="li-value">
                                        <input type="text" id="username" name="username" value="${param.username}">
                                    </div>
                                </li>
                                <li>
                                    <div class="li-key">
                                        <label for="password">Password</label>
                                    </div>
                                    <div class="li-value">
                                        <input type="password" id="password" name="password">
                                    </div>
                                </li>
                            </ul>
                            <div>
                                <input type="submit" value="Login"/>
                                <input type="hidden" name="requester" value="login"/>
                                <c:set var="returnUrl" value="${param.returnUrl}"/>
                                <c:if test="${param.returnUrl eq null and not fn:startsWith(currentPath, '/login')}">
                                    <c:set var="returnUrl" value="${domainRelativeReturnUrl}"/>
                                </c:if>             
                                <input type="hidden" name="returnUrl" value="${fn:escapeXml(returnUrl)}"/>
                            </div>
                        </fieldset>
                    </form>
                </c:otherwise>
            </c:choose>
            <div class="message-box error-message"><c:out value="${message}"/></div>
        </section>        
    </jsp:body>         
</t:page>