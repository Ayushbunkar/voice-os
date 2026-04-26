"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Mic, LayoutDashboard, Smartphone, Zap, History, CreditCard, LogOut, Shield } from "lucide-react";
import { useStore } from "@/lib/store";

export function Sidebar() {
  const pathname = usePathname();
  const user = useStore((state) => state.user);
  const logout = useStore((state) => state.logout);
  

  const navItems = [
    { name: "Overview", href: "/dashboard", icon: LayoutDashboard },
    { name: "My Devices", href: "/dashboard/devices", icon: Smartphone },
    { name: "Automations", href: "/dashboard/macros", icon: Zap },
    { name: "History", href: "/dashboard/history", icon: History },
    { name: "Billing", href: "/dashboard/billing", icon: CreditCard },
  ];

  if (user?.isAdmin) {
    navItems.push({ name: "Admin", href: "/dashboard/admin", icon: Shield });
  }

  return (
    <div className="w-64 h-screen bg-card border-r border-border flex flex-col fixed left-0 top-0">
      <div className="p-6 flex items-center gap-3">
        <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center">
          <Mic className="w-5 h-5 text-primary" />
        </div>
        <span className="text-lg font-bold tracking-tight">VoiceOS</span>
      </div>

      <div className="flex-1 px-4 py-4 space-y-1 overflow-y-auto">
        {navItems.map((item) => {
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium transition-colors ${
                isActive 
                  ? "bg-primary/10 text-primary" 
                  : "text-foreground/70 hover:bg-border/50 hover:text-foreground"
              }`}
            >
              <item.icon className="w-5 h-5" />
              {item.name}
            </Link>
          );
        })}
      </div>

      <div className="p-4 border-t border-border">
        <div className="flex items-center gap-3 px-3 py-2 mb-2">
           <div className="w-8 h-8 rounded-full bg-border flex items-center justify-center font-bold">
              {user?.name?.charAt(0).toUpperCase() || "U"}
           </div>
           <div className="flex flex-col">
              <span className="text-sm font-semibold truncate w-32">{user?.name || "User"}</span>
              <span className="text-xs text-primary uppercase tracking-wider font-bold">{user?.plan} PLAN</span>
           </div>
        </div>
        <button
          onClick={() => {
            logout();
            window.location.href = "/login";
          }}
          className="flex items-center gap-3 px-3 py-2 w-full text-left rounded-lg font-medium text-red-400 hover:bg-red-400/10 transition-colors"
        >
          <LogOut className="w-5 h-5" />
          Sign out
        </button>
      </div>
    </div>
  );
}
