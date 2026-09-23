// Retake the console screenshots used on tansohq.com.
//
// Every shot is 1600x1000, dark theme, against whatever the demo seed
// currently holds. Run the seed first if the console is empty:
//   psql ... -f scripts/seed-margin-demo.sql
//
// Usage:
//   node scripts/shoot-console.mjs
//
// Env:
//   CONSOLE_URL  default http://localhost:3000
//   TANSO_USER   default test
//   TANSO_PASS   default password
//   OUT_DIR      default the tansohq.com-launch public/screenshots folder

import { chromium } from "playwright"
import { mkdir } from "node:fs/promises"
import path from "node:path"

const CONSOLE_URL = process.env.CONSOLE_URL ?? "http://localhost:3000"
const USER = process.env.TANSO_USER ?? "test"
const PASS = process.env.TANSO_PASS ?? "password"
const OUT_DIR =
  process.env.OUT_DIR ??
  path.resolve(
    process.env.HOME,
    "Desktop/Github-Wiki/GitHub/tansohq.com-launch/public/screenshots"
  )

const VIEWPORT = { width: 1600, height: 1000 }

// Each shot names the file it writes, the route it opens, and an optional
// step to run once the route has settled.
const SHOTS = [
  { file: "customers.png", route: "/customers" },
  { file: "spend-usage.png", route: "/spend/usage" },
  { file: "spend-outcomes.png", route: "/spend/outcomes" },
  { file: "spend-reconcile.png", route: "/spend/reconcile" },
  { file: "spend-savings.png", route: "/spend/savings" },
  { file: "spend-digest.png", route: "/spend/alerts" },
  { file: "spend-teams.png", route: "/spend/teams" },
  { file: "spend-pnl.png", route: "/spend/pnl" },
  { file: "console-overview.png", route: "/" },
  {
    file: "weights.png",
    route: "/credits",
    async after(page) {
      await page.getByRole("tab", { name: "Weights" }).click()
      await page.waitForTimeout(1200)
    },
  },
  {
    file: "spend-gateway-budget.png",
    route: "/spend/teams",
    async after(page) {
      await page.getByRole("row", { name: /^Platform/ }).click()
      // The drawer slides in; capturing mid-animation is what blurred and
      // clipped the previous version of this shot.
      await page.waitForTimeout(2500)
      await page
        .getByText("Two clocks, UTC calendar", { exact: false })
        .scrollIntoViewIfNeeded()
      await page.waitForTimeout(800)
    },
  },
]

const browser = await chromium.launch()
const context = await browser.newContext({
  viewport: VIEWPORT,
  deviceScaleFactor: 1,
  colorScheme: "dark",
})

// next-themes reads this before first paint, so the console never flashes light.
await context.addInitScript(() => {
  window.localStorage.setItem("theme", "dark")
})

const page = await context.newPage()

await page.goto(`${CONSOLE_URL}/login`, { waitUntil: "networkidle" })
await page.getByLabel("Email or username").fill(USER)
await page.getByLabel("Password").fill(PASS)
await page.getByRole("button", { name: "Sign in" }).click()
await page.waitForURL((url) => !url.pathname.startsWith("/login"), {
  timeout: 15000,
})

await mkdir(OUT_DIR, { recursive: true })

for (const shot of SHOTS) {
  await page.goto(`${CONSOLE_URL}${shot.route}`, { waitUntil: "networkidle" })
  await page.addStyleTag({ content: "nextjs-portal{display:none!important}" })
  // Let the tables and charts finish their entrance before the shutter.
  await page.waitForTimeout(1800)

  if (shot.after) await shot.after(page)

  const out = path.join(OUT_DIR, shot.file)
  await page.screenshot({ path: out })
  console.log(`wrote ${out}`)
}

await browser.close()
console.log(`\n${SHOTS.length} shots written to ${OUT_DIR}`)
