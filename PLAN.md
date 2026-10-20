# Plan de correccion: H-05, H-06 y H-07

## Summary

Propuesta documental del 2026-10-19 para los tres items Pendientes de
[HALLAZGOS.md](HALLAZGOS.md). No autoriza implementar, no modifica codigo,
contrato ni estados. HALLAZGOS sigue siendo el unico backlog.

**Antecedente conservado:** la aprobacion humana anterior correspondia solo a
H-01 a H-04: JWT RSA y CORS externo POST/OPTIONS, migracion transaccional,
respaldo/restauracion del operador y calibracion BCrypt. Se implementaron
coste 12, prehash Base64(SHA-256) para evitar truncamiento a 72 bytes e
integracion quarkus-jacoco. Su evidencia permanece en
[HALLAZGOS.md](HALLAZGOS.md) y [evidencias/](evidencias/).
Esa aprobacion no se extiende a H-05/H-06/H-07 ni certifica consumidores externos.

## Scope

- **Dentro:** propuestas independientes de manejo de errores, dependencias y
  responsabilidades; caracterizacion primero y correccion despues.
- **Fuera:** implementar ahora, reabrir H-01/H-04, cambiar tokens, CORS,
  contrasenas o migracion, agregar endpoints y acceder a otros componentes.
- [ENDPOINTS.md](docs/ENDPOINTS.md) es la referencia vigente y
  [GOOD_PRACTICES.md](docs/GOOD_PRACTICES.md) define las convenciones.

## Supuestos

- Se usan JDK 21, Maven y las herramientas de pruebas existentes, con fixtures
  aislados, sin secretos reales. No se incorporan dependencias por comodidad.
- La compatibilidad de consumidores externos no esta verificada.
- La evidencia previa en [TESTING.md](docs/TESTING.md) no sustituye ejecutar
  nuevamente la suite despues de futuros cambios.

## Decisiones tecnicas por tomar

- La forma publica de errores de H-05 requiere aprobacion especifica.
- Las versiones destino de H-06 se eligen con advisories y pruebas, no se
  presupone que una version concreta sea segura.
- H-07 es un refactor sin cambio observable; no absorbe las correcciones
  funcionales de H-05 ni las actualizaciones de H-06.

## Item: H-05 - Errores: respuesta con detalles internos

