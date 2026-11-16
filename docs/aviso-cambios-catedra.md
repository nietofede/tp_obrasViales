# Aviso de recorte de alcance a la cátedra

Mail enviado por el equipo (comisión 3K3) y respuesta del profesor. Detalle técnico del recorte en `docs/DECISIONES.md` ("Recorte de alcance" y "Respuesta de la cátedra") y plan en `docs/plan-recorte.md`.

## Mail enviado

(Transcripto con tildes; el contenido es el enviado.)

Hola, profe. Somos el grupo 155 del 3K3.

Queremos pedirle autorización para sacar del alcance algunas funciones. Son sobre todo endpoints de carga y edición de datos.

Lo que pedimos quitar:

- La carga y edición de máquinas, operarios, certificaciones y plantas de mezcla. Esos datos vendrían precargados.
- El armado y la edición de cuadrillas. También vendrían precargadas.
- La edición de la composición mínima de cada tipo de trabajo y de la configuración de desplazamiento (viaje máximo, ventana de la mezcla y margen del camión). Quedarían como valores fijos.
- La consulta de asignaciones del operario. Como el operario no tendría otra función, también sacaríamos ese rol.
- La edición de obras y tramos. Se podrían crear, pero no modificar.
- Las consultas sueltas de un tramo, de una fase y de una reserva, porque esa información ya aparece al consultar la obra y la orden.
- La reprogramación manual y la cancelación de una orden. La reprogramación la sigue haciendo el sistema cuando se suspende una orden por clima.
- Que el límite de suspensiones sea configurable. Quedaría fijo en tres.
- El listado de fases en riesgo y el endpoint para decidir sobre ellas. Cuando una fase llega a tres suspensiones quedaría marcada en riesgo y el sistema dejaría de reprogramarla solo; el planificador decide reprogramando la fase y creando la orden nueva.
- El registro del consumo real de materiales y de las horas de máquina al cerrar una orden. La orden se cerraría al llegar al 100 % de avance.
- El chequeo de mantenimiento de las máquinas al reservar, ya que el mantenimiento preventivo estaba fuera del alcance.
- La caché del último pronóstico. Si Open-Meteo no responde, se mantienen los reintentos y la orden queda pendiente de confirmación del capataz.

En total, la API pasaría de 54 a 32 endpoints.

Quedamos atentos a su respuesta antes de hacer cualquier cambio. Si les parece que algo de esto debería quedar, lo mantenemos.

## Respuesta del profesor (2026-11-16)

> Hola! Por lo que contas el dominio ya era muy grande, achiquenlo con los criterios que evaluen y vemos en la exposición si queda mal o chico. A simple vista lo que me planteas suena razonable.

## Segundo aviso: quitar RN-04 (borrador, 2026-11-16, sin enviar)

Hola, profe. Siguiendo lo que nos dijo de achicar con los criterios que se evalúan, queremos sacar también la RN-04 (el operario solo puede manejar una máquina si tiene la certificación vigente). Es la regla más aislada del motor de recursos: no interviene en el proceso central y quitarla nos deja más tiempo para el resto. Las demás reglas quedan igual y conservan su número, así que pasaríamos de doce a once.

Quedamos atentos por si prefiere que la mantengamos.

Saludos,
Grupo 155 - 3K3
