import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, WalletCards } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { api, errorMessage } from "@/lib/api";
import {
  credentialsSchema,
  userSchema,
  type Credentials,
} from "@/lib/contracts";

export function LoginPage() {
  const client = useQueryClient();
  const form = useForm<Credentials>({
    resolver: zodResolver(credentialsSchema),
    defaultValues: { email: "", password: "" },
  });
  const login = useMutation({
    mutationFn: () =>
      api("/auth/login", userSchema, {
        method: "POST",
        body: JSON.stringify(credentialsSchema.parse(form.getValues())),
      }),
    onSuccess: async (user) => {
      await client.cancelQueries();
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== "me",
      });
      client.setQueryData(["me"], user);
    },
    onSettled: () => form.resetField("password"),
  });
  return (
    <main className="grid min-h-svh lg:grid-cols-2">
      <section className="flex flex-col justify-between bg-primary px-7 py-9 text-primary-foreground sm:px-12 lg:px-16 lg:py-14">
        <div className="flex items-center gap-2 text-xl font-semibold tracking-tight">
          <WalletCards aria-hidden="true" size={25} /> spendly
          <span className="ml-2 text-xs font-normal opacity-65">
            나의 소비 기록
          </span>
        </div>
        <div className="py-12 lg:py-24">
          <p className="mb-5 text-xs tracking-[0.22em] opacity-65">
            작은 기록, 선명한 일상
          </p>
          <h1 className="text-3xl leading-snug font-semibold tracking-tight sm:text-4xl lg:text-5xl">
            돈의 흐름을 알면,
            <br />
            일상이 가벼워집니다.
          </h1>
          <p className="mt-6 max-w-sm text-sm leading-7 opacity-75">
            수입과 지출을 한곳에 모으고
            <br />
            나에게 맞는 소비 습관을 만들어 보세요.
          </p>
        </div>
        <p className="hidden text-xs opacity-55 lg:block">
          하루의 기록이 내일의 여유로.
        </p>
      </section>
      <section className="flex items-center justify-center px-6 py-12 sm:px-12">
        <Card className="w-full max-w-sm border-0 bg-transparent shadow-none ring-0">
          <CardHeader className="px-0">
            <p className="mb-3 text-xs font-medium tracking-widest text-primary">
              다시 만나 반가워요
            </p>
            <CardTitle className="text-2xl">
              <h2>로그인</h2>
            </CardTitle>
            <CardDescription className="leading-6">
              기록해 둔 나의 수입과 지출을 확인하세요.
            </CardDescription>
          </CardHeader>
          <CardContent className="px-0 pt-5">
            <form
              noValidate
              onSubmit={form.handleSubmit(() => login.mutate())}
              className="space-y-5"
            >
              <div className="space-y-2">
                <Label htmlFor="email">이메일</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="username"
                  placeholder="name@example.com"
                  aria-invalid={!!form.formState.errors.email}
                  aria-describedby={
                    form.formState.errors.email ? "email-error" : undefined
                  }
                  {...form.register("email")}
                />
                {form.formState.errors.email && (
                  <p
                    id="email-error"
                    role="alert"
                    className="text-xs text-destructive"
                  >
                    {form.formState.errors.email.message}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">비밀번호</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="current-password"
                  aria-invalid={!!form.formState.errors.password}
                  aria-describedby={
                    form.formState.errors.password
                      ? "password-error"
                      : undefined
                  }
                  {...form.register("password")}
                />
                {form.formState.errors.password && (
                  <p
                    id="password-error"
                    role="alert"
                    className="text-xs text-destructive"
                  >
                    {form.formState.errors.password.message}
                  </p>
                )}
              </div>
              {login.isError && (
                <p role="alert" className="text-sm text-destructive">
                  {errorMessage(login.error)}
                </p>
              )}
              <Button
                type="submit"
                className="mt-2 h-11 w-full"
                size="lg"
                disabled={login.isPending}
              >
                {login.isPending ? "로그인 중…" : "로그인"}
                <ArrowRight aria-hidden="true" />
              </Button>
            </form>
            <p className="mt-6 text-xs leading-6 text-muted-foreground">
              기존에 가입한 계정으로 로그인해 주세요.
            </p>
          </CardContent>
        </Card>
      </section>
    </main>
  );
}
