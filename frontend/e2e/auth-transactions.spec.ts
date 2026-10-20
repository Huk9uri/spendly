import { randomUUID } from "node:crypto";
import {
  test,
  expect,
  type APIRequestContext,
  type Page,
} from "@playwright/test";

// Run only against an isolated test backend; accounts and records are synthetic.
async function write(request: APIRequestContext, path: string, data?: unknown) {
  const csrf = await (await request.get("/api/auth/csrf")).json();
  const response = await request.post(`/api${path}`, {
    data,
    headers: { [csrf.headerName]: csrf.token },
  });
  expect(response.ok()).toBeTruthy();
  return response.status() === 204 ? undefined : response.json();
}
async function account(request: APIRequestContext) {
  const credentials = {
    email: `${randomUUID()}@example.invalid`,
    password: randomUUID(),
  };
  await write(request, "/auth/register", credentials);
  return credentials;
}
async function seed(page: Page) {
  const credentials = await account(page.request);
  await write(page.request, "/auth/login", credentials);
  const category = await write(page.request, "/categories", {
    name: "식비",
    type: "EXPENSE",
  });
  for (let index = 0; index < 21; index++) {
    await write(page.request, "/transactions", {
      type: "EXPENSE",
      amount: index === 0 ? 999999999999999 : 12000 + index,
      categoryId: category.id,
      occurredOn: "2026-10-21",
      description: index === 0 ? "최대 금액 확인" : `점심 기록 ${index}`,
      memo: index === 0 ? "할인%_확인" : "",
    });
  }
  await write(page.request, "/auth/logout");
  return credentials;
}
async function login(
  page: Page,
  credentials: { email: string; password: string },
) {
  await page.getByLabel("이메일", { exact: true }).fill(credentials.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(credentials.password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("heading", { name: "거래 내역" })).toBeVisible();
}

test("real session, CSRF, filters, paging, reload, logout and account isolation", async ({
  page,
}, info) => {
  const credentials = await seed(page);
  const second = await account(page.request);
  await page.goto("/transactions");
  await expect(page).toHaveURL(/\/login$/);
  await page.screenshot({
    path: `test-results/login-${info.project.name}.png`,
    fullPage: true,
  });
  await page.getByLabel("이메일", { exact: true }).fill(credentials.email);
  await page.getByLabel("비밀번호", { exact: true }).fill("incorrect password");
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("이메일 또는 비밀번호");
  await login(page, credentials);
  await expect(page.getByText("21건", { exact: true })).toBeVisible();
  await page.getByLabel("다음 페이지").click();
  await expect(page.getByText("2 / 2 페이지")).toBeVisible();
  await page.getByLabel("정렬", { exact: true }).selectOption("amount,desc");
  await expect(page.getByText("1 / 2 페이지")).toBeVisible();
  await expect(page.getByText("최대 금액 확인")).toBeVisible();
  await expect(page.getByText("−999,999,999,999,999")).toBeVisible();
  await page.getByLabel("거래 검색", { exact: true }).fill("%_");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await expect(page.getByText("1건", { exact: true })).toBeVisible();
  await expect(page.getByText("최대 금액 확인")).toBeVisible();
  await page.reload();
  await expect(page.getByText("최대 금액 확인")).toBeVisible();
  await page.screenshot({
    path: `test-results/transactions-${info.project.name}.png`,
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  expect(
    await page.evaluate(() => localStorage.length + sessionStorage.length),
  ).toBe(0);
  await page.getByLabel("유형", { exact: true }).selectOption("INCOME");
  await expect(page.getByText("표시할 거래가 없습니다.")).toBeVisible();
  await page.getByRole("button", { name: "로그아웃", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await login(page, second);
  await expect(page.getByText("표시할 거래가 없습니다.")).toBeVisible();
  await expect(page.getByText("최대 금액 확인")).toHaveCount(0);
});

test("retryable errors and expired sessions", async ({ page }) => {
  const credentials = await seed(page);
  await page.goto("/login");
  await page.route("**/api/transactions?**", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/json",
      body: '{"code":"UNAVAILABLE"}',
    }),
  );
  await login(page, credentials);
  await expect(page.getByRole("alert")).toBeVisible();
  await page.unroute("**/api/transactions?**");
  await page.getByRole("button", { name: "다시 시도" }).click();
  await expect(page.getByText("21건", { exact: true })).toBeVisible();
  await write(page.request, "/auth/logout");
  await page.getByLabel("유형", { exact: true }).selectOption("INCOME");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByText("점심 기록 1", { exact: true })).toHaveCount(0);
});
