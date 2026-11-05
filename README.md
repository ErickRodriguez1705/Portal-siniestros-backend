# Portal de Siniestros — Backend

API REST para gestionar pólizas y siniestros vehiculares. Java 17, Spring Boot 3.5, PostgreSQL y MongoDB.

## Ejecutar en IntelliJ IDEA

1. Abre esta carpeta como proyecto Maven y selecciona JDK 17 o superior.
2. Inicia PostgreSQL y MongoDB con `docker compose up -d` desde esta carpeta.
3. Ejecuta `PortalApplication` en IntelliJ IDEA. También puedes usar `mvn spring-boot:run` si tienes Maven instalado.
4. La API estará en `http://localhost:8081/api`.

### Acceso de evaluación local

1. Copia `application-local.properties.example` como `application-local.properties` en la raíz del proyecto.
2. En la copia, reemplaza `CAMBIAR_CONTRASENA_POSTGRES` por la contraseña de tu PostgreSQL y ajusta el usuario si corresponde.
3. Inicia el backend. Si la cuenta aún no existe, se creará automáticamente con rol administrador.

| Campo | Valor para evaluación local |
| --- | --- |
| Correo | `administrador@portal-siniestros.local` |
| Contraseña | `PortalSiniestros2026!` |

Estas credenciales son públicas y solo sirven para una instalación local con datos ficticios. No las uses en un servidor público. Para otra instalación, configura `ADMIN_INITIAL_EMAIL`, `ADMIN_INITIAL_PASSWORD` (mínimo 12 caracteres) y, opcionalmente, `ADMIN_INITIAL_NAME`. El administrador puede registrar clientes y asignarles pólizas desde la web.

La cuenta administradora se crea una sola vez. Cambiar esas variables después no modifica una cuenta existente. Los registros que ya estaban en PostgreSQL y MongoDB se conservan al actualizar el proyecto.

Configura `DB_PASSWORD` antes de iniciar la API. Para trabajar en tu equipo, puedes crear `application-local.properties` en la raíz del proyecto con `spring.datasource.username` y `spring.datasource.password`; Spring lo carga automáticamente y Git lo ignora. Los demás valores de conexión se configuran en `src/main/resources/application.properties` o con `DB_URL`, `DB_USER`, `MONGO_URI`, `FRONTEND_ORIGIN` y `UPLOAD_DIR`. Configura `JWT_SECRET` con al menos 32 caracteres en un despliegue. Si se omite, se genera una clave temporal al arrancar: las sesiones dejarán de ser válidas tras cada reinicio.

### Si PostgreSQL rechaza la contraseña

El proyecto intenta conectarse por defecto a `jdbc:postgresql://localhost:5432/siniestros` con usuario `siniestros` y la contraseña definida en `DB_PASSWORD`. El contenedor de `compose.yaml` usa `localdev` solo para desarrollo local. Un mensaje `password authentication failed for user "siniestros"` significa que el servidor en ese puerto no reconoce esa combinación. El mensaje posterior de Hibernate sobre `Dialect` es una consecuencia de la conexión fallida.

1. Comprueba qué PostgreSQL está atendiendo en el puerto `5432`. Si usas Docker, ejecuta `docker compose ps` en esta carpeta. Si ya tienes un PostgreSQL instalado en Windows, puede estar ocupando el puerto antes que el contenedor.
2. Prueba la conexión con `psql -h localhost -p 5432 -U siniestros -d siniestros`. Cuando solicite contraseña, usa la de tu servidor. El contenedor local de `compose.yaml` emplea `localdev`.
3. Si tu servidor usa otra cuenta, configura `DB_URL`, `DB_USER` y `DB_PASSWORD` con sus valores reales en **Run > Edit Configurations > Environment variables** de IntelliJ IDEA y reinicia la aplicación.
4. Si el contenedor se creó anteriormente con otra contraseña, cambiar `POSTGRES_PASSWORD` en `compose.yaml` no cambia la contraseña almacenada en el volumen existente. Usa la contraseña original o cambia el usuario dentro de PostgreSQL. No borres el volumen si contiene datos que quieras conservar.

## API

