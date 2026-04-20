"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Sidebar } from "@/components/Sidebar";
import api from "@/lib/api";
import { useStore } from "@/lib/store";
import { Loader2 } from "lucide-react";

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const login = useStore((state) => state.login);
  const user = useStore((state) => state.user);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check auth on mount
    const fetchUser = async () => {
      const token = localStorage.getItem("token");
      if (!token) {
        router.push("/login");
        return;
      }
      try {
        const res = await api.get("/auth/me");
        if (res.data.success) {
          login(res.data.user, token);
        }
      } catch (err) {
        router.push("/login");
      } finally {
        setLoading(false);
      }
    };
    fetchUser();
  }, [router, login]);

  if (loading || !user) {
    return (
      <div className="h-screen flex items-center justify-center bg-background">
        <Loader2 className="w-8 h-8 text-primary animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background md:flex">
      <div className="hidden md:block">
        <Sidebar />
      </div>
      <div className="flex-1 md:ml-64 p-4 md:p-8 overflow-y-auto">
        {children}
      </div>
    </div>
  );
}
