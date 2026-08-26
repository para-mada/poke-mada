# Diseño propuesto: mochila de objetos virtuales

Fecha: 2026-08-25

Estado: núcleo de modelos/compilación implementado el 2026-08-25. API y ejecución pendientes.

## Alcance y decisión principal

La mochila será un dominio nuevo y propio para objetos virtuales de evento. No dependerá de
`market`, `new_market`, `BankItem`, la mochila de RAM de Sun/Moon ni el inventario de comodines.
`market` y `new_market` se consideran legado a deprecar y quedan fuera de esta arquitectura.

El sistema se está montando de nuevo y no existe una base de datos productiva que migrar. Wildcards
se usaron anteriormente como inventario, pero en el nuevo sistema serán un dominio separado. La
compatibilidad necesaria es de código y comportamiento durante la transición, no de datos.

Los objetos serán configuración relacional construida con un conjunto cerrado de condiciones y
efectos implementados en código. La configuración nunca contendrá Python, expresiones evaluables ni
scripts. Tampoco se usará YAML/JSON libre como fuente de verdad editable por staff.

La regla operativa es:

> Las releases amplían el vocabulario. El staff combina y configura ese vocabulario.

## Mapa del sistema actual

### Wildcards

`Wildcard` concentra demasiadas responsabilidades:

- catálogo y presentación (`name`, `sprite`, `description`, `category`);
- venta y balance (`price`, `noob_price`, `segment_available`);
- disponibilidad (`is_active`, `is_wip`, `always_available`);
- selección de comportamiento (`handler_key`);
- validación, compra, ejecución y consumo mediante métodos del propio modelo.

`StreamerWildcardInventoryItem` guarda `profile + wildcard + quantity`, pero no tiene restricción
única para ese par. El código compensa agregando cantidades en algunos serializers, mientras otros
flujos usan `.first()` o `.get()`. Esto permite resultados ambiguos si existen filas duplicadas.

### Handlers

`WildCardExecutorRegistry` ya demuestra una idea reutilizable: una clave cerrada registrada en código
selecciona un ejecutor. Sin embargo, cada Wildcard solo tiene un `handler_key`, por lo que la
composición termina en herencia o handlers especializados.

Existen handlers de servidor, handlers que devuelven comandos al cliente y handlers mixtos. Algunos
ejemplos:

- servidor: dinero, robo, revivir registros, alertas y regalos de comodines;
- cliente/juego: `give_item` y `give_game_money` devuelven `{command, data}`;
- no-op: `dummy_handler` hereda la implementación que siempre devuelve `True`.

La configuración adicional usa modelos `OneToOne` específicos por handler y Django Admin selecciona
el inline según `handler_key`. Esto es útil como antecedente, pero no escala bien a múltiples efectos.

### Workaround histórico de objetos dummy

El código confirma que `dummy_handler` existe y no realiza ninguna acción. Esto explica el workaround
anterior, pero no define el nuevo modelo. Como no hay datos que conservar, no hace falta clasificar ni
migrar Wildcards dummy: los objetos se registrarán desde cero como `VirtualItem` y los Wildcards
seguirán siendo Wildcards.

### Recompensas y buzón

`RewardBundle` agrupa `Reward`; actualmente los tipos son objeto del juego, Wildcard, dinero y
Pokémon. `StreamerRewardInventory` representa un paquete pendiente por perfil.

El canje actual tiene riesgos importantes:

- no engloba validación, entrega y marcado como reclamado en una transacción;
- dos solicitudes concurrentes podrían entregar dos veces;
- los tipos objeto y Pokémon no se procesan en servidor, pero el paquete acaba marcado como usado;
- el cliente Java deshabilita esos paquetes con “REQUIERE JUEGO”.

La nueva mochila debe integrarse como un nuevo tipo explícito de recompensa, no disfrazado de
Wildcard.

### API, WebSocket y aplicación de escritorio

`use_card` valida, ejecuta el handler, descuenta inmediatamente y devuelve el resultado o comando en
la misma respuesta HTTP. No existe identificador idempotente ni confirmación de que un comando sobre
el juego fue aplicado.