| Método | Ruta | Uso |
| --- | --- | --- |
| POST | `/api/auth/login` | Iniciar sesión y obtener JWT |
| GET | `/api/auth/me` | Identidad de la sesión |
| GET | `/api/admin/clientes` | Listar clientes (solo administrador) |
| POST | `/api/admin/clientes` | Registrar cliente (solo administrador) |
| GET | `/api/admin/polizas` | Listar pólizas (solo administrador) |
| POST | `/api/admin/polizas` | Asignar póliza (solo administrador) |
| GET | `/api/admin/siniestros` | Listar siniestros reportados de todos los clientes (solo administrador) |
| GET | `/api/admin/siniestros/{id}` | Ver el detalle de un siniestro (solo administrador) |
| PUT | `/api/admin/siniestros/{id}/estado` | Pasar a revisión, aprobar o rechazar (solo administrador) |
| GET | `/api/admin/siniestros/{id}/imagenes/{nombre}` | Ver una foto asociada al siniestro (solo administrador) |
| GET | `/api/polizas` | Pólizas activas del cliente |
| POST | `/api/siniestros` | Crear reporte |
| GET | `/api/siniestros` | Listar reportes propios |
| GET | `/api/siniestros/{id}` | Ver reporte propio |
| PUT | `/api/siniestros/{id}` | Editar reporte propio |
| DELETE | `/api/siniestros/{id}` | Eliminar reporte propio y su detalle |
| POST | `/api/imagenes` | Subir JPG o PNG como `multipart/form-data` con campo `archivo` |
| GET | `/api/imagenes/{nombre}` | Leer una imagen propia con JWT |

Salvo el inicio de sesión, las rutas requieren `Authorization: Bearer <token>`. Las rutas `/api/admin/**` exigen el rol `ADMIN` en el backend.

Ejemplo de reporte:

```json
{
  "numeroPoliza": "AUT-2026-0001",
  "fechaAccidente": "2026-11-04T10:30:00",
  "ubicacion": "Av. Arequipa, Lima",
  "descripcion": "El vehículo recibió un impacto en el parachoques trasero al detenerse en el semáforo.",
  "fotos": ["/api/imagenes/550e8400-e29b-41d4-a716-446655440000.jpg"],
  "metadatos": {"tipo":"choque","terceros":"si","datosTerceros":"Conductor de otro vehículo"}
}
```

## Cómo se reparten los datos

PostgreSQL conserva cliente, póliza, fecha, ubicación y estado del siniestro. MongoDB conserva descripción, enlaces de fotos y metadatos, vinculados por `siniestroId`.

Las imágenes subidas se guardan como archivos en `./uploads` (configurable con `UPLOAD_DIR`) dentro de una carpeta por cliente. MongoDB conserva sus rutas `/api/imagenes/{nombre}`. Cada archivo debe ser JPG o PNG y pesar como máximo 5 MB; un reporte admite hasta cinco fotos. La descarga requiere JWT y solo busca archivos del cliente autenticado. `uploads/` está excluido de Git. Para producción haría falta almacenamiento persistente administrado y limpieza de archivos que ya no se usan.

Las dos bases no comparten una transacción. La API primero crea un registro `PENDIENTE_DETALLE` en PostgreSQL, luego guarda el detalle en MongoDB y finalmente marca el reporte `REPORTADO`. Los registros pendientes no se muestran al cliente. Para una versión de producción, habría que agregar reconciliación y reintentos automáticos, migraciones de esquema y gestión segura de archivos fotográficos.

La edición usa el mismo cuerpo JSON que la creación. La API verifica que el siniestro y la póliza pertenezcan al cliente autenticado. La eliminación borra el registro de PostgreSQL y el detalle de MongoDB. Como no hay una transacción compartida, una caída entre ambos borrados podría dejar un detalle huérfano; la reconciliación de estos casos queda pendiente para producción.

## Revisión de siniestros

El administrador puede cambiar un reporte `REPORTADO` a `EN_REVISION`, `APROBADO` o `RECHAZADO`. Desde `EN_REVISION` puede aprobarlo o rechazarlo. Para rechazarlo debe indicar un motivo; la API guarda la observación y fecha de la decisión en PostgreSQL. Una decisión final no se modifica desde esta versión. El cliente puede ver el estado y la observación, pero solo puede editar o eliminar un reporte mientras esté `REPORTADO`. Aprobar aquí registra una decisión de revisión; no calcula indemnizaciones ni ejecuta pagos.

### Actualización del esquema

Flyway ejecuta las migraciones de `src/main/resources/db/migration` al iniciar el backend. La migración `V2__ampliar_estados_siniestro.sql` actualiza automáticamente la restricción de estados en instalaciones anteriores, sin borrar reportes. Para adoptar una base existente sin historial de Flyway, `baseline-on-migrate` registra su versión inicial y después ejecuta V2. En una base nueva, V2 no encuentra la tabla y Hibernate la crea con los cinco estados. Las siguientes modificaciones de esquema deben incorporarse como nuevas migraciones versionadas.
