import type { Metadata } from "next";
import { TopNavbar } from "@/components/TopNavbar";
import "./globals.css";

export const metadata: Metadata = {
  title: "VoiceOS Cloud",
  description: "AI Voice Automation SaaS Platform",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="font-sans bg-background text-foreground">
        <TopNavbar />
        {children}
      </body>
    </html>
  );
}
