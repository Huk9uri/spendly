import { describe, expect, it } from "vitest";
import { categoryFormSchema, transactionFormSchema, seoulToday } from "./forms";

const valid = {
  type: "EXPENSE",
  amount: "12000",
  categoryId: "cc1dc707-7a72-4780-a83b-904ab40b8dab",
  occurredOn: "2024-02-29",
  description: " 점심 ",
  memo: "  메모  ",
};
describe("creation form rules", () => {
  it("accepts exact integer currency and preserves optional memo whitespace", () => {
    const result = transactionFormSchema.parse({
      ...valid,
      amount: "999999999999999",
    });
    expect(Number(result.amount)).toBe(999999999999999);
    expect(result.description).toBe("점심");
    expect(result.memo).toBe("  메모  ");
    expect(transactionFormSchema.parse({ ...valid, memo: "" }).memo).toBe("");
  });
  it("rejects fractions, exponents, separators, signs, zero and unsafe/range amounts", () => {
    for (const amount of [
      "",
      "0",
      "-1",
      "+1",
      "12.5",
      "1e3",
      "1,000",
      " 100 ",
      "1000000000000000",
      "9007199254740992",
    ]) {
      expect(
        transactionFormSchema.safeParse({ ...valid, amount }).success,
        amount,
      ).toBe(false);
    }
  });
  it("validates calendar dates, required selection, names and lengths", () => {
    for (const occurredOn of [
      "",
      "0000-01-01",
      "2026-02-29",
      "2024-02-30",
      "2026-13-01",
    ]) {
      expect(
        transactionFormSchema.safeParse({ ...valid, occurredOn }).success,
      ).toBe(false);
    }
    expect(
      transactionFormSchema.safeParse({ ...valid, categoryId: "" }).success,
    ).toBe(false);
    expect(
      transactionFormSchema.safeParse({ ...valid, description: "   " }).success,
    ).toBe(false);
    expect(
      transactionFormSchema.safeParse({
        ...valid,
        description: "a".repeat(101),
      }).success,
    ).toBe(false);
    expect(
      transactionFormSchema.safeParse({ ...valid, memo: "a".repeat(1001) })
        .success,
    ).toBe(false);
  });
  it("uses the Seoul calendar day around UTC midnight", () => {
    expect(seoulToday(new Date("2026-10-20T15:00:00Z"))).toBe("2026-10-21");
    expect(seoulToday(new Date("2026-10-20T14:59:59Z"))).toBe("2026-10-20");
  });
  it("normalizes category names and enforces the existing domain bounds", () => {
    expect(
      categoryFormSchema.parse({ name: " 식비 ", type: "EXPENSE" }).name,
    ).toBe("식비");
    for (const name of ["", "   ", "가".repeat(51)])
      expect(
        categoryFormSchema.safeParse({ name, type: "EXPENSE" }).success,
      ).toBe(false);
    expect(
      categoryFormSchema.safeParse({ name: "식비", type: "OTHER" }).success,
    ).toBe(false);
  });
});
