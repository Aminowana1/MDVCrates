# MDVCrates 1.2.3

Plugin de crates físicas para Paper/Purpur 1.21.6, Java 21.

## Novedades 1.2.2

- `orbits` puede mover `ItemDisplay` reales además de partículas.
- Cada orbiter es la misma entidad moviéndose por la trayectoria, sin dejar rastro.
- Nuevo `idle.item-displays` para cristales/bloques/ítems flotantes estáticos.
- `material` acepta cualquier Material Bukkit; también se admiten MMOItems por TYPE + ID.
- `size` o `scale.x/y/z` controla el tamaño del ItemDisplay.
- `opening-movement` permite desplazar displays al iniciar la apertura (X/Y/Z, delay, duración y curva).
- `phase-deg` permite elegir la posición inicial de un orbiter.

## Novedades 1.2.0

### Crates separadas por archivo

Las definiciones ahora viven en:

```text
plugins/MDVCrates/crates/
  caja1.yml
  caja_evento.yml
  caja_vip.yml
```

Cada archivo contiene directamente la definición de una crate, sin el antiguo bloque `crates:`.

Al arrancar 1.2.0, si existe el antiguo `plugins/MDVCrates/crates.yml`, el plugin:

1. crea automáticamente un YAML por crate dentro de `crates/`;
2. conserva `placements.yml` por separado;
3. renombra el archivo antiguo a `crates.yml.migrated-backup` (o un nombre numerado si ya existe un backup).

No hace falta migrar las crates a mano.

### Vanilla sin Base64

Los objetos vanilla simples ahora se guardan así:

```yaml
rewards:
  diamantes:
    type: VANILLA
    weight: 10.0
    amount: 5
    material: DIAMOND
```

Si una recompensa antigua era `type: ITEM` + `BUKKIT_BYTES_BASE64` pero al decodificarla resulta ser un ItemStack vanilla sin metadata, 1.2.0 la convierte automáticamente a `VANILLA` + `material`.

Los objetos custom no-MMOItems que sí tengan metadata/componentes continúan como `ITEM` Base64 para no perder información.

Los MMOItems continúan guardándose como `MMOITEM` con TYPE + ID.

### Editor con paginación

`/mdvcrates editor <id>` ya no tiene límite práctico de 45 recompensas. El editor mantiene todas las recompensas en memoria durante la sesión y permite navegar con **Página anterior** / **Página siguiente**. Al guardar o cerrar se guardan todas las páginas.

El visualizador público ya tenía paginación y la conserva.

Configuración:

```yaml
editor:
  size: 54
  rewards-per-page: 45
  default-new-item-weight: 10.0
  show-command-rewards: true
```

## Tipos de recompensa

### VANILLA

```yaml
hierro:
  type: VANILLA
  weight: 20
  amount: 8
  material: IRON_INGOT
```

### MMOITEM

```yaml
piedra_lunar:
  type: MMOITEM
  weight: 10
  amount: 1
  mmoitems-type: MATERIAL
  mmoitems-id: PIEDRA_LUNAR
```

### ITEM custom no-MMOItems

Los crea automáticamente el editor cuando el ItemStack contiene metadata/componentes que deben conservarse. Se guarda como snapshot Base64.

### COMMAND

```yaml
rango:
  type: COMMAND
  chance: 5
  amount: 1
  name: "&6Rango especial"
  commands:
    - "lp user {player} parent addtemp vip 7d"
  preview:
    material: NETHER_STAR
    name: "&6&lRango especial"
```

## Probabilidades

- Solo `weight`: reparto proporcional clásico.
- `chance`: porcentaje explícito de 0 a 100.
- Si se mezclan, los `chance` reservan su porcentaje y el restante se reparte por `weight`.
- Si todos usan `chance` y no suman 100, se normalizan para que cada apertura siga dando una recompensa.

## Funciones principales

- Crates físicas registradas por ubicación.
- Ubicaciones en `placements.yml`, independientes de las definiciones.
- `/mdvcrates reload` refresca crates colocadas en chunks cargados.
- Bloques soportados: `CHEST`, `TRAPPED_CHEST`, `ENDER_CHEST` y shulkers de cualquier color.
- Llaves MMOItems verificadas por TYPE + ID.
- Click derecho con llave correcta: abre la crate.
- Click izquierdo: abre el visualizador de recompensas sin consumir llave.
- Reserva de slot y sistema `pending.yml` para proteger la recompensa durante la animación/logout/reload.
- Recompensas VANILLA, MMOITEM, ITEM custom y COMMAND.
- Editor gráfico paginado.
- Visualizador de premios paginado.
- Idle Rings/Orbits/Random Points y animaciones de opening.

### Rings 1.2.1: centro desplazable y órbita del ring completo

Los `rings` aceptan ahora `center-offset.x/y/z` para mover su centro y un bloque
`center-orbit` opcional para que el ring completo gire horizontalmente alrededor
de la crate sin cambiar la orientación de su plano:

