<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="java.util.List, java.util.Map" %>
<%
    org.sakaiproject.util.ResourceLoader rb = (org.sakaiproject.util.ResourceLoader) request.getAttribute("rb");
    String sakaiHtmlHead = (String) request.getAttribute("sakaiHtmlHead");
    String bodyClass     = (String) request.getAttribute("bodyClass");
    String toolTitle     = (String) request.getAttribute("toolTitle");
    String siteId        = (String) request.getAttribute("siteId");
    String placementId   = (String) request.getAttribute("placementId");
    int    pageSize      = (Integer) request.getAttribute("pageSize");
    String manageMsg     = (String) request.getAttribute("manageMsg");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> reservations = (List<Map<String, String>>) request.getAttribute("reservations");

    String PENDING   = "PENDING";
    String CONFIRMED = "CONFIRMED";
    String CANCELLED = "CANCELLED";
%>
<!DOCTYPE html>
<html>
<head>
    <%= sakaiHtmlHead != null ? sakaiHtmlHead : "" %>
    <link rel="stylesheet" href="/library/skin/default-skin/tool_base.css"/>
    <style>
        .manage-table { width: 100%; border-collapse: collapse; margin-top: 1em; }
        .manage-table th { background: #e8e8e8; padding: 6px 8px; text-align: left; border-bottom: 2px solid #ccc; }
        .manage-table td { padding: 6px 8px; border-bottom: 1px solid #ddd; vertical-align: middle; }
        .manage-table tr:hover td { background: #f9f9f9; }
        .estado-PENDING   { color: #c06000; font-weight: bold; }
        .estado-CONFIRMED { color: #006600; font-weight: bold; }
        .estado-CANCELLED { color: #990000; font-weight: bold; }
        .btn-confirm { background: #006600; color: #fff; border: none; padding: 3px 10px; cursor: pointer; border-radius: 3px; }
        .btn-cancel  { background: #990000; color: #fff; border: none; padding: 3px 10px; cursor: pointer; border-radius: 3px; margin-left: 4px; }
        .toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 1em; }
        .alertMessage { background: #ffe0e0; border: 1px solid #c00; padding: 8px 12px; margin-bottom: 1em; color: #900; }
    </style>
</head>
<body class="<%= bodyClass != null ? bodyClass : "" %>">
<div class="portletBody">

    <h3 style="display:inline-block; margin-right:16px;"><%= rb.getString("manage.page.title") %> — <%= toolTitle %></h3>
    <% if (siteId != null && placementId != null) { %>
    <a href="/portal/site/<%= siteId %>/tool/<%= placementId %>/tool"
       class="button"><%= rb.getString("manage.back.form") %></a>
    <% } %>

    <% if ("error".equals(manageMsg)) { %>
    <div class="alertMessage"><%= rb.getString("manage.action.error") %></div>
    <% } %>

    <div class="toolbar">
        <label for="pageSizeSelect"><%= rb.getString("manage.show") %></label>
        <select id="pageSizeSelect" onchange="changePageSize(this.value)">
            <% for (int n : new int[]{20, 50, 100, 200}) { %>
            <option value="<%= n %>" <%= n == pageSize ? "selected" : "" %>><%= n %></option>
            <% } %>
        </select>
        <span style="color:#666; font-size:0.9em;">(<%= reservations.size() %> <%= rb.getString("manage.results") %>)</span>
    </div>

    <% if (reservations.isEmpty()) { %>
    <p><%= rb.getString("manage.no.reservations") %></p>
    <% } else { %>
    <table class="manage-table">
        <thead>
            <tr>
                <th><%= rb.getString("manage.col.date") %></th>
                <th><%= rb.getString("manage.col.time") %></th>
                <th><%= rb.getString("manage.col.resource") %></th>
                <th><%= rb.getString("manage.col.user") %></th>
                <th><%= rb.getString("manage.col.status") %></th>
                <th><%= rb.getString("manage.col.actions") %></th>
            </tr>
        </thead>
        <tbody>
        <% for (Map<String, String> r : reservations) {
            String status  = r.get("status");
            String eventId = r.get("eventId");
        %>
            <tr>
                <td><%= r.get("date") %></td>
                <td><%= r.get("time") %> <small style="color:#666">(<%= r.get("duration") %>)</small></td>
                <td><%= r.get("resource") %></td>
                <td><%= r.get("name") %><br/><small style="color:#666"><%= r.get("email") %></small></td>
                <td><span class="estado-<%= status %>">
                    <%= PENDING.equals(status)   ? rb.getString("manage.status.pending")   :
                        CONFIRMED.equals(status) ? rb.getString("manage.status.confirmed") :
                                                   rb.getString("manage.status.cancelled") %>
                </span></td>
                <td>
                <% if (PENDING.equals(status)) { %>
                    <form method="post" action="" style="display:inline">
                        <input type="hidden" name="eventId"  value="<%= eventId %>"/>
                        <input type="hidden" name="pageSize" value="<%= pageSize %>"/>
                        <input type="hidden" name="action"   value="confirm"/>
                        <button type="submit" class="btn-confirm"
                                onclick="return confirm('<%= rb.getString("manage.confirm.ask") %>')">
                            <%= rb.getString("manage.action.confirm") %>
                        </button>
                    </form>
                    <form method="post" action="" style="display:inline">
                        <input type="hidden" name="eventId"  value="<%= eventId %>"/>
                        <input type="hidden" name="pageSize" value="<%= pageSize %>"/>
                        <input type="hidden" name="action"   value="cancel"/>
                        <button type="submit" class="btn-cancel"
                                onclick="return confirm('<%= rb.getString("manage.cancel.ask") %>')">
                            <%= rb.getString("manage.action.cancel") %>
                        </button>
                    </form>
                <% } else { %>
                    —
                <% } %>
                </td>
            </tr>
        <% } %>
        </tbody>
    </table>
    <% } %>

</div>
<script>
function changePageSize(n) {
    var url = window.location.pathname + "?pageSize=" + n;
    window.location.href = url;
}
</script>
</body>
</html>