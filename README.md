# MDVCrates 1.1.2

Plugin de crates físicas para Paper/Purpur 1.21.6, Java 21.

## Funciones principales

- Crates físicas registradas por ubicación.
- Las ubicaciones físicas se guardan aparte en `placements.yml`: puedes editar o reemplazar `crates.yml` y usar `/mdvcrates reload` sin volver a colocar las crates.
- `/mdvcrates reload` refresca automáticamente las crates colocadas en chunks cargados; las de chunks descargados se sincronizan al cargarse, sin forzar chunks.
- Bloques soportados: `CHEST`, `TRAPPED_CHEST`, `ENDER_CHEST`, `SHULKER_BOX` y todos los colores de shulker.
- Llaves MMOItems verificadas por TYPE + ID.
- Click derecho con la llave correcta: abre la crate.
- Click izquierdo sobre una crate registrada: abre el visualizador de recompensas aunque no tengas la llave o tengas otra llave en la mano; nunca consume llave.
- Consume exactamente 1 llave por apertura y solo después de validar todo.
- Requiere al menos 1 slot libre antes de consumir la llave.
- Reserva ese slot: si se ocupa durante la animación, el objeto intruso se dropea y la recompensa ocupa el slot reservado.
- Recompensas MMOItems por TYPE + ID.
- Para previews MMOItems se intenta el build de display de MMOItems (`build(true)`), para no copiar el estado/modificadores del ItemStack que el admin metió al editor. La entrega real sigue usando el build normal de MMOItems.
- Recompensas de cualquier ItemStack custom guardadas como snapshot Base64.
- Recompensas por uno o varios comandos de consola con `{player}` y PlaceholderAPI opcional.
- `weight` clásico y `chance` directo por recompensa.
- Visualizador configurable por crate con `viewer.show-percentages: true/false`.
- Editor gráfico: `/mdvcrates editor <id>`.
- Una sola persona puede abrir cada crate a la vez y un jugador no puede abrir dos simultáneamente.
- Protección ante logout, muerte, teleport, reload/apagado: el ganador se fija y se persiste en `pending.yml` antes de consumir la llave.
- Idle con Rings 3D, Orbits y Random Points.
- Cada Ring, Orbit y Random Point tiene `interval-ticks` propio.
- La succión de opening tiene `spawn-interval-ticks` + `motes-per-spawn`.
- Las animaciones idle siguen activas durante opening.
- Nombre/holograma por crate con offset configurable y `hide-during-opening`.
- La ruleta muestra `xN` en verde cuando una recompensa tiene cantidad mayor a 1.
- Apertura con partículas de succión hacia la crate, ItemDisplay de ruleta, desaceleración, pausa final configurable, burst y elevación del premio.
- Protección contra abrir el inventario vanilla, romper, explosiones, pistones y hoppers.

## Probabilidades

### Solo pesos

```yaml
rewards:
  comun:
    type: MMOITEM
    weight: 70
  raro:
    type: MMOITEM
    weight: 30
```

Se comporta como siempre: 70/30 relativo.

### Chance directo

```yaml
rewards:
  comun:
    type: MMOITEM
    chance: 90
    weight: 1
  raro:
    type: MMOITEM
    chance: 10
    weight: 1
```

Si todos usan `chance`, es recomendable que sumen 100.

### Mezcla de chance + weight

Los `chance` explícitos reservan su porcentaje. El porcentaje restante se reparte entre los rewards que solo tienen `weight`.

## Animaciones

### Intervalos idle

```yaml
rings:
  ring1:
    interval-ticks: 4
```

El Ring se dibuja cada 4 ticks.

```yaml
orbits:
  orbit1:
    interval-ticks: 3
```

### Random Points

```yaml
random-points:
  ambient1:
    enabled: true
    particle: SOUL_FIRE_FLAME
    interval-ticks: 5
    points-per-spawn: 2
    shape: SPHERE
    radius: 1.5
    vertical-radius: 0.8
    center-y-offset: 0.5
    surface-only: false
    count: 1
    spread: 0.0
    extra: 0.0
```

### Succión opening

```yaml
suction:
  enabled: true
  particle: SOUL_FIRE_FLAME
  spawn-interval-ticks: 4
  motes-per-spawn: 1
  radius: 1.55
  travel-ticks-min: 8
  travel-ticks-max: 14
```

`spawn-interval-ticks: 4` + `motes-per-spawn: 1` equivale a aproximadamente 5 motes nuevos por segundo.

## Nombre de la crate

```yaml
name-display:
  enabled: true
  text: "{display-name}"
  offset:
    x: 0.5
    y: 1.85
    z: 0.5
  hide-during-opening: false
```

## Comandos

- `/mdvcrates create <id>` crea una definición básica.
- `/mdvcrates editor <id>` abre el editor de recompensas de ítem.
- `/mdvcrates place <id>` coloca una instancia sobre el bloque que estás mirando.
- `/mdvcrates remove` elimina la instancia física que estás mirando.
- `/mdvcrates move <id>` mueve la instancia más cercana de ese ID al bloque sobre el que estás mirando.
- `/mdvcrates reload` recarga configs y actualiza automáticamente todas las instancias ya colocadas.
- `/mdvcrates list` lista crates cargadas.

Permiso admin: `mdvcrates.admin`.

## Editor

El editor no consume los objetos del administrador:

- SHIFT + click sobre un objeto de tu inventario: añade una copia como recompensa.
- Click izquierdo sobre una recompensa de ítem: la quita del editor.
- Recompensas COMMAND se representan con su `preview`, pero se editan solo en `crates.yml`.
- Al cerrar se guarda automáticamente.

Si el objeto añadido es MMOItems, MDVCrates guarda únicamente TYPE + ID. Para el display del editor/ruleta vuelve a construir un preview limpio desde MMOItems; no serializa el ItemStack usado/gastado que metiste al editor.

## Compilar

El repo incluye `.github/workflows/maven.yml`.

1. Sube el contenido a GitHub.
2. Abre **Actions**.
3. Ejecuta **Build MDVCrates**.
4. Descarga el artifact `MDVCrates-JAR`.

También compila con Java 21 + Maven:

```bash
mvn clean package
```

El jar sale en `target/MDVCrates-1.1.2.jar`.