`DataConsumer` publica en `ws/data/<username>` y hoy se usa para notificaciones. El cliente Java solo
convierte mensajes cuyo tipo contiene `notification` en texto visible; no tiene un executor genérico
de comandos de inventario. El canal actual tampoco autentica por sí mismo al dueño del nombre de
usuario, así que no debe transportar órdenes autorizadas ni datos sensibles.

## Invariantes obligatorias

1. Una pila es única por `(profile, virtual_item)`.
2. `quantity >= 0`, `reserved_quantity >= 0` y `reserved_quantity <= quantity` también se imponen en DB.
3. La disponibilidad para uso es `quantity - reserved_quantity`.
4. Toda concesión, consumo, reserva, liberación y ajuste deja un asiento auditable.
5. Cada intento de uso tiene una clave idempotente única por actor.
6. Repetir una petición devuelve la misma operación; nunca vuelve a descontar ni aplicar efectos.
7. La configuración efectiva se captura al crear la operación; un cambio de balance no altera usos en curso.
8. El servidor resuelve condiciones y payloads. El cliente nunca proporciona cantidades, stats o efectos.
9. Actor, objetivo y Pokémon se resuelven contra recursos que el actor puede usar o afectar.
10. Los cambios de inventario usan `transaction.atomic`, bloqueo de fila y expresiones de base de datos.
11. Un comando cliente tiene ID estable y solo puede confirmarse una vez.
12. Una pérdida de ACK provoca reintento del mismo comando, no creación de otro.
13. Si algún comando pudo ejecutarse, una desconexión no causa reembolso automático.
14. Un objeto solo puede activarse si toda su configuración valida contra el vocabulario instalado.

## Compatibilidad con Wildcards sin mezclar inventarios

Wildcards deben seguir existiendo y también pueden usar el nuevo vocabulario. La abstracción común
no debe ser una superclase genérica de inventario, sino una **definición de acción** reutilizable:

- `VirtualItem` conserva su catálogo y `VirtualItemStack`;
- `Wildcard` conserva su catálogo, economía e inventario de comodines;
- ambos pueden apuntar a un `ActionDefinition` con condiciones y efectos;
- cada dominio aporta su propio adaptador de reserva/consumo al ejecutar la acción.

Durante la transición, `Wildcard.action_definition` puede ser opcional. Los comodines existentes
continúan con `handler_key`; los nuevos o migrados gradualmente usan el motor compuesto. No es
necesario convertir Wildcards en VirtualItems ni viceversa.

Ejemplos:

- un comodín que concede un objeto competitivo usa `give_in_game_item` y no solicita Pokémon;
- un comodín que roba un Pokémon solicita jugador y Pokémon ajenos y aplica las condiciones de robo;
- Super Proteína usa el mismo motor, pero su consumo sale de `VirtualItemStack`.

## Contratos de entrada y ámbito

No se debe añadir `valid_pokemon_target` a toda acción. Cada primitive declara en código qué contexto
necesita y dónde puede aplicarse. El compilador de configuración combina esas declaraciones y genera
el contrato de entrada de la acción.

Campos cerrados iniciales de contexto:

- `actor_profile`: siempre implícito y autenticado;
- `target_profile`: ninguno, propio, otro o cualquiera;
- `target_pokemon`: ninguno, Pokémon propio o Pokémon del perfil objetivo;
- `team_scope`: ninguno, equipo propio o equipo del perfil objetivo;
- selecciones adicionales cerradas, por ejemplo `competitive_item_choice`.

Ejemplos de declaraciones:

- `modify_pokemon_stat`: requiere `target_pokemon=OWN`;
- `restore_team`: requiere `team_scope=OWN` y no recibe un Pokémon individual;
- `give_in_game_item` con item fijo: no requiere objetivo;
- `steal_pokemon`: requiere `target_profile=OTHER` y `target_pokemon=TARGET_PROFILE`;
- `pokemon_not_fainted`: solo admite acciones con `target_pokemon`;
- `team_has_fainted_member`: solo admite acciones con `team_scope`.

La propiedad y existencia del target son invariantes automáticas del contrato, no condiciones que
staff tenga que recordar agregar. Las condiciones representan reglas adicionales.

