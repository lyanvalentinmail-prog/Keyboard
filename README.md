# Atlas Keyboard

**Atlas Keyboard** es un teclado (IME) real para Android, escrito en Kotlin, que se
registra en el sistema como método de entrada y puede usarse en cualquier aplicación:
WhatsApp, Telegram, navegadores, notas, correo…

No es una demostración ni una Activity que simula un teclado: es un
[`InputMethodService`](https://developer.android.com/reference/android/inputmethodservice/InputMethodService)
completo que el sistema reconoce en **Ajustes → Idiomas y teclados**.

100 % privado y local: sin red, sin analíticas, sin servidores.

---

## Características

### Teclado
- Distribución QWERTY con **Ñ** (español) y QWERTY en inglés.
- Teclas especiales con **iconos vectoriales** (⇧ ⇪ ⌫ ↵) teñidos según el tema activo.
- **Barra de herramientas superior** con accesos directos: ⚙ configuración, 😀 emojis, 📋 portapapeles y 🅰 fuentes de texto. La fila inferior queda limpia (`?123 , ␣ . ↵`), alineada con las filas de letras.
- **Doble espacio → punto y espacio**: al pulsar espacio dos veces seguidas tras una palabra se inserta `. ` automáticamente (como Gboard) y se activan las mayúsculas.
- **Fuentes de texto estilizadas** (Unicode, sin red): Negrita, Cursiva, Negrita cursiva, Monoespaciada, Manuscrita, Gótica, Doble trazo, Circulares y Cuadrados — se insertan mientras escribes en cualquier app, y las sugerencias/autocorrección siguen funcionando sobre el texto estilizado.
- Mayúsculas, minúsculas, bloqueo de mayúsculas (doble toque o pulsación larga en `⇧`).
- Retroceso con **repetición** al mantener pulsado (`⌫`).
- Intro/inteligente: ejecuta la acción del campo (buscar, enviar, ir…) o inserta salto de línea.
- Espacio, números y **dos páginas de símbolos** (`?123` / `ABC`, `=<` / `123`).
- Pulsación prolongada con **popup de alternativas** (acentos `á é í ó ú ü ñ ç`, dígitos en la fila QWERTY, variantes de puntuación) arrastrando el dedo para elegir.
- Vista previa de la tecla pulsada y **animaciones sutiles** de pulsación.
- Se adapta al tamaño y orientación de la pantalla (altura ajustable, modo horizontal compacto).

### Sugerencias y autocorrección
- Franja con hasta **3 sugerencias** sobre el teclado.
- Autocorrección opcional al pulsar espacio o puntuación (incluye acentuación automática: `cafe` → `café`).
- Diccionarios **locales** empaquetados en la app (`res/raw`), en español e inglés.
- Se puede desactivar por completo. **En campos de contraseña se desactivan automáticamente** sugerencias y corrección.
- Nada de lo que escribes sale del dispositivo.

### Emojis
- Panel organizado en **8 categorías** (caritas, gestos, animales, comida, deportes, viajes, objetos, símbolos) con más de 1000 emojis.
- Las pestañas de categoría usan **iconos vectoriales SVG** teñidos según el tema; la pestaña activa se resalta con el color de acento.
- Se abre desde el icono `😀` de la barra superior y se vuelve al teclado con `ABC`.

### Portapapeles
- Historial local de los últimos 25 textos copiados (accesible desde el icono `📋` de la barra superior).
- Toca un elemento para **insertarlo**, `✕` para eliminarlo o **Borrar todo** para vaciar el historial.
- Respeta el contenido marcado como sensible en Android 13+.

### Temas y personalización
- 4 temas incluidos: **Oscuro, Claro, AMOLED y Minimalista**.
- Personalización total: color de fondo, color de teclas, color del texto, radio de las teclas y tamaño del texto.
- Todo se guarda con **DataStore** y se aplica en caliente.

### Ajustes (Activity independiente)
- **Vista previa en vivo** del teclado: una miniatura se redibuja al instante con cada cambio de tema, color, radio o tamaño de texto.
- Vibrar al pulsar + intensidad de vibración.
- Sonido al pulsar.
- Mayúsculas automáticas, autocorrección, sugerencias.
- Fila numérica, altura del teclado, tema, idioma.
- Secciones separadas visualmente con divisores.
- Sección de **privacidad** que explica el procesamiento 100 % local.

---

## Requisitos

- **JDK 17** (Temurin, OpenJDK…).
- **Android SDK 35** instalado (vía Android Studio o `cmdline-tools`).
- Para instalar en dispositivo: Android 8.0+ (minSdk 24, targetSdk 35).

## Clonar el repositorio

```bash
git clone https://github.com/<tu-usuario>/<tu-repo>.git
cd Keyboard
```

## Compilar

El proyecto usa Gradle con Kotlin DSL y wrapper:

```bash
./gradlew assembleDebug
```

> La primera vez, `gradlew` descarga automáticamente `gradle-wrapper.jar` y la
> distribución de Gradle 8.10.2 (si prefieres, `gradle wrapper --gradle-version 8.10.2`
> con un Gradle previamente instalado y luego `./gradlew assembleDebug`).

El APK queda en:

```
app/build/outputs/apk/debug/app-debug.apk
```

También puedes compilar un release firmado configurando tu keystore y ejecutando
`./gradlew assembleRelease`.

### Compilación con GitHub Actions

El repositorio incluye `.github/workflows/build.yml`. En cada `push` a `main`,
cada *pull request* o manualmente (`workflow_dispatch`), el workflow:

1. Descarga el repositorio.
2. Configura JDK 17 (Temurin).
3. Configura Gradle 8.10.2 con caché.
4. Ejecuta `./gradlew assembleDebug`.
5. Publica el APK como artifact llamado **`atlas-keyboard-debug-apk`**.

Descárgalo desde la página **Actions → ejecución del workflow → Artifacts**.

## Instalar el APK

### Desde el ordenador (ADB)

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Desde el móvil

1. Copia `app-debug.apk` al dispositivo.
2. Ábrelo con un gestor de archivos.
3. Permite la instalación de aplicaciones de orígenes desconocidos si Android lo solicita.

## Activar el teclado en Android

1. Abre **Atlas Keyboard** desde el cajón de aplicaciones.
2. Pulsa **“1. Activar Atlas Keyboard”** (te lleva a *Ajustes → Administrar teclados*) y activa el interruptor de **Atlas Keyboard**. Acepta el aviso estándar del sistema (Android lo muestra para *todos* los teclados de terceros).
3. Pulsa **“2. Elegir como teclado predeterminado”** y selecciona **Atlas Keyboard**.

Ruta manual equivalente (puede variar según el fabricante):

```
Ajustes → Sistema → Idiomas y teclados → Teclado en pantalla → Administrar teclados → Atlas Keyboard
```

## Cambiar entre teclados

- Mantén pulsada la zona de navegación o toca el **icono de teclado** de la barra de navegación (abajo a la derecha) mientras un campo de texto está enfocado.
- O usa el selector “Cambiar teclado” de la notificación/ajustes rápidos que muestra Android con el teclado visible.

## Configurar el teclado

Abre la app **Atlas Keyboard**:

| Opción | Descripción |
| --- | --- |
| Vibrar al pulsar / Intensidad | Háptica por tecla (0–100 %) |
| Sonido al pulsar | Click del sistema por tecla |
| Mayúsculas automáticas | Mayúscula tras `.`, `!`, `?` o salto de línea |
| Autocorrección | Corrige al pulsar espacio o puntuación |
| Sugerencias | Muestra hasta 3 palabras |
| Fila de números | Fila extra 1–0 sobre QWERTY |
| Altura del teclado | 200–320 dp |
| Tema | Oscuro / Claro / AMOLED / Minimalista |
| Personalizar | Colores de fondo/teclas/texto, radio y tamaño de texto |
| Idioma | Español / English (distribución y diccionario) |
| Portapapeles | Ver y borrar el historial local |

## Privacidad

- Las pulsaciones se procesan **localmente en el dispositivo**.
- Sin permiso de Internet: ningún texto se envía a servidores.
- No registra contraseñas ni guarda conversaciones completas.
- El historial del portapapeles y las preferencias se guardan solo en el almacenamiento local de la app y pueden borrarse en cualquier momento.
- Sin analíticas ni rastreadores.

## Estructura del proyecto

```
├── .github/workflows/build.yml   # CI: build del APK como artifact
├── gradle/libs.versions.toml     # Catálogo de versiones
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml   # Servicio IME + Activity de ajustes
│       ├── java/com/atlas/keyboard/
│       │   ├── AtlasInputMethodService.kt   # Núcleo del IME
│       │   ├── keyboard/
│       │   │   ├── KeyboardModel.kt         # Teclas, filas, modos
│       │   │   ├── KeyboardLayouts.kt       # QWERTY es/en + símbolos
│       │   │   └── KeyboardView.kt          # Render Canvas, gestos, animaciones
│       │   ├── suggestion/
│       │   │   └── SuggestionEngine.kt      # Sugerencias/corrección local
│       │   ├── textstyle/
│       │   │   └── TextStyles.kt            # Fuentes de texto Unicode
│       │   ├── theme/
│       │   │   └── KeyboardTheme.kt         # Temas + personalización
│       │   ├── data/
│       │   │   ├── AtlasSettings.kt
│       │   │   ├── SettingsRepository.kt    # DataStore
│       │   │   └── ClipboardRepository.kt   # Historial local del portapapeles
│       │   └── ui/
│       │       ├── KeyboardToolbarView.kt   # Barra superior de accesos
│       │       ├── SuggestionStripView.kt   # Franja de sugerencias
│       │       ├── EmojiPanelView.kt        # Panel de emojis por categorías
│       │       ├── ClipboardPanelView.kt    # Panel del portapapeles
│       │       ├── TextStylePanelView.kt    # Panel de fuentes de texto
│       │       ├── ThemePreviewView.kt      # Miniatura del teclado en Ajustes
│       │       └── SettingsActivity.kt      # Configuración
│       └── res/
│           ├── xml/method.xml               # Declaración del método de entrada
│           ├── raw/dictionary_es.txt        # Diccionario español (local)
│           ├── raw/dictionary_en.txt        # Diccionario inglés (local)
│           ├── layout/activity_settings.xml
│           ├── drawable/                    # VectorDrawables: icono y teclas
│           ├── mipmap-anydpi-v26/           # Icono adaptativo (API 26+)
│           ├── mipmap-*/                    # PNG del icono generados (API 24-25)
│           └── values/…
├── tools/
│   └── generate_launcher_icons.py           # Regenera los PNG del icono
└── gradlew / gradlew.bat
```

## Tecnología

- Kotlin 2.0 · Android Gradle Plugin 8.7 · compileSdk/targetSdk 35 · minSdk 24.
- AndroidX: Core KTX, AppCompat, DataStore Preferences, Lifecycle, Corrutinas.
- Sin dependencias de red, sin servicios externos.

## Licencia

Disponible para uso personal y educativo.
