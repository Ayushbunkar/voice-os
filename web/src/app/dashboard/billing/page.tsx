"use client";

import { useEffect, useMemo, useState } from "react";
import { CreditCard, Loader2, Rocket } from "lucide-react";
import api from "@/lib/api";
import { useStore } from "@/lib/store";

interface Plan {
  id: "free" | "pro" | "enterprise";
  name: string;
  price: number;
  currency: string;
  priceId?: string;
  razorpayPlanId?: string;
  commandsPerDay: number;
  features: string[];
}

interface Summary {
  totalCommands: number;
  commandsToday: number;
  activeDevices: number;
  totalMacros: number;
  successRate: number;
}

export default function BillingPage() {
  const user = useStore((state) => state.user);

  const [provider, setProvider] = useState<"stripe" | "razorpay">("stripe");
  const [plans, setPlans] = useState<Plan[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyPlan, setBusyPlan] = useState<string | null>(null);
  const [portalLoading, setPortalLoading] = useState(false);
  const [error, setError] = useState("");

  const currentPlan = user?.plan || "free";

  const upgradePlans = useMemo(
    () => plans.filter((plan) => plan.id !== "free"),
    [plans]
  );

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        const [plansRes, summaryRes] = await Promise.all([
          api.get("/billing/plans"),
          api.get("/analytics/summary"),
        ]);

        if (plansRes.data.success) {
          setPlans(plansRes.data.plans || []);
        }

        if (summaryRes.data.success) {
          setSummary(summaryRes.data.data || null);
        }
      } catch (err: any) {
        setError(err.response?.data?.message || "Failed to load billing");
      } finally {
        setLoading(false);
      }
    };

    load();
  }, []);

  const startCheckout = async (plan: Plan) => {
    setBusyPlan(plan.id);
    setError("");
    try {
      const payload = provider === "stripe"
        ? { provider, priceId: plan.priceId }
        : { provider, plan: plan.id };

      const res = await api.post("/billing/checkout", payload);

      if (provider === "stripe" && res.data.url) {
        window.location.href = res.data.url;
        return;
      }

      if (provider === "razorpay" && res.data.shortUrl) {
        window.location.href = res.data.shortUrl;
        return;
      }

      setError("Checkout session was created but redirect URL was missing.");
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to start checkout");
    } finally {
      setBusyPlan(null);
    }
  };

  const openPortal = async () => {
    setPortalLoading(true);
    setError("");
    try {
      const res = await api.post("/billing/portal", { provider: "stripe" });
      if (res.data.url) {
        window.location.href = res.data.url;
      }
    } catch (err: any) {
      setError(err.response?.data?.message || "Failed to open billing portal");
    } finally {
      setPortalLoading(false);
    }
  };

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
        <h1 className="text-3xl font-bold tracking-tight">Billing & Plans</h1>
        <p className="text-foreground/60 mt-2">Manage subscription, limits, and payment provider.</p>
      </div>

      {error && (
        <div className="p-3 rounded-xl border border-red-500/20 bg-red-500/10 text-red-400 text-sm">
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-4">
        <div className="bg-card border border-border rounded-2xl p-4">
          <p className="text-xs text-foreground/50">Current Plan</p>
          <p className="text-xl font-bold uppercase">{currentPlan}</p>
        </div>
        <div className="bg-card border border-border rounded-2xl p-4">
          <p className="text-xs text-foreground/50">Commands Today</p>
          <p className="text-xl font-bold">{summary?.commandsToday || 0}</p>
        </div>
        <div className="bg-card border border-border rounded-2xl p-4">
          <p className="text-xs text-foreground/50">Total Commands</p>
          <p className="text-xl font-bold">{summary?.totalCommands || 0}</p>
        </div>
        <div className="bg-card border border-border rounded-2xl p-4">
          <p className="text-xs text-foreground/50">Success Rate</p>
          <p className="text-xl font-bold">{summary?.successRate || 0}%</p>
        </div>
      </div>

      <div className="bg-card border border-border rounded-3xl p-5 flex items-center justify-between gap-4">
        <div>
          <h2 className="text-lg font-bold">Payment Provider</h2>
          <p className="text-sm text-foreground/60">Choose provider before checkout.</p>
        </div>

        <div className="inline-flex rounded-xl border border-border overflow-hidden">
          <button
            onClick={() => setProvider("stripe")}
            className={`px-4 py-2 text-sm font-semibold ${provider === "stripe" ? "bg-primary text-primary-foreground" : "bg-background"}`}
          >
            Stripe
          </button>
          <button
            onClick={() => setProvider("razorpay")}
            className={`px-4 py-2 text-sm font-semibold ${provider === "razorpay" ? "bg-primary text-primary-foreground" : "bg-background"}`}
          >
            Razorpay
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {upgradePlans.map((plan) => {
          const isCurrent = currentPlan === plan.id;
          return (
            <div key={plan.id} className="bg-card border border-border rounded-3xl p-6 flex flex-col">
              <div className="flex items-center justify-between">
                <h3 className="text-xl font-bold">{plan.name}</h3>
                <CreditCard className="w-5 h-5 text-primary" />
              </div>
              <p className="text-3xl font-extrabold mt-3">
                {plan.currency} {plan.price / 100}
                <span className="text-sm font-medium text-foreground/60"> / month</span>
              </p>
              <p className="text-sm text-foreground/60 mt-2">{plan.commandsPerDay} commands/day</p>

              <ul className="mt-4 space-y-2 text-sm text-foreground/80">
                {plan.features.map((feature) => (
                  <li key={feature}>• {feature}</li>
                ))}
              </ul>

              <button
                onClick={() => startCheckout(plan)}
                disabled={busyPlan === plan.id || isCurrent}
                className="mt-6 inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-primary text-primary-foreground font-semibold disabled:opacity-50"
              >
                {busyPlan === plan.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Rocket className="w-4 h-4" />}
                {isCurrent ? "Current Plan" : `Upgrade via ${provider}`}
              </button>
            </div>
          );
        })}
      </div>

      <div className="bg-card border border-border rounded-2xl p-5 flex items-center justify-between">
        <div>
          <h2 className="text-lg font-bold">Manage Subscription</h2>
          <p className="text-sm text-foreground/60">Open Stripe billing portal for invoices and cancellation.</p>
        </div>
        <button
          onClick={openPortal}
          disabled={portalLoading}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-border hover:bg-background disabled:opacity-50"
        >
          {portalLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
          Open Portal
        </button>
      </div>
    </div>
  );
}