- **Problema y evidencia:** [AuthResource.java:35](src/main/java/com/bcp/workshop/auth/AuthResource.java#L35)
  devuelve mensaje y clase de excepcion; [LegacyDatabase.java:66](src/main/java/com/bcp/workshop/auth/LegacyDatabase.java#L66)
  registra fallos SQL y devuelve null, que acaba en 401.
- **Alcance propio / cambio propuesto:** devolver un mensaje generico en 500,
  sin clase ni mensaje interno; registrar el detalle una sola vez en el
  servidor, sin credenciales, hash ni token. Distinguir error de persistencia
  de ausencia de usuario mediante una excepcion especifica, sin capturas
  genericas en repositorio ni respuestas con apariencia de exito.
- **Excepcion de contrato solicitada por este item:** propuesta a aprobar:
  500 `{"message":"Internal server error"}` para excepciones inesperadas o de
  persistencia; retirar exception y dejar de convertir fallos SQL en 401.
  Cuenta inexistente/clave incorrecta siguen en 401 con su mensaje actual.
  No cambiar 200, validaciones 400, otros errores del framework ni salud.
  No editar ENDPOINTS ni implementar hasta aprobar expresamente esta diferencia;
  no es posible ocultar detalles y preservar a la vez el cuerpo 500 legado.
- **Archivos previstos:** AuthResource.java, LegacyDatabase.java; nueva
  CredentialStorageException.java y, si se adopta un mapper JAX-RS para este
  item, AuthExceptionMapper.java en el paquete actual. Pruebas:
  AuthResourceTest.java, EndpointFailureTest.java, RepositoryLifecycleTest.java.
  Documentacion al implementar: docs/ENDPOINTS.md, docs/TESTING.md,
  HALLAZGOS.md y evidencia bajo evidencias/. Sin nueva dependencia.
- **Prueba antes de tocar produccion:** ampliar la caracterizacion existente
  del 500 con y sin mensaje, registrar el SQL fallido que hoy acaba en null
  y proteger todas las respuestas no afectadas. Agregar regresion de seguridad
  que exija el cuerpo generico exacto, ausencia de exception/detalles internos
  y 500 HTTP ante fallo SQL con fixture aislado: debe fallar antes.
  Despues, comprobar el log controlado y que no incluya secretos.
- **Cierre verificable:** regresiones pasan, 401 solo para credenciales
  incorrectas/ausentes y documentacion del cambio aprobada. No renombrar capas
  ni actualizar dependencias como parte de este item.

## Item: H-06 - SCA: Quarkus 3.27.4 y Commons Text 1.9 declarados

- **Problema y evidencia:** [pom.xml:13](pom.xml#L13) fija Quarkus 3.27.4 y
  [pom.xml:58](pom.xml#L58) Commons Text 1.9. Su presencia exige investigar
  advisories; no demuestra por si sola explotabilidad. El uso local es
  [escapeHtml4](src/main/java/com/bcp/workshop/auth/LoginResponse.java#L9),
  no StringSubstitutor.
- **Alcance propio / cambio propuesto:** inventariar dependencias efectivas con
  `mvn -B -ntp dependency:tree`; contrastar advisories oficiales, rangos
  afectados y uso real, incluidas transitivas. Elegir la menor actualizacion
  mantenida y compatible que remedie los advisories aplicables; alinear las
  extensiones Quarkus al BOM. Para Commons Text, preferir actualizacion que
  conserve escapeHtml4; no escribir un escape casero ni eliminarlo. Si un
  advisory no aplica, registrar razon y evidencia, sin suprimir alertas.
- **Archivos previstos:** pom.xml; nueva LoginResponseTest.java para escape;
  EndpointContractTest.java, EndpointFailureTest.java, TokenServiceTest.java
  y src/test/powershell/EndpointProdTest.ps1 solo para reforzar compatibilidad.
  README.md y docs/TESTING.md para requisitos/evidencias si procede,
  HALLAZGOS.md y resultados SCA bajo evidencias/. ENDPOINTS queda intacto.
- **Prueba antes de tocar versiones:** fijar en LoginResponseTest el resultado
  exacto de escape para `&`, `<`, `>`, comillas, apostrofe, Unicode y null
  segun el comportamiento actual; ejecutar la matriz HTTP, firma JWT y
  migracion existentes. Guardar arbol/advisories iniciales. Tras actualizar,
  exigir mismas respuestas HTTP/JSON, tambien cuerpos vacios prod, sin
  adaptar expectativas para aceptar regresiones del framework.
- **Cierre verificable:** comparativa antes/despues de versiones y advisories,
  comandos con salida y pruebas compatibles. No declarar ausencia global de
  CVE ni cerrar solo porque compila. Un cambio inevitable de contrato exige
  detener este item y pedir autorizacion, no introducirlo como actualizacion.

## Item: H-07 - Clean code: Resource mezcla responsabilidades

- **Problema y evidencia:** ya existen servicio, repositorio, seguridad y DTO,
  pero [AuthResource.login](src/main/java/com/bcp/workshop/auth/AuthResource.java#L23)
  aun orquesta autenticacion, emision y captura de errores. Se debe evaluar
  la deuda restante, sin rehacer la separacion ya implementada.
- **Alcance propio / cambio propuesto:** Resource valida y adapta HTTP;
  AuthService orquesta autenticacion y emision usando PasswordHasher,
  repositorio y TokenService; el mapeo a DTO sigue en LoginResponse.
  Aislar traduccion de errores sin decidir aqui mensajes o estados nuevos;
  reutilizar la pieza de H-05 si existe y esta aprobada. Metodos de una
  responsabilidad, guard clauses, nombres de proposito, helpers compartidos
  solo donde exista duplicacion y constantes para literales de dominio.
- **Archivos previstos:** AuthResource.java, AuthService.java, LoginResponse.java;
  AuthResourceTest.java y EndpointFailureTest.java para adaptar dobles;
  nueva AuthServiceTest.java para orquestacion, y LoginResponseTest.java si
  se necesita ampliar el mapeo. docs/TESTING.md, HALLAZGOS.md y diffs en
  evidencias/. Sin dependencias nuevas ni cambio de ENDPOINTS o esquema SQLite.
- **Prueba antes de tocar capas:** completar caracterizacion de validacion
  (incluida precedencia), respuesta 200 exacta, 401, 500 vigente y claims JWT.
  Agregar pruebas de servicio que verifiquen que no se genera token si falla
  la autenticacion y que una autenticacion valida emite una sola vez con la
  identidad recuperada. Adaptar wiring de dobles tras refactor, no resultados.
- **Cierre verificable:** matriz de contrato sin diferencias, sin acceso a
  datos/verificacion de hash en Resource, sin duplicar duracion ni mapeo.
  Si H-05 se implemento antes, tomar su contrato aprobado como nueva linea
  base; si no, conservar el legado. No cerrar H-05 por extraer un catch.

## Riesgos y mitigaciones

- **Ruptura del cliente:** H-05 explicita y bloquea cambios hasta aprobacion;
  H-06/H-07 comparan la misma matriz antes/despues, sin relajar aserciones.
- **Actualizacion incompatible o cobertura enganosa:** evidenciar versiones,
  perfiles prod/test y lineas modificadas, no inferir seguridad de BUILD SUCCESS.

## Criterios de aceptacion medibles

1. Tres cambios revisables separados, cada uno con prueba previa, diff y
   resultado de correccion; cero cierres sin evidencia en HALLAZGOS.md.
2. Cero cambios contractuales fuera de H-05 expresamente aprobado.
3. Cobertura de lineas modificadas >= 85%; CodeQL, Dependency Review y JaCoCo
   sin supresiones ni reduccion de gates; cero secretos reales en evidencia.

## Plan de validacion

- **Unitarias:** errores/repositorio para H-05, escape para H-06,
  orquestacion/mapeo para H-07, conservando hash/migracion/token existentes.
- **Funcionales:** `mvn -B -ntp clean verify quarkus:build`; luego el script
  prod de [TESTING.md](docs/TESTING.md), con datos sinteticos y JDK 21.
- **Seguridad:** H-05 ausencia de detalles/secretos; H-06 advisories y controles
  existentes; H-07 regresiones de autenticacion, firma y SQL sin cambios.
- **Evidencia requerida:** log por item con comando, directorio y codigo de
  salida; arbol SCA para H-06, diff y JaCoCo HTML/XML. Medir lineas ejecutables
  modificadas mediante diff y archivos nuevos cruzados con XML, como TESTING.
  Este plan no ejecuta pruebas ni afirma nuevas mediciones.

## Tareas sugeridas por rol

- **Chapter/PO:** aprobar alcance y excepcion contractual de H-05.
- **Tech Lead:** validar limites por item; decidir contrato H-05; revisar
  versiones destino y responsabilidad de capas.
- **Backend:** escribir caracterizacion; implementar cada item por separado;
  guardar diffs, resultados y documentacion correspondiente.
- **QA:** preparar fixtures aislados; contrastar matriz antes/despues;
  verificar cobertura y separar fallos de pendientes.
- **Frontend:** coordinar impacto del 500 aprobado, sin acceder ni modificar
  su componente desde este repositorio.
- **Cyber:** revisar no divulgacion y evidencia de advisories/aplicabilidad.

## Preguntas abiertas

- ¿Se aprueba el cuerpo 500 generico y tratar el fallo SQL como 500 en H-05?
- ¿Que versiones mantenidas de Quarkus/Commons Text resultan compatibles y
  remedian advisories aplicables? Resolver con evidencia durante H-06.
- ¿Se implementara H-07 despues de H-05? Es recomendable para evitar duplicar
  traduccion de errores, pero sus alcances y evidencias permanecen separados.
