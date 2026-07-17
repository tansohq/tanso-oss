"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";

interface Decision {
  allowed: boolean;
  meta?: {
    reason?: {
      description?: string;
    };
  };
  credit?: {
    denomination: string;
    balance: number;
    totalGranted: number;
    totalConsumed: number;
    hardLimit?: boolean;
  };
}

interface GenerateResponse {
  answer?: string;
  error?: string;
  requestId?: string;
  decision?: Decision;
}

const starterPrompt = "Summarize why hard credit limits matter for an AI API.";

export default function Home() {
  const [prompt, setPrompt] = useState(starterPrompt);
  const [decision, setDecision] = useState<Decision>();
  const [answer, setAnswer] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(false);
  const [connecting, setConnecting] = useState(true);

  const loadStatus = useCallback(async () => {
    setConnecting(true);
    try {
      const response = await fetch("/api/status", { cache: "no-store" });
      const payload = (await response.json()) as GenerateResponse;
      if (!response.ok) throw new Error(payload.error ?? "Tanso is unavailable");
      setDecision(payload.decision);
      setError(undefined);
    } catch (statusError) {
      setError(
        statusError instanceof Error
          ? statusError.message
          : "Tanso is unavailable",
      );
    } finally {
      setConnecting(false);
    }
  }, []);

  useEffect(() => {
    void loadStatus();
  }, [loadStatus]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setAnswer(undefined);
    setError(undefined);

    try {
      const response = await fetch("/api/generate", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Request-Id": crypto.randomUUID(),
        },
        body: JSON.stringify({ prompt }),
      });
      const payload = (await response.json()) as GenerateResponse;

      if (!response.ok) {
        throw new Error(payload.error ?? "The request was denied");
      }

      setAnswer(payload.answer);
      setDecision(payload.decision);
    } catch (requestError) {
      setError(
        requestError instanceof Error
          ? requestError.message
          : "The request failed",
      );
      await loadStatus();
    } finally {
      setLoading(false);
    }
  }

  const credit = decision?.credit;
  const balance = Number(credit?.balance ?? 0);
  const granted = Number(credit?.totalGranted ?? 5);
  const percent = granted > 0 ? Math.max(0, (balance / granted) * 100) : 0;

  return (
    <main>
      <a className="skipLink" href="#demo-workbench">
        Skip to demo
      </a>
      <div className="ambient" aria-hidden="true" />
      <section className="shell">
        <header className="masthead">
          <a className="wordmark" href="https://tansohq.com">
            TANSO
          </a>
          <div className="connection" role="status" aria-live="polite">
            <span
              className={`signal ${error ? "signalError" : ""}`}
              aria-hidden="true"
            />
            {connecting ? "connecting" : error ? "check setup" : "core online"}
          </div>
        </header>

        <div className="grid">
          <div className="intro">
            <p className="eyebrow">Executable example / 01</p>
            <h1>
              Spend a credit.
              <br />
              <em>Prove the limit.</em>
            </h1>
            <p className="lede">
              The API route checks entitlement before the model call, records
              cost and revenue afterward, and lets Tanso atomically deduct the
              credit.
            </p>

            <ol className="flow" aria-label="Request flow">
              <li>
                <span>01</span>
                Evaluate entitlement
              </li>
              <li>
                <span>02</span>
                Run provider call
              </li>
              <li>
                <span>03</span>
                Record usage + margin
              </li>
            </ol>
          </div>

          <div className="workbench" id="demo-workbench">
            <div className="ledger">
              <div>
                <span className="label">Available</span>
                <strong>{connecting ? "—" : balance}</strong>
                <small>{credit?.denomination ?? "AI_CREDITS"}</small>
              </div>
              <div
                className="meter"
                role="progressbar"
                aria-label="Credits remaining"
                aria-valuemin={0}
                aria-valuemax={granted}
                aria-valuenow={balance}
              >
                <span style={{ width: `${percent}%` }} />
              </div>
              <p>
                Hard limit{" "}
                <b>{credit?.hardLimit === false ? "disabled" : "armed"}</b>
              </p>
            </div>

            <form onSubmit={submit}>
              <label htmlFor="prompt">Prompt</label>
              <textarea
                id="prompt"
                value={prompt}
                onChange={(event) => setPrompt(event.target.value)}
                maxLength={2_000}
                rows={5}
                aria-describedby="prompt-help"
              />
              <p className="formHelp" id="prompt-help">
                Each successful request consumes one of the five demo credits.
              </p>
              <button
                type="submit"
                disabled={loading || connecting || !prompt.trim()}
              >
                {loading ? "Running request…" : "Run one-credit request"}
                <span aria-hidden="true">↗</span>
              </button>
            </form>

            <div
              className="result"
              role="status"
              aria-live="polite"
              aria-atomic="true"
            >
              <span className="label">Result</span>
              {error ? (
                <p className="error" role="alert">
                  {error}
                </p>
              ) : answer ? (
                <p>{answer}</p>
              ) : (
                <p className="muted">
                  Run the request five times. The sixth is denied before any
                  provider cost is incurred.
                </p>
              )}
            </div>
          </div>
        </div>

        <footer>
          <code>POST /api/v1/client/entitlements</code>
          <code>POST /api/v1/client/events</code>
          <a href="https://github.com/tansohq/tanso-oss">
            View implementation ↗
          </a>
        </footer>
      </section>
    </main>
  );
}
