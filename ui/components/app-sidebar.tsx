"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import {
  Activity,
  Bell,
  Coins,
  Gauge,
  GitMerge,
  LogOut,
  Package,
  Plug,
  Puzzle,
  ReceiptText,
  Repeat,
  Scale,
  Settings,
  Users,
  Wallet,
  PiggyBank,
  Landmark,
} from "lucide-react"

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarSeparator,
  useSidebar,
} from "@/components/ui/sidebar"
import { clearToken, getClaims } from "@/lib/auth"
import { isBuildSideOff, useVendorConnections } from "@/features/spend/queries"
import { usePortfolio } from "@/features/analytics/queries"
import { isModuleOff } from "@/lib/api/client"

// Monetization: the AI you sell. Plans, credits, margin per customer.
const monetizationNav = [
  { title: "Plans", href: "/plans", icon: Package },
  { title: "Features", href: "/features", icon: Puzzle },
  { title: "Customers", href: "/customers", icon: Users },
  { title: "Subscriptions", href: "/subscriptions", icon: Repeat },
  { title: "Credits", href: "/credits", icon: Coins },
  { title: "Invoices", href: "/invoices", icon: ReceiptText },
  { title: "Events", href: "/events", icon: Activity },
]

// Internal spend: the AI you buy. Connections first, since nothing else here
// shows anything until a vendor is connected. P&L needs both halves, so it
// is hidden when monetization is off.
const internalSpendNav = [
  { title: "Connections", href: "/spend/connections", icon: Plug },
  { title: "Spend", href: "/spend/usage", icon: Wallet },
  { title: "Teams", href: "/spend/teams", icon: Users },
  { title: "Alerts", href: "/spend/alerts", icon: Bell },
  { title: "Savings", href: "/spend/savings", icon: PiggyBank },
  { title: "Reconcile", href: "/spend/reconcile", icon: Scale },
  { title: "Outcomes", href: "/spend/outcomes", icon: GitMerge },
  { title: "P&L", href: "/spend/pnl", icon: Landmark },
]

export function AppSidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { isMobile, setOpenMobile } = useSidebar()
  const email = getClaims()?.email
  const vendorConnections = useVendorConnections()
  const buildSideOff = isBuildSideOff(vendorConnections.error)
  const portfolio = usePortfolio()
  const monetizationOff = isModuleOff(portfolio.error)

  // On mobile the sidebar is a sheet; a tap on a link should close it.
  function closeMobile() {
    if (isMobile) setOpenMobile(false)
  }

  function isActive(href: string) {
    return href === "/" ? pathname === "/" : pathname.startsWith(href)
  }

  function logout() {
    clearToken()
    router.replace("/login")
  }

  function renderItems(items: typeof monetizationNav) {
    return items.map((item) => (
      <SidebarMenuItem key={item.href}>
        <SidebarMenuButton
          render={<Link href={item.href} />}
          isActive={isActive(item.href)}
          onClick={closeMobile}
        >
          <item.icon />
          {item.title}
        </SidebarMenuButton>
      </SidebarMenuItem>
    ))
  }

  return (
    <Sidebar>
      <SidebarHeader>
        <div className="flex items-center gap-2 px-2 py-1">
          <div className="flex items-baseline gap-2">
            <span className="text-lg font-semibold tracking-tight">Tanso</span>
            <span className="font-mono text-xs text-primary">console</span>
          </div>
        </div>
      </SidebarHeader>
      <SidebarContent>
        {!monetizationOff && (
          <>
            <SidebarGroup>
              <SidebarGroupContent>
                <SidebarMenu>
                  <SidebarMenuItem>
                    <SidebarMenuButton
                      render={<Link href="/" />}
                      isActive={isActive("/")}
                      onClick={closeMobile}
                    >
                      <Gauge />
                      Overview
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
            <SidebarGroup>
              <SidebarGroupLabel>Monetization</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>{renderItems(monetizationNav)}</SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </>
        )}
        {!buildSideOff && (
          <>
            {!monetizationOff && <SidebarSeparator />}
            <SidebarGroup>
              <SidebarGroupLabel>Internal spend</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {renderItems(monetizationOff ? internalSpendNav.filter((i) => i.href !== "/spend/pnl") : internalSpendNav)}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          </>
        )}
      </SidebarContent>
      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              render={<Link href="/settings" />}
              isActive={isActive("/settings")}
              onClick={closeMobile}
            >
              <Settings />
              Settings
            </SidebarMenuButton>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <SidebarMenuButton onClick={logout}>
              <LogOut />
              Sign out
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
        {email && (
          <div className="truncate px-2 pb-1 text-xs text-muted-foreground">
            {email}
          </div>
        )}
      </SidebarFooter>
    </Sidebar>
  )
}
