# Alinear el contrato de token con los consumidores

## Fuente

Issue aprobado: [#12 — Alinear validación de tokens con el JWT emitido por Quarkus](https://github.com/user-bcp-student14/workshop-requirements-api/issues/12).

Evidencia: `src/main/java/com/bcp/workshop/auth/TokenService.java:50-54` emite JWT con RS256, issuer y audience. `workshop-requirements-api/app/main.py:17-26` documenta el consumidor legado incompatible. El contexto funcional está descrito en `docs/ENDPOINTS.md`.

## Contexto actual

El backend Quarkus autentica credenciales y entrega un token en la respuesta de `POST /auth/login`. La API Python debe aceptar ese token para los endpoints protegidos; el frontend lo transmite como Bearer al consultar las tareas.

## Problema confirmado

El formato de token emitido por este servicio no coincide con el formato que espera actualmente la API Python, por lo que el flujo autenticación → consulta protegida puede fallar.

## Comportamiento a preservar

- `POST /auth/login` conserva su ruta, campos de respuesta y claims documentados.
- El servicio solo emite tokens firmados según la configuración vigente; no incorpora secretos ni credenciales reales al repositorio.
- Los tokens inválidos o expirados no deben conceder acceso.
- No se restablece un fallback inseguro al formato legado.

## Alcance

Definir y mantener el contrato de emisión de token desde el backend, incluyendo algoritmo, issuer, audience, sujeto, rol y expiración, de forma que los consumidores puedan validar el token emitido.

## Fuera de alcance

Implementación del validador en la API, cambios de almacenamiento de contraseñas, autorización sobre tareas y cambios del diseño de inicio de sesión del frontend.

## Criterios de aceptación

- La documentación del backend identifica los campos y condiciones que deben usar los consumidores para validar un token.
- El comportamiento de `POST /auth/login` sigue cumpliendo el contrato documentado.
- Una prueba de integración del flujo con los consumidores demuestra que el token emitido puede ser validado por un consumidor configurado, sin depender de valores secretos guardados en Git.

## Contratos afectados/protegidos

`POST /auth/login`, cuerpo de respuesta `token`/`expiresIn`/`user`, claims del JWT y header `Authorization: Bearer` usado por los servicios consumidores. La documentación vigente de endpoints debe permanecer consistente con el contrato publicado.

## Riesgos, supuestos y preguntas abiertas

- Decisiones aprobadas: publicar la clave mediante JWKS en `/auth/.well-known/jwks.json`, issuer `workshop-auth-backend` y audience `workshop-requirements-api`.
- La URL de JWKS debe ser confiable y alcanzable por el consumidor; coordinar la rotación de claves y su caché.
- Confirmar si existen consumidores adicionales al frontend y la API Python antes de retirar compatibilidad antigua.
- No se debe cambiar issuer, audience, algoritmo o claims sin coordinación con esos consumidores.
