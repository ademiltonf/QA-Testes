package com.oxe.iptvplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Começa junto com o app: religa o controle pelo celular, os lembretes e configura as capas. */
class OxeApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
        if (Prefs.remoteEnabled(this)) RemoteServer.start(this)
    }

    /**
     * Carregador das capas e logos:
     * - se identifica como navegador (alguns servidores bloqueiam apps desconhecidos);
     * - não usa o modo "hardware" (algumas TV Box mostram as imagens em branco);
     * - guarda as capas no aparelho para abrir mais rápido.
     */
    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept", "image/avif,image/webp,image/*,*/*;q=0.8")
                        .build()
                )
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .allowHardware(false)
            .respectCacheHeaders(false)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("capas"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}
