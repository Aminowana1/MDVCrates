# MDVCrates 1.2.0

Plugin de crates físicas para Paper/Purpur 1.21.6, Java 21.

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

El jar sale en `target/MDVCrates-1.2.0.jar`.
