"use client";

import { useEffect, useState } from "react";
import api from "@/lib/api";
import { Smartphone, Terminal, Loader2, Send } from "lucide-react";

export default function DevicesPage() {
  const [devices, setDevices] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedDevice, setSelectedDevice] = useState<string | null>(null);
  const [commandInput, setCommandInput] = useState("");
  const [sending, setSending] = useState(false);

  useEffect(() => {
    fetchDevices();
  }, []);

  const fetchDevices = async () => {
    try {
      const res = await api.get("/devices");
      if (res.data.success) setDevices(res.data.devices);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSendCommand = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commandInput.trim() || !selectedDevice) return;
    setSending(true);
    
    try {
      // First parse natural language to structured via the generic endpoint
      // Then the backend sends it via WS to the device since deviceId is provided
      await api.post("/commands", {
        input: commandInput,
        deviceId: selectedDevice
      });
      setCommandInput("");
    } catch (err) {
      console.error(err);
      alert("Failed to send command");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="max-w-5xl mx-auto space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">My Devices</h1>
        <p className="text-foreground/60 mt-2">Manage connected devices and send remote commands.</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
        {/* Device List */}
        <div className="md:col-span-2 space-y-4">
          {loading ? (
             <div className="flex justify-center py-10"><Loader2 className="animate-spin text-primary w-6 h-6" /></div>
          ) : devices.length === 0 ? (
             <div className="p-8 border border-border rounded-3xl bg-card text-center text-foreground/60">
                No devices found. Open the VoiceOS Android app and sign in to connect your first device.
             </div>
          ) : (
            devices.map(dev => (
               <div 
                 key={dev.id} 
                 onClick={() => setSelectedDevice(dev.id)}
                 className={`p-6 rounded-2xl border cursor-pointer transition-all ${
                    selectedDevice === dev.id 
                    ? "border-primary bg-primary/5" 
                    : "border-border bg-card hover:border-border/80"
                 }`}
               >
                  <div className="flex items-center justify-between">
                     <div className="flex items-center gap-4">
                        <div className="w-12 h-12 bg-primary/10 rounded-full flex items-center justify-center">
                           <Smartphone className="w-6 h-6 text-primary" />
                        </div>
                        <div>
                           <h3 className="font-bold text-lg">{dev.device_name}</h3>
                           <p className="text-sm text-foreground/50 capitalize">{dev.device_type}</p>
                        </div>
                     </div>
                     <div className="flex items-center gap-2">
                        <span className="relative flex h-3 w-3">
                          <span className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${dev.status === 'online' ? 'bg-green-400' : 'bg-gray-400'}`}></span>
                          <span className={`relative inline-flex rounded-full h-3 w-3 ${dev.status === 'online' ? 'bg-green-500' : 'bg-gray-500'}`}></span>
                        </span>
                        <span className="text-xs uppercase tracking-wider font-bold text-foreground/60">{dev.status}</span>
                     </div>
                  </div>
               </div>
            ))
          )}
        </div>

        {/* Remote Terminal */}
        <div className="md:col-span-1">
           <div className="bg-card border border-border rounded-3xl p-6 sticky top-8">
              <h3 className="text-lg font-bold mb-4 flex items-center gap-2">
                 <Terminal className="w-5 h-5 text-primary" />
                 Remote Terminal
              </h3>
              
              {!selectedDevice ? (
                 <p className="text-sm text-foreground/50 py-4">Select a device to send remote commands.</p>
              ) : (
                 <form onSubmit={handleSendCommand} className="flex flex-col gap-4">
                    <p className="text-sm text-primary mb-2 font-medium">Ready to send to device.</p>
                    <textarea 
                       value={commandInput}
                       onChange={(e) => setCommandInput(e.target.value)}
                       placeholder="e.g. 'open whatsapp' or 'scroll down'"
                       className="w-full h-32 p-3 bg-background border border-border rounded-lg text-sm focus:outline-none focus:ring-1 focus:ring-primary resize-none placeholder-foreground/30"
                       required
                    />
                    <button 
                       type="submit"
                       disabled={sending || devices.find(d => d.id === selectedDevice)?.status !== 'online'}
                       className="w-full flex items-center justify-center gap-2 py-3 bg-primary text-primary-foreground font-bold rounded-lg hover:bg-primary/90 disabled:opacity-50 transition-colors"
                    >
                       {sending ? <Loader2 className="w-4 h-4 animate-spin" /> : <><Send className="w-4 h-4" /> Send Command</>}
                    </button>
                    {devices.find(d => d.id === selectedDevice)?.status !== 'online' && (
                       <p className="text-xs text-red-400 text-center">Device must be online to receive commands.</p>
                    )}
                 </form>
              )}
           </div>
        </div>
      </div>
    </div>
  );
}