```yaml
center-offset:
  x: 0.0
  y: 0.0
  z: 0.0

center-orbit:
  enabled: true
  radius: 1.10
  speed-deg-per-tick: -2.0
  phase-deg: 0.0
  y-offset: 0.0
```

`phase-deg: 180.0` permite colocar un segundo ring exactamente al lado opuesto.
`phase-speed-deg-per-tick` sigue moviendo los puntos DENTRO del ring y
`plane-rotation-deg-per-tick` sigue rotando el plano del ring.

Las partículas aceptan además `particle-speed`. Es un nombre más claro para el
parámetro Bukkit `extra`; las configs antiguas con `extra` continúan funcionando.
`particle-speed: 0.0` elimina el impulso extra de la partícula, pero no puede
reducir su vida visual, ya que esa duración la controla el cliente de Minecraft.
- Holograma/nombre configurable por crate.

## Comandos

- `/mdvcrates create <id>` crea `plugins/MDVCrates/crates/<id>.yml`.
- `/mdvcrates editor <id>` abre el editor paginado.
- `/mdvcrates place <id>` coloca una instancia.
- `/mdvcrates remove` elimina la instancia mirada.
- `/mdvcrates move <id>` mueve una instancia cercana.
- `/mdvcrates reload` recarga configs y crates individuales.
- `/mdvcrates list` lista crates cargadas.

Permiso admin: `mdvcrates.admin`.

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

El jar sale en `target/MDVCrates-1.2.2.jar`.

## ItemDisplays de crate y órbitas 1.2.2

### ItemDisplay real dentro de `orbits`

Un orbit puede seguir usando partículas como siempre. Si contiene `item-display:`,
el plugin crea una entidad `ItemDisplay` por orbiter y mueve ESA MISMA entidad por
la trayectoria. No va dejando copias ni partículas detrás.

```yaml
orbits:
  luna_calcita:
    enabled: true
    orbiters: 1
    radius: 1.05
    y-offset: 0.0
    angular-speed-deg-per-tick: 2.4
    phase-deg: 0.0
    random-plane: false
    tilt-deg:
      x: 55.0
      y: 0.0
      z: 20.0
    plane-rotation-deg-per-tick:
      x: 0.0
      y: 0.0
      z: 0.0
    item-display:
      enabled: true
      material: CALCITE
      size: 0.14
      transform: FIXED
      billboard: FIXED
      view-range: 1.0
```

`material` acepta cualquier `Material` de Bukkit. También se puede mostrar un
MMOItem usando `mmoitems-type` + `mmoitems-id` dentro de `item-display`.

### ItemDisplays estáticos

`idle.item-displays` permite colocar objetos visuales en posiciones fijas respecto
a la crate, por ejemplo un `AMETHYST_CLUSTER` flotando encima:

```yaml
item-displays:
  cristal:
    enabled: true
    material: AMETHYST_CLUSTER
    offset:
      x: 0.5
      y: 1.55
      z: 0.5
    size: 0.65
    transform: FIXED
    billboard: FIXED
    hide-during-opening: false
```

### Movimiento al comenzar opening

Tanto un display estático como el `item-display` de un orbit pueden incluir:

```yaml
opening-movement:
  enabled: true
  delay-ticks: 0
  duration-ticks: 20
  curve: LINEAR
  offset:
    x: 0.0
    y: 1.0
    z: 0.0
```

Esto mueve el mismo display 1 bloque hacia arriba durante los primeros 20 ticks
de la apertura y luego lo mantiene en la posición final mientras siga abierta.
Al terminar la apertura vuelve a su posición idle base.

Curvas soportadas: `LINEAR`, `EASE_IN_QUAD`, `EASE_OUT_QUAD`,
`EASE_IN_OUT_QUAD` y `EASE_OUT_CUBIC`.


## 1.2.3 - BlockDisplay real

`ItemDisplay` usa el modelo de inventario del material. Para cubos/bloques 3D reales usa `block-display:` en una órbita o `idle.block-displays:` para displays estáticos.

### Bloque orbitando
```yaml
orbits:
  piedra:
    enabled: true
    orbiters: 1
    radius: 1.05
    y-offset: 0.0
    angular-speed-deg-per-tick: 2.4
    random-plane: false
    tilt-deg: {x: 55.0, y: 0.0, z: 20.0}
    plane-rotation-deg-per-tick: {x: 0.0, y: 0.0, z: 0.0}
    block-display:
      enabled: true
      material: CALCITE
      size: 0.14
      billboard: FIXED
      teleport-duration-ticks: 1
      hide-during-opening: false
```

### Bloque estático arriba de la crate
```yaml
block-displays:
  cristal:
    enabled: true
    material: AMETHYST_CLUSTER
    offset: {x: 0.5, y: 1.55, z: 0.5}
    size: 0.65
    billboard: FIXED
    opening-movement:
      enabled: true
      duration-ticks: 20
      curve: LINEAR
      offset: {x: 0.0, y: 1.0, z: 0.0}
```
