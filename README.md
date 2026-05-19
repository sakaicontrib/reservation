# Space Reservation Tool – User and Administrator Guide

## What is this tool for?

This tool allows users to request the reservation of a resource (classroom, meeting room, equipment, etc.) for one or more time slots. Requests are recorded in the site's calendar and must be approved by a site maintainer before they are considered confirmed.

---

## How to submit a request

1. **Open the tool** from the Sakai site menu.

2. **Fill in the form:**
   - Your name and email address are filled in automatically from your Sakai account and cannot be modified.
   - Select the **resource** you wish to reserve (classroom, room, etc.).
   - Fill in any additional fields shown (these vary depending on the site configuration).

3. **Add time slots:**
   - One slot is shown by default. Enter the **date**, **start time** and **duration**.
   - To book the **full day**, check the "Full day" checkbox — the time fields will be hidden.
   - To reserve **multiple days**, click **"+ Add day"** and fill in each slot separately. Each day can have a different schedule.
   - If a conflict is detected with an existing reservation, a red warning will appear below that slot and you will not be able to submit until it is resolved.

4. **Submit the request** by clicking "Submit request".

> **Note:** Only users with the `access` role can submit reservation requests. Site maintainers can view the form but cannot submit it.

---

## What happens next

- You will receive an **acknowledgement email** confirming that your request has been registered.
- The administrator will receive an email with the details and links to **confirm** or **reject** the reservation.
- When the administrator confirms, you will receive a **confirmation email** and the events will become visible in the site calendar.
- If the administrator rejects the reservation, you will receive a **rejection notification email**.

---

## Reservation statuses

| Status | Meaning |
|--------|---------|
| **Pending** | The request has been registered but not yet approved. |
| **Confirmed** | The administrator has approved the reservation. It appears in the site calendar. |
| **Rejected** | The reservation has been rejected. The event remains visible in the site calendar but does not block the time slot for new requests. |

---

## Important notes

- The tool **automatically checks** whether the resource is already reserved for the selected time slot and warns you before submitting.
- If you are unsure whether your request was received, check your acknowledgement email.
- To **cancel** a reservation, the administrator can use the cancellation link in the request email or the management panel. You will receive a notification email when this happens.
- To modify a submitted request, contact the site administrator.
- If the administrator deletes a reservation event directly from the Sakai calendar, the tool will no longer have any record of it.

---

## Administrator guide

### Prerequisites

- The Sakai site must have the **Calendar** tool added. Without it, reservations cannot be recorded.
- Only users with the site maintainer role (`site.upd`) have access to the options panel and management panel, and can confirm or reject reservations.

### Initial setup (Options)

Click the **⚙ Options** button (visible only to maintainers) to configure the tool.

| Field | Description |
|-------|-------------|
| **Tool title** | Name shown in the form header. |
| **Administrator email** | Address that will receive reservation requests with the action links. |
| **Sender email** | From address for outgoing emails. If left blank, `noreply@<domain>` is used. |
| **Resource field** | Specifies which form field identifies the resource to be reserved (used for conflict detection). |

### Form fields

The **Form fields** section allows full customisation of the fields shown to requesters. Available types:

| Type | Recommended use |
|------|-----------------|
| Short text | Short free-text answers. |
| Long text | Justifications or descriptions. |
| Date | Date without time. |
| Date and time | Specific date and time. |
| Time | Free time entry (native browser picker). |
| Dropdown | Predefined option list (e.g. list of classrooms). |
| Multiple checkboxes | Multi-selection (e.g. required equipment). |

Use the **▲ ▼** buttons to reorder fields and **✕ Remove** to delete them. Changes only take effect after clicking **Save options**.

### Managing reservations

#### Via email links

When a request is received, the administrator gets an email containing:

- The requester's details and reservation information.
- A link to **CONFIRM** the reservation.
- A link to **REJECT** the reservation.

Each link can only be used once. After confirming or rejecting, both links are invalidated. The requester automatically receives a confirmation or rejection email in either case.

#### Via the management panel

Click the **☰ Manage reservations** button (visible only to maintainers) to open the management panel. This panel shows all reservations for the resources configured in this placement, ordered by date (most recent first).

