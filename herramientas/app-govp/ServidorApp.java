import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

// App web de GOVP para el equipo (NO es parte de la entrega). Sirve los archivos de esta carpeta y reenvía:
//   /kc/...  -> Keycloak  (por defecto http://localhost:8080)
//   /gw/...  -> gateway   (por defecto http://localhost:8081)
// El navegador habla con un solo origen, así que no hace falta configurar CORS en el backend.
// Escucha solo en 127.0.0.1.
//
// Uso, desde esta carpeta:   java ServidorApp.java
// Con otros puertos:         java ServidorApp.java 9100 http://localhost:18080 http://localhost:18081
public class ServidorApp {

    private static final Map<String, String> ARCHIVOS = Map.of(
            "/", "index.html",
            "/index.html", "index.html",
            "/app.js", "app.js",
            "/estilos.css", "estilos.css");
    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8");
    private static final Set<String> NO_COPIAR = Set.of("host", "connection", "content-length", "transfer-encoding",
            "upgrade", "expect", "origin", "referer", "accept-encoding", "cookie");
    private static final List<String> DEVOLVER = List.of("Content-Type", "X-Correlation-Id", "WWW-Authenticate");

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 9100;
        String keycloak = args.length > 1 ? args[1] : "http://localhost:8080";
        String gateway = args.length > 2 ? args[2] : "http://localhost:8081";
        Path carpeta = buscarCarpeta();

        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);
        servidor.setExecutor(Executors.newFixedThreadPool(8));

        servidor.createContext("/", ex -> {
            try (ex) {
                String ruta = ex.getRequestURI().getRawPath();
                if (ruta.startsWith("/kc/")) {
                    reenviar(ex, cliente, keycloak, ruta.substring(3));
                } else if (ruta.startsWith("/gw/")) {
                    reenviar(ex, cliente, gateway, ruta.substring(3));
                } else if (ARCHIVOS.containsKey(ruta)) {
                    String nombre = ARCHIVOS.get(ruta);
                    String ext = nombre.substring(nombre.lastIndexOf('.') + 1);
                    responder(ex, 200, TIPOS.get(ext), Files.readAllBytes(carpeta.resolve(nombre)));
                } else {
                    responder(ex, 404, "text/plain; charset=utf-8", "No existe".getBytes());
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        });
        servidor.start();
        System.out.println("App GOVP en http://localhost:" + puerto);
        System.out.println("  Keycloak: " + keycloak + "   Gateway: " + gateway);
        System.out.println("  Ctrl+C para cerrarla.");
    }

    private static Path buscarCarpeta() {
        for (Path p : List.of(Path.of("."), Path.of("herramientas", "app-govp"))) {
            if (Files.exists(p.resolve("index.html")) && Files.exists(p.resolve("app.js"))) {
                return p;
            }
        }
        throw new IllegalStateException("No encuentro index.html y app.js: corré la app desde herramientas/app-govp");
    }

    private static void reenviar(HttpExchange ex, HttpClient cliente, String destino, String ruta) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        URI uri = URI.create(destino + ruta + (query != null ? "?" + query : ""));
        byte[] cuerpo = ex.getRequestBody().readAllBytes();

        HttpRequest.Builder pedido = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(90))
                .method(ex.getRequestMethod(), cuerpo.length > 0
                        ? HttpRequest.BodyPublishers.ofByteArray(cuerpo)
                        : HttpRequest.BodyPublishers.noBody());
        ex.getRequestHeaders().forEach((nombre, valores) -> {
            if (!NO_COPIAR.contains(nombre.toLowerCase())) {
                valores.forEach(v -> pedido.header(nombre, v));
            }
        });

        try {
            HttpResponse<byte[]> r = cliente.send(pedido.build(), HttpResponse.BodyHandlers.ofByteArray());
            for (String h : DEVOLVER) {
                r.headers().firstValue(h).ifPresent(v -> ex.getResponseHeaders().add(h, v));
            }
            byte[] b = r.body();
            ex.sendResponseHeaders(r.statusCode(), b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
            }
        } catch (IOException | InterruptedException e) {
            responder(ex, 502, "application/json",
                    ("{\"title\":\"Sin conexión\",\"detail\":\"No se pudo conectar con " + destino
                            + ". ¿Está levantado el stack?\"}").getBytes());
        }
    }

    private static void responder(HttpExchange ex, int codigo, String tipo, byte[] cuerpo) throws IOException {
        ex.getResponseHeaders().add("Content-Type", tipo);
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(cuerpo);
        }
    }
}
