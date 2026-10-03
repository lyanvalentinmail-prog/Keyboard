# ProGuard rules for Atlas Keyboard.
# El build debug/release no usa minificación por defecto; este archivo queda listo
# para habilitar R8 en el futuro.

# Mantener el servicio de método de entrada siempre accesible para el sistema.
-keep class com.atlas.keyboard.AtlasInputMethodService { *; }
-keep class com.atlas.keyboard.ui.SettingsActivity { *; }
