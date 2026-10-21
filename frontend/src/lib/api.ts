import { z } from "zod";

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  constructor(status: number, code: string) {
    super(code);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }
}

const errorSchema = z.object({ code: z.string() });
const csrfSchema = z.object({ token: z.string(), headerName: z.string() });

export async function api<T>(
  path: string,
  schema: z.ZodType<T>,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  const method = (init.method ?? "GET").toUpperCase();
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    // Never retain a token across login/logout or persist it in browser storage.
    const csrf = await api("/auth/csrf", csrfSchema, { signal: init.signal });
    headers.set(csrf.headerName, csrf.token);
    if (init.body) headers.set("Content-Type", "application/json");
  }
  const response = await fetch(`/api${path}`, {
    ...init,
    headers,
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!response.ok) {
    const error = errorSchema.safeParse(
      await response.json().catch(() => null),
    );
    if (response.status === 401 && path !== "/auth/login" && path !== "/me") {
      window.dispatchEvent(new Event("spendly:unauthorized"));
    }
    throw new ApiError(
      response.status,
      error.success ? error.data.code : "REQUEST_FAILED",
    );
  }
  return schema.parse(
    response.status === 204 ? undefined : await response.json(),
  );
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "CATEGORY_ALREADY_EXISTS")
      return "같은 유형에 이미 사용 중인 이름입니다. 삭제된 카테고리의 이름도 중복 사용할 수 없습니다.";
    if (error.code === "CATEGORY_ARCHIVED")
      return "삭제된 카테고리입니다. 다른 카테고리를 선택해 주세요.";
    if (error.code === "CATEGORY_NOT_FOUND")
      return "카테고리를 찾을 수 없습니다. 목록을 새로 확인해 주세요.";
    if (error.code === "CATEGORY_TYPE_MISMATCH")
      return "거래 유형과 같은 유형의 카테고리를 선택해 주세요.";
    if (error.status === 400) return "입력값을 확인한 뒤 다시 시도해 주세요.";
    if (error.status === 401) return "이메일 또는 비밀번호를 확인해 주세요.";
    if (error.status === 403)
      return "요청을 확인하지 못했습니다. 다시 시도해 주세요.";
    if (error.status === 429)
      return "로그인 시도가 많습니다. 잠시 후 다시 시도해 주세요.";
    if (error.status === 409)
      return "로그인 상태가 변경되었습니다. 페이지를 새로고침해 주세요.";
  }
  return "요청을 처리하지 못했습니다. 연결을 확인하고 다시 시도해 주세요.";
}
