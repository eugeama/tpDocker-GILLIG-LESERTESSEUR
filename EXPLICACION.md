# Explicación completa del proyecto: Kotlin + Ktor + MySQL + Docker

---

## Índice

1. [Qué construimos y por qué](#1-qué-construimos-y-por-qué)
2. [Arquitectura general](#2-arquitectura-general)
3. [Kotlin: el lenguaje](#3-kotlin-el-lenguaje)
4. [Ktor: el framework web](#4-ktor-el-framework-web)
5. [HikariCP y los pools de conexiones](#5-hikaricp-y-los-pools-de-conexiones)
6. [JDBC: cómo Kotlin habla con MySQL](#6-jdbc-cómo-kotlin-habla-con-mysql)
7. [Explicación línea por línea de cada archivo](#7-explicación-línea-por-línea-de-cada-archivo)
8. [Docker: qué es y por qué lo usamos](#8-docker-qué-es-y-por-qué-lo-usamos)
9. [Beneficios y desventajas del stack](#9-beneficios-y-desventajas-del-stack)
10. [Cómo correr el proyecto](#10-cómo-correr-el-proyecto)

---

## 1. Qué construimos y por qué

Construimos una **API REST**: un programa que escucha en un puerto HTTP y responde a pedidos en formato JSON. No tiene interfaz visual — está pensado para que otros programas (aplicaciones móviles, frontends web, otros servicios) lo consulten.

La API tiene cuatro endpoints:

| Endpoint | Método | Qué hace |
|---|---|---|
| `/health` | GET | Responde que el servidor está vivo, sin tocar la DB |
| `/db-status` | GET | Consulta la DB con `SELECT NOW()` y reporta si la conexión funciona |
| `/items` | POST | Recibe un JSON con un nombre y lo guarda en MySQL |
| `/items` | GET | Lee todos los registros de MySQL y los devuelve como JSON |

El objetivo académico es demostrar:
- Una imagen Docker que ejecuta un backend
- Comunicación entre dos contenedores en una red Docker privada
- Credenciales leídas de variables de entorno (no hardcodeadas)
- Al menos los dos endpoints obligatorios (`/health` y `/db-status`)
- Los endpoints opcionales (`/items`) con persistencia real en base de datos

---

## 2. Arquitectura general

```
  Tu computadora (navegador, curl, Postman)
           │
           │ HTTP — puerto 8081
           ▼
  ┌──────────────────────────────────┐
  │       contenedor-ktor            │
  │       Sistema: Ubuntu + JDK 21   │
  │                                  │
  │   Netty (servidor HTTP)          │
  │      │                           │
  │   Ktor (framework)               │
  │      │                           │
  │   HikariCP (pool de conexiones)  │
  └──────────────┬───────────────────┘
                 │
                 │ TCP — puerto 3306
                 │ (red interna Docker: mi-red)
                 │
  ┌──────────────▼───────────────────┐
  │       contenedor-sql             │
  │       MySQL 8                    │
  │       Base de datos: my_database │
  │       Tabla: usuarios            │
  └──────────────────────────────────┘
```

**Puntos clave de esta arquitectura:**

- El contenedor MySQL **no está expuesto** al exterior. Solo el contenedor Ktor puede hablarle, y solo porque están en la misma red Docker.
- El contenedor Ktor sí expone el puerto 8081 al host (tu computadora).
- Docker actúa como DNS interno: el hostname `contenedor-sql` resuelve a la IP del contenedor MySQL automáticamente dentro de la red `mi-red`.

---

## 3. Kotlin: el lenguaje

### ¿Qué es?

Kotlin es un lenguaje de programación creado por JetBrains (los mismos que hacen IntelliJ IDEA) en 2011 y liberado en 2016. Compila a **bytecode de la JVM**, lo que significa que corre en la misma máquina virtual que Java y puede usar cualquier librería Java directamente.

Google lo adoptó como lenguaje oficial de Android en 2017. Hoy también se usa ampliamente en backends.

### Relación con Java

Kotlin **no reemplaza** la JVM — la usa. Cuando compilás código Kotlin, el resultado es un archivo `.class` idéntico al que genera Java. Por eso:

- Podés llamar código Java desde Kotlin y viceversa sin ningún adaptador
- Usás las mismas librerías (HikariCP, el driver MySQL, etc.)
- El rendimiento en tiempo de ejecución es igual al de Java

La diferencia es en **productividad del desarrollador** y **seguridad del código**.

### Conceptos clave de Kotlin usados en este proyecto

#### `val` vs `var`

```kotlin
val nombre = "Liam"   // inmutable: no se puede reasignar
var contador = 0      // mutable: se puede cambiar
contador = 1          // OK
nombre = "otro"       // ERROR de compilación
```

Preferir `val` es una buena práctica porque reduce bugs: si una variable no debería cambiar, el compilador lo garantiza.

#### `data class`

```kotlin
data class Usuario(val id: Int, val nombre: String, val creado: String)
```

Una `data class` es una clase cuyo único propósito es contener datos. Kotlin genera automáticamente:
- Constructor con todos los parámetros
- `equals()` — compara por valor, no por referencia
- `hashCode()` — necesario para usar objetos en maps y sets
- `toString()` — imprime `Usuario(id=1, nombre=Admin, creado=2026-05-25)`
- `copy()` — crea una copia modificando solo algunos campos

En Java equivalente serían ~40 líneas. En Kotlin, 1 línea.

#### Null safety

En la mayoría de los lenguajes, cualquier variable puede ser `null` y descubrís el problema en runtime con un error. Kotlin lo resuelve en el tipo:

```kotlin
var nombre: String = "Liam"   // NUNCA puede ser null
var apodo: String? = null     // PUEDE ser null — el ? lo indica

nombre.length   // siempre seguro
apodo.length    // ERROR de compilación — puede ser null
apodo?.length   // OK: devuelve null si apodo es null, sin explotar
apodo!!.length  // OK pero peligroso: fuerza el acceso, lanza excepción si es null
```

Este sistema elimina en compilación la clase entera de errores `NullPointerException`.

#### Extension functions

Kotlin permite añadir métodos a clases existentes sin modificarlas ni heredar de ellas:

```kotlin
fun Application.configureRouting() {
    // "this" es la instancia de Application
}
```

Esto se llama **extension function**. Ktor la usa para modularizar la configuración: cada archivo agrega funcionalidad a `Application` como si fuera su propio método, pero el código está separado físicamente.

#### Lambdas y bloques `{ }`

En Kotlin, las funciones son ciudadanos de primera clase — se pueden pasar como parámetros:

```kotlin
get("/health") {
    call.respond(...)
}
```

El bloque `{ ... }` después de `get("/health")` es una **lambda**: una función anónima que Ktor ejecutará cada vez que llegue una request a esa ruta. Esto es lo que hace el DSL de Ktor legible y conciso.

#### `.apply { }`, `.use { }`, y otros scope functions

```kotlin
val config = HikariConfig().apply {
    jdbcUrl = "..."
    username = "..."
}
```

`.apply { }` ejecuta el bloque sobre el objeto, con `this` apuntando al objeto. Evita repetir `config.` en cada línea. Es azúcar sintáctico — el compilador lo convierte en llamadas normales.

```kotlin
dataSource.connection.use { conn ->
    // usar conn
}
```

`.use { }` garantiza que el recurso (la conexión) se cierra al salir del bloque, incluso si hay una excepción. Es el equivalente a `try-with-resources` de Java o `with` en Python.

---

## 4. Ktor: el framework web

### ¿Qué es?

Ktor es un framework web creado por JetBrains para Kotlin. Está diseñado desde cero para usar **coroutines** de Kotlin, lo que lo hace eficiente en concurrencia.

A diferencia de Spring Boot (el framework Java más popular), Ktor es **minimalista**: no viene con ORM, autenticación, validación ni ningún otro módulo por defecto. Vos elegís exactamente qué plugins usar. Esto lo hace liviano y transparente.

### Arquitectura de Ktor

Ktor tiene tres capas:

1. **Motor (Engine)**: el servidor HTTP de bajo nivel. En este proyecto usamos **Netty**, un servidor HTTP asíncrono de alto rendimiento desarrollado por JBoss. Ktor también soporta Jetty y CIO (el motor propio).

2. **Aplicación (Application)**: el objeto central de Ktor. Es donde se instalan plugins y se registran módulos.

3. **Plugins (antes llamados Features)**: funcionalidades opcionales que se instalan en la aplicación. Ejemplos: `ContentNegotiation` (serialización JSON), `Authentication`, `CORS`, `Sessions`, etc.

### `EngineMain` y `application.yaml`

```kotlin
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
```

`EngineMain` es una clase de Ktor que:
1. Lee el archivo `application.yaml` del classpath
2. Crea el servidor Netty en el puerto configurado
3. Carga los módulos declarados en el YAML **en el orden en que están listados**
4. Bloquea el thread principal hasta que el servidor se detenga

El `application.yaml`:

```yaml
ktor:
  deployment:
    port: 8081
  application:
    modules:
      - com.example.SerializationKt.configureSerialization
      - com.example.BDDKt.configureDatabase
      - com.example.RoutingKt.configureRouting
```

Cada entrada en `modules` tiene el formato `PaqueteKt.nombreFuncion`. Cuando Kotlin compila un archivo `BDD.kt`, genera una clase Java llamada `BDDKt`. Las funciones de nivel superior (no dentro de una clase) se convierten en métodos estáticos de esa clase. Así es como Ktor las encuentra y las llama por reflexión al arrancar.

### DSL de routing

DSL significa **Domain-Specific Language**: un mini-lenguaje diseñado para un propósito específico. El routing de Ktor es un DSL construido con las features de Kotlin (lambdas, extension functions, builders):

```kotlin
routing {
    get("/health") { ... }
    post("/items") { ... }
    get("/items") { ... }
}
```

Cada bloque `{ }` recibe un `PipelineContext` como receptor, y dentro podés usar `call` para acceder a la request y construir la response. `call.receive<T>()` deserializa el body de la request al tipo T. `call.respond(status, body)` serializa el body a JSON y envía la respuesta.

### Coroutines (concepto fundamental)

Las coroutines son la característica más importante de Kotlin para backends. Para entenderlas, primero hay que entender el problema que resuelven:

**El problema con threads tradicionales:**

Un servidor HTTP tradicional (como Spring MVC) asigna un thread a cada request:
- Request 1 → Thread 1
- Request 2 → Thread 2
- ...
- Request 1000 → Thread 1000

Cada thread del sistema operativo consume ~1MB de memoria de stack y hay un límite práctico de ~1000-2000 threads por proceso. Cuando un thread espera (lectura de DB, llamada externa), está bloqueado y no puede hacer nada más.

**La solución con coroutines:**

Una coroutine es un bloque de código que puede **suspenderse** (ceder el control) mientras espera algo, y **reanudarse** cuando el resultado está listo, **sin bloquear el thread**:

```
Thread 1: ─── Request A ──▶ [espera DB] ──── Request B ──▶ [espera DB] ──── continúa A ──── continúa B ───
```

Un solo thread puede manejar miles de requests concurrentes porque nunca está bloqueado esperando — siempre está ejecutando trabajo útil. Ktor usa este modelo en todos sus handlers.

---

## 5. HikariCP y los pools de conexiones

### El problema: abrir conexiones es caro

Abrir una conexión a una base de datos no es instantáneo. Involucra:
1. Establecer una conexión TCP con el servidor MySQL
2. Autenticar el usuario con usuario y contraseña
3. Negociar parámetros de la sesión (charset, timezone, etc.)

En una base de datos típica esto tarda entre **5 y 50 milisegundos**. Si tu API recibe 100 requests por segundo, abrir y cerrar 100 conexiones por segundo añade una latencia constante e innecesaria.

### La solución: un pool de conexiones

Un **pool** es un grupo de conexiones que se crean al arrancar el servidor y se **reutilizan**:

```
Arranque del servidor:
  Pool crea 10 conexiones → [C1][C2][C3][C4][C5][C6][C7][C8][C9][C10]
  Todas están "disponibles"

Request llega:
  Pool entrega C1 al handler
  Pool marca C1 como "en uso"

Handler termina:
  Handler "devuelve" C1 al pool (no la cierra)
  Pool marca C1 como "disponible" de nuevo

Próxima request:
  Pool entrega C1 nuevamente (ya establecida, instantáneo)
```

El desarrollador no gestiona esto manualmente — Ktor llama a `dataSource.connection` y HikariCP hace todo el trabajo de prestar y recibir conexiones.

### ¿Qué es HikariCP específicamente?

HikariCP (Hikari Connection Pool) es la librería Java de pool de conexiones más popular y performante. Es la que usa Spring Boot por defecto. El nombre "Hikari" viene del japonés 光 (luz) — haciendo referencia a su velocidad.

En `BDD.kt`:

```kotlin
lateinit var dataSource: HikariDataSource
```

`dataSource` es el objeto que representa el pool entero. Es una variable global (`lateinit` indica que se inicializará antes del primer uso). Cualquier función del proyecto puede pedir una conexión del pool llamando a `dataSource.connection`.

```kotlin
val config = HikariConfig().apply {
    jdbcUrl         = "jdbc:mysql://contenedor-sql:3306/my_database?..."
    driverClassName = "com.mysql.cj.jdbc.Driver"
    username        = "user_name"
    password        = "user_password"
    maximumPoolSize = 10         // máximo 10 conexiones simultáneas
    isAutoCommit    = false      // las transacciones no se confirman automáticamente
    transactionIsolation = "TRANSACTION_REPEATABLE_READ"  // nivel de aislamiento
    validate()                   // verifica que la config sea válida antes de crear el pool
}
dataSource = HikariDataSource(config)
```

`HikariDataSource(config)` crea el pool físicamente: abre las primeras conexiones al MySQL, verifica que funcionen, y las deja listas para prestar.

### `maximumPoolSize = 10`

Si llegan 15 requests simultáneas, las primeras 10 obtienen conexión inmediatamente. Las otras 5 esperan en cola hasta que alguna de las 10 devuelva su conexión. Si esperan demasiado (configurable, por defecto 30 segundos), HikariCP lanza una excepción de timeout.

### `isAutoCommit = false`

Una **transacción** es una secuencia de operaciones que se ejecutan como una unidad: o todas tienen éxito (commit) o ninguna ocurre (rollback).

Con `autoCommit = false`, cada operación SQL no se confirma automáticamente — hay que llamar a `conn.commit()`. Esto permite agrupar varias operaciones en una transacción atómica.

En `insertUsuario()`, se establece `conn.autoCommit = true` explícitamente porque es una única operación simple que debe confirmarse sola.

---

## 6. JDBC: cómo Kotlin habla con MySQL

### ¿Qué es JDBC?

JDBC (Java Database Connectivity) es la **API estándar de Java** para conectarse a bases de datos relacionales. Define las interfaces `Connection`, `Statement`, `PreparedStatement` y `ResultSet`. Cada base de datos provee su propio **driver** que implementa esas interfaces.

Para MySQL, el driver es `com.mysql.cj.jdbc.Driver` y viene en la dependencia `mysql:mysql-connector-java:8.0.33` declarada en `build.gradle.kts`.

La URL de conexión JDBC tiene este formato:
```
jdbc:mysql://host:puerto/baseDeDatos?parametros
jdbc:mysql://contenedor-sql:3306/my_database?useSSL=false&serverTimezone=UTC
```

### `Statement` vs `PreparedStatement`

**Statement** (no usar para datos del usuario):
```kotlin
stmt.executeQuery("SELECT * FROM usuarios WHERE nombre = '$nombre'")
```

Si `nombre` es `"'; DROP TABLE usuarios; --"`, la query resultante sería:
```sql
SELECT * FROM usuarios WHERE nombre = ''; DROP TABLE usuarios; --'
```
Esto es una **inyección SQL** — el atacante puede ejecutar cualquier SQL en tu base de datos. Es la vulnerabilidad número 1 en aplicaciones web (OWASP Top 10).

**PreparedStatement** (siempre usar con datos del usuario):
```kotlin
conn.prepareStatement("INSERT INTO usuarios (nombre, creado) VALUES (?, CURDATE())")
stmt.setString(1, nombre)
```

El `?` es un placeholder. El driver MySQL separa el SQL de los datos: compila la query primero, luego sustituye el valor de forma segura. Aunque `nombre` contenga caracteres SQL especiales, serán tratados como texto literal, nunca como código.

### `ResultSet`

Un `ResultSet` es el cursor de resultados de una query. Se comporta como un iterador:

```kotlin
val rs = stmt.executeQuery("SELECT id, nombre, creado FROM usuarios")
while (rs.next()) {           // avanza al siguiente registro, devuelve false cuando no hay más
    val id = rs.getInt("id")
    val nombre = rs.getString("nombre")
    val creado = rs.getDate("creado").toString()
}
```

---

## 7. Explicación línea por línea de cada archivo

### `main.kt`

```kotlin
package com.example
```
Todos los archivos del proyecto pertenecen al paquete `com.example`. Los paquetes organizan el código y evitan conflictos de nombres entre librerías.

```kotlin
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
```
Punto de entrada del programa. `EngineMain.main(args)` lee `application.yaml`, crea el servidor Netty y carga los módulos. El hilo principal queda bloqueado aquí hasta que el servidor se detenga (Ctrl+C o señal del SO).

---

### `application.yaml`

```yaml
ktor:
  deployment:
    port: 8081
```
Ktor escuchará conexiones HTTP en el puerto 8081.

```yaml
  application:
    modules:
      - com.example.SerializationKt.configureSerialization
      - com.example.BDDKt.configureDatabase
      - com.example.RoutingKt.configureRouting
```
Los módulos se cargan **en este orden exacto**. El orden importa porque:
- La serialización debe estar activa antes que el routing (para poder responder JSON)
- La DB debe estar configurada antes del routing (para que los handlers puedan usarla)

```yaml
db:
  url: ${DB_URL}
  user: ${DB_USER}
  password: ${DB_PASSWORD}
```
Los `${NOMBRE}` son variables de entorno. Ktor las resuelve al arrancar. Si alguna no está definida, el servidor falla con un error descriptivo. Esto obliga a pasarlas siempre en el `docker run`.

---

### `Serialization.kt`

```kotlin
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}
```

`install(ContentNegotiation)` registra el plugin de negociación de contenido en Ktor. Cuando un handler llama a `call.respond(objeto)`, este plugin revisa el header `Accept` de la request y serializa el objeto al formato pedido. Con `json()` se usa `kotlinx.serialization` para convertir data classes a JSON y viceversa.

Sin este plugin, `call.respond(HealthResponse(...))` fallaría porque Ktor no sabría cómo convertir el objeto a texto.

---

### `Usuario.kt`

```kotlin
@Serializable
data class Usuario(
    val id: Int,
    val nombre: String,
    val creado: String
)
```

`@Serializable` le dice al compilador de Kotlin que genere el código de serialización en tiempo de compilación. Esto es diferente a cómo lo hace Java con reflection (que inspecciona la clase en runtime): aquí el compilador genera el serializador como código Kotlin normal, lo que es más rápido y detecta errores en compilación.

`creado` es `String` y no `LocalDate` porque MySQL devuelve el tipo `DATE` como un objeto Java `java.sql.Date`, y convertirlo directamente a `String` con `.toString()` da el formato `yyyy-MM-dd` que es el estándar ISO 8601 — directamente serializable a JSON sin configuración adicional.

```kotlin
@Serializable
data class ItemRequest(val nombre: String)
```

Modelo separado para el cuerpo del `POST /items`. Solo tiene `nombre` porque `id` lo genera MySQL automáticamente (AUTO_INCREMENT) y `creado` lo genera la DB con `CURDATE()`. El cliente no necesita ni puede establecer esos valores.

---

### `BDD.kt`

```kotlin
lateinit var dataSource: HikariDataSource
```

Variable global mutable. `lateinit` le dice al compilador "esta variable se asignará antes del primer uso, pero no en la declaración". Sin `lateinit`, Kotlin exigiría inicializarla con un valor en la declaración, o declararla como nullable (`HikariDataSource?`), lo que requeriría verificación de null en cada uso.

```kotlin
fun Application.configureDatabase() {
    val config = HikariConfig().apply {
        jdbcUrl         = environment.config.property("db.url").getString()
```

`environment.config` es el objeto que representa el `application.yaml` ya cargado. `.property("db.url")` busca la clave `db.url` en el YAML — que fue resuelta desde la variable de entorno `DB_URL`. `.getString()` obtiene el valor como String.

```kotlin
        driverClassName = "com.mysql.cj.jdbc.Driver"
```

Le dice a HikariCP qué driver JDBC usar. Si en el futuro se cambia a PostgreSQL, esta línea cambia a `"org.postgresql.Driver"` y la URL cambia de `jdbc:mysql://` a `jdbc:postgresql://`. El resto del código JDBC queda igual.

```kotlin
        maximumPoolSize = 10
        isAutoCommit    = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
```

`TRANSACTION_REPEATABLE_READ` es el nivel de aislamiento de transacciones: garantiza que si leés la misma fila dos veces dentro de una transacción, obtenés el mismo valor. Previene el fenómeno de "non-repeatable reads". `validate()` verifica que todos los parámetros obligatorios estén completos antes de crear el pool.

```kotlin
fun checkDatabaseConnection(): String {
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT NOW()")
            rs.next()
            return rs.getString(1)
        }
    }
}
```

Pide una conexión al pool → crea un statement → ejecuta `SELECT NOW()` (devuelve la fecha y hora del servidor MySQL) → avanza al primer resultado → lee la primera columna como String → los `.use { }` cierran ambos recursos al salir → la conexión vuelve al pool. Si cualquier paso falla, la excepción se propaga al handler de routing que la captura.

```kotlin
fun insertUsuario(nombre: String) {
    dataSource.connection.use { conn ->
        conn.autoCommit = true
        conn.prepareStatement(
            "INSERT INTO usuarios (nombre, creado) VALUES (?, CURDATE())"
        ).use { stmt ->
            stmt.setString(1, nombre)
            stmt.executeUpdate()
        }
    }
}
```

`conn.autoCommit = true` sobreescribe el `isAutoCommit = false` del pool para esta conexión específica. Es necesario porque este INSERT debe confirmarse solo, sin esperar un `commit()` explícito. `stmt.setString(1, nombre)` sustituye el primer `?` con el valor de `nombre` de forma segura. `executeUpdate()` ejecuta el INSERT y devuelve el número de filas afectadas (ignorado aquí).

```kotlin
fun getAllUsuarios(): List<Usuario> {
    dataSource.connection.use { conn ->
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT id, nombre, creado FROM usuarios")
            val list = mutableListOf<Usuario>()
            while (rs.next()) {
                list.add(Usuario(
                    id     = rs.getInt("id"),
                    nombre = rs.getString("nombre"),
                    creado = rs.getDate("creado").toString()
                ))
            }
            return list
        }
    }
}
```

Itera todas las filas del ResultSet. `mutableListOf<Usuario>()` crea una lista mutable (se puede agregar elementos) de tipo `Usuario`. `rs.getDate("creado")` devuelve un `java.sql.Date`. `.toString()` lo convierte a `"2026-05-25"`. La lista resultante (inmutable al retornar) es serializada a un array JSON por Ktor.

---

### `Routing.kt`

```kotlin
@Serializable
data class HealthResponse(val status: String, val message: String)

@Serializable
data class DbStatusResponse(val status: String, val connected: Boolean, val detail: String? = null)
```

`String? = null` — el `?` declara que `detail` puede ser null. `= null` es el valor por defecto cuando no se pasa. Esto permite llamar a `DbStatusResponse(status = "UP", connected = true, detail = result)` o `DbStatusResponse(status = "DOWN", connected = false)` sin necesidad de pasar `detail` en el segundo caso.

```kotlin
get("/health") {
    call.respond(
        HttpStatusCode.OK,
        HealthResponse(status = "UP", message = "API activa")
    )
}
```

`HttpStatusCode.OK` es el código HTTP 200. `call.respond(statusCode, body)` establece el código de respuesta y serializa `body` a JSON. Este endpoint no toca la DB, por eso siempre responde aunque MySQL esté caído.

```kotlin
get("/db-status") {
    try {
        val result = checkDatabaseConnection()
        call.respond(HttpStatusCode.OK,
            DbStatusResponse(status = "UP", connected = true, detail = result))
    } catch (e: Exception) {
        call.respond(HttpStatusCode.ServiceUnavailable,
            DbStatusResponse(status = "DOWN", connected = false, detail = e.message))
    }
}
```

`HttpStatusCode.ServiceUnavailable` = HTTP 503. El `catch (e: Exception)` atrapa cualquier excepción que lance `checkDatabaseConnection()` — puede ser timeout de conexión, credenciales incorrectas, MySQL caído, etc. `e.message` contiene el mensaje de error del driver, útil para debuggear.

```kotlin
post("/items") {
    val req = call.receive<ItemRequest>()
    insertUsuario(req.nombre)
    call.respond(HttpStatusCode.Created)
}
```

`call.receive<ItemRequest>()` deserializa el body JSON de la request al tipo `ItemRequest`. Si el JSON no tiene el campo `nombre` o tiene un tipo incorrecto, Ktor responde automáticamente HTTP 400 Bad Request. `HttpStatusCode.Created` = HTTP 201, el código semánticamente correcto para indicar que se creó un recurso.

```kotlin
get("/items") {
    call.respond(getAllUsuarios())
}
```

`getAllUsuarios()` devuelve `List<Usuario>`. Ktor serializa una lista de objetos `@Serializable` como un array JSON: `[{"id":1,"nombre":"Admin","creado":"2026-05-25"}, ...]`.

---

## 8. Docker: qué es y por qué lo usamos

### El problema que resuelve Docker

Sin Docker, para correr una aplicación en otro servidor hay que:
1. Instalar la versión correcta de Java
2. Configurar las variables de entorno
3. Copiar el JAR
4. Iniciar el proceso correctamente

Si el servidor tiene una versión diferente de Java, o una configuración diferente, la aplicación puede fallar. El famoso problema: "en mi máquina funciona".

Docker resuelve esto con **contenedores**: paquetes que incluyen la aplicación y todo su entorno (sistema operativo base, dependencias, configuración). La misma imagen corre igual en cualquier host que tenga Docker instalado.

### Imágenes vs Contenedores

- **Imagen**: una plantilla de solo lectura. Define el sistema de archivos, las dependencias y el comando de arranque. Es como una clase en programación orientada a objetos.
- **Contenedor**: una instancia en ejecución de una imagen. Tiene su propio sistema de archivos, red y procesos. Es como un objeto instanciado de una clase.

### `DockerfileUbuntu` línea por línea

```dockerfile
FROM ubuntu:latest
```
La imagen base. Docker descarga Ubuntu desde Docker Hub y lo usa como punto de partida. Todos los comandos siguientes se ejecutan sobre esta base.

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
```
`RUN` ejecuta un comando durante la construcción de la imagen. Instala el JDK 21 (Java Development Kit sin interfaz gráfica) y los certificados SSL. `--no-install-recommends` evita instalar paquetes opcionales. `rm -rf /var/lib/apt/lists/*` borra el caché de apt después de instalar para reducir el tamaño de la imagen.

```dockerfile
WORKDIR /app
```
Establece el directorio de trabajo para los comandos siguientes. Si el directorio no existe, lo crea. Equivale a `mkdir /app && cd /app`.

```dockerfile
COPY ktor-poli/ .
```
Copia todo el contenido de la carpeta `ktor-poli/` del host al directorio de trabajo `/app` en la imagen. Esto incluye el código fuente, el `gradlew` y los archivos de configuración de Gradle.

```dockerfile
RUN chmod +x gradlew && ./gradlew buildFatJar --no-daemon && rm -rf /root/.gradle
```
Tres operaciones encadenadas:
1. `chmod +x gradlew`: le da permisos de ejecución al script de Gradle (en Linux los archivos no son ejecutables por defecto)
2. `./gradlew buildFatJar --no-daemon`: compila todo el proyecto Kotlin y genera un **fat JAR** — un archivo `.jar` que contiene la aplicación y todas sus dependencias (Ktor, HikariCP, driver MySQL, etc.) en un solo archivo. `--no-daemon` evita que Gradle deje procesos en background dentro del contenedor.
3. `rm -rf /root/.gradle`: elimina el caché de Gradle (las librerías descargadas durante la compilación). Ya no se necesitan en la imagen final porque todo está empaquetado en el fat JAR. Esto reduce el tamaño de la imagen significativamente (puede ser ~500MB de diferencia).

```dockerfile
EXPOSE 8081
```
**Documentación** — le dice a Docker que el proceso dentro del contenedor usa el puerto 8081. No abre el puerto por sí solo; eso lo hace el `-p 8081:8081` en el `docker run`.

```dockerfile
ENTRYPOINT ["java", "-jar", "/app/build/libs/ktor-poli-all.jar"]
```
El comando que se ejecuta cuando el contenedor arranca. Usa la forma JSON (exec form) en lugar de string para evitar que sea procesado por `/bin/sh`, lo que garantiza que `java` reciba directamente las señales del SO (importante para Ctrl+C y shutdown graceful).

### `DockerfileSQL` línea por línea

```dockerfile
FROM mysql:latest
```
Imagen oficial de MySQL de Docker Hub. Ya tiene MySQL instalado y configurado.

```dockerfile
ENV MYSQL_ROOT_PASSWORD=my-secret-pw
ENV MYSQL_DATABASE=my_database
ENV MYSQL_USER=user_name
ENV MYSQL_PASSWORD=user_password
```
Variables de entorno que la imagen oficial de MySQL lee al primer arranque para inicializar la base de datos. Crea la DB `my_database` y el usuario `user_name` con esa contraseña automáticamente.

```dockerfile
EXPOSE 3306
```
Puerto estándar de MySQL. Solo accesible dentro de la red Docker `mi-red`, no expuesto al host.

### Red Docker (`mi-red`)

```bash
docker network create mi-red
```

Crea una red virtual de tipo **bridge** (puente). Los contenedores conectados a esta red pueden comunicarse entre sí usando sus nombres como hostnames.

Cuando se levanta `contenedor-ktor` con `--network mi-red`, Docker registra el nombre `contenedor-ktor` en su DNS interno. Lo mismo con `contenedor-sql`. Por eso en `application.yaml` la URL de conexión usa `contenedor-sql` como hostname — Docker resuelve ese nombre a la IP del contenedor MySQL.

---

## 9. Beneficios y desventajas del stack

### Beneficios

| Beneficio | Explicación |
|---|---|
| **Null safety en compilación** | El compilador garantiza que las variables no-nullable nunca sean null. Los `NullPointerException` en producción son prácticamente imposibles si se usa el tipo system correctamente |
| **Concisión** | `data class` con 3 campos = 1 línea. Getters, setters, equals, hashCode, toString se generan automáticamente. Menos código = menos bugs |
| **Coroutines nativas** | Ktor maneja miles de requests concurrentes con pocos threads. Sin bloqueo, sin callback hell, con código que parece secuencial |
| **Sin reflexión en runtime** | `@Serializable` genera el serializador en compilación. No hay inspección de clases en runtime, lo que es más rápido y los errores aparecen al compilar, no al ejecutar |
| **Interoperabilidad con Java** | Toda la inmensa biblioteca de librerías Java está disponible sin ningún adaptador |
| **Fat JAR simple** | Una imagen Docker solo necesita un JRE y el JAR. Sin servidores de aplicaciones (Tomcat, Wildfly), sin configuraciones adicionales |
| **Seguridad SQL por defecto** | El uso de `PreparedStatement` previene inyección SQL |
| **Credenciales por variables de entorno** | Las credenciales nunca están en el código fuente ni en la imagen Docker |

### Desventajas

| Desventaja | Explicación |
|---|---|
| **Curva de aprendizaje** | Las coroutines, los generics, el DSL de Ktor y el sistema de tipos son conceptos que requieren tiempo para dominar. No es un lenguaje de scripting simple |
| **Ktor es minimalista** | No trae ORM, validación, autenticación ni migraciones de DB. Hay que elegir e integrar cada librería por separado (Exposed para ORM, Flyway para migraciones, etc.) |
| **Comunidad más pequeña** | Comparado con Spring Boot (Java) o Express (Node.js), hay menos ejemplos, menos Stack Overflow, menos librerías específicas para Ktor |
| **Tiempo de arranque de la JVM** | La JVM tarda ~200-500ms en arrancar. Para funciones serverless que se instancian por request, esto es un problema. Go o Node arrancan en milisegundos |
| **JDBC crudo es verboso** | Sin un ORM (como Exposed, la librería de JetBrains), el código JDBC con ResultSet y mapeo manual es más verboso que equivalentes en Django (Python) o Rails (Ruby) |
| **Tamaño de la imagen** | Una imagen con JDK y el código compilado es más grande que una imagen equivalente en Go o Node (~200MB vs ~50MB) |

---

## 10. Cómo correr el proyecto

Ejecutar en orden desde la raíz del proyecto:

```bash
# 1. Crear la red interna (solo la primera vez)
docker network create mi-red

# 2. Construir la imagen MySQL
cd SQL
docker build -t mi-mysql -f DockerfileSQL .

# 3. Levantar el contenedor MySQL
docker run -d --name contenedor-sql --network mi-red mi-mysql

# 4. Esperar ~15 segundos a que MySQL inicialice completamente
# (se puede verificar con: docker logs contenedor-sql)

# 5. Volver a la raíz y construir la imagen Ktor
cd ..
docker build -t mi-ktor -f DockerfileUbuntu .

# 6. Levantar el contenedor Ktor con las credenciales como variables de entorno
docker run -d --name contenedor-ktor --network mi-red -p 8081:8081 \
  -e DB_URL="jdbc:mysql://contenedor-sql:3306/my_database?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" \
  -e DB_USER="user_name" \
  -e DB_PASSWORD="user_password" \
  mi-ktor
```

### Verificar que funciona

```bash
# Verificar que el servidor está vivo (no necesita DB)
curl http://localhost:8081/health
# Respuesta esperada: {"status":"UP","message":"API activa"}

# Verificar conexión a la base de datos
curl http://localhost:8081/db-status
# Respuesta esperada: {"status":"UP","connected":true,"detail":"2026-05-25 12:00:00"}

# Crear un item (persiste en MySQL)
curl -X POST http://localhost:8081/items \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Laptop"}'
# Respuesta esperada: HTTP 201 (sin body)

# Leer todos los items (incluye el Admin del init.sql y el Laptop recién creado)
curl http://localhost:8081/items
# Respuesta esperada: [{"id":1,"nombre":"Admin","creado":"2026-05-25"},{"id":2,"nombre":"Laptop","creado":"2026-05-25"}]
```

### Detener y limpiar

```bash
docker stop contenedor-ktor contenedor-sql
docker rm contenedor-ktor contenedor-sql
docker rmi mi-ktor mi-mysql
docker network rm mi-red
```

### Por qué las credenciales van en el `docker run` y no en el código

```
Flujo de las credenciales:

docker run -e DB_PASSWORD="user_password"
     │
     ▼
Variable de entorno del proceso Java dentro del contenedor
     │
     ▼
Ktor lee application.yaml → resuelve ${DB_PASSWORD}
     │
     ▼
HikariConfig.password = "user_password"
     │
     ▼
HikariCP abre la conexión a MySQL con esa contraseña
```

En ningún momento la contraseña está escrita en el código fuente, en el Dockerfile, ni en la imagen Docker. Solo existe en el momento del `docker run`. En un entorno de producción real, estas variables las gestiona el equipo de infraestructura (o un sistema como Kubernetes Secrets / AWS Secrets Manager), completamente separado del código del desarrollador.
