# Demo — Super Proteína

`SUPER_PROTEIN` aumenta en 1 el Ataque de un Pokémon propio y solo puede usarse una vez por tramo.
El servidor valida el target y genera el comando `modify_pokemon_stat.v1`; el objeto se consume cuando
el cliente confirma el comando.

## 1. Instalar y conceder unidades

Desde el repositorio del servidor:

```powershell
.\.venv\Scripts\python.exe manage.py migrate
.\.venv\Scripts\python.exe manage.py install_virtual_inventory_demo --username TU_USUARIO --quantity 3
```

El comando puede ejecutarse nuevamente: repara/reutiliza el catálogo y suma las unidades indicadas.

## 2. Autenticarse

```http
POST /user/login/
Content-Type: application/json

{"username":"TU_USUARIO","password":"TU_PASSWORD"}
```

Usa el token recibido como `Authorization: Token ...` en las siguientes solicitudes.

## 3. Consultar la mochila

```http
GET /api/virtual-inventory/
Authorization: Token TU_TOKEN
```

La Super Proteína anuncia `target_pokemon: true` y la capacidad `modify_pokemon_stat.v1`.

## 4. Usarla

```http
POST /api/virtual-inventory/SUPER_PROTEIN/use/
Authorization: Token TU_TOKEN
Idempotency-Key: 9a33b593-3baa-4ea3-8eba-fb5938663406
Content-Type: application/json

{"target_pokemon_id": 123, "quantity": 1}
```

La operación queda en `awaiting_client` y devuelve un comando parecido a:

```json
{
  "capability": "modify_pokemon_stat.v1",
  "payload": {"target_pokemon_id": 123, "stat": "attack", "amount": 1, "quantity": 1}
}
```

Repetir exactamente la petición con el mismo `Idempotency-Key` recupera la misma operación. Usar esa
clave con otros parámetros devuelve `idempotency_conflict`.

## 5. Confirmar desde el cliente

```http
GET /api/virtual-inventory/commands/pending/
Authorization: Token TU_TOKEN
```

Después de aplicar el cambio en RAM:

```http
POST /api/virtual-inventory/commands/UUID_DEL_COMANDO/ack/
Authorization: Token TU_TOKEN
Content-Type: application/json

{"succeeded": true, "result": {"applied": true}}
```

El ACK completa la operación, quita la reserva y consume una Super Proteína. Si falla, envía
`succeeded: false`; la operación queda en `needs_review` sin perder silenciosamente el objeto.
