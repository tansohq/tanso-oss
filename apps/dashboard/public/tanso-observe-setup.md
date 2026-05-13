# Tanso Observe Setup Guide

> Drop this file into your project and ask your AI coding assistant to implement it.

## Instructions for the AI assistant

You are helping a developer integrate Tanso Observe into their codebase. Tanso tracks AI costs per customer, feature, and model.

**Before writing any code, ask the developer these questions:**

1. **Are you working in sandbox or production?** The API URL is different:
   - **Sandbox** (testing): `https://sandbox.api.tansohq.com`
   - **Production**: `https://api.tansohq.com`
2. **Which AI providers do you use?** (OpenAI, Anthropic, Google, Cohere, etc.)
3. **What language and framework is this project?** (Node.js/Express, Python/FastAPI, Go, etc.)
4. **Where do AI calls happen in your code?** (Ask them to point you to the files/functions that make AI API calls)
5. **What's your Stripe customer ID field?** Observe uses Stripe customer IDs (`cus_abc123`) to identify customers. Ask where in their request context this lives.
6. **What do you call the features that use AI?** (e.g., "summarization", "chat", "search", "image_generation" — these become `featureKey` values)
7. **Do you know what you charge per AI call?** If yes, you'll pass `revenueAmount` to enable per-event margin tracking.

---

## Integration Pattern

After every AI API call in their codebase, add a POST request to Tanso. Here is the shape:

```
POST {TANSO_API_URL}/api/v1/client/events
Authorization: Bearer {TANSO_API_KEY}
Content-Type: application/json

{
  "eventName": "chat_completion",
  "stripeCustomerId": "cus_abc123",
  "featureKey": "ai_summarization",
  "costInput": {
    "model": "gpt-4o",
    "costUnits": 1500
  }
}
```

### Environment variables to add

Ask the developer whether they're working in **sandbox** (testing) or **production**:

| Environment | API URL | API Key prefix |
|-------------|---------|----------------|
| Sandbox | `https://sandbox.api.tansohq.com` | `sk_test_...` |
| Production | `https://api.tansohq.com` | `sk_live_...` |

```
TANSO_API_KEY=sk_live_...   # from Tanso dashboard > Getting Started
TANSO_API_URL=https://api.tansohq.com  # or https://sandbox.api.tansohq.com for sandbox
```

---

## Field Reference

### Required
| Field | Description |
|-------|-------------|
| `eventName` | What happened: `chat_completion`, `embedding_created`, `image_generated`, etc. |
| `stripeCustomerId` | Stripe customer ID (`cus_...`). Used to identify the customer. |
| `featureKey` | Which product feature: `summarization`, `search`, `chat`, etc. |

### Recommended (enables auto-cost)
| Field | Description |
|-------|-------------|
| `costInput.model` | Model name exactly as the provider returns it: `gpt-4o`, `claude-3-5-sonnet-20241022`, etc. |
| `costInput.costUnits` | Token count. Use `total_tokens` from the response, or `input_tokens + output_tokens`. |

### Optional
| Field | Description |
|-------|-------------|
| `revenueAmount` | What you charge for this call in USD (e.g., credits x price). Enables per-event margin. |
| `costAmount` | Override auto-calculated cost with your own value in USD. |
| `costInput.modelProvider` | `openai`, `anthropic`, `google`, etc. Auto-detected from model name if omitted. |
| `usageUnits` | Display-only quantity (tokens, requests, etc.). |
| `meta` | JSON object for extra context: `{ "sessionId": "...", "inputTokens": 500, "outputTokens": 1000 }` |
| `eventIdempotencyKey` | Unique key to prevent duplicates on retry. Auto-generated if omitted. |

---

## Provider-Specific Examples

### OpenAI (Node.js)

