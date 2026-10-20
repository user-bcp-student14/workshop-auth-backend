## 1. Summary

Incorporar una confirmación previa al envío de transferencias, con resumen y validación de datos, para ayudar al usuario a evitar duplicados.

- **Meta de negocio:** reducir un 20% las quejas por transferencias duplicadas en un mes.
- **Dependencias:** servicios de cuentas y transferencias.
- **Definition of Done:** pruebas unitarias, documentación breve y PR revisado.

## 2. Scope

- **Dentro del alcance:** confirmación antes del envío, mensaje resumen y validación de datos.
- **Fuera del alcance:** cambios de infraestructura y rediseño completo de la interfaz. La idempotencia del servicio no se considera incluida sin una decisión explícita.

## 3. Supuestos

- Se reutilizan la API REST, el frontend web y el mecanismo de logging estandarizado existentes.
- La confirmación ayuda a prevenir errores del usuario; no garantiza por sí sola evitar duplicados por reintentos o concurrencia.
- Las reglas de validación, el mecanismo de medición y los contratos de los servicios deben confirmarse antes de implementar.

## 4. Decisiones técnicas por tomar (pendientes o resueltas)

- **Resuelta:** mantener el stack existente y no modificar infraestructura.
- **Pendiente:** acordar los campos del resumen y su presentación sin exponer información sensible.
- **Pendiente:** definir reglas de importe, precisión, límites y validación de cuentas.
- **Pendiente:** establecer qué ocurre al cancelar, modificar datos, recibir un error o repetir la confirmación.
- **Pendiente:** verificar si la API actual ofrece idempotencia y cómo debe utilizarla el frontend, sin ampliar el alcance automáticamente.

## 5. Riesgos y mitigaciones

- **Datos sensibles en logs:** aplicar la política de logging existente, omitir cuerpos sensibles y verificar registros con datos sintéticos.
- **Importes aceptados o rechazados incorrectamente:** acordar reglas y probar límites, precisión y formatos antes de implementar.
- **Duplicados pese a la confirmación:** revisar reintentos y solicitudes concurrentes con el servicio de transferencias; documentar las garantías y limitaciones reales.
- **Dependencias no disponibles:** acordar el comportamiento ante fallos y probarlo sin presentar un envío fallido como exitoso.

## 6. Criterios de aceptación (medibles)

1. El 100% de los escenarios válidos de la matriz acordada presenta el resumen antes de solicitar el envío; no se envía ninguna transferencia antes de la confirmación explícita.
2. El resumen coincide con los datos utilizados para el envío en el 100% de los casos funcionales acordados.
3. El 100% de los casos inválidos de la matriz aprobada se rechaza antes del envío, con un mensaje comprensible.
4. La revisión de los logs generados por las pruebas encuentra **cero exposiciones** de datos clasificados como sensibles.
5. La entrega incluye pruebas unitarias en verde, documentación breve y PR revisado.
6. Al completar un mes de seguimiento, las quejas por duplicados disminuyen al menos un 20% frente a la línea base acordada. Este criterio de negocio queda pendiente hasta disponer de la medición posterior.

## 7. Plan de validación

- **Pruebas unitarias:** reglas de validación, límites del importe y construcción del resumen; comprobar el control del envío según el estado de confirmación.
- **Pruebas funcionales:** recorrido válido, datos inválidos, correspondencia resumen/envío y errores de servicios. Incorporar cancelación, edición y reintentos cuando se acuerde su comportamiento.
- **Seguridad (comprobaciones mínimas):** revisar logs con datos sintéticos y validar que la confirmación no sustituya las comprobaciones del servidor.
- **Evidencia requerida (qué adjuntar):** matriz de casos con resultados, salida de pruebas, ejemplos sintéticos del resumen, checklist de logs y documentación breve. Registrar línea base y resultado mensual por separado.
- **Trazabilidad del skill:** referenciar [SKILL.md](.github/skills/feature-intake-spec-checklist/SKILL.md), [input.sample.md](.github/skills/feature-intake-spec-checklist/examples/input.sample.md) y [output.sample.md](.github/skills/feature-intake-spec-checklist/examples/output.sample.md); verificar su versionado y la existencia del tag `skill-feature-intake-v0.1.0` antes de la entrega.

## 8. Tareas sugeridas por rol

- **Chapter/PO**
  - Acordar reglas, mensajes y alcance de la confirmación.
  - Definir línea base, fuente de quejas y ventana de medición mensual.
- **Tech Lead**
  - Revisar contratos y capacidades de ambos servicios.
  - Registrar decisiones sobre validación, errores y reintentos.
  - Revisar separación de responsabilidades y cumplimiento del alcance.
- **Backend**
  - Verificar las validaciones existentes y completar únicamente las acordadas.
  - Implementar los cambios necesarios en la API, si el análisis los requiere.
  - Agregar pruebas unitarias y comprobar el logging seguro.
- **Frontend**
  - Implementar el resumen y la confirmación sin rediseño general.
  - Integrar validaciones y estados de error conforme a las decisiones aprobadas.
- **QA**
  - Construir la matriz de camino feliz, bordes y errores.
  - Ejecutar integración con cuentas y transferencias, verificando resumen y envío.
  - Recopilar resultados, revisar logs y preparar la evidencia de aceptación.
- **Cyber**
  - Revisar exposición de datos en resumen y logs.
  - Evaluar las garantías existentes frente a reintentos y duplicados.

## 9. Preguntas abiertas

- ¿Qué campos debe mostrar el resumen y cuáles requieren enmascaramiento?
- ¿Cuáles son las reglas exactas de importe, moneda, precisión y cuentas?
- ¿Cómo deben funcionar cancelación, edición, doble confirmación y reintentos?
- ¿Qué contratos y garantías de idempotencia ofrecen los servicios actuales?
- ¿Cuál es la línea base de quejas, qué se considera duplicado y cómo se compararán los periodos?
- ¿Quién será responsable de medir el resultado al finalizar el mes?
