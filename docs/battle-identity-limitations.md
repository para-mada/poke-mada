# Limitaciones de identidad durante el combate

## Decisión

Las habilidades y movimientos que alteran la especie o apariencia de un Pokémon quedan fuera del
alcance de la detección automática de combatientes hasta localizar en RAM un identificador estable
que relacione cada posición del campo con una instancia concreta del equipo.

Casos excluidos:

- Ilusión;
- Impostor;
- Transformación;
- cualquier modificación, habilidad o movimiento que produzca el mismo efecto.

Estos casos no deben formar parte de verificaciones funcionales ni considerarse soportados por la
interfaz de combate. Tampoco deben resolverse mediante heurísticas basadas en especie, forma,
habilidad, moveset, texto de batalla o posición del registro.

## Motivo

Las direcciones activas conocidas publican la especie visible, que puede ser un disfraz o una forma
copiada. Los registros de batalla observados tampoco proporcionan por sí solos una relación fiable
entre esa apariencia y el Pokémon real. Los buffers de texto pueden estar atrasados, parciales o
mezclar mensajes de distintas transiciones.

Una inferencia incorrecta puede:

- seleccionar otro Pokémon del mismo equipo;
- mezclar movesets de especies duplicadas;
- impedir que la aplicación detecte el combate;
- revelar Ilusión en el equipo rival y dar información que el jugador aún no conoce.

## Condición para retomar el soporte

Antes de implementar estos casos debe existir una fuente reproducible de la relación:

```text
posición activa -> instancia estable del roster
```

La solución debe distinguir Pokémon duplicados con la misma especie y forma, funcionar en combates
simples, dobles, SOS y Battle Royale, y separar el estado real de RAM de la información públicamente
conocida por el jugador.

Hasta entonces, los dumps relacionados sirven únicamente para investigación y no constituyen un
contrato de ejecución.
