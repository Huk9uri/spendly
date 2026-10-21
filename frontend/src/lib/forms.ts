import { z } from "zod";

export const categoryNameSchema = z
  .string()
  .trim()
  .min(1, "카테고리 이름을 입력해 주세요.")
  .max(50, "이름은 50자 이하여야 합니다.");
export const categoryFormSchema = z.object({
  name: categoryNameSchema,
  type: z.enum(["EXPENSE", "INCOME"]),
});
export type CategoryFormValues = z.infer<typeof categoryFormSchema>;

export const transactionFormSchema = z.object({
  type: z.enum(["EXPENSE", "INCOME"]),
  amount: z
    .string()
    .regex(/^[0-9]+$/, "금액은 숫자로 된 정수만 입력해 주세요.")
    .refine((value) => {
      const amount = Number(value);
      return (
        Number.isSafeInteger(amount) &&
        amount >= 1 &&
        amount <= 999_999_999_999_999
      );
    }, "금액은 1~999,999,999,999,999원이어야 합니다."),
  categoryId: z.uuid("카테고리를 선택해 주세요."),
  occurredOn: z
    .string()
    .regex(/^(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}$/, "날짜를 입력해 주세요.")
    .pipe(z.iso.date("올바른 날짜를 입력해 주세요.")),
  description: z
    .string()
    .trim()
    .min(1, "거래 설명을 입력해 주세요.")
    .max(100, "설명은 100자 이하여야 합니다."),
  memo: z.string().max(1000, "메모는 1000자 이하여야 합니다."),
});
export type TransactionFormValues = z.infer<typeof transactionFormSchema>;

export function seoulToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((value) => value.type === type)!.value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

export const selectClass =
  "h-11 w-full rounded-lg border border-input bg-background px-3 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50";
