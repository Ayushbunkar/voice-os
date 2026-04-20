/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone', // Useful for Docker deployment
  async headers() {
    return [
      {
        source: '/:path*.apk',
        headers: [
          {
            key: 'Content-Type',
            value: 'application/vnd.android.package-archive',
          },
          {
            key: 'Content-Disposition',
            value: 'attachment',
          },
          {
            key: 'Cache-Control',
            value: 'public, max-age=300',
          },
        ],
      },
    ];
  },
};

export default nextConfig;
