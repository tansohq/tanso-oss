import "server-only";

import { TansoClient } from "@tansohq/sdk";

export function getTansoClient(): TansoClient {
  const apiKey = process.env.TANSO_API_KEY;

  if (!apiKey) {
    throw new Error(
      "TANSO_API_KEY is missing. Copy .env.example to .env.local before starting the example.",
    );
  }

  return new TansoClient({
    apiKey,
    baseUrl: process.env.TANSO_BASE_URL ?? "http://localhost:8080",
  });
}

export function getDemoIdentity() {
  return {
    customerReferenceId:
      process.env.TANSO_CUSTOMER_REFERENCE_ID ?? "demo-user",
    featureKey: process.env.TANSO_FEATURE_KEY ?? "ai.chat",
  };
}
