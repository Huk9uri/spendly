import { useRef } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { FormPage, FieldError } from "@/components/form-page";
import { ErrorState, LoadingState } from "@/components/status";
import { api, ApiError, errorMessage } from "@/lib/api";
import {
  categoriesSchema,
  transactionSchema,
  type User,
} from "@/lib/contracts";
import {
  transactionFormSchema,
  seoulToday,
  selectClass,
  type TransactionFormValues,
} from "@/lib/forms";

export function TransactionNewPage({ user }: { user: User }) {
  const submitting = useRef(false);
  const client = useQueryClient();
  const navigate = useNavigate();
  const form = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionFormSchema),
    defaultValues: {
      type: "EXPENSE",
      amount: "",
      categoryId: "",
      occurredOn: seoulToday(),
      description: "",
      memo: "",
    },
  });
  const type = useWatch({ control: form.control, name: "type" });
  const categories = useQuery({
    queryKey: ["categories", user.id, "active"],
    queryFn: ({ signal }) => api("/categories", categoriesSchema, { signal }),
    staleTime: 0,
  });
  const eligible =
    categories.data?.filter(
      (category) => !category.archived && category.type === type,
    ) ?? [];
  const create = useMutation({
    mutationFn: (values: TransactionFormValues) =>
      api("/transactions", transactionSchema, {
        method: "POST",
        body: JSON.stringify({ ...values, amount: Number(values.amount) }),
      }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ["transactions", user.id] });
      navigate("/transactions", {
        replace: true,
        state: { transactionCreated: true },
      });
    },
    onError: (error) => {
      if (
        error instanceof ApiError &&
        [
          "CATEGORY_ARCHIVED",
          "CATEGORY_NOT_FOUND",
          "CATEGORY_TYPE_MISMATCH",
        ].includes(error.code)
      ) {
        form.setError("categoryId", {
          message: "카테고리 상태가 변경되었습니다. 다시 선택해 주세요.",
        });
        void client.invalidateQueries({ queryKey: ["categories", user.id] });
      }
    },
    onSettled: () => {
      submitting.current = false;
    },
  });
  const errors = form.formState.errors;
  return (
    <FormPage
      user={user}
      title="거래 등록"
      description="오늘의 수입과 지출을 기록해 보세요. 금액은 원 단위 정수로 입력합니다."
    >
      {categories.isPending ? (
        <LoadingState text="카테고리를 불러오는 중…" />
      ) : categories.isError ? (
        <ErrorState
          message={errorMessage(categories.error)}
          retry={() => void categories.refetch()}
        />
      ) : (
        <form
          noValidate
          className="space-y-6 rounded-xl border bg-card p-5 sm:p-8"
          onSubmit={(event) => {
            void form.handleSubmit((values) => {
              if (
                !eligible.some((category) => category.id === values.categoryId)
              ) {
                form.setError("categoryId", {
                  message: "사용 중인 카테고리를 다시 선택해 주세요.",
                });
                return;
              }
              if (submitting.current) return;
              submitting.current = true;
              create.mutate(values);
            })(event);
          }}
        >
          <fieldset
            disabled={create.isPending}
            className="space-y-6 disabled:opacity-70"
          >
            <legend className="sr-only">거래 정보</legend>
            <div className="grid gap-5 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="transaction-type">유형</Label>
                <select
                  id="transaction-type"
                  className={selectClass}
                  {...form.register("type", {
                    onChange: () =>
                      form.setValue("categoryId", "", {
                        shouldValidate: form.formState.isSubmitted,
                      }),
                  })}
                >
                  <option value="EXPENSE">지출</option>
                  <option value="INCOME">수입</option>
                </select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="amount">금액 (원)</Label>
                <Input
                  id="amount"
                  type="text"
                  inputMode="numeric"
                  placeholder="예: 12000"
                  aria-invalid={!!errors.amount}
                  aria-describedby="amount-error"
                  {...form.register("amount")}
                />
                <FieldError
                  id="amount-error"
                  message={errors.amount?.message}
                />
              </div>
            </div>
            <div className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <Label htmlFor="transaction-category">카테고리</Label>
                <Link
                  to="/settings/categories"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-primary underline underline-offset-4"
                >
                  카테고리 관리 (새 탭)
                </Link>
              </div>
              <select
                id="transaction-category"
                className={selectClass}
                aria-invalid={!!errors.categoryId}
                aria-describedby="transaction-category-error"
                {...form.register("categoryId")}
              >
                <option value="">카테고리를 선택하세요</option>
                {eligible.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              <FieldError
                id="transaction-category-error"
                message={errors.categoryId?.message}
              />
              {eligible.length === 0 && (
                <p
                  role="status"
                  className="text-xs leading-6 text-muted-foreground"
                >
                  선택할 수 있는 {type === "EXPENSE" ? "지출" : "수입"}{" "}
                  카테고리가 없습니다. 카테고리 관리에서 먼저 추가해 주세요.
                </p>
              )}
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={categories.isFetching}
                onClick={() => void categories.refetch()}
              >
                카테고리 새로고침
              </Button>
            </div>
            <div className="space-y-2">
              <Label htmlFor="occurred-on">발생일</Label>
              <Input
                id="occurred-on"
                type="date"
                min="0001-01-01"
                max="9999-12-31"
                aria-invalid={!!errors.occurredOn}
                aria-describedby="occurred-on-error"
                {...form.register("occurredOn")}
              />
              <FieldError
                id="occurred-on-error"
                message={errors.occurredOn?.message}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">설명</Label>
              <Input
                id="description"
                maxLength={100}
                placeholder="예: 점심 식사"
                aria-invalid={!!errors.description}
                aria-describedby="description-error"
                {...form.register("description")}
              />
              <FieldError
                id="description-error"
                message={errors.description?.message}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="memo">메모 (선택)</Label>
              <textarea
                id="memo"
                rows={4}
                maxLength={1000}
                className="w-full resize-y rounded-lg border border-input bg-transparent px-3 py-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
                placeholder="더 남기고 싶은 내용을 적어 주세요."
                aria-invalid={!!errors.memo}
                aria-describedby="memo-error"
                {...form.register("memo")}
              />
              <FieldError id="memo-error" message={errors.memo?.message} />
            </div>
          </fieldset>
          {create.isError && (
            <p role="alert" className="text-sm text-destructive">
              {errorMessage(create.error)}
            </p>
          )}
          <div className="flex items-center justify-end gap-4 border-t pt-5">
            <Link to="/transactions" className="text-sm text-muted-foreground">
              취소
            </Link>
            <Button
              type="submit"
              className="h-11"
              disabled={create.isPending || eligible.length === 0}
            >
              {create.isPending ? "저장 중…" : "거래 저장"}
            </Button>
          </div>
        </form>
      )}
    </FormPage>
  );
}
