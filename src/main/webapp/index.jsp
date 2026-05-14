<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="java.util.List, java.util.Map, java.util.Arrays, org.sakaiproject.reserva.CustomField" %>
<%
    String sakaiHead     = (String)  request.getAttribute("sakaiHtmlHead");
    String bodyClass     = (String)  request.getAttribute("bodyClass");
    String toolTitle     = (String)  request.getAttribute("toolTitle");
    Boolean esInstructor = (Boolean) request.getAttribute("esInstructor");
    Boolean exito          = (Boolean) request.getAttribute("exito");
    String  mensajeExito   = (String)  request.getAttribute("mensajeExito");
    String  error          = (String)  request.getAttribute("error");
    Boolean calendarMissing = (Boolean) request.getAttribute("calendarMissing");
    if (calendarMissing == null) calendarMissing = false;

    if (toolTitle    == null) toolTitle    = "Reserva de Espacios";
    if (esInstructor == null) esInstructor = false;
    if (exito        == null) exito        = false;

    String nombre = request.getAttribute("nombre") != null ? (String) request.getAttribute("nombre") : "";
    String email  = request.getAttribute("email")  != null ? (String) request.getAttribute("email")  : "";

    @SuppressWarnings("unchecked")
    List<CustomField> customFields = request.getAttribute("customFields") != null
            ? (List<CustomField>) request.getAttribute("customFields")
            : java.util.Collections.emptyList();

    @SuppressWarnings("unchecked")
    Map<String, String> customValues = request.getAttribute("customValues") != null
            ? (Map<String, String>) request.getAttribute("customValues")
            : java.util.Collections.emptyMap();

    String resourceFieldId = request.getAttribute("resourceFieldId") != null
            ? (String) request.getAttribute("resourceFieldId") : "espacio";
    String endFieldType    = request.getAttribute("endFieldType") != null
            ? (String) request.getAttribute("endFieldType") : "duration-slot";
    boolean isDuration = "duration-slot".equals(endFieldType);
    String endLabel = isDuration ? "Duración" : "Hora fin";

    String siteId      = (String) request.getAttribute("siteId");
    String placementId = (String) request.getAttribute("placementId");

    String escNombre = nombre.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
    String escEmail  = email.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
