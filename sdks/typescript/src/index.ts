export interface TansoClientOptions {
  /**
   * A server-side Tanso key beginning with `sk_test_` or `sk_live_`.
   * Never expose this value to browser code.
   */
  apiKey: string;
  /** Defaults to http://localhost:8080. */
  baseUrl?: string;
  /** Optional fetch implementation for runtimes and tests. */
  fetch?: typeof globalThis.fetch;
  /** Optional headers included with every request. */
  headers?: Record<string, string>;
}

export interface TansoErrorBody {
  message?: string;
  detail?: string;
}

export interface TansoApiResponse<T> {
  success: boolean;
  data?: T;
  error?: TansoErrorBody;
  meta?: unknown[];
}

export interface Pagination {
  total: number;
  limit: number;
  offset: number;
  hasMore: boolean;
}

export interface PaginatedResponse<T> {
  items: T[];
  pagination: Pagination;
}

export interface CustomerInput {
  customerReferenceId?: string;
  firstName?: string;
  lastName?: string;
  email: string;
  phoneNumber?: string;
  address?: string;
}

export interface Customer {
  customerReferenceId: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  createdAt?: string;
  modifiedAt?: string;
  subscriptions?: unknown[];
  creditPools?: CreditPool[];
}

export interface UsageContext {
  eventName?: string;
  usageUnits?: number;
  meta?: Record<string, unknown>;
}

export interface EntitlementEvaluationInput {
  customerReferenceId: string;
  featureKey: string;
  usage?: UsageContext;
  context?: {
    idempotencyKey?: string;
    flowId?: string;
  };
}

export interface EntitlementDecision {
  referenceCustomerId: string;
  featureKey: string;
  allowed: boolean;
  meta?: {
    reason?: {
      description?: string;
    };
  };
  flowId?: string;
  usage?: {
    used?: number;
    limit?: number;
    remaining?: number;
  };
  simulation?: {
    requestedUsage: number;
    projectedUsage: number;
    projectedRemaining: number;
    wouldExceedLimit: boolean;
  };
  credit?: {
    denomination: string;
    balance: number;
    totalGranted: number;
    totalConsumed: number;
    hardLimit?: boolean;
  };
}

export interface EventInput {
  eventIdempotencyKey?: string;
  flowId?: string;
  featureKey?: string;
  featureId?: string;
  eventName: string;
  occurredAt?: string;
  customerId?: string;
  customerReferenceId?: string;
  stripeCustomerId?: string;
  subscriptionId?: string;
  entitlementId?: string;
  invoiceId?: string;
  costAmount?: number;
  revenueAmount?: number;
  usageUnits?: number;
  meta?: Record<string, unknown>;
  costInput?: {
    model?: string;
    modelProvider?: string;
    inputTokens?: number;
    outputTokens?: number;
  };
}

export interface EventIngestionResult {
  usageLimitExceeded?: boolean;
  message?: string;
}

export interface CreditPool {
  id: string;
  name: string;
  denomination: string;
  currency?: string;
  balance: number;
  totalGranted: number;
  totalConsumed: number;
  totalExpired: number;
  totalReversed: number;
  hardLimit?: boolean;
  status: "ACTIVE" | "FROZEN" | "DEPLETED" | "ARCHIVED" | string;
  metadata?: Record<string, unknown>;
  createdAt?: string;
}

export interface PageOptions {
  limit?: number;
  offset?: number;
}

export interface IngestEventOptions {
  /**
   * Sent as X-Idempotency-Key and takes precedence over the body field.
   */
  idempotencyKey?: string;
}

export class TansoApiError extends Error {
  readonly status: number;
  readonly detail: string | undefined;
  readonly response: unknown;

  constructor(
    message: string,
    options: { status: number; detail?: string; response?: unknown },
  ) {
    super(message);
    this.name = "TansoApiError";
    this.status = options.status;
    this.detail = options.detail;
    this.response = options.response;
  }
}

export class TansoClient {
  readonly baseUrl: string;
  private readonly apiKey: string;
  private readonly fetchImpl: typeof globalThis.fetch;
  private readonly defaultHeaders: Record<string, string>;

