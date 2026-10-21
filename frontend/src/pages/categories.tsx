import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormPage, FieldError } from "@/components/form-page";
import { ErrorState, LoadingState } from "@/components/status";
import { api, errorMessage } from "@/lib/api";
import {
  categoriesSchema,
  categorySchema,
  type Category,
  type User,
} from "@/lib/contracts";
import {
  categoryFormSchema,
  categoryNameSchema,
  selectClass,
  type CategoryFormValues,
} from "@/lib/forms";

export function CategoriesPage({ user }: { user: User }) {
  const client = useQueryClient();
  const [includeArchived, setIncludeArchived] = useState(false);
  const form = useForm<CategoryFormValues>({
    resolver: zodResolver(categoryFormSchema),
    defaultValues: { name: "", type: "EXPENSE" },
  });
  const categories = useQuery({
    queryKey: ["categories", user.id],
    queryFn: ({ signal }) =>
      api("/categories?includeArchived=true", categoriesSchema, { signal }),
  });
  const create = useMutation({
    mutationFn: (values: CategoryFormValues) =>
      api("/categories", categorySchema, {
        method: "POST",
        body: JSON.stringify(values),
      }),
    onSuccess: async (_, values) => {
      form.reset({ name: "", type: values.type });
      await client.invalidateQueries({ queryKey: ["categories", user.id] });
    },
  });
  const visible = categories.data?.filter(
    (category) => includeArchived || !category.archived,
  );
  return (
    <FormPage
      user={user}
      title="카테고리 관리"
      description="나에게 맞는 카테고리를 만들고 수입과 지출을 정리해 보세요."
    >
      <section
        aria-labelledby="new-category-title"
        className="rounded-xl border bg-card p-5 sm:p-6"
      >
        <h2 id="new-category-title" className="mb-5 text-base font-semibold">
          새 카테고리
        </h2>
        <form
          noValidate
          onSubmit={form.handleSubmit((values) => create.mutate(values))}
          className="space-y-4"
        >
          <div className="grid gap-4 sm:grid-cols-[1fr_8rem]">
            <div className="space-y-2">
              <Label htmlFor="category-name">카테고리 이름</Label>
              <Input
                id="category-name"
                maxLength={50}
                placeholder="예: 식비, 교통비, 급여"
                aria-invalid={!!form.formState.errors.name}
                aria-describedby="category-name-error"
                {...form.register("name")}
              />
              <FieldError
                id="category-name-error"
                message={form.formState.errors.name?.message}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="category-type">카테고리 유형</Label>
              <select
                id="category-type"
                className={selectClass}
                {...form.register("type")}
              >
                <option value="EXPENSE">지출</option>
                <option value="INCOME">수입</option>
              </select>
            </div>
          </div>
          {create.isError && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage(create.error)}
            </p>
          )}
          {create.isSuccess && (
            <p role="status" className="text-sm text-primary">
              카테고리를 추가했습니다.
            </p>
          )}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-muted-foreground">
              유형은 생성 후 변경할 수 없습니다.
            </p>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? "추가 중…" : "카테고리 추가"}
            </Button>
          </div>
        </form>
      </section>
      <section
        aria-labelledby="category-list-title"
        className="mt-6 overflow-hidden rounded-xl border bg-card"
      >
        <div className="flex flex-wrap items-center justify-between gap-3 border-b p-5">
          <h2 id="category-list-title" className="text-sm font-semibold">
            카테고리 목록
          </h2>
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input
              type="checkbox"
              checked={includeArchived}
              onChange={(event) => setIncludeArchived(event.target.checked)}
            />
            삭제된 카테고리 포함
          </label>
        </div>
        {categories.isPending ? (
          <LoadingState />
        ) : categories.isError ? (
          <div className="p-5">
            <ErrorState
              message={errorMessage(categories.error)}
              retry={() => void categories.refetch()}
            />
          </div>
        ) : !visible?.length ? (
          <p className="p-10 text-center text-sm text-muted-foreground">
            카테고리가 없습니다. 위에서 첫 카테고리를 만들어 보세요.
          </p>
        ) : (
          <ul className="divide-y">
            {visible.map((category) => (
              <CategoryRow
                key={category.id}
                category={category}
                userId={user.id}
              />
            ))}
          </ul>
        )}
      </section>
      <p className="mt-5 text-xs leading-6 text-muted-foreground">
        카테고리를 삭제해도 기존 거래는 유지됩니다. 삭제된 항목은 새 거래에서
        선택할 수 없습니다.
      </p>
      <Link
        to="/transactions/new"
        className="mt-6 inline-block text-sm font-medium text-primary underline underline-offset-4"
      >
        거래 등록으로 이동
      </Link>
    </FormPage>
  );
}

