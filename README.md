# SafeCareApp 🛡️📱

> **Aplicación Android nativa para la gestión centralizada de seguridad doméstica, telemetría IoT y respuesta ante emergencias.**  
> *Proyecto Final de Grado – Desarrollo de Aplicaciones Multiplataforma (DAM).*

---

## 📌 Descripción general

**SafeCareApp** es una plataforma móvil de supervisión y respuesta ante alertas de seguridad en tiempo real. Integra monitorización reactiva de sensores (puerta, movimiento, inactividad), streaming de videovigilancia emulado y gestión contextual orientada a entornos familiares (Modo Mayores, Modo Mascotas y Modo Niños), además de un sistema de activación rápida de emergencias con derivación telefónica.

Para asegurar una evaluación funcional sin requerir hardware físico propietario, el proyecto incluye una **Consola de Simulación IoT integrada** que emula los paquetes de red y el comportamiento de un puente microcontrolador (ESP32/ESP32-CAM) escribiendo telemetría en tiempo real sobre la nube.

---

## 🚀 Características principales

- **Armado multi-escenario:** Transición fluida entre *Modo Casa*, *Modo Fuera* y *Desactivado*.
- **Alertas contextuales inteligentes:** 
  - *Modo Mascotas:* Algoritmo de filtrado de falsos positivos que suprime alarmas de sensores volumétricos interiores.
  - *Modo Personas Mayores:* Detección y notificación automática ante inactividad prolongada en zonas habitadas.
  - *Modo Niños:* Supervisión de accesos y avisos de control infantil en aperturas de puertas.
- **Botón de pánico y fallback:** Confirmación con retardo de seguridad (2.8s), registro del evento y apertura automática del marcador telefónico hacia el 112 o contacto de confianza configurado.
- **Streaming de cámara:** Transmisión y renderizado de frames en base64 mediante CameraX.
- **Auditoría e historial:** Registro tipado de incidencias (`ALARMA`, `SENSOR`, `PANICO`, `SISTEMA`) con filtros por categoría.
- **Notificaciones en segundo plano:** Filtrado local y canales dedicados para avisos críticos.

---

## 🛠️ Stack tecnológico y arquitectura

- **Lenguaje:** Kotlin 2.0 (JVM target 11)
- **Arquitectura:** MVVM (Model-View-ViewModel) + Patrón Repository
- **Reactividad:** Kotlin Coroutines, StateFlow y `callbackFlow`
- **Interfaz:** Material Design 3 (Edge-to-Edge, ViewBinding, Dynamic Theming)
- **Backend & Cloud:** Firebase Realtime Database (persistencia offline activa) y Firebase Authentication
- **APIs & Hardware:** Android CameraX, Open-Meteo REST API, JUnit 4
- **Compatibilidad:** Android 8.0 (API 26) hasta Android 14+ (API 36)

```text
               ┌─────────────────────────────────────────┐
               │              Capa de Vista              │
               │   (Fragments / Activities / Material3)  │
               └────────────────────▲────────────────────┘
                                    │ repeatOnLifecycle
                                    │ StateFlow
               ┌────────────────────┴────────────────────┐
               │                ViewModel                │
               │    (Panel, Cámaras, Historial, Ajustes) │
               └─────────▲─────────────────────▲─────────┘
                         │                     │
       ReglasAlertasUi ──┘                     └── combine()
    (Lógica desacoplada)                               │
               ┌───────────────────────────────────────┴─┐
               │             Capa de Datos               │
               │  (RepositorioSistema, RepositorioAuth)  │
               └────────────────────▲────────────────────┘
                                    │ callbackFlow / Listeners
               ┌────────────────────┴────────────────────┐
               │         Firebase Cloud Backend          │
               │   (Realtime DB NoSQL / Authentication)  │
               └─────────────────────────────────────────┘
```

---

## 🧪 Pruebas unitarias

El núcleo lógico de decisión ante incidentes se encuentra aislado en la clase síncrona `ReglasAlertasUi.kt`, permitiendo una cobertura de tests unitarios al 100% en el paquete de dominio sin dependencias del framework Android:

```powershell
./gradlew testDebugUnitTest
```

- Validación de intrusión por accesos principales en Modo Casa y Fuera.
- Supresión de falsos positivos por movimiento en Modo Mascotas (`assertFalse`).
- Detección de patrones de inactividad asistencial en Modo Mayores (`assertTrue`).

---

## ⚙️ Instalación y despliegue

### Opción A: Probar la app (APK precompilado)
1. Descarga el instalador desde la sección de **[Releases](https://github.com/iselapilar2006-prog/SafeCareApp/releases)**.
2. Instala el APK en un terminal físico con Android 8.0 o superior.
3. Regístrate con una cuenta nueva desde la pantalla de bienvenida.
4. Desde *Ajustes → Consola de Simulación (TFC)* puedes interactuar con los sensores simulados.

### Opción B: Entorno de desarrollo
1. Clona el repositorio:
   ```bash
   git clone https://github.com/iselapilar2006-prog/SafeCareApp.git
   ```
2. Abre el proyecto en Android Studio (Ladybug / Hedgehog o superior).
3. Configuración de Firebase:
   - Crea un proyecto en la consola de Firebase habilitando *Authentication* y *Realtime Database*.
   - Genera tu archivo `google-services.json` basándote en la plantilla `app/google-services.json.example`.
   - Ubica el archivo en `SafeCareApp/app/google-services.json`.
4. Sincroniza las dependencias de Gradle y ejecuta el proyecto.

---

## 📄 Licencia

Este proyecto está bajo la Licencia **MIT**. Consulta el archivo `LICENSE` para más detalles.
