<%@ page contentType="text/html;charset=UTF-8" %>
<%
    String sakaiHead    = (String)  request.getAttribute("sakaiHtmlHead");
    String bodyClass    = (String)  request.getAttribute("bodyClass");
    String toolTitle    = (String)  request.getAttribute("toolTitle");
    Boolean cancelExito = (Boolean) request.getAttribute("cancelExito");
    String  cancelError = (String)  request.getAttribute("cancelError");
    String  cancelMsg   = (String)  request.getAttribute("cancelMensaje");

    if (toolTitle    == null) toolTitle    = "Reserva de Espacios";
    if (cancelExito  == null) cancelExito  = false;

    String siteId      = (String) request.getAttribute("siteId");
    String placementId = (String) request.getAttribute("placementId");
%>
<html>
<head>
    <%= sakaiHead != null ? sakaiHead : "" %>
    <title>Cancelar Reserva – <%= toolTitle %></title>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <h3><%= toolTitle %> – Cancelación</h3>

    <% if (cancelExito) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong>&#10007; Reserva cancelada.</strong><br/>
            <%= cancelMsg != null ? cancelMsg.replace("<","&lt;") : "La reserva ha sido cancelada y se ha notificado al solicitante." %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    &#8592; Volver al formulario
                </a>
            </p>
        <% } %>
    <% } else if (cancelError != null && !cancelError.isEmpty()) { %>
        <div class="alertMessage" style="background:#ffeaea; border-left:4px solid #c00;">
            <strong>&#10007; Error:</strong> <%= cancelError.replace("<","&lt;").replace("&","&amp;") %>
        </div>
        <% if (siteId != null && placementId != null) { %>
            <p style="margin-top:14px;">
                <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button">
                    &#8592; Volver al formulario
                </a>
            </p>
        <% } %>
    <% } else { %>
        <p>Procesando la cancelación...</p>
    <% } %>

</div>
</body>
</html>