# Bitácora persistente — mochila virtual

Última actualización: 2026-08-25 — primer objeto de catálogo implementado

## Objetivo actual

Construir desde cero una mochila propia para objetos virtuales de evento. El núcleo relacional,
servicios transaccionales, contrato REST y `SUPER_PROTEIN` ya están implementados; el próximo
objetivo es integrar `modify_pokemon_stat.v1` en Java/RAM.

`market` y `new_market` están fuera de alcance y se consideran legado a deprecar.
Wildcards deben continuar existiendo, con inventario separado, pero podrán reutilizar el mismo motor
de acciones que los objetos virtuales.

## Investigación completada

- Flujo de catálogo, compra, validación, ejecución y consumo de `Wildcard`.
- `StreamerWildcardInventoryItem`, helpers de `MastersProfile` y escrituras directas de cantidad.
- Registry, `BaseWildCardHandler`, todos los handlers registrados y modelos de settings.
- `dummy_handler` y handlers que devuelven comandos para modificar el juego.
- `RewardBundle`, `Reward`, `StreamerRewardInventory`, entrega, canje y regalos de Admin.
- API `wildcards_with_inventory`, `use_card`, `buy_card`, `get_rewards`, `claim_reward`.
- `DataConsumer`/WebSockets y consumo de notificaciones en el cliente Java.
- Buzón y contrato de recompensas del cliente Java.

## Hallazgos confirmados

- `dummy_handler` es un no-op real; el workaround existe en código.
- No existe una base de datos productiva que migrar; el sistema se está montando de nuevo.
- Wildcards fueron usados históricamente como inventario, pero ya no cumplirán ese papel.
- `Wildcard` mezcla metadata, economía, inventario, validación y ejecución.
- Solo admite un `handler_key`; la composición actual deriva en handlers especializados/herencia.
- No hay unique constraint para `(profile, wildcard)` ni operación idempotente/ACK cliente.
- `use_card` descuenta después de producir el resultado, sin transacción que cubra el flujo.
- `claim_reward` no es atómico y solo entrega dinero/Wildcards aunque marca el bundle como usado.
- El WebSocket actual se dirige por username y no debe transportar órdenes autorizadas.
- El escritorio solo muestra notificaciones genéricas y no ejecuta comandos de Wildcards.

## Decisiones y propuestas arquitectónicas

- Decisión confirmada: sin dependencia de `market`, `new_market`, mochila RAM o inventario de Wildcards.
- Decisión confirmada: Wildcards siguen siendo un dominio poseíble y no se convierten en VirtualItems.
- Propuesta: compartir `ActionDefinition`/condiciones/efectos y ejecución, pero mantener balances,
  reglas comerciales y ledgers separados.
- Decisión implementada: dominio/app Django independiente `virtual_inventory`.
- Decisión implementada: `VirtualItem`, `VirtualItemStack`, `ActionDefinition`,
  `ActionCondition`, `ActionEffect`, `ActionOperation`, `ActionClientCommand` y ledgers separados.
- Primitives cerrados registrados en código; configuración editable mediante modelos relacionales
  tipados, sin scripts ni YAML/JSON libre.
- Foreign Keys, choices, constraints y formularios específicos previenen configuraciones inválidas;
  el compilador de activación valida compatibilidad entre primitives y targets.
- JSON solo para snapshots internos de operaciones y payloads cliente ya autorizados.
- Servicios transaccionales en lugar de lógica de negocio nueva dentro de modelos.
- Constraints DB, bloqueo de filas, reservas e idempotencia como invariantes obligatorias.
- El servidor valida condiciones y genera payloads autorizados; el cliente solo implementa
  capacidades versionadas.
- Cada primitive declara su contrato de target. El servidor deriva los selectores requeridos y
  rechaza en configuración condiciones incompatibles con el ámbito de la acción.
- Propiedad/existencia de targets son invariantes automáticas; no condiciones configurables.
- Comandos obtenidos/confirmados por API autenticada. WebSocket solo puede avisar que hay pendientes.
- No habrá mapeo ni copia de cantidades desde Wildcards: el catálogo y los inventarios comienzan
  vacíos con los modelos nuevos.
- La transición es de código: conservar temporalmente Wildcards reales y deprecar el workaround de
  objetos cuando ya no tenga consumidores.

Detalle y ejemplos: [virtual-inventory-design.md](virtual-inventory-design.md).

## Archivos/componentes analizados

Servidor:

- `event_api/models.py`, `serializers.py`, `admin.py`;
- `event_api/wildcards/registry.py`, `wildcard_handler.py`, todos los `handlers/` y settings;
- `rewards_api/models.py`, `serializers.py`;
- `api/views.py`, `api/model_views/rewards.py`;
- `admin_panel/models.py`, `admin_panel/admin.py`;
- `websocket/sockets.py`, `pokemada/ws_urls.py`;
- migraciones relevantes de inventario Wildcard.

Escritorio:

- `server/ServerClient.java`, `NotificationConnection.java`;
- `MailboxController.java`, integración de notificaciones en `MainController.java`;
- placeholder actual de Comodines y parser independiente de mochila Sun/Moon.

## Cambios implementados

