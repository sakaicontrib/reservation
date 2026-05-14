<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.sakaiproject.reserva.CustomField, java.util.List, java.util.Map" %>
<%
    String sakaiHead     = (String)  request.getAttribute("sakaiHtmlHead");
    String bodyClass     = (String)  request.getAttribute("bodyClass");
    String toolTitle     = (String)  request.getAttribute("toolTitle");
    Boolean confirmExito = (Boolean) request.getAttribute("confirmExito");
    String  confirmError = (String)  request.getAttribute("confirmError");
    String  confirmMsg   = (String)  request.getAttribute("confirmMensaje");

    if (toolTitle    == null) toolTitle    = "Reserva de Espacios";
    if (confirmExito == null) confirmExito = false;

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
    <title>Confirmar Reserva – <%= toolTitle %></title>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <h3><%= toolTitle %> – Confirmación</h3>

    <% if (confirmExito) { %>
        <div class="alertMessage" style="background:#e6ffe6; border-left:4px solid #090;">
            <strong>&#10003; Reserva confirmada.</strong><br/>
            <%= confirmMsg != null ? confirmMsg.replace("<","&lt;") : "La reserva ha sido confirmada y se ha notificado al solicitante." %>
        </div>
        <% if (!slotDescs.isEmpty()) { %>
        <h4 style="margin-top:1em;">Franjas confirmadas</h4>
        <ul style="margin:0 0 0 1.2em; padding:0;">
            <% for (String desc : slotDescs) { %>
                <li><%= desc.replace("<","&lt;").replace("&","&amp;") %></li>
            <% } %>
        </ul>
        <% } %>
        <% if (!customFields.isEmpty()) { %>
        <h4 style="margin-top:1em;">Campos adicionales</h4>
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
                    &#8592; Volver al formulario
                </a>
            </p>
        <% } %>
    <% } else if (confirmError != null && !confirmError.isEmpty()) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong>&#10007; Error:</strong> <%= confirmError.replace("<","&lt;").replace("&","&amp;") %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    &#8592; Volver al formulario
                </a>
            </p>
        <% } %>
    <% } else { %>
        <p>Procesando la confirmación...</p>
    <% } %>

</div>
</body>
</html>