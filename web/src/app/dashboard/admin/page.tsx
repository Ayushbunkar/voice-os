"use client";

import { useEffect, useState } from "react";
import { Loader2, ShieldAlert } from "lucide-react";
import api from "@/lib/api";
import { useStore } from "@/lib/store";

interface Overview {
  users: number;
  devices: number;
  commands: number;
  commands24h: number;
  errors24h: number;
  activeSubscriptions: number;
}

interface AdminData {
  overview: Overview;
  recentErrors: Array<{
    id: string;
    input_text: string;
    error_message: string;
    created_at: string;
  }>;
}

export default function AdminPage() {
  const user = useStore((state) => state.user);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [data, setData] = useState<AdminData | null>(null);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await api.get("/admin/overview");
        if (res.data.success) setData(res.data.data);
      } catch (err: any) {
        setError(err.response?.data?.message || "Failed to load admin analytics");
      } finally {
        setLoading(false);
      }
    };

    if (user?.isAdmin) {
      load();
    } else {
      setLoading(false);
    }
  }, [user?.isAdmin]);

  if (!user?.isAdmin) {
    return (
      <div className="max-w-4xl mx-auto bg-card border border-border rounded-3xl p-10 text-center">
        <ShieldAlert className="mx-auto w-8 h-8 text-red-400" />
        <h1 className="text-2xl font-bold mt-4">Admin Access Required</h1>
        <p className="text-foreground/60 mt-2">This page is visible only for workspace administrators.</p>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="h-[70vh] flex items-center justify-center">
        <Loader2 className="w-6 h-6 text-primary animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Admin Panel</h1>
        <p className="text-foreground/60 mt-2">Platform health, usage, and error diagnostics.</p>
      </div>

      {error && (
        <div className="p-3 rounded-xl border border-red-500/20 bg-red-500/10 text-red-400 text-sm">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <StatCard label="Users" value={data?.overview.users || 0} />
        <StatCard label="Devices" value={data?.overview.devices || 0} />
        <StatCard label="Commands" value={data?.overview.commands || 0} />
        <StatCard label="Commands (24h)" value={data?.overview.commands24h || 0} />
        <StatCard label="Errors (24h)" value={data?.overview.errors24h || 0} />
        <StatCard label="Active Subs" value={data?.overview.activeSubscriptions || 0} />
      </div>

      <div className="bg-card border border-border rounded-3xl p-6">
        <h2 className="text-lg font-bold">Recent Errors</h2>
        <div className="mt-4 space-y-3">
          {data?.recentErrors?.length ? (
            data.recentErrors.map((err) => (
              <div key={err.id} className="p-3 rounded-xl border border-border bg-background/60">
                <div className="text-xs text-foreground/50">#{err.id.slice(0, 8)}</div>
                <div className="text-sm mt-1">{err.input_text}</div>
                <div className="text-sm text-red-400 mt-1">{err.error_message || "Unknown error"}</div>
              </div>
            ))
          ) : (
            <div className="text-sm text-foreground/60">No recent errors.</div>
          )}
        </div>
      </div>
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-card border border-border rounded-2xl p-4">
      <p className="text-xs text-foreground/50">{label}</p>
      <p className="text-2xl font-bold mt-1">{value}</p>
    </div>
  );
}