- El PoC equivocado basado en `new_market.BankItem` fue retirado por completo.
- Nueva app Django `virtual_inventory` registrada en settings.
- Modelos: catálogo de objetos, acciones, condiciones/efectos tipados, stacks con reserva,
  operaciones idempotentes, comandos cliente y ledger de objetos virtuales.
- Configuraciones relacionales para nivel, límite por tramo, modificación de stat, entrega de objeto
  del juego/virtual y notificación.
- Compilador que deriva targets desde efectos y rechaza condiciones sin contexto compatible.
- Señales que desactivan/versionan una acción cuando cambia su configuración.
- Django Admin con formularios tipados y acción “Validar y activar”.
- Wildcard mantiene `handler_key` legacy y puede enlazar opcionalmente un `ActionDefinition` nuevo.
- Inventario Wildcard conserva tabla separada y gana reserva, unicidad y checks de cantidad.
- Migraciones `virtual_inventory.0001`, `event_api.0125` y `event_api.0126`.
- Configuración de tests aislada del problema legacy DRF/coreapi.
- La integración de recompensas y la UI/capacidades Java siguen pendientes.
- Servicios `VirtualItemGrantService`, `ActionUseService` y `ActionCommandService`: concesión, reserva,
  preparación idempotente, condiciones runtime, comandos, ACK y consumo transaccional.
- Operaciones guardan el tramo para aplicar límites `once_per_segment` de forma verificable.
- Views REST autenticadas registradas bajo `/api/virtual-inventory/`: listado, uso por código,
  comandos pendientes y ACK. Coaches operan sobre su perfil entrenado según la semántica existente.
- El uso exige `Idempotency-Key` UUID; repetir exactamente la solicitud devuelve la operación y
  cambiar parámetros produce `idempotency_conflict` sin consumir otra unidad.
- Efectos de servidor (`give_virtual_item`, `notification`) se completan atómicamente; los efectos
  RAM reservan primero y consumen únicamente tras ACK. Un fallo cliente pasa a `needs_review` y
  conserva la reserva por seguridad.
- Aún no se implementaron recompensas, UI/capacidades Java ni avisos WebSocket de comandos pendientes.
- Catálogo canónico idempotente `install_super_protein()`: Super Proteína aumenta Ataque en 1 sobre
  un Pokémon propio, una vez por tramo, con capacidad cliente `modify_pokemon_stat.v1`.
- Comando `install_virtual_inventory_demo` instala/repara el catálogo y opcionalmente concede una
  cantidad a `--username` mediante el ledger, sin usar `market`.
- Guía ejecutable del flujo completo en `docs/virtual-inventory-super-protein-demo.md`.

## Validación realizada

- Antes de corregir el alcance, la suite Java existente pasó: 89 pruebas, 0 fallos. Ese resultado no
  valida aún la arquitectura propuesta.
- Se confirmó por búsqueda que no quedan referencias al endpoint/UI temporal de `inventory_items`.
- La consulta local confirmó que SQLite no tiene tablas, coherente con el reinicio sin datos.
- `manage.py check` está bloqueado por un problema preexistente de DRF/coreapi en el entorno local.
- `makemigrations --dry-run --check`: no detecta cambios pendientes.
- Todas las migraciones del proyecto aplican correctamente sobre SQLite temporal.
- Suite `virtual_inventory`: 7 pruebas, 0 fallos; cubre contratos Pokémon/equipo, configuración
  tipada obligatoria, target de otro jugador, desactivación/versionado y constraints de stacks.
- System checks con URLConf aislado: 0 problemas.
- Suite `virtual_inventory` ampliada: 12 pruebas, 0 fallos. Cubre además listado privado, header
  idempotente obligatorio, replay sin doble consumo, conflicto de clave y ciclo pending-command/ACK.
- `makemigrations --dry-run --check`: no hay cambios de modelos sin migración tras añadir el tramo.
- Suite ampliada tras el primer catálogo: 14 pruebas, 0 fallos; incluye instalación repetible y
  concesión por management command. Las pruebas HTTP aíslan su URLConf sin pisar el del Admin.

## Problemas sin resolver

- Política de expiración/revisión para efectos dirigidos a otro jugador.
- Roles autorizados para concesiones y ajustes manuales.
- Identidad estable de Pokémon entre servidor, guardado y RAM.
- Primer conjunto exacto de condiciones, efectos y capacidades cliente.
- Adaptador/servicio para ejecutar `ActionDefinition` desde una pila Wildcard; el modelo ya usa FKs
  explícitas y constraint de exclusividad, pero el endpoint actual solo prepara VirtualItems.
- Semántica de recuperación cuando una escritura RAM pudo ocurrir pero no llegó el ACK.
- Alcance del primer corte respecto a recompensas y deprecación de endpoints legacy.

## Próximos pasos recomendados

1. Implementar `modify_pokemon_stat.v1` en Java y la UI de mochila.
2. Añadir política/acción staff para resolver `needs_review` y liberar o consumir reservas.
3. Probar concurrencia real (dos conexiones/DB productiva), expiración y recuperación de comandos.
4. Crear el adaptador Wildcard al motor compartido sin mezclar inventarios.
5. Integrar recompensas y avisos WebSocket solo como señal de comandos pendientes.
