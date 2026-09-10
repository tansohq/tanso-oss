import type { NextConfig } from "next"

const nextConfig: NextConfig = {
  // The dev-server indicator sits on the sidebar footer and ends up in every screen recording of
  // the console. Off only when a recording asks for it; normal development keeps it.
  ...(process.env.TANSO_DEMO_RECORDING ? { devIndicators: false } : {}),
}

export default nextConfig
