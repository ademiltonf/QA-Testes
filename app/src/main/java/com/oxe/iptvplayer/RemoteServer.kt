package com.oxe.iptvplayer

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Controle pelo celular: a TV abre um pequeno site na rede Wi-Fi.
 * O celular acessa pelo QR Code e envia os comandos (setas, OK, voltar, texto...).
 */
object RemoteServer {

    private var server: ServerSocket? = null
    var port: Int = 0
        private set
    /** Código secreto no link, para ninguém da rede controlar sem o QR Code. */
    val token: String = (1..6).map { "ABCDEFGHJKMNPQRSTUVWXYZ23456789".random() }.joinToString("")

    val running: Boolean get() = server?.isClosed == false

    private lateinit var app: Context
    private val instrumentation = Instrumentation()

    @Synchronized
    fun start(ctx: Context) {
        app = ctx.applicationContext
        if (running) return
        for (p in 8090..8099) {
            try {
                server = ServerSocket(p)
                port = p
                break
            } catch (e: Exception) { /* porta ocupada, tenta a próxima */ }
        }
        val s = server ?: return
        thread(name = "oxe-remote", isDaemon = true) {
            while (!s.isClosed) {
                try {
                    val client = s.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (e: Exception) { /* servidor fechado */ }
            }
        }
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (e: Exception) { }
        server = null
    }

    /** Endereço que o celular deve abrir. */
    fun url(): String? {
        val ip = localIp() ?: return null
        return "http://$ip:$port/?t=$token"
    }

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    private fun handle(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(sock.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }
            val body = if (contentLength in 1..10_000) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = reader.read(buf, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                String(buf, 0, read)
            } else ""

            val parts = requestLine.split(" ")
            val target = parts.getOrNull(1) ?: "/"
            val path = target.substringBefore("?")
            val query = target.substringAfter("?", "").split("&").mapNotNull {
                val kv = it.split("=", limit = 2)
                if (kv.size == 2) kv[0] to URLDecoder.decode(kv[1], "UTF-8") else null
            }.toMap()

            val out = sock.getOutputStream()
            if (query["t"] != token) {
                respond(out, 403, "text/plain", "Código inválido. Leia o QR Code de novo na TV.")
                return
            }
            when (path) {
                "/" -> respond(out, 200, "text/html", page())
                "/cmd" -> respond(out, 200, "text/plain", command(query["k"].orEmpty()))
                "/text" -> respond(out, 200, "text/plain", typeText(body))
                else -> respond(out, 404, "text/plain", "Não encontrado")
            }
        }
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; else -> "Not Found" }
        val head = "HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun command(k: String): String {
        // Volume funciona mesmo com o app em segundo plano
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (k) {
            "volup" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); return "ok" }
            "voldown" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); return "ok" }
            "mute" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); return "ok" }
            "home" -> {
                app.startActivity(
                    Intent(app, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                return "ok"
            }
        }
        val key = when (k) {
            "up" -> KeyEvent.KEYCODE_DPAD_UP
            "down" -> KeyEvent.KEYCODE_DPAD_DOWN
            "left" -> KeyEvent.KEYCODE_DPAD_LEFT
            "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
            "back" -> KeyEvent.KEYCODE_BACK
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "chup" -> KeyEvent.KEYCODE_CHANNEL_UP
            "chdown" -> KeyEvent.KEYCODE_CHANNEL_DOWN
            "ffwd" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "rew" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "del" -> KeyEvent.KEYCODE_DEL
            else -> return "comando desconhecido"
        }
        return try {
            instrumentation.sendKeyDownUpSync(key)
            "ok"
        } catch (e: Exception) {
            "Abra o app na TV para usar o controle"
        }
    }

    private fun typeText(text: String): String = try {
        if (text.isNotEmpty()) instrumentation.sendStringSync(text)
        "ok"
    } catch (e: Exception) {
        "Abra o app na TV para usar o controle"
    }

    private fun page(): String = """
<!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Controle OXE</title>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent;font-family:Poppins,system-ui,sans-serif}
body{margin:0;background:radial-gradient(circle at 50% 0,#3a1a00,#0b0b0b 60%);color:#fff;min-height:100vh;display:flex;flex-direction:column;align-items:center;padding:18px 14px 30px;user-select:none}
h1{margin:4px 0 2px;font-size:22px}h1 b{color:#ff7a00}
.sub{color:#a8a8a8;font-size:13px;margin-bottom:16px}
.pad{position:relative;width:250px;height:250px;border-radius:50%;background:#1a1a1a;border:2px solid #2a2a2a;margin:8px 0 18px}
.pad button{position:absolute;width:70px;height:70px;border:0;background:none;color:#fff;font-size:28px}
.u{top:4px;left:90px}.d{bottom:4px;left:90px}.l{left:4px;top:90px}.r{right:4px;top:90px}
.ok{left:75px!important;top:75px;width:100px!important;height:100px!important;border-radius:50%;background:linear-gradient(135deg,#ff9a3c,#c85a00)!important;font-weight:700;font-size:22px!important}
.row{display:flex;gap:10px;margin:6px 0;width:100%;max-width:330px}
.row button{flex:1;height:56px;border-radius:16px;border:1px solid #333;background:#1c1c1c;color:#fff;font-size:15px;font-weight:600}
.row button.o{background:#ff7a00;border-color:#ff7a00}
button:active{transform:scale(.94);filter:brightness(1.3)}
.txt{display:flex;gap:8px;width:100%;max-width:330px;margin-top:14px}
input{flex:1;height:52px;border-radius:14px;border:1px solid #333;background:#161616;color:#fff;padding:0 14px;font-size:16px}
#msg{color:#ff9a3c;font-size:13px;min-height:18px;margin-top:10px;text-align:center}
</style></head><body>
<h1><b>OXE</b>CONTROLE</h1><div class="sub">Controle remoto da TV</div>
<div class="pad">
<button class="u" onclick="k('up')">▲</button><button class="l" onclick="k('left')">◀</button>
<button class="ok" onclick="k('ok')">OK</button>
<button class="r" onclick="k('right')">▶</button><button class="d" onclick="k('down')">▼</button>
</div>
<div class="row"><button onclick="k('back')">↩ Voltar</button><button onclick="k('home')">⌂ Início</button><button class="o" onclick="k('play')">⏯</button></div>
<div class="row"><button onclick="k('rew')">⏪</button><button onclick="k('chdown')">CH −</button><button onclick="k('chup')">CH +</button><button onclick="k('ffwd')">⏩</button></div>
<div class="row"><button onclick="k('voldown')">🔉 Vol −</button><button onclick="k('mute')">🔇</button><button onclick="k('volup')">🔊 Vol +</button></div>
<div class="txt"><input id="t" placeholder="Digitar na TV (busca, link...)"><button class="o" style="width:80px;border-radius:14px;border:0;background:#ff7a00;color:#fff;font-weight:700" onclick="send()">Enviar</button></div>
<div class="row"><button onclick="k('del')">⌫ Apagar letra</button></div>
<div id="msg"></div>
<script>
const T='$token';
function show(r){const m=document.getElementById('msg');m.textContent=(r&&r!=='ok')?r:'';}
function k(c){if(navigator.vibrate)navigator.vibrate(15);fetch('/cmd?t='+T+'&k='+c).then(r=>r.text()).then(show).catch(()=>show('Sem conexão com a TV'));}
function send(){const i=document.getElementById('t');if(!i.value)return;fetch('/text?t='+T,{method:'POST',body:i.value}).then(r=>r.text()).then(show);i.value='';}
document.getElementById('t').addEventListener('keydown',e=>{if(e.key==='Enter')send();});
</script></body></html>
""".trimIndent()
}