%>
<html>
<head>
    <%= sakaiHead != null ? sakaiHead : "" %>
    <title><%= toolTitle %></title>
    <style>
        .reserva-form table { width: 100%; }
        .reserva-form td:first-child { width: 180px; font-weight: bold; padding: 6px 8px; vertical-align: top; }
        .reserva-form td:last-child  { padding: 4px 8px; }
        .reserva-form input[type=text],
        .reserva-form input[type=date],
        .reserva-form input[type=time],
        .reserva-form input[type=datetime-local],
        .reserva-form select,
        .reserva-form textarea {
            width: 100%; max-width: 480px; padding: 5px 7px;
            border: 1px solid #ccc; border-radius: 3px; font-size: 14px;
        }
        .reserva-form input[readonly] { background: #f0f0f0; cursor: not-allowed; color: #555; }
        .reserva-form textarea { height: 80px; resize: vertical; }
        .slot-row { border: 1px solid #ddd; padding: 8px 10px; margin-bottom: 6px; background: #fafafa; border-radius: 3px; }
        .slot-row input[type=date], .slot-row input[type=time], .slot-row select { width: auto; max-width: 160px; }
        .slot-time-part { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 4px; }
        .conflict-warning { color: #c00; font-size: 13px; margin-top: 4px; display: none; }
        .field-required::after { content: " *"; color: #c00; }
    </style>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <h3 style="margin:0"><%= toolTitle %></h3>
        <% if (esInstructor && siteId != null && placementId != null) { %>
            <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/options" class="button">&#9881; Opciones</a>
        <% } %>
    </div>

    <% if (calendarMissing) { %>
        <div class="alertMessage" style="margin-bottom:12px; background:#fff3cd; border-left:4px solid #f0ad4e;">
            <strong>Aviso:</strong> Esta herramienta requiere que el sitio tenga el <strong>Calendario</strong> añadido.
            Por favor, añada la herramienta Calendario a este sitio antes de usar las reservas.
        </div>
    <% } %>

    <% if (exito) { %>
        <div class="alertMessage" style="margin-bottom:12px;">
            <%= mensajeExito != null ? mensajeExito : "Solicitud enviada correctamente." %>
        </div>
    <% } %>

    <% if (error != null && !error.isEmpty()) { %>
        <div class="alertMessage" style="margin-bottom:12px; background:#ffeaea; border-left:4px solid #c00;">
            <strong>Error:</strong> <%= error.replace("<","&lt;").replace("&","&amp;") %>
        </div>
    <% } %>

    <% if (!exito) { %>
    <div class="reserva-form">
    <form method="post" action="" id="reservaForm">
        <input type="hidden" name="slot_count" id="slot_count" value="1" />

        <table class="listHier lines nolines">
            <%-- Fixed: requester name (read-only) --%>
            <tr>
                <td><label for="nombreField" class="field-required">Nombre</label></td>
                <td><input type="text" id="nombreField" value="<%= escNombre %>"
                           readonly title="Nombre obtenido de su cuenta Sakai" /></td>
            </tr>
            <%-- Fixed: requester email (read-only) --%>
            <tr>
                <td><label for="emailField" class="field-required">Email</label></td>
                <td><input type="text" id="emailField" value="<%= escEmail %>"
                           readonly title="Email obtenido de su cuenta Sakai" /></td>
            </tr>

            <%-- Dynamic custom fields. Slot section is inserted after the resource field. --%>
            <% for (CustomField cf : customFields) {
                   String cfInputId = "custom_" + cf.getId();
                   String cfVal     = customValues.getOrDefault(cf.getId(), "");
                   String cfEsc     = cfVal.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
                   boolean isResource = cf.getId().equals(resourceFieldId);
                   String onchange    = isResource ? " onchange=\"checkAllSlots()\"" : "";
                   String reqMark     = cf.isRequired() ? " class=\"field-required\"" : "";
                   String reqAttr     = cf.isRequired() ? " required" : "";
            %>
            <tr>
                <td><label for="<%= cfInputId %>"<%= reqMark %>><%= cf.getLabel().replace("<","&lt;") %></label></td>
                <td>
                    <% if ("textarea".equals(cf.getType())) { %>
                        <textarea id="<%= cfInputId %>" name="<%= cfInputId %>"<%= reqAttr %><%= onchange %>><%= cfEsc %></textarea>

                    <% } else if ("select".equals(cf.getType())) {
                           if (cf.getOptions().size() == 1) {
                               String singleOpt    = cf.getOptions().get(0);
                               String singleOptEsc = singleOpt.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
                               String autoVal      = cfVal.isEmpty() ? singleOpt : cfVal;
                               String autoValEsc   = autoVal.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;");
                    %>
                        <input type="hidden" id="<%= cfInputId %>" name="<%= cfInputId %>" value="<%= autoValEsc %>" />
                        <input type="text" value="<%= singleOptEsc %>" readonly title="Único recurso disponible" />
                    <%     } else { %>
                        <select id="<%= cfInputId %>" name="<%= cfInputId %>"<%= reqAttr %><%= onchange %>>
                            <option value="">-- Seleccione --</option>
                            <% for (String opt : cf.getOptions()) {
                                   String optEsc = opt.replace("\"","&quot;").replace("<","&lt;");
                            %>
                                <option value="<%= optEsc %>"<%= opt.equals(cfVal) ? " selected" : "" %>><%= optEsc %></option>
                            <% } %>
                        </select>
                    <%     } %>

                    <% } else if ("checkbox-group".equals(cf.getType())) {
                           List<String> checked = cfVal.isEmpty()
                               ? java.util.Collections.emptyList()
                               : Arrays.asList(cfVal.split(", "));
                    %>
                        <input type="hidden" id="<%= cfInputId %>" name="<%= cfInputId %>" value="<%= cfEsc %>" />
                        <div class="cbg-container" data-fieldid="<%= cfInputId %>">
                        <% for (String opt : cf.getOptions()) {
                               String optEsc = opt.replace("\"","&quot;").replace("<","&lt;");
                        %>
                            <label style="display:block; margin-bottom:4px;">
                                <input type="checkbox" class="cbg-item" value="<%= optEsc %>"
                                       <%= checked.contains(opt) ? "checked" : "" %> />
                                <%= optEsc %>
                            </label>
                        <% } %>
                        </div>

                    <% } else if ("date".equals(cf.getType())) { %>
                        <input type="date" id="<%= cfInputId %>" name="<%= cfInputId %>"
                               value="<%= cfEsc %>"<%= reqAttr %><%= onchange %> />

                    <% } else if ("datetime-local".equals(cf.getType())) { %>
                        <input type="datetime-local" id="<%= cfInputId %>" name="<%= cfInputId %>"
                               value="<%= cfEsc %>"<%= reqAttr %><%= onchange %> />

                    <% } else if ("time".equals(cf.getType())) { %>
                        <input type="time" id="<%= cfInputId %>" name="<%= cfInputId %>"
                               value="<%= cfEsc %>"<%= reqAttr %><%= onchange %> />

                    <% } else if ("time-slot".equals(cf.getType())) { %>
                        <select id="<%= cfInputId %>" name="<%= cfInputId %>"<%= reqAttr %><%= onchange %>>
                            <option value="">-- Seleccione hora --</option>
                            <% for (int h = 0; h < 24; h++) {
                                   for (int m = 0; m < 60; m += 30) {
                                       String slot = String.format("%02d:%02d", h, m);
                            %>
                                <option value="<%= slot %>"<%= slot.equals(cfVal) ? " selected" : "" %>><%= slot %></option>
                            <%     }
                               } %>
                        </select>

                    <% } else if ("duration-slot".equals(cf.getType())) { %>
                        <select id="<%= cfInputId %>" name="<%= cfInputId %>"<%= reqAttr %><%= onchange %>>
                            <option value="">-- Seleccione duración --</option>
                            <% int[] durMins = {30,60,90,120,150,180,210,240,270,300,330,360,390,420,450,480};
                               for (int dur : durMins) {
                                   int h = dur / 60, mo = dur % 60;
                                   String label = h > 0 ? h + "h" + (mo > 0 ? " " + mo + " min" : "") : mo + " min";
                            %>
                                <option value="<%= dur %>"<%= String.valueOf(dur).equals(cfVal) ? " selected" : "" %>><%= label %></option>
                            <% } %>
                        </select>

                    <% } else { %>
                        <input type="text" id="<%= cfInputId %>" name="<%= cfInputId %>"
                               value="<%= cfEsc %>"<%= reqAttr %><%= onchange %> />
                    <% } %>
                </td>
            </tr>
            <%-- Insert slot section right after the resource field --%>
            <% if (cf.getId().equals(resourceFieldId)) { %>
            <tr>
                <td><span class="field-required">Franjas horarias</span></td>
                <td>
                    <div id="slots-container"></div>
                    <button type="button" onclick="addSlot()" class="button" style="margin-top:6px;">+ Añadir día</button>
                </td>
            </tr>
            <% } %>
            <% } %>

            <%-- Submit --%>
            <tr>
                <td></td>
                <td style="padding-top:10px;">
                    <input type="submit" value="Enviar solicitud" class="active" id="submitBtn"
                           <%= calendarMissing ? "disabled" : "" %> />
                    <small style="display:block; margin-top:6px; color:#777;">(*) Campos obligatorios</small>
                </td>
            </tr>
        </table>
    </form>
    </div>
    <% } %>

</div>

<script>
var checkConflictUrl = '<%= (siteId != null && placementId != null)
    ? "/portal/site/" + siteId + "/tool/" + placementId + "/checkConflict"
    : "" %>';
var resourceFieldId = 'custom_<%= resourceFieldId %>';
var endFieldType    = '<%= endFieldType %>';
var endLabel        = '<%= endLabel %>';
var slotCount       = 0;

function getFieldValue(id) {
    var el = document.getElementById(id);
    return el ? el.value : '';
}

// Keep checkbox-group hidden inputs in sync
document.querySelectorAll('.cbg-container').forEach(function(container) {
    var fieldId = container.getAttribute('data-fieldid');
    var hidden  = document.getElementById(fieldId);
    function sync() {
        var vals = [];
        container.querySelectorAll('.cbg-item:checked').forEach(function(cb) { vals.push(cb.value); });
        if (hidden) hidden.value = vals.join(', ');
    }
    container.querySelectorAll('.cbg-item').forEach(function(cb) { cb.addEventListener('change', sync); });
    sync();
});

function buildEndInput(i) {
    var val = '';
    if (endFieldType === 'duration-slot') {
        var sel = '<select name="slot_end_' + i + '" id="slot_end_' + i + '" onchange="checkSlotConflict(' + i + ')" required>';
        sel += '<option value="">-- Duración --</option>';
        var opts = [30,60,90,120,150,180,210,240,270,300,330,360,390,420,450,480];
        opts.forEach(function(d) {
            var h = Math.floor(d/60), m = d%60;
            var lbl = h > 0 ? h + 'h' + (m > 0 ? ' ' + m + ' min' : '') : m + ' min';
            sel += '<option value="' + d + '">' + lbl + '</option>';
        });
        sel += '</select>';
        return sel;
    } else if (endFieldType === 'time-slot') {
        var sel = '<select name="slot_end_' + i + '" id="slot_end_' + i + '" onchange="checkSlotConflict(' + i + ')" required>';
        sel += '<option value="">-- Hora fin --</option>';
        for (var h = 0; h < 24; h++) {
            for (var m = 0; m < 60; m += 30) {
                var hh = (h < 10 ? '0' : '') + h, mm = (m < 10 ? '0' : '') + m;
                sel += '<option value="' + hh + ':' + mm + '">' + hh + ':' + mm + '</option>';
            }
        }
        sel += '</select>';
        return sel;
    } else {
        return '<input type="time" name="slot_end_' + i + '" id="slot_end_' + i + '" onchange="checkSlotConflict(' + i + ')" required />';
    }
}

function addSlot() {
    var i = slotCount++;
    document.getElementById('slot_count').value = slotCount;

    var div = document.createElement('div');
    div.className = 'slot-row';
    div.id = 'slot_row_' + i;
    div.innerHTML =
        '<div style="display:flex; align-items:center; justify-content:space-between;">' +
            '<strong>Franja ' + (i + 1) + '</strong>' +
            (i > 0 ? '<button type="button" onclick="removeSlot(' + i + ')" style="color:red; border:none; background:none; cursor:pointer;">✕ Eliminar</button>' : '') +
        '</div>' +
        '<div class="slot-time-part">' +
            '<label>Fecha <input type="date" name="slot_date_' + i + '" id="slot_date_' + i + '" onchange="checkSlotConflict(' + i + ')" required /></label>' +
            '<span id="slot_time_' + i + '" style="display:inline-flex; align-items:center; gap:8px;">' +
                '<label>Inicio <input type="time" name="slot_start_' + i + '" id="slot_start_' + i + '" onchange="checkSlotConflict(' + i + ')" required /></label>' +
                '<label>' + endLabel + ' ' + buildEndInput(i) + '</label>' +
            '</span>' +
        '</div>' +
        '<div style="margin-top:4px;">' +
            '<label><input type="checkbox" name="slot_allday_' + i + '" id="slot_allday_' + i + '" onchange="toggleAllDay(' + i + ')"> Día completo</label>' +
        '</div>' +
        '<div class="conflict-warning" id="slot_warning_' + i + '"></div>';

    document.getElementById('slots-container').appendChild(div);
}

function removeSlot(i) {
    var row = document.getElementById('slot_row_' + i);
    if (row) row.remove();
}

function toggleAllDay(i) {
    var allDay   = document.getElementById('slot_allday_' + i).checked;
    var timePart = document.getElementById('slot_time_' + i);
    if (timePart) {
        timePart.style.display = allDay ? 'none' : '';
        timePart.querySelectorAll('input, select').forEach(function(el) {
            el.disabled = allDay;
            el.required = !allDay;
        });
    }
    checkSlotConflict(i);
}

function checkSlotConflict(i) {
    if (!checkConflictUrl) return;
    var resource = getFieldValue(resourceFieldId);
    var dateEl   = document.getElementById('slot_date_' + i);
    var date     = dateEl ? dateEl.value : '';
    var allDayEl = document.getElementById('slot_allday_' + i);
    var allDay   = allDayEl && allDayEl.checked;
    var warning  = document.getElementById('slot_warning_' + i);

    if (!resource || !date) { if (warning) warning.style.display = 'none'; return; }

    var url;
    if (allDay) {
        url = checkConflictUrl + '?resource=' + encodeURIComponent(resource)
            + '&startValue=' + encodeURIComponent(date)
            + '&allday=true';
    } else {
        var startEl = document.getElementById('slot_start_' + i);
        var endEl   = document.getElementById('slot_end_' + i);
        var startTime = startEl ? startEl.value : '';
        var endValue  = endEl   ? endEl.value   : '';
        if (!startTime || !endValue) { if (warning) warning.style.display = 'none'; return; }
        url = checkConflictUrl
            + '?resource='   + encodeURIComponent(resource)
            + '&startValue=' + encodeURIComponent(date + 'T' + startTime)
            + '&endValue='   + encodeURIComponent(endValue);
    }

    fetch(url)
        .then(function(r) { return r.json(); })
        .then(function(data) {
            if (!warning) return;
            if (data.conflicto) {
                warning.textContent = '⚠ ' + (data.mensaje || 'Conflicto de reserva en esta franja.');
                warning.style.display = 'block';
                document.getElementById('submitBtn').disabled = true;
            } else {
                warning.style.display = 'none';
                if (!document.querySelector('.conflict-warning[style*="block"]'))
                    document.getElementById('submitBtn').disabled = false;
            }
        })
        .catch(function() { if (warning) warning.style.display = 'none'; });
}

function checkAllSlots() {
    for (var i = 0; i < slotCount; i++) {
        if (document.getElementById('slot_row_' + i)) checkSlotConflict(i);
    }
}

// Add first slot on load
addSlot();

// If the resource field was auto-populated (single-option select), trigger conflict checks
(function() {
    var rf = document.getElementById(resourceFieldId);
    if (rf && rf.tagName === 'INPUT' && rf.type === 'hidden' && rf.value) checkAllSlots();
})();
</script>

</body>
</html>