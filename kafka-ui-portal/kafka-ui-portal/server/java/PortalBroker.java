import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** A loopback shutdown channel gives Kafka a normal JVM shutdown hook on Windows too. */
class PortalBroker {
  public static void main(String[] args) throws Exception {
    if (args.length != 3) throw new IllegalArgumentException("Expected config, control port and token");
    String token = args[2];
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[1])), 0);
    server.createContext("/", exchange -> {
      String authorization = exchange.getRequestHeaders().getFirst("Authorization");
      boolean allowed = authorization != null && MessageDigest.isEqual(
          authorization.getBytes(StandardCharsets.UTF_8), ("Bearer " + token).getBytes(StandardCharsets.UTF_8));
      if (!allowed) { exchange.sendResponseHeaders(403, -1); exchange.close(); return; }
      boolean stop = exchange.getRequestURI().getPath().equals("/shutdown") && exchange.getRequestMethod().equals("POST");
      if (!stop && !exchange.getRequestURI().getPath().equals("/health")) { exchange.sendResponseHeaders(404, -1); exchange.close(); return; }
      byte[] body = ("{\"pid\":" + ProcessHandle.current().pid() + "}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(stop ? 202 : 200, body.length);
      exchange.getResponseBody().write(body); exchange.close();
      if (stop) new Thread(() -> System.exit(0), "portal-graceful-shutdown").start();
    });
    server.start();
    try { Class.forName("kafka.Kafka").getMethod("main", String[].class).invoke(null, (Object) new String[]{args[0]}); }
    catch (Throwable failure) { server.stop(0); failure.printStackTrace(); System.exit(1); }
  }
}