- Use the **Show** dropdown to control how many records are displayed (20, 50, 100 or 200).
- The table shows: date, time, duration, resource, requester name and email, and status.
- **Pending** reservations have **Confirm** and **Reject** buttons. Confirming or rejecting from this panel sends the appropriate email to the requester automatically.
- Confirmed and rejected reservations are shown for reference but cannot be changed.

When confirmed, events become visible in the site calendar and the requester receives a confirmation email. When rejected, events are marked as rejected and the requester receives a notification email.

> **Note:** To modify an already confirmed reservation, manually delete the calendar events in Sakai and ask the requester to submit a new request.

---

## Developer notes

### Building and deploying

**Requirements:**
- Java 11+
- Maven 3.6+
- A running Sakai 25 instance with Tomcat

**Clone and build:**

```bash
git clone https://github.com/sakaicontrib/reservation.git
cd reservation
mvn clean install
```

**Deploy to Tomcat:**

```bash
mvn clean install sakai:deploy -Dmaven.tomcat.home=/path/to/tomcat -Dmaven.test.skip=true
```

Or copy the WAR manually:

```bash
cp target/reservation.war /path/to/tomcat/webapps/
```

**Add the tool to a Sakai site:**

1. Log in as a Sakai administrator and go to **Administration Workspace → Sites**.
2. Open the target site, add a new tool and select **Space Reservation** (`sakai.reservation`).
3. The site must also have the **Calendar** tool enabled — reservations are stored as calendar events.
4. Configure the tool via the **⚙ Options** button (maintainer role required).

> **Note:** The tool is intentionally restricted from the standard Site Info → Manage Tools panel so that only Sakai administrators can deploy it.

### Internationalisation (i18n)

The tool uses Sakai's `ResourceLoader` to detect the user's language from their Sakai preferences. Message files are located at:

```
src/main/resources/org/sakaiproject/reservation/messages.properties       (English, default)
src/main/resources/org/sakaiproject/reservation/messages_es.properties     (Spanish)
src/main/resources/org/sakaiproject/reservation/messages_eu.properties     (Basque)
```

### Lessons learned

**1. `sakai-kernel-util` must use `compile` scope, not `provided`.**
Unlike `sakai-kernel-api`, this JAR is not present in Tomcat's shared lib. Contrib tools must bundle it inside their own WAR (`WEB-INF/lib`). Declaring it as `provided` causes a `ClassNotFoundException` at startup.

**2. The bundle name must include the full package path.**
Using just `"messages"` conflicts with other `messages.properties` files on the Sakai classpath. The correct pattern is `"org.sakaiproject.reservation.messages"`, following the same convention used by other contrib tools.

**3. `<resources>` must be declared explicitly in `pom.xml`.**
When `<sourceDirectory>` is customised in `pom.xml`, Maven does not automatically include `src/main/resources` in the build. Without an explicit `<resources>` section, the properties files are not packaged into the WAR.

### Architecture overview

- **Servlet + JSPs** — no Spring MVC, deployed as a standalone WAR in Tomcat.
- **No own database** — reservations are stored as `CalendarEvent` objects in Sakai's calendar, using custom event properties.
- **Per-placement configuration** — supports multiple independent instances in the same site, each with its own resource list and settings.
- **Routes:**

| Method | Path | Handler |
|--------|------|---------|
| GET | `/tool` | Reservation form |
| POST | `/tool` | Process reservation request |
| GET | `/tool/confirm` | Confirm reservation (token-based) |
| GET | `/tool/cancel` | Reject reservation (token-based) |
| GET | `/tool/options` | Options panel (maintainer only) |
| POST | `/tool/options` | Save options |
| GET | `/tool/manage` | Management panel (maintainer only) |
| POST | `/tool/manage` | Confirm/reject from management panel |
| GET | `/tool/checkConflict` | AJAX conflict check |

### Custom event properties

Reservations are stored in the Sakai calendar with the following custom properties:

| Property | Description |
|----------|-------------|
| `reservation.status` | Status: `PENDING`, `CONFIRMED` or `CANCELLED` |
| `reservation.token` | One-time use token for email confirmation/rejection links |
| `reservation.requester.name` | Requester display name |
| `reservation.requester.email` | Requester email address |
| `reservation.group.event.ids` | Comma-separated IDs of all events in a multi-slot request |
| `reservation.slot.descriptions` | JSON array of slot descriptions for notification emails |