Al guardar/activar una configuración, el servidor la compila y rechaza combinaciones incompatibles.
Por ejemplo:

```text
pokemon_not_fainted requiere target_pokemon,
pero RESTORE_WHOLE_TEAM tiene team_scope=OWN y no selecciona Pokémon.
```

Para expresar una regla parecida en el equipo se crea/usa un primitive de equipo explícito. No se
interpreta silenciosamente una condición de Pokémon como condición de equipo.

El contrato compilado también indica a la UI qué selectores mostrar. Añadir una condición servidor
que usa campos ya existentes no requiere cliente nuevo; añadir un tipo de selector o una capacidad
de juego nueva sí puede requerirlo.

## Arquitectura mínima propuesta

Crear un dominio/app Django propio, por ejemplo `virtual_inventory`, con estos modelos conceptuales.

### `VirtualItem`

- `code`: identificador estable, único e inmutable, por ejemplo `SUPER_PROTEIN`;
- `name`, `description`, `sprite`;
- `is_active`;
- `stack_limit` opcional;
- `configuration_version`, incrementado al cambiar condiciones o efectos;
- metadatos de disponibilidad estrictamente necesarios para mostrarlo.

El cliente presenta metadatos del servidor. No lleva un catálogo embebido de objetos de evento.

### `ActionDefinition`, condiciones y efectos

`ActionDefinition` contiene una lista ordenada de condiciones y efectos y una versión de
configuración. Puede ser referenciado por un VirtualItem o por un Wildcard.

Las filas conceptuales `ActionCondition` y `ActionEffect` sustituyen a versiones exclusivas de
VirtualItem para evitar duplicar el motor. Cada una selecciona un primitive registrado y se acompaña
de un modelo de configuración tipado específico.

### `ActionCondition`

- `action_definition`;
- `position`;
- `kind`: elección cerrada registrada en código;
- configuración en un modelo relacional tipado correspondiente al `kind`;
- `is_active`.

Ejemplos iniciales de `kind`:

- `valid_own_pokemon_target`;
- `target_is_other_player`;
- `target_pokemon_level_below`;
- `target_pokemon_not_fainted`;
- `once_per_segment`.

No hace falta modelar `has_inventory_quantity` como fila de configuración: es una invariante del
servicio de uso y siempre se comprueba.

### `ActionEffect`

- `action_definition`;
- `position`;
- `kind`: elección cerrada registrada en código;
- configuración en un modelo relacional tipado correspondiente al `kind`;
- `is_active`.

Vocabulario inicial sugerido:

- servidor: `give_virtual_item`, `change_economy`, `grant_reward`, `grant_wildcard`, `notification`;
- cliente v1: `restore_hp`, `restore_pp`, `modify_pokemon_stat`, `give_in_game_item`.

Cada implementación declara si es `SERVER` o `CLIENT`, su versión, el tipo de objetivo, el modelo de
configuración esperado y la capacidad cliente requerida.

Ejemplos de modelos tipados:

- `ModifyPokemonStatEffectConfig(effect OneToOne, stat choices, amount PositiveSmallIntegerField)`;
- `GiveVirtualItemEffectConfig(effect OneToOne, item ForeignKey, quantity PositiveIntegerField)`;
- `PokemonLevelBelowConditionConfig(condition OneToOne, maximum_level)`;
- `OncePerSegmentConditionConfig(condition OneToOne, limit)`.

Esto permite que Foreign Keys, `choices`, `CheckConstraint`, unicidad y permisos de Django impidan
gran parte de las configuraciones absurdas antes de llegar al compilador. Al activar una acción, el
compilador comprueba además que el modelo de configuración corresponde al `kind`, que existe
exactamente uno y que los contratos de target son compatibles.

JSON se reserva para dos usos internos, no editables como configuración:

- snapshot inmutable de la acción dentro de una operación ya iniciada;
- payload autorizado y versionado enviado a una capacidad cliente.

### `VirtualItemStack`

- `profile`;
- `item`;
- `quantity`;
- `reserved_quantity`;
- `updated_at`;
- restricción única `(profile, item)` y checks de cantidades.

### `ActionOperation`

