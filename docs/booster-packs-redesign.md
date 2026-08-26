# Rediseño de Ruletas a Booster Packs

Fecha: 2026-08-26

Estado: v1 implementada el 2026-08-26. Este archivo conserva el diseño y sirve como guía de
mantenimiento/evolución.

## Implementación realizada

- dominio Django nuevo `booster_packs` con packs, pools, entradas, slots, inventario, ledger,
  aperturas y snapshots de resultados;
- API autenticada de lista, detalle/probabilidades, apertura idempotente e historial;
- apertura atómica con bloqueo de perfil/stock, resultados múltiples y notificación `on_commit`;
- configuración relacionada que desactiva y versiona automáticamente el pack al cambiar;
- Admin para configurar, validar/activar, conceder stock y auditar aperturas/movimientos;
- buzón renombrado a `SOBRE`, con canje atómico e idempotente para dinero y Wildcards;
- rechazo preventivo de objetos del juego/Pokémon hasta disponer de una entrega segura;
- pantalla JavaFX funcional con catálogo, stock, probabilidades, historial y revelado animado;
- ruta, modelos, serializers, Admin, FXML, CSS y archivos de configuración legacy eliminados tras
  comprobar que la base local no contenía ruletas ni historial.

Permanecen como decisiones de contenido —no de arquitectura— el arte, sonido, número definitivo de
slots, pesos y catálogo inicial. No se inventaron valores de balance ni se creó un pack de muestra.

Repositorios involucrados:

- cliente JavaFX: `C:\Users\xshaf\IdeaProjects\PokeMada`;
- servidor Django: `C:\Users\xshaf\PycharmProjects\server`.

## Resumen de la decisión

La sección deja de representar una ruleta o portal de invocación. Pasa a ser un sistema de sobres:

- texto visible en español: **SOBRES**;
- nombre de producto/documentación: **Booster Packs**;
- nombres técnicos: `booster_packs`, `BoosterPack`, `PackOpening`, etc.;
- acción del usuario: `ABRIR`, nunca `GIRAR`, `TIRAR` ni `INVOCAR`;
- el usuario posee una cantidad entera de sobres sin abrir;
- cada apertura revela varias recompensas, no un único premio disfrazado de sobre;
- al menos una posición del sobre puede usar un pool garantizado distinto;
- el resultado se decide y persiste en el servidor de forma atómica e idempotente;
- la animación del cliente solo revela un resultado ya decidido. Nunca sortea ni autoriza premios.

En la primera versión se recomienda **no incluir pity**, compras, apertura múltiple, intercambio,
duplicados convertidos automáticamente ni sobres con caducidad. Esas funciones pueden añadirse sin
deformar el modelo base.

## Por qué no basta con renombrar

El servidor actual ya contiene una ruleta funcional aunque el cliente solo muestre un placeholder:

- `rewards_api.models.Roulette` consume un Wildcard como pase;
- `Roulette.spin()` selecciona un único `RoulettePrice` ponderado;
- cada `RoulettePrice` solo contiene Wildcards;
- `POST /api/roulette/{id}/roll/` consume primero y después crea historial, bundle y buzón;
- el flujo no está cubierto por una sola transacción ni es idempotente;
- el cliente aún no consume esta API y conserva un FXML totalmente estático;
- el buzón muestra el tipo numérico `1` como `RULETA`.

Un Booster Pack debe ser un dominio de contenido y aperturas. Una ruleta elige una casilla; un sobre
contiene varias posiciones, revela varios resultados y conserva una apertura concreta que puede
recuperarse si se pierde la respuesta HTTP.

## Experiencia propuesta

### Pantalla principal

La sección **SOBRES** muestra:

