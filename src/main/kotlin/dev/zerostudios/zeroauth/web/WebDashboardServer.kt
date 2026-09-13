package dev.zerostudios.zeroauth.web

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.auth.AuthManager
import org.bukkit.Bukkit
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WebDashboardServer(
    private val plugin: ZeroAuthPlugin,
    private val manager: AuthManager
) {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    fun start() {
        if (!plugin.config.getBoolean("web-dashboard.enabled", false)) return
        val token = plugin.config.getString("web-dashboard.token", "change-me")?.trim().orEmpty()
        if (token.isEmpty()) {
            plugin.logger.severe("Web dashboard is disabled because web-dashboard.token is empty.")
            return
        }
        try {
            val host = plugin.config.getString("web-dashboard.host", "127.0.0.1") ?: "127.0.0.1"
            val port = plugin.config.getInt("web-dashboard.port", 8765)
            val created = HttpServer.create(InetSocketAddress(host, port), 0)
            created.createContext("/") { exchange -> handle(exchange) }
            executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "ZeroAuth-WebDashboard").apply { isDaemon = true }
            }
            created.executor = executor
            created.start()
            server = created
            plugin.logger.info("Web dashboard listening on http://$host:$port/dashboard")
        } catch (exception: Exception) {
            plugin.logger.severe("Could not start the web dashboard: ${exception.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
    }

    fun restart() {
        stop()
        start()
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestURI.path == "/" && exchange.requestMethod == "GET") {
                exchange.responseHeaders.add("Location", "/dashboard")
                exchange.sendResponseHeaders(302, -1)
                return
            }
            if (!authorized(exchange)) {
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}", "application/json; charset=utf-8")
                return
            }
            when {
                exchange.requestURI.path == "/dashboard" && exchange.requestMethod == "GET" ->
                    respond(exchange, 200, dashboardHtml(), "text/html; charset=utf-8")
                exchange.requestURI.path == "/api/status" && exchange.requestMethod == "GET" ->
                    respond(exchange, 200, statusJson(), "application/json; charset=utf-8")
                exchange.requestURI.path == "/api/reload" && exchange.requestMethod == "POST" -> {
                    sync { plugin.reloadPluginConfiguration() }
                    respond(exchange, 200, "{\"success\":true}", "application/json; charset=utf-8")
                }
                exchange.requestURI.path == "/api/reset-password" && exchange.requestMethod == "POST" ->
                    resetPassword(exchange)
                else -> respond(exchange, 404, "{\"error\":\"Not found\"}", "application/json; charset=utf-8")
            }
        } catch (exception: Exception) {
            plugin.logger.warning("Web dashboard request failed: ${exception.message}")
            if (exchange.responseHeaders["Content-Type"].isNullOrEmpty()) {
                respond(exchange, 500, "{\"error\":\"Internal server error\"}", "application/json; charset=utf-8")
            }
        } finally {
            exchange.close()
        }
    }

    private fun authorized(exchange: HttpExchange): Boolean {
        val configured = plugin.config.getString("web-dashboard.token", "")?.toByteArray(StandardCharsets.UTF_8)
            ?: return false
        val queryToken = exchange.requestURI.rawQuery.orEmpty()
            .split('&')
            .firstOrNull { it.substringBefore('=').equals("token", true) }
            ?.substringAfter('=', "")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
        val provided = exchange.requestHeaders.getFirst("X-ZeroAuth-Token") ?: queryToken ?: return false
        return MessageDigest.isEqual(configured, provided.toByteArray(StandardCharsets.UTF_8))
    }

    private fun statusJson(): String = sync {
        Bukkit.getOnlinePlayers().joinToString(prefix = "[", postfix = "]") { player ->
            "{" +
                "\"name\":\"${json(player.name)}\"," +
                "\"registered\":${manager.isRegistered(player.uniqueId)}," +
                "\"authenticated\":${manager.isAuthenticated(player.uniqueId)}," +
                "\"world\":\"${json(player.world.name)}\"," +
                "\"email\":${manager.email(player.uniqueId)?.let { "\"${json(it)}\"" } ?: "null"}" +
                "}"
        }
    }

    private fun resetPassword(exchange: HttpExchange) {
        val form = parseForm(exchange.requestBody.readBytes().toString(StandardCharsets.UTF_8))
        val playerName = form["player"].orEmpty()
        val password = form["password"].orEmpty()
        if (playerName.isBlank() || password.isBlank()) {
            respond(exchange, 400, "{\"error\":\"player and password are required\"}", "application/json; charset=utf-8")
            return
        }
        val result = sync {
            val player = Bukkit.getPlayerExact(playerName) ?: return@sync AuthManager.ResetPasswordResult.NOT_REGISTERED
            manager.resetPassword(player.uniqueId, password)
        }
        when (result) {
            AuthManager.ResetPasswordResult.SUCCESS -> respond(exchange, 200, "{\"success\":true}", "application/json; charset=utf-8")
            AuthManager.ResetPasswordResult.NOT_REGISTERED -> respond(exchange, 404, "{\"error\":\"Player is not registered or is offline\"}", "application/json; charset=utf-8")
            AuthManager.ResetPasswordResult.INVALID_PASSWORD -> respond(exchange, 400, "{\"error\":\"Password does not meet the configured minimum length\"}", "application/json; charset=utf-8")
        }
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&')
        .filter { it.isNotEmpty() }
        .associate {
            URLDecoder.decode(it.substringBefore('='), StandardCharsets.UTF_8) to
                URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8)
        }

    private fun dashboardHtml(): String = """
        <!doctype html>
        <html><head><meta charset="utf-8"><title>ZeroAuth Admin</title>
        <style>body{font-family:Arial,sans-serif;background:#111827;color:#e5e7eb;margin:2rem}table{border-collapse:collapse;width:100%;background:#1f2937}th,td{padding:.7rem;border-bottom:1px solid #374151;text-align:left}button{padding:.5rem .8rem;margin:.3rem;background:#2563eb;color:#fff;border:0;border-radius:4px;cursor:pointer}input{padding:.5rem;background:#374151;color:#fff;border:1px solid #4b5563;border-radius:4px}</style>
        </head><body><h1>ZeroAuth Admin Dashboard</h1>
        <p><button onclick="loadStatus()">Refresh</button> <button onclick="reloadPlugin()">Reload configuration</button></p>
        <form onsubmit="resetPassword(event)"><input id="player" placeholder="Player" required><input id="password" type="password" placeholder="New password" required><button>Reset password</button></form>
        <p id="message"></p><table><thead><tr><th>Player</th><th>Registered</th><th>Authenticated</th><th>World</th><th>Email</th></tr></thead><tbody id="players"></tbody></table>
        <script>
        const token=new URLSearchParams(location.search).get('token')||prompt('Dashboard token');
        const headers={'X-ZeroAuth-Token':token,'Content-Type':'application/x-www-form-urlencoded'};
        async function loadStatus(){const r=await fetch('/api/status',{headers});const data=await r.json();document.getElementById('players').innerHTML=data.map(p=>`<tr><td>${'$'}{p.name}</td><td>${'$'}{p.registered}</td><td>${'$'}{p.authenticated}</td><td>${'$'}{p.world}</td><td>${'$'}{p.email||''}</td></tr>`).join('');}
        async function reloadPlugin(){const r=await fetch('/api/reload',{method:'POST',headers});document.getElementById('message').textContent=(await r.json()).success?'Configuration reloaded.':'Reload failed.';loadStatus();}
        async function resetPassword(e){e.preventDefault();const body=new URLSearchParams({player:player.value,password:password.value});const r=await fetch('/api/reset-password',{method:'POST',headers,body});document.getElementById('message').textContent=(await r.json()).error||'Password reset.';}
        loadStatus();
        </script></body></html>
    """.trimIndent()

    private fun respond(exchange: HttpExchange, status: Int, body: String, contentType: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun json(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun <T> sync(task: () -> T): T = if (Bukkit.isPrimaryThread()) {
        task()
    } else {
        Bukkit.getScheduler().callSyncMethod(plugin, Callable { task() }).get()
    }
}