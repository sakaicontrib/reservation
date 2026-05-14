package org.sakaiproject.reserva;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.calendar.api.Calendar;
import org.sakaiproject.calendar.api.CalendarEvent;
import org.sakaiproject.calendar.api.CalendarEventEdit;
import org.sakaiproject.calendar.api.CalendarService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.InUseException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.Placement;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.util.RequestFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * Generic resource reservation tool for Sakai 25.
 * Supports multi-day reservations (one calendar event per slot).
 * All form fields except user name and email are configurable as custom fields.
 *
 * Routes:
 *   GET  /tool               → reservation form
 *   POST /tool               → process reservation request
 *   GET  /tool/confirm       → confirm reservation (admin)
 *   GET  /tool/options       → options panel (instructor/admin)
 *   POST /tool/options       → save options
 *   GET  /tool/checkConflict → AJAX conflict check
 */
@Slf4j
public class ReservaEspaciosServlet extends HttpServlet {

    // Calendar event property keys
    static final String PROP_TOKEN              = "reserva.token";
    static final String PROP_ESTADO             = "reserva.estado";
    static final String PROP_SOLICITANTE_NOMBRE = "reserva.solicitante.nombre";
    static final String PROP_SOLICITANTE_EMAIL  = "reserva.solicitante.email";
    static final String PROP_CUSTOM_VALUES      = "reserva.custom.values";
    static final String PROP_GROUP_ID           = "reserva.group.id";
    static final String PROP_GROUP_EVENT_IDS    = "reserva.group.event.ids"; // comma-separated, lead only
    static final String PROP_IS_LEAD            = "reserva.is.lead";
    static final String PROP_SLOT_DESCS         = "reserva.slot.descriptions"; // JSON array, lead only

    static final String ESTADO_PENDIENTE  = "PENDIENTE";
    static final String ESTADO_CONFIRMADO = "CONFIRMADO";
    static final String ESTADO_CANCELADO  = "CANCELADO";

    static final String EVENT_TYPE_PENDING   = "Meeting";
    static final String EVENT_TYPE_CONFIRMED = "Activity";

    // Placement configuration keys
    static final String CFG_TOOL_TITLE     = "tool.title";
    static final String CFG_ADMIN_EMAIL    = "admin.email";
    static final String CFG_FROM_EMAIL     = "from.email";
    static final String CFG_CUSTOM_FIELDS   = "custom.fields";
    static final String CFG_RESOURCE_FIELD  = "field.resource";

    static final String DEFAULT_RESOURCE_FIELD = "espacio";

