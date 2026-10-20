# Pruebas del contrato de endpoints

Ejecucion real: 2026-10-19. Alcance exclusivo: workshop-auth-backend, rama
day1/prompting. Referencia: [ENDPOINTS.md](ENDPOINTS.md). No se agregaron
endpoints. La ampliacion original fue solo de pruebas; las correcciones
posteriores aprobadas de PLAN.md se distinguen en las secciones por item.

## Matriz endpoint / caso / tipo / resultado esperado

| endpoint | caso | tipo | resultado esperado |
|---|---|---|---|
| POST /auth/login | Credenciales sinteticas correctas, sin Authorization | Camino feliz | 200; token, expiresIn=3600, user y debug; identidad/rol correctos y displayName escapado. |
| POST /auth/login | Trainer sin client; campo desconocido | Caso borde | 200; client opcional, propiedad desconocida ignorada. |
| POST /auth/login | client null o vacio | Caso borde | 200; no altera la identidad. |
| POST /auth/login | Contrasena de 120 caracteres, y misma clave con otro sufijo | Caso borde | Clave completa: 200; sufijo distinto: 401, sin truncamiento a 72 bytes. |
| POST /auth/login | Username/password con espacios adicionales | Caso borde | 401; no se recortan para autenticar. |
| POST /auth/login | Cuerpo ausente o JSON null | Error | 400, `{"message":"Request is null"}`. |
| POST /auth/login | Username ausente, null, vacio o solo espacios | Error | 400, `{"message":"Username is required"}`; tiene precedencia sobre password. |
| POST /auth/login | Password ausente, null, vacia o solo espacios | Error | 400, `{"message":"Password is required"}` con username valido. |
| POST /auth/login | Cuenta inexistente o password incorrecta | Error | 401, message `Invalid user <username> or password`. |
| POST /auth/login | Error SQL de repositorio aislado | Error / correccion H-05 | 500, exactamente `{"message":"Internal server error"}`; SQLException conservada como causa, nunca 401. |
| POST /auth/login | Excepcion sintetica del servicio | Error / correccion H-05 | 500, exactamente `{"message":"Internal server error"}`; sin exception ni mensaje interno. |
| POST /auth/login | Excepcion sintetica sin mensaje | Error / caso borde H-05 | El mismo cuerpo generico 500, sin diferencias segun el mensaje interno. |
| POST /auth/login | JSON mal formado | Error | 400 y cuerpo vacio en test y prod. |
| POST /auth/login | Username como objeto | Error | 400; cuerpo vacio en prod. En modo test, Quarkus agrega diagnosticos de deserializacion (se comprueban por separado). |
| POST /auth/login | POST desde Origin no permitido | Error | 403, cuerpo vacio y sin Access-Control-Allow-Origin. |
| POST /auth/login | GET en ruta POST | Error | 405, cuerpo vacio. |
| POST /auth/login | Accept: text/plain | Error | 406, cuerpo vacio. |
| POST /auth/login | Content-Type: text/plain | Error | 415, cuerpo vacio. |
| POST /auth/login | Preflight permitido / origen desconocido / DELETE | Caso borde | Se permite POST al origen configurado; el origen ajeno no recibe permiso y DELETE no esta autorizado. |
| GET /q/health | Sin Authorization ni cuerpo | Camino feliz | 200, application/json, `{"status":"UP","checks":[]}` y solo esos dos campos. |
| GET /q/health | GET con cuerpo JSON vacio | Caso borde | 200, UP y checks vacio; cuerpo ignorado. |
| GET /q/health | Accept: text/plain | Caso borde | 200 con JSON; no comparte negociacion del Resource de login. |
| GET /q/health | Origin permitido para POST o desconocido | Error | 403, cuerpo vacio; GET no esta habilitado por CORS. |
| GET /q/health | Check de salud sintetico DOWN, exclusivo del perfil de fallo | Error | 503, status DOWN; un check synthetic-down con status DOWN. No se agrega un check al backend de produccion. |

Las filas agrupan variantes; no equivalen al numero de metodos JUnit.
Hay pruebas para cada codigo de error documentado: login
400/401/403/405/406/415/500 y salud 403/503.

## Codigo de los tests y aislamiento

Codigo completo versionable:

- [AuthResourceTest.java](../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java):
  identidad/forma de respuesta, validacion, claves incorrectas, client opcional
  y contrasena larga. Mantiene la caracterizacion previa.
