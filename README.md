# Taller Evaluativo: Renderizado Distribuido de Fractales (Patrón Master-Worker)

**Asignatura:** Computación en Internet I (`09810 - TIC`)  
**Facultad:** Facultad de Ingeniería, Diseño y Ciencias Aplicadas  
**Departamento:** Departamento de Computación y Sistemas Inteligentes (DCSI)  
**Institución:** Universidad Icesi, Cali, Colombia  
**Tecnología:** Java 17+, ZeroC Ice 3.7.10, Slice IDL, Gradle 8.6 Multimódulo  

---

## 🎯 Propósito del Taller

Este taller evalúa el diseño y optimización de sistemas de **procesamiento distribuido** bajo el patrón **Master-Worker** y el modelo **MIMD + NORMA (Shared-Nothing)**.

A diferencia de esquemas simples de comunicación por red, este sistema resuelve el renderizado del **Conjunto de Mandelbrot**, un problema con alta densidad aritmética y **carga intrínsecamente heterogénea**:
* Las regiones exteriores del fractal escapan en 1 o 2 iteraciones (toman nanosegundos).
* El centro negro del fractal exige evaluar hasta 3.000 o 5.000 iteraciones (toma milisegundos por píxel).

El estudiante experimentará cómo las **decisiones arquitectónicas** (la estrategia de partición de tareas, el tamaño de baldosa *tileSize* y el dimensionamiento del pool de hilos) determinan si un clúster logra una aceleración real (**Speedup $S > 1$**) o colapsa por sobrecosto de red o contención de CPU.

---

## 🏛️ Arquitectura del Sistema (Flujo Desacoplado Asíncrono)

Para evitar que el Maestro mantenga hilos bloqueados hacia los Workers, el sistema opera bajo un **modelo desacoplado de extracción de trabajo (*Work-Pulling / Bag of Tasks*)**:

```text
       ┌─────────────────────────────────────────────────────────────┐
       │                       CLIENTE                               │
       │  1. Ejecuta cómputo monohilo secuencial (T_sec de control)  │
       │  2. Invoca master.renderFractal(config)                     │
       │  3. Valida resultado celda por celda contra el monolito     │
       │  4. Calcula Speedup (S), Eficiencia (E) y guarda el PNG     │
       └──────────────────────────────┬──────────────────────────────┘
                                      │ Invocación RPC
                                      ▼
       ┌─────────────────────────────────────────────────────────────┐
       │                       MAESTRO (MASTER)                      │
       │  - Aplica Patrón Strategy para dividir el fractal en tareas │
       │  - Encola las tareas en una ConcurrentLinkedQueue (Bolsa)   │
       │  - Notifica asíncronamente a los Workers registrados        │
       │  - Atiende llamadas pollTask() y reportResult()             │
       │  - Consolida incrementalmente los bloques de píxeles        │
       └──────────────▲───────────────────────────────▲──────────────┘
                      │                               │
         (1) notify   │                  (1) notify   │
         (2) pollTask │                  (2) pollTask │
         (3) report   │                  (3) report   │
                      ▼                               ▼
       ┌─────────────────────────────┐ ┌─────────────────────────────┐
       │     WORKER 1 (Worker-1)     │ │     WORKER 2 (Worker-2)     │
       │ - Recibe notify()           │ │ - Recibe notify()           │
       │ - Bucle: pollTask()         │ │ - Bucle: pollTask()         │
       │ - Cómputo local intensivo   │ │ - Cómputo local intensivo   │
       │ - master.reportResult()     │ │ - master.reportResult()     │
       │ - Repite hasta cola vacía   │ │ - Repite hasta cola vacía   │
       └─────────────────────────────┘ └─────────────────────────────┘
```

---

## 🧩 Patrón Strategy: Partición de Tareas

El Maestro delega la descomposición geométrica del fractal en implementaciones de la interfaz `TaskPartitionStrategy`:

1. **`FixedCountPartitionStrategy` (División en $N$ tareas fijas):**
   * Divide la altura total de la imagen en $N$ franjas horizontales contiguas.
   * *Comportamiento esperado:* Sencillo, pero sufre de severo desbalance de carga (*load imbalance*) cuando la franja central coincide con el cuerpo del fractal.