1. una lista lateral de diseños disponibles para el tramo actual;
2. arte, nombre, descripción y cantidad poseída del sobre seleccionado;
3. una vista previa de contenido: número de recompensas y garantía mínima;
4. botón `ABRIR SOBRE` habilitado solo si hay sesión, stock y configuración activa;
5. accesos a `CONTENIDO Y PROBABILIDADES` e `HISTORIAL`;
6. estados claros de carga, vacío y error.

No mostrar una billetera de “pases de invocación”. El dato importante es `N sobres disponibles` para
el diseño seleccionado. Si luego existe una tienda, comprar y abrir siguen siendo operaciones
separadas.

### Apertura

Al pulsar `ABRIR SOBRE`:

1. el cliente genera un UUID de idempotencia y llama al servidor una sola vez;
2. el servidor consume el sobre y devuelve la apertura persistida completa;
3. el cliente anima el rasgado/apertura del paquete;
4. revela las recompensas, una por una, en el orden de sus posiciones;
5. permite `REVELAR TODO` u omitir animaciones;
6. termina con un resumen y el mensaje `Recompensas enviadas al buzón` mientras se mantenga el
   sistema actual de canje.

Cerrar la pantalla, perder red o reiniciar el cliente no vuelve a sortear. Repetir la petición con el
mismo `Idempotency-Key` devuelve la misma apertura.

### Lenguaje visual

Conservar la identidad nocturna Master V/Alola, pero abandonar círculos, órbitas y simbología de
ruleta/portal. La pieza central debe ser el arte físico de un paquete sellado. Las rarezas pueden
mantener colores consistentes:

- común: gris;
- rara: azul;
- épica: violeta;
- maestra: blanco/dorado o blanco/violeta.

La rareza describe la entrada revelada y no sustituye a su probabilidad exacta. La pantalla de
probabilidades debe indicar el porcentaje por posición/pool cuando estos sean distintos.

## Modelo servidor recomendado

Crear una app Django nueva `booster_packs`. No seguir añadiendo responsabilidades de gacha a
`rewards_api`; esa app puede continuar siendo el catálogo y buzón de recompensas.

### `BoosterPack`

- `id`: UUID interno;
- `code`: identificador estable, único e inmutable, apto para URL;
- `name`, `description`;
- `pack_art`, `banner_image`, `small_logo` según necesidad real de UI;
- `is_active`;
- `segment`: tramo en que aparece;
- `display_order`;
- `configuration_version`: aumenta cuando cambian slots, pools o pesos;
- timestamps.

No guardar un archivo JSON que, al guardar el modelo, borre y recree configuración. El Admin debe
editar relaciones tipadas y validables.

### `BoosterPackPool`

Representa un conjunto ponderado dentro de un sobre.

- `pack`;
- `code` y `name`, únicos dentro del pack, por ejemplo `STANDARD` o `RARE_GUARANTEED`;
- `is_active`.

### `BoosterPackPoolEntry`

- `pool`;
- `reward_bundle`: FK a un `RewardBundle` de plantilla, no creado por usuario;
- `rarity`: choice cerrado (`COMMON`, `RARE`, `EPIC`, `MASTER`);
- `weight`: entero estrictamente mayor que cero;
- `display_order` solo para Admin/previsualización;
- `is_active`.

Usar `RewardBundle` permite que una entrada entregue dinero, Wildcards, objetos virtuales, objetos
del juego o Pokémon a medida que el buzón soporte esos tipos. No limitar los sobres a Wildcards como
hace `RoulettePriceWildcard`.

### `BoosterPackSlot`

Describe cada revelado del sobre.

- `pack`;
- `position`: entero positivo y único dentro del pack;
- `pool`: pool del que se selecciona una entrada;
- `label` opcional para Admin, por ejemplo `Garantía rara`.

Ejemplo de un sobre de cinco revelados: posiciones 1–4 apuntan a `STANDARD` y posición 5 a
`RARE_GUARANTEED`. En v1 todos los sorteos son independientes y con reemplazo. No agregar reglas
genéricas de garantía, exclusión o reroll; se expresan con pools explícitos.

