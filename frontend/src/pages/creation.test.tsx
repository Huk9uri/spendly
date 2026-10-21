import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { TransactionNewPage } from "./transaction-new";
import { CategoriesPage } from "./categories";
import { createQueryClient } from "@/lib/query-client";

const user = {
  id: "cc1dc707-7a72-4780-a83b-904ab40b8dab",
  email: "test@example.invalid",
};
const expense = {
  id: "5211b3ec-5120-4c1a-b001-90e5d20c27bb",
  name: "식비",
  type: "EXPENSE",
  archived: false,
};
const income = {
  ...expense,
  id: "6111b3ec-5120-4c1a-b001-90e5d20c27bb",
  name: "급여",
  type: "INCOME",
};
const archived = {
  ...expense,
  id: "7111b3ec-5120-4c1a-b001-90e5d20c27bb",
  name: "옛날",
  archived: true,
};
function mount(page: "new" | "categories") {
  const client = createQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter
        initialEntries={[
          page === "new" ? "/transactions/new" : "/settings/categories",
        ]}
      >
        <Routes>
          <Route
            path="/transactions/new"
            element={<TransactionNewPage user={user} />}
          />
          <Route
            path="/settings/categories"
            element={<CategoriesPage user={user} />}
          />
          <Route path="/transactions" element={<h1>목록으로 이동</h1>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
it("filters category types, excludes archived entries and clears selection on type change", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockImplementation(() =>
        Promise.resolve(Response.json([expense, income, archived])),
      ),
  );
  mount("new");
  await screen.findByLabelText("카테고리", { exact: true });
  expect(screen.getByRole("option", { name: "식비" })).toBeInTheDocument();
  expect(
    screen.queryByRole("option", { name: "급여" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("option", { name: "옛날" }),
  ).not.toBeInTheDocument();
  await userEvent.selectOptions(
    screen.getByLabelText("카테고리", { exact: true }),
    expense.id,
  );
  await userEvent.selectOptions(
    screen.getByLabelText("유형", { exact: true }),
    "INCOME",
  );
  expect(screen.getByLabelText("카테고리", { exact: true })).toHaveValue("");
  expect(screen.getByRole("option", { name: "급여" })).toBeInTheDocument();
});
it("preserves fields on failure, submits exact numeric currency and invalidates list on success", async () => {
  let failed = true;
  const payloads: Record<string, unknown>[] = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string, init: RequestInit) => {
      if (input.endsWith("/csrf"))
        return Response.json({ token: "test", headerName: "X-CSRF-TOKEN" });
      if (input.startsWith("/api/categories")) return Response.json([expense]);
      const body = JSON.parse(init.body as string);
      payloads.push(body);
      return failed
        ? Response.json({ code: "UNAVAILABLE" }, { status: 503 })
        : Response.json({ ...body, id: user.id, version: 0 });
    }),
  );
  const client = mount("new");
  client.setQueryData(["transactions", user.id, "size=20"], { old: true });
  await screen.findByLabelText("금액 (원)");
  await userEvent.type(screen.getByLabelText("금액 (원)"), "999999999999999");
  await userEvent.selectOptions(
    screen.getByLabelText("카테고리", { exact: true }),
    expense.id,
  );
  await userEvent.type(
    screen.getByLabelText("설명", { exact: true }),
    " 점심 ",
  );
  await userEvent.click(screen.getByRole("button", { name: "거래 저장" }));
  await screen.findByRole("alert");
  expect(screen.getByLabelText("금액 (원)")).toHaveValue("999999999999999");
  expect(screen.getByLabelText("설명", { exact: true })).toHaveValue(" 점심 ");
  failed = false;
  await userEvent.click(screen.getByRole("button", { name: "거래 저장" }));
  await screen.findByRole("heading", { name: "목록으로 이동" });
  expect(payloads[1]).toMatchObject({
    amount: 999999999999999,
    description: "점심",
    memo: "",
  });
  expect(payloads[1]).not.toHaveProperty("userId");
  expect(
    client.getQueryState(["transactions", user.id, "size=20"])?.isInvalidated,
  ).toBe(true);
});
it("shows an empty-category path without allowing an unclassified transaction", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockImplementation(() => Promise.resolve(Response.json([]))),
  );
  mount("new");
  expect(
    await screen.findByText(/선택할 수 있는 지출 카테고리가 없습니다/),
  ).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "거래 저장" })).toBeDisabled();
  expect(
    screen.getByRole("link", { name: "카테고리 관리 (새 탭)" }),
  ).toHaveAttribute("href", "/settings/categories");
});
it("keeps duplicate category names editable and maps the category-specific conflict", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string, init?: RequestInit) => {
      if (input.endsWith("/csrf"))
        return Response.json({ token: "test", headerName: "X-CSRF-TOKEN" });
      if (init?.method === "POST")
        return Response.json(
          { code: "CATEGORY_ALREADY_EXISTS" },
          { status: 409 },
        );
      return Response.json([]);
    }),
  );
  mount("categories");
  await userEvent.type(screen.getByLabelText("카테고리 이름"), "식비");
  await userEvent.click(screen.getByRole("button", { name: "카테고리 추가" }));
  await waitFor(() =>
    expect(screen.getByRole("alert")).toHaveTextContent("이미 사용 중인 이름"),
  );
  expect(screen.getByLabelText("카테고리 이름")).toHaveValue("식비");
});
