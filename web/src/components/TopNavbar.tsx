"use client";

import Link from "next/link";
import { useState } from "react";
import { Menu, Mic, X } from "lucide-react";

export function TopNavbar() {
  const [isOpen, setIsOpen] = useState(false);

  const closeMenu = () => setIsOpen(false);

  return (
    <header className="sticky top-0 z-50 w-full border-b border-border/70 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
      <div className="mx-auto max-w-6xl px-4 md:px-6 py-4 flex items-center justify-between gap-3">
        <Link href="/" className="flex items-center gap-2" onClick={closeMenu}>
          <div className="w-7 h-7 rounded-full bg-primary/20 flex items-center justify-center shrink-0">
            <Mic className="w-4 h-4 text-primary" />
          </div>
          <span className="text-base md:text-xl font-bold tracking-tight leading-tight">VoiceOS Cloud</span>
        </Link>

        <button
          type="button"
          onClick={() => setIsOpen((prev) => !prev)}
          className="sm:hidden inline-flex items-center justify-center w-9 h-9 rounded-lg border border-border text-foreground/90"
          aria-label={isOpen ? "Close menu" : "Open menu"}
          aria-expanded={isOpen}
        >
          {isOpen ? <X className="w-4 h-4" /> : <Menu className="w-4 h-4" />}
        </button>

        <nav className="hidden sm:flex items-center gap-2 md:gap-6">
          <Link href="/#features" className="text-sm font-medium text-foreground/80 hover:text-primary transition-colors">Features</Link>
          <Link href="/pricing" className="text-sm font-medium text-foreground/80 hover:text-primary transition-colors">Pricing</Link>
          <Link href="/login" className="text-xs md:text-sm font-medium hover:text-primary transition-colors px-2 md:px-0">Sign In</Link>
          <Link href="/register" className="text-xs md:text-sm font-bold bg-primary text-primary-foreground px-3 md:px-5 py-2 md:py-2.5 rounded-full hover:bg-primary/90 transition-all shadow-lg shadow-primary/20">
            Get Started
          </Link>
        </nav>
      </div>

      {isOpen ? (
        <nav className="sm:hidden border-t border-border/70 px-4 pb-4 pt-3 flex flex-col gap-2">
          <Link href="/#features" onClick={closeMenu} className="px-3 py-2 rounded-lg text-sm font-medium text-foreground/85 hover:bg-border/50">Features</Link>
          <Link href="/pricing" onClick={closeMenu} className="px-3 py-2 rounded-lg text-sm font-medium text-foreground/85 hover:bg-border/50">Pricing</Link>
          <Link href="/login" onClick={closeMenu} className="px-3 py-2 rounded-lg text-sm font-medium text-foreground/85 hover:bg-border/50">Sign In</Link>
          <Link
            href="/register"
            onClick={closeMenu}
            className="mt-1 inline-flex items-center justify-center rounded-full bg-primary text-primary-foreground px-4 py-2.5 text-sm font-semibold hover:bg-primary/90"
          >
            Get Started
          </Link>
        </nav>
      ) : null}
    </header>
  );
}