### `OwnedBoosterPack`

- `profile`;
- `pack`;
- `quantity >= 0`;
- `reserved_quantity >= 0` si se prevé coordinar operaciones largas; para la apertura puramente de
  servidor puede omitirse y consumirse dentro de la misma transacción;
- restricción única `(profile, pack)`;
- checks de DB para cantidades válidas;
- `updated_at`.

No reutilizar el inventario de Wildcards como pases. Los sobres deben poder concederse desde Admin,
eventos o recompensas sin fingir que son comodines.

### `BoosterPackLedgerEntry`

Ledger append-only para explicar cada cambio de cantidad:

- perfil, pack, delta, saldo resultante;
- motivo cerrado: `GRANT`, `OPEN`, `STAFF_ADJUSTMENT`, `REFUND`;
- referencia opcional a apertura/recompensa/evento;
- actor staff opcional y timestamp.

### `PackOpening`

- UUID público;
- `profile`;
- FK nullable al pack más snapshot de `pack_code`, `pack_name` y `configuration_version`;
- `idempotency_key`, único por perfil;
- estado cerrado: `OPENING`, `COMPLETED`, `FAILED` (normalmente se crea ya completado dentro de la
  transacción);
- FK al `RewardBundle` concreto creado como resultado;
- timestamps.

### `PackOpeningResult`

- `opening`;
- `position`, único dentro de la apertura;
- FK nullable a la entrada seleccionada;
- snapshots de nombre, rareza, peso y total de peso;
- FK o referencia al bundle plantilla origen;
- datos mínimos de presentación necesarios para reconstruir el historial.

Guardar snapshots evita que editar un pack antiguo cambie lo que el historial dice que ocurrió. No
guardar únicamente un mensaje humano.

## Algoritmo de apertura

Implementar un servicio `BoosterPackOpeningService.open(...)`; no colocar esta lógica en `save()` ni
en el ViewSet.

Dentro de `transaction.atomic()`:

1. buscar una apertura existente por `(profile, idempotency_key)` y devolverla si existe;
2. obtener con `select_for_update()` el `OwnedBoosterPack`;
3. validar perfil autorizado, pack activo, tramo, cantidad y configuración completa;
4. cargar slots, pools y entradas activas en orden;
5. seleccionar una entrada por slot usando pesos enteros y `secrets.randbelow(total_weight)`;
6. decrementar exactamente un sobre con una actualización protegida contra saldo negativo;
7. crear `PackOpening`, todos sus resultados y el asiento del ledger;
8. copiar/agrupar las recompensas de los bundles plantilla en un nuevo `RewardBundle` de usuario;
9. crear exactamente un `StreamerRewardInventory` para el perfil;
10. marcar la apertura completada;
11. emitir la notificación WebSocket mediante `transaction.on_commit()`.

Si cualquier paso falla, no se consume el sobre ni queda un buzón parcial. Nunca recalcular una
apertura ya creada. No aceptar pesos, cantidades, pool ni premios desde el cliente.

Antes de activar un pack, validar:

- al menos un slot activo;
- posiciones continuas desde 1;
- cada slot apunta a un pool activo con al menos una entrada activa;
- todos los pesos son positivos;
- los bundles plantilla existen, están activos y tienen al menos una recompensa activa;
- no se generan tipos de recompensa que el flujo de buzón vaya a descartar silenciosamente.

## API propuesta

Base: `/api/booster-packs/`.

### `GET /api/booster-packs/`

Lista packs visibles para el perfil/tramo. Cada elemento incluye como mínimo:

```json
{
  "code": "MASTER_V_ALOLA",
  "name": "Alola Master Pack",
  "description": "...",
  "art_url": "...",
  "quantity": 2,
  "cards_per_pack": 5,
  "guarantee_label": "1 rara o superior",
  "configuration_version": 3
}
```

### `GET /api/booster-packs/{code}/`