2. **`FixedTileSizePartitionStrategy` (División por baldosas $S \times S$):**
   * Divide la imagen en una cuadrícula de baldosas de tamaño configurable (ej. $16\times 16$, $64\times 64$, $128\times 128$).
   * *Comportamiento esperado:* Permite que el bucle dinámico de extracción mantenga a todos los workers y núcleos saturados de cómputo útil.

---

## 📁 Estructura del Proyecto

```text
fractal-master-worker/
├── settings.gradle                        # Orquestador multimódulo
├── build.gradle                           # Configuración global y plugin oficial de Slice
├── common/                                # Subproyecto de contrato y lógica compartida
│   ├── build.gradle                       # Plugin com.zeroc.gradle.ice-builder.slice
│   └── src/main/
│       ├── slice/FractalCompute.ice       # Contrato IDL (RenderConfig, TaskTile, TileResult, Interfaces)
│       └── java/co/icesi/fractal/
│           ├── core/MandelbrotCore.java   # Motor matemático y generador de PNG
│           └── strategy/                  # Patrón Strategy (FixedCount, FixedTileSize, Factory)
├── monolith/                              # Línea base secuencial en una sola CPU
│   ├── build.gradle
│   └── src/main/java/co/icesi/fractal/monolith/MonolithicMain.java
├── server/                                # Nodo Maestro
│   ├── build.gradle
│   └── src/main/java/co/icesi/fractal/master/
│       ├── MasterMain.java                # Inicialización Ice y endpoints
│       └── MasterServiceI.java            # Servant: Bolsa de tareas y consolidación incremental
├── worker/                                # Nodo Trabajador
│   ├── build.gradle
│   └── src/main/java/co/icesi/fractal/worker/
│       ├── WorkerMain.java                # Bucle de extracción (pollTask) y ThreadPool
│       └── WorkerObserverI.java           # Servant callback: recepción de notifyTasksAvailable
└── client/                                # Nodo Cliente y Banco de Pruebas
    ├── build.gradle
    └── src/main/java/co/icesi/fractal/client/
        ├── ClientMain.java                # Benchmark único con validación de píxeles y Speedup
        └── ExperimentRunner.java          # Ejecutor automatizado de la matriz experimental
```

---

## 🚀 Guía de Ejecución Rápida

### 1. Compilar todo el proyecto
```bash
./gradlew build
```

### 2. Probar la versión monolítica (Línea base)
```bash
./gradlew :monolith:runMonolith --args="1920 1080 3000 output/mandelbrot_monolith.png"
```

### 3. Iniciar el Clúster Distribuido

**Terminal 1 — Iniciar Maestro (Puerto 10000):**
```bash
./gradlew :server:runMaster --args="10000"
```

**Terminal 2 — Iniciar Worker 1 (Puerto 10001 con 4 hilos):**
```bash
./gradlew :worker:runWorker --args="10001 Worker-1 localhost 10000 4"
```

**Terminal 3 — Iniciar Worker 2 (Puerto 10002 con 4 hilos):**
```bash
./gradlew :worker:runWorker --args="10002 Worker-2 localhost 10000 4"
```

**Terminal 4 — Ejecutar Cliente de Pruebas:**
```bash
# Argumentos: <host> <puerto> <ancho> <alto> <maxIter> <estrategia: 0=franjas, 1=baldosas> <parametro> <salida>
./gradlew :client:runClient --args="localhost 10000 1920 1080 3000 1 64 output/mandelbrot_distribuido.png"
```

### 4. Ejecutar la Matriz Automatizada de Experimentos
Con el Maestro y los Workers corriendo, ejecute el generador de la tabla comparativa:
```bash
./gradlew :client:runExperiment --args="localhost 10000 1920 1080 2000"
```

El script imprimirá automáticamente una tabla en formato Markdown con las métricas de **$T_{\text{sec}}$, $T_{\text{dist}}$, $S$ y $E$** para cada combinación arquitectónica.
