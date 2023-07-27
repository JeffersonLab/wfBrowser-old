<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@taglib prefix="t" tagdir="/WEB-INF/tags"%> 
<c:set var="title" value="Help"/>
<t:page title="${title}">  
    <jsp:attribute name="stylesheets">
        <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/css/help.css"/>        
    </jsp:attribute>
    <jsp:attribute name="scripts">
        <script type="text/javascript" src="${pageContext.request.contextPath}/resources/v${initParam.resourceVersionNumber}/js/help.js"></script>              
    </jsp:attribute>            
    <jsp:body>
        <section>
            <h2><c:out value="${title}"/></h2>
            <h3>About <c:out value="${initParam.appName}"/></h3>
            <ul class="key-value-list">
                <li>
                    <div class="li-key"><span>Release Version</span></div>
                    <div class="li-value"><c:out value="${initParam.releaseNumber}"/></div>
                </li>
                <li>
                    <div class="li-key"><span>Release Date</span></div>
                    <div class="li-value"><c:out value="${initParam.releaseDate}"/></div>
                </li>                    
                <li>
                    <div class="li-key"><span>Content Contact</span></div>
                    <div class="li-value"><c:out value="${initParam.contentContact}"/></div>                        
                </li>
                <li>
                    <div class="li-key"><span>Technical Contact</span></div>
                    <div class="li-value"><c:out value="${initParam.technicalContact}"/></div>                        
                </li>                    
            </ul>
            <h3><a href="${initParam.documentationUrl}">Documentation</a></h3>
            <c:choose>
                <c:when test="${pageContext.request.userPrincipal ne null}">
                    <h3>Feedback Form</h3>
                    <div style="font-weight: bold; margin-bottom: 4px;">(<span class="required-field"></span> required)</div>
                    <fieldset id="feedback-fieldset">
                        <form method="post" action="ajax/feedback">
                            <ul class="key-value-list">
                                <li>
                                    <div class="li-key"><label class="required-field" for="subject">Subject</label></div>
                                    <div class="li-value"><input type="text" id="subject" name="subject"/></div>
                                </li>
                                <li>
                                    <div class="li-key"><label class="required-field" for="body">Message</label></div>
                                    <div class="li-value"><textarea id="body" name="body"></textarea></div>
                                </li>                                       
                            </ul>
                            <button type="button" id="send-feedback-button">Submit</button>
                        </form>
                    </fieldset>
                </c:when>
                <c:otherwise>
                    <c:url var="feedbackUrl" value="/login">
                        <c:param name="returnUrl" value="${pageContext.request.contextPath}/help"/>
                    </c:url>
                    <h3><a href="${feedbackUrl}">Feedback Form</a></h3>
                </c:otherwise>
            </c:choose>
        </section>
    </jsp:body>         
</t:page>