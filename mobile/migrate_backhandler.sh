#!/bin/bash
# Запускать из D:\messenger_proj\source\mobile

set -e

ROOT="$(pwd)"
COMPOSE_APP="$ROOT/composeApp/src"
SHARED="$ROOT/shared/src"

echo "=== Шаг 1: удаляем старые файлы из shared ==="
rm -f "$SHARED/commonMain/kotlin/org/messenger/app/shared/util/BackHandler.kt"
rm -f "$SHARED/androidMain/kotlin/org/messenger/app/shared/util/BackHandler.android.kt"
rm -f "$SHARED/iosMain/kotlin/org/messenger/app/shared/util/BackHandler.ios.kt"
rm -f "$SHARED/jsMain/kotlin/org/messenger/app/shared/util/BackHandler.js.kt"
rm -f "$SHARED/jvmMain/kotlin/org/messenger/app/shared/util/BackHandler.jvm.kt"

echo "=== Шаг 2: создаём папки в composeApp ==="
mkdir -p "$COMPOSE_APP/commonMain/kotlin/org/messenger/app/util"
mkdir -p "$COMPOSE_APP/androidMain/kotlin/org/messenger/app/util"
mkdir -p "$COMPOSE_APP/iosMain/kotlin/org/messenger/app/util"
mkdir -p "$COMPOSE_APP/jvmMain/kotlin/org/messenger/app/util"
mkdir -p "$COMPOSE_APP/wasmJsMain/kotlin/org/messenger/app/util"

echo "=== Шаг 3: создаём expect в commonMain ==="
cat > "$COMPOSE_APP/commonMain/kotlin/org/messenger/app/util/BackHandler.kt" <<'EOF'
package org.messenger.app.util

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
EOF

echo "=== Шаг 4: создаём actual для Android ==="
cat > "$COMPOSE_APP/androidMain/kotlin/org/messenger/app/util/BackHandler.android.kt" <<'EOF'
package org.messenger.app.util

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
EOF

echo "=== Шаг 5: создаём no-op actual для остальных платформ ==="
# Единый шаблон no-op для iOS / JVM / WasmJs
NOOP_CONTENT='package org.messenger.app.util

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
'

echo "$NOOP_CONTENT" > "$COMPOSE_APP/iosMain/kotlin/org/messenger/app/util/BackHandler.ios.kt"
echo "$NOOP_CONTENT" > "$COMPOSE_APP/jvmMain/kotlin/org/messenger/app/util/BackHandler.jvm.kt"
echo "$NOOP_CONTENT" > "$COMPOSE_APP/wasmJsMain/kotlin/org/messenger/app/util/BackHandler.wasmJs.kt"

echo "=== Шаг 6: обновляем импорты в 3 файлах ==="
FILES_TO_PATCH=(
    "$COMPOSE_APP/commonMain/kotlin/org/messenger/app/App.kt"
    "$COMPOSE_APP/commonMain/kotlin/org/messenger/app/ui/chat/ChatScreen.kt"
    "$COMPOSE_APP/commonMain/kotlin/org/messenger/app/ui/forward/ForwardTargetScreen.kt"
)

for f in "${FILES_TO_PATCH[@]}"; do
    if [ -f "$f" ]; then
        echo "  patching: $f"
        # Заменяем полный путь org.messenger.app.shared.util.PlatformBackHandler -> org.messenger.app.util.PlatformBackHandler
        sed -i 's/org\.messenger\.app\.shared\.util\.PlatformBackHandler/org.messenger.app.util.PlatformBackHandler/g' "$f"
    else
        echo "  WARNING: file not found: $f"
    fi
done

echo "=== Шаг 7: удаляем дубликат импорта в ChatComponents.kt ==="
CHAT_COMPONENTS="$COMPOSE_APP/commonMain/kotlin/org/messenger/app/ui/chat/ChatComponents.kt"
if [ -f "$CHAT_COMPONENTS" ]; then
    # Удаляем все строки "import androidx.compose.foundation.background" кроме первой
    awk '!/^import androidx\.compose\.foundation\.background$/ || !seen++' "$CHAT_COMPONENTS" > "$CHAT_COMPONENTS.tmp"
    mv "$CHAT_COMPONENTS.tmp" "$CHAT_COMPONENTS"
    echo "  deduped imports in ChatComponents.kt"
fi

echo ""
echo "=== ГОТОВО ==="
echo "Теперь в Android Studio:"
echo "  1. File -> Sync Project with Gradle Files"
echo "  2. Build -> Clean Project"
echo "  3. File -> Invalidate Caches... -> Invalidate and Restart"
echo "  4. Удалить приложение с устройства вручную"
echo "  5. Run ▶"
echo ""
echo "ВАЖНО: не забудь убрать из shared/build.gradle.kts строку:"
echo "  implementation(libs.androidx.activity.compose)  // в androidMain.dependencies"