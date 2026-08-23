# Distribución para Windows

La aplicación se distribuye mediante `jpackage` como un instalador por usuario. El equipo del
usuario final no necesita tener Java instalado: el paquete incluye un runtime reducido de Java 21,
JavaFX y los módulos utilizados por PokeMada.

La instalación contiene dos ejecutables: `PokeMada.exe` para la interfaz y
`PokeMadaDataDumper.exe` para la herramienta de captura RAM de solo lectura.

## Directorios

- Instalación: `%LOCALAPPDATA%\PokeMada`
- Caché de sprites: `%LOCALAPPDATA%\PokeMada\cache\sprites`
- Caché de objetos: `%LOCALAPPDATA%\PokeMada\cache\items`

La ubicación de datos puede sobrescribirse durante desarrollo con
`-Dpokemada.data.dir=C:\ruta\alternativa`.

## Requisitos de la máquina de compilación

- Windows 10 u 11.
- JDK 21 con `jlink` y `jpackage`; configura `JAVA_HOME` para apuntar a él.
- WiX Toolset 3.11 o 3.14 para producir el instalador `.exe`; el script puede descargar una copia
  portable oficial con `-BootstrapWix`.

WiX solo se necesita para construir el instalador. La copia portable permanece en
`target\build-tools`, y no se instala ni se incluye en el equipo del usuario final.

## Construir el instalador

Desde PowerShell, en la raíz del repositorio:

```powershell
.\scripts\package-windows.ps1 -Version 1.0.0 -BootstrapWix
```

El resultado se guarda en `target\installer\PokeMada-1.0.0.exe`. El instalador crea accesos en el
menú Inicio y el escritorio, y no requiere privilegios de administrador.

Para comprobar la distribución sin instalar WiX:

```powershell
.\scripts\package-windows.ps1 -Type app-image -Version 1.0.0
.\target\installer\PokeMada\PokeMada.exe
```

Esta segunda variante produce una carpeta autocontenida ejecutable, no un instalador.

## Versiones y actualizaciones

El UUID de actualización de Windows permanece fijo en el script. Para publicar una actualización,
incrementa `-Version`; no cambies `--win-upgrade-uuid`, ya que Windows lo utiliza para relacionar
las versiones instaladas.
