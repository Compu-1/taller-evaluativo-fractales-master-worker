package co.icesi.fractal.monolith;

import co.icesi.fractal.core.MandelbrotCore;
import java.io.IOException;

/**
 * Implementacion Monolitica Secuencial del Renderizador de Mandelbrot.
 * Opera en un solo hilo sobre una sola CPU, sirviendo como linea base (T_sec).
 */
public class MonolithicMain {

    public static void main(String[] args) {
        int width = (args.length >= 1) ? Integer.parseInt(args[0]) : 1920;
        int height = (args.length >= 2) ? Integer.parseInt(args[1]) : 1080;
        int maxIter = (args.length >= 3) ? Integer.parseInt(args[2]) : 3000;
        String outputPath = (args.length >= 4) ? args[3] : "output/mandelbrot_monolith.png";

        double xMin = -2.0, xMax = 0.5;
        double yMin = -1.25, yMax = 1.25;

        System.out.println("=================================================");
        System.out.println("      RENDERIZADO MONOLITICO SECUENCIAL");
        System.out.println("=================================================");
        System.out.printf("Dimensiones: %dx%d (%d pixeles)%n", width, height, (long) width * height);
        System.out.printf("Plano Complejo: X=[%.2f, %.2f], Y=[%.2f, %.2f]%n", xMin, xMax, yMin, yMax);
        System.out.printf("Profundidad: %d iteraciones maximas por pixel%n", maxIter);
        System.out.println("Iniciando computo en monohilo...");

        long startTime = System.currentTimeMillis();

        int[][] iterations = new int[height][width];
        double dx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
        double dy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;

        for (int y = 0; y < height; y++) {
            double ci = yMin + y * dy;
            for (int x = 0; x < width; x++) {
                double cr = xMin + x * dx;
                iterations[y][x] = MandelbrotCore.computePoint(cr, ci, maxIter);
            }
        }

        long tSec = System.currentTimeMillis() - startTime;

        System.out.println("=================================================");
        System.out.printf("[MONOLITO] Tiempo Secuencial (T_sec): %d ms (%.2f s)%n", tSec, tSec / 1000.0);
        System.out.println("=================================================");

        try {
            MandelbrotCore.saveImage(iterations, width, height, maxIter, outputPath);
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar la imagen: " + e.getMessage());
        }
    }
}
