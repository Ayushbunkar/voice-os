"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Play, Plus, Trash2, WandSparkles } from "lucide-react";
import api from "@/lib/api";

interface MacroStep {
  action: string;
  app?: string;
  target?: string;
  message?: string;
  direction?: "up" | "down";
  index?: number;
  text?: string;
}

interface Macro {
  id: string;
  name: string;
  description: string;
  steps: MacroStep[];
  delay_ms: number;
  is_active: boolean;
}

interface Device {
  id: string;
  device_name: string;
  status: string;
}

const DEFAULT_STEPS = `[
  {"action":"OPEN_APP","app":"whatsapp"},
  {"action":"SEND_MESSAGE","target":"Riya","message":"hello"}
]`;

export default function MacrosPage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [runningMacroId, setRunningMacroId] = useState<string | null>(null);
  const [error, setError] = useState("");

  const [macros, setMacros] = useState<Macro[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [selectedDeviceId, setSelectedDeviceId] = useState("");

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [stepsJson, setStepsJson] = useState(DEFAULT_STEPS);

  const onlineDevices = useMemo(() => devices.filter((d) => d.status === "online"), [devices]);

  const loadData = async () => {
    setLoading(true);
    setError("");
    try {
      const [macrosRes, devicesRes] = await Promise.all([
        api.get("/macros"),
        api.get("/devices"),
      ]);

      if (macrosRes.data.success) setMacros(macrosRes.data.macros || []);
      if (devicesRes.data.success) {
        const fetchedDevices = devicesRes.data.devices || [];
        setDevices(fetchedDevices);

        const defaultDevice = fetchedDevices.find((d: Device) => d.status === "online")?.id;
        if (defaultDevice) setSelectedDeviceId(defaultDevice);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to load macros");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError("");

    try {
      const parsedSteps = JSON.parse(stepsJson) as MacroStep[];
      await api.post("/macros", {
        name,
        description,
        steps: parsedSteps,
        delayMs: 1500,
      });

      setName("");
      setDescription("");
      setStepsJson(DEFAULT_STEPS);
      await loadData();
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to create macro. Check steps JSON.");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (macroId: string) => {
    try {
      await api.delete(`/macros/${macroId}`);
      setMacros((prev) => prev.filter((m) => m.id !== macroId));
    } catch {
      setError("Failed to delete macro");
    }
  };

  const handleRun = async (macroId: string) => {
    setRunningMacroId(macroId);
    setError("");
    try {
      await api.post(`/macros/${macroId}/execute`, {
        deviceId: selectedDeviceId || undefined,
        maxRetries: 1,
      });
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to execute macro");
    } finally {
      setRunningMacroId(null);
    }
  };

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Cloud Macros</h1>
        <p className="text-foreground/60 mt-2">Create once, sync everywhere, execute in real time.</p>
      </div>

      {error && (
        <div className="p-3 rounded-xl border border-red-500/20 bg-red-500/10 text-red-400 text-sm">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-8">
        <div className="xl:col-span-2 space-y-4">
          <div className="bg-card border border-border rounded-3xl p-5 flex items-center justify-between">
            <div>
              <h2 className="text-lg font-bold">Saved Macros</h2>
              <p className="text-sm text-foreground/60">Execute automations on any online device.</p>
            </div>
            <div className="flex items-center gap-3">
              <label className="text-sm text-foreground/70">Target device</label>
              <select
                value={selectedDeviceId}
                onChange={(e) => setSelectedDeviceId(e.target.value)}
                className="bg-background border border-border rounded-lg px-3 py-2 text-sm"
              >
                <option value="">Auto select</option>
                {onlineDevices.map((device) => (
                  <option key={device.id} value={device.id}>
                    {device.device_name}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {loading ? (
            <div className="flex justify-center py-16">
              <Loader2 className="w-6 h-6 text-primary animate-spin" />
            </div>
          ) : macros.length === 0 ? (
            <div className="bg-card border border-border rounded-3xl p-10 text-center text-foreground/60">
              No macros yet. Create your first cloud automation.
            </div>
          ) : (
            macros.map((macro) => (
              <div key={macro.id} className="bg-card border border-border rounded-3xl p-6">
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <h3 className="text-lg font-bold capitalize">{macro.name}</h3>
                    <p className="text-sm text-foreground/60 mt-1">{macro.description || "No description"}</p>
                    <p className="text-xs text-foreground/50 mt-2">
                      {macro.steps.length} step(s) · delay {macro.delay_ms} ms · {macro.is_active ? "active" : "disabled"}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => handleRun(macro.id)}
                      disabled={runningMacroId === macro.id || onlineDevices.length === 0}
                      className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground font-semibold disabled:opacity-50"
                    >
                      {runningMacroId === macro.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                      Run
                    </button>
                    <button
                      onClick={() => handleDelete(macro.id)}
                      className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-border hover:bg-background"
                    >
                      <Trash2 className="w-4 h-4 text-red-400" />
                    </button>
                  </div>
                </div>

                <pre className="mt-4 p-4 rounded-xl bg-background border border-border text-xs overflow-x-auto text-foreground/80">
                  {JSON.stringify(macro.steps, null, 2)}
                </pre>
              </div>
            ))
          )}
        </div>

        <div className="bg-card border border-border rounded-3xl p-6 h-fit">
          <h2 className="text-lg font-bold flex items-center gap-2">
            <Plus className="w-5 h-5 text-primary" />
            New Macro
          </h2>

          <form onSubmit={handleCreate} className="mt-5 space-y-4">
            <div>
              <label className="text-sm text-foreground/70">Name</label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                className="mt-1 w-full bg-background border border-border rounded-lg px-3 py-2 text-sm"
                placeholder="morning routine"
              />
            </div>

            <div>
              <label className="text-sm text-foreground/70">Description</label>
              <input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="mt-1 w-full bg-background border border-border rounded-lg px-3 py-2 text-sm"
                placeholder="Open WhatsApp and send updates"
              />
            </div>

            <div>
              <label className="text-sm text-foreground/70">Steps (JSON)</label>
              <textarea
                value={stepsJson}
                onChange={(e) => setStepsJson(e.target.value)}
                required
                className="mt-1 w-full h-44 bg-background border border-border rounded-lg px-3 py-2 text-xs font-mono"
              />
            </div>

            <button
              type="submit"
              disabled={saving}
              className="w-full inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground font-semibold disabled:opacity-50"
            >
              {saving ? <Loader2 className="w-4 h-4 animate-spin" /> : <WandSparkles className="w-4 h-4" />}
              Create Macro
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