  constructor(options: TansoClientOptions) {
    if (!options.apiKey?.trim()) {
      throw new TypeError("TansoClient requires an apiKey");
    }

    const fetchImpl = options.fetch ?? globalThis.fetch;
    if (!fetchImpl) {
      throw new TypeError("TansoClient requires a fetch implementation");
    }

    this.apiKey = options.apiKey.trim();
    this.baseUrl = (options.baseUrl ?? "http://localhost:8080").replace(
      /\/+$/,
      "",
    );
    this.fetchImpl = fetchImpl;
    this.defaultHeaders = options.headers ?? {};
  }

  createCustomer(input: CustomerInput): Promise<Customer> {
    return this.request<Customer>("/api/v1/client/customers", {
      method: "POST",
      body: input,
    });
  }

  getCustomer(customerReferenceId: string): Promise<Customer> {
    return this.request<Customer>(
      `/api/v1/client/customers/${encodeURIComponent(customerReferenceId)}`,
    );
  }

  checkEntitlement(
    customerReferenceId: string,
    featureKey: string,
    options: { record?: boolean } = {},
  ): Promise<EntitlementDecision> {
    const query = new URLSearchParams({
      record: String(options.record ?? true),
    });

    return this.request<EntitlementDecision>(
      `/api/v1/client/entitlements/${encodeURIComponent(customerReferenceId)}/${encodeURIComponent(featureKey)}?${query}`,
    );
  }

  evaluateEntitlement(
    input: EntitlementEvaluationInput,
  ): Promise<EntitlementDecision> {
    return this.request<EntitlementDecision>("/api/v1/client/entitlements", {
      method: "POST",
      body: input,
    });
  }

  ingestEvent(
    input: EventInput,
    options: IngestEventOptions = {},
  ): Promise<EventIngestionResult | undefined> {
    const headers = options.idempotencyKey
      ? { "X-Idempotency-Key": options.idempotencyKey }
      : undefined;

    return this.request<EventIngestionResult | undefined>(
      "/api/v1/client/events",
      {
        method: "POST",
        body: input,
        ...(headers ? { headers } : {}),
      },
    );
  }

  listCreditPools(
    customerReferenceId: string,
    options: PageOptions = {},
  ): Promise<PaginatedResponse<CreditPool>> {
    const query = new URLSearchParams({
      limit: String(options.limit ?? 50),
      offset: String(options.offset ?? 0),
    });

    return this.request<PaginatedResponse<CreditPool>>(
      `/api/v1/client/credits/${encodeURIComponent(customerReferenceId)}/pools?${query}`,
    );
  }

  getCreditPool(
    customerReferenceId: string,
    poolId: string,
  ): Promise<CreditPool> {
    return this.request<CreditPool>(
      `/api/v1/client/credits/${encodeURIComponent(customerReferenceId)}/pools/${encodeURIComponent(poolId)}`,
    );
  }

  private async request<T>(
    path: string,
    options: {
      method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
      body?: unknown;
      headers?: Record<string, string>;
    } = {},
  ): Promise<T> {
    const headers = new Headers(this.defaultHeaders);
    headers.set("Accept", "application/json");
    headers.set("Authorization", `Bearer ${this.apiKey}`);

    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }
    for (const [name, value] of Object.entries(options.headers ?? {})) {
      headers.set(name, value);
    }

    const requestInit: RequestInit = {
      method: options.method ?? "GET",
      headers,
      ...(options.body === undefined
        ? {}
        : { body: JSON.stringify(options.body) }),
    };
    const response = await this.fetchImpl(
      `${this.baseUrl}${path}`,
      requestInit,
    );

    const payload = await parseResponse(response);
    const envelope = isApiResponse(payload)
      ? (payload as TansoApiResponse<T>)
      : undefined;

    if (!response.ok || envelope?.success === false) {
      const error = envelope?.error;
      const message =
        error?.message ??
        extractLooseError(payload) ??
        `Tanso request failed with status ${response.status}`;

      throw new TansoApiError(message, {
        status: response.status,
        ...(error?.detail ? { detail: error.detail } : {}),
        ...(payload !== undefined ? { response: payload } : {}),
      });
    }

    return envelope ? (envelope.data as T) : (payload as T);
  }
}

async function parseResponse(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) return undefined;

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return text;
  }
}

function isApiResponse(value: unknown): value is TansoApiResponse<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    typeof (value as { success?: unknown }).success === "boolean"
  );
}

function extractLooseError(value: unknown): string | undefined {
  if (typeof value === "string") return value;
  if (typeof value !== "object" || value === null) return undefined;

  const error = (value as { error?: unknown }).error;
  return typeof error === "string" ? error : undefined;
}
