"use client";

import { useEffect, useMemo, useState } from "react";
import { Activity, Loader2 } from "lucide-react";
import { formatDistanceToNow } from "date-fns";
import api from "@/lib/api";

interface CommandHistoryItem {
  id: string;
  input_text: string;
  output_json: { intent?: string };
  status: string;
  model_used: string;
  latency_ms: number;
  created_at: string;
}

export default function HistoryPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [commands, setCommands] = useState<CommandHistoryItem[]>([]);
  const [statusFilter, setStatusFilter] = useState<"all" | "success" | "failed">("all");

  const filteredCommands = useMemo(() => {
    if (statusFilter === "all") return commands;
    return commands.filter((c) => c.status === statusFilter);
  }, [commands, statusFilter]);

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const res = await api.get("/commands/history?limit=100");
        if (res.data.success) {
          setCommands(res.data.data || []);
        }
      } catch (err: any) {
        setError(err.response?.data?.message || "Failed to load command history");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, []);

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Command History</h1>
          <p className="text-foreground/60 mt-2">Monitor every AI command execution and model response.</p>
        </div>

        <div className="flex items-center gap-2">
          <label className="text-sm text-foreground/70">Status</label>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value as "all" | "success" | "failed")}
            className="bg-card border border-border rounded-lg px-3 py-2 text-sm"
          >
            <option value="all">All</option>
            <option value="success">Success</option>
            <option value="failed">Failed</option>
          </select>
        </div>
      </div>

      {error && (
        <div className="p-3 rounded-xl border border-red-500/20 bg-red-500/10 text-red-400 text-sm">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-16">
          <Loader2 className="w-6 h-6 text-primary animate-spin" />
        </div>
      ) : filteredCommands.length === 0 ? (
        <div className="bg-card border border-border rounded-3xl p-12 text-center text-foreground/60">
          No commands found for the selected filter.
        </div>
      ) : (
        <div className="space-y-4">
          {filteredCommands.map((cmd) => (
            <div key={cmd.id} className="bg-card border border-border rounded-2xl p-5">
              <div className="flex items-start justify-between gap-6">
                <div className="space-y-2">
                  <div className="text-sm text-foreground/50">#{cmd.id.slice(0, 8)}</div>
                  <p className="text-base font-medium">"{cmd.input_text}"</p>
                  <div className="text-sm text-foreground/60">
                    Intent: {cmd.output_json?.intent || "UNKNOWN"} · Model: {cmd.model_used || "n/a"} · Latency: {cmd.latency_ms || 0}ms
                  </div>
                </div>

                <div className="text-right space-y-2 min-w-40">
                  <div
                    className={`inline-flex items-center gap-2 px-2.5 py-1 rounded-md text-xs font-bold ${
                      cmd.status === "success"
                        ? "bg-green-500/10 text-green-500"
                        : "bg-red-500/10 text-red-400"
                    }`}
                  >
                    <Activity className="w-3.5 h-3.5" />
                    {cmd.status}
                  </div>
                  <div className="text-xs text-foreground/50">
                    {formatDistanceToNow(new Date(cmd.created_at), { addSuffix: true })}
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
