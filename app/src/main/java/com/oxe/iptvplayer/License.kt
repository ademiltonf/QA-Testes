package com.oxe.iptvplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Licença do OXEIPTVPRO: código do aparelho, teste grátis e ativação paga. */
object License {

    private const val API = "https://waxbilfpgfdrjzhlskij.supabase.co/functions/v1/oxe"
    private const val PREFS = "oxe_license"
    private const val OFFLINE_DAYS = 7L
    private const val DAY = 86_400_000L

    data class State(
        val status: String,        // trial, active, expired, blocked
        val plan: String?,         // anual, vitalicio
        val activeUntil: Long?,
        val trialUntil: Long,
        val checkedAt: Long
    ) {
        val allowed: Boolean
            get() {
                val now = System.currentTimeMillis()
                if (status == "blocked") return false
                return (activeUntil != null && activeUntil > now) || trialUntil > now
            }
        val isPaid: Boolean
            get() = activeUntil != null && activeUntil > System.currentTimeMillis()
        val trialDaysLeft: Int
            get() = (((trialUntil - System.currentTimeMillis()) + DAY - 1) / DAY).toInt().coerceAtLeast(0)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Código fixo do aparelho, no formato de MAC (ex: A4:3F:91:7C:22:E0). */
    @SuppressLint("HardwareIds")
    fun deviceId(ctx: Context): String {
        prefs(ctx).getString("device_id", null)?.let { return it }
        val androidId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
            ?: UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("SHA-256").digest("oxe:$androidId".toByteArray())
        val id = hash.take(6).joinToString(":") { "%02X".format(it) }
        prefs(ctx).edit().putString("device_id", id).apply()
        return id
    }

    fun cached(ctx: Context): State? {
        val p = prefs(ctx)
        val status = p.getString("status", null) ?: return null
        val checkedAt = p.getLong("checked_at", 0)
        return State(
            status = status,
            plan = p.getString("plan", null),
            activeUntil = p.getLong("active_until", 0).takeIf { it > 0 },
            trialUntil = p.getLong("trial_until", 0),
            checkedAt = checkedAt
        )
    }

    /** Pode usar o app agora? (usa a última verificação, com tolerância sem internet) */
    fun allowedOffline(ctx: Context): Boolean {
        val c = cached(ctx) ?: return false
        val recent = System.currentTimeMillis() - c.checkedAt < OFFLINE_DAYS * DAY
        return c.allowed && recent
    }

    /** Consulta o servidor. Sem internet, usa a última resposta (até 7 dias). */
    fun check(ctx: Context): State {
        return try {
            val r = post(
                JSONObject()
                    .put("action", "status")
                    .put("device_id", deviceId(ctx))
                    .put("version", BuildConfigInfo.versionName(ctx))
            )
            val s = State(
                status = r.optString("status", "expired"),
                plan = r.optString("plan").nullIfBlank(),
                activeUntil = r.optLong("active_until", 0).takeIf { it > 0 },
                trialUntil = r.optLong("trial_until", 0),
                checkedAt = System.currentTimeMillis()
            )
            prefs(ctx).edit()
                .putString("status", s.status)
                .putString("plan", s.plan)
                .putLong("active_until", s.activeUntil ?: 0)
                .putLong("trial_until", s.trialUntil)
                .putLong("checked_at", s.checkedAt)
                .apply()
            s
        } catch (e: IOException) {
            val c = cached(ctx)
            if (c != null && System.currentTimeMillis() - c.checkedAt < OFFLINE_DAYS * DAY) c else throw e
        }
    }

    /** Cria o link de pagamento do plano escolhido. Retorna a URL. */
    fun checkout(ctx: Context, plan: String): String {
        val r = post(
            JSONObject()
                .put("action", "checkout")
                .put("device_id", deviceId(ctx))
                .put("plan", plan)
        )
        return r.optString("url").nullIfBlank() ?: throw IOException("Não foi possível gerar o pagamento")
    }

    private fun post(body: JSONObject): JSONObject {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", Http.USER_AGENT)
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code !in 200..299) {
                throw IOException(json.optString("error").nullIfBlank() ?: "Erro no servidor ($code)")
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    /** Gera a imagem do QR Code. */
    fun qrCode(text: String, size: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1)
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565)
    }
}

/** Versão do app (sem depender do BuildConfig). */
object BuildConfigInfo {
    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
