/**
 * Phone API — pair and control Android devices connected to the Melaya runner.
 *
 * Maps to `/api/v1/private/phone/*`. Agents use these endpoints to drive a
 * paired phone (tap, type, read screen state, launch apps) via the Melaya APK.
 *
 * @example
 * ```ts
 * const { code } = await melaya.phone.pair();
 * // Show code in your app — the user enters it in the phone APK to pair.
 *
 * const devices = await melaya.phone.listDevices();
 * const tree = await melaya.phone.screenTree();
 * ```
 */
import type { HttpClient } from "./client.js";
import type { PhoneApp, PhoneDevice, PhonePairResult } from "./platform-types.js";

export class PhoneAPI {
  constructor(private readonly http: HttpClient) {}

  /** Start phone device pairing — generates a pairing code to enter on the Melaya APK. */
  async pair(): Promise<PhonePairResult> {
    return this.http.post<PhonePairResult>("/api/v1/private/phone/pair");
  }

  /** List all paired phone devices for the authenticated user. */
  async listDevices(): Promise<PhoneDevice[]> {
    const response = await this.http.get<{ devices: PhoneDevice[] }>(
      "/api/v1/private/phone/devices",
    );
    return response.devices;
  }

  /** Revoke a paired phone device by ID. */
  async revokeDevice(deviceId: string): Promise<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(
      `/api/v1/private/phone/devices/${encodeURIComponent(deviceId)}`,
    );
  }

  /** Get the current accessibility tree from the paired phone's screen. */
  async screenTree(): Promise<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>("/api/v1/private/phone/screen-tree");
  }

  /** List installed apps on the paired phone. */
  async listApps(): Promise<PhoneApp[]> {
    const response = await this.http.get<{ result?: { apps?: PhoneApp[] } }>(
      "/api/v1/private/phone/apps",
    );
    return response.result?.apps ?? [];
  }

  /**
   * Set the allowlist of apps that agents are permitted to interact with.
   * Pass an array of package names.
   */
  async setAllowedApps(packageNames: string[]): Promise<{
    ok: boolean;
    synced: boolean;
    allowedPackages: string[];
  }> {
    return this.http.put("/api/v1/private/phone/apps/allowed", {
      apps: packageNames.map((packageName) => ({ package: packageName })),
    });
  }

  /** Register the currently active pipeline run on the phone (used by agents). */
  async registerActiveRun(runId: string): Promise<{ ok: boolean }> {
    return this.http.post<{ ok: boolean }>("/api/v1/private/phone/active-run", { runId });
  }
}