Devuelve detalles y probabilidades por slot/pool. El porcentaje se calcula en servidor como
`entry.weight / sum(active weights del pool)` y se expone con precisión suficiente; el cliente solo
lo presenta.

### `POST /api/booster-packs/{code}/open/`

Requiere `Idempotency-Key: <UUID>`. No necesita body en v1. Devuelve la apertura con los resultados
ordenados, el saldo restante y el ID del bundle enviado al buzón.

Respuesta conceptual:

```json
{
  "id": "uuid",
  "pack_code": "MASTER_V_ALOLA",
  "state": "COMPLETED",
  "remaining_quantity": 1,
  "reward_bundle_id": "uuid",
  "results": [
    {"position": 1, "name": "500 monedas", "rarity": "COMMON", "image_url": null},
    {"position": 5, "name": "Revive", "rarity": "RARE", "image_url": "..."}
  ]
}
```

### `GET /api/booster-packs/openings/`

Historial paginado del perfil. No incrustar todo el historial en cada pack de la lista como hace hoy
`RouletteSimpleSerializer`.

Errores deben usar objetos estables (`code`, `detail`) para `NO_PACKS`, `PACK_INACTIVE`,
`INVALID_CONFIGURATION`, etc., no strings libres como contrato.

## Django Admin

Crear una pantalla de Booster Pack con pools, entradas y slots editables. Si los inlines anidados
resultan difíciles de validar, usar pantallas separadas y enlaces; la integridad importa más que una
única página enorme.

El Admin debe ofrecer:

- previsualización de probabilidades por pool;
- resumen “4 estándar + 1 rara garantizada”;
- acción explícita `Validar y activar`;
- catálogo inactivo mientras su configuración sea inválida;
- concesión de sobres a perfiles mediante un servicio que escriba ledger;
- historial de aperturas y ledger de solo lectura;
- `code`, snapshots, saldos resultantes e idempotency keys de solo lectura.

## Cambios en el cliente JavaFX

### Renombres estructurales

Renombrar, no mantener alias visuales de ruleta:

- `roulettes-view.fxml` -> `booster-packs-view.fxml`;
- `roulettesView` -> `boosterPacksView`;
- `showRoulettes` -> `showBoosterPacks`;
- `.roulette-page` -> `.booster-packs-page`;
- clases CSS de portal/orbit/summon por nombres de pack/reveal;
- texto de navegación `RULETAS` -> `SOBRES` y el icono por uno de paquete;
- sección README `Ruletas` -> `Booster Packs / Sobres`;
- `MailboxController.bundleType(1)` -> `SOBRE`.

No enrutar secciones comparando el texto visible del botón. Como parte de este cambio, asignar a los
botones un `userData` estable (`home`, `live`, `booster-packs`, etc.) o usar sus `fx:id`; así cambiar
`SOBRES` por otra traducción no rompe la navegación.

### Nuevo `BoosterPacksController`

El FXML debe tener controlador, a diferencia del placeholder actual. Responsabilidades:

- recibir sesión/token desde la coordinación existente de `MainController`/`MailboxController`;
- cargar lista y detalle sin bloquear el hilo JavaFX;
- mantener estados `LOADING`, `READY`, `EMPTY`, `OPENING`, `REVEALING`, `ERROR`;
- deshabilitar una segunda apertura mientras existe una petición activa;
- conservar el UUID de idempotencia hasta obtener respuesta definitiva;
- animar únicamente los resultados recibidos;
- actualizar cantidad local con `remaining_quantity` y refrescar el buzón;
- permitir revelar todo y consultar historial/probabilidades;
- ignorar callbacks tardíos si el controlador ya cambió de selección o sesión.

Agregar a `ServerClient` records y métodos explícitos: `BoosterPackSummary`, `BoosterPackDetail`,
`PackOddsEntry`, `PackOpening`, `PackOpeningResult`, `boosterPacks(...)`, `boosterPack(...)`,
`openBoosterPack(...)` y `packOpenings(...)`.

