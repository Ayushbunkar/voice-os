import Link from "next/link";
import { ArrowRight, Smartphone, Bot, Zap } from "lucide-react";

export default function LandingPage() {
  return (
    <div className="min-h-screen bg-background flex flex-col items-center">
      {/* Hero Section */}
      <main className="flex-1 w-full max-w-6xl px-4 md:px-6 flex flex-col items-center justify-center text-center mt-8 md:mt-16 relative z-10">
        {/* Subtle glow background */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-primary/10 blur-[120px] rounded-full -z-10" />

        <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-primary/10 text-primary text-xs md:text-sm font-medium mb-6 md:mb-8 border border-primary/20">
          <Zap className="w-3 h-3 md:w-4 md:h-4" />
          <span>VoiceOS Phase 3 is now live</span>
        </div>

        <h1 className="text-4xl sm:text-5xl md:text-7xl font-extrabold tracking-tight mb-6 md:mb-8 leading-tight max-w-4xl">
          Control Your Android World With <span className="text-transparent bg-clip-text bg-gradient-to-r from-primary to-blue-400">Your Voice.</span>
        </h1>
        
        <p className="text-base md:text-xl text-foreground/70 max-w-2xl mb-8 md:mb-12">
          The ultimate AI-powered accessibility and automation platform. Seamlessly sync macros, execute multi-step commands, and control devices remotely.
        </p>

        <div className="flex flex-col sm:flex-row items-center gap-3 md:gap-4 w-full sm:w-auto">
          <Link href="/login" className="group flex items-center justify-center gap-2 bg-primary text-primary-foreground px-8 py-4 rounded-full text-base md:text-lg font-bold hover:bg-primary/90 transition-all w-full sm:w-auto shadow-xl shadow-primary/20">
            Go to Dashboard
            <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
          </Link>
          <Link href="/download-apk" className="flex items-center justify-center gap-2 bg-card text-card-foreground border border-border px-8 py-4 rounded-full text-base md:text-lg font-bold hover:bg-border/50 transition-all w-full sm:w-auto">
            <Smartphone className="w-5 h-5" />
            Download APK
          </Link>
        </div>

        {/* Feature Grid */}
        <div id="features" className="grid grid-cols-1 md:grid-cols-3 gap-4 md:gap-6 w-full mt-16 md:mt-32 text-left">
          <div className="bg-card border border-border p-8 rounded-3xl">
            <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center mb-6">
              <Bot className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-3">AI Command Understanding</h3>
            <p className="text-foreground/60 leading-relaxed">Advanced natural language understanding translates your voice into precise Android UI commands.</p>
          </div>
          <div className="bg-card border border-border p-8 rounded-3xl">
            <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center mb-6">
              <Zap className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-3">Cloud Macros</h3>
            <p className="text-foreground/60 leading-relaxed">Create complex automation flows on the web dashboard and instantly sync them to all your devices.</p>
          </div>
          <div className="bg-card border border-border p-8 rounded-3xl">
            <div className="w-12 h-12 rounded-2xl bg-primary/10 flex items-center justify-center mb-6">
              <Smartphone className="w-6 h-6 text-primary" />
            </div>
            <h3 className="text-xl font-bold mb-3">Remote Control</h3>
            <p className="text-foreground/60 leading-relaxed">Send commands to your phone directly from the web interface in real-time via WebSockets.</p>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full border-t border-border mt-32 py-8 text-center text-foreground/50 text-sm">
        <p>© 2026 VoiceOS. Built by DeepMind Advanced Agentic Coding.</p>
      </footer>
    </div>
  );
}
