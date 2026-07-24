/**
 * Assistant API — get and save the caller's onboarding / persona profile.
 *
 * Maps to `/api/v1/private/assistant/profile`. The profile is stored
 * envelope-encrypted (service=`assistant_profile`) and used to personalise
 * Friday / the in-app assistant experience.
 *
 * @example
 * ```ts
 * const profile = await melaya.assistant.getProfile();
 * await melaya.assistant.setProfile({ name: "Antoine", goals: ["grow my trading edge"] });
 * ```
 */
import type { HttpClient } from "./client.js";
import type { AssistantProfile } from "./platform-types.js";

export class AssistantAPI {
  constructor(private readonly http: HttpClient) {}

  /** Get the caller's assistant onboarding profile. */
  async getProfile(): Promise<AssistantProfile> {
    return this.http.get<AssistantProfile>("/api/v1/private/assistant/profile");
  }

  /** Save the caller's assistant onboarding profile. */
  async setProfile(profile: AssistantProfile): Promise<{ ok: boolean }> {
    return this.http.put<{ ok: boolean }>("/api/v1/private/assistant/profile", profile);
  }
}