No reutilizar los records de `RewardBundle` como respuesta de apertura: son contratos distintos.

### Animación y accesibilidad

La primera entrega puede usar transiciones JavaFX simples sobre nodos existentes; no necesita video.
Respetar una opción de omitir animación y no depender solo del color para indicar rareza. Durante la
animación, mostrar texto accesible y conservar un botón funcional por teclado.

## Relación con el buzón y la mochila virtual

En v1 la apertura crea un bundle reclamable porque ese es el límite transaccional ya presente. Aun
así, antes de habilitar sobres hay que corregir el canje del buzón:

- hacerlo atómico e idempotente;
- bloquear `StreamerRewardInventory` al reclamar;
- no marcar usado un bundle si algún tipo no se entregó;
- integrar el tipo `Reward.VIRTUAL_ITEM` si los packs concederán objetos de la mochila virtual;
- mantener objetos/Pokémon deshabilitados hasta que exista entrega segura al cliente.

Cambiar la etiqueta del tipo de bundle numérico `1` de `Ruleta` a `Sobre` puede hacerse con una
migración de choices sin cambiar el valor almacenado. Las nuevas aperturas deben establecer
explícitamente `type=BOOSTER_PACK_BUNDLE`; el endpoint actual ni siquiera fija el tipo al crear el
bundle y por eso cae en `Premio`.

A futuro se puede reemplazar el buzón por concesión inmediata mediante un `RewardGrantService`, pero
no mezclar esa reescritura con el primer corte de Booster Packs salvo que dicho servicio ya exista y
esté probado.

## Migración del sistema viejo

Antes de escribir migraciones, comprobar en la base objetivo cuántas filas existen en `Roulette`,
`RoulettePrice`, `RouletteRollHistory` y qué Wildcards se usan como pases.

Camino recomendado si no hay datos que conservar:

1. crear y probar `booster_packs` como dominio nuevo;
2. cambiar el cliente al endpoint nuevo;
3. retirar el registro `/api/roulette`;
4. eliminar Admin y modelos Roulette en una migración posterior;
5. limpiar media legacy (`ruletas/`, `roulette/images/`) solo mediante una tarea explícita y después
   de comprobar referencias.

Si sí hay configuración o historial valioso:

- no convertir automáticamente cada ruleta en un sobre definitivo;
- ofrecer una migración temporal: cada `Roulette` se vuelve un pack de **un solo slot**, cada
  `RoulettePrice` una entrada y sus Wildcards un `RewardBundle` plantilla;
- marcar esos packs migrados como inactivos para que staff añada slots/garantías y los valide;
- convertir `RouletteRollHistory` a aperturas legacy solo si hace falta mostrarlo, conservando el
  mensaje original como metadata;
- no convertir cantidades de Wildcards-pase a sobres sin una decisión explícita de producto.

No sostener ambas APIs indefinidamente. El endpoint legacy puede devolver deprecación durante una
release y eliminarse en la siguiente si existe algún consumidor externo confirmado.

## Orden de implementación para el próximo agente

### Fase 0 — Verificación

- Leer este documento completo y `docs/virtual-inventory-design.md` en el cliente.
- Revisar el estado de ambos worktrees y preservar cambios ajenos.
- Buscar nuevamente `roulette|ruleta|roll|spin|gacha`, porque el código puede haber cambiado.
- Inspeccionar datos reales antes de elegir migración vacía o de compatibilidad.
- Confirmar cómo se autentica/inyecta la sesión en controladores JavaFX.

### Fase 1 — Núcleo servidor

- Crear app, modelos, constraints y migraciones.
- Crear servicio transaccional de concesión de sobres y ledger.
- Crear compilador/validador simple de configuración.
- Crear servicio de apertura atómico e idempotente.
- Escribir pruebas unitarias y de concurrencia antes del ViewSet.

