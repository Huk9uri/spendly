import { Button } from "@/components/ui/button";

export function ErrorState({
  message,
  retry,
}: {
  message: string;
  retry: () => void;
}) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-destructive/25 bg-destructive/5 p-5"
    >
      <p className="text-sm text-destructive">{message}</p>
      <Button variant="outline" size="sm" className="mt-3" onClick={retry}>
        다시 시도
      </Button>
    </div>
  );
}

export function LoadingState({ text = "불러오는 중…" }: { text?: string }) {
  return (
    <p
      role="status"
      className="py-12 text-center text-sm text-muted-foreground"
    >
      {text}
    </p>
  );
}
