<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.sakaiproject.reservation.CustomField, org.sakaiproject.util.ResourceLoader, java.util.List, java.util.Map" %>
<%
    ResourceLoader rb    = (ResourceLoader) request.getAttribute("rb");
    String sakaiHead     = (String)  request.getAttribute("sakaiHtmlHead");
    String bodyClass     = (String)  request.getAttribute("bodyClass");
    String toolTitle     = (String)  request.getAttribute("toolTitle");
    Boolean confirmSuccess = (Boolean) request.getAttribute("confirmSuccess");
    String  confirmError   = (String)  request.getAttribute("confirmError");
    String  confirmMsg     = (String)  request.getAttribute("confirmMessage");

    if (toolTitle      == null) toolTitle      = rb.getString("tool.title.default");
    if (confirmSuccess == null) confirmSuccess = false;

    @SuppressWarnings("unchecked")
    List<CustomField> customFields = (List<CustomField>) request.getAttribute("customFields");
    if (customFields == null) customFields = java.util.Collections.emptyList();

    @SuppressWarnings("unchecked")
    Map<String, String> customValues = (Map<String, String>) request.getAttribute("customValues");
    if (customValues == null) customValues = java.util.Collections.emptyMap();

    @SuppressWarnings("unchecked")
    List<String> slotDescs = (List<String>) request.getAttribute("slotDescs");
    if (slotDescs == null) slotDescs = java.util.Collections.emptyList();

    String siteId      = (String) request.getAttribute("siteId");
    String placementId = (String) request.getAttribute("placementId");
%>
<html>
<head>
    <%= sakaiHead != null ? sakaiHead : "" %>
    <title><%= rb.getString("confirm.page.title") %> – <%= toolTitle %></title>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <h3><%= toolTitle %> – <%= rb.getString("confirm.heading") %></h3>

    <% if (confirmSuccess) { %>
        <div class="alertMessage" style="background:#e6ffe6; border-left:4px solid #090;">
            <strong><%= rb.getString("confirm.success.label") %></strong><br/>
            <%= confirmMsg != null ? confirmMsg.replace("<","&lt;") : rb.getString("confirm.success.default") %>
        </div>
        <% if (!slotDescs.isEmpty()) { %>
        <h4 style="margin-top:1em;"><%= rb.getString("confirm.slots.heading") %></h4>
        <ul style="margin:0 0 0 1.2em; padding:0;">
            <% for (String desc : slotDescs) { %>
                <li><%= desc.replace("<","&lt;").replace("&","&amp;") %></li>
            <% } %>
        </ul>
        <% } %>
        <% if (!customFields.isEmpty()) { %>
        <h4 style="margin-top:1em;"><%= rb.getString("confirm.fields.heading") %></h4>
        <table class="listHier lines nolines" style="max-width:500px;">
            <% for (CustomField cf : customFields) {
                   String val = customValues.getOrDefault(cf.getId(), "");
                   if (!val.isEmpty()) { %>
            <tr>
                <td style="font-weight:bold; padding:4px 8px;"><%= cf.getLabel().replace("<","&lt;") %></td>
                <td style="padding:4px 8px;"><%= val.replace("<","&lt;").replace("&","&amp;") %></td>
            </tr>
            <%     }
               } %>
        </table>
        <% } %>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    <%= rb.getString("confirm.back") %>
                </a>
            </p>
        <% } %>
    <% } else if (confirmError != null && !confirmError.isEmpty()) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong>&#10007; <%= rb.getString("error.title") %>:</strong> <%= confirmError.replace("<","&lt;").replace("&","&amp;") %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    <%= rb.getString("confirm.back") %>
                </a>
            </p>
        <% } %>
    <% } else { %>
        <p><%= rb.getString("confirm.processing") %></p>
    <% } %>

</div>
</body>
</html>