### Fase 2 — API y Admin

- Implementar serializers separados para lista, detalle, odds, apertura e historial.
- Implementar endpoints y permisos por perfil/tramo.
- Registrar rutas y Admin.
- Emitir notificación solo `on_commit`.
- Probar respuestas repetidas con el mismo UUID y rechazo con UUID reutilizado para otro pack.

### Fase 3 — Buzón seguro

- Corregir canje transaccional/idempotente.
- Renombrar bundle type y etiqueta del cliente.
- Asegurar que todas las recompensas admitidas por los packs se puedan reclamar realmente.

### Fase 4 — Cliente

- Hacer renombres de archivos/campos/CSS.
- Introducir `BoosterPacksController` y contratos en `ServerClient`.
- Implementar estados, carga, apertura, revelado, odds e historial.
- Reemplazar navegación basada en texto por IDs estables.
- Actualizar README.

### Fase 5 — Retiro legacy

- Eliminar ruta, imports, Admin y modelos de roulette cuando no haya consumidores.
- Ejecutar migraciones y suites completas de ambos repos.
- Buscar residuos de terminología, incluyendo media y documentación.

## Pruebas mínimas obligatorias

Servidor:

- no se abre sin stock, sin consumir nada;
- una apertura descuenta exactamente un sobre;
- dos aperturas concurrentes con stock 1 producen una sola apertura válida;
- repetir el mismo idempotency key devuelve exactamente resultados y saldo originales;
- el mismo key no se puede reutilizar para otro pack;
- un error al crear recompensas revierte consumo, ledger, historial y buzón;
- cada slot usa exclusivamente su pool y respeta pesos;
- un pool/slot inválido impide activar o abrir;
- probabilidades excluyen entradas inactivas y suman aproximadamente 100 % por pool;
- editar configuración no altera snapshots de aperturas anteriores;
- coach/perfiles no autorizados reciben el rechazo esperado;
- la notificación solo se envía después del commit.

Cliente:

- parseo de lista, detalle, odds, apertura y errores incompletos/malformados;
- botón deshabilitado con cantidad cero, carga o apertura activa;
- doble clic produce una única petición lógica/UUID;
- reintento conserva el UUID;
- resultados se revelan en orden y `REVELAR TODO` llega al mismo resumen;
- cierre/cambio de pack ignora callbacks obsoletos;
- navegación funciona aunque cambie el texto visible;
- buzón presenta `SOBRE` y refresca al completar.

## Criterios de aceptación de v1

La funcionalidad está lista cuando:

- no quedan referencias visibles a ruleta, giro, tirada, portal o invocación;
- un staff puede configurar y conceder un pack sin editar JSON ni código;
- un jugador ve sus sobres y probabilidades reales;
- abrir consume exactamente uno y revela varias recompensas persistidas;
- una posición garantizada puede configurarse mediante un pool propio;
- reintentos y concurrencia no duplican ni pierden premios;
- las recompensas llegan a un buzón reclamable de forma segura;
- el historial reconstruye aperturas aunque el pack cambie después;
- las suites de ambos repos pasan y la UI fue verificada visualmente.

## Decisiones pendientes (no bloquean el diseño base)

1. Nombre final visible de la sección: se recomienda `SOBRES`; `BOOSTER PACKS` puede quedar como
   subtítulo de marca.
2. Número de revelados del primer pack y pools exactos.
3. Quién y mediante qué eventos concede sobres.
4. Catálogo real de recompensas y tabla de rarezas/pesos.
5. Si el historial es privado o puede aparecer en un feed público.
6. Si una futura v2 tendrá apertura x5/x10, protección contra duplicados o pity.
7. Arte y sonido definitivos del paquete y cada rareza.

Hasta decidirlos, implementar valores configurables y no inventar balance. La arquitectura anterior
permite entregar v1 con un solo diseño de pack sin cerrar el camino a temporadas futuras.
