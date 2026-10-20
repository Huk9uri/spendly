import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import { api, ApiError, errorMessage } from "./api";
import {
  credentialsSchema,
  transactionSchema,
  transactionSearch,
} from "./contracts";

afterEach(() => vi.restoreAllMocks());
describe("API security and contracts", () => {
  it("fetches a fresh CSRF token for every write and sends session cookies", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        Response.json({ token: "first", headerName: "X-CSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(
        Response.json({ token: "rotated", headerName: "X-CSRF-TOKEN" }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetch);
    await api("/auth/logout", z.undefined(), { method: "POST" });
    await api("/auth/logout", z.undefined(), { method: "POST" });
    expect(fetch.mock.calls[1][1].headers.get("X-CSRF-TOKEN")).toBe("first");
    expect(fetch.mock.calls[3][1].headers.get("X-CSRF-TOKEN")).toBe("rotated");
    expect(fetch.mock.calls[1][1].credentials).toBe("same-origin");
    expect(localStorage.length).toBe(0);
  });
  it("notifies expired sessions but treats invalid login credentials separately", async () => {
    const expired = vi.fn();
    window.addEventListener("spendly:unauthorized", expired);
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          Response.json({ code: "UNAUTHORIZED" }, { status: 401 }),
        ),
    );
    await expect(api("/transactions", z.unknown())).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(expired).toHaveBeenCalledTimes(1);
    await expect(api("/auth/login", z.unknown())).rejects.toBeInstanceOf(
      ApiError,
    );
    expect(expired).toHaveBeenCalledTimes(1);
    window.removeEventListener("spendly:unauthorized", expired);
  });
  it("handles non-JSON proxy errors without exposing their contents", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response("private upstream detail", { status: 502 }),
        ),
    );
    const failure = await api("/me", z.unknown()).catch(
      (error: unknown) => error,
    );
    expect(errorMessage(failure)).not.toContain("private");
    expect(failure).toMatchObject({ status: 502, code: "REQUEST_FAILED" });
  });
  it("normalizes email while preserving password spaces and case", () => {
    const value = credentialsSchema.parse({
      email: "  Person@Example.com ",
      password: " Mixed Secret 123 ",
    });
    expect(value.email).toBe("person@example.com");
    expect(value.password).toBe(" Mixed Secret 123 ");
    expect(
      credentialsSchema.safeParse({
        email: "a@b.com",
        password: "            ",
      }).success,
    ).toBe(false);
  });
  it("rejects fractional and unsafe currency from the server", () => {
    const base = {
      id: "cc1dc707-7a72-4780-a83b-904ab40b8dab",
      categoryId: "cc1dc707-7a72-4780-a83b-904ab40b8dab",
      type: "EXPENSE",
      occurredOn: "2026-10-21",
      description: "test",
      memo: "",
      version: 0,
    };
    for (const amount of [
      1.5,
      0,
      Number.MAX_SAFE_INTEGER + 1,
      1_000_000_000_000_000,
    ]) {
      expect(transactionSchema.safeParse({ ...base, amount }).success).toBe(
        false,
      );
    }
    expect(
      transactionSchema.safeParse({ ...base, amount: 999_999_999_999_999 })
        .success,
    ).toBe(true);
  });
  it("encodes literal search and never sends a client owner identifier", () => {
    const query = new URLSearchParams(
      transactionSearch(
        new URLSearchParams({ q: "%_한글&", user_id: "forged", page: "2" }),
      ),
    );
    expect(query.get("q")).toBe("%_한글&");
    expect(query.get("page")).toBe("2");
    expect(query.get("size")).toBe("20");
    expect(query.has("user_id")).toBe(false);
  });
});