```javascript
import OpenAI from 'openai'

const openai = new OpenAI()

async function chat(userId, prompt) {
  const response = await openai.chat.completions.create({
    model: 'gpt-4o',
    messages: [{ role: 'user', content: prompt }],
  })

  // Track with Tanso
  await fetch(`${process.env.TANSO_API_URL}/api/v1/client/events`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${process.env.TANSO_API_KEY}`,
    },
    body: JSON.stringify({
      eventName: 'chat_completion',
      stripeCustomerId: userId,
      featureKey: 'chat',
      costInput: {
        model: 'gpt-4o',
        costUnits: response.usage.total_tokens,
      },
    }),
  })

  return response.choices[0].message.content
}
```

### OpenAI (Python)

```python
from openai import OpenAI
import httpx, os

client = OpenAI()

def chat(user_id: str, prompt: str) -> str:
    response = client.chat.completions.create(
        model="gpt-4o",
        messages=[{"role": "user", "content": prompt}],
    )

    # Track with Tanso
    httpx.post(
        f"{os.environ['TANSO_API_URL']}/api/v1/client/events",
        headers={
            "Authorization": f"Bearer {os.environ['TANSO_API_KEY']}",
        },
        json={
            "eventName": "chat_completion",
            "stripeCustomerId": user_id,
            "featureKey": "chat",
            "costInput": {
                "model": "gpt-4o",
                "costUnits": response.usage.total_tokens,
            },
        },
    )

    return response.choices[0].message.content
```

### Anthropic (Node.js)

```javascript
import Anthropic from '@anthropic-ai/sdk'

const anthropic = new Anthropic()

async function chat(userId, prompt) {
  const response = await anthropic.messages.create({
    model: 'claude-sonnet-4-20250514',
    max_tokens: 1024,
    messages: [{ role: 'user', content: prompt }],
  })

  // Track with Tanso
  await fetch(`${process.env.TANSO_API_URL}/api/v1/client/events`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${process.env.TANSO_API_KEY}`,
    },
    body: JSON.stringify({
      eventName: 'chat_completion',
      stripeCustomerId: userId,
      featureKey: 'chat',
      costInput: {
        model: 'claude-sonnet-4-20250514',
        costUnits: response.usage.input_tokens + response.usage.output_tokens,
      },
    }),
  })

  return response.content[0].text
}
```

### Anthropic (Python)

```python
import anthropic, httpx, os

client = anthropic.Anthropic()

def chat(user_id: str, prompt: str) -> str:
    response = client.messages.create(
        model="claude-sonnet-4-20250514",
        max_tokens=1024,
        messages=[{"role": "user", "content": prompt}],
    )

    # Track with Tanso
    httpx.post(
        f"{os.environ['TANSO_API_URL']}/api/v1/client/events",
        headers={
            "Authorization": f"Bearer {os.environ['TANSO_API_KEY']}",
        },
        json={
            "eventName": "chat_completion",
            "stripeCustomerId": user_id,
            "featureKey": "chat",
            "costInput": {
                "model": "claude-sonnet-4-20250514",
                "costUnits": response.usage.input_tokens + response.usage.output_tokens,
            },
        },
    )

    return response.content[0].text
```

---

## Implementation Checklist

After asking the developer the setup questions:

1. [ ] Add `TANSO_API_KEY` and `TANSO_API_URL` to environment variables
2. [ ] Find every place in the codebase where AI API calls are made
3. [ ] After each AI call, add the Tanso tracking POST with the correct `eventName`, customer ID, `featureKey`, and `costInput`
4. [ ] Use fire-and-forget pattern (don't await or let Tanso tracking failures break the main flow)
5. [ ] If they have a shared AI client wrapper, add tracking there once instead of at every call site
6. [ ] Test by running the app and checking the Tanso dashboard for incoming events

## Important

- Tanso tracking should never block or break the main application flow. Wrap in try/catch or fire-and-forget.
- The `model` field should match exactly what the provider returns (e.g., `gpt-4o`, not `GPT4`).
- Cost is auto-calculated from model + token count. Over 100 models are supported with auto-refreshed pricing.
- If the developer doesn't have token counts available (e.g., streaming), they can pass `costAmount` directly in USD.