function CategoryRow({
  category,
  userId,
}: {
  category: Category;
  userId: string;
}) {
  const client = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [name, setName] = useState(category.name);
  const [validation, setValidation] = useState<string>();
  const mutation = useMutation({
    mutationFn: (
      change: { action: "rename"; name: string } | { action: "delete" },
    ) =>
      change.action === "rename"
        ? api(`/categories/${category.id}`, categorySchema, {
            method: "PATCH",
            body: JSON.stringify({ name: change.name }),
          })
        : api(`/categories/${category.id}`, z.undefined(), {
            method: "DELETE",
          }),
    onSuccess: async () => {
      setEditing(false);
      setConfirming(false);
      await client.invalidateQueries({ queryKey: ["categories", userId] });
    },
  });
  const editId = `name-${category.id}`;
  return (
    <li aria-label={`${category.name} 카테고리`} className="p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-medium break-all">{category.name}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            {category.type === "INCOME" ? "수입" : "지출"}
            {category.archived ? " · 삭제됨" : ""}
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            disabled={mutation.isPending || editing || confirming}
            onClick={() => {
              mutation.reset();
              setName(category.name);
              setValidation(undefined);
              setEditing(true);
            }}
          >
            이름 변경
          </Button>
          {!category.archived && (
            <Button
              size="sm"
              variant="ghost"
              disabled={mutation.isPending || editing || confirming}
              onClick={() => {
                mutation.reset();
                setConfirming(true);
              }}
            >
              삭제
            </Button>
          )}
        </div>
      </div>
      {editing && (
        <form
          className="mt-4 space-y-3"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            const parsed = categoryNameSchema.safeParse(name);
            if (!parsed.success) {
              setValidation(parsed.error.issues[0].message);
              return;
            }
            setValidation(undefined);
            mutation.mutate({ action: "rename", name: parsed.data });
          }}
        >
          <Label htmlFor={editId}>새 이름</Label>
          <Input
            id={editId}
            value={name}
            onChange={(event) => setName(event.target.value)}
            maxLength={50}
            aria-invalid={!!validation}
            aria-describedby={`${editId}-error`}
            autoFocus
          />
          <FieldError id={`${editId}-error`} message={validation} />
          <div className="flex justify-end gap-2">
            <Button
              type="button"
              variant="ghost"
              disabled={mutation.isPending}
              onClick={() => {
                setEditing(false);
                mutation.reset();
              }}
            >
              취소
            </Button>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "저장 중…" : "이름 저장"}
            </Button>
          </div>
        </form>
      )}
      {confirming && (
        <div className="mt-4 rounded-lg bg-secondary p-4">
          <p className="text-sm">이 카테고리를 삭제할까요?</p>
          <p className="mt-2 text-xs leading-5 text-muted-foreground">
            기존 거래는 유지되며 새 거래에서 선택할 수 없습니다.
          </p>
          <div className="mt-3 flex justify-end gap-2">
            <Button
              variant="ghost"
              disabled={mutation.isPending}
              onClick={() => {
                setConfirming(false);
                mutation.reset();
              }}
            >
              취소
            </Button>
            <Button
              variant="destructive"
              disabled={mutation.isPending}
              onClick={() => mutation.mutate({ action: "delete" })}
            >
              {mutation.isPending ? "삭제 중…" : "삭제 확인"}
            </Button>
          </div>
        </div>
      )}
      {mutation.isError && (
        <p role="alert" className="mt-3 text-sm text-destructive">
          {errorMessage(mutation.error)}
        </p>
      )}
    </li>
  );
}