- UUID público;
- `idempotency_key` y actor;
- fuente explícita (pila de VirtualItem o pila de Wildcard), cantidad y objetivos resueltos;
- estado: `PREPARING`, `AWAITING_CLIENT`, `COMPLETED`, `REJECTED`, `NEEDS_REVIEW`;
- snapshot inmutable de configuración y targets;
- marcas de tiempo y error seguro para usuario/staff.

### `ActionClientCommand`

- UUID estable;
- operación y orden;
- perfil cuyo cliente debe ejecutarlo;
- `capability` versionada, por ejemplo `modify_pokemon_stat.v1`;
- payload autorizado e inmutable;
- estado: `PENDING`, `DELIVERED`, `ACKNOWLEDGED`, `FAILED`;
- intentos, timestamps y resultado verificado.

### Ledgers de inventario

Cada inventario conserva su ledger append-only de `GRANT`, `RESERVE`, `CONSUME`, `RELEASE` y
`STAFF_ADJUSTMENT`, relacionado con la operación/recompensa que lo originó. No se crea una tabla
genérica de assets ni se mezclan cantidades de objetos con comodines.

## Servicios, no lógica en modelos

- `ActionCatalog`: registros cerrados de condiciones y efectos, contratos, modelos de configuración
  y validación.
- `VirtualItemGrantService`: concede o ajusta inventario transaccionalmente.
- `ActionUseService.prepare(...)`: compila contexto, valida, reserva en el inventario correcto y crea
  la operación.
- `ActionOperationService.advance(...)`: ejecuta efectos de servidor y coordina comandos cliente.
- `ActionCommandService.ack(...)`: procesa ACK idempotente y finaliza o marca revisión.

No crear una jerarquía de clases por cada objeto. “Super Proteína” es una fila configurada que usa
`modify_pokemon_stat`; no una clase `SuperProteinHandler`.

## Contrato con el cliente

Endpoints conceptuales:

- `GET /api/virtual-inventory/`: catálogo poseído, cantidades y requisitos de selección para UI;
- `POST /api/virtual-inventory/{code}/use/`: target e `Idempotency-Key`;
- `GET /api/virtual-inventory/commands/pending/`: órdenes autorizadas para el cliente autenticado;
- `POST /api/virtual-inventory/commands/{uuid}/ack/`: resultado idempotente.

El servidor responde `COMPLETED` para operaciones solo servidor o `AWAITING_CLIENT` con IDs de
operación/comandos. El cliente entiende capacidades, no nombres de objetos ni condiciones.

Inicialmente se recomienda polling autenticado breve al usar un objeto y al reconectar. El WebSocket
puede enviar únicamente `client_operation_ready` como aviso; el cliente siempre obtiene el comando
real mediante API autenticada.

El cliente debe guardar localmente los IDs ejecutados y, ante un reintento, devolver el ACK previo sin
volver a escribir en RAM. Además debe releer RAM cuando sea posible para verificar el resultado.

## Flujos de ejemplo

### 1. Objeto puramente de servidor

`AYUDAR_JUGADOR`:

- condición `target_is_other_player`;
- efecto `give_virtual_item {item: FULL_RESTORE, quantity: 1}`;
- efecto `notification {template: ...}`.

Dentro de una transacción se bloquea la pila del actor, se valida el objetivo, se reserva/consume una
unidad, se concede Full Restore al objetivo y se registra todo. No interviene el cliente del juego.

### 2. Un efecto de juego

`SUPER_PROTEIN`:

- condición `valid_own_pokemon_target`;
- efecto `modify_pokemon_stat {stat: attack, amount: 1}`.

El servidor crea `modify_pokemon_stat.v1` con Pokémon y cantidad ya resueltos. El cliente modifica y
verifica RAM, luego confirma el mismo command UUID.

### 3. Múltiples efectos

`EMERGENCY_KIT`:

- condición `valid_own_pokemon_target`;
- efectos ordenados `restore_hp {mode: full}`, `restore_pp {mode: full}` y `notification`.

Los dos comandos cliente tienen IDs estables. Si el primero se aplicó y se perdió la conexión, se
reanuda desde el segundo; el primero solo devuelve su ACK almacenado. La notificación servidor se
emite al completar los comandos.

