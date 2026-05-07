import UIKit
import UserNotifications
import ComposeApp

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        requestNotificationPermission(application: application)

        // Если приложение запущено тапом по уведомлению
        if let notif = launchOptions?[.remoteNotification] as? [AnyHashable: Any] {
            handleNotificationPayload(notif)
        }
        return true
    }

    private func requestNotificationPermission(application: UIApplication) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
    }

    // APNS токен получен
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let tokenHex = deviceToken.map { String(format: "%02x", $0) }.joined()
        IosPushBridge.shared.onApnsToken(tokenHex: tokenHex)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("APNS register failed: \(error)")
    }

    // Foreground: показывать баннер
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    // Тап по уведомлению
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        handleNotificationPayload(response.notification.request.content.userInfo)
        completionHandler()
    }

    private func handleNotificationPayload(_ userInfo: [AnyHashable: Any]) {
        // Сервер шлёт chat_id и chat_name как строки в data-payload
        let chatId = (userInfo["chat_id"] as? String)
            ?? (userInfo["chatId"] as? String)
        let chatName = (userInfo["chat_name"] as? String)
            ?? (userInfo["chatName"] as? String)

        guard let id = chatId else { return }
        IosPushBridge.shared.onOpenChatString(chatId: id, chatName: chatName)
    }
}