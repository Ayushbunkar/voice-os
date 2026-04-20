import Link from "next/link";
import { redirect } from "next/navigation";
import fs from "fs";
import path from "path";

export default function DownloadApkPage() {
  const apkUrl =
    process.env.NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL ||
    process.env.ANDROID_APP_DOWNLOAD_URL ||
    "";

  const isExternalUrl = /^https?:\/\//i.test(apkUrl);
  const isLocalAsset = apkUrl.startsWith("/");

  if (isExternalUrl) {
    redirect(apkUrl);
  }

  if (isLocalAsset) {
    const publicFilePath = path.join(process.cwd(), "public", apkUrl.replace(/^\//, ""));
    if (fs.existsSync(publicFilePath) && fs.statSync(publicFilePath).size > 0) {
      redirect(apkUrl);
    }
  }

  return (
    <main className="min-h-[70vh] bg-background px-4 md:px-6 py-10 md:py-16 flex items-center justify-center">
      <section className="max-w-xl w-full bg-card border border-border rounded-2xl p-6 md:p-8 text-center">
        <h1 className="text-2xl md:text-3xl font-bold mb-3">APK File Not Ready Yet</h1>
        <p className="text-foreground/70 mb-6">
          The download URL is configured, but the APK file is missing or empty. Upload a real APK to
          <span className="font-semibold"> web/public/voiceos-latest.apk</span> or set a working hosted URL
          in <span className="font-semibold">NEXT_PUBLIC_ANDROID_APP_DOWNLOAD_URL</span>.
        </p>
        <Link
          href="/"
          className="inline-flex items-center justify-center rounded-full px-5 py-3 bg-primary text-primary-foreground font-semibold hover:bg-primary/90 transition-colors"
        >
          Back to Home
        </Link>
      </section>
    </main>
  );
}
