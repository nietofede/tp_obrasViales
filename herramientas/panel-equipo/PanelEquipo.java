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
import java.util.Set;

// Panel interno del equipo (NO es parte de la entrega). Sirve index.html y reenvia los pedidos del navegador:
//   /kc/...  -> Keycloak  (por defecto http://localhost:8080)
//   /gw/...  -> gateway   (por defecto http://localhost:8081)
// Asi el navegador habla con un solo origen (este servidor) y no hacen falta reglas de CORS en el backend.
// Escucha solo en 127.0.0.1: no se ve desde otras maquinas.
//
// Uso, desde esta carpeta:   java PanelEquipo.java
// Con otros puertos:         java PanelEquipo.java 9090 http://localhost:18080 http://localhost:18081
public class PanelEquipo {

    // encabezados que no se copian: los maneja el propio cliente HTTP
    private static final Set<String> NO_COPIAR = Set.of("host", "connection", "content-length", "transfer-encoding",
            "upgrade", "expect", "origin", "referer", "accept-encoding");
    private static final List<String> RESPUESTA = List.of("Content-Type", "X-Correlation-Id", "WWW-Authenticate");

    public static void main(String[] args) throws IOException {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : 9090;
        String keycloak = args.length > 1 ? args[1] : "http://localhost:8080";
        String gateway = args.length > 2 ? args[2] : "http://localhost:8081";
        Path pagina = buscarPagina();

        HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", puerto), 0);

        servidor.createContext("/", ex -> {
            try (ex) {
                String ruta = ex.getRequestURI().getRawPath();
                if (ruta.startsWith("/kc/")) {
                    reenviar(ex, cliente, keycloak, ruta.substring(3));
                } else if (ruta.startsWith("/gw/")) {
                    reenviar(ex, cliente, gateway, ruta.substring(3));
                } else if (ruta.equals("/config")) {
                    responder(ex, 200, "application/json",
                            ("{\"keycloak\":\"" + keycloak + "\",\"gateway\":\"" + gateway + "\"}").getBytes());
                } else if (ruta.equals("/") || ruta.equals("/index.html")) {
                    responder(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(pagina));
                } else {
                    responder(ex, 404, "text/plain", "No existe".getBytes());
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        });
        servidor.start();
        System.out.println("Panel del equipo en http://localhost:" + puerto);
        System.out.println("  Keycloak: " + keycloak + "   Gateway: " + gateway);
        System.out.println("  Ctrl+C para cerrarlo.");
    }

    private static Path buscarPagina() {
        for (Path p : List.of(Path.of("index.html"), Path.of("herramientas", "panel-equipo", "index.html"))) {
            if (Files.exists(p)) {
                return p;
            }
        }
        throw new IllegalStateException("No encuentro index.html: corré el panel desde herramientas/panel-equipo");
    }

    private static void reenviar(HttpExchange ex, HttpClient cliente, String destino, String ruta) throws IOException {
        String query = ex.getRequestURI().getRawQuery();
        URI uri = URI.create(destino + ruta + (query != null ? "?" + query : ""));
        byte[] cuerpo = ex.getRequestBody().readAllBytes();

        HttpRequest.Builder pedido = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
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
            for (String h : RESPUESTA) {
                r.headers().firstValue(h).ifPresent(v -> ex.getResponseHeaders().add(h, v));
            }
            ex.getResponseHeaders().add("Access-Control-Expose-Headers", String.join(",", RESPUESTA));
            byte[] b = r.body();
            ex.sendResponseHeaders(r.statusCode(), b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(b);
                }
            }
        } catch (IOException | InterruptedException e) {
            responder(ex, 502, "application/json",
                    ("{\"panel\":\"No se pudo conectar con " + destino + ". ¿Está levantado el stack?\"}").getBytes());
        }
    }

    private static void responder(HttpExchange ex, int codigo, String tipo, byte[] cuerpo) throws IOException {
        ex.getResponseHeaders().add("Content-Type", tipo);
        ex.sendResponseHeaders(codigo, cuerpo.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(cuerpo);
        }
    }
}
