import type { HttpClient } from "./client.js";

export type BugResult = Record<string, unknown>;

/** User-facing bug reports, comments, and notifications. */
export class BugsAPI {
  constructor(private readonly http: HttpClient) {}

  create(body: BugResult): Promise<BugResult> {
    return this.http.post("/api/v1/private/bugs", body);
  }

  listMine(): Promise<BugResult[]> {
    return this.http.get("/api/v1/private/bugs/mine");
  }

  get(bugId: string): Promise<BugResult> {
    return this.http.get(`/api/v1/private/bugs/${encodeURIComponent(bugId)}`);
  }

  addComment(bugId: string, comment: string): Promise<BugResult> {
    return this.http.post(`/api/v1/private/bugs/${encodeURIComponent(bugId)}/comments`, { comment });
  }

  listNotifications(): Promise<BugResult[]> {
    return this.http.get("/api/v1/private/bugs/notifications");
  }

  markNotificationsRead(notificationIds?: string[]): Promise<BugResult> {
    return this.http.post(
      "/api/v1/private/bugs/notifications/read",
      notificationIds ? { notificationIds } : undefined,
    );
  }
}
