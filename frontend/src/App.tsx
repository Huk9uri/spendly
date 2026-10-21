import { useEffect } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { api, ApiError, errorMessage } from "@/lib/api";
import { userSchema } from "@/lib/contracts";
import { ErrorState, LoadingState } from "@/components/status";
import { LoginPage } from "@/pages/login";
import { TransactionsPage } from "@/pages/transactions";
import { CategoriesPage } from "@/pages/categories";
import { TransactionNewPage } from "@/pages/transaction-new";

export default function App() {
  const client = useQueryClient();
  const navigate = useNavigate();
  const session = useQuery({
    queryKey: ["me"],
    queryFn: async ({ signal }) => {
      try {
        return await api("/me", userSchema, { signal });
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 0,
  });
  useEffect(() => {
    const expired = () => {
      void client.cancelQueries();
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== "me",
      });
      client.setQueryData(["me"], null);
      navigate("/login", { replace: true });
    };
    window.addEventListener("spendly:unauthorized", expired);
    return () => window.removeEventListener("spendly:unauthorized", expired);
  }, [client, navigate]);
  useEffect(() => {
    if (session.data === null) {
      void client.cancelQueries({
        predicate: (query) => query.queryKey[0] !== "me",
      });
      client.removeQueries({
        predicate: (query) => query.queryKey[0] !== "me",
      });
    }
  }, [client, session.data]);
  if (session.isPending)
    return <LoadingState text="로그인 상태를 확인하는 중…" />;
  if (session.isError)
    return (
      <main className="mx-auto max-w-lg p-6 pt-20">
        <ErrorState
          message={errorMessage(session.error)}
          retry={() => void session.refetch()}
        />
      </main>
    );
  const user = session.data;
  return (
    <Routes>
      <Route
        path="/transactions/new"
        element={
          user ? (
            <TransactionNewPage user={user} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/settings/categories"
        element={
          user ? (
            <CategoriesPage user={user} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/settings"
        element={
          <Navigate to={user ? "/settings/categories" : "/login"} replace />
        }
      />
      <Route
        path="/login"
        element={user ? <Navigate to="/transactions" replace /> : <LoginPage />}
      />
      <Route
        path="/transactions"
        element={
          user ? (
            <TransactionsPage user={user} />
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="*"
        element={<Navigate to={user ? "/transactions" : "/login"} replace />}
      />
    </Routes>
  );
}
