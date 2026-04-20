"use client";

import { useEffect, useState } from "react";
import api from "@/lib/api";
import { Mic, Smartphone, Zap, Activity } from "lucide-react";
import { formatDistanceToNow } from "date-fns";

export default function DashboardOverview() {
  const [stats, setStats] = useState({
    deviceCount: 0,
    macroCount: 0,
    commandsToday: 0
  });
  const [recentCommands, setRecentCommands] = useState<any[]>([]);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [devicesRes, macrosRes, historyRes] = await Promise.all([
          api.get("/devices"),
          api.get("/macros"),
          api.get("/commands/history?limit=5")
        ]);

        if (devicesRes.data.success) {
          setStats(s => ({ ...s, deviceCount: devicesRes.data.devices.length }));
        }
        if (macrosRes.data.success) {
          setStats(s => ({ ...s, macroCount: macrosRes.data.macros.length }));
        }
        if (historyRes.data.success) {
          setRecentCommands(historyRes.data.data);
          // Just an approximation for UI, real today stat should come from usage table
          setStats(s => ({ ...s, commandsToday: historyRes.data.data.length })); 
        }
      } catch (err) {
        console.error("Failed to load dashboard data", err);
      }
    };
    fetchData();
  }, []);

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Dashboard Overview</h1>
        <p className="text-foreground/60 mt-2">Welcome back to your VoiceOS Cloud command center.</p>
      </div>

      <div className="grid gap-6 grid-cols-1 md:grid-cols-3">
        {/* Metric Cards */}
        <div className="bg-card border border-border p-6 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center">
            <Smartphone className="w-6 h-6 text-primary" />
          </div>
          <div>
             <p className="text-sm text-foreground/60 font-medium">Connected Devices</p>
             <h3 className="text-2xl font-bold">{stats.deviceCount}</h3>
          </div>
        </div>

        <div className="bg-card border border-border p-6 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center">
            <Zap className="w-6 h-6 text-primary" />
          </div>
          <div>
             <p className="text-sm text-foreground/60 font-medium">Cloud Macros</p>
             <h3 className="text-2xl font-bold">{stats.macroCount}</h3>
          </div>
        </div>

        <div className="bg-card border border-border p-6 rounded-2xl flex items-center gap-4">
          <div className="w-12 h-12 bg-primary/10 rounded-xl flex items-center justify-center">
            <Activity className="w-6 h-6 text-primary" />
          </div>
          <div>
             <p className="text-sm text-foreground/60 font-medium">Commands Executed</p>
             <h3 className="text-2xl font-bold">{stats.commandsToday}</h3>
          </div>
        </div>
      </div>

      <div className="bg-card border border-border rounded-3xl p-8">
         <h2 className="text-xl font-bold mb-6 flex items-center gap-2">
           <Mic className="w-5 h-5 text-primary" />
           Recent Voice Commands
         </h2>
         
         <div className="space-y-4">
           {recentCommands.length === 0 ? (
              <p className="text-foreground/50 text-sm py-4">No recent commands found.</p>
           ) : (
             recentCommands.map((cmd) => (
                <div key={cmd.id} className="flex items-center justify-between p-4 rounded-xl border border-border/50 bg-background/50">
                   <div className="flex flex-col gap-1">
                      <span className="font-medium">"{cmd.input_text}"</span>
                      <span className="text-xs text-foreground/50">Intent: {cmd.output_json?.intent} • Model: {cmd.model_used}</span>
                   </div>
                   <div className="flex items-center gap-4 text-sm text-foreground/50">
                      <span className={`px-2 py-1 rounded-md text-xs font-bold ${cmd.status === 'success' ? 'bg-green-500/10 text-green-500' : 'bg-amber-500/10 text-amber-500'}`}>
                         {cmd.status}
                      </span>
                      {formatDistanceToNow(new Date(cmd.created_at), { addSuffix: true })}
                   </div>
                </div>
             ))
           )}
         </div>
      </div>
    </div>
  );
}
