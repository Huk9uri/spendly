import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  ListFilter,
  LogOut,
  Search,
  WalletCards,
} from "lucide-react";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ErrorState, LoadingState } from "@/components/status";
import { api, errorMessage } from "@/lib/api";
import {
  categoriesSchema,
  transactionPageSchema,
  transactionSearch,
  type User,
} from "@/lib/contracts";

const won = new Intl.NumberFormat("ko-KR");
const selectClass =
  "h-11 w-full rounded-md border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50";

export function TransactionsPage({ user }: { user: User }) {
  const client = useQueryClient();
  const [params, setParams] = useSearchParams();
  const query = transactionSearch(params);
  const transactions = useQuery({
    queryKey: ["transactions", user.id, query],
    queryFn: ({ signal }) =>
      api(`/transactions?${query}`, transactionPageSchema, { signal }),
  });
  const categories = useQuery({
    queryKey: ["categories", user.id],
    queryFn: ({ signal }) =>
      api("/categories?includeArchived=true", categoriesSchema, { signal }),
  });
  const logout = useMutation({
    mutationFn: () => api("/auth/logout", z.undefined(), { method: "POST" }),
    onSuccess: async () => {
      await client.cancelQueries();
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== "me",
      });
      client.setQueryData(["me"], null);
    },
  });
  function update(key: string, value: string) {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next);
  }
  const categoryNames = new Map(
    categories.data?.map((category) => [category.id, category.name]),
  );
  const data = transactions.data;
  return (
    <div className="min-h-svh bg-background">
      <header className="border-b bg-card">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-3 px-5 py-5 sm:px-8">
          <a
            href="/transactions"
            className="flex items-center gap-2 text-xl font-semibold tracking-tight text-primary"
          >
            <WalletCards aria-hidden="true" />
            spendly
          </a>
          <div className="flex min-w-0 items-center gap-3">
            <span className="max-w-36 truncate text-xs text-muted-foreground sm:max-w-64">
              {user.email}
            </span>
            <Button
              aria-label="로그아웃"
              variant="ghost"
              size="sm"
              onClick={() => logout.mutate()}
              disabled={logout.isPending}
            >
              <LogOut aria-hidden="true" size={15} />
              <span className="hidden sm:inline">로그아웃</span>
              <span className="sr-only sm:hidden">로그아웃</span>
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-5 py-8 sm:px-8 sm:py-12">
        <div className="mb-8">
          <p className="mb-2 text-xs font-medium tracking-widest text-primary">
            나의 금융 기록
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">거래 내역</h1>
          <p className="mt-3 text-sm text-muted-foreground">
            차곡차곡 쌓인 기록에서 나의 소비 흐름을 살펴보세요.
          </p>
        </div>
        {logout.isError && (
          <p role="alert" className="mb-5 text-sm text-destructive">
            {errorMessage(logout.error)}
          </p>
        )}
        <section
          aria-label="거래 조회 조건"
          className="mb-6 rounded-xl border bg-card p-5"
        >
          <div className="mb-4 flex items-center gap-2 text-sm font-medium">
            <ListFilter aria-hidden="true" size={16} />
            조회 조건
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <div className="space-y-2">
              <Label htmlFor="month">월</Label>
              <Input
                id="month"
                type="month"
                min="0001-01"
                max="9999-12"
                value={params.get("month") ?? ""}
                onChange={(e) => update("month", e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="type">유형</Label>
              <select
                id="type"
                className={selectClass}
                value={params.get("type") ?? ""}
                onChange={(e) => update("type", e.target.value)}
              >
                <option value="">전체 유형</option>
                <option value="EXPENSE">지출</option>
                <option value="INCOME">수입</option>
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="category">카테고리</Label>
              <select
                id="category"
                className={selectClass}
                value={params.get("categoryId") ?? ""}
                disabled={categories.isPending || categories.isError}
                onChange={(e) => update("categoryId", e.target.value)}
              >
                <option value="">전체 카테고리</option>
                {categories.data?.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name} ·{" "}
                    {category.type === "INCOME" ? "수입" : "지출"}
                    {category.archived ? " (삭제됨)" : ""}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="sort">정렬</Label>
              <select
                id="sort"
                className={selectClass}
                value={params.get("sort") ?? "occurredOn,desc"}
                onChange={(e) => update("sort", e.target.value)}
              >
                <option value="occurredOn,desc">최신 날짜순</option>
                <option value="occurredOn,asc">오래된 날짜순</option>
                <option value="amount,desc">높은 금액순</option>
                <option value="amount,asc">낮은 금액순</option>
                <option value="createdAt,desc">최근 등록순</option>
              </select>
            </div>
          </div>
          <form
            className="mt-4 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              update("q", String(new FormData(e.currentTarget).get("q") ?? "").trim());
            }}
          >
            <Label htmlFor="search" className="sr-only">
              거래 검색
            </Label>
            <div className="relative min-w-0 flex-1">
              <Search
                aria-hidden="true"
                size={16}
                className="absolute top-3 left-3 text-muted-foreground"
              />
              <Input
                id="search"
                className="pl-9"
                placeholder="설명이나 메모로 검색"
                maxLength={100}
                name="q"
                key={params.get("q") ?? ""}
                defaultValue={params.get("q") ?? ""}
              />
            </div>
            <Button type="submit" variant="secondary">
              검색
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setParams({});
              }}
            >
              초기화
            </Button>
          </form>
        </section>
        {categories.isError && (
          <div className="mb-5">
            <ErrorState
              message="카테고리 이름을 불러오지 못했습니다."
              retry={() => void categories.refetch()}
            />
          </div>
        )}
        <section
          aria-label="거래 목록"
          className="overflow-hidden rounded-xl border bg-card"
        >
          <div className="flex items-center justify-between border-b px-5 py-4">
            <h2 className="text-sm font-semibold">전체 기록</h2>
            <p className="text-xs text-muted-foreground">
              {data ? `${won.format(data.totalElements)}건` : "조회 중"}
            </p>
          </div>
          {transactions.isPending ? (
            <LoadingState />
          ) : transactions.isError ? (
            <div className="p-5">
              <ErrorState
                message={errorMessage(transactions.error)}
                retry={() => void transactions.refetch()}
              />
            </div>
          ) : !data?.content.length ? (
            <div className="px-5 py-16 text-center">
              <WalletCards
                aria-hidden="true"
                size={30}
                className="mx-auto mb-4 text-muted-foreground/60"
              />
              <h3 className="text-sm font-medium">표시할 거래가 없습니다.</h3>
              <p className="mt-2 text-xs text-muted-foreground">
                조회 조건을 바꾸거나 기록한 거래를 확인해 주세요.
              </p>
            </div>
          ) : (
            <ul className="divide-y">
              {data.content.map((transaction) => (
                <li
                  key={transaction.id}
                  className="flex flex-wrap items-start gap-3 px-5 py-5 sm:gap-4"
                >
                  <span
                    className={`mt-1 rounded-xl p-2.5 ${transaction.type === "INCOME" ? "bg-primary/10 text-primary" : "bg-secondary text-secondary-foreground"}`}
                  >
                    {transaction.type === "INCOME" ? (
                      <ArrowDownLeft aria-hidden="true" size={19} />
                    ) : (
                      <ArrowUpRight aria-hidden="true" size={19} />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium break-words">
                      {transaction.description}
                    </p>
                    <p className="mt-1 text-xs leading-5 text-muted-foreground">
                      {transaction.occurredOn} ·{" "}
                      {categoryNames.get(transaction.categoryId) ?? "카테고리"}{" "}
                      · {transaction.type === "INCOME" ? "수입" : "지출"}
                    </p>
                    {transaction.memo && (
                      <p className="mt-1 text-xs break-words text-muted-foreground">
                        {transaction.memo}
                      </p>
                    )}
                  </div>
                  <span
                    className={`w-full shrink-0 pt-1 pl-12 text-right text-sm font-semibold tabular-nums sm:w-auto sm:pl-0 sm:text-base ${transaction.type === "INCOME" ? "text-primary" : ""}`}
                  >
                    {transaction.type === "INCOME" ? "+" : "−"}
                    {won.format(transaction.amount)}
                    <span className="ml-0.5 text-xs font-normal">원</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {data && !transactions.isError && (
            <nav
              aria-label="거래 페이지"
              className="flex items-center justify-between border-t px-5 py-4"
            >
              <p className="text-xs text-muted-foreground">
                {data.totalPages === 0
                  ? "0 / 0"
                  : `${data.page + 1} / ${data.totalPages}`}{" "}
                페이지
              </p>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  aria-label="이전 페이지"
                  disabled={data.page === 0 || transactions.isFetching}
                  onClick={() => update("page", String(data.page - 1))}
                >
                  <ChevronLeft aria-hidden="true" size={16} />
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  aria-label="다음 페이지"
                  disabled={
                    data.page + 1 >= data.totalPages || transactions.isFetching
                  }
                  onClick={() => update("page", String(data.page + 1))}
                >
                  <ChevronRight aria-hidden="true" size={16} />
                </Button>
              </div>
            </nav>
          )}
        </section>
        <p className="mt-6 text-center text-xs text-muted-foreground">
          하루의 기록이 내일의 여유로.
        </p>
      </main>
    </div>
  );
}
