# IPTV Player (celular + TV Box)

Player de listas M3U/M3U8 para Android, feito em Kotlin com o Media3 (ExoPlayer).

## O que ele faz
- Abre listas M3U por **URL** ou **arquivo** e lembra a última lista usada
- Separa os canais por **categoria** (`group-title`), tem **busca** e mostra os **logos** (`tvg-logo`)
- Toca HLS (.m3u8), TS e MP4
- **TV Box:** aparece na tela inicial da TV, dá pra navegar com o controle remoto, e ▲▼ ou CH+/CH- trocam de canal
- **Celular:** gira para tela cheia na horizontal e mostra botões ⏮ ⏭ para trocar de canal

## Como gerar o APK
1. Instale o **Android Studio**: https://developer.android.com/studio
2. Descompacte este projeto e abra a pasta `IPTVPlayer` em **File → Open**
3. Espere o Gradle sincronizar. Na primeira vez ele baixa tudo e pode demorar. Se aparecer uma pergunta sobre o Gradle Wrapper, aceite.
4. Vá em **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
5. O APK fica em `app/build/outputs/apk/debug/app-debug.apk`

## Como instalar
- **Celular:** mande o APK para o aparelho e abra. Se pedir, permita a instalação de "fontes desconhecidas".
- **TV Box:** copie o APK para um pendrive e abra num gerenciador de arquivos, ou use o app **Downloader**. Também dá pra usar o ADB: `adb install app-debug.apk`.

## Personalizar
- **Nome do app:** `app/src/main/res/values/strings.xml` → `app_name`
- **Pacote:** `applicationId` em `app/build.gradle.kts`. Troque `com.exemplo.iptvplayer` antes de publicar.
- **Cores:** `res/values/colors.xml`
- **Ícone e banner da TV:** `res/drawable/ic_launcher.xml` e `banner.xml`. Também dá pra trocar por PNG.
- **User-Agent:** `USER_AGENT` em `Channel.kt`. Alguns servidores bloqueiam players desconhecidos.

## Próximos passos possíveis
- Favoritos e último canal assistido
- Guia de programação (EPG / XMLTV)
- Login de servidores Xtream Codes (usuário/senha)
- Várias listas salvas

## Importante
O app é só um **player**. Ele não pode vir com listas embutidas de conteúdo sem licença: a Google Play remove apps assim, e isso pode dar problema jurídico.
