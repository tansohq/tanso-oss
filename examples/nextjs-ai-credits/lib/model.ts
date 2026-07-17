/**
 * Replace this function with an OpenAI, Anthropic, or other model call.
 *
 * The example stays provider-free so the full Tanso path can be tested without
 * another API key or a credit card.
 */
export async function runModel(prompt: string): Promise<{
  text: string;
  inputTokens: number;
  outputTokens: number;
}> {
  await new Promise((resolve) => setTimeout(resolve, 350));

  const inputTokens = Math.max(8, Math.ceil(prompt.length / 4));
  const text =
    "Your request passed the entitlement check. Tanso recorded one unit of usage, deducted one credit, and kept the provider key on the server.";

  return {
    text,
    inputTokens,
    outputTokens: Math.ceil(text.length / 4),
  };
}
