<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html>
    <body>
        <h1>Login to website</h1>

        <form action="LoginController" method="post">
            Username <input type="text" name="txtUsername" /><br/>
            Password <input type="password" name="txtPassword" /><br/>
            <input type="submit" value="Login" name="action" />           
        </form>
        <c:set var="message" value="${requestScope.message}"/>
        <c:if test="${message != null}">
            <text style="color: red">${message}</text>
        </c:if>

    </body>
</html>