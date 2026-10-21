import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { ArrowLeft, WalletCards } from "lucide-react";
import type { User } from "@/lib/contracts";

export function FormPage({
  user,
  title,
  description,
  children,
}: {
  user: User;
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="min-h-svh bg-background">
      <header className="border-b bg-card">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-4 px-5 py-5 sm:px-8">
          <Link
            to="/transactions"
            className="flex items-center gap-2 text-xl font-semibold tracking-tight text-primary"
          >
            <WalletCards aria-hidden="true" />
            spendly
          </Link>
          <span className="max-w-40 truncate text-xs text-muted-foreground">
            {user.email}
          </span>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-5 py-8 sm:px-8 sm:py-12">
        <Link
          to="/transactions"
          className="mb-6 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary"
        >
          <ArrowLeft aria-hidden="true" size={16} />
          거래 내역으로
        </Link>
        <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
        <p className="mt-3 mb-8 text-sm leading-6 text-muted-foreground">
          {description}
        </p>
        {children}
      </main>
    </div>
  );
}

export function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} role="alert" className="text-xs leading-5 text-destructive">
      {message}
    </p>
  ) : null;
}
