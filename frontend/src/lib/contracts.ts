import { z } from "zod";

export const userSchema = z.object({ id: z.uuid(), email: z.string() });
export type User = z.infer<typeof userSchema>;

export const credentialsSchema = z.object({
  email: z
    .string()
    .trim()
    .toLowerCase()
    .max(254, "이메일은 254자 이하여야 합니다.")
    .pipe(z.email("이메일 형식을 확인해 주세요.")),
  password: z
    .string()
    .min(12, "비밀번호는 12자 이상이어야 합니다.")
    .max(128, "비밀번호는 128자 이하여야 합니다.")
    .refine((value) => value.trim().length > 0, "비밀번호를 입력해 주세요."),
});
export type Credentials = z.infer<typeof credentialsSchema>;

export const transactionSchema = z.object({
  id: z.uuid(),
  type: z.enum(["INCOME", "EXPENSE"]),
  amount: z
    .number()
    .int()
    .positive()
    .max(999_999_999_999_999)
    .refine(Number.isSafeInteger),
  categoryId: z.uuid(),
  occurredOn: z.iso.date(),
  description: z.string(),
  memo: z.string(),
  version: z.number().int().nonnegative().refine(Number.isSafeInteger),
});
export type Transaction = z.infer<typeof transactionSchema>;
export const transactionPageSchema = z.object({
  content: z.array(transactionSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative().refine(Number.isSafeInteger),
  totalPages: z.number().int().nonnegative().refine(Number.isSafeInteger),
});
export const categoriesSchema = z.array(
  z.object({
    id: z.uuid(),
    name: z.string(),
    type: z.enum(["INCOME", "EXPENSE"]),
    archived: z.boolean(),
  }),
);

export function transactionSearch(params: URLSearchParams): string {
  const result = new URLSearchParams();
  for (const key of ["month", "type", "categoryId", "q", "page", "sort"]) {
    const value = params.get(key);
    if (value) result.set(key, value);
  }
  result.set("size", "20");
  return result.toString();
}
