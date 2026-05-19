<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.sakaiproject.util.ResourceLoader" %>
<%
    ResourceLoader rb    = (ResourceLoader) request.getAttribute("rb");
    String sakaiHead    = (String)  request.getAttribute("sakaiHtmlHead");
    String bodyClass    = (String)  request.getAttribute("bodyClass");
    String toolTitle    = (String)  request.getAttribute("toolTitle");
    Boolean cancelSuccess = (Boolean) request.getAttribute("cancelSuccess");
    String  cancelError   = (String)  request.getAttribute("cancelError");
    String  cancelMsg     = (String)  request.getAttribute("cancelMessage");

    if (toolTitle     == null) toolTitle     = rb.getString("tool.title.default");
    if (cancelSuccess == null) cancelSuccess = false;

    String siteId      = (String) request.getAttribute("siteId");
    String placementId = (String) request.getAttribute("placementId");
%>
<html>
<head>
    <%= sakaiHead != null ? sakaiHead : "" %>
    <title><%= rb.getString("cancel.page.title") %> – <%= toolTitle %></title>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <h3><%= toolTitle %> – <%= rb.getString("cancel.heading") %></h3>

    <% if (cancelSuccess) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong><%= rb.getString("cancel.success.label") %></strong><br/>
            <%= cancelMsg != null ? cancelMsg.replace("<","&lt;") : rb.getString("cancel.success.default") %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    <%= rb.getString("cancel.back") %>
                </a>
            </p>
        <% } %>
    <% } else if (cancelError != null && !cancelError.isEmpty()) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong>&#10007; <%= rb.getString("error.title") %>:</strong> <%= cancelError.replace("<","&lt;").replace("&","&amp;") %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    <%= rb.getString("cancel.back") %>
                </a>
            </p>
        <% } %>
    <% } else { %>
        <p><%= rb.getString("cancel.processing") %></p>
    <% } %>

</div>
</body>
</html>
