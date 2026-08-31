# MDVCrates 1.0.0

Plugin de crates físicas para Paper/Purpur 1.21.6, Java 21.

## Funciones principales

- Crates físicas registradas por ubicación.
- Llaves MMOItems verificadas por TYPE + ID.
- Consume exactamente 1 llave por apertura y solo después de validar todo.
- Requiere al menos 1 slot libre antes de consumir la llave.
- Reserva ese slot: si se ocupa durante la animación, el objeto intruso se dropea y la recompensa ocupa el slot reservado.
- Recompensas MMOItems por TYPE + ID.
- Recompensas de cualquier ItemStack custom guardadas como snapshot Base64.
- Recompensas por uno o varios comandos de consola con `{player}` y PlaceholderAPI opcional.
- Editor gráfico: `/mdvcrates editor <id>`.
- Una sola persona puede abrir cada crate a la vez y un jugador no puede abrir dos simultáneamente.
- Protección ante logout, muerte, teleport, reload/apagado: el ganador se fija y se persiste en `pending.yml` antes de consumir la llave; al entregar correctamente se elimina el pendiente.
- Animación idle con anillos 3D y grupos de orbitadores.
- Apertura con partículas de succión hacia el cofre, ItemDisplay de ruleta, desaceleración, pausa final exacta configurable (8 ticks = 0.4 s), burst y elevación del premio.
- Protección contra abrir el inventario vanilla, romper, explosiones, pistones y hoppers.

## Comandos

- `/mdvcrates create <id>` crea una definición básica.
- `/mdvcrates editor <id>` abre el editor de recompensas de ítem.
- `/mdvcrates place <id>` coloca una instancia sobre el bloque que estás mirando.
- `/mdvcrates remove` elimina la instancia física que estás mirando.
- `/mdvcrates move <id>` mueve la instancia más cercana de ese ID (máx. 12 bloques) al bloque sobre el que estás mirando.
- `/mdvcrates reload` recarga configs y animaciones.
- `/mdvcrates list` lista crates cargadas.

Permiso admin: `mdvcrates.admin`.

## Editor

El editor no consume los objetos del administrador:

- SHIFT + click sobre un objeto de tu inventario: añade una copia como recompensa.
- Click izquierdo sobre una recompensa de ítem: la quita del editor.
- Recompensas COMMAND se representan con su `preview`, pero se editan solo en `crates.yml`.
- Al cerrar se guarda automáticamente.

Si el objeto añadido es MMOItems, MDVCrates detecta su TYPE e ID usando la API de MMOItems en runtime y guarda esa referencia. Si no lo es, guarda el ItemStack completo como bytes Base64.

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

El jar sale en `target/MDVCrates-1.0.0.jar`.
