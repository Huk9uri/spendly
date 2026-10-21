import { randomUUID } from "node:crypto";
import { test, expect, type Page } from "@playwright/test";

async function signIn(page: Page) {
  const credentials = {
    email: `${randomUUID()}@example.invalid`,
    password: randomUUID(),
  };
  const csrf = await (await page.request.get("/api/auth/csrf")).json();
  const registered = await page.request.post("/api/auth/register", {
    data: credentials,
    headers: { [csrf.headerName]: csrf.token },
  });
  expect(registered.status()).toBe(201);
  await page.goto("/login");
  await page.getByLabel("이메일", { exact: true }).fill(credentials.email);
  await page.getByLabel("비밀번호", { exact: true }).fill(credentials.password);
  await page.getByRole("button", { name: "로그인", exact: true }).click();
  await expect(page.getByRole("heading", { name: "거래 내역" })).toBeVisible();
}

test("create categories, rename, validate and register the first transaction once", async ({
  page,
}, info) => {
  await signIn(page);
  await page.getByRole("link", { name: "카테고리 관리", exact: true }).click();
  await page.getByLabel("카테고리 이름").fill("식비");
  await page.getByRole("button", { name: "카테고리 추가" }).click();
  const row = page.getByRole("listitem", {
    name: "식비 카테고리",
    exact: true,
  });
  await row.getByRole("button", { name: "이름 변경" }).click();
  await row.getByLabel("새 이름").fill("외식비");
  await row.getByRole("button", { name: "이름 저장" }).click();
  await expect(
    page.getByRole("listitem", { name: "외식비 카테고리", exact: true }),
  ).toBeVisible();
  await page.getByLabel("카테고리 이름").fill("외식비");
  await page.getByRole("button", { name: "카테고리 추가" }).click();
  await expect(page.getByRole("alert")).toContainText("이미 사용 중인 이름");
  await expect(page.getByLabel("카테고리 이름")).toHaveValue("외식비");
  await page.getByLabel("카테고리 이름").fill("급여");
  await page.getByLabel("카테고리 유형").selectOption("INCOME");
  await page.getByRole("button", { name: "카테고리 추가" }).click();
  await expect(
    page.getByRole("listitem", { name: "급여 카테고리", exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: `test-results/categories-${info.project.name}.png`,
    fullPage: true,
  });
  await page.getByRole("link", { name: "거래 등록으로 이동" }).click();
  await page
    .getByLabel("카테고리", { exact: true })
    .selectOption({ label: "외식비" });
  await page.getByLabel("유형", { exact: true }).selectOption("INCOME");
  await expect(page.getByLabel("카테고리", { exact: true })).toHaveValue("");
  await expect(page.getByRole("option", { name: "급여" })).toHaveCount(1);
  await expect(page.getByRole("option", { name: "외식비" })).toHaveCount(0);
  await page.getByLabel("유형", { exact: true }).selectOption("EXPENSE");
  await page
    .getByLabel("카테고리", { exact: true })
    .selectOption({ label: "외식비" });
  await page.getByLabel("금액 (원)").fill("1e3");
  await page.getByLabel("발생일").fill("2026-10-21");
  await page.getByLabel("설명", { exact: true }).fill("첫 거래 점심");
  await page.getByRole("button", { name: "거래 저장" }).click();
  await expect(
    page.getByText("금액은 숫자로 된 정수만 입력해 주세요."),
  ).toBeVisible();
  await page.getByLabel("금액 (원)").fill("999999999999999");
  await page.screenshot({
    path: `test-results/transaction-new-${info.project.name}.png`,
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  let posts = 0;
  page.on("request", (request) => {
    if (
      new URL(request.url()).pathname === "/api/transactions" &&
      request.method() === "POST"
    )
      posts++;
  });
  await page.locator("form").evaluate((form: HTMLFormElement) => {
    form.requestSubmit();
    form.requestSubmit();
  });
  await expect(page).toHaveURL(/\/transactions$/);
  await expect(page.getByText("거래를 등록했습니다.")).toBeVisible();
  await expect(page.getByText("첫 거래 점심", { exact: true })).toBeVisible();
  await expect(page.getByText("1건", { exact: true })).toBeVisible();
  expect(posts).toBe(1);
  const stored = await (
    await page.request.get("/api/transactions?size=20")
  ).json();
  expect(stored.content[0].memo).toBe("");
  expect(stored.content[0].occurredOn).toBe("2026-10-21");
  expect(stored.content[0].amount).toBe(999999999999999);
});

test("deleted categories keep existing transactions and cannot be selected for new ones", async ({
  page,
}) => {
  await signIn(page);
  await page.getByRole("link", { name: "거래 등록", exact: true }).click();
  await expect(
    page.getByText(/선택할 수 있는 지출 카테고리가 없습니다/),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "거래 저장" })).toBeDisabled();
  await page.goto("/settings/categories");
  await page.getByLabel("카테고리 이름").fill("기존 식비");
  await page.getByRole("button", { name: "카테고리 추가" }).click();
  await expect(
    page.getByRole("listitem", { name: "기존 식비 카테고리" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "거래 등록으로 이동" }).click();
  await page.getByLabel("금액 (원)").fill("12000");
  await page
    .getByLabel("카테고리", { exact: true })
    .selectOption({ label: "기존 식비" });
  await page.getByLabel("설명", { exact: true }).fill("삭제 전 거래");
  await page.getByRole("button", { name: "거래 저장" }).click();
  await expect(page.getByText("삭제 전 거래", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "카테고리 관리", exact: true }).click();
  const row = page.getByRole("listitem", {
    name: "기존 식비 카테고리",
    exact: true,
  });
  await row.getByRole("button", { name: "삭제", exact: true }).click();
  await row.getByRole("button", { name: "취소", exact: true }).click();
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "삭제", exact: true }).click();
  await row.getByRole("button", { name: "삭제 확인", exact: true }).click();
  await expect(row).toHaveCount(0);
  await page.getByLabel("삭제된 카테고리 포함").check();
  await expect(row.getByText("지출 · 삭제됨")).toBeVisible();
  await row.getByRole("button", { name: "이름 변경" }).click();
  await row.getByLabel("새 이름").fill("지난 식비");
  await row.getByRole("button", { name: "이름 저장" }).click();
  await expect(
    page.getByRole("listitem", { name: "지난 식비 카테고리", exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "거래 내역으로" }).click();
  await expect(page.getByText("삭제 전 거래", { exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "거래 목록", exact: true }).getByText(/지난 식비 · 지출/)).toBeVisible();
  await page.getByRole("link", { name: "거래 등록", exact: true }).click();
  await expect(page.getByRole("option", { name: "지난 식비" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "거래 저장" })).toBeDisabled();
});
