# Мои места — Android v3.0.3

Исправление сборки GitHub Actions.

## Что изменено в 3.0.3
- Удалена ошибочная проверка конкретной папки `platforms/android-37`, из-за которой падал запуск #5 до начала Gradle-сборки.
- Явно настраивается JDK 17.
- Android SDK определяется через `ANDROID_HOME`, `ANDROID_SDK_ROOT` или стандартный путь GitHub runner.
- Создаётся `local.properties` с корректным `sdk.dir`.
- Перед сборкой выводятся доступные Android platforms/build-tools, но их точные имена больше не блокируют workflow.
- Gradle 9.6 запускает `:app:assembleDebug` и готовый APK загружается как Artifact.

После загрузки проекта в GitHub откройте **Actions → Build Android APK**. Успешная сборка даст Artifact `MoiMesta-APK-v3.0.3` с файлом `MoiMesta-v3.0.3.apk`.