- [EndpointContractTest.java](../src/test/java/com/bcp/workshop/auth/EndpointContractTest.java):
  11 pruebas HTTP adicionales de ambas rutas, validacion de entrada,
  negociacion de contenido, metodos, CORS y salud UP.
- [EndpointFailureTest.java](../src/test/java/com/bcp/workshop/auth/EndpointFailureTest.java):
  pruebas HTTP con alternativas CDI seleccionadas solo en FailureProfile:
  excepciones con/sin mensaje, fallo SQL y check DOWN.
- [EndpointProdTest.ps1](../src/test/powershell/EndpointProdTest.ps1):
  5 comprobaciones contra la aplicacion empaquetada, necesarias para validar
  los cuerpos vacios del contrato prod sin confundirlos con diagnosticos test.
- [AuthTestResource.java](../src/test/java/com/bcp/workshop/auth/AuthTestResource.java):
  crea SQLite y RSA efimeros bajo target, registra/desregistra JDBC por
  arranque y elimina los archivos al terminar. El registro explicito evita
  perder el driver al reiniciar Quarkus entre perfiles.

Extractos ejecutados (el codigo completo esta en los enlaces, no en copias
independientes que puedan divergir):

```java
@Test
void reportsHealthWithoutAuthentication() {
    given().accept("application/json").get(HEALTH).then().statusCode(200)
            .contentType("application/json").body("status", is("UP"))
            .body("checks", empty()).body("size()", is(2));
}
```

```java
@Test
void reportsGenericInternalErrorOverHttp() {
    given().contentType("application/json")
            .body(Map.of("username", "synthetic-failure", "password", "test-only"))
            .post("/auth/login").then().statusCode(500)
            .body("", equalTo(Map.of("message", "Internal server error")));
}
```

```java
public static class FailureProfile implements QuarkusTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(FailingAuthService.class, DownHealthCheck.class);
    }
}
```

El check DOWN y el servicio que lanza excepciones estan en src/test, son
alternativas sin prioridad global y solo se activan con ese perfil. La prueba
normal exige checks vacio: detecta si el check sintetico se filtra a otros
tests. No se introduce un endpoint ni un comportamiento nuevo de produccion.

El script prod genera su propio SQLite/clave y elige un puerto local temporal;
espera readiness con limite, falla si un resultado no coincide, restaura el
entorno y detiene solo su PID. Requiere PowerShell 7 y la aplicacion empaquetada.
Las pruebas Quarkus usan el puerto de pruebas predeterminado (8081): debe estar
libre. Ningun comando usa la base local del desarrollador.

## Comandos, directorio y resultados realmente ejecutados

Directorio de todos los comandos:

```text
C:\Users\Usuario\Documents\T67269-edinson-remigio\reqflow-workspace\workshop-auth-backend
```

Entorno: JDK 21 de Eclipse Adoptium y Maven 3.10.0. Preparacion de esta maquina:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
$env:Path = "$env:JAVA_HOME\bin;C:\ProgramData\chocolatey\lib\maven\apache-maven-3.10.0\bin;$env:Path"
```

No es necesario configurar credenciales ni claves reales para la suite.

| ejecucion | comando | codigo de salida | resultado real | evidencia |
|---|---|---|---|---|
| Primer intento dirigido | `mvn -B -ntp test "-Dtest=AuthResourceTest,EndpointContractTest,EndpointFailureTest"` | 1 | 19 casos: 3 pasan, 1 error de arranque JDBC y 15 omitidos por ese error. No cuenta como validacion exitosa. | [13-endpoints-targeted.log](../evidencias/13-endpoints-targeted.log) |
| Segundo intento dirigido | Mismo comando | 1 | 19 casos: 18 pasan, 1 falla por esperar cuerpo prod en modo test; sin errores ni omitidos. | [14-endpoints-targeted.log](../evidencias/14-endpoints-targeted.log) |
| Suite final y empaquetado | `mvn -B -ntp clean verify quarkus:build` | 0 | **36 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS**, gate JaCoCo aprobado. | [15-endpoints-verify.log](../evidencias/15-endpoints-verify.log) |
| Primer intento prod | Script siguiente | 1 | Timeout durante sondeo de arranque; sin afirmar que se validaron los cuerpos. | [16-endpoints-prod.log](../evidencias/16-endpoints-prod.log) |
| Prod final | Script siguiente | 0 | **5 comprobaciones aprobadas**, HTTP 400 (dos variantes), 405, 406, 415 y cuerpos vacios. | [17-endpoints-prod.log](../evidencias/17-endpoints-prod.log) |

Comando prod ejecutado despues del empaquetado:

```powershell
& '.\src\test\powershell\EndpointProdTest.ps1' `
  -JavaExecutable "$env:JAVA_HOME\bin\java.exe"
```

