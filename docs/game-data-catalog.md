# Catálogo de datos del juego

Django es la fuente canónica de especies, formas, stats base, tipos y movimientos. PokeMada obtiene el catálogo autenticado desde `GET /api/game-data/catalog/` y no necesita que esos datos se editen también en Java.

## Descarga y caché

Al autenticar una sesión, la aplicación:

1. carga `%LOCALAPPDATA%\PokeMada\cache\game-data\catalog.json`, si existe;
2. envía su `ETag` guardado mediante `If-None-Match`;
3. conserva la copia actual si Django responde `304 Not Modified`;
4. si hay una versión nueva, comprueba que el SHA-256 del JSON coincida con el `ETag`;
5. analiza el catálogo antes de escribirlo y reemplaza el archivo mediante un movimiento atómico.

Si la red falla, se mantiene la última copia válida. Los CSV/TSV empaquetados con versiones antiguas funcionan únicamente como bootstrap de compatibilidad antes de la primera sincronización; ya no son la fuente que se debe actualizar.

## Consumidores

- `PokemonBaseStats`: stats base por Pokédex y forma.
- `PokemonTypeDex`: tipos por Pokédex y forma.
- `PokemonSpeciesDex`: nombres.
- `PokemonMoveDex`: nombres, tipo, categoría, potencia, PP, precisión y descripción.
- Cajas: recibe la forma y los datos reales del ejemplar desde Django y cruza su presentación con el catálogo.
- Poké Vial: calcula los PP máximos desde `PokemonMoveDex`, por lo que adopta automáticamente cambios publicados por el servidor.

La versión no se incrementa manualmente: Django serializa el contenido canónico y usa su SHA-256. Cualquier cambio real produce una versión distinta y una única descarga nueva por instalación.
