# Manual de uso – Herramienta de reservas

## ¿Para qué sirve?

Esta herramienta permite solicitar la reserva de un recurso (aula, sala de reuniones, equipamiento, etc.) para una o varias franjas horarias. Las solicitudes quedan registradas en el calendario del sitio y deben ser aprobadas por un administrador antes de considerarse confirmadas.

---

## Cómo hacer una solicitud

1. **Accede a la herramienta** desde el menú del sitio Sakai.

2. **Rellena el formulario:**
   - Tu nombre y correo electrónico se rellenan automáticamente con los datos de tu cuenta Sakai y no se pueden modificar.
   - Selecciona el **recurso** que deseas reservar (aula, sala, etc.).
   - Rellena el resto de campos que aparezcan (pueden variar según la configuración del sitio).

3. **Añade las franjas horarias:**
   - Por defecto aparece una franja. Indica la **fecha**, la **hora de inicio** y la **duración**.
   - Si necesitas reservar el **día completo**, marca la casilla "Día completo" — se ocultarán los campos de hora.
   - Para reservar **varios días**, pulsa **"+ Añadir día"** y rellena cada franja por separado. Cada día puede tener un horario distinto.
   - Si hay un conflicto con otra reserva ya existente, aparecerá un aviso en rojo bajo esa franja y no podrás enviar hasta resolverlo.

4. **Envía la solicitud** pulsando "Enviar solicitud".

---

## Qué ocurre después

- Recibirás un **correo de acuse de recibo** confirmando que tu solicitud ha sido registrada.
- El administrador recibirá un correo con los detalles y un enlace para **confirmar la reserva**.
- Cuando el administrador confirme, recibirás un **correo de confirmación** y los eventos quedarán visibles en el calendario del sitio.

---

## Estados de una reserva

| Estado | Significado |
|--------|-------------|
| **Pendiente** | La solicitud ha sido registrada pero aún no ha sido aprobada. |
| **Confirmada** | El administrador ha aprobado la reserva. Aparece en el calendario del sitio. |
| **Cancelada** | La reserva ha sido cancelada y no ocupa el horario. |

---

## Notas importantes

- La herramienta **comprueba automáticamente** si el recurso ya está reservado en la franja horaria seleccionada y te avisa antes de enviar.
- Si tienes dudas sobre si tu solicitud ha sido recibida, comprueba tu correo de acuse de recibo.
- Para **cancelar** una reserva, el administrador dispone de un enlace de cancelación en el mismo correo donde recibe la solicitud. Al cancelar, recibirás un correo de notificación.
- Para modificar una reserva ya enviada, contacta con el administrador del sitio.

---

## Guía para administradores

### Requisitos previos

- El sitio Sakai debe tener la herramienta **Calendario** añadida. Sin ella, las reservas no se pueden registrar.
- Solo los usuarios con rol de mantenedor del sitio (`site.upd`) tienen acceso al panel de opciones y pueden confirmar o cancelar reservas.

### Configuración inicial (Opciones)

Accede al panel de opciones pulsando el botón **⚙ Opciones** (visible solo para mantenedores).

| Campo | Descripción |
|-------|-------------|
| **Título de la herramienta** | Nombre que aparece en la cabecera del formulario. |
| **Email del administrador** | Dirección que recibirá las solicitudes con los enlaces de acción. |
| **Email remitente** | Dirección From de los correos enviados. Si se deja en blanco se usa `noreply@<dominio>`. |
| **Campo de recurso** | Indica qué campo del formulario identifica el recurso a reservar (se usa para detectar conflictos de horario). |

### Campos del formulario

La sección **Campos del formulario** permite personalizar completamente los campos que verán los solicitantes. Tipos disponibles:

| Tipo | Uso recomendado |
|------|-----------------|
| Texto libre | Respuestas cortas. |
| Texto largo | Justificaciones o descripciones. |
| Fecha | Fechas sin hora. |
| Fecha y hora | Fecha y hora específica. |
| Hora | Hora libre (selector nativo del navegador). |
| Desplegable | Lista de opciones predefinidas (p. ej. lista de aulas). |
| Casillas múltiples | Selección de varias opciones (p. ej. materiales). |

Usa los botones **▲ ▼** para reordenar los campos y **✕ Eliminar** para quitarlos. Los cambios solo se aplican al pulsar **Guardar opciones**.

### Gestión de solicitudes

Cuando se recibe una solicitud, el administrador recibe un correo con:

- Los datos del solicitante y los detalles de la reserva.
- Un enlace para **CONFIRMAR** la reserva.
- Un enlace para **CANCELAR** la reserva.

Cada enlace solo puede usarse una vez. Tras confirmar o cancelar, ambos enlaces quedan invalidados.

Al confirmar, los eventos pasan a ser visibles en el calendario del sitio y el solicitante recibe un correo de confirmación. Al cancelar, los eventos quedan marcados como cancelados y el solicitante recibe un correo de notificación.

> **Nota:** Para modificar una reserva ya confirmada hay que eliminar manualmente los eventos del calendario de Sakai y pedir al solicitante que envíe una nueva solicitud.

---

## Notas para desarrolladores

### Internacionalización (i18n)

La herramienta usa `ResourceLoader` de Sakai para detectar el idioma del usuario según sus preferencias de Sakai. Los ficheros de mensajes están en:

```
src/main/resources/org/sakaiproject/reserva/messages.properties       (inglés, defecto)
src/main/resources/org/sakaiproject/reserva/messages_es.properties     (español)
src/main/resources/org/sakaiproject/reserva/messages_eu.properties     (euskera)
```

Lecciones aprendidas durante la implementación:

**1. `sakai-kernel-util` debe ir con scope `compile`, no `provided`.**
A diferencia de `sakai-kernel-api`, este JAR no está en el lib compartido de Tomcat. Las herramientas contrib lo empaquetan dentro de su propio WAR (`WEB-INF/lib`). Declararlo como `provided` hace que Tomcat no lo encuentre al arrancar.

**2. El nombre del bundle debe incluir la ruta de paquete completa.**
Usar simplemente `"messages"` colisiona con otros `messages.properties` del classpath de Sakai. El patrón correcto es `"org.sakaiproject.reserva.messages"`, igual que hace evalsys con `"org.sakaiproject.evaluation.tool.bundle.messages"`.

**3. Hay que declarar `<resources>` explícitamente en el `pom.xml`.**
Al tener `<sourceDirectory>` personalizado en el `pom.xml`, Maven no incluye automáticamente `src/main/resources` en el build. Sin la sección `<resources>`, los ficheros de properties no llegan al WAR.