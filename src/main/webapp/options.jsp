<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="org.sakaiproject.reserva.CustomField, org.sakaiproject.util.ResourceLoader, java.util.List" %>
<%
    ResourceLoader rb        = (ResourceLoader) request.getAttribute("rb");
    String sakaiHead         = (String) request.getAttribute("sakaiHtmlHead");
    String bodyClass         = (String) request.getAttribute("bodyClass");
    String toolTitle         = (String) request.getAttribute("toolTitle");
    String adminEmail        = (String) request.getAttribute("adminEmail");
    String fromEmail         = (String) request.getAttribute("fromEmail");
    String resourceFieldId   = (String) request.getAttribute("resourceFieldId");
    String slotDefaultStart  = (String) request.getAttribute("slotDefaultStart");

    if (toolTitle        == null) toolTitle        = rb.getString("tool.title.default");
    if (adminEmail       == null) adminEmail       = "";
    if (fromEmail        == null) fromEmail        = "";
    if (resourceFieldId  == null) resourceFieldId  = "espacio";
    if (slotDefaultStart == null) slotDefaultStart = "08:00";

    @SuppressWarnings("unchecked")
    List<CustomField> customFields = (List<CustomField>) request.getAttribute("customFields");
    if (customFields == null) customFields = new java.util.ArrayList<>();

    String siteId      = (String) request.getAttribute("siteId");
    String placementId = (String) request.getAttribute("placementId");