Desglose de las 36 pruebas finales: EndpointFailureTest 3, AuthResourceTest 6,
CorsTest 3, EndpointContractTest 11, LegacyDatabaseTest 2, PasswordStorageTest 1,
CorsConfigurationTest 1, CredentialMigrationTest 3, PasswordHasherTest 2,
RepositoryLifecycleTest 2 y TokenServiceTest 2.
Las 5 comprobaciones PowerShell son adicionales, no estan incluidas en las 36.

## Cobertura: reporte, metodo y umbral

Regla de [REGLAS.md](../REGLAS.md): >= 85% del codigo modificado.
El gate existente en [pom.xml](../pom.xml) exige ademas 85% global de lineas.
No se redujeron ni silenciaron CodeQL, Dependency Review o JaCoCo.

Reportes generados por la ejecucion limpia:

- [HTML JaCoCo](../target/site/jacoco/index.html).
- [XML JaCoCo](../target/site/jacoco/jacoco.xml).
- [Resultados Surefire](../target/surefire-reports/).
- [Medicion explicita](../evidencias/18-endpoints-cobertura.txt).

| metrica | resultado |
|---|---|
| Lineas de produccion agregadas/modificadas frente a main, incluidos archivos nuevos | **170/174 = 97.70%** |
| Lineas globales de produccion | **182/186 = 97.85%** |
| Ramas globales de produccion | **86/96 = 89.58%** |
| Gate Maven JaCoCo | All coverage checks have been met; salida 0 |

Metodo ejecutado: extraer numeros de lineas agregadas de
`git diff main --unified=0 -- src\main\java`; sumar todas las lineas de archivos
Java nuevos listados por `git ls-files --others --exclude-standard`.
Cruzar esos numeros por paquete/archivo con sourcefile/line del XML JaCoCo.
El denominador incluye solo lineas ejecutables (`mi + ci > 0`); el numerador
incluye las que tienen alguna instruccion cubierta (`ci > 0`).
Formula: `100 * lineas_modificadas_cubiertas / lineas_modificadas_ejecutables`.
Se excluyen lineas eliminadas, comentarios y blancos, no clases de produccion.

Esta tarea no cambia src/main: su cobertura nueva no tiene denominador propio.
Por eso se informa explicitamente el diff acumulado frente a main, no se
inventa un 100% para los tests. JaCoCo mide el backend ejercitado, no el codigo
de las pruebas ni el script PowerShell. La medicion procede de la suite JUnit;
no se atribuye cobertura adicional a la instancia prod del script.

## Fallos encontrados, pendientes y pruebas propuestas

### Fallos de esta ejecucion, corregidos solo en pruebas

1. Reinicio entre perfiles: el segundo arranque no encontraba driver SQLite.
   Se registro/desregistro el driver en el ciclo de vida del recurso de test;
   no se cambio el repositorio de produccion.
2. Expectativa de cuerpo prod usada en modo test: Quarkus agrega diagnosticos
   para username de tipo objeto. Se conservan pruebas separadas de ambos
   modos; el script **exige** cuerpo vacio en prod. No se relajo a "cualquier
   cuerpo" ni se oculto una discrepancia de produccion.
3. El primer sondeo de readiness prod expiro. El script ahora reintenta tambien
   timeouts de conexion dentro de un limite y sigue fallando si no arranca.
   No se atrapan fallos de aserciones ni errores arbitrarios como exitos.

### Defectos conocidos del backend, no corregidos ni ocultados

- La evidencia historica anterior a H-05 conserva el 500 legado y SQL como
  401; no describe el contrato actual. Ver correccion y resultados debajo.
- **Salud sin checks de negocio:** UP/checks vacio no comprueba SQLite. El
  503 se prueba con un check sintetico, no demuestra que produccion detecte
  caidas de la base.
- No se descubrio otro bug de produccion durante esta ampliacion. Los estados
  no se duplican aqui: referencia unica [HALLAZGOS.md](../HALLAZGOS.md).

### Pendientes y propuestas, no presentadas como ejecutadas

- Comportamiento ante excepciones inesperadas de infraestructura de salud:
  ENDPOINTS lo deja abierto; no se inventa un JSON 500 ni un test de contrato.