    private CalendarService calendarService;
    private EmailService emailService;
    private ServerConfigurationService serverConfigService;
    private TimeService timeService;
    private ToolManager toolManager;
    private UserDirectoryService userDirectoryService;
    private SecurityService securityService;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            org.sakaiproject.component.api.ComponentManager cm =
                org.sakaiproject.component.cover.ComponentManager.getInstance();
            calendarService      = (CalendarService)            cm.get(CalendarService.class);
            emailService         = (EmailService)               cm.get(EmailService.class);
            serverConfigService  = (ServerConfigurationService) cm.get(ServerConfigurationService.class);
            timeService          = (TimeService)                cm.get(TimeService.class);
            toolManager          = (ToolManager)                cm.get(ToolManager.class);
            userDirectoryService = (UserDirectoryService)       cm.get(UserDirectoryService.class);
            securityService      = (SecurityService)            cm.get(SecurityService.class);
        } catch (Exception e) {
            throw new ServletException("Error initializing ReservaEspaciosServlet", e);
        }
    }

    // =========================================================================
    // Routing
    // =========================================================================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = nvl(req.getPathInfo(), "/");
        if ("/confirm".equals(path))            handleConfirmGet(req, resp);
        else if ("/cancel".equals(path))        handleCancelGet(req, resp);
        else if ("/options".equals(path))       handleOptionsGet(req, resp);
        else if ("/checkConflict".equals(path)) handleCheckConflict(req, resp);
        else                                    handleFormGet(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = nvl(req.getPathInfo(), "/");
        if ("/options".equals(path)) handleOptionsPost(req, resp);
        else                         handleFormPost(req, resp);
    }

    // =========================================================================
    // Default field definitions
    // =========================================================================

    private List<CustomField> defaultFields() {
        return Arrays.asList(
            new CustomField("espacio",         "Aula / Espacio",         "select",         true,  Collections.emptyList()),
            new CustomField("material",     "Material",               "checkbox-group", false, Arrays.asList("Cámara", "Auriculares", "Corte de red")),
            new CustomField("motivo",       "Motivo",                 "textarea",       false, Collections.emptyList())
        );
    }

    private List<CustomField> getCustomFields() {
        String json = getPlacementProperties().getProperty(CFG_CUSTOM_FIELDS, "");
        if (json.isEmpty() || "[]".equals(json.trim())) return defaultFields();
        return CustomFieldSerializer.fromJson(json);
    }

    private String getEndFieldType() {
        return "duration-slot";
    }

    // =========================================================================
    // GET /tool
    // =========================================================================

    private void handleFormGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        populateSakaiHead(req);
        populateUserData(req);
        populateFormAttributes(req);
        req.setAttribute("calendarMissing", !calendarExists(getSiteId()));
        resp.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/index.jsp").include(req, resp);
    }

    // =========================================================================
    // POST /tool
    // =========================================================================

    private void handleFormPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        populateSakaiHead(req);
        populateUserData(req);
        populateFormAttributes(req);

        String nombre = nvl((String) req.getAttribute("nombre"), "");
        String email  = nvl((String) req.getAttribute("email"),  "");

        if (!calendarExists(getSiteId())) {
            req.setAttribute("calendarMissing", true);
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/index.jsp").include(req, resp);
            return;
        }

        Properties props       = getPlacementProperties();
        List<CustomField> customFields = getCustomFields();
        String resourceFieldId = props.getProperty(CFG_RESOURCE_FIELD, DEFAULT_RESOURCE_FIELD);

        // Read custom field values (start/end come from slot section, not from custom fields)
        Map<String, String> customValues = readCustomValues(req, customFields);
        req.setAttribute("customValues", customValues);

        // Validate required custom fields
        for (CustomField cf : customFields) {
            if (cf.isRequired() && customValues.getOrDefault(cf.getId(), "").isEmpty()) {
                req.setAttribute("error", "El campo \"" + cf.getLabel() + "\" es obligatorio.");
                resp.setContentType("text/html;charset=UTF-8");
                req.getRequestDispatcher("/index.jsp").include(req, resp);
                return;
            }
        }

        String resource = customValues.getOrDefault(resourceFieldId, "");
        if (resource.isEmpty()) {
            req.setAttribute("error", "Debe indicar el recurso a reservar.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/index.jsp").include(req, resp);
            return;
        }

        // Read slots
        int slotCount = parseIntSafe(nvl(req.getParameter("slot_count"), "0"), 0);
        if (slotCount < 1) {
            req.setAttribute("error", "Debe añadir al menos una franja horaria.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/index.jsp").include(req, resp);
            return;
        }

        String endFieldType = getEndFieldType();
        List<long[]>  rangos    = new ArrayList<>();
        List<String>  slotDescs = new ArrayList<>();

        for (int i = 0; i < slotCount; i++) {
            String date   = nvl(req.getParameter("slot_date_" + i), "");
            boolean allDay = req.getParameter("slot_allday_" + i) != null;

            if (date.isEmpty()) continue;

            long[] rango;
            String desc;
            try {
                if (allDay) {
                    rango = calcularRangoAllDay(date);
                    desc  = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (día completo)";
                } else {
                    String startTime = nvl(req.getParameter("slot_start_" + i), "");
                    String endValue  = nvl(req.getParameter("slot_end_" + i),   "");
                    if (startTime.isEmpty() || endValue.isEmpty()) {
                        req.setAttribute("error", "La franja " + (i + 1) + " necesita hora de inicio y duración.");
                        resp.setContentType("text/html;charset=UTF-8");
                        req.getRequestDispatcher("/index.jsp").include(req, resp);
                        return;
                    }
                    rango = calcularRango(date + "T" + startTime, endValue, endFieldType);
                    if (rango[1] <= rango[0]) {
                        req.setAttribute("error", "La duración debe ser mayor que cero en la franja " + (i + 1) + ".");
                        resp.setContentType("text/html;charset=UTF-8");
                        req.getRequestDispatcher("/index.jsp").include(req, resp);
                        return;
                    }
                    desc = formatearFecha(rango[0]) + " – " + formatearFecha(rango[1]);
                }
            } catch (DateTimeParseException | NumberFormatException e) {
                req.setAttribute("error", "Formato de fecha u hora no válido en la franja " + (i + 1) + ".");
                resp.setContentType("text/html;charset=UTF-8");
                req.getRequestDispatcher("/index.jsp").include(req, resp);
                return;
            }
            rangos.add(rango);
            slotDescs.add(desc);
        }

        if (rangos.isEmpty()) {
            req.setAttribute("error", "Debe añadir al menos una franja horaria válida.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/index.jsp").include(req, resp);
            return;
        }

        // Check conflicts for each slot
        String siteId = getSiteId();
        for (int i = 0; i < rangos.size(); i++) {
            String conflicto = comprobarConflicto(siteId, resource, rangos.get(i)[0], rangos.get(i)[1]);
            if (conflicto != null) {
                req.setAttribute("error", "Franja " + (i + 1) + ": " + conflicto);
                resp.setContentType("text/html;charset=UTF-8");
                req.getRequestDispatcher("/index.jsp").include(req, resp);
                return;
            }
        }

        // Create calendar events
        String groupId          = UUID.randomUUID().toString();
        String token            = UUID.randomUUID().toString();
        String customValuesJson = CustomFieldSerializer.valuesToJson(customValues);
        String slotDescsJson    = buildSlotDescsJson(slotDescs);
        List<String> eventIds = new ArrayList<>();
        for (int i = 0; i < rangos.size(); i++) {
            boolean isLead = (i == 0);
            String eventId = crearEvento(siteId, resource, nombre, email,
                    rangos.get(i)[0], rangos.get(i)[1], token,
                    isLead ? customValuesJson : "{}",
                    isLead, groupId, slotDescsJson, customFields);
            if (eventId == null) {
                req.setAttribute("error", "Error al crear la reserva. Inténtelo de nuevo.");
                resp.setContentType("text/html;charset=UTF-8");
                req.getRequestDispatcher("/index.jsp").include(req, resp);
                return;
            }
            eventIds.add(eventId);
        }

        // Store all event IDs on lead event
        actualizarGroupEventIds(siteId, eventIds.get(0), String.join(",", eventIds));

        // Build confirmation URL (always uses lead event)
        Placement placement = toolManager.getCurrentPlacement();
        String serverUrl    = serverConfigService.getServerUrl();
        String baseUrl      = serverUrl + "/portal/site/" + siteId + "/tool/" + placement.getId();
        String confirmUrl   = baseUrl + "/confirm?eventId=" + urlEncode(eventIds.get(0)) + "&token=" + token;
        String cancelUrl    = baseUrl + "/cancel?eventId="  + urlEncode(eventIds.get(0)) + "&token=" + token;

        String adminEmail = props.getProperty(CFG_ADMIN_EMAIL, "");
        String fromEmail  = getFromEmail(props);
        String toolTitle  = props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios");

        if (!adminEmail.isEmpty()) {
            try {
                String asunto = "[" + toolTitle + "] Nueva solicitud: " + resource;
                String cuerpo = buildEmailAdmin(nombre, email, resource, confirmUrl, cancelUrl,
                        customFields, customValues, slotDescs);
                emailService.send(fromEmail, adminEmail, asunto, cuerpo, null, null, null);
            } catch (Exception e) {
                log.warn("Error sending admin email: {}", e.getMessage(), e);
            }
        }

        if (!email.isEmpty()) {
            try {
                String asunto = "[" + toolTitle + "] Solicitud recibida: " + resource;
                String cuerpo = buildEmailAcuse(nombre, resource,
                        customFields, customValues, slotDescs);
                emailService.send(fromEmail, email, asunto, cuerpo, null, null, null);
            } catch (Exception e) {
                log.warn("Error sending acknowledgement email: {}", e.getMessage(), e);
            }
        }

        req.setAttribute("exito", true);
        req.setAttribute("mensajeExito",
                "Su solicitud ha sido registrada. Recibirá un correo de confirmación cuando sea aprobada.");
        resp.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/index.jsp").include(req, resp);
    }

    // =========================================================================
    // GET /tool/confirm
    // =========================================================================

    private void handleConfirmGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        populateSakaiHead(req);

        Properties props = getPlacementProperties();
        req.setAttribute("toolTitle", props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios"));

        String eventId = nvl(req.getParameter("eventId"), "");
        String token   = nvl(req.getParameter("token"),   "");

        if (eventId.isEmpty() || token.isEmpty()) {
            req.setAttribute("confirmError", "Enlace de confirmación no válido.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/confirm.jsp").include(req, resp);
            return;
        }

        if (!esInstructor(req)) {
            req.setAttribute("confirmError", "No tiene permisos para confirmar reservas en este sitio.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/confirm.jsp").include(req, resp);
            return;
        }

        String siteId = getSiteId();
        List<CustomField> customFields = getCustomFields();
        Map<String, String> customValues = leerCustomValues(siteId, eventId, customFields);
        List<String> slotDescs = leerSlotDescs(siteId, eventId);

        String resultado = confirmarReserva(siteId, eventId, token);

        if (resultado == null) {
            req.setAttribute("confirmExito",   true);
            req.setAttribute("confirmMensaje", "La reserva ha sido confirmada correctamente.");
            req.setAttribute("customFields",   customFields);
            req.setAttribute("customValues",   customValues);
            req.setAttribute("slotDescs",      slotDescs);
        } else {
            req.setAttribute("confirmError", resultado);
        }

        resp.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/confirm.jsp").include(req, resp);
    }

    // =========================================================================
    // GET /tool/cancel — admin cancellation
    // =========================================================================

    private void handleCancelGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        populateSakaiHead(req);

        Properties props = getPlacementProperties();
        req.setAttribute("toolTitle", props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios"));

        String eventId = nvl(req.getParameter("eventId"), "");
        String token   = nvl(req.getParameter("token"),   "");

        if (eventId.isEmpty() || token.isEmpty()) {
            req.setAttribute("cancelError", "Enlace de cancelación no válido.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/cancel.jsp").include(req, resp);
            return;
        }

        if (!esInstructor(req)) {
            req.setAttribute("cancelError", "No tiene permisos para cancelar reservas en este sitio.");
            resp.setContentType("text/html;charset=UTF-8");
            req.getRequestDispatcher("/cancel.jsp").include(req, resp);
            return;
        }

        String resultado = cancelarReserva(getSiteId(), eventId, token);

        if (resultado == null) {
            req.setAttribute("cancelExito", true);
            req.setAttribute("cancelMensaje", "La reserva ha sido cancelada correctamente.");
        } else {
            req.setAttribute("cancelError", resultado);
        }

        resp.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/cancel.jsp").include(req, resp);
    }

    // =========================================================================
    // GET /tool/checkConflict
    // =========================================================================

    private void handleCheckConflict(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String resource   = nvl(req.getParameter("resource"),   "");
        String startValue = nvl(req.getParameter("startValue"), "");
        String endValue   = nvl(req.getParameter("endValue"),   "");
        boolean allDay    = "true".equals(req.getParameter("allday"));

        resp.setContentType("application/json;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        if (resource.isEmpty() || startValue.isEmpty()) {
            out.print("{\"conflicto\":false}");
            return;
        }

        try {
            long[] rango;
            if (allDay) {
                rango = calcularRangoAllDay(startValue);
            } else {
                if (endValue.isEmpty()) { out.print("{\"conflicto\":false}"); return; }
                rango = calcularRango(startValue, endValue, getEndFieldType());
            }
            if (rango[1] <= rango[0]) { out.print("{\"conflicto\":false}"); return; }
            String msg = comprobarConflicto(getSiteId(), resource, rango[0], rango[1]);
            if (msg == null) out.print("{\"conflicto\":false}");
            else             out.print("{\"conflicto\":true,\"mensaje\":" + jsonString(msg) + "}");
        } catch (Exception e) {
            out.print("{\"conflicto\":false}");
        }
    }

    // =========================================================================
    // GET /tool/options
    // =========================================================================

    private void handleOptionsGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!esInstructor(req)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN); return; }

        populateSakaiHead(req);

        Properties props = getPlacementProperties();
        req.setAttribute("toolTitle",       props.getProperty(CFG_TOOL_TITLE,     "Reserva de Espacios"));
        req.setAttribute("adminEmail",      props.getProperty(CFG_ADMIN_EMAIL,    ""));
        req.setAttribute("fromEmail",       props.getProperty(CFG_FROM_EMAIL,     ""));
        req.setAttribute("resourceFieldId", props.getProperty(CFG_RESOURCE_FIELD, DEFAULT_RESOURCE_FIELD));
        req.setAttribute("customFields",    getCustomFields());

        resp.setContentType("text/html;charset=UTF-8");
        req.getRequestDispatcher("/options.jsp").include(req, resp);
    }

    // =========================================================================
    // POST /tool/options
    // =========================================================================

    private void handleOptionsPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        if (!esInstructor(req)) { resp.sendError(HttpServletResponse.SC_FORBIDDEN); return; }

        Placement placement = toolManager.getCurrentPlacement();
        Properties props    = placement.getPlacementConfig();

        props.setProperty(CFG_TOOL_TITLE,     nvl(req.getParameter("toolTitle"),       "Reserva de Espacios"));
        props.setProperty(CFG_ADMIN_EMAIL,    nvl(req.getParameter("adminEmail"),      ""));
        props.setProperty(CFG_FROM_EMAIL,     nvl(req.getParameter("fromEmail"),       ""));
        props.setProperty(CFG_RESOURCE_FIELD, nvl(req.getParameter("resourceFieldId"), DEFAULT_RESOURCE_FIELD));

        String[] ids       = req.getParameterValues("cf_id");
        String[] labels    = req.getParameterValues("cf_label");
        String[] types     = req.getParameterValues("cf_type");
        String[] requireds = req.getParameterValues("cf_required");
        String[] optionss  = req.getParameterValues("cf_options");

        List<CustomField> fields = new ArrayList<>();
        if (ids != null) {
            Set<String> requiredSet = new HashSet<>();
            if (requireds != null) requiredSet.addAll(Arrays.asList(requireds));
            for (int i = 0; i < ids.length; i++) {
                if (ids[i] == null || ids[i].trim().isEmpty()) continue;
                String label    = labels  != null && i < labels.length  ? labels[i]  : "";
                String type     = types   != null && i < types.length   ? types[i]   : "text";
                boolean required = requiredSet.contains(ids[i]);
                List<String> opts = new ArrayList<>();
                if (("select".equals(type) || "checkbox-group".equals(type))
                        && optionss != null && i < optionss.length) {
                    for (String opt : optionss[i].split("\n")) {
                        opt = opt.trim();
                        if (!opt.isEmpty()) opts.add(opt);
                    }
                }
                fields.add(new CustomField(ids[i], label, type, required, opts));
            }
        }
        props.setProperty(CFG_CUSTOM_FIELDS, CustomFieldSerializer.toJson(fields));
        placement.save();

        resp.sendRedirect("/portal/site/" + placement.getContext() + "/tool/" + placement.getId() + "/tool");
    }

    // =========================================================================
    // Calendar logic
    // =========================================================================

    private String comprobarConflicto(String siteId, String resource, long inicioMs, long finMs) {
        SecurityAdvisor advisor = calendarReadAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);

            Time tIni = timeService.newTime(inicioMs);
            Time tFin = timeService.newTime(finMs);
            List<CalendarEvent> eventos = cal.getEvents(timeService.newTimeRange(tIni, tFin, true, false), null);

            for (CalendarEvent ev : eventos) {
                if (ESTADO_CANCELADO.equals(ev.getField(PROP_ESTADO))) continue;
                if (resource.equals(ev.getLocation())) {
                    String iniStr = formatearFecha(ev.getRange().firstTime().getTime());
                    String finStr = formatearFecha(ev.getRange().lastTime().getTime());
                    return "El recurso \"" + resource + "\" ya está reservado de " + iniStr
                            + " a " + finStr + " (" + ev.getDisplayName() + ").";
                }
            }
            return null;
        } catch (IdUnusedException e) {
            log.debug("Calendar for site {} does not exist yet", siteId);
            return null;
        } catch (PermissionException e) {
            log.warn("No permission to read calendar for site {}", siteId, e);
            return null;
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private String crearEvento(String siteId, String resource, String nombre,
            String email, long inicioMs, long finMs, String token,
            String customValuesJson, boolean isLead, String groupId,
            String slotDescsJson, List<CustomField> customFields) {
        SecurityAdvisor advisor = calendarWriteAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);

            CalendarEventEdit edit = cal.addEvent();
            edit.setDisplayName("[PENDIENTE] Reserva de " + resource);
            edit.setType(EVENT_TYPE_PENDING);
            edit.setLocation(resource);

            if (isLead) {
                edit.setDescription(buildEventDescription(nombre, email, customFields,
                        CustomFieldSerializer.valuesFromJson(customValuesJson, customFields)));
            } else {
                edit.setDescription("Solicitante: " + nombre + "\nEmail: " + email);
            }

            Time tIni = timeService.newTime(inicioMs);
            Time tFin = timeService.newTime(finMs);
            edit.setRange(timeService.newTimeRange(tIni, tFin, true, false));

            edit.setField(PROP_TOKEN,              token);
            edit.setField(PROP_ESTADO,             ESTADO_PENDIENTE);
            edit.setField(PROP_SOLICITANTE_NOMBRE, nombre);
            edit.setField(PROP_SOLICITANTE_EMAIL,  email);
            edit.setField(PROP_GROUP_ID,           groupId);
            edit.setField(PROP_IS_LEAD,            isLead ? "true" : "false");
            if (isLead) {
                edit.setField(PROP_CUSTOM_VALUES, customValuesJson);
                edit.setField(PROP_SLOT_DESCS,    slotDescsJson);
            }

            String eventId = edit.getId();
            cal.commitEvent(edit);
            return eventId;

        } catch (IdUnusedException e) {
            log.error("Calendar for site {} not found", siteId, e);
        } catch (PermissionException e) {
            log.error("No permission to create event in calendar for site {}", siteId, e);
        } catch (Exception e) {
            log.error("Error creating event", e);
        } finally {
            securityService.popAdvisor(advisor);
        }
        return null;
    }

    private void actualizarGroupEventIds(String siteId, String leadEventId, String eventIds) {
        SecurityAdvisor advisor = calendarWriteAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);
            CalendarEventEdit edit = cal.getEditEvent(leadEventId, CalendarService.EVENT_MODIFY_CALENDAR);
            edit.setField(PROP_GROUP_EVENT_IDS, eventIds);
            cal.commitEvent(edit);
        } catch (Exception e) {
            log.warn("Could not update group event IDs on lead event {}", leadEventId, e);
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private String confirmarReserva(String siteId, String eventId, String token) {
        SecurityAdvisor advisor = calendarWriteAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);

            CalendarEventEdit edit = cal.getEditEvent(eventId, CalendarService.EVENT_MODIFY_CALENDAR);

            String storedToken = edit.getField(PROP_TOKEN);
            if (!token.equals(storedToken)) {
                cal.cancelEvent(edit);
                return "El enlace de confirmación no es válido o ha caducado.";
            }

            String estado = edit.getField(PROP_ESTADO);
            if (ESTADO_CONFIRMADO.equals(estado)) {
                cal.cancelEvent(edit);
                return "Esta reserva ya fue confirmada anteriormente.";
            }
            if (ESTADO_CANCELADO.equals(estado)) {
                cal.cancelEvent(edit);
                return "Esta reserva fue cancelada y no puede confirmarse.";
            }

            String solEmail      = nvl(edit.getField(PROP_SOLICITANTE_EMAIL),  "");
            String solNombre     = nvl(edit.getField(PROP_SOLICITANTE_NOMBRE), "");
            String resource      = nvl(edit.getLocation(), "");
            String groupEventIds = nvl(edit.getField(PROP_GROUP_EVENT_IDS), eventId);
            String slotDescsJson = nvl(edit.getField(PROP_SLOT_DESCS), "[]");

            // Confirm lead event
            edit.setDisplayName("[CONFIRMADA] Reserva de " + resource);
            edit.setType(EVENT_TYPE_CONFIRMED);
            edit.setField(PROP_ESTADO, ESTADO_CONFIRMADO);
            edit.setField(PROP_TOKEN,  "");
            cal.commitEvent(edit);

            // Confirm all other events in the group
            for (String gId : groupEventIds.split(",")) {
                gId = gId.trim();
                if (gId.isEmpty() || gId.equals(eventId)) continue;
                try {
                    CalendarEventEdit gEdit = cal.getEditEvent(gId, CalendarService.EVENT_MODIFY_CALENDAR);
                    gEdit.setDisplayName("[CONFIRMADA] Reserva de " + resource);
                    gEdit.setType(EVENT_TYPE_CONFIRMED);
                    gEdit.setField(PROP_ESTADO, ESTADO_CONFIRMADO);
                    cal.commitEvent(gEdit);
                } catch (Exception e) {
                    log.warn("Could not confirm event {} in group", gId, e);
                }
            }

            // Send confirmation email
            if (!solEmail.isEmpty()) {
                try {
                    Properties props = getPlacementProperties();
                    String toolTitle = props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios");
                    List<String> slotDescs = parseSlotDescs(slotDescsJson);
                    String asunto = "[" + toolTitle + "] Reserva confirmada: " + resource;
                    String cuerpo = buildEmailConfirmacion(solNombre, resource, slotDescs);
                    emailService.send(getFromEmail(props), solEmail, asunto, cuerpo, null, null, null);
                } catch (Exception e) {
                    log.warn("Error sending confirmation email: {}", e.getMessage(), e);
                }
            }

            return null;

        } catch (IdUnusedException e) {
            return "Reserva no encontrada. El enlace puede haber expirado.";
        } catch (InUseException e) {
            return "La reserva está siendo procesada. Inténtelo de nuevo.";
        } catch (PermissionException e) {
            return "Sin permisos para confirmar esta reserva.";
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private String cancelarReserva(String siteId, String eventId, String token) {
        SecurityAdvisor advisor = calendarWriteAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);

            CalendarEventEdit edit = cal.getEditEvent(eventId, CalendarService.EVENT_MODIFY_CALENDAR);

            String storedToken = edit.getField(PROP_TOKEN);
            if (!token.equals(storedToken)) {
                cal.cancelEvent(edit);
                return "El enlace de cancelación no es válido o ya ha sido utilizado.";
            }

            String estado = edit.getField(PROP_ESTADO);
            if (ESTADO_CANCELADO.equals(estado)) {
                cal.cancelEvent(edit);
                return "Esta reserva ya fue cancelada anteriormente.";
            }
            if (ESTADO_CONFIRMADO.equals(estado)) {
                cal.cancelEvent(edit);
                return "Esta reserva ya fue confirmada y no puede cancelarse mediante este enlace.";
            }

            String solEmail      = nvl(edit.getField(PROP_SOLICITANTE_EMAIL),  "");
            String solNombre     = nvl(edit.getField(PROP_SOLICITANTE_NOMBRE), "");
            String resource      = nvl(edit.getLocation(), "");
            String groupEventIds = nvl(edit.getField(PROP_GROUP_EVENT_IDS), eventId);
            String slotDescsJson = nvl(edit.getField(PROP_SLOT_DESCS), "[]");

            // Cancel lead event
            edit.setDisplayName("[CANCELADA] Reserva de " + resource);
            edit.setField(PROP_ESTADO, ESTADO_CANCELADO);
            edit.setField(PROP_TOKEN,  "");
            cal.commitEvent(edit);

            // Cancel all other events in the group
            for (String gId : groupEventIds.split(",")) {
                gId = gId.trim();
                if (gId.isEmpty() || gId.equals(eventId)) continue;
                try {
                    CalendarEventEdit gEdit = cal.getEditEvent(gId, CalendarService.EVENT_MODIFY_CALENDAR);
                    gEdit.setDisplayName("[CANCELADA] Reserva de " + resource);
                    gEdit.setField(PROP_ESTADO, ESTADO_CANCELADO);
                    cal.commitEvent(gEdit);
                } catch (Exception e) {
                    log.warn("Could not cancel event {} in group", gId, e);
                }
            }

            // Send cancellation email to requester
            if (!solEmail.isEmpty()) {
                try {
                    Properties props = getPlacementProperties();
                    String toolTitle = props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios");
                    List<String> slotDescs = parseSlotDescs(slotDescsJson);
                    String asunto = "[" + toolTitle + "] Reserva cancelada: " + resource;
                    String cuerpo = buildEmailCancelacion(solNombre, resource, slotDescs);
                    emailService.send(getFromEmail(props), solEmail, asunto, cuerpo, null, null, null);
                } catch (Exception e) {
                    log.warn("Error sending cancellation email: {}", e.getMessage(), e);
                }
            }

            return null;

        } catch (IdUnusedException e) {
            return "Reserva no encontrada. El enlace puede haber expirado.";
        } catch (InUseException e) {
            return "La reserva está siendo procesada. Inténtelo de nuevo.";
        } catch (PermissionException e) {
            return "Sin permisos para cancelar esta reserva.";
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void populateFormAttributes(HttpServletRequest req) {
        Properties props = getPlacementProperties();
        req.setAttribute("toolTitle",       props.getProperty(CFG_TOOL_TITLE, "Reserva de Espacios"));
        req.setAttribute("esInstructor",    esInstructor(req));
        req.setAttribute("customFields",    getCustomFields());
        req.setAttribute("resourceFieldId", props.getProperty(CFG_RESOURCE_FIELD, DEFAULT_RESOURCE_FIELD));
        req.setAttribute("endFieldType",    getEndFieldType());
    }

    private Map<String, String> readCustomValues(HttpServletRequest req, List<CustomField> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        for (CustomField cf : fields) {
            // checkbox-group uses hidden input serialized by JS
            values.put(cf.getId(), nvl(req.getParameter("custom_" + cf.getId()), ""));
        }
        return values;
    }

    private Map<String, String> leerCustomValues(String siteId, String eventId, List<CustomField> fields) {
        SecurityAdvisor advisor = calendarReadAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);
            CalendarEvent ev = cal.getEvent(eventId);
            return CustomFieldSerializer.valuesFromJson(ev.getField(PROP_CUSTOM_VALUES), fields);
        } catch (Exception e) {
            log.warn("Could not read custom field values for event {}", eventId, e);
            return new LinkedHashMap<>();
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private List<String> leerSlotDescs(String siteId, String eventId) {
        SecurityAdvisor advisor = calendarReadAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            String calRef = calendarService.calendarReference(siteId, "main");
            Calendar cal  = calendarService.getCalendar(calRef);
            CalendarEvent ev = cal.getEvent(eventId);
            return parseSlotDescs(nvl(ev.getField(PROP_SLOT_DESCS), "[]"));
        } catch (Exception e) {
            log.warn("Could not read slot descriptions for event {}", eventId, e);
            return Collections.emptyList();
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private boolean calendarExists(String siteId) {
        SecurityAdvisor advisor = calendarReadAdvisor();
        try {
            securityService.pushAdvisor(advisor);
            calendarService.getCalendar(calendarService.calendarReference(siteId, "main"));
            return true;
        } catch (IdUnusedException e) {
            return false;
        } catch (Exception e) {
            log.warn("Could not check calendar existence for site {}", siteId, e);
            return true; // assume it exists to avoid blocking the tool on unexpected errors
        } finally {
            securityService.popAdvisor(advisor);
        }
    }

    private SecurityAdvisor calendarReadAdvisor() {
        return (userId, function, reference) -> {
            if (function != null && function.startsWith("calendar.read"))
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            return SecurityAdvisor.SecurityAdvice.PASS;
        };
    }

    private SecurityAdvisor calendarWriteAdvisor() {
        return (userId, function, reference) -> {
            if (function != null && (function.startsWith("calendar.new")
                    || function.startsWith("calendar.revise")
                    || function.startsWith("calendar.read")))
                return SecurityAdvisor.SecurityAdvice.ALLOWED;
            return SecurityAdvisor.SecurityAdvice.PASS;
        };
    }

    private void populateUserData(HttpServletRequest req) {
        try {
            Session session = (Session) req.getAttribute(RequestFilter.ATTR_SESSION);
            if (session != null && session.getUserId() != null) {
                User user = userDirectoryService.getUser(session.getUserId());
                req.setAttribute("nombre", user.getDisplayName());
                req.setAttribute("email",  user.getEmail());
            }
        } catch (Exception e) {
            log.warn("Could not get current user", e);
        }
    }

    private void populateSakaiHead(HttpServletRequest req) {
        req.setAttribute("sakaiHtmlHead", (String) req.getAttribute("sakai.html.head"));
        req.setAttribute("bodyClass",     (String) req.getAttribute("sakai.html.body.class"));
        Placement placement = toolManager.getCurrentPlacement();
        if (placement != null) {
            req.setAttribute("siteId",      placement.getContext());
            req.setAttribute("placementId", placement.getId());
        }
    }

    private Properties getPlacementProperties() {
        try {
            Placement placement = toolManager.getCurrentPlacement();
            if (placement != null) return placement.getPlacementConfig();
        } catch (Exception e) {
            log.warn("Could not get placement", e);
        }
        return new Properties();
    }

    private String getSiteId() {
        Placement p = toolManager.getCurrentPlacement();
        return p != null ? p.getContext() : "";
    }

    private boolean esInstructor(HttpServletRequest req) {
        try {
            Session session = (Session) req.getAttribute(RequestFilter.ATTR_SESSION);
            if (session == null) return false;
            Placement placement = toolManager.getCurrentPlacement();
            if (placement == null) return false;
            return securityService.unlock(session.getUserId(), "site.upd", "/site/" + placement.getContext());
        } catch (Exception e) {
            log.warn("Error checking permissions", e);
            return false;
        }
    }

    private String getFromEmail(Properties props) {
        String from = props.getProperty(CFG_FROM_EMAIL, "").trim();
        if (!from.isEmpty()) return from;
        String domain = serverConfigService.getServerUrl()
                .replaceAll("https?://", "").replaceAll("/.*", "");
        return "noreply@" + domain;
    }

    /**
     * Computes a time range from a start datetime-local string and an end value.
     * The end value is either minutes (duration-slot) or HH:mm (time/time-slot).
     */
    private long[] calcularRango(String startDtLocal, String endValue, String endFieldType) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime startDT = LocalDateTime.parse(startDtLocal,
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        LocalDateTime endDT;
        if ("duration-slot".equals(endFieldType) || (!endValue.contains(":"))) {
            int minutes = Integer.parseInt(endValue);
            endDT = startDT.plusMinutes(minutes);
        } else {
            endDT = startDT.toLocalDate().atTime(LocalTime.parse(endValue));
        }
        return new long[]{
            startDT.atZone(zone).toInstant().toEpochMilli(),
            endDT.atZone(zone).toInstant().toEpochMilli()
        };
    }

    private long[] calcularRangoAllDay(String date) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate d = LocalDate.parse(date);
        return new long[]{
            d.atStartOfDay(zone).toInstant().toEpochMilli(),
            d.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        };
    }

    private String formatearFecha(long epochMs) {
        return LocalDateTime.ofEpochSecond(epochMs / 1000, 0, java.time.ZoneOffset.UTC)
                .atZone(java.time.ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String buildSlotDescsJson(List<String> descs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < descs.size(); i++) {
            sb.append("\"").append(descs.get(i)
                    .replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            if (i < descs.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<String> parseSlotDescs(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.trim().equals("[]")) return result;
        json = json.trim();
        if (json.startsWith("[")) json = json.substring(1);
        if (json.endsWith("]"))   json = json.substring(0, json.length() - 1);
        // Split on commas not inside quotes
        int depth = 0; int start = 0; boolean inStr = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i-1) != '\\')) inStr = !inStr;
            if (!inStr && c == ',') {
                String item = json.substring(start, i).trim();
                if (item.startsWith("\"")) item = item.substring(1);
                if (item.endsWith("\""))   item = item.substring(0, item.length() - 1);
                result.add(item.replace("\\\"", "\""));
                start = i + 1;
            }
        }
        String last = json.substring(start).trim();
        if (!last.isEmpty()) {
            if (last.startsWith("\"")) last = last.substring(1);
            if (last.endsWith("\""))   last = last.substring(0, last.length() - 1);
            result.add(last.replace("\\\"", "\""));
        }
        return result;
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { return s; }
    }

    private String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                        .replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }

    private String nvl(String s, String def) {
        return (s != null && !s.isEmpty()) ? s : def;
    }

    // =========================================================================
    // Email and description builders
    // =========================================================================

    private String buildEventDescription(String nombre, String email,
            List<CustomField> fields, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append("Solicitante: ").append(nombre).append("\nEmail: ").append(email).append("\n");
        appendCustomValues(sb, fields, values);
        return sb.toString();
    }

    private String buildEmailAdmin(String nombre, String email, String resource,
            String confirmUrl, String cancelUrl, List<CustomField> fields,
            Map<String, String> values, List<String> slotDescs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Se ha recibido una nueva solicitud de reserva.\n\n");
        sb.append("Solicitante : ").append(nombre).append("\n");
        sb.append("Email       : ").append(email).append("\n");
        sb.append("Recurso     : ").append(resource).append("\n");
        appendSlotDescs(sb, slotDescs);
        appendCustomValues(sb, fields, values);
        sb.append("\nPara CONFIRMAR la reserva acceda al siguiente enlace:\n");
        sb.append(confirmUrl).append("\n\n");
        sb.append("Para CANCELAR la reserva acceda al siguiente enlace:\n");
        sb.append(cancelUrl).append("\n\n");
        sb.append("(Deberá estar autenticado en Sakai para confirmar o cancelar la reserva)\n");
        return sb.toString();
    }

    private String buildEmailAcuse(String nombre, String resource,
            List<CustomField> fields, Map<String, String> values,
            List<String> slotDescs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimado/a ").append(nombre).append(",\n\n");
        sb.append("Hemos recibido su solicitud de reserva. Le informaremos\n");
        sb.append("por correo electrónico cuando sea confirmada.\n\n");
        sb.append("Recurso : ").append(resource).append("\n");
        appendSlotDescs(sb, slotDescs);
        appendCustomValues(sb, fields, values);
        sb.append("\nGracias por usar el sistema de reservas.\n");
        return sb.toString();
    }

    private String buildEmailCancelacion(String nombre, String resource, List<String> slotDescs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimado/a ").append(nombre).append(",\n\n");
        sb.append("Su solicitud de reserva ha sido CANCELADA.\n\n");
        sb.append("  Recurso : ").append(resource).append("\n");
        appendSlotDescs(sb, slotDescs);
        sb.append("\nSi tiene alguna duda, contacte con el administrador del sitio.\n");
        return sb.toString();
    }

    private String buildEmailConfirmacion(String nombre, String resource, List<String> slotDescs) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimado/a ").append(nombre).append(",\n\n");
        sb.append("Su reserva ha sido CONFIRMADA.\n\n");
        sb.append("  Recurso : ").append(resource).append("\n");
        appendSlotDescs(sb, slotDescs);
        sb.append("\nGracias por usar el sistema de reservas.\n");
        return sb.toString();
    }

    private void appendSlotDescs(StringBuilder sb, List<String> slotDescs) {
        if (slotDescs == null || slotDescs.isEmpty()) return;
        sb.append(slotDescs.size() == 1 ? "  Fecha     : " : "  Franjas   :\n");
        for (String desc : slotDescs) {
            sb.append(slotDescs.size() == 1 ? desc : "    - " + desc).append("\n");
        }
    }

    private void appendCustomValues(StringBuilder sb, List<CustomField> fields,
            Map<String, String> values) {
        appendCustomValues(sb, fields, values, null, null);
    }

    private void appendCustomValues(StringBuilder sb, List<CustomField> fields,
            Map<String, String> values, String skipStart, String skipEnd) {
        if (fields == null || fields.isEmpty()) return;
        for (CustomField cf : fields) {
            if (cf.getId().equals(skipStart) || cf.getId().equals(skipEnd)) continue;
            String val = values.getOrDefault(cf.getId(), "");
            if (!val.isEmpty()) sb.append("  ").append(cf.getLabel()).append(" : ").append(val).append("\n");
        }
    }
}