import Link from "next/link";
import { CheckCircle2 } from "lucide-react";

const plans = [
  {
    name: "Starter",
    price: "$0",
    period: "/month",
    description: "Best for exploring VoiceOS Cloud.",
    features: ["50 commands/day", "1 connected device", "Basic automations"],
    cta: "Start Free",
    href: "/register",
    highlighted: false,
  },
  {
    name: "Pro",
    price: "$19",
    period: "/month",
    description: "Ideal for power users and creators.",
    features: ["1000 commands/day", "Up to 5 devices", "Cloud macro sync", "Priority support"],
    cta: "Choose Pro",
    href: "/register",
    highlighted: true,
  },
  {
    name: "Enterprise",
    price: "$99",
    period: "/month",
    description: "For teams and business workflows.",
    features: ["Unlimited commands", "Unlimited devices", "Advanced analytics", "Admin controls"],
    cta: "Contact Sales",
    href: "/register",
    highlighted: false,
  },
];

export default function PricingPage() {
  return (
    <main className="min-h-screen bg-background px-4 md:px-6 py-10 md:py-16">
      <section className="max-w-6xl mx-auto">
        <div className="text-center mb-10 md:mb-14">
          <h1 className="text-4xl md:text-6xl font-extrabold tracking-tight mb-4">Simple Pricing</h1>
          <p className="text-foreground/70 max-w-2xl mx-auto text-base md:text-lg">
            Pick a plan that matches your voice automation needs. Upgrade anytime.
          </p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-5 md:gap-6">
          {plans.map((plan) => (
            <article
              key={plan.name}
              className={`rounded-3xl border p-6 md:p-8 flex flex-col ${
                plan.highlighted
                  ? "border-primary bg-primary/5 shadow-xl shadow-primary/10"
                  : "border-border bg-card"
              }`}
            >
              <h2 className="text-2xl font-bold mb-2">{plan.name}</h2>
              <p className="text-foreground/70 mb-5">{plan.description}</p>

              <div className="mb-6">
                <span className="text-4xl font-extrabold">{plan.price}</span>
                <span className="text-foreground/60">{plan.period}</span>
              </div>

              <ul className="space-y-3 mb-8 flex-1">
                {plan.features.map((feature) => (
                  <li key={feature} className="flex items-center gap-2 text-foreground/90">
                    <CheckCircle2 className="w-4 h-4 text-primary" />
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>

              <Link
                href={plan.href}
                className={`rounded-full px-5 py-3 text-center font-semibold transition-colors ${
                  plan.highlighted
                    ? "bg-primary text-primary-foreground hover:bg-primary/90"
                    : "bg-border/50 text-foreground hover:bg-border"
                }`}
              >
                {plan.cta}
              </Link>
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