- Validacion de JWT por consumidores reales, TLS, proxies y conectividad entre
  componentes: fuera de ambito; no se afirman resultados.
- No se propone agregar checks reales ni endpoints refresh/logout para llenar
  huecos de cobertura. Requeririan una decision de producto y otro alcance.
- El fallo SQL HTTP usa el perfil aislado: un servicio alternativo invoca un
  repositorio con URL JDBC no disponible. Es una SQLException real, no una
  caida de la base de produccion ni una prueba de infraestructura externa.

## H-05: errores controlados (implementacion aprobada)

Directorio y entorno: los indicados en Comandos. Todos usan JDK 21.

| fase | comando Maven | salida | resultado real |
|---|---|---|---|
| Caracterizacion antes | `mvn -B -ntp "-Dtest=AuthResourceTest,EndpointContractTest,EndpointFailureTest,RepositoryLifecycleTest" test` | 0 | 22 pruebas pasan; captura el contrato legado. |
| Correccion antes | `mvn -B -ntp "-Dtest=EndpointFailureTest#hidesInternalDetailsForFailuresWithAndWithoutMessages+reportsStorageFailureAsGenericServerErrorInsteadOfInvalidCredentials" test` | 1 | 2 fallos esperados: filtra detalles y SQL devuelve 401. |
| Caracterizacion y correccion despues | `mvn -B -ntp clean verify` | 0 | 39 pruebas, 0 fallos/errores/omitidas; gate JaCoCo >=85% pasa. |

Evidencia: [antes](../evidencias/19-h05-caracterizacion-antes.log),
[regresiones rojas](../evidencias/20-h05-regresion-antes.log),
[despues](../evidencias/21-h05-despues.log),
[diff propio](../evidencias/H-05.diff) y
[cobertura incremental](../evidencias/22-h05-cobertura.txt).
Los 200/400/401 de credenciales y errores del framework permanecen iguales;
solo se actualizan expectativas del cambio de 500 aprobado.
AuthResourceTest captura el logger y comprueba un unico registro con clase y
stack, sin mensaje, credenciales, hash, token ni Throwable que pueda revelarlos.
El detalle de mensaje arbitrario se omite tambien en servidor deliberadamente.

## H-06: versiones y compatibilidad

Prueba nueva antes de cambiar pom.xml:
[LoginResponseTest.java](../src/test/java/com/bcp/workshop/auth/LoginResponseTest.java).
Fija ocho variantes de escape exacto (`&`, `<`, `>`, comillas, apostrofe,
Unicode, null y combinacion), ademas del DTO publico completo.

| fase | comando / comprobacion | salida | resultado real |
|---|---|---|---|
| Caracterizacion antes | `mvn -B -ntp "-Dtest=LoginResponseTest,AuthResourceTest,EndpointContractTest,EndpointFailureTest,TokenServiceTest,CredentialMigrationTest" test quarkus:build` | 0 | 30 pruebas pasan con las versiones anteriores. |
| Contrato prod antes | `& .\src\test\powershell\EndpointProdTest.ps1 -JavaExecutable "$env:JAVA_HOME\bin\java.exe"` | 0 | 5 comprobaciones de cuerpos HTTP pasan. |
| Correccion SCA antes | Comparar numero de alertas OSV con cero | 1 | 49 coincidencias, regresion esperada. |
| Caracterizacion y correccion despues | `mvn -B -ntp clean verify quarkus:build dependency:tree "-DoutputFile=evidencias\28-h06-dependencias-despues.txt"` | 0 | 41 pruebas, 0 fallos/errores/omitidas; gate JaCoCo pasa. |
| Contrato prod despues | Mismo comando PowerShell anterior | 0 | 5 comprobaciones pasan sin cambiar ninguna expectativa. |
| Correccion SCA despues | POST OSV querybatch, comparar alertas con cero | 0 | 243 dependencias directas/transitivas consultadas, 0 coincidencias. |

Directorio/entorno: los indicados en Comandos, JDK 21.
Evidencias: [antes](../evidencias/25-h06-caracterizacion-antes.log),
[prod antes](../evidencias/26-h06-prod-antes.log),
[SCA antes](../evidencias/27-h06-sca-antes.txt),
[despues](../evidencias/28-h06-despues.log),
[prod despues](../evidencias/30-h06-prod-despues.log),
[investigacion SCA completa](../evidencias/31-h06-sca.md),
[diff propio](../evidencias/H-06.diff).

