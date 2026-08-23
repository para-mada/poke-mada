# PokeMada Data Dumper

Herramienta CLI independiente y estrictamente de solo lectura para capturar regiones de RAM del
emulador. El perfil inicial es exclusivo de Pokémon Sun/Moon; no utiliza sus direcciones para
Ultra Sun/Ultra Moon.

La configuración compartida `Data Dumper` de IntelliJ ejecuta la herramienta sin argumentos. Con
MadaLime abierto y el juego iniciado, el resultado se crea en:

```text
%LOCALAPPDATA%\PokeMada\dumps\pokemon-sm\<fecha-utc>-<id>
```

Cada captura contiene los binarios originales y un `manifest.json` con dirección virtual, longitud,
SHA-256 y contrato de transporte. Las regiones predeterminadas incluyen equipo, estado de combate,
Pokémon activos, entrenador enemigo y mochila.

## Argumentos

```text
--host HOST
--port PORT
--timeout SEGUNDOS
--output DIRECTORIO
--range NOMBRE:DIRECCION:LONGITUD
--global
```

Los números pueden escribirse en decimal o hexadecimal (`0x...`). `--range` agrega una región a la
captura estándar y puede repetirse. No existe una opción de escritura.

MadaLime permite lecturas RPC de hasta 1024 bytes. El cliente conserva compatibilidad de lectura con
servidores Citra/Lime3DS sin modificar mediante una degradación automática a bloques de 32 bytes;
la escritura permanece limitada a 24 bytes en el transporte general y el dumper nunca la expone.

## Dump global

`--global` genera `global-guest-1gib.sparse.bin`, una imagen dispersa cuyo tamaño lógico es 1 GiB.
Los datos se escriben en sus offsets virtuales guest reales, pero solo se descargan las regiones
respaldadas por RAM de MadaLime: FCRAM de New 3DS, RAM adicional, VRAM, DSP y páginas de sistema.
Los huecos sin mapear no ocupan bloques físicos del disco ni provocan millones de lecturas inútiles.

La captura guarda `global-progress.txt` después de cada bloque de 1 MiB. Si se interrumpe, vuelve a
ejecutar `--global --output <el-mismo-directorio>` para continuar desde el último bloque terminado.
