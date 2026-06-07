package org.messenger.app.updates

import org.messenger.app.shared.data.local.TokenStorage
import org.messenger.app.shared.data.model.UpdateInfo
import org.messenger.app.shared.domain.repository.UpdateRepository

class UpdateChecker(
    private val repository: UpdateRepository,
    private val tokenStorage: TokenStorage,
    private val isAdmin: Boolean,
) {
    /**
     * Возвращает UpdateInfo, если есть обновление И его пора снова показывать
     * (учёт «отложил»: сутки для юзера, 5 мин для админа).
     */
    suspend fun check(force: Boolean = false): UpdateInfo? {
        val info = repository.checkForUpdate(
            platform = PlatformUpdater.platformId(),
            currentVersionCode = PlatformUpdater.currentVersionCode(),
        ) ?: return null

        if (force) return info

        val now = currentTimeMillis()
        val lastShown = tokenStorage.getUpdateLastCheckMs()
        val remindInterval = if (isAdmin)
            UpdateConfig.REMIND_INTERVAL_ADMIN_MS
        else
            UpdateConfig.REMIND_INTERVAL_USER_MS

        // если ранее откладывали — не показываем до истечения интервала
        if (lastShown != 0L && now - lastShown < remindInterval) return null
        return info
    }

    /** Пользователь нажал «Позже» — фиксируем время, чтобы не дёргать снова. */
    fun postpone() {
        tokenStorage.setUpdateLastCheckMs(currentTimeMillis())
    }
}