Cobertura: **186/190 = 97.89% global**; H-06 no cambia lineas Java de
produccion y no tiene denominador incremental propio.
[Medicion](../evidencias/32-h06-cobertura.txt),
[XML](../evidencias/h06-jacoco.xml). No se modifico ENDPOINTS ni se relajaron
aserciones del framework para aceptar una regresion. Cero alertas de esta
consulta no equivale a ausencia global de vulnerabilidades.

## H-07: ownership de autenticacion y emision

[AuthServiceTest.java](../src/test/java/com/bcp/workshop/auth/AuthServiceTest.java)
registra llamadas al repositorio, verificador y emisor sin nuevas librerias:
autenticacion valida emite una sola vez con la identidad almacenada (no la
entrada); usuario ausente, password incorrecta o error de datos nunca emiten.
La validacion HTTP precede al servicio. Tambien cubre error de firma y el
DTO exacto. Tres pruebas adicionales invocan directamente el servicio.

Antes del refactor se ejecutaron seis caracterizaciones de orquestacion
contra el adaptador existente, junto con la matriz HTTP/DTO/JWT. La prueba
estructural detecto TokenService en el Resource; despues comprueba que
pertenece al servicio. No se usa esa comprobacion estructural como sustituto
de los contadores y resultados funcionales.

| fase | comando / comprobacion | salida | resultado real |
|---|---|---|---|
| Caracterizacion antes | Selector JUnit exacto reproducido debajo | 0 | 33 pruebas pasan, incluida la forma 500 de H-05. |
| Correccion antes | `mvn -B -ntp "-Dtest=AuthServiceTest#assignsTokenOrchestrationToServiceNotHttpAdapter" test` | 1 | 1 fallo esperado: el Resource aun posee el emisor. |
| Caracterizacion y correccion despues | `mvn -B -ntp clean verify quarkus:build` | 0 | 51 pruebas, 0 fallos/errores/omitidas; JaCoCo pasa. |
| Contrato prod despues | `& .\src\test\powershell\EndpointProdTest.ps1 -JavaExecutable "$env:JAVA_HOME\bin\java.exe"` | 0 | Las mismas 5 comprobaciones pasan. |

Comando exacto de caracterizacion previa, en el directorio/entorno indicados:

```powershell
mvn -B -ntp "-Dtest=AuthResourceTest,EndpointContractTest,EndpointFailureTest,TokenServiceTest,LoginResponseTest,AuthServiceTest#emitsExactlyOnceWithStoredIdentityAfterSuccessfulVerification+doesNotVerifyOrEmitForAnAbsentAccount+doesNotEmitWhenPasswordVerificationFails+doesNotReachBusinessLogicBeforeValidationSucceeds+preservesGenericFailureAndDoesNotEmitWhenStorageFails+preservesGenericFailureWhenTokenIssuanceFails" test
```

Evidencia: [antes](../evidencias/33-h07-caracterizacion-antes.log),
[regresion roja](../evidencias/34-h07-arquitectura-antes.log),
[despues](../evidencias/35-h07-despues.log),
[prod](../evidencias/37-h07-prod-despues.log),
[diff propio](../evidencias/H-07.diff).
Cobertura incremental **16/16 = 100%**, global **191/195 = 97.95%**,
calculada con hunks agregados frente al snapshot anterior al item y XML JaCoCo,
igual que el metodo explicado arriba; [medicion](../evidencias/36-h07-cobertura.txt),
[reporte XML](../evidencias/h07-jacoco.xml). No se excluyeron clases.

Resource conserva unicamente validacion/adaptacion HTTP y delegacion de errores
al mapper H-05. AuthService.login orquesta verificacion y emision;
LoginResponse.from sigue siendo el unico mapeo/escape de identidad.
No se duplican duracion, mapeo ni armado de respuestas de rechazo. Solo se
adapto el constructor de los dobles, sin cambiar expectativas.
ENDPOINTS conserva el mismo SHA-256 desde el cierre H-05:
`43EC9A109AF50E99BC566D3E11EF40DE938D41B7F0185CB4E4473DBC84FF4220`.

## Documentos consultados

[ENDPOINTS.md](ENDPOINTS.md), [REGLAS.md](../REGLAS.md),
[README.md](../README.md), [GOOD_PRACTICES.md](GOOD_PRACTICES.md) y
[HALLAZGOS.md](../HALLAZGOS.md), ademas de pom.xml, codigo del Resource y los
tests/fixtures enlazados. Referencia de comportamiento de Quarkus: mapper de
deserializacion en test y comprobacion prod sobre la version instalada.
