import { useEffect, useState } from "react";

export type AnalysisNotificationTone = "progress" | "success" | "warning" | "error";

export interface AnalysisNotification {
  id: string;
  title: string;
  message: string;
  tone: AnalysisNotificationTone;
  final?: boolean;
}

function NotificationCard({
  notification,
  onDismiss,
}: {
  notification: AnalysisNotification;
  onDismiss: (id: string) => void;
}) {
  const [exiting, setExiting] = useState(false);
  useEffect(() => {
    if (!notification.final) return;
    const exitTimer = window.setTimeout(() => setExiting(true), 2500);
    const removeTimer = window.setTimeout(() => onDismiss(notification.id), 2850);
    return () => {
      window.clearTimeout(exitTimer);
      window.clearTimeout(removeTimer);
    };
  }, [notification.final, notification.id, onDismiss]);

  return (
    <li className={`analysis-notification analysis-notification-${notification.tone}${exiting ? " is-exiting" : ""}`}>
      <span className="analysis-notification-indicator" aria-hidden="true">
        {notification.tone === "progress" ? <span className="spinner" /> : notification.tone === "success" ? "✓" : "!"}
      </span>
      <div className="analysis-notification-copy">
        <strong>{notification.title}</strong>
        <span>{notification.message}</span>
      </div>
    </li>
  );
}

export function AnalysisNotificationCenter({
  notifications,
  announcement,
  onDismiss,
}: {
  notifications: AnalysisNotification[];
  announcement: string;
  onDismiss: (id: string) => void;
}) {
  if (notifications.length === 0) return null;

  return (
    <aside className="analysis-notification-center" aria-label="Notificações da análise">
      <ol>
        {notifications.slice(0, 3).map((notification) => (
          <NotificationCard key={notification.id} notification={notification} onDismiss={onDismiss} />
        ))}
      </ol>
      <span className="visually-hidden" aria-live="polite" aria-atomic="true">{announcement}</span>
    </aside>
  );
}