### 4. Objetivo en otro jugador

`REMOTE_RESTORE` usa `target_is_other_player` y un efecto cliente dirigido al perfil objetivo. El
servidor valida liga/tramo/autorización, reserva al actor y coloca el comando en la cola autenticada
del cliente objetivo. La operación permanece pendiente hasta ACK o revisión; el actor no puede elegir
el payload.

### 5. Cambio de balance sin release

Staff cambia `SUPER_PROTEIN.modify_pokemon_stat.amount` de 3 a 1 en Admin. La validación del schema
acepta el valor, aumenta `configuration_version` y los usos nuevos toman el nuevo snapshot. Cliente y
código del servidor no cambian.

### 6. Nueva condición solo servidor

Se implementa y prueba `target_has_no_active_battle` en el registro cerrado, se despliega únicamente
el servidor y después staff puede añadirla a objetos. El cliente no conoce la razón del rechazo.

### 7. Nueva capacidad cliente

Para `change_ability` se define `change_ability.v1`, payload, executor Java, verificación y ACK. El
servidor no permite activar objetos con ese efecto para clientes que no anuncien la capacidad mínima.
Tras desplegar el cliente compatible, staff puede crear o reconfigurar muchos objetos usando esa
capacidad sin más releases.

## Django Admin para staff

- Editor de `VirtualItem`/`ActionDefinition` con pasos ordenados.
- `kind` como selector cerrado y formulario relacional específico para su configuración.
- previsualización humana: “Ataque +1”, “objetivo: Pokémon propio”.
- botón/acción “Validar y activar”; un objeto inválido permanece inactivo.
- campos técnicos (`code`, versiones, ledger, operaciones) de solo lectura para staff ordinario.
- clonar objeto para experimentar sin alterar operaciones en curso.
- permisos separados para editar catálogo, conceder inventario y resolver operaciones.

Para el primer corte, inlines/formularios específicos para los pocos primitives iniciales son más
seguros que construir un editor dinámico universal.

## Adopción incremental sin migración de datos

1. Añadir modelos, constraints, registros y Admin sin cambiar Wildcards ni clientes.
2. Registrar desde cero el catálogo inicial de objetos virtuales.
3. Implementar concesión/uso solo servidor y pruebas de concurrencia/idempotencia.
4. Añadir `Reward.VIRTUAL_ITEM` y canje transaccional; mantener tipos anteriores sin cambios mientras
   todavía sean útiles para Wildcards reales.
5. Publicar API de lectura y UI genérica de mochila.
6. Implementar el protocolo de comandos y las primeras capacidades Java versionadas.
7. Dejar de crear dummy Wildcards para representar objetos; retirar ese camino cuando ya no tenga
   consumidores de código.
8. Deprecar por separado las piezas legacy que dejen de usarse, sin acoplar esa limpieza al estreno
   de la mochila.

No se integra ni migra información desde `market`/`new_market`.

## Complejidad que conviene evitar

- No crear un DSL, motor de reglas genérico ni scripts configurables.
- No hacer que cada objeto sea una clase/handler nuevo.
- No almacenar funciones o nombres de imports en DB.
- No usar YAML ni JSON libre como superficie de configuración de staff.
- No enviar condiciones al cliente para que este decida autorización.
- No intentar transacciones distribuidas con la RAM: usar operación persistente, reserva y reanudación.
- No generalizar Wildcards, recompensas y objetos bajo una superclase “Asset” en este corte.
- No modelar como condición configurable lo que siempre debe ser una invariante de seguridad.
- No prometer rollback automático después de una escritura de juego cuyo ACK sea incierto.

## Decisiones pendientes de revisión

1. Si un objeto dirigido a otro jugador puede permanecer pendiente indefinidamente o expira.
2. Qué roles pueden conceder/ajustar inventario manualmente.
3. Identificador estable del Pokémon objetivo entre servidor, guardado y proceso en ejecución.
4. Primer conjunto exacto de primitives y capacidades Java.
5. Política de operaciones `NEEDS_REVIEW` y herramienta de resolución para staff.
6. Punto exacto en que los endpoints/handlers legacy de objetos-Wildcard pueden marcarse deprecated.