%>
<html>
<head>
    <%= sakaiHead != null ? sakaiHead : "" %>
    <title><%= rb.getString("options.page.title") %> – <%= toolTitle %></title>
    <style>
        .opts-form table { width: 100%; max-width: 640px; }
        .opts-form td:first-child { width: 200px; font-weight: bold; padding: 6px 8px; vertical-align: top; }
        .opts-form td:last-child  { padding: 4px 8px; }
        .opts-form input[type=text],
        .opts-form select,
        .opts-form textarea {
            width: 100%; max-width: 420px; padding: 5px 7px;
            border: 1px solid #ccc; border-radius: 3px; font-size: 14px;
        }
        .opts-form textarea { height: 90px; resize: vertical; }
    </style>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody container-fluid">

    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
        <h3 style="margin:0"><%= rb.getString("options.page.title") %> – <%= toolTitle %></h3>
        <% if (siteId != null && placementId != null) { %>
            <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool" class="button"><%= rb.getString("options.back") %></a>
        <% } %>
    </div>

    <div class="opts-form">
    <form method="post" action="">

        <%-- General settings --%>
        <h4><%= rb.getString("options.general.heading") %></h4>
        <table class="listHier lines nolines">
            <tr>
                <td><label for="toolTitle"><%= rb.getString("options.tool.title.label") %></label></td>
                <td><input type="text" id="toolTitle" name="toolTitle"
                           value="<%= toolTitle.replace("\"","&quot;") %>" /></td>
            </tr>
            <tr>
                <td><label for="adminEmail"><%= rb.getString("options.admin.email.label") %></label></td>
                <td>
                    <input type="text" id="adminEmail" name="adminEmail"
                           value="<%= adminEmail.replace("\"","&quot;") %>"
                           placeholder="admin@universidad.es" />
                    <small style="color:#777;"><%= rb.getString("options.admin.email.hint") %></small>
                </td>
            </tr>
            <tr>
                <td><label for="fromEmail"><%= rb.getString("options.from.email.label") %></label></td>
                <td>
                    <input type="text" id="fromEmail" name="fromEmail"
                           value="<%= fromEmail.replace("\"","&quot;") %>"
                           placeholder="noreply@universidad.es" />
                    <small style="color:#777;"><%= rb.getString("options.from.email.hint") %></small>
                </td>
            </tr>
        </table>

        <%-- Slot configuration --%>
        <h4 style="margin-top:2em;"><%= rb.getString("options.slots.heading") %></h4>
        <table class="listHier lines nolines">
            <tr>
                <td><label for="resourceFieldId"><%= rb.getString("options.resource.field.label") %></label></td>
                <td>
                    <select id="resourceFieldId" name="resourceFieldId">
                        <% for (CustomField cf : customFields) { %>
                        <option value="<%= cf.getId().replace("\"","&quot;") %>"
                                <%= cf.getId().equals(resourceFieldId) ? "selected" : "" %>>
                            <%= cf.getLabel().replace("<","&lt;") %> (<%= cf.getId() %>)
                        </option>
                        <% } %>
                    </select>
                    <small style="color:#777;"><%= rb.getString("options.resource.field.hint") %></small>
                </td>
            </tr>
            <tr>
                <td><label for="slotDefaultStart"><%= rb.getString("options.slot.default.start.label") %></label></td>
                <td>
                    <input type="time" id="slotDefaultStart" name="slotDefaultStart"
                           value="<%= slotDefaultStart %>" />
                    <small style="color:#777;"><%= rb.getString("options.slot.default.start.hint") %></small>
                </td>
            </tr>
        </table>

        <%-- Custom fields --%>
        <h4 style="margin-top:2em;"><%= rb.getString("options.fields.heading") %></h4>

        <div id="custom-fields-container">
        <% for (CustomField cf : customFields) { %>
        <div class="custom-field-row" style="border:1px solid #ddd; padding:0.8em; margin-bottom:0.5em; background:#fafafa;">
            <input type="hidden" name="cf_id" value="<%= cf.getId().replace("\"","&quot;") %>" />
            <table style="width:100%">
                <tr>
                    <td style="width:30px; text-align:center; vertical-align:middle;">
                        <button type="button" onclick="moveUp(this)" style="display:block;width:100%">▲</button>
                        <button type="button" onclick="moveDown(this)" style="display:block;width:100%">▼</button>
                    </td>
                    <td>
                        <table style="width:100%">
                            <tr>
                                <td><label><%= rb.getString("options.field.label") %></label><br/>
                                    <input type="text" name="cf_label"
                                           value="<%= cf.getLabel().replace("\"","&quot;") %>"
                                           style="width:180px;" /></td>
                                <td><label><%= rb.getString("options.field.type") %></label><br/>
                                    <select name="cf_type" onchange="toggleOptions(this)">
                                        <option value="text"           <%= "text".equals(cf.getType())           ? "selected" : "" %>><%= rb.getString("options.field.type.text") %></option>
                                        <option value="textarea"       <%= "textarea".equals(cf.getType())       ? "selected" : "" %>><%= rb.getString("options.field.type.textarea") %></option>
                                        <option value="date"           <%= "date".equals(cf.getType())           ? "selected" : "" %>><%= rb.getString("options.field.type.date") %></option>
                                        <option value="datetime-local" <%= "datetime-local".equals(cf.getType()) ? "selected" : "" %>><%= rb.getString("options.field.type.datetime") %></option>
                                        <option value="time"           <%= "time".equals(cf.getType())           ? "selected" : "" %>><%= rb.getString("options.field.type.time") %></option>
                                        <option value="select"         <%= "select".equals(cf.getType())         ? "selected" : "" %>><%= rb.getString("options.field.type.select") %></option>
                                        <option value="checkbox-group" <%= "checkbox-group".equals(cf.getType()) ? "selected" : "" %>><%= rb.getString("options.field.type.checkbox") %></option>
                                    </select>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <label>
                                        <input type="checkbox" name="cf_required"
                                               value="<%= cf.getId().replace("\"","&quot;") %>"
                                               <%= cf.isRequired() ? "checked" : "" %> /> <%= rb.getString("options.field.required") %>
                                    </label>
                                </td>
                                <td style="vertical-align:bottom;">
                                    <button type="button" onclick="removeField(this)" style="color:red;"><%= rb.getString("options.field.remove") %></button>
                                </td>
                            </tr>
                            <tr class="options-row" style="<%= ("select".equals(cf.getType()) || "checkbox-group".equals(cf.getType())) ? "" : "display:none" %>">
                                <td colspan="4">
                                    <label><%= rb.getString("options.field.options") %></label><br/>
                                    <textarea name="cf_options" rows="3" style="width:100%; max-width:100%"><% for (String opt : cf.getOptions()) { out.println(opt.replace("<","&lt;")); } %></textarea>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>
            </table>
        </div>
        <% } %>
        </div>

        <button type="button" onclick="addField()" class="button" style="margin-top:0.5em;"><%= rb.getString("options.field.add") %></button>

        <script>
        var optsI18n = {
            label:    '<%= rb.getString("options.field.label") %>',
            type:     '<%= rb.getString("options.field.type") %>',
            required: '<%= rb.getString("options.field.required") %>',
            remove:   '<%= rb.getString("options.field.remove") %>',
            options:  '<%= rb.getString("options.field.options") %>',
            typeText:     '<%= rb.getString("options.field.type.text") %>',
            typeTextarea: '<%= rb.getString("options.field.type.textarea") %>',
            typeDate:     '<%= rb.getString("options.field.type.date") %>',
            typeDatetime: '<%= rb.getString("options.field.type.datetime") %>',
            typeTime:     '<%= rb.getString("options.field.type.time") %>',
            typeSelect:   '<%= rb.getString("options.field.type.select") %>',
            typeCheckbox: '<%= rb.getString("options.field.type.checkbox") %>'
        };

        function addField() {
            var id = 'field_' + new Date().getTime();
            var container = document.getElementById('custom-fields-container');
            var div = document.createElement('div');
            div.className = 'custom-field-row';
            div.style.cssText = 'border:1px solid #ddd; padding:0.8em; margin-bottom:0.5em; background:#fafafa;';

            var hidden = document.createElement('input');
            hidden.type = 'hidden'; hidden.name = 'cf_id'; hidden.value = id;
            div.appendChild(hidden);

            var table = document.createElement('table');
            table.style.width = '100%';
            table.innerHTML =
                '<tr>' +
                    '<td style="width:30px; text-align:center; vertical-align:middle;">' +
                        '<button type="button" onclick="moveUp(this)" style="display:block;width:100%">▲</button>' +
                        '<button type="button" onclick="moveDown(this)" style="display:block;width:100%">▼</button>' +
                    '</td>' +
                    '<td>' +
                        '<table style="width:100%">' +
                            '<tr>' +
                                '<td><label>' + optsI18n.label + '</label><br/>' +
                                    '<input type="text" name="cf_label" value="" style="width:180px;" /></td>' +
                                '<td><label>' + optsI18n.type + '</label><br/>' +
                                    '<select name="cf_type" onchange="toggleOptions(this)">' +
                                        '<option value="text">' + optsI18n.typeText + '</option>' +
                                        '<option value="textarea">' + optsI18n.typeTextarea + '</option>' +
                                        '<option value="date">' + optsI18n.typeDate + '</option>' +
                                        '<option value="datetime-local">' + optsI18n.typeDatetime + '</option>' +
                                        '<option value="time">' + optsI18n.typeTime + '</option>' +
                                        '<option value="select">' + optsI18n.typeSelect + '</option>' +
                                        '<option value="checkbox-group">' + optsI18n.typeCheckbox + '</option>' +
                                    '</select>' +
                                '</td>' +
                                '<td style="vertical-align:bottom;">' +
                                    '<label>' +
                                        '<input type="checkbox" name="cf_required" value="' + id + '" /> ' + optsI18n.required +
                                    '</label>' +
                                '</td>' +
                                '<td style="vertical-align:bottom;">' +
                                    '<button type="button" onclick="removeField(this)" style="color:red;">' + optsI18n.remove + '</button>' +
                                '</td>' +
                            '</tr>' +
                            '<tr class="options-row" style="display:none">' +
                                '<td colspan="4">' +
                                    '<label>' + optsI18n.options + '</label><br/>' +
                                    '<textarea name="cf_options" rows="3" style="width:100%; max-width:100%"></textarea>' +
                                '</td>' +
                            '</tr>' +
                        '</table>' +
                    '</td>' +
                '</tr>';
            div.appendChild(table);
            container.appendChild(div);
        }

        function removeField(btn) { btn.closest('.custom-field-row').remove(); }

        function moveUp(btn) {
            var row = btn.closest('.custom-field-row');
            var prev = row.previousElementSibling;
            if (prev && prev.classList.contains('custom-field-row')) row.parentNode.insertBefore(row, prev);
        }

        function moveDown(btn) {
            var row = btn.closest('.custom-field-row');
            var next = row.nextElementSibling;
            if (next && next.classList.contains('custom-field-row')) row.parentNode.insertBefore(next, row);
        }

        function toggleOptions(select) {
            var optRow = select.closest('table').querySelector('.options-row');
            if (optRow) optRow.style.display = (select.value === 'select' || select.value === 'checkbox-group') ? '' : 'none';
        }

        </script>

        <div style="margin-top:1.5em;">
            <input type="submit" value="<%= rb.getString("options.save") %>" class="active" />
        </div>

    </form>
    </div>

</div>
</body>
</html>