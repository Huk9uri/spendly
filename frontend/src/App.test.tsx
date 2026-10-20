import { QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { expect, it, vi } from "vitest";
import { createQueryClient } from "@/lib/query-client";
import App from "./App";

const user = {
  id: "cc1dc707-7a72-4780-a83b-904ab40b8dab",
  email: "test@example.invalid",
};
function mount() {
  const client = createQueryClient();
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={["/transactions"]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return client;
}
it("redirects anonymous visitors and validates input before login requests", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValue(
      Response.json({ code: "UNAUTHORIZED" }, { status: 401 }),
    );
  vi.stubGlobal("fetch", fetch);
  mount();
  await screen.findByRole("heading", { name: "로그인" });
  await userEvent.click(screen.getByRole("button", { name: "로그인" }));
  expect(await screen.findAllByRole("alert")).toHaveLength(2);
  expect(fetch).toHaveBeenCalledTimes(1);
});
it("logs in, displays empty transactions, and clears cached records on logout", async () => {
  let loggedIn = false;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string) => {
      if (input === "/api/me")
        return loggedIn
          ? Response.json(user)
          : Response.json({ code: "UNAUTHORIZED" }, { status: 401 });
      if (input === "/api/auth/csrf")
        return Response.json({ token: "test", headerName: "X-CSRF-TOKEN" });
      if (input === "/api/auth/login") {
        loggedIn = true;
        return Response.json(user);
      }
      if (input === "/api/auth/logout") {
        loggedIn = false;
        return new Response(null, { status: 204 });
      }
      if (input.startsWith("/api/categories")) return Response.json([]);
      return Response.json({
        content: [],
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      });
    }),
  );
  const client = mount();
  await screen.findByRole("heading", { name: "로그인" });
  await userEvent.type(screen.getByLabelText("이메일"), "test@example.invalid");
  await userEvent.type(screen.getByLabelText("비밀번호"), "Test password 123");
  await userEvent.click(screen.getByRole("button", { name: "로그인" }));
  await screen.findByRole("heading", { name: "거래 내역" });
  await screen.findByText("표시할 거래가 없습니다.");
  expect(client.getQueriesData({ queryKey: ["transactions"] })).toHaveLength(1);
  await userEvent.click(screen.getByRole("button", { name: "로그아웃" }));
  await screen.findByRole("heading", { name: "로그인" });
  expect(client.getQueriesData({ queryKey: ["transactions"] })).toHaveLength(0);
});
it("redirects on expired data requests without retaining the previous user cache", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: string) => {
      if (input === "/api/me") return Response.json(user);
      if (input.startsWith("/api/categories")) return Response.json([]);
      return Response.json({ code: "UNAUTHORIZED" }, { status: 401 });
    }),
  );
  const client = mount();
  await screen.findByRole("heading", { name: "로그인" });
  await waitFor(() => expect(client.getQueryData(["me"])).toBeNull());
  expect(client.getQueriesData({ queryKey: ["transactions"] })).toHaveLength(0);
});
it("shows a retryable error instead of treating a backend outage as signed-out", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockRejectedValue(new TypeError("network error")),
  );
  mount();
  expect(await screen.findByRole("alert")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
  expect(
    screen.queryByRole("heading", { name: "로그인" }),
  ).not.toBeInTheDocument();
});